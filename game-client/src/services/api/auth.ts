import type { UserResponse } from '@/types';
import { CATALOG } from './catalog.ts';

export class AuthError extends Error {
    /** 401: credenciales rechazadas; 409: correo o username en uso. Sin status: no hubo respuesta. */
    readonly status?: number;
    /** Código de error de game-crud (`INVALID_CREDENTIALS`, `CONFLICT`, …). */
    readonly code?: string;

    constructor(message: string, status?: number, code?: string) {
        super(message);
        this.name = 'AuthError';
        this.status = status;
        this.code = code;
    }
}

export interface LoginResponse {
    token: string;
    user: UserResponse;
}

async function post(path: string, body: unknown, signal?: AbortSignal): Promise<LoginResponse> {
    let res: Response;
    try {
        res = await fetch(`${CATALOG}/auth/${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
            signal,
        });
    } catch (err) {
        if (signal?.aborted) throw err;
        throw new AuthError('No se pudo conectar con el servidor');
    }
    if (res.ok) return res.json() as Promise<LoginResponse>;
    let message = `HTTP ${res.status}`;
    let code: string | undefined;
    try {
        const body = (await res.json()) as { error?: { code?: string; message?: string } };
        code = body.error?.code;
        message = body.error?.message ?? message;
    } catch {
        // Sin cuerpo JSON: respondió el proxy.
    }
    throw new AuthError(message, res.status, code);
}

/** `POST /catalog/auth/login`: valida correo y contraseña y devuelve la sesión. */
export function login(email: string, password: string, signal?: AbortSignal): Promise<LoginResponse> {
    return post('login', { email, password }, signal);
}

/** `POST /catalog/auth/register`: crea la cuenta y devuelve la sesión ya iniciada. */
export function register(
    email: string,
    password: string,
    username: string,
    avatarId: number | null,
    signal?: AbortSignal,
): Promise<LoginResponse> {
    return post('register', { email, password, username, avatarId }, signal);
}
