import { useEffect, useId, useState, type FormEvent } from "react";
import { Button, CloseIcon, ConfirmDialog, EditIcon, ImageIcon, PlusIcon, RefreshIcon, TrashIcon } from "@components";
import { useModalDialog } from "@hooks";
import { createAvatar, deleteAvatar, describeError, listAvatars, replaceAvatarImage, updateAvatar } from "@services";
import type { Avatar } from "@/types";
import "./adminsScreen.css";
import "./avatarsScreen.css";

/** Lo que acepta game-crud (detecta el formato por el contenido). */
const ACCEPT = "image/png,image/jpeg,image/webp,image/gif";

export interface AvatarsScreenProps {
    onSaved: (message: string) => void;
}

/**
 * Avatares que eligen los jugadores en game-client (al registrarse y desde el perfil): no pueden
 * subir los suyos. Alta, edición (nombre, imagen, activo) y baja.
 */
export const AvatarsScreen = ({ onSaved }: AvatarsScreenProps) => {
    const [version, setVersion] = useState(0);
    const refresh = () => setVersion((v) => v + 1);
    const [avatars, setAvatars] = useState<Avatar[] | null>(null);
    const [loadError, setLoadError] = useState<unknown>(null);

    const [creating, setCreating] = useState(false);
    const [editing, setEditing] = useState<Avatar | null>(null);
    const [deleting, setDeleting] = useState<Avatar | null>(null);
    const [deleteBusy, setDeleteBusy] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        listAvatars(controller.signal)
            .then((list) => {
                setAvatars(list);
                setLoadError(null);
            })
            .catch((error: Error) => {
                if (error.name !== "AbortError") setLoadError(error);
            });
        return () => controller.abort();
    }, [version]);

    const askDelete = (avatar: Avatar) => {
        setDeleteError(null);
        setDeleting(avatar);
    };

    const confirmDelete = async () => {
        if (!deleting) return;
        setDeleteBusy(true);
        setDeleteError(null);
        try {
            await deleteAvatar(deleting.id);
            onSaved(`Se eliminó «${deleting.name}».`);
            setDeleting(null);
            refresh();
        } catch (e) {
            setDeleteError(describeError(e, "eliminar el avatar"));
        } finally {
            setDeleteBusy(false);
        }
    };

    const done = (message: string) => {
        onSaved(message);
        refresh();
    };

    const activeCount = avatars?.filter((a) => a.active).length ?? 0;

    return (
        <>
            <section className="admin__hero">
                <div>
                    <p className="admin__eyebrow">Jugadores</p>
                    <h1>Avatares</h1>
                    <p className="admin__subtitle">
                        Los que pueden elegir los jugadores al crear su cuenta y desde su perfil. No pueden subir los suyos.
                    </p>
                </div>
                <div className="admin__hero-actions">
                    <Button variant="secondary" iconOnly icon={<RefreshIcon size={16} />} onClick={refresh}>
                        Recargar
                    </Button>
                    <Button variant="primary" icon={<PlusIcon size={16} />} onClick={() => setCreating(true)}>
                        Nuevo avatar
                    </Button>
                </div>
            </section>

            {loadError ? (
                <div className="empty-state empty-state--error">
                    <h2>No se pudieron cargar los avatares</h2>
                    <p>{describeError(loadError, "cargar los avatares")}</p>
                    <Button variant="secondary" icon={<RefreshIcon size={16} />} onClick={refresh}>Reintentar</Button>
                </div>
            ) : avatars && avatars.length === 0 ? (
                <div className="empty-state">
                    <h2>Todavía no hay avatares</h2>
                    <p>Sin avatares, los jugadores se muestran con la inicial de su nombre.</p>
                    <Button variant="primary" icon={<PlusIcon size={16} />} onClick={() => setCreating(true)}>Nuevo avatar</Button>
                </div>
            ) : (
                <>
                    {avatars && (
                        <p className="admin__results" role="status">
                            {avatars.length === 1 ? "1 avatar" : `${avatars.length} avatares`}, {activeCount} disponibles para los jugadores
                        </p>
                    )}
                    <ul className="avatars-grid" aria-busy={avatars === null}>
                        {avatars?.map((avatar) => (
                            <li key={avatar.id} className={`avatars-grid__card${avatar.active ? "" : " avatars-grid__card--inactive"}`}>
                                <img className="avatars-grid__img" src={avatar.url} alt="" />
                                <strong title={avatar.name}>{avatar.name}</strong>
                                <span className={`avatars-grid__badge${avatar.active ? " avatars-grid__badge--active" : ""}`}>
                                    {avatar.active ? "Disponible" : "Oculto"}
                                </span>
                                <div className="avatars-grid__actions">
                                    <Button size="sm" variant="ghost" icon={<EditIcon size={15} />} onClick={() => setEditing(avatar)}>
                                        Editar
                                    </Button>
                                    <Button size="sm" variant="ghost" iconOnly icon={<TrashIcon size={15} />} onClick={() => askDelete(avatar)}>
                                        Eliminar
                                    </Button>
                                </div>
                            </li>
                        ))}
                    </ul>
                </>
            )}

            <AvatarFormDialog open={creating} onClose={() => setCreating(false)} onDone={done} />
            <AvatarFormDialog open={editing !== null} avatar={editing} onClose={() => setEditing(null)} onDone={done} />

            <ConfirmDialog
                open={deleting !== null}
                title="¿Eliminar este avatar?"
                confirmLabel="Eliminar"
                busy={deleteBusy}
                error={deleteError}
                onConfirm={confirmDelete}
                onCancel={() => !deleteBusy && setDeleting(null)}
            >
                Se borra <strong>{deleting?.name}</strong> y su imagen. Los jugadores que lo usan quedan sin avatar hasta
                que elijan otro. Para dejar de ofrecerlo sin quitárselo a nadie, editalo y marcalo como oculto.
            </ConfirmDialog>
        </>
    );
};

