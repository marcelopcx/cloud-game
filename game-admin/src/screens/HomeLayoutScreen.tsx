import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
    ArrowDownIcon,
    ArrowUpIcon,
    Button,
    ChevronLeftIcon,
    ChevronRightIcon,
    CloseIcon,
    GamePickerDialog,
    GripIcon,
    ImageIcon,
    LayoutIcon,
    PlusIcon,
    RefreshIcon,
    TrashIcon,
} from "@components";
import { move, useDragSort } from "@hooks";
import { describeError, getHome, saveHome } from "@services";
import type { Game, HomeSectionType, UpdateHomeRequest } from "@/types";
import "./homeLayoutScreen.css";

export interface HomeLayoutScreenProps {
    /** Catálogo completo (GET /games); null mientras carga. */
    games: Game[] | null;
    onSaved: (message: string) => void;
    onError: (message: string) => void;
    /** Avisa si hay cambios sin guardar (para confirmar antes de salir). */
    onDirtyChange: (dirty: boolean) => void;
}

interface DraftSection {
    key: string;
    type: HomeSectionType;
    title: string;
    gameIds: number[];
}

const TYPES: Record<HomeSectionType, { label: string; hint: string; defaultTitle: string }> = {
    HERO: {
        label: "Banner principal",
        hint: "Arriba de todo, con el buscador. Usa el banner 1, los géneros y la descripción del juego.",
        defaultTitle: "",
    },
    BANNER: {
        label: "Sub banner",
        hint: "Banner destacado entre secciones, con el banner 2 y la descripción del juego.",
        defaultTitle: "",
    },
    CAROUSEL: {
        label: "Carrusel",
        hint: "Fila deslizable de tarjetas verticales (póster), en el orden elegido.",
        defaultTitle: "Destacados",
    },
    GRID: {
        label: "Grid",
        hint: "Tarjetas con filtro por género. Sin juegos elegidos muestra todo el catálogo por título. Nunca muestra los «Próximamente».",
        defaultTitle: "Descubre todos los juegos",
    },
    UPCOMING: {
        label: "Próximamente",
        hint: "Carrusel que se llena solo con los juegos en estado «Próximamente», del más nuevo al más viejo.",
        defaultTitle: "Próximamente",
    },
};

/** Secciones con título editable. */
const titled = (type: HomeSectionType) => type === "CAROUSEL" || type === "GRID" || type === "UPCOMING";

/** GET /home → borrador. UPCOMING trae los juegos «Próximamente» del momento, pero no guarda ninguno. */
const toDraft = (s: { type: HomeSectionType; title: string | null; games: Game[] }): DraftSection => ({
    key: newKey(),
    type: s.type,
    title: s.title ?? "",
    gameIds: s.type === "UPCOMING" ? [] : s.games.map((g) => g.id),
});

/** Lo que hoy está fijo en game-client (homeScreen.tsx), para arrancar desde ahí. */
const CURRENT_DEFAULT: { type: HomeSectionType; title?: string; paths: string[] }[] = [
    { type: "HERO", paths: ["/games/supertuxkart"] },
    {
        type: "CAROUSEL",
        title: "Jugados recientemente",
        paths: ["soh", "battleship", "supertuxkart", "ghostship", "spaghettikart", "dusklight"].map((id) => `/games/${id}`),
    },
    { type: "BANNER", paths: ["/games/dusklight"] },
    { type: "UPCOMING", title: "Próximamente", paths: [] },
    { type: "GRID", title: "Descubre todos los juegos", paths: [] },
];

let nextKey = 1;
const newKey = () => `s${nextKey++}`;

/** Lo que se envía en PUT /home (sin ids de juegos que ya no existen). */
function toRequest(sections: DraftSection[], exists: (id: number) => boolean): UpdateHomeRequest {
    return {
        sections: sections.map((s) => ({
            type: s.type,
            ...(titled(s.type) ? { title: s.title.trim() || undefined } : {}),
            gameIds: s.gameIds.filter(exists),
        })),
    };
}

function problem(section: DraftSection, ids: number[]): string | null {
    if ((section.type === "HERO" || section.type === "BANNER") && ids.length !== 1) return "Elegí un juego.";
    if (section.type === "CAROUSEL" && ids.length === 0) return "Agregá al menos un juego.";
    return null;
}

/**
 * Distribución del home de game-client (GET/PUT /home): qué secciones hay, en qué
 * orden y con qué juegos. Se edita en borrador y se guarda entera.
 */
