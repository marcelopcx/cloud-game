/** Fila de GET/PUT /games/{id}/inputs: una acción con su tecla y su botón (null = sin ese control). */
export interface GameInput {
    action: string;
    keyboard: string | null;
    controller: string | null;
}

/** GET /inputs: acciones, teclas (y botones del ratón) y botones del mando, en el orden de los seeds. */
export interface InputOptions {
    actions: string[];
    keyboard: string[];
    controller: string[];
}
