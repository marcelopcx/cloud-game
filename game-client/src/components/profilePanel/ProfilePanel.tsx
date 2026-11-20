import { useEffect, useRef, useState } from "react";
import { useAvatars } from "@hooks";
import { CatalogRequestError, saveSession, selectAvatar } from "@services";
import type { Avatar } from "@/types";
import { AvatarPicker } from "../avatarPicker/AvatarPicker.tsx";
import { UserAvatar } from "../userAvatar/UserAvatar.tsx";
import "../friendsPanel/friendsPanel.css";
import "./profilePanel.css";

export interface ProfilePanelProps {
    username: string;
    /** Ruta del avatar elegido; sin avatar el botón muestra `icon` y el panel la inicial. */
    avatar?: string | null;
    avatarId?: number | null;
    /** Token de la sesión: para cambiar el avatar. */
    token: string;
    /** Icono del botón (el mismo de usuario del header). */
    icon: string;
    onLogout: () => void;
}

// menu: datos y opciones · avatar: elegir otro avatar
type View = "menu" | "avatar";

/**
 * Botón de perfil del header con un desplegable: datos del jugador, cambiar avatar (de los
 * predeterminados), configuración (pendiente) y cerrar sesión. Reutiliza el aspecto del panel de amigos.
 */
export const ProfilePanel = ({ username, avatar, avatarId = null, token, icon, onLogout }: ProfilePanelProps) => {
    const [open, setOpen] = useState(false);
    const [view, setView] = useState<View>("menu");
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const rootRef = useRef<HTMLDivElement>(null);
    const { avatars, error: avatarsError } = useAvatars(open && view === "avatar");

    // Se cierra al tocar fuera o con Escape.
    useEffect(() => {
        if (!open) return;
        const onPointer = (event: PointerEvent) => {
            if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
        };
        const onKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") setOpen(false);
        };
        document.addEventListener("pointerdown", onPointer);
        document.addEventListener("keydown", onKey);
        return () => {
            document.removeEventListener("pointerdown", onPointer);
            document.removeEventListener("keydown", onKey);
        };
    }, [open]);

    const choose = async (chosen: Avatar) => {
        if (chosen.id === avatarId) {
            setView("menu");
            return;
        }
        setSaving(true);
        setError(null);
        try {
            const user = await selectAvatar(token, chosen.id);
            saveSession({ token, user });
            setView("menu");
        } catch (err) {
            setError(err instanceof CatalogRequestError && err.message === "BAD_REQUEST"
                ? "Ese avatar ya no está disponible."
                : "No se pudo cambiar el avatar.");
        } finally {
            setSaving(false);
        }
    };

    const label = `Perfil · ${username}`;

    return (
        <div className="friends" ref={rootRef}>
            <button
                type="button"
                className={`friends-button${avatar ? " profile-button--avatar" : ""}${open ? " friends-button--open" : ""}`}
                aria-label={label}
                title={label}
                aria-expanded={open}
                aria-haspopup="dialog"
                onClick={() => {
                    // Cada vez que se abre, arranca en el menú.
                    setView("menu");
                    setError(null);
                    setOpen((o) => !o);
                }}
            >
                {avatar ? <UserAvatar username={username} src={avatar} className="profile-button-avatar" /> : <img src={icon} alt="" className="profile-icon" />}
            </button>

            {open && (
                <div className="friends-panel profile-panel" role="dialog" aria-label="Perfil">
                    <div className="profile-info">
                        <UserAvatar username={username} src={avatar} className="friends-avatar profile-avatar" />
                        <span className="friends-playing">
                            <span className="friends-name">{username}</span>
                        </span>
                    </div>

                    {view === "menu" ? (
                        <ul className="friends-list profile-menu">
                            <li>
                                <button type="button" className="friends-row friends-row--button" onClick={() => setView("avatar")}>
                                    Cambiar avatar
                                </button>
                            </li>
                            <li>
                                {/* Placeholder: aún no hay pantalla de configuración. */}
                                <button type="button" className="friends-row friends-row--button">
                                    Configuración
                                </button>
                            </li>
                            <li>
                                <button
                                    type="button"
                                    className="friends-row friends-row--button profile-logout"
                                    onClick={() => {
                                        setOpen(false);
                                        onLogout();
                                    }}
                                >
                                    Cerrar sesión
                                </button>
                            </li>
                        </ul>
                    ) : (
                        <div className="profile-avatars">
                            <h3 className="friends-heading">Elige tu avatar</h3>
                            {avatars ? (
                                avatars.length ? (
                                    <AvatarPicker avatars={avatars} selectedId={avatarId} onSelect={(a) => void choose(a)} disabled={saving} />
                                ) : (
                                    <p className="friends-empty">No hay avatares disponibles.</p>
                                )
                            ) : (
                                <p className="friends-empty">{avatarsError ? "No se pudieron cargar los avatares." : "Cargando…"}</p>
                            )}
                            {error && <p className="profile-error" role="alert">{error}</p>}
                            <button type="button" className="friends-row friends-row--button profile-back" onClick={() => setView("menu")} disabled={saving}>
                                Volver
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};
