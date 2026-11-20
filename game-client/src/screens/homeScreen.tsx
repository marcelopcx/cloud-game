import { Fragment, useEffect, useState } from "react";
import {CardGame, GameModal, type GameDetails} from "@components";
import "./homeScreen.css";
import {Footer} from "@components";
import {Friend, FriendsPanel, Header, ProfilePanel} from "@components";
import headerLogo from "../../assets/logos/header.svg";
import searchIcon from "../../assets/icons/search.svg";
import userIcon from "../../assets/icons/user.svg";
import dicesIcon from "../../assets/icons/dices.svg";
import {Banner, Button, Carousel, GamesGridLayout, HeroActions, HeroContent, Text} from "@components";
import superTuxKartBanner from "../../assets/images/banner-supertux-kart.jpg";
import twilightPrincessBanner from "../../assets/images/banner-twilight-princess.jpg";
import playIcon from "../../assets/icons/play.svg";
import gamepadIcon from "../../assets/icons/gamepad.svg";
import mouseIcon from "../../assets/icons/computer-mouse.svg";
import { useCatalog, useFriendList, useGameSearch, useHomeLayout, usePlayerSession } from "@hooks";
import type { CatalogGame, HomeSection } from "@services";
import { logout } from "@services";

// Sin historial de partidas todavía: el carrusel muestra estos juegos del catálogo.
const RECENT_GAME_IDS = ["soh", "battleship", "supertuxkart", "ghostship", "spaghettikart", "dusklight"];

export interface HomeScreenProps {
    onLogin: () => void;
    onPlay: (gameId: string, title: string, icon?: string) => void;
    /** Ya llegaron el catálogo y la distribución (o fallaron): lo que falta son las imágenes. */
    onDataReady?: () => void;
}

