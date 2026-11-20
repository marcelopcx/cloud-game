import { useId } from "react";
import { Button } from "../button/Button.tsx";
import { ImageIcon } from "../icons/Icons.tsx";

/** Lo que acepta game-crud (detecta el formato por el contenido). */
const ACCEPT = "image/png,image/jpeg,image/webp,image/gif,image/x-icon,image/vnd.microsoft.icon,.ico";

export interface ImageFieldProps {
    label: string;
    hint: string;
    /** Proporción de la miniatura (la del tipo de imagen en SteamGridDB). */
    ratio: string;
    required?: boolean;
    /** Imagen que se muestra: la guardada o la del archivo elegido. */
    src?: string;
    /** Hay una imagen guardada en game-crud. */
    saved: boolean;
    /** Cambio pendiente: archivo nuevo (File), quitar (null) o nada (undefined). */
    change: File | null | undefined;
    onChange: (change: File | null | undefined) => void;
}

/**
 * Imagen del juego: se elige un archivo local y se sube al guardar. Reemplazar pisa la imagen
 * de ese tipo en el servidor; las opcionales se pueden quitar.
 */
export const ImageField = ({ label, hint, ratio, required, src, saved, change, onChange }: ImageFieldProps) => {
    const id = useId();
    const status = change instanceof File
        ? `${change.name} · ${(change.size / 1024).toFixed(0)} KB (se sube al guardar)`
        : change === null
            ? "Se quita al guardar."
            : hint;

    return (
        <div className="image-field">
            <div className="image-field__thumb" style={{ aspectRatio: ratio }}>
                {src ? <img src={src} alt="" /> : <ImageIcon size={20} />}
            </div>
            <div className="image-field__input">
                <label htmlFor={id}>
                    {label} {required && <span className="game-form__required">*</span>}
                </label>
                <div className="image-field__actions">
                    <label className="button button--secondary button--sm image-field__pick">
                        {saved || change instanceof File ? "Reemplazar" : "Elegir archivo"}
                        <input
                            id={id}
                            type="file"
                            accept={ACCEPT}
                            className="sr-only"
                            onChange={(e) => {
                                const file = e.target.files?.[0];
                                if (file) onChange(file);
                                e.target.value = "";
                            }}
                        />
                    </label>
                    {change !== undefined ? (
                        <Button size="sm" variant="ghost" onClick={() => onChange(undefined)}>
                            Deshacer
                        </Button>
                    ) : (
                        saved && !required && (
                            <Button size="sm" variant="ghost" onClick={() => onChange(null)}>
                                Quitar
                            </Button>
                        )
                    )}
                </div>
                <small className={change !== undefined ? "image-field__pending" : undefined}>{status}</small>
            </div>
        </div>
    );
};
