import type { Admin, CreateAdminRequest } from '@/types';
import { API, readError } from './games.ts';

export async function listAdmins(signal?: AbortSignal): Promise<Admin[]> {
  const res = await fetch(`${API}/admins`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Admin[]>;
}

export async function createAdmin(admin: CreateAdminRequest, signal?: AbortSignal): Promise<Admin> {
  const res = await fetch(`${API}/admins`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(admin),
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Admin>;
}

export async function updateAdminPassword(id: number, password: string, signal?: AbortSignal): Promise<void> {
  const res = await fetch(`${API}/admins/${id}/password`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
    signal,
  });
  if (!res.ok) throw await readError(res);
}

/** 409 si es el último: el panel quedaría sin nadie que pueda entrar. */
export async function deleteAdmin(id: number, signal?: AbortSignal): Promise<void> {
  const res = await fetch(`${API}/admins/${id}`, { method: 'DELETE', signal });
  if (!res.ok) throw await readError(res);
}