export const HomeLayoutScreen = ({ games, onSaved, onError, onDirtyChange }: HomeLayoutScreenProps) => {
    const [draft, setDraft] = useState<DraftSection[] | null>(null);
    const [saved, setSaved] = useState<string>("");
    const [loadError, setLoadError] = useState<unknown>(null);
    const [reloadKey, setReloadKey] = useState(0);
    const [saving, setSaving] = useState(false);
    const [picking, setPicking] = useState<string | null>(null);
    const byId = useMemo(() => new Map((games ?? []).map((g) => [g.id, g])), [games]);
    // Mientras no cargó el catálogo no se descartan ids (serían todos "desconocidos").
    const exists = (id: number) => games === null || byId.has(id);

    useEffect(() => {
        const controller = new AbortController();
        getHome(controller.signal)
            .then((home) => {
                const sections = home.sections.map(toDraft);
                setDraft(sections);
                setSaved(JSON.stringify(toRequest(sections, () => true)));
                setLoadError(null);
            })
            .catch((e: Error) => {
                if (e.name !== "AbortError") setLoadError(e);
            });
        return () => controller.abort();
    }, [reloadKey]);

    const request = draft ? toRequest(draft, exists) : null;
    const dirty = request !== null && JSON.stringify(request) !== saved;
    const problems = new Map((draft ?? []).map((s) => [s.key, problem(s, s.gameIds.filter(exists))]));
    const invalid = [...problems.values()].filter(Boolean).length;

    useEffect(() => onDirtyChange(dirty), [dirty, onDirtyChange]);

    // Aviso del navegador al cerrar o recargar con cambios sin guardar.
    useEffect(() => {
        if (!dirty) return;
        const warn = (e: BeforeUnloadEvent) => e.preventDefault();
        window.addEventListener("beforeunload", warn);
        return () => window.removeEventListener("beforeunload", warn);
    }, [dirty]);

    const update = (key: string, change: (s: DraftSection) => DraftSection) =>
        setDraft((all) => all && all.map((s) => (s.key === key ? change(s) : s)));

    const hasHero = draft?.some((s) => s.type === "HERO") ?? false;

    // El banner principal siempre queda primero (lleva el header del home).
    const moveSection = (from: number, to: number) =>
        setDraft((all) => {
            if (!all) return all;
            const next = move(all, from, to);
            const hero = next.findIndex((s) => s.type === "HERO");
            return hero > 0 ? all : next;
        });

    const addSection = (type: HomeSectionType) => {
        const section: DraftSection = { key: newKey(), type, title: TYPES[type].defaultTitle, gameIds: [] };
        setDraft((all) => (type === "HERO" ? [section, ...(all ?? [])] : [...(all ?? []), section]));
        if (type !== "GRID" && type !== "UPCOMING") setPicking(section.key);
    };

    const startFromCurrent = () => {
        const byPath = new Map((games ?? []).map((g) => [g.path, g.id]));
        setDraft(CURRENT_DEFAULT.map((s) => ({
            key: newKey(),
            type: s.type,
            title: s.title ?? "",
            gameIds: s.paths.map((p) => byPath.get(p)).filter((id): id is number => id !== undefined),
        })));
    };

    const handleSave = async () => {
        if (!request) return;
        setSaving(true);
        try {
            const home = await saveHome(request);
            const sections = home.sections.map(toDraft);
            setDraft(sections);
            setSaved(JSON.stringify(toRequest(sections, () => true)));
            onSaved(sections.length ? "Se guardó la distribución del home." : "El home volvió a la distribución por defecto.");
        } catch (e) {
            onError(describeError(e, "guardar el home"));
        } finally {
            setSaving(false);
        }
    };

    const discard = () => setReloadKey((k) => k + 1);

    const sectionProps = useDragSort(moveSection);
    const pickingSection = draft?.find((s) => s.key === picking) ?? null;

    if (loadError) {
        return (
            <div className="empty-state empty-state--error">
                <h2>No se pudo cargar el home</h2>
                <p>{describeError(loadError, "cargar el home")}</p>
                <Button variant="secondary" icon={<RefreshIcon size={16} />} onClick={discard}>Reintentar</Button>
            </div>
        );
    }

    return (
        <div className="home-layout">
            <section className="admin__hero">
                <div>
                    <p className="admin__eyebrow">game-client</p>
                    <h1>Home</h1>
                    <p className="admin__subtitle">Qué secciones ven los jugadores, en qué orden y con qué juegos.</p>
                </div>
                <div className="admin__hero-actions">
                    {dirty && <Button variant="ghost" onClick={discard} disabled={saving}>Descartar cambios</Button>}
                    <Button variant="primary" onClick={handleSave} disabled={!dirty || invalid > 0 || saving}>
                        {saving ? "Guardando…" : "Guardar home"}
                    </Button>
                </div>
            </section>

            {dirty && (
                <p className={invalid ? "home-layout__status home-layout__status--error" : "home-layout__status"} role="status">
                    {invalid
                        ? `Hay ${invalid === 1 ? "una sección incompleta" : `${invalid} secciones incompletas`}: completalas o quitalas para guardar.`
                        : "Cambios sin guardar. El home de los jugadores cambia al guardar."}
                </p>
            )}

            {draft === null ? (
                <div className="home-layout__loading">Cargando distribución…</div>
            ) : (
                <div className="home-layout__columns">
                    <div className="home-layout__sections">
                        {draft.length === 0 && (
                            <div className="empty-state">
                                <LayoutIcon size={28} />
                                <h2>El home usa la distribución por defecto</h2>
                                <p>
                                    Mientras no haya secciones guardadas, game-client muestra la que tiene fija en el código.
                                    Podés arrancar desde esa o agregar secciones abajo.
                                </p>
                                <Button variant="secondary" onClick={startFromCurrent} disabled={games === null}>
                                    Empezar desde la actual
                                </Button>
                            </div>
                        )}

                        <ol className="home-layout__list">
                            {draft.map((section, index) => (
                                <SectionCard
                                    key={section.key}
                                    index={index}
                                    count={draft.length}
                                    section={section}
                                    byId={byId}
                                    exists={exists}
                                    problem={problems.get(section.key) ?? null}
                                    dragProps={sectionProps(index)}
                                    locked={section.type === "HERO"}
                                    firstMovable={hasHero ? 1 : 0}
                                    onMove={(to) => moveSection(index, to)}
                                    onChange={(change) => update(section.key, change)}
                                    onRemove={() => setDraft((all) => all && all.filter((s) => s.key !== section.key))}
                                    onPick={() => setPicking(section.key)}
                                />
                            ))}
                        </ol>

                        <div className="home-layout__add">
                            <span>Agregar sección</span>
                            <div>
                                <Button size="sm" icon={<PlusIcon size={14} />} onClick={() => addSection("HERO")} disabled={hasHero}
                                    title={hasHero ? "Ya hay un banner principal" : undefined}>
                                    Banner principal
                                </Button>
                                <Button size="sm" icon={<PlusIcon size={14} />} onClick={() => addSection("BANNER")}>Sub banner</Button>
                                <Button size="sm" icon={<PlusIcon size={14} />} onClick={() => addSection("CAROUSEL")}>Carrusel</Button>
                                <Button size="sm" icon={<PlusIcon size={14} />} onClick={() => addSection("GRID")}>Grid</Button>
                                <Button size="sm" icon={<PlusIcon size={14} />} onClick={() => addSection("UPCOMING")}>Próximamente</Button>
                            </div>
                        </div>
                    </div>

                    <aside className="home-layout__map" aria-label="Mapa del home">
                        <p className="admin__eyebrow">Mapa del home</p>
                        <div className="home-map">
                            {draft.length === 0 && <p className="home-map__empty">Distribución por defecto</p>}
                            {draft[0]?.type !== "HERO" && draft.length > 0 && <div className="home-map__header">Header</div>}
                            {draft.map((section, index) => (
                                <MapBlock key={section.key} index={index} section={section} byId={byId} exists={exists} invalid={!!problems.get(section.key)} />
                            ))}
                        </div>
                    </aside>
                </div>
            )}

            <GamePickerDialog
                open={pickingSection !== null}
                title={pickingSection ? `${TYPES[pickingSection.type].label}${pickingSection.title ? ` · ${pickingSection.title}` : ""}` : ""}
                games={games}
                selected={pickingSection?.gameIds ?? []}
                mode={pickingSection?.type === "HERO" || pickingSection?.type === "BANNER" ? "single" : "multiple"}
                onToggle={(game) => {
                    if (!pickingSection) return;
                    update(pickingSection.key, (s) => {
                        if (s.type === "HERO" || s.type === "BANNER") return { ...s, gameIds: [game.id] };
                        return s.gameIds.includes(game.id)
                            ? { ...s, gameIds: s.gameIds.filter((id) => id !== game.id) }
                            : { ...s, gameIds: [...s.gameIds, game.id] };
                    });
                }}
                onClose={() => setPicking(null)}
            />
        </div>
    );
};

