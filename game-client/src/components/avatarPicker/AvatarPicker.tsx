import type { Avatar } from "@/types";
import "./avatarPicker.css";

export interface AvatarPickerProps {
    avatars: Avatar[];
    selectedId: number | null;
    onSelect: (avatar: Avatar) => void;
    disabled?: boolean;
    className?: string;
}

/**
 * Grilla de avatares predeterminados (los administra game-admin): el jugador elige uno, no sube
 * imágenes. Son botones de radio para que se recorran con teclado y mando.
 */
export const AvatarPicker = ({ avatars, selectedId, onSelect, disabled, className = "" }: AvatarPickerProps) => (
    <div className={["avatar-picker", className].filter(Boolean).join(" ")} role="radiogroup" aria-label="Avatar">
        {avatars.map((avatar) => {
            const selected = avatar.id === selectedId;
            return (
                <button
                    key={avatar.id}
                    type="button"
                    role="radio"
                    aria-checked={selected}
                    aria-label={avatar.name}
                    title={avatar.name}
                    className={`avatar-picker__option${selected ? " avatar-picker__option--selected" : ""}`}
                    disabled={disabled}
                    onClick={() => onSelect(avatar)}
                >
                    <img src={avatar.url} alt="" draggable={false} />
                </button>
            );
        })}
    </div>
);
