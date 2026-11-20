import { useState, type HTMLAttributes } from "react";
import "./gameIcon.css";

export type GameIconSize = "sm" | "md" | "lg";

export interface GameIconProps extends HTMLAttributes<HTMLSpanElement> {
    src?: string;
    /** Nombre del juego: texto alternativo y fuente de la inicial si la imagen falla. */
    name: string;
    size?: GameIconSize;
}

export const GameIcon = ({ src, name, size = "md", className = "", ...rest }: GameIconProps) => {
    // Se guarda la URL que falló para que un cambio de `src` vuelva a intentar.
    const [failedSrc, setFailedSrc] = useState<string | null>(null);
    const showImage = Boolean(src) && failedSrc !== src;

    const iconClassName = ["game-icon", `game-icon--${size}`, className].filter(Boolean).join(" ");

    return (
        <span className={iconClassName} {...rest}>
            {showImage ? (
                <img
                    className="game-icon-img"
                    src={src}
                    alt={name}
                    draggable={false}
                    onError={() => setFailedSrc(src ?? null)}
                />
            ) : (
                <span className="game-icon-fallback" role="img" aria-label={name}>
                    {name.trim().charAt(0).toUpperCase()}
                </span>
            )}
        </span>
    );
};