interface SectionCardProps {
    index: number;
    count: number;
    section: DraftSection;
    byId: Map<number, Game>;
    exists: (id: number) => boolean;
    problem: string | null;
    dragProps: ReturnType<ReturnType<typeof useDragSort>>;
    locked: boolean;
    firstMovable: number;
    onMove: (to: number) => void;
    onChange: (change: (s: DraftSection) => DraftSection) => void;
    onRemove: () => void;
    onPick: () => void;
}

const SectionCard = ({
    index, count, section, byId, exists, problem, dragProps, locked, firstMovable, onMove, onChange, onRemove, onPick,
}: SectionCardProps) => {
    // Solo se arrastra desde el asa: así se puede seleccionar texto en el título.
    const [armed, setArmed] = useState(false);
    const ids = section.gameIds.filter(exists);
    const single = section.type === "HERO" || section.type === "BANNER";
    const gameProps = useDragSort((from, to) => onChange((s) => ({ ...s, gameIds: move(s.gameIds.filter(exists), from, to) })));
    const moveGame = (from: number, to: number) => onChange((s) => ({ ...s, gameIds: move(s.gameIds.filter(exists), from, to) }));

    return (
        <li
            className={`home-section home-section--${section.type.toLowerCase()}${problem ? " home-section--invalid" : ""}`}
            {...dragProps}
            draggable={armed && !locked}
            onDragEnd={() => {
                setArmed(false);
                dragProps.onDragEnd();
            }}
        >
            <header className="home-section__header">
                <span
                    className={locked ? "home-section__grip home-section__grip--locked" : "home-section__grip"}
                    onPointerDown={() => setArmed(true)}
                    onPointerUp={() => setArmed(false)}
                    title={locked ? "El banner principal va siempre primero" : "Arrastrar para reordenar"}
                >
                    {locked ? <span className="home-section__index">1</span> : <GripIcon size={16} />}
                </span>
                <div className="home-section__heading">
                    <span className="home-section__badge">{TYPES[section.type].label}</span>
                    <p>{TYPES[section.type].hint}</p>
                </div>
                <div className="home-section__tools">
                    {!locked && (
                        <>
                            <Button size="sm" variant="ghost" iconOnly icon={<ArrowUpIcon size={15} />} disabled={index <= firstMovable} onClick={() => onMove(index - 1)}>
                                Subir sección
                            </Button>
                            <Button size="sm" variant="ghost" iconOnly icon={<ArrowDownIcon size={15} />} disabled={index >= count - 1} onClick={() => onMove(index + 1)}>
                                Bajar sección
                            </Button>
                        </>
                    )}
                    <Button size="sm" variant="ghost" iconOnly icon={<TrashIcon size={15} />} className="home-section__remove" onClick={onRemove}>
                        Quitar sección
                    </Button>
                </div>
            </header>

            <div className="home-section__body">
                {!single && (
                    <label className="home-section__title">
                        <span>Título</span>
                        <input
                            value={section.title}
                            maxLength={100}
                            placeholder={TYPES[section.type].defaultTitle}
                            onChange={(e) => onChange((s) => ({ ...s, title: e.target.value }))}
                        />
                    </label>
                )}

                {section.type === "UPCOMING" ? (
                    <UpcomingPreview games={[...byId.values()].filter((g) => g.status === "COMING_SOON")} />
                ) : single ? (
                    <BannerPreview game={ids[0] !== undefined ? byId.get(ids[0]) : undefined} hero={section.type === "HERO"} onPick={onPick} />
                ) : (
                    <>
                        <ol className={`home-section__games home-section__games--${section.type.toLowerCase()}`}>
                            {ids.map((id, i) => {
                                const game = byId.get(id);
                                const image = section.type === "CAROUSEL" ? game?.images?.poster : game?.images?.cover ?? game?.images?.poster;
                                return (
                                    <li key={id} className="home-game" draggable {...gameProps(i)}>
                                        <span className="home-game__thumb">
                                            {image ? <img src={image} alt="" loading="lazy" draggable={false} /> : <ImageIcon size={18} />}
                                            <span className="home-game__pos">{i + 1}</span>
                                        </span>
                                        <span className="home-game__name" title={game?.name}>{game?.name ?? `#${id}`}</span>
                                        <span className="home-game__tools">
                                            <button type="button" aria-label={`Mover ${game?.name} antes`} disabled={i === 0} onClick={() => moveGame(i, i - 1)}>
                                                <ChevronLeftIcon size={14} />
                                            </button>
                                            <button type="button" aria-label={`Quitar ${game?.name}`} onClick={() => onChange((s) => ({ ...s, gameIds: s.gameIds.filter((g) => g !== id) }))}>
                                                <CloseIcon size={14} />
                                            </button>
                                            <button type="button" aria-label={`Mover ${game?.name} después`} disabled={i === ids.length - 1} onClick={() => moveGame(i, i + 1)}>
                                                <ChevronRightIcon size={14} />
                                            </button>
                                        </span>
                                    </li>
                                );
                            })}
                            <li>
                                <button type="button" className="home-game home-game--add" onClick={onPick}>
                                    <PlusIcon size={20} />
                                    <span>{ids.length ? "Agregar o quitar" : "Elegir juegos"}</span>
                                </button>
                            </li>
                        </ol>
                        {section.type === "GRID" && ids.length === 0 && (
                            <p className="home-section__note">Sin juegos elegidos: muestra todo el catálogo, ordenado por título.</p>
                        )}
                    </>
                )}
                {problem && <p className="home-section__problem" role="alert">{problem}</p>}
            </div>
        </li>
    );
};

