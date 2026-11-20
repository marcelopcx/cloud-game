import type { Avatar, UpdateAvatarRequest } from '@/types';
import { API, readError } from './games.ts';

/** Todos, también los inactivos. */
export async function listAvatars(signal?: AbortSignal): Promise<Avatar[]> {
  const res = await fetch(`${API}/avatars?all=true`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Avatar[]>;
}

/** El cuerpo es la imagen; game-crud la achica a 512×512 y la pasa a WebP. */
export async function createAvatar(name: string, file: File, signal?: AbortSignal): Promise<Avatar> {
  const res = await fetch(`${API}/avatars?${new URLSearchParams({ name })}`, {
    method: 'POST',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file,
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Avatar>;
}

export async function updateAvatar(id: number, changes: UpdateAvatarRequest, signal?: AbortSignal): Promise<Avatar> {
  const res = await fetch(`${API}/avatars/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(changes),
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Avatar>;
}

/** Quien tiene este avatar pasa a ver la imagen nueva. */
export async function replaceAvatarImage(id: number, file: File, signal?: AbortSignal): Promise<Avatar> {
  const res = await fetch(`${API}/avatars/${id}/image`, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file,
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Avatar>;
}

/** Los jugadores que lo tenían quedan sin avatar hasta que elijan otro. */
export async function deleteAvatar(id: number, signal?: AbortSignal): Promise<void> {
  const res = await fetch(`${API}/avatars/${id}`, { method: 'DELETE', signal });
  if (!res.ok) throw await readError(res);
}
