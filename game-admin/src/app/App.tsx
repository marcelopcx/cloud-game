import { useEffect, useMemo, useState } from "react";
import {
    Button,
    ConfirmDialog,
    ControlsModal,
    GameDetail,
    GameFormModal,
    GameGrid,
    HealthBadge,
    PlusIcon,
    RefreshIcon,
    SearchIcon,
    Toasts,
    Toolbar,
} from "@components";
import { useCatalog, useDebounced, useFilteredGames, useHealth, useToasts } from "@hooks";
import { AdminsScreen, AvatarsScreen, HomeLayoutScreen } from "@screens";
import { deleteGame, describeError } from "@services";
import type { Game } from "@/types";
import "./App.css";

/** Seed de GAMEPAD (seed_gamepads.sql): se sugieren aunque ningún juego los use todavía. */
const SEED_GAMEPADS = ["Mando", "Teclado y ratón", "Táctil"];

type View = "games" | "home" | "avatars" | "admins";

/** La vista va en el hash (#home, #avatars, #admins) para que sobreviva a una recarga. */
const viewFromHash = (): View => {
    const hash = window.location.hash;
    return hash === "#home" ? "home" : hash === "#avatars" ? "avatars" : hash === "#admins" ? "admins" : "games";
};

const sortEs = (values: Iterable<string>) => [...new Set(values)].sort((a, b) => a.localeCompare(b, "es"));