export const HomeScreen = ({ onPlay, onLogin, onDataReady }: HomeScreenProps) => {
    // Se conserva el juego al cerrar para que el modal tenga contenido durante la animación de salida.
    const [selectedGame, setSelectedGame] = useState<GameDetails | null>(null);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const catalog = useCatalog();
    const player = usePlayerSession();
    const friends = useFriendList(player?.token ?? null);
    const [searchQuery, setSearchQuery] = useState("");
    const { results: searchResults, loading: searching } = useGameSearch(searchQuery);
    // Distribución armada en game-admin. Sin configurar (o si falla) se usa la de abajo por defecto.
    const layout = useHomeLayout();
    // Una sección que quedó sin juegos (se borraron del catálogo) no se muestra; el grid vacío es "todos".
    const sections = layout?.filter((s) => s.type === "GRID" || s.games.length > 0) ?? null;
    const dataReady = layout !== null && catalog.status !== "loading";
    useEffect(() => {
        if (dataReady) onDataReady?.();
    }, [dataReady, onDataReady]);

    // Los «Próximamente» van solo en su carrusel: los grids muestran lo que se puede jugar.
    const playableGames = catalog.games.filter((g) => !g.comingSoon);
    const upcomingGames = catalog.games.filter((g) => g.comingSoon);
    const genres = [...new Set(playableGames.flatMap((g) => g.gender ?? []))];
    const recentGames = RECENT_GAME_IDS
        .map((gameId) => catalog.games.find((g) => g.gameId === gameId))
        .filter((g) => g !== undefined);

    const openGame = (gameId: string) => {
        const game = catalog.games.find((g) => g.gameId === gameId);
        if (!game) return;
        setSelectedGame(game.details);
        setIsModalOpen(true);
    };

    const openCatalogGame = (game: CatalogGame) => {
        setSelectedGame(game.details);
        setIsModalOpen(true);
    };

    const playGame = (game: GameDetails) => {
        if (game.comingSoon) return;
        setIsModalOpen(false);
        onPlay(game.gameId, game.title, game.icon);
    };

    // Dado del header: abre el modal de un juego cualquiera que se pueda jugar (no los «Próximamente»).
    const openRandomGame = () => {
        if (playableGames.length === 0) return;
        openCatalogGame(playableGames[Math.floor(Math.random() * playableGames.length)]);
    };

    // Banners destacados: el título es fijo, el ícono sale del catálogo si ya cargó.
    const playFeatured = (gameId: string, title: string) => {
        onPlay(gameId, title, catalog.games.find((g) => g.gameId === gameId)?.details.icon);
    };

    const header = (
        <Header
            logo={headerLogo}
            logoAlt="Cloud Game logo"
            searchIcon={searchIcon}
            searchPlaceholder="Busqueda"
            userIcon={player ? undefined : userIcon}
            userLabel={player ? `${player.user.username} · tu progreso se guarda` : "Iniciar sesión para guardar tu progreso"}
            onUserClick={onLogin}
            actions={<>
                <button type="button" className="header-user" aria-label="Juego aleatorio" title="Juego aleatorio"
                    disabled={playableGames.length === 0} onClick={openRandomGame}>
                    <img src={dicesIcon} alt="" className="header-user-icon" />
                </button>
                <FriendsPanel token={player?.token ?? null} />
                {player && (
                    <ProfilePanel
                        username={player.user.username}
                        avatar={player.user.avatar}
                        avatarId={player.user.avatarId}
                        token={player.token}
                        icon={userIcon}
                        onLogout={logout}
                    />
                )}
            </>}
            onSearchChange={setSearchQuery}
            searching={searching}
            suggestions={searchResults.map((g) => ({
                id: g.id, title: g.title, icon: g.details.icon, badge: g.comingSoon ? "Próximamente" : undefined,
            }))}
            onSelectSuggestion={(s) => {
                const game = searchResults.find((g) => g.id === s.id);
                if (game) openCatalogGame(game);
            }}
        />
    );

    const catalogError = catalog.status === "error" && (
        <div className="home-screen__catalog-error">
            <Text as="p">No se pudo cargar el catálogo de juegos.</Text>
            <Button label="Reintentar" variant="primary" onClick={catalog.retry} />
        </div>
    );

    const renderSection = (section: HomeSection) => {
        switch (section.type) {
            case "HERO":
                return (
                    <Banner key={section.id} variant="featured" backgroundImage={section.games[0].heroImage} header={header}>
                        <GameHero game={section.games[0]} onPlay={playGame} showGenres />
                    </Banner>
                );
            case "BANNER":
                return (
                    <Banner key={section.id} variant="highlight" backgroundImage={section.games[0].bannerImage}>
                        <GameHero game={section.games[0]} onPlay={playGame} titleSize="large" />
                    </Banner>
                );
            case "CAROUSEL":
                return (
                    <Carousel key={section.id} title={section.title ?? undefined}>
                        {section.games.map((game) => (
                            <CardGame key={game.id} id={game.id} title={game.title} image={game.portrait} gender={game.gender} rating={game.rating} comingSoon={game.comingSoon} onClick={() => openCatalogGame(game)} />
                        ))}
                    </Carousel>
                );
            case "UPCOMING":
                return (
                    <Carousel key={section.id} title={section.title ?? "Próximamente"}>
                        {section.games.map((game) => (
                            <CardGame key={game.id} id={game.id} title={game.title} image={game.portrait} gender={game.gender} rating={game.rating} comingSoon onClick={() => openCatalogGame(game)} />
                        ))}
                    </Carousel>
                );
            case "GRID": {
                // Sin juegos elegidos: todo el catálogo, ordenado por título. Nunca los «Próximamente».
                const games = (section.games.length > 0 ? section.games : catalog.games).filter((g) => !g.comingSoon);
                return (
                    <GamesGridLayout
                        key={section.id}
                        title={section.title ?? undefined}
                        genres={[...new Set(games.flatMap((g) => g.gender ?? []))]}
                        games={games}
                        sortByTitle={section.games.length === 0}
                        onGameClick={(id) => {
                            const game = games.find((g) => g.id === id);
                            if (game) openCatalogGame(game);
                        }}
                    />
                );
            }
        }
    };

    const friendsCarousel = friends.length > 0 && (
        <Carousel title="Amigos activos" itemsPerView={6} maxItemWidth={230}>
            {friends.map((f) => (
                <Friend key={f.userId} username={f.username} avatar={f.avatar} gameName={f.game?.name} gameIcon={f.game?.icon}
                    onClick={() => {
                        const game = f.game && catalog.games.find((g) => g.id === f.game?.id);
                        if (game) openCatalogGame(game);
                    }} />
            ))}
        </Carousel>
    );

    // El header va dentro del banner principal; sin banner principal va solo arriba.
    const configuredHome = sections && sections.length > 0 && (
        <>
            {sections[0].type !== "HERO" && <div className="home-screen__header">{header}</div>}
            {sections.map((section, i) => (
                <Fragment key={section.id}>
                    {renderSection(section)}
                    {i === 0 && friendsCarousel}
                </Fragment>
            ))}
            {catalogError}
        </>
    );

    // Las imágenes del home (incluidos footer y modal, que llega por portal) no se pueden arrastrar.
    return (
        <div style={{ display: "contents" }} onDragStart={(e) => {
            if (e.target instanceof HTMLImageElement) e.preventDefault();
        }}>
        
<div className="home-screen">
    {sections === null ? (
        <div className="home-screen__loading">{header}</div>
    ) : configuredHome || (
    <>
    <Banner
        variant="featured"
        backgroundImage={superTuxKartBanner}
        header={header}
    >
        <HeroContent>
            <Text as="h3" className="home-screen__controls">
                Controles
                <span className="home-screen__controls-icons">
                    <img src={gamepadIcon} alt="Gamepad" />
                    <img src={mouseIcon} alt="Mouse" />
                </span>
            </Text>
            <Text as="h1">SuperTuxKart</Text>
            <Text as="h3"><strong>Género</strong> Carreras de karts arcade en 3D</Text>
            <Text as="p">
                SuperTuxKart es un juego de carreras de karts gratuito y de código abierto, protagonizado por Tux y
                otras mascotas del software libre. En el modo historia debes enfrentarte al malvado Nolok para salvar
                el Reino de las Mascotas, o competir en Grand Prix, batallas y fútbol con hasta ocho amigos o en línea.
            </Text>
            <HeroActions>
                <Button label="Jugar" variant="primary" icon={playIcon} onClick={() => playFeatured("supertuxkart", "SuperTuxKart")} />
            </HeroActions>
        </HeroContent>
    </Banner>

    {friendsCarousel}

    {recentGames.length > 0 && (
        <Carousel title="Jugados recientemente">
            {recentGames.map((game) => (
                <CardGame key={game.id} id={game.id} title={game.title} image={game.portrait} gender={game.gender} rating={game.rating} comingSoon={game.comingSoon} onClick={() => openGame(game.gameId)} />
            ))}
        </Carousel>
    )}

    
    <Banner variant="highlight" backgroundImage={twilightPrincessBanner}>
        <HeroContent titleSize="large">
            <Text as="h3" className="home-screen__controls">
                Controles
                <span className="home-screen__controls-icons">
                    <img src={gamepadIcon} alt="Gamepad" />
                    <img src={mouseIcon} alt="Mouse" />
                </span>
            </Text>
            <Text as="h1">Twilight Princess</Text>
            <Text as="small">The Legend of <strong>Zelda</strong></Text>
            <Text as="p">
                El Crepúsculo cubre Hyrule y Link, convertido en lobo, recorre el reino junto a la misteriosa Midna
                para liberar a la princesa Zelda y enfrentar a Zant.
            </Text>
            <HeroActions>
                <Button label="Jugar" variant="primary" icon={playIcon} onClick={() => playFeatured("dusklight", "The Legend of Zelda: Twilight Princess")} />
            </HeroActions>
        </HeroContent>
    </Banner>

    {upcomingGames.length > 0 && (
        <Carousel title="Próximamente">
            {upcomingGames.map((game) => (
                <CardGame key={game.id} id={game.id} title={game.title} image={game.portrait} gender={game.gender} rating={game.rating} comingSoon onClick={() => openCatalogGame(game)} />
            ))}
        </Carousel>
    )}

    {catalogError}

    <GamesGridLayout
        genres={genres}
        games={playableGames}
        onGameClick={(id) => {
            const game = catalog.games.find((g) => g.id === id);
            if (game) openGame(game.gameId);
        }}
    />
    </>
    )}

</div>
   <div>
        <Footer></Footer>
    </div>

    <GameModal
        open={isModalOpen}
        game={selectedGame}
        onClose={() => setIsModalOpen(false)}
        onPlay={playGame}
    />
    </div>
    );
};

