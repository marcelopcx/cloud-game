package api.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import api.dto.FriendPlayingResponse;
import api.dto.FriendRequestResponse;
import api.dto.FriendSearchResponse;
import api.dto.FriendSearchResponse.Relation;
import api.exception.ApiException;
import api.model.Friend;
import api.model.FriendStatus;
import api.model.Game;
import api.model.Image;
import api.model.User;
import api.repository.FriendRepository;
import api.repository.GameRepository;
import api.repository.UserRepository;

/**
 * Amigos de un usuario: las solicitudes ACCEPTED en cualquier dirección. Si están jugando lo
 * responde game-dispatcher ({@link PlayingGateway}); la base solo guarda la amistad.
 * Una solicitud rechazada no se le avisa al emisor: para él sigue enviada y no puede repetirla.
 */
@Service
public class FriendService {

    public static final int SEARCH_MAX_LIMIT = 20;

    private final FriendRepository friends;
    private final UserRepository users;
    private final GameRepository games;
    private final PlayingGateway dispatcher;
    private final FriendEvents events;

    public FriendService(FriendRepository friends, UserRepository users, GameRepository games,
                         PlayingGateway dispatcher, FriendEvents events) {
        this.friends = friends;
        this.users = users;
        this.games = games;
        this.dispatcher = dispatcher;
        this.events = events;
    }

    /**
     * Un usuario entró o salió de partida ({@link DispatcherPlayingFeed}): se avisa con un evento
     * {@code friend} solo a sus amigos que tienen {@code /friends/events} abierto.
     */
    @Transactional(readOnly = true)
    public void playingChanged(PlayingGateway.Playing change) {
        Set<Integer> connected = events.connected();
        if (connected.isEmpty()) {
            return;
        }
        Map<Integer, User> byId = accepted(change.userId());
        List<Integer> targets = byId.keySet().stream().filter(connected::contains).toList();
        if (targets.isEmpty()) {
            return;
        }
        User user = users.findById(change.userId()).orElse(null);
        if (user == null) {
            return;
        }
        FriendPlayingResponse.Game game = null;
        if (change.gameId() != null) {
            game = games.findAll().stream()
                    .filter(g -> change.gameId().equals(stationGameId(g.getPath())))
                    .findFirst()
                    .map(FriendService::gameOf)
                    .orElse(null);
        }
        events.send(targets, FriendEvents.FRIEND, new FriendPlayingResponse(user.getId(), user.getUsername(),
                UserService.avatarUrl(user), game, game == null ? null : change.since()));
    }

    /** Amigos en partida, del que empezó hace menos al que lleva más tiempo. */
    @Transactional(readOnly = true)
    public List<FriendPlayingResponse> playing(int userId) {
        return playing(accepted(userId));
    }

    /**
     * Todos los amigos: primero los que juegan (como {@link #playing(int)}) y después el resto por
     * username, con {@code game} y {@code since} en null. Sin dispatcher, todos salen desconectados.
     */
    @Transactional(readOnly = true)
    public List<FriendPlayingResponse> list(int userId) {
        Map<Integer, User> byId = accepted(userId);
        List<FriendPlayingResponse> out = new ArrayList<>();
        try {
            out.addAll(playing(byId));
        } catch (ApiException e) {
            // El dispatcher no responde: la lista sigue sirviendo sin el estado.
        }
        Set<Integer> shown = new HashSet<>();
        out.forEach(f -> shown.add(f.userId()));
        byId.values().stream()
                .filter(u -> !shown.contains(u.getId()))
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .forEach(u -> out.add(new FriendPlayingResponse(u.getId(), u.getUsername(), UserService.avatarUrl(u), null, null)));
        return out;
    }

    /** Amigos (ACCEPTED en cualquier dirección) por id. */
    private Map<Integer, User> accepted(int userId) {
        Map<Integer, User> byId = new LinkedHashMap<>();
        for (Friend f : friends.findByUserAndStatus(userId, FriendStatus.ACCEPTED)) {
            User other = f.getSender().getId() == userId ? f.getReceiver() : f.getSender();
            byId.put(other.getId(), other);
        }
        return byId;
    }

    private List<FriendPlayingResponse> playing(Map<Integer, User> byId) {
        if (byId.isEmpty()) {
            return List.of();
        }
        List<PlayingGateway.Playing> playing = dispatcher.playing(byId.keySet());
        if (playing.isEmpty()) {
            return List.of();
        }
        Map<String, Game> catalog = new HashMap<>();
        games.findAll().forEach(g -> catalog.put(stationGameId(g.getPath()), g));

        List<FriendPlayingResponse> out = new ArrayList<>();
        for (PlayingGateway.Playing p : playing) {
            User friend = byId.get(p.userId());
            Game game = catalog.get(p.gameId());
            if (friend == null || game == null) {
                continue;
            }
            out.add(new FriendPlayingResponse(friend.getId(), friend.getUsername(), UserService.avatarUrl(friend),
                    gameOf(game), p.since()));
        }
        out.sort((a, b) -> b.since().compareTo(a.since()));
        return out;
    }

    private static FriendPlayingResponse.Game gameOf(Game game) {
        Image image = game.mainImage();
        return new FriendPlayingResponse.Game(game.getId(), game.getName(), image == null ? null : image.getIcon());
    }

