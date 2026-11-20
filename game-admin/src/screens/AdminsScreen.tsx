import { useEffect, useId, useState, type FormEvent } from "react";
import { Button, CloseIcon, ConfirmDialog, KeyIcon, PlusIcon, RefreshIcon, TrashIcon } from "@components";
import { useModalDialog } from "@hooks";
import { createAdmin, deleteAdmin, describeError, listAdmins, updateAdminPassword } from "@services";
import type { Admin } from "@/types";
import "./adminsScreen.css";

export interface AdminsScreenProps {
    onSaved: (message: string) => void;
}

const dateFormat = new Intl.DateTimeFormat("es", { dateStyle: "medium", timeStyle: "short" });

/**
 * Administradores: quienes entran a /admin/, /docs/ y /api-docs/ (nginx valida el login contra
 * game-crud). Alta, cambio de contraseña y baja; el último no se puede borrar.
 */
export const AdminsScreen = ({ onSaved }: AdminsScreenProps) => {
    const [version, setVersion] = useState(0);
    const refresh = () => setVersion((v) => v + 1);
    const [admins, setAdmins] = useState<Admin[] | null>(null);
    const [loadError, setLoadError] = useState<unknown>(null);

    const [creating, setCreating] = useState(false);
    const [changing, setChanging] = useState<Admin | null>(null);
    const [deleting, setDeleting] = useState<Admin | null>(null);
    const [deleteBusy, setDeleteBusy] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        listAdmins(controller.signal)
            .then((list) => {
                setAdmins(list);
                setLoadError(null);
            })
            .catch((error: Error) => {
                if (error.name !== "AbortError") setLoadError(error);
            });
        return () => controller.abort();
    }, [version]);

    const askDelete = (admin: Admin) => {
        setDeleteError(null);
        setDeleting(admin);
    };

    const confirmDelete = async () => {
        if (!deleting) return;
        setDeleteBusy(true);
        setDeleteError(null);
        try {
            await deleteAdmin(deleting.id);
            onSaved(`Se eliminó a «${deleting.username}».`);
            setDeleting(null);
            refresh();
        } catch (e) {
            setDeleteError(describeError(e, "eliminar el administrador"));
        } finally {
            setDeleteBusy(false);
        }
    };

    const onlyOne = admins?.length === 1;

    return (
        <>
            <section className="admin__hero">
                <div>
                    <p className="admin__eyebrow">Acceso</p>
                    <h1>Administradores</h1>
                    <p className="admin__subtitle">Quienes entran a este panel, a la documentación y a los docs de las APIs.</p>
                </div>
                <div className="admin__hero-actions">
                    <Button variant="secondary" iconOnly icon={<RefreshIcon size={16} />} onClick={refresh}>
                        Recargar
                    </Button>
                    <Button variant="primary" icon={<PlusIcon size={16} />} onClick={() => setCreating(true)}>
                        Nuevo administrador
                    </Button>
                </div>
            </section>

            {loadError ? (
                <div className="empty-state empty-state--error">
                    <h2>No se pudieron cargar los administradores</h2>
                    <p>{describeError(loadError, "cargar los administradores")}</p>
                    <Button variant="secondary" icon={<RefreshIcon size={16} />} onClick={refresh}>Reintentar</Button>
                </div>
            ) : (
                <ul className="admins-list" aria-busy={admins === null}>
                    {admins?.map((admin) => (
                        <li key={admin.id} className="admins-list__row">
                            <span className="admins-list__avatar" aria-hidden="true">
                                {admin.username.charAt(0).toUpperCase()}
                            </span>
                            <div className="admins-list__info">
                                <strong>{admin.username}</strong>
                                <small>Desde {dateFormat.format(new Date(admin.createdAt))}</small>
                            </div>
                            <div className="admins-list__actions">
                                <Button size="sm" variant="ghost" icon={<KeyIcon size={15} />} onClick={() => setChanging(admin)}>
                                    Cambiar contraseña
                                </Button>
                                <Button
                                    size="sm"
                                    variant="ghost"
                                    iconOnly
                                    icon={<TrashIcon size={15} />}
                                    disabled={onlyOne}
                                    onClick={() => askDelete(admin)}
                                >
                                    {onlyOne ? "Es el único administrador" : "Eliminar"}
                                </Button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}

            <AdminFormDialog
                open={creating}
                onClose={() => setCreating(false)}
                onDone={(message) => {
                    onSaved(message);
                    refresh();
                }}
            />
            <AdminFormDialog
                open={changing !== null}
                admin={changing}
                onClose={() => setChanging(null)}
                onDone={onSaved}
            />

            <ConfirmDialog
                open={deleting !== null}
                title="¿Eliminar este administrador?"
                confirmLabel="Eliminar"
                busy={deleteBusy}
                error={deleteError}
                onConfirm={confirmDelete}
                onCancel={() => !deleteBusy && setDeleting(null)}
            >
                <strong>{deleting?.username}</strong> ya no va a poder entrar al panel ni a la documentación.
            </ConfirmDialog>
        </>
    );
};

interface AdminFormDialogProps {
    open: boolean;
    /** Si se pasa, cambia la contraseña de ese administrador en vez de crear uno. */
    admin?: Admin | null;
    onClose: () => void;
    onDone: (message: string) => void;
}

const AdminFormDialog = ({ open, admin, onClose, onDone }: AdminFormDialogProps) => {
    const ref = useModalDialog(open);
    const titleId = useId();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    // Cambia la key del form para vaciarlo cada vez que se abre.
    const [formKey, setFormKey] = useState(0);

    const close = () => {
        if (busy) return;
        setError(null);
        setFormKey((k) => k + 1);
        onClose();
    };

    const submit = async (e: FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        const data = new FormData(e.currentTarget);
        const password = String(data.get("password") ?? "");
        if (password !== String(data.get("confirm") ?? "")) {
            setError("Las contraseñas no coinciden.");
            return;
        }
        setBusy(true);
        setError(null);
        try {
            if (admin) {
                await updateAdminPassword(admin.id, password);
                onDone(`Se cambió la contraseña de «${admin.username}».`);
            } else {
                const created = await createAdmin({ username: String(data.get("username") ?? "").trim(), password });
                onDone(`Se creó a «${created.username}».`);
            }
            setFormKey((k) => k + 1);
            onClose();
        } catch (err) {
            setError(describeError(err, admin ? "cambiar la contraseña" : "crear el administrador"));
        } finally {
            setBusy(false);
        }
    };

    return (
        <dialog
            ref={ref}
            className="modal admin-form-dialog"
            aria-labelledby={titleId}
            onClose={close}
            onCancel={(e) => busy && e.preventDefault()}
        >
            <form key={formKey} className="admin-form" onSubmit={submit}>
                <header className="admin-form__header">
                    <h2 id={titleId}>{admin ? `Contraseña de ${admin.username}` : "Nuevo administrador"}</h2>
                    <Button variant="ghost" size="sm" iconOnly icon={<CloseIcon size={16} />} onClick={close} disabled={busy}>
                        Cerrar
                    </Button>
                </header>

                {!admin && (
                    <label>
                        <span>Usuario</span>
                        <input
                            name="username"
                            required
                            autoComplete="off"
                            pattern="[A-Za-z0-9._\-]{3,50}"
                            placeholder="maria"
                            autoFocus
                        />
                        <small>De 3 a 50 letras, números, punto, guion o guion bajo.</small>
                    </label>
                )}
                <label>
                    <span>{admin ? "Nueva contraseña" : "Contraseña"}</span>
                    <input name="password" type="password" required minLength={8} maxLength={72} autoComplete="new-password" autoFocus={!!admin} />
                    <small>Mínimo 8 caracteres.</small>
                </label>
                <label>
                    <span>Repetir contraseña</span>
                    <input name="confirm" type="password" required minLength={8} maxLength={72} autoComplete="new-password" />
                </label>
                {admin && <p className="admin-form__hint">Si es tu propio usuario, el navegador te va a pedir la contraseña nueva.</p>}

                {error && <p className="admin-form__error" role="alert">{error}</p>}

                <footer>
                    <Button variant="ghost" onClick={close} disabled={busy}>Cancelar</Button>
                    <Button variant="primary" type="submit" disabled={busy}>
                        {busy ? "Guardando…" : admin ? "Cambiar contraseña" : "Crear"}
                    </Button>
                </footer>
            </form>
        </dialog>
    );
};
