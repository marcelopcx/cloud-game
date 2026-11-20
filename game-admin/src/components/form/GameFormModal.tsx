import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type MouseEvent } from "react";
import { useModalDialog } from "@hooks";
import { applyImageChanges, createGame, describeError, updateGame } from "@services";
import type { CreateGameRequest, Game, GameStatus, ImageChanges, ImageKind, UpdateGameRequest } from "@/types";
import { Button } from "../button/Button.tsx";
import { CloseIcon, ImageIcon, StarIcon } from "../icons/Icons.tsx";
import { TagPicker } from "../tag-picker/TagPicker.tsx";
import { GAME_STATUS_OPTIONS } from "../status/gameStatus.ts";
import { ImageField } from "./ImageField.tsx";
import "./gameFormModal.css";

export interface GameFormModalProps {
    open: boolean;
    onClose: () => void;
    onCreated?: (game: Game) => void;
    /** Si se pasa, el modal edita ese juego (PATCH) en vez de crear uno. */
    game?: Game | null;
    onUpdated?: (game: Game) => void;
    /** El juego se guardó pero falló alguna imagen (el modal ya se cerró). */
    onError?: (message: string) => void;
    /** Sugerencias de los selectores; null mientras cargan. */
    categoryOptions: string[] | null;
    gamepadOptions: string[] | null;
}

const text = (data: FormData, key: string) => String(data.get(key) ?? "").trim();
const optionalText = (data: FormData, key: string) => text(data, key) || undefined;

const optionalNumber = (data: FormData, key: string) => {
    const value = text(data, key);
    return value === "" ? undefined : Number(value);
};

function toRequest(data: FormData): CreateGameRequest {
    return {
        name: text(data, "name"),
        description: optionalText(data, "description"),
        rating: optionalNumber(data, "rating"),
        path: text(data, "path"),
        minContainers: optionalNumber(data, "minContainers") ?? 1,
        maxContainers: optionalNumber(data, "maxContainers"),
        status: (text(data, "status") || "ACTIVE") as GameStatus,
        categories: data.getAll("categories").map(String),
        gamepads: data.getAll("gamepads").map(String),
    };
}

/** El juego como lo produciría el formulario, para compararlo con lo editado. */
function fromGame(game: Game): CreateGameRequest {
    return {
        name: game.name,
        description: game.description ?? undefined,
        rating: game.rating ?? undefined,
        path: game.path,
        minContainers: game.minContainers,
        maxContainers: game.maxContainers ?? undefined,
        status: game.status,
        categories: game.categories,
        gamepads: game.gamepads,
    };
}

const same = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

/** Solo los campos que cambiaron; vacío = sin cambios. Un campo opcional vaciado se envía como ""/null para borrarlo. */
function changes(before: CreateGameRequest, after: CreateGameRequest): UpdateGameRequest {
    const diff: UpdateGameRequest = {};
    if (after.name !== before.name) diff.name = after.name;
    if (after.path !== before.path) diff.path = after.path;
    if (after.description !== before.description) diff.description = after.description ?? "";
    if (after.rating !== before.rating) diff.rating = after.rating ?? null;
    if (after.minContainers !== before.minContainers) diff.minContainers = after.minContainers;
    if (after.maxContainers !== before.maxContainers) diff.maxContainers = after.maxContainers ?? null;
    if (after.status !== before.status) diff.status = after.status;
    if (!same(after.categories, before.categories)) diff.categories = after.categories;
    if (!same(after.gamepads, before.gamepads)) diff.gamepads = after.gamepads;
    return diff;
}

/** Campos de imagen del formulario, en el orden en que se muestran. */
const IMAGE_FIELDS: { kind: ImageKind; label: string; hint: string; ratio: string }[] = [
    { kind: "banner1", label: "Banner 1", hint: "Hero 1920×620 · banner principal", ratio: "1920 / 620" },
    { kind: "banner2", label: "Banner 2", hint: "Hero 1920×620 · sub banner", ratio: "1920 / 620" },
    { kind: "poster", label: "Póster", hint: "Grid vertical 600×900 · carrusel", ratio: "2 / 3" },
    { kind: "cover", label: "Cover", hint: "Grid horizontal 920×430 · tarjeta del grid", ratio: "460 / 215" },
    { kind: "background", label: "Portada", hint: "Hero 1920×620 · fondo de la ficha", ratio: "1920 / 620" },
    { kind: "icon", label: "Ícono", hint: "Cuadrado · ficha y barra del reproductor", ratio: "1 / 1" },
];