interface GameHeroProps {
    game: CatalogGame;
    onPlay: (game: GameDetails) => void;
    titleSize?: "default" | "large";
    showGenres?: boolean;
}

/** Contenido de un banner configurado: controles, nombre, géneros, descripción y Jugar. */
const GameHero = ({ game, onPlay, titleSize, showGenres = false }: GameHeroProps) => {
    const platforms = game.details.platforms ?? [];
    return (
        <HeroContent titleSize={titleSize}>
            {(platforms.includes("gamepad") || platforms.includes("mouse")) && (
                <Text as="h3" className="home-screen__controls">
                    Controles
                    <span className="home-screen__controls-icons">
                        {platforms.includes("gamepad") && <img src={gamepadIcon} alt="Gamepad" />}
                        {platforms.includes("mouse") && <img src={mouseIcon} alt="Mouse" />}
                    </span>
                </Text>
            )}
            <Text as="h1">{game.title}</Text>
            {showGenres && game.details.genres && game.details.genres.length > 0 && (
                <Text as="h3"><strong>Género</strong> {game.details.genres.join(", ")}</Text>
            )}
            {game.details.description && <Text as="p">{game.details.description}</Text>}
            <HeroActions>
                {game.comingSoon
                    ? <Button label="Próximamente" variant="transparent" className="button--coming-soon" disabled />
                    : <Button label="Jugar" variant="primary" icon={playIcon} onClick={() => onPlay(game.details)} />}
            </HeroActions>
        </HeroContent>
    );
};
