import type { CreateGameRequest, Game, ImageChanges, ImageKind, UpdateGameRequest } from '@/types';

// game-crud: en desarrollo el proxy de Vite reenvía /api → :8096.
export const API = import.meta.env.VITE_CRUD_URL || '/api';

/** Máximo que acepta GET /games/search (GameService.SEARCH_MAX_LIMIT). */
export const SEARCH_MAX_LIMIT = 50;

export class GameRequestError extends Error {
  readonly status: number;
  /** Código de game-crud (p. ej. BAD_REQUEST); sin él, la respuesta no vino de game-crud. */
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = 'GameRequestError';
    this.status = status;
    this.code = code;
  }
}

export async function readError(res: Response): Promise<GameRequestError> {
  try {
    const body = (await res.json()) as { error?: { code?: string; message?: string } };
    return new GameRequestError(body.error?.message ?? `HTTP ${res.status}`, res.status, body.error?.code);
  } catch {
    return new GameRequestError(`HTTP ${res.status}`, res.status);
  }
}

/** Sin código de error (o fetch que ni llegó): respondió el proxy, no game-crud (apagado o caído). */
export function isUnreachable(e: unknown): boolean {
  return !(e instanceof GameRequestError) || (!e.code && e.status >= 502 && e.status <= 504);
}

/** Mensaje para el usuario: "No se pudo <action>: <motivo>". */
export function describeError(e: unknown, action: string): string {
  if (isUnreachable(e)) return 'No se pudo conectar con game-crud. ¿Está corriendo?';
  return `No se pudo ${action}: ${(e as Error).message}`;
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const res = await fetch(`${API}${path}`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<T>;
}

export function listGames(signal?: AbortSignal): Promise<Game[]> {
  return getJson('/games', signal);
}

export function searchGames(q: string, limit = SEARCH_MAX_LIMIT, signal?: AbortSignal): Promise<Game[]> {
  const params = new URLSearchParams({ q, limit: String(limit) });
  return getJson(`/games/search?${params}`, signal);
}

export function getGame(id: number, signal?: AbortSignal): Promise<Game> {
  return getJson(`/games/${id}`, signal);
}

export async function createGame(game: CreateGameRequest, signal?: AbortSignal): Promise<Game> {
  const res = await fetch(`${API}/games`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(game),
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Game>;
}

export async function updateGame(id: number, changes: UpdateGameRequest, signal?: AbortSignal): Promise<Game> {
  const res = await fetch(`${API}/games/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(changes),
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Game>;
}

export async function deleteGame(id: number, signal?: AbortSignal): Promise<void> {
  const res = await fetch(`${API}/games/${id}`, { method: 'DELETE', signal });
  if (!res.ok) throw await readError(res);
}

/** Sube (o reemplaza) una imagen: el cuerpo es el archivo; game-crud borra el anterior de ese tipo. */
export async function uploadGameImage(id: number, kind: ImageKind, file: File, signal?: AbortSignal): Promise<Game> {
  const res = await fetch(`${API}/games/${id}/images/${kind}`, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file,
    signal,
  });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Game>;
}

/** Quita una imagen opcional (el póster solo se reemplaza). */
export async function removeGameImage(id: number, kind: ImageKind, signal?: AbortSignal): Promise<Game> {
  const res = await fetch(`${API}/games/${id}/images/${kind}`, { method: 'DELETE', signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Game>;
}

/** Aplica las imágenes pendientes del formulario, una por una; devuelve el juego final (null si no había). */
export async function applyImageChanges(id: number, changes: ImageChanges, signal?: AbortSignal): Promise<Game | null> {
  let game: Game | null = null;
  for (const [kind, file] of Object.entries(changes) as [ImageKind, File | null][]) {
    game = file ? await uploadGameImage(id, kind, file, signal) : await removeGameImage(id, kind, signal);
  }
  return game;
}
