import type { Avatar, UserResponse } from '@/types';
import { getJson } from './catalog.ts';

/** Avatares activos, los únicos que puede elegir el jugador (los administra game-admin). */
export function listAvatars(signal?: AbortSignal): Promise<Avatar[]> {
    return getJson<Avatar[]>('/avatars', signal);
}

/** `PUT /catalog/auth/me/avatar`: cambia el avatar del jugador y devuelve el usuario actualizado. */
export function selectAvatar(token: string, avatarId: number, signal?: AbortSignal): Promise<UserResponse> {
    return getJson<UserResponse>('/auth/me/avatar', signal, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ avatarId }),
    });
}
