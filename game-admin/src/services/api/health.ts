import { API } from './games.ts';

/** up: todo bien; db-down: game-crud responde pero sin PostgreSQL; down: no responde. */
export type CrudHealth = 'up' | 'db-down' | 'down';

/** GET /health de game-crud (Actuator, con el chequeo de la base; 503 si algo está DOWN). */
export async function checkHealth(signal?: AbortSignal): Promise<CrudHealth> {
  try {
    const res = await fetch(`${API}/health`, { signal, cache: 'no-store' });
    const body = (await res.json()) as { status?: string; components?: { db?: { status?: string } } };
    if (body.status === 'UP') return 'up';
    return body.components?.db?.status === 'DOWN' ? 'db-down' : 'down';
  } catch (e) {
    if ((e as Error).name === 'AbortError') throw e;
    return 'down';
  }
}