/** Mismo fondo que usa game-client: banner 1 en el principal, banner 2 en el sub banner. */
function bannerImage(game: Game, hero: boolean) {
    const images = game.images;
    if (!images) return undefined;
    const wide = images.background || images.banner1 || images.banner2 || images.cover || images.poster;
    return (hero ? images.banner1 || wide : images.banner2 || images.banner1 || wide) || undefined;
}

const BannerPreview = ({ game, hero, onPick }: { game?: Game; hero: boolean; onPick: () => void }) =>
    game ? (
        <div className="home-banner">
            {bannerImage(game, hero) && <img src={bannerImage(game, hero)} alt="" />}
            <div className="home-banner__shade" />
            <div className="home-banner__content">
                <strong>{game.name}</strong>
                {game.description && <p>{game.description}</p>}
                {!(hero ? game.images?.banner1 : game.images?.banner2) && (
                    <small>Sin {hero ? "banner 1" : "banner 2"}: se usa otra imagen del juego.</small>
                )}
            </div>
            <Button size="sm" variant="secondary" className="home-banner__change" onClick={onPick}>Cambiar juego</Button>
        </div>
    ) : (
        <button type="button" className="home-banner home-banner--empty" onClick={onPick}>
            <PlusIcon size={22} />
            <span>Elegir juego</span>
        </button>
    );

