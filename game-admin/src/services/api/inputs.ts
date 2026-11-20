import type { GameInput, InputOptions } from '@/types';
import { API, readError } from './games.ts';

export async function getGameInputs(id: number, signal?: AbortSignal): Promise<GameInput[]> {
  const res = await fetch(`${API}/games/${id}/inputs`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<GameInput[]>;
}

/** Reemplaza todos los controles del juego; devuelve lo guardado (en el orden de las acciones). */
export async function replaceGameInputs(id: number, inputs: GameInput[], signal?: AbortSignal): Promise<GameInput[]> {
  const res = await fetch(`${API}/games/${id}/inputs`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(inputs),
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<GameInput[]>;
}

export async function getInputOptions(signal?: AbortSignal): Promise<InputOptions> {
  const res = await fetch(`${API}/inputs`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<InputOptions>;
}
