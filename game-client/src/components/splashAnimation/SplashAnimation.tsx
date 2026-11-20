import {
    useCallback,
    useEffect,
    useLayoutEffect,
    useRef,
    useState,
    type TransitionEvent as ReactTransitionEvent,
} from "react";
import { LottieLight } from "lottie-react";
import { usePrefersReducedMotion } from "@hooks";
import "./splashAnimation.css";

export interface SplashAnimationProps {
    animationData: object;
    /**
     * La página detrás ya cargó. Mientras sea false el splash no se puede saltar y, si la animación
     * termina antes, queda en el último cuadro hasta que cargue. Con true, un clic lo cierra.
     */
    ready?: boolean;
    fadeOutMs?: number;
    onFinish?: () => void;
}

export const SplashAnimation = ({ animationData, ready = true, fadeOutMs = 500, onFinish }: SplashAnimationProps) => {
    const reducedMotion = usePrefersReducedMotion();
    const [phase, setPhase] = useState<"playing" | "leaving" | "done">("playing");
    const finishedRef = useRef(false);
    const [animationEnded, setAnimationEnded] = useState(false);

    const visible = phase !== "done";
    // Con movimiento reducido la animación no llega a terminar nunca, pero para cuando ya no
    // se está moviendo sirve igual: avisa de que sigue cargando y permite salir en cuanto la
    // página esté lista.
    const idle = animationEnded || reducedMotion;

    const finish = useCallback(() => {
        if (finishedRef.current) return;
        finishedRef.current = true;
        setPhase("done");
        onFinish?.();
    }, [onFinish]);
    useLayoutEffect(() => {
        if (!visible) return;
        const root = document.documentElement;
        root.classList.add("splash-locked");
        return () => root.classList.remove("splash-locked");
    }, [visible]);

    // Sale por clic (con la página lista) o sola, cuando terminó la animación y la página ya cargó.
    const leaving = phase === "leaving" || (phase === "playing" && idle && ready);

    useEffect(() => {
        if (!leaving) return;
        const timer = window.setTimeout(finish, fadeOutMs + 120);
        return () => window.clearTimeout(timer);
    }, [leaving, fadeOutMs, finish]);

    if (!visible) return null;

    const handleTransitionEnd = (event: ReactTransitionEvent<HTMLDivElement>) => {
        if (event.target !== event.currentTarget || event.propertyName !== "opacity") return;
        finish();
    };

    return (
        <div
            className={[
                "splash-animation",
                !ready && "splash-animation--loading",
                leaving && "splash-animation--leaving",
            ].filter(Boolean).join(" ")}
            style={{ transitionDuration: `${fadeOutMs}ms` }}
            onTransitionEnd={handleTransitionEnd}
            onClick={() => ready && setPhase("leaving")}
            aria-hidden="true"
        >
            <LottieLight
                className="splash-animation__player"
                src={animationData}
                loop={false}
                autoplay={!reducedMotion}
                subscriptions={{ complete: () => setAnimationEnded(true) }}
            />
            {idle && !ready && <p className="splash-animation__status">Cargando…</p>}
        </div>
    );
};
