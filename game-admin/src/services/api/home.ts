import type { HomeResponse, UpdateHomeRequest } from '@/types';
import { API, readError } from './games.ts';

export async function getHome(signal?: AbortSignal): Promise<HomeResponse> {
  const res = await fetch(`${API}/home`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<HomeResponse>;
}

export async function saveHome(home: UpdateHomeRequest, signal?: AbortSignal): Promise<HomeResponse> {
  const res = await fetch(`${API}/home`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(home),
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<HomeResponse>;
}
