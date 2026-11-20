import type { UserResponse } from '@/types';
import { CatalogRequestError, getJson } from './catalog.ts';

/** Usuario registrado con ese correo, o null si es la primera vez que entra. */
export async function findUserByEmail(email: string, signal?: AbortSignal): Promise<UserResponse | null> {
    try {
        return await getJson<UserResponse>(`/users/by-email?email=${encodeURIComponent(email)}`, signal);
    } catch (err) {
        if (err instanceof CatalogRequestError && err.message === 'NOT_FOUND') return null;
        throw err;
    }
}

/** Registra el correo con el username elegido. Lanza CatalogRequestError('CONFLICT') si ya existe. */
export function createUser(email: string, username: string, signal?: AbortSignal): Promise<UserResponse> {
    return getJson<UserResponse>('/users', signal, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, username }),
    });
}