/** Los juegos que hoy muestra el carrusel UPCOMING (no se eligen: salen del estado de cada juego). */
const UpcomingPreview = ({ games }: { games: Game[] }) => (
    <>
        {games.length > 0 && (
            <ol className="home-section__games home-section__games--carousel home-section__games--static">
                {games.map((game) => (
                    <li key={game.id} className="home-game">
                        <span className="home-game__thumb">
                            {game.images?.poster ? <img src={game.images.poster} alt="" loading="lazy" draggable={false} /> : <ImageIcon size={18} />}
                        </span>
                        <span className="home-game__name" title={game.name}>{game.name}</span>
                    </li>
                ))}
            </ol>
        )}
        <p className="home-section__note">
            {games.length > 0
                ? "Se llena sola: para sumar o sacar un juego, cambiá su estado a «Próximamente» en el catálogo."
                : "Ningún juego está en «Próximamente»: game-client no muestra la sección hasta que haya alguno."}
        </p>
    </>
);

const MapBlock = ({ index, section, byId, exists, invalid }: {
    index: number; section: DraftSection; byId: Map<number, Game>; exists: (id: number) => boolean; invalid: boolean;
}) => {
    const ids = section.gameIds.filter(exists);
    const names = ids.map((id) => byId.get(id)?.name).filter(Boolean) as string[];
    let body: ReactNode;
    switch (section.type) {
        case "HERO":
        case "BANNER":
            body = <span className="home-map__label">{names[0] ?? "Sin juego"}</span>;
            break;
        case "UPCOMING":
        case "CAROUSEL":
            body = (
                <span className="home-map__cards">
                    {Array.from({ length: Math.min(Math.max(ids.length, 1), 6) }, (_, i) => <i key={i} />)}
                </span>
            );
            break;
        case "GRID":
            body = (
                <span className="home-map__grid">
                    {Array.from({ length: 8 }, (_, i) => <i key={i} />)}
                </span>
            );
            break;
    }
    return (
        <div className={`home-map__block home-map__block--${section.type.toLowerCase()}${invalid ? " home-map__block--invalid" : ""}`}>
            <span className="home-map__title">
                {index + 1}. {section.title.trim() || TYPES[section.type].label}
                {(section.type === "CAROUSEL" || section.type === "GRID") && <em>{ids.length || "todos"}</em>}
                {section.type === "UPCOMING" && <em>auto</em>}
            </span>
            {body}
        </div>
    );
};
