import type { Category, Game } from '@/types';
import { API, readError } from './games.ts';

export async function listCategories(signal?: AbortSignal): Promise<Category[]> {
  const res = await fetch(`${API}/categories`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Category[]>;
}

export async function listCategoryGames(id: number, signal?: AbortSignal): Promise<Game[]> {
  const res = await fetch(`${API}/categories/${id}/games`, { signal });
  if (!res.ok) throw await readError(res);
  return res.json() as Promise<Game[]>;
}
