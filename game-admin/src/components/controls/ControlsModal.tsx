import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type MouseEvent } from "react";
import { useModalDialog } from "@hooks";
import { describeError, getGameInputs, getInputOptions, replaceGameInputs } from "@services";
import type { Game, GameInput, InputOptions } from "@/types";
import { Button } from "../button/Button.tsx";
import { CloseIcon, EditIcon, PlusIcon, RefreshIcon, TrashIcon } from "../icons/Icons.tsx";
import "./controlsModal.css";

/** Nombres de GAMEPAD que habilitan cada columna (GameInputService en game-crud). */
export const GAMEPAD_CONTROLLER = "Mando";
export const GAMEPAD_KEYBOARD = "Teclado y ratón";

export interface ControlsModalProps {
    /** El juego cuyos controles se editan; null = cerrado. */
    game: Game | null;
    onClose: () => void;
    onSaved: (message: string) => void;
    /** Para agregar «Mando» o «Teclado y ratón» a los gamepads del juego. */
    onEditGame: (game: Game) => void;
}

interface Row extends GameInput {
    key: number;
}

type Load =
    | { status: "loading" }
    | { status: "error"; message: string }
    | { status: "ready"; options: InputOptions; initial: GameInput[] };

let nextKey = 0;
const toRows = (inputs: GameInput[]): Row[] => inputs.map((input) => ({ ...input, key: nextKey++ }));

/** Lo que se envía: sin espacios sobrantes y sin las columnas que el juego no soporta. */
const toPayload = (rows: Row[], hasKeyboard: boolean, hasController: boolean): GameInput[] =>
    rows.map((r) => ({
        action: r.action.trim(),
        keyboard: hasKeyboard ? r.keyboard || null : null,
        controller: hasController ? r.controller || null : null,
    }));

/** Primer problema de cada fila (índice → mensaje), con las mismas reglas que PUT /games/{id}/inputs. */
function validate(payload: GameInput[]): Map<number, string> {
    const problems = new Map<number, string>();
    const seen = new Set<string>();
    payload.forEach((row, i) => {
        if (!row.action) problems.set(i, "Escribí el nombre de la acción.");
        else if (seen.has(row.action)) problems.set(i, `La acción «${row.action}» está repetida.`);
        else if (!row.keyboard && !row.controller) problems.set(i, `«${row.action}» necesita una tecla o un botón.`);
        seen.add(row.action);
    });
    return problems;
}

/**
 * Editor de los controles de un juego (GAME_INPUT): una fila por acción con su tecla y su botón
 * de mando. Las columnas dependen de los gamepads del juego; al guardar se reemplazan todos.
 */
