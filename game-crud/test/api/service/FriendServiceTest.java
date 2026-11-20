package api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import api.dto.FriendPlayingResponse;
import api.dto.FriendSearchResponse;
import api.dto.FriendSearchResponse.Relation;
import api.exception.ApiException;
import api.model.Friend;
import api.model.FriendStatus;
import api.model.Game;
import api.model.User;
import api.repository.FriendRepository;
import api.repository.GameRepository;
import api.repository.UserRepository;

class FriendServiceTest {

    private final FriendRepository friends = mock(FriendRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GameRepository games = mock(GameRepository.class);
    private final PlayingGateway dispatcher = mock(PlayingGateway.class);
    private final FriendEvents events = mock(FriendEvents.class);
    private final FriendService service = new FriendService(friends, users, games, dispatcher, events);

    private final User me = user(1, "yo");
    private final User ana = user(2, "ana");
    private final User beto = user(3, "beto");
    private final User caro = user(4, "caro");

    @Test
    void friendsInBothDirectionsWithTheirGameNewestFirst() {
        when(friends.findByUserAndStatus(1, FriendStatus.ACCEPTED))
                .thenReturn(List.of(new Friend(me, ana), new Friend(beto, me), new Friend(me, caro)));
        Instant early = Instant.parse("2026-10-02T10:00:00Z");
        Instant late = Instant.parse("2026-10-02T11:00:00Z");
        when(dispatcher.playing(Set.of(2, 3, 4))).thenReturn(List.of(
                new PlayingGateway.Playing(2, "stk", early),
                new PlayingGateway.Playing(3, "neverball", late),
                new PlayingGateway.Playing(4, "fuera-del-catalogo", late)));
        when(games.findAll()).thenReturn(List.of(game(10, "SuperTuxKart", "/games/stk"),
                game(11, "Neverball", "/games/neverball")));

        List<FriendPlayingResponse> out = service.playing(1);

        assertEquals(2, out.size());
        assertEquals("beto", out.get(0).username());
        assertEquals("Neverball", out.get(0).game().name());
        assertEquals("ana", out.get(1).username());
        assertEquals(10, out.get(1).game().id());
        assertEquals(early, out.get(1).since());
    }

    @Test
    void nobodyPlayingSkipsTheCatalog() {
        when(friends.findByUserAndStatus(1, FriendStatus.ACCEPTED)).thenReturn(List.of(new Friend(me, ana)));
        when(dispatcher.playing(any())).thenReturn(List.of());

        assertTrue(service.playing(1).isEmpty());
        verify(games, never()).findAll();
    }

    @Test
    void listPutsPlayersFirstAndTheRestByUsername() {
        when(friends.findByUserAndStatus(1, FriendStatus.ACCEPTED))
                .thenReturn(List.of(new Friend(me, caro), new Friend(beto, me), new Friend(me, ana)));
        when(dispatcher.playing(any())).thenReturn(List.of(new PlayingGateway.Playing(3, "stk", Instant.now())));
        when(games.findAll()).thenReturn(List.of(game(10, "SuperTuxKart", "/games/stk")));

        List<FriendPlayingResponse> out = service.list(1);

        assertEquals(List.of("beto", "ana", "caro"), out.stream().map(FriendPlayingResponse::username).toList());
        assertEquals("SuperTuxKart", out.get(0).game().name());
        assertNull(out.get(1).game());
        assertNull(out.get(2).since());
    }

    @Test
    void listWithoutDispatcherShowsEveryoneOffline() {
        when(friends.findByUserAndStatus(1, FriendStatus.ACCEPTED)).thenReturn(List.of(new Friend(me, ana)));
        when(dispatcher.playing(any())).thenThrow(new ApiException(502, "DISPATCHER_UNAVAILABLE", "caído"));

        List<FriendPlayingResponse> out = service.list(1);

        assertEquals(1, out.size());
        assertNull(out.get(0).game());
    }

    @Test
    void searchTellsTheRelationWithEachUser() {
        Friend sent = new Friend(me, ana);
        Friend received = withId(new Friend(beto, me), 9);
        Friend rejectedByMe = new Friend(caro, me);
        rejectedByMe.setStatus(FriendStatus.REJECTED);
        User dani = user(5, "dani");
        when(users.searchByUsername("a", 1, 10)).thenReturn(List.of(ana, beto, caro, dani));
        when(friends.findBetween(1, List.of(2, 3, 4, 5))).thenReturn(List.of(sent, received, rejectedByMe));

        List<FriendSearchResponse> out = service.search(1, " A ", 10);

        assertEquals(List.of(Relation.SENT, Relation.RECEIVED, Relation.NONE, Relation.NONE),
                out.stream().map(FriendSearchResponse::relation).toList());
        assertEquals(9, out.get(1).requestId());
        assertNull(out.get(0).requestId());
    }

    @Test
    void emptySearchDoesNotQuery() {
        assertTrue(service.search(1, "  ", 10).isEmpty());
        verify(users, never()).searchByUsername(any(), anyInt(), anyInt());
    }

    @Test
    void sendCreatesAPendingRequest() {
        when(users.findById(1)).thenReturn(Optional.of(me));
        when(users.findById(2)).thenReturn(Optional.of(ana));
        when(friends.findBetween(1, List.of(2))).thenReturn(List.of());

        assertEquals(Relation.SENT, service.send(1, 2).relation());
        verify(friends).save(any(Friend.class));
        verify(events).send(List.of(2), FriendEvents.REQUESTS, Map.of());
    }

    @Test
    void sendingBackAcceptsTheirRequest() {
        Friend theirs = new Friend(ana, me);
        when(users.findById(1)).thenReturn(Optional.of(me));
        when(users.findById(2)).thenReturn(Optional.of(ana));
        when(friends.findBetween(1, List.of(2))).thenReturn(List.of(theirs));

        assertEquals(Relation.FRIEND, service.send(1, 2).relation());
        assertEquals(FriendStatus.ACCEPTED, theirs.getStatus());
        verify(friends, never()).save(any());
        verify(events).send(List.of(1, 2), FriendEvents.FRIENDS, Map.of());
    }

    @Test
    void playingChangeGoesOnlyToConnectedFriends() {
        when(events.connected()).thenReturn(Set.of(1, 3, 99));
        when(friends.findByUserAndStatus(2, FriendStatus.ACCEPTED))
                .thenReturn(List.of(new Friend(me, ana), new Friend(ana, beto), new Friend(caro, ana)));
        when(users.findById(2)).thenReturn(Optional.of(ana));
        when(games.findAll()).thenReturn(List.of(game(10, "SuperTuxKart", "/games/stk")));
        Instant since = Instant.parse("2026-10-02T10:00:00Z");

        service.playingChanged(new PlayingGateway.Playing(2, "stk", since));
        FriendPlayingResponse playing = new FriendPlayingResponse(2, "ana", null,
                new FriendPlayingResponse.Game(10, "SuperTuxKart", null), since);
        verify(events).send(List.of(1, 3), FriendEvents.FRIEND, playing);

        service.playingChanged(new PlayingGateway.Playing(2, null, null));
        verify(events).send(List.of(1, 3), FriendEvents.FRIEND, new FriendPlayingResponse(2, "ana", null, null, null));
    }

    @Test
    void playingChangeWithNobodyConnectedSkipsTheDatabase() {
        when(events.connected()).thenReturn(Set.of());

        service.playingChanged(new PlayingGateway.Playing(2, "stk", Instant.now()));
        verify(friends, never()).findByUserAndStatus(anyInt(), any());
    }

    @Test
    void aRejectedSenderCannotResend() {
        Friend mine = new Friend(me, ana);
        mine.setStatus(FriendStatus.REJECTED);
        when(users.findById(1)).thenReturn(Optional.of(me));
        when(users.findById(2)).thenReturn(Optional.of(ana));
        when(friends.findBetween(1, List.of(2))).thenReturn(List.of(mine));

        ApiException ex = assertThrows(ApiException.class, () -> service.send(1, 2));
        assertEquals(409, ex.status());
    }

    @Test
    void cannotSendToYourself() {
        assertEquals(400, assertThrows(ApiException.class, () -> service.send(1, 1)).status());
    }

    @Test
    void onlyTheReceiverRespondsToAPendingRequest() {
        Friend toAna = new Friend(me, ana);
        when(friends.findById(5)).thenReturn(Optional.of(toAna));

        assertEquals(404, assertThrows(ApiException.class, () -> service.respond(1, 5, true)).status());
        service.respond(2, 5, false);
        assertEquals(FriendStatus.REJECTED, toAna.getStatus());
        assertEquals(404, assertThrows(ApiException.class, () -> service.respond(2, 5, true)).status());
    }

    @Test
    void stationGameIdIsTheLastPathSegment() {
        assertEquals("stk", FriendService.stationGameId("/games/stk"));
        assertEquals("stk", FriendService.stationGameId("stk"));
    }

    private static User user(int id, String username) {
        User u = new User(username + "@example.com", username);
        set(u, "id", id);
        return u;
    }

    private static Friend withId(Friend f, int id) {
        set(f, "id", id);
        return f;
    }

    private static Game game(int id, String name, String path) {
        Game g = new Game(name, null, null, path, 1, null);
        set(g, "id", id);
        return g;
    }

    private static void set(Object target, String field, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