function App() {
    const health = useHealth();
    const { toasts, push, dismiss } = useToasts();

    const [view, setView] = useState<View>(viewFromHash);
    const [homeDirty, setHomeDirty] = useState(false);
    useEffect(() => {
        const onHash = () => setView(viewFromHash());
        window.addEventListener("hashchange", onHash);
        return () => window.removeEventListener("hashchange", onHash);
    }, []);

    // Cambia tras crear, editar o borrar para que se vuelvan a pedir las listas.
    const [version, setVersion] = useState(0);
    const refresh = () => setVersion((v) => v + 1);
    const catalog = useCatalog(version);

    const [query, setQuery] = useState("");
    const [categoryId, setCategoryId] = useState<number | null>(null);
    const debouncedQuery = useDebounced(query);
    const filtered = useFilteredGames({ query: debouncedQuery, categoryId }, version);

    const [creating, setCreating] = useState(false);
    const [editing, setEditing] = useState<Game | null>(null);
    const [viewing, setViewing] = useState<Game | null>(null);
    const [editingControls, setEditingControls] = useState<Game | null>(null);
    const [deleting, setDeleting] = useState<Game | null>(null);
    const [deleteBusy, setDeleteBusy] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    const allGames = catalog.games;
    const shown = filtered.active ? filtered.games : allGames;
    const listError = filtered.active ? filtered.error : catalog.error;

    const categoryCounts = useMemo(() => {
        const counts = new Map<string, number>();
        allGames?.forEach((g) => g.categories.forEach((c) => counts.set(c, (counts.get(c) ?? 0) + 1)));
        return counts;
    }, [allGames]);

    const categoryOptions = useMemo(() => catalog.categories && catalog.categories.map((c) => c.name), [catalog.categories]);
    const gamepadOptions = useMemo(
        () => allGames && sortEs([...SEED_GAMEPADS, ...allGames.flatMap((g) => g.gamepads)]),
        [allGames],
    );

    const stats = useMemo(() => {
        if (!allGames) return null;
        const rated = allGames.filter((g) => g.rating != null);
        const incomplete = allGames.filter((g) => !g.images?.banner1 || !g.images?.banner2 || !g.images?.cover || !g.images?.background || !g.images?.icon).length;
        return {
            games: allGames.length,
            categories: catalog.categories?.length ?? 0,
            rating: rated.length ? rated.reduce((sum, g) => sum + (g.rating ?? 0), 0) / rated.length : null,
            incomplete,
        };
    }, [allGames, catalog.categories]);

    const handleSaved = (message: string) => {
        push("success", message);
        refresh();
    };

    const openEdit = (game: Game) => {
        setViewing(null);
        setEditing(game);
    };

    const openControls = (game: Game) => {
        setViewing(null);
        setEditingControls(game);
    };

    const askDelete = (game: Game) => {
        setDeleteError(null);
        setDeleting(game);
    };

    const confirmDelete = async () => {
        if (!deleting) return;
        setDeleteBusy(true);
        setDeleteError(null);
        try {
            await deleteGame(deleting.id);
            if (viewing?.id === deleting.id) setViewing(null);
            handleSaved(`Se eliminó «${deleting.name}».`);
            setDeleting(null);
        } catch (e) {
            setDeleteError(describeError(e, "eliminar el juego"));
        } finally {
            setDeleteBusy(false);
        }
    };

    const clearFilters = () => {
        setQuery("");
        setCategoryId(null);
    };

    const selectedCategory = catalog.categories?.find((c) => c.id === categoryId);

    return (
        <div className="admin">
            <header className="topbar">
                <div className="topbar__inner">
                    <a className="topbar__brand" href={import.meta.env.BASE_URL}>
                        <img src={`${import.meta.env.BASE_URL}favicon.svg`} alt="" width={28} height={28} />
                        <span>Cloud Game <strong>Admin</strong></span>
                    </a>
                    <nav className="topbar__tabs" aria-label="Secciones del panel">
                        <a href="#games" aria-current={view === "games" ? "page" : undefined}>Juegos</a>
                        <a href="#home" aria-current={view === "home" ? "page" : undefined}>
                            Home{homeDirty && <span className="topbar__dot" title="Cambios sin guardar" />}
                        </a>
                        <a href="#avatars" aria-current={view === "avatars" ? "page" : undefined}>Avatares</a>
                        <a href="#admins" aria-current={view === "admins" ? "page" : undefined}>Administradores</a>
                    </nav>
                    <nav className="topbar__nav" aria-label="Otras herramientas">
                        <a href="/docs/">Documentación</a>
                        <a href="/api-docs/">APIs</a>
                    </nav>
                    <HealthBadge health={health} />
                </div>
            </header>

            {/* El editor del home queda montado (oculto) para no perder el borrador al cambiar de pestaña. */}
            <main className="admin__main" hidden={view !== "home"}>
                <HomeLayoutScreen
                    games={allGames}
                    onSaved={(message) => push("success", message)}
                    onError={(message) => push("error", message)}
                    onDirtyChange={setHomeDirty}
                />
            </main>

            {view === "avatars" && (
                <main className="admin__main">
                    <AvatarsScreen onSaved={(message) => push("success", message)} />
                </main>
            )}

            {view === "admins" && (
                <main className="admin__main">
                    <AdminsScreen onSaved={(message) => push("success", message)} />
                </main>
            )}

            <main className="admin__main" hidden={view !== "games"}>
                <section className="admin__hero">
                    <div>
                        <p className="admin__eyebrow">Catálogo</p>
                        <h1>Juegos</h1>
                        <p className="admin__subtitle">Lo que ven los jugadores en el home de Cloud Game.</p>
                    </div>
                    <div className="admin__hero-actions">
                        <Button variant="secondary" iconOnly icon={<RefreshIcon size={16} />} onClick={refresh}>
                            Recargar
                        </Button>
                        <Button variant="primary" icon={<PlusIcon size={16} />} onClick={() => setCreating(true)}>
                            Nuevo juego
                        </Button>
                    </div>
                </section>

                <ul className="stats" aria-label="Resumen del catálogo">
                    <li>
                        <span>Juegos</span>
                        <strong>{stats?.games ?? "—"}</strong>
                    </li>
                    <li>
                        <span>Categorías</span>
                        <strong>{stats?.categories ?? "—"}</strong>
                    </li>
                    <li>
                        <span>Rating promedio</span>
                        <strong>{stats?.rating != null ? stats.rating.toFixed(1) : "—"}</strong>
                    </li>
                    <li title="Juegos sin banner 1, banner 2, cover, portada o ícono">
                        <span>Imágenes incompletas</span>
                        <strong className={stats?.incomplete ? "stats__warn" : undefined}>{stats?.incomplete ?? "—"}</strong>
                    </li>
                </ul>

                <Toolbar
                    query={query}
                    onQueryChange={setQuery}
                    categories={catalog.categories}
                    counts={categoryCounts}
                    categoryId={categoryId}
                    onCategoryChange={setCategoryId}
                />

                {shown && !listError && (
                    <p className="admin__results" role="status">
                        {filtered.active ? (
                            <>
                                {shown.length === 1 ? "1 resultado" : `${shown.length} resultados`}
                                {debouncedQuery.trim() && <> para «{debouncedQuery.trim()}»</>}
                                {selectedCategory && <> en {selectedCategory.name}</>}
                                {filtered.truncated && " (se muestran los primeros; afiná la búsqueda)"}
                                <button type="button" onClick={clearFilters}>Limpiar filtros</button>
                            </>
                        ) : (
                            <>{shown.length === 1 ? "1 juego" : `${shown.length} juegos`}, del más nuevo al más viejo</>
                        )}
                    </p>
                )}

                {listError ? (
                    <div className="empty-state empty-state--error">
                        <h2>No se pudieron cargar los juegos</h2>
                        <p>{describeError(listError, "cargar los juegos")}</p>
                        <Button variant="secondary" icon={<RefreshIcon size={16} />} onClick={refresh}>Reintentar</Button>
                    </div>
                ) : shown && shown.length === 0 ? (
                    filtered.active ? (
                        <div className="empty-state">
                            <SearchIcon size={28} />
                            <h2>Sin resultados</h2>
                            <p>Ningún juego coincide con el filtro.</p>
                            <Button variant="secondary" onClick={clearFilters}>Limpiar filtros</Button>
                        </div>
                    ) : (
                        <div className="empty-state">
                            <h2>Todavía no hay juegos</h2>
                            <p>Dá de alta el primero con su ruta <code>/games/&lt;id&gt;</code> y su póster.</p>
                            <Button variant="primary" icon={<PlusIcon size={16} />} onClick={() => setCreating(true)}>Nuevo juego</Button>
                        </div>
                    )
                ) : (
                    <GameGrid games={shown} onOpen={setViewing} onEdit={openEdit} onDelete={askDelete} onEditControls={openControls} />
                )}
            </main>

            <GameDetail
                game={viewing}
                onClose={() => setViewing(null)}
                onEdit={openEdit}
                onDelete={askDelete}
                onEditControls={openControls}
            />

            <ControlsModal
                game={editingControls}
                onClose={() => setEditingControls(null)}
                onSaved={(message) => push("success", message)}
                onEditGame={(game) => {
                    setEditingControls(null);
                    setEditing(game);
                }}
            />

            <GameFormModal
                open={creating}
                onClose={() => setCreating(false)}
                onCreated={(game) => handleSaved(`Se creó «${game.name}».`)}
                onError={(message) => push("error", message)}
                categoryOptions={categoryOptions}
                gamepadOptions={gamepadOptions}
            />
            <GameFormModal
                open={editing !== null}
                game={editing}
                onClose={() => setEditing(null)}
                onUpdated={(game) => handleSaved(`Se guardaron los cambios de «${game.name}».`)}
                onError={(message) => push("error", message)}
                categoryOptions={categoryOptions}
                gamepadOptions={gamepadOptions}
            />

            <ConfirmDialog
                open={deleting !== null}
                title="¿Eliminar este juego?"
                confirmLabel="Eliminar"
                busy={deleteBusy}
                error={deleteError}
                onConfirm={confirmDelete}
                onCancel={() => !deleteBusy && setDeleting(null)}
            >
                Se borran <strong>{deleting?.name}</strong>, su imagen y sus vínculos. Las categorías y gamepads siguen
                existiendo. No se puede deshacer.
            </ConfirmDialog>

            <Toasts toasts={toasts} onDismiss={dismiss} />
        </div>
    );
}

export default App;