interface AvatarFormDialogProps {
    open: boolean;
    /** Si se pasa, edita ese avatar en vez de crear uno. */
    avatar?: Avatar | null;
    onClose: () => void;
    onDone: (message: string) => void;
}

const AvatarFormDialog = ({ open, avatar, onClose, onDone }: AvatarFormDialogProps) => {
    const ref = useModalDialog(open);
    const titleId = useId();
    const fileId = useId();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [file, setFile] = useState<File | null>(null);
    const [preview, setPreview] = useState<string | null>(null);
    // Cambia la key del form para vaciarlo cada vez que se abre.
    const [formKey, setFormKey] = useState(0);

    const pick = (next: File | null) => {
        if (preview) URL.revokeObjectURL(preview);
        setFile(next);
        setPreview(next ? URL.createObjectURL(next) : null);
    };

    const reset = () => {
        pick(null);
        setError(null);
        setFormKey((k) => k + 1);
    };

    const close = () => {
        if (busy) return;
        reset();
        onClose();
    };

    const submit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const name = String(data.get("name") ?? "").trim();
        const active = data.get("active") === "on";
        if (!avatar && !file) {
            setError("Elegí una imagen.");
            return;
        }
        setBusy(true);
        setError(null);
        try {
            if (avatar) {
                if (name !== avatar.name || active !== avatar.active) await updateAvatar(avatar.id, { name, active });
                if (file) await replaceAvatarImage(avatar.id, file);
                onDone(`Se guardaron los cambios de «${name}».`);
            } else {
                const created = await createAvatar(name, file!);
                onDone(`Se creó «${created.name}».`);
            }
            reset();
            onClose();
        } catch (err) {
            setError(describeError(err, avatar ? "guardar el avatar" : "crear el avatar"));
        } finally {
            setBusy(false);
        }
    };

    const shown = preview ?? avatar?.url;

    return (
        <dialog
            ref={ref}
            className="modal admin-form-dialog"
            aria-labelledby={titleId}
            onClose={close}
            onCancel={(e) => busy && e.preventDefault()}
        >
            <form key={`${formKey}-${avatar?.id ?? "new"}`} className="admin-form" onSubmit={submit}>
                <header className="admin-form__header">
                    <h2 id={titleId}>{avatar ? `Editar ${avatar.name}` : "Nuevo avatar"}</h2>
                    <Button variant="ghost" size="sm" iconOnly icon={<CloseIcon size={16} />} onClick={close} disabled={busy}>
                        Cerrar
                    </Button>
                </header>

                <div className="avatar-form__image">
                    <div className="avatar-form__preview">
                        {shown ? <img src={shown} alt="" /> : <ImageIcon size={24} />}
                    </div>
                    <div className="avatar-form__pick">
                        <label htmlFor={fileId} className="button button--secondary button--sm">
                            {shown ? "Reemplazar imagen" : "Elegir imagen"}
                        </label>
                        <input
                            id={fileId}
                            type="file"
                            accept={ACCEPT}
                            className="sr-only"
                            onChange={(e) => {
                                const next = e.target.files?.[0];
                                if (next) pick(next);
                                e.target.value = "";
                            }}
                        />
                        <small>{file ? `${file.name} · ${(file.size / 1024).toFixed(0)} KB` : "Cuadrada, se recorta en círculo. Se achica a 512×512."}</small>
                    </div>
                </div>

                <label>
                    <span>Nombre</span>
                    <input name="name" required maxLength={100} defaultValue={avatar?.name ?? ""} placeholder="Gorra azul" autoComplete="off" />
                    <small>Lo ven los administradores y es el texto alternativo para los jugadores.</small>
                </label>

                {avatar && (
                    <label className="avatar-form__check">
                        <input name="active" type="checkbox" defaultChecked={avatar.active} />
                        <span>Disponible para los jugadores</span>
                    </label>
                )}

                {error && <p className="admin-form__error" role="alert">{error}</p>}

                <footer>
                    <Button variant="ghost" onClick={close} disabled={busy}>Cancelar</Button>
                    <Button variant="primary" type="submit" disabled={busy}>
                        {busy ? "Guardando…" : avatar ? "Guardar" : "Crear"}
                    </Button>
                </footer>
            </form>
        </dialog>
    );
};
