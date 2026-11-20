import "./userAvatar.css";

export interface UserAvatarProps {
    username: string;
    /** Ruta del avatar elegido; sin avatar se muestra la inicial. */
    src?: string | null;
    className?: string;
}

/** Avatar circular del jugador: la imagen elegida o, si no tiene, la inicial del username. */
export const UserAvatar = ({ username, src, className = "" }: UserAvatarProps) => (
    <span className={["user-avatar", className].filter(Boolean).join(" ")} aria-hidden="true">
        {src ? (
            <img className="user-avatar__img" src={src} alt="" draggable={false} />
        ) : (
            username.trim().charAt(0).toUpperCase()
        )}
    </span>
);