export const ControlsModal = ({ game, onClose, onSaved, onEditGame }: ControlsModalProps) => {
    const ref = useModalDialog(game !== null);
    const titleId = useId();
    const actionsListId = useId();
    const [load, setLoad] = useState<Load>({ status: "loading" });
    const [rows, setRows] = useState<Row[]>([]);
    const [attempted, setAttempted] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [reloadCount, setReloadCount] = useState(0);
    const tableRef = useRef<HTMLTableSectionElement>(null);

    const hasKeyboard = game?.gamepads.includes(GAMEPAD_KEYBOARD) ?? false;
    const hasController = game?.gamepads.includes(GAMEPAD_CONTROLLER) ?? false;

    useEffect(() => {
        if (!game) return;
        const controller = new AbortController();
        Promise.all([getGameInputs(game.id, controller.signal), getInputOptions(controller.signal)])
            .then(([inputs, options]) => {
                setLoad({ status: "ready", options, initial: inputs });
                setRows(toRows(inputs));
            })
            .catch((e: Error) => {
                if (e.name !== "AbortError") setLoad({ status: "error", message: describeError(e, "cargar los controles") });
            });
        return () => controller.abort();
    }, [game, reloadCount]);

    const payload = useMemo(() => toPayload(rows, hasKeyboard, hasController), [rows, hasKeyboard, hasController]);
    const problems = useMemo(() => validate(payload), [payload]);
    const initial = load.status === "ready" ? load.initial : null;
    const dirty = initial !== null && JSON.stringify(payload) !== JSON.stringify(initial);
    // Teclas o botones guardados en una columna que el juego ya no soporta: al guardar se quitan.
    const dropped = initial?.filter((r) => (!hasKeyboard && r.keyboard) || (!hasController && r.controller)).length ?? 0;

    const update = (key: number, change: Partial<GameInput>) =>
        setRows((all) => all.map((r) => (r.key === key ? { ...r, ...change } : r)));

    const addRow = () => {
        setRows((all) => [...all, { key: nextKey++, action: "", keyboard: null, controller: null }]);
        // El input de la fila nueva todavía no existe: se enfoca tras el render.
        requestAnimationFrame(() => tableRef.current?.querySelector<HTMLInputElement>("tr:last-child input")?.focus());
    };

    const handleClose = () => {
        if (!saving) onClose();
    };

    // Al cerrarse el diálogo (por cualquier vía) queda listo para el próximo juego.
    const handleClosed = () => {
        setLoad({ status: "loading" });
        setRows([]);
        setAttempted(false);
        setError(null);
        onClose();
    };

    const retry = () => {
        setLoad({ status: "loading" });
        setReloadCount((c) => c + 1);
    };

    const handleBackdropClick = (event: MouseEvent<HTMLDialogElement>) => {
        if (event.target === event.currentTarget) handleClose();
    };

    const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!game) return;
        setAttempted(true);
        if (problems.size > 0) {
            setError(problems.values().next().value ?? null);
            return;
        }
        setSaving(true);
        setError(null);
        try {
            const saved = await replaceGameInputs(game.id, payload);
            onSaved(`Se guardaron los controles de «${game.name}» (${saved.length === 1 ? "1 acción" : `${saved.length} acciones`}).`);
            onClose();
        } catch (e) {
            setError(describeError(e, "guardar los controles"));
        } finally {
            setSaving(false);
        }
    };

    const options = load.status === "ready" ? load.options : null;
    const noColumns = !hasKeyboard && !hasController;

    return (
        <dialog
            ref={ref}
            className="modal controls-modal"
            aria-labelledby={titleId}
            onClose={handleClosed}
            onCancel={(e) => saving && e.preventDefault()}
            onClick={handleBackdropClick}
        >
            {game && (
                <form className="controls-form" onSubmit={handleSubmit} noValidate>
                    <header className="controls-form__header">
                        <div>
                            <p className="controls-form__eyebrow">Juego #{game.id}</p>
                            <h2 id={titleId}>Controles de «{game.name}»</h2>
                        </div>
                        <Button variant="ghost" iconOnly icon={<CloseIcon />} onClick={handleClose} disabled={saving}>
                            Cerrar
                        </Button>
                    </header>

                    <div className="controls-form__body">
                        <p className="controls-form__hint">
                            Cada acción con su tecla y su botón de mando (nomenclatura Xbox), como los ve el jugador en el
                            modal de controles del reproductor. Las acciones nuevas se crean al guardar.
                        </p>

                        {noColumns ? (
                            <div className="controls-form__notice">
                                <p>
                                    El juego no tiene «{GAMEPAD_CONTROLLER}» ni «{GAMEPAD_KEYBOARD}» en sus gamepads, así que no
                                    se le pueden cargar controles.
                                </p>
                                <Button size="sm" variant="secondary" icon={<EditIcon size={14} />} onClick={() => onEditGame(game)}>
                                    Editar gamepads
                                </Button>
                            </div>
                        ) : load.status === "loading" ? (
                            <p className="controls-form__status">Cargando controles…</p>
                        ) : load.status === "error" ? (
                            <div className="controls-form__notice controls-form__notice--error" role="alert">
                                <p>{load.message}</p>
                                <Button size="sm" variant="secondary" icon={<RefreshIcon size={14} />} onClick={retry}>
                                    Reintentar
                                </Button>
                            </div>
                        ) : (
                            <>
                                {(!hasKeyboard || !hasController) && (
                                    <p className="controls-form__status">
                                        Solo {hasKeyboard ? "teclado" : "mando"}: para cargar {hasKeyboard ? "botones" : "teclas"}, agregá
                                        «{hasKeyboard ? GAMEPAD_CONTROLLER : GAMEPAD_KEYBOARD}» a los gamepads del juego.
                                    </p>
                                )}
                                {dropped > 0 && (
                                    <p className="controls-form__notice controls-form__notice--warn">
                                        {dropped === 1 ? "Una acción tiene" : `${dropped} acciones tienen`} controles de un tipo que el
                                        juego ya no tiene en sus gamepads: al guardar se quitan.
                                    </p>
                                )}

                                {rows.length === 0 ? (
                                    <p className="controls-form__status">Este juego todavía no tiene controles cargados.</p>
                                ) : (
                                    <div className="controls-form__scroll">
                                        <table className="controls-form__table">
                                            <thead>
                                                <tr>
                                                    <th scope="col">Acción</th>
                                                    {hasKeyboard && <th scope="col">Teclado y ratón</th>}
                                                    {hasController && <th scope="col">Mando</th>}
                                                    <th scope="col"><span className="controls-form__sr">Quitar</span></th>
                                                </tr>
                                            </thead>
                                            <tbody ref={tableRef}>
                                                {rows.map((row, i) => {
                                                    const problem = attempted ? problems.get(i) : undefined;
                                                    return (
                                                        <tr key={row.key} className={problem ? "controls-form__row--invalid" : undefined} title={problem}>
                                                            <td>
                                                                <input
                                                                    value={row.action}
                                                                    onChange={(e) => update(row.key, { action: e.target.value })}
                                                                    list={actionsListId}
                                                                    maxLength={100}
                                                                    placeholder="Saltar"
                                                                    aria-label={`Acción ${i + 1}`}
                                                                    aria-invalid={problem ? true : undefined}
                                                                />
                                                            </td>
                                                            {hasKeyboard && (
                                                                <td>
                                                                    <InputSelect
                                                                        value={row.keyboard}
                                                                        options={options!.keyboard}
                                                                        label={`Tecla de ${row.action || `la acción ${i + 1}`}`}
                                                                        onChange={(keyboard) => update(row.key, { keyboard })}
                                                                    />
                                                                </td>
                                                            )}
                                                            {hasController && (
                                                                <td>
                                                                    <InputSelect
                                                                        value={row.controller}
                                                                        options={options!.controller}
                                                                        label={`Botón de ${row.action || `la acción ${i + 1}`}`}
                                                                        onChange={(controller) => update(row.key, { controller })}
                                                                    />
                                                                </td>
                                                            )}
                                                            <td className="controls-form__remove">
                                                                <Button
                                                                    size="sm"
                                                                    variant="ghost"
                                                                    iconOnly
                                                                    icon={<TrashIcon size={15} />}
                                                                    onClick={() => setRows((all) => all.filter((r) => r.key !== row.key))}
                                                                >
                                                                    {`Quitar ${row.action || `la acción ${i + 1}`}`}
                                                                </Button>
                                                            </td>
                                                        </tr>
                                                    );
                                                })}
                                            </tbody>
                                        </table>
                                    </div>
                                )}

                                <datalist id={actionsListId}>
                                    {options!.actions.map((a) => <option key={a} value={a} />)}
                                </datalist>
                                <div>
                                    <Button size="sm" variant="secondary" icon={<PlusIcon size={14} />} onClick={addRow}>
                                        Agregar acción
                                    </Button>
                                </div>
                                <p className="controls-form__status">
                                    Se ordenan como en el catálogo de acciones: las nuevas quedan al final.
                                </p>
                            </>
                        )}
                    </div>

                    <footer className="controls-form__footer">
                        {error && <p className="controls-form__error" role="alert">{error}</p>}
                        <Button variant="ghost" onClick={handleClose} disabled={saving}>
                            Cancelar
                        </Button>
                        <Button type="submit" variant="primary" disabled={saving || !dirty || noColumns}>
                            {saving ? "Guardando…" : "Guardar controles"}
                        </Button>
                    </footer>
                </form>
            )}
        </dialog>
    );
};

/** Select de tecla o botón; "—" = sin ese control. Conserva un valor guardado que ya no esté en la lista. */
const InputSelect = ({ value, options, label, onChange }: {
    value: string | null;
    options: string[];
    label: string;
    onChange: (value: string | null) => void;
}) => (
    <select value={value ?? ""} onChange={(e) => onChange(e.target.value || null)} aria-label={label}>
        <option value="">—</option>
        {value && !options.includes(value) && <option value={value}>{value}</option>}
        {options.map((o) => <option key={o} value={o}>{o}</option>)}
    </select>
);
