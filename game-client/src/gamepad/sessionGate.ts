/**
 * Compuerta entre el mando del juego y el de la UI.
 *
 * `webrtc/session.ts` es el único que sabe si el pump de input está vivo (arranca
 * con el DataChannel y se destruye al cerrar el peer). Como vive por debajo de
 * `app/App.tsx`, publica el estado acá y la navegación se entera sola.
 *
 * `MenuOptionGame` publica si el menú de opciones del player está abierto: mientras
 * lo está, el mando vuelve a la UI aunque haya partida, y al juego le llega en reposo.
 */

/**
 * Hay una partida con input en curso (el pump de `session.ts` está vivo): la UI no
 * toca el mando, aunque el juego no lo use. Se suelta al cerrar o al caerse la sesión.
 */
let claimedBySession = false;

/** El menú de opciones del player está abierto: el mando navega el menú. */
let menuOpen = false;

const listeners = new Set<() => void>();

function notify(): void {
    listeners.forEach((listener) => listener());
}

export function isGamepadClaimedBySession(): boolean {
    return claimedBySession;
}

export function isGamepadMenuOpen(): boolean {
    return menuOpen;
}

/** El mando es del juego: hay partida y el menú de opciones está cerrado. */
export function isGamepadOwnedByGame(): boolean {
    return claimedBySession && !menuOpen;
}

export function subscribeGamepadClaim(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
}

export function setGamepadClaimedBySession(claimed: boolean): void {
    if (claimedBySession === claimed) return;
    claimedBySession = claimed;
    notify();
}

export function setGamepadMenuOpen(open: boolean): void {
    if (menuOpen === open) return;
    menuOpen = open;
    notify();
}
