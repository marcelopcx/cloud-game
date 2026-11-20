import { useEffect, useState } from "react";

// Lado de la miniatura que se analiza: alcanza para sacar el color dominante sin costo.
const SAMPLE_SIZE = 48;
// Luminosidad final del tinte: oscuro para que el texto blanco encima siga siendo legible.
const TINT_LIGHTNESS = 0.2;
const MAX_SATURATION = 0.75;

const rgbToHsl = (r: number, g: number, b: number): [number, number, number] => {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const l = (max + min) / 2;
    if (max === min) return [0, 0, l];

    const d = max - min;
    const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    const h = max === r ? (g - b) / d + (g < b ? 6 : 0) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    return [h / 6, s, l];
};

const hslToRgb = (h: number, s: number, l: number): [number, number, number] => {
    if (s === 0) return [l * 255, l * 255, l * 255];

    const hue = (p: number, q: number, t: number) => {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1 / 6) return p + (q - p) * 6 * t;
        if (t < 1 / 2) return q;
        if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
        return p;
    };
    const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
    const p = 2 * l - q;
    return [hue(p, q, h + 1 / 3) * 255, hue(p, q, h) * 255, hue(p, q, h - 1 / 3) * 255];
};

// Promedio de la imagen ponderado por saturación: los colores vivos pesan más que grises y negros,
// así el tinte toma el tono protagonista en lugar de un gris apagado.
const dominantTint = (image: HTMLImageElement): string | null => {
    const canvas = document.createElement("canvas");
    canvas.width = SAMPLE_SIZE;
    canvas.height = SAMPLE_SIZE;
    const context = canvas.getContext("2d", { willReadFrequently: true });
    if (!context) return null;

    context.drawImage(image, 0, 0, SAMPLE_SIZE, SAMPLE_SIZE);
    const { data } = context.getImageData(0, 0, SAMPLE_SIZE, SAMPLE_SIZE);

    let r = 0, g = 0, b = 0, total = 0;
    for (let i = 0; i < data.length; i += 4) {
        const [, s, l] = rgbToHsl(data[i], data[i + 1], data[i + 2]);
        const weight = s * (1 - Math.abs(2 * l - 1)) + 0.02;
        r += data[i] * weight;
        g += data[i + 1] * weight;
        b += data[i + 2] * weight;
        total += weight;
    }

    const [h, s] = rgbToHsl(r / total, g / total, b / total);
    const tint = hslToRgb(h, Math.min(s * 1.4, MAX_SATURATION), TINT_LIGHTNESS);
    return tint.map(Math.round).join(", ");
};

// Devuelve el color dominante de la imagen como "r, g, b" (para usar en rgba(var(--x), a)).
// Si la imagen no se puede leer (p. ej. un CDN sin CORS), devuelve null y el CSS usa su fallback.
export const useImageTint = (src: string, enabled = true): string | null => {
    // Se guarda junto a la imagen que lo originó para no mostrar el tinte viejo al cambiar de imagen.
    const [sample, setSample] = useState<{ src: string; tint: string | null } | null>(null);

    useEffect(() => {
        if (!enabled || !src) return;

        let cancelled = false;
        const image = new Image();
        image.crossOrigin = "anonymous";
        image.onload = () => {
            try {
                const tint = dominantTint(image);
                if (!cancelled) setSample({ src, tint });
            } catch {
                // Canvas contaminado por una imagen de otro origen: se queda el fallback.
            }
        };
        image.src = src;

        return () => {
            cancelled = true;
        };
    }, [src, enabled]);

    return enabled && sample?.src === src ? sample.tint : null;
};