    /** Bandeja de entrada: solicitudes PENDING que recibió el usuario, la más nueva primero. */
    @Transactional(readOnly = true)
    public List<FriendRequestResponse> requests(int userId) {
        return friends.findReceived(userId, FriendStatus.PENDING).stream()
                .map(f -> new FriendRequestResponse(f.getId(), f.getSender().getId(), f.getSender().getUsername(),
                        UserService.avatarUrl(f.getSender())))
                .toList();
    }

    /** Usuarios cuyo username contiene {@code q} (sin mayúsculas ni tildes), sin el propio, con la relación. */
    @Transactional(readOnly = true)
    public List<FriendSearchResponse> search(int userId, String q, int limit) {
        if (limit < 1 || limit > SEARCH_MAX_LIMIT) {
            throw ApiException.badRequest("limit: debe estar entre 1 y " + SEARCH_MAX_LIMIT);
        }
        String text = q == null ? "" : q.strip();
        if (text.isEmpty()) {
            return List.of();
        }
        List<User> found = users.searchByUsername(GameService.likeEscape(GameService.fold(text)), userId, limit);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Integer, Friend> byOther = new HashMap<>();
        for (Friend f : friends.findBetween(userId, found.stream().map(User::getId).toList())) {
            byOther.put(f.getSender().getId() == userId ? f.getReceiver().getId() : f.getSender().getId(), f);
        }
        return found.stream().map(u -> {
            Friend f = byOther.get(u.getId());
            Relation relation = relation(userId, f);
            return new FriendSearchResponse(u.getId(), u.getUsername(), UserService.avatarUrl(u), relation,
                    relation == Relation.RECEIVED ? f.getId() : null);
        }).toList();
    }

    /**
     * Envía una solicitud a {@code targetId}. Si el otro ya le había enviado una pendiente, la
     * acepta; si el usuario se la había rechazado, la reemplaza por una nueva en su dirección.
     */
    @Transactional
    public FriendSearchResponse send(int userId, int targetId) {
        if (userId == targetId) {
            throw ApiException.badRequest("userId: no puedes enviarte una solicitud a ti mismo");
        }
        User me = users.findById(userId)
                .orElseThrow(() -> new ApiException(401, "UNAUTHORIZED", "El usuario del token ya no existe"));
        User target = users.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("No existe el usuario " + targetId));
        Friend existing = friends.findBetween(userId, List.of(targetId)).stream().findFirst().orElse(null);
        switch (relation(userId, existing)) {
            case FRIEND -> throw ApiException.conflict("Ya eres amigo de " + target.getUsername());
            case SENT -> throw ApiException.conflict("Ya le enviaste una solicitud a " + target.getUsername());
            case RECEIVED -> {
                existing.setStatus(FriendStatus.ACCEPTED);
                events.send(List.of(userId, targetId), FriendEvents.FRIENDS, Map.of());
                events.send(List.of(userId), FriendEvents.REQUESTS, Map.of());
                return new FriendSearchResponse(targetId, target.getUsername(), UserService.avatarUrl(target), Relation.FRIEND, null);
            }
            case NONE -> {
                if (existing != null) {
                    // La rechazó el usuario: se borra para que la nueva vaya en su dirección.
                    friends.delete(existing);
                    friends.flush();
                }
                friends.save(new Friend(me, target));
                events.send(List.of(targetId), FriendEvents.REQUESTS, Map.of());
                return new FriendSearchResponse(targetId, target.getUsername(), UserService.avatarUrl(target), Relation.SENT, null);
            }
        }
        throw new IllegalStateException();
    }

    /** Acepta o rechaza una solicitud PENDING que recibió el usuario; 404 si no es suya o ya respondió. */
    @Transactional
    public void respond(int userId, int requestId, boolean accept) {
        Friend f = friends.findById(requestId)
                .filter(r -> r.getReceiver().getId() == userId && r.getStatus() == FriendStatus.PENDING)
                .orElseThrow(() -> ApiException.notFound("No tienes una solicitud pendiente " + requestId));
        f.setStatus(accept ? FriendStatus.ACCEPTED : FriendStatus.REJECTED);
        // Los otros dispositivos del receptor también actualizan su bandeja.
        events.send(List.of(userId), FriendEvents.REQUESTS, Map.of());
        if (accept) {
            events.send(List.of(userId, f.getSender().getId()), FriendEvents.FRIENDS, Map.of());
        }
    }

    /** Relación vista desde {@code userId}; una rechazada cuenta como enviada para el emisor y nada para el receptor. */
    private static Relation relation(int userId, Friend f) {
        if (f == null) {
            return Relation.NONE;
        }
        boolean mine = f.getSender().getId() == userId;
        return switch (f.getStatus()) {
            case ACCEPTED -> Relation.FRIEND;
            case PENDING -> mine ? Relation.SENT : Relation.RECEIVED;
            case REJECTED -> mine ? Relation.SENT : Relation.NONE;
        };
    }

    /** Id de la estación (lo que usa el dispatcher): último segmento de {@code GAME.path}. */
    static String stationGameId(String path) {
        String[] parts = path.split("/");
        return parts.length == 0 ? path : parts[parts.length - 1];
    }
}
