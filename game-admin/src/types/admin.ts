/** Fila de ADMIN, como la devuelve GET /admins (sin contraseña). */
export interface Admin {
    id: number;
    username: string;
    createdAt: string;
}

/** Cuerpo de POST /admins. */
export interface CreateAdminRequest {
    username: string;
    password: string;
}
