import { useState } from "react";
import { LottieLight } from "lottie-react";
import { usePrefersReducedMotion } from "@hooks";
import loadingAnimation from "../../../assets/animations/loading-gamecloud.json";
import "./loadingAnimation.css";

/**
 * Pantalla de carga a pantalla completa (por encima de la barra y los controles)
 * con la animación Lottie en bucle. Mientras el juego no está listo se repite
 * indefinidamente; en cuanto llega `ready` se corta el bucle para que la
 * animación termine sola el ciclo que esté en marcha y sólo entonces se oculta,
 * nunca a mitad de reproducción. Si `ready` vuelve a ser falso (p. ej. reintentar)
 * la animación reaparece y hay que ver otro ciclo entero.
 *
 * Con `prefers-reduced-motion` la animación no se reproduce (y entonces nunca
 * terminaría por sí sola), así que se quita en cuanto el juego está listo, sin
 * esperas. Lo mismo si el motor falla al cargar: mejor entrar al juego sin la
 * animación que quedarse detrás de ella para siempre.
 */
export const LoadingAnimation = ({ ready }: { ready: boolean }) => {
    const reducedMotion = usePrefersReducedMotion();
    // `ready` ya llegó: se cortó el bucle y se espera a que termine el ciclo.
    const [waiting, setWaiting] = useState(false);
    const [finished, setFinished] = useState(false);

    // Ajuste de estado durante el render (sin efectos): al reintentar hay que
    // volver a esperar un ciclo completo antes de poder ocultarse.
    if (ready !== waiting) {
        setWaiting(ready);
        if (!ready) setFinished(false);
    }

    const outOfMotion = reducedMotion || finished;
    if (ready && outOfMotion) return null;

    return (
        <div className="loading-animation" role="status" aria-label="Cargando">
            <LottieLight
                className="loading-animation__player"
                src={loadingAnimation}
                loop={!ready}
                autoplay={!reducedMotion}
                subscriptions={{
                    complete: () => setFinished(true),
                    // Si la animación ni siquiera cargó no hay ciclo que esperar.
                    error: () => {
                        if (ready) setFinished(true);
                    },
                }}
            />
        </div>
    );
};
