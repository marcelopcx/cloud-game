import type { GameStatus } from "@/types";

/** Estados en el orden del formulario, con lo que significa cada uno para game-client. */
export const GAME_STATUS_OPTIONS: { value: GameStatus; label: string; hint: string }[] = [
    { value: "TEST", label: "De prueba", hint: "Solo aparece en game-client en desarrollo." },
    { value: "ACTIVE", label: "Activo", hint: "Visible y jugable para todos los jugadores." },
    { value: "COMING_SOON", label: "Próximamente", hint: "Se muestra en el catálogo, pero no se puede jugar." },
    { value: "DISABLED", label: "Desactivado", hint: "Oculto para los jugadores, también en el juego aleatorio." },
];

export const gameStatusLabel = (status: GameStatus) =>
    GAME_STATUS_OPTIONS.find((o) => o.value === status)?.label ?? status;