/** Object URLs de los archivos elegidos, para las miniaturas; se liberan al cambiar. */
function useFilePreviews(changes: ImageChanges): Partial<Record<ImageKind, string>> {
    const urls = useMemo(() => {
        const out: Partial<Record<ImageKind, string>> = {};
        for (const [kind, file] of Object.entries(changes) as [ImageKind, File | null][]) {
            if (file) out[kind] = URL.createObjectURL(file);
        }
        return out;
    }, [changes]);
    useEffect(() => () => Object.values(urls).forEach((u) => URL.revokeObjectURL(u)), [urls]);
    return urls;
}

/** "Super Tux Kart" → "/games/super-tux-kart" (sugerencia de ruta al crear). */
const slugPath = (name: string) =>
    "/games/" + name.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");

/**
 * Formulario para crear o editar un juego (GAME + IMAGE + categorías y gamepads).
 * Los límites de cada campo son los de game-crud/schemas/schema.sql; el
 * navegador los valida antes de enviar.
 */
export const GameFormModal = ({
    open,
    onClose,
    onCreated,
    game,
    onUpdated,
    onError,
    categoryOptions,
    gamepadOptions,
}: GameFormModalProps) => {
    const dialogRef = useModalDialog(open);
    const titleId = useId();
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [dirty, setDirty] = useState(false);
    // Cambia la key del form para rehacerlo con sus valores iniciales (también los TagPicker e
    // ImageField, que form.reset() no toca): tras crear, y al cerrar una edición sin guardar.
    // Al cerrar sin crear, el borrador del juego nuevo se conserva.
    const [resetCount, setResetCount] = useState(0);
    const formKey = `${game?.id ?? "new"}-${resetCount}`;
    const formRef = useRef<HTMLFormElement>(null);
    const initial = game ? fromGame(game) : null;
    // Lo que muestra la tarjeta de vista previa; se actualiza con cada cambio del formulario.
    const [preview, setPreview] = useState<{ key: string; values: CreateGameRequest } | null>(null);
    const shown = preview?.key === formKey ? preview.values : initial;
    // Imágenes elegidas y aún no subidas (se suben al guardar, después del JSON del juego).
    const [imageChanges, setImageChanges] = useState<ImageChanges>({});
    const filePreviews = useFilePreviews(imageChanges);
    const hasImageChanges = Object.keys(imageChanges).length > 0;
    const imageSrc = (kind: ImageKind): string | undefined =>
        kind in imageChanges ? filePreviews[kind] : game?.images?.[kind] ?? undefined;
    const setImage = (kind: ImageKind, change: File | null | undefined) =>
        setImageChanges((all) => {
            const next = { ...all };
            if (change === undefined) delete next[kind];
            else next[kind] = change;
            return next;
        });

    const handleClose = () => {
        setError(null);
        setDirty(false);
        if (game) {
            setResetCount((c) => c + 1);
            setImageChanges({});
        }
        onClose();
    };

    // En edición, Confirmar solo se habilita si el formulario difiere del juego guardado.
    // overrides: los TagPicker avisan antes de re-renderizar sus inputs ocultos, así que pasan la lista nueva.
    const handleInput = (overrides?: Partial<CreateGameRequest>) => {
        if (!formRef.current) return;
        const values = { ...toRequest(new FormData(formRef.current)), ...overrides };
        setPreview({ key: formKey, values });
        if (initial) setDirty(Object.keys(changes(initial, values)).length > 0);
    };

    // Al crear, la ruta se completa desde el nombre mientras nadie la haya escrito a mano.
    const pathTouched = useRef(false);
    const handleNameChange = (name: string) => {
        const path = formRef.current?.elements.namedItem("path") as HTMLInputElement | null;
        if (game || pathTouched.current || !path) return;
        path.value = name.trim() ? slugPath(name) : "";
    };

    // Clic en el fondo (fuera del contenido) cierra el modal.
    const handleBackdropClick = (event: MouseEvent<HTMLDialogElement>) => {
        if (event.target === event.currentTarget && !submitting) handleClose();
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const values = toRequest(new FormData(event.currentTarget));

        if (values.maxContainers !== undefined && values.maxContainers < values.minContainers) {
            setError("El máximo de contenedores no puede ser menor que el mínimo.");
            return;
        }
        if (!game && !imageChanges.poster) {
            setError("Elegí el póster: es la única imagen obligatoria.");
            return;
        }

        setSubmitting(true);
        setError(null);
        let saved: Game | null = null;
        try {
            if (game && initial) {
                const diff = changes(initial, values);
                if (Object.keys(diff).length > 0) saved = await updateGame(game.id, diff);
            } else {
                saved = await createGame(values);
            }
        } catch (e) {
            setError(describeError(e, `${game ? "guardar" : "crear"} el juego`));
            setSubmitting(false);
            return;
        }

        // Los datos ya quedaron guardados: si falla una imagen se avisa, pero el modal se cierra
        // igual (al crear, reintentar duplicaría el juego). Se reintenta editándolo.
        let imageError: string | null = null;
        try {
            const target = saved ?? game!;
            saved = (await applyImageChanges(target.id, imageChanges)) ?? saved;
        } catch (e) {
            imageError = `${describeError(e, "subir las imágenes")}. Los datos del juego quedaron guardados: editalo para reintentar.`;
        }
        if (game) {
            if (saved) onUpdated?.(saved);
        } else {
            setResetCount((c) => c + 1);
            pathTouched.current = false;
            if (saved) onCreated?.(saved);
        }
        setImageChanges({});
        setSubmitting(false);
        if (imageError) onError?.(imageError);
        handleClose();
    };

    return (
        <dialog
            ref={dialogRef}
            className="modal game-form-modal"
            aria-labelledby={titleId}
            onClose={handleClose}
            onCancel={(e) => submitting && e.preventDefault()}
            onClick={handleBackdropClick}
        >
            {/* key: al cambiar de juego (o pasar a crear) el form se rehace con sus valores iniciales. */}
            <form
                ref={formRef}
                key={formKey}
                className="game-form"
                onSubmit={handleSubmit}
                onInput={() => handleInput()}
            >
                <header className="game-form__header">
                    <div>
                        <p className="game-form__eyebrow">{game ? `Juego #${game.id}` : "Catálogo"}</p>
                        <h2 id={titleId}>{game ? `Editar «${game.name}»` : "Nuevo juego"}</h2>
                    </div>
                    <Button variant="ghost" iconOnly icon={<CloseIcon />} onClick={handleClose} disabled={submitting}>
                        Cerrar
                    </Button>
                </header>

                <div className="game-form__layout">
                    <div className="game-form__body">
                        <section className="game-form__section">
                            <h3>Datos del juego</h3>
                            <div className="game-form__grid">
                                <label className="game-form__wide">
                                    <span>Nombre <span className="game-form__required">*</span></span>
                                    <input
                                        name="name"
                                        defaultValue={initial?.name}
                                        required
                                        maxLength={100}
                                        placeholder="SuperTuxKart"
                                        onChange={(e) => handleNameChange(e.target.value)}
                                    />
                                </label>
                                <label className="game-form__wide">
                                    <span>Ruta <span className="game-form__required">*</span></span>
                                    <input
                                        name="path"
                                        defaultValue={initial?.path}
                                        required
                                        maxLength={255}
                                        placeholder="/games/supertuxkart"
                                        className="game-form__mono"
                                        onChange={() => (pathTouched.current = true)}
                                    />
                                    <small>Carpeta del juego: <code>/games/&lt;id&gt;</code> (el id de <code>games/&lt;id&gt;/manifest.yaml</code>).</small>
                                </label>
                                <label className="game-form__wide">
                                    <span>Descripción</span>
                                    <textarea name="description" defaultValue={initial?.description} maxLength={500} rows={3} placeholder="Una línea o dos que aparecen en la ficha del juego." />
                                </label>
                                <label>
                                    <span>Rating</span>
                                    <input name="rating" defaultValue={initial?.rating} type="number" min={0} max={5} step={0.1} placeholder="0 a 5" />
                                </label>
                            </div>
                        </section>

                        <section className="game-form__section">
                            <h3>Estado</h3>
                            <div className="game-form__status" role="radiogroup" aria-label="Estado del juego">
                                {GAME_STATUS_OPTIONS.map((o) => (
                                    <label key={o.value} className={`game-form__status-option game-form__status-option--${o.value.toLowerCase().replace("_", "-")}`}>
                                        <input type="radio" name="status" value={o.value} defaultChecked={(initial?.status ?? "ACTIVE") === o.value} />
                                        <span className="game-form__status-label">{o.label}</span>
                                        <small>{o.hint}</small>
                                    </label>
                                ))}
                            </div>
                        </section>

                        <section className="game-form__section">
                            <h3>Contenedores</h3>
                            <div className="game-form__grid">
                                <label>
                                    <span>Mínimo</span>
                                    <input name="minContainers" type="number" min={1} step={1} defaultValue={initial?.minContainers ?? 1} />
                                </label>
                                <label>
                                    <span>Máximo</span>
                                    <input name="maxContainers" defaultValue={initial?.maxContainers} type="number" min={1} step={1} placeholder="Sin límite" />
                                </label>
                            </div>
                        </section>

                        <section className="game-form__section">
                            <h3>Clasificación</h3>
                            <TagPicker
                                name="categories"
                                label="Categorías"
                                options={categoryOptions}
                                defaultValue={initial?.categories}
                                onChange={(categories) => handleInput({ categories })}
                                hint="Las que no existan se crean al guardar."
                            />
                            <TagPicker
                                name="gamepads"
                                label="Gamepads"
                                options={gamepadOptions}
                                defaultValue={initial?.gamepads}
                                onChange={(gamepads) => handleInput({ gamepads })}
                                hint="«Mando» y «Teclado y ratón» habilitan el mapeo de acciones de ese tipo de control."
                            />
                        </section>

                        <section className="game-form__section">
                            <h3>Imágenes</h3>
                            <p className="game-form__section-hint">
                                Archivos PNG, JPEG, WebP, GIF o ICO (hasta 25 MB) que se suben al servidor al guardar. Subir otra reemplaza la anterior de ese tipo. Solo el póster es obligatorio.
                            </p>
                            <div className="game-form__images">
                                {IMAGE_FIELDS.map((f) => (
                                    <ImageField
                                        key={f.kind}
                                        label={f.label}
                                        hint={f.hint}
                                        ratio={f.ratio}
                                        required={f.kind === "poster"}
                                        src={imageSrc(f.kind)}
                                        saved={Boolean(game?.images?.[f.kind])}
                                        change={imageChanges[f.kind]}
                                        onChange={(change) => setImage(f.kind, change)}
                                    />
                                ))}
                            </div>
                        </section>
                    </div>

                    <aside className="game-form__preview" aria-label="Vista previa">
                        <p className="game-form__eyebrow">Vista previa</p>
                        <div className="game-form__card">
                            <div className="game-form__card-poster">
                                {imageSrc("cover") || imageSrc("poster") ? <img src={imageSrc("cover") || imageSrc("poster")} alt="" /> : <ImageIcon size={28} />}
                                {shown?.rating !== undefined && !Number.isNaN(shown.rating) && (
                                    <span className="game-form__card-rating"><StarIcon size={12} /> {shown.rating.toFixed(1)}</span>
                                )}
                            </div>
                            <div className="game-form__card-body">
                                <div className="game-form__card-title">
                                    {imageSrc("icon") && <img src={imageSrc("icon")} alt="" />}
                                    <strong>{shown?.name || "Nombre del juego"}</strong>
                                </div>
                                {shown?.path && <code>{shown.path}</code>}
                                {shown && shown.categories.length > 0 && (
                                    <ul>{shown.categories.map((c) => <li key={c}>{c}</li>)}</ul>
                                )}
                            </div>
                        </div>
                    </aside>
                </div>

                <footer className="game-form__footer">
                    {error && <p className="game-form__error" role="alert">{error}</p>}
                    <Button variant="ghost" onClick={handleClose} disabled={submitting}>
                        Cancelar
                    </Button>
                    {game ? (
                        <Button type="submit" variant="primary" disabled={submitting || !(dirty || hasImageChanges)}>
                            {submitting ? "Guardando…" : "Guardar cambios"}
                        </Button>
                    ) : (
                        <Button type="submit" variant="primary" disabled={submitting}>
                            {submitting ? "Creando…" : "Crear juego"}
                        </Button>
                    )}
                </footer>
            </form>
        </dialog>
    );
};
