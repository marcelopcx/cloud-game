import { useSyncExternalStore } from 'react';
import { currentSession, subscribeSession, type PlayerSession } from '@services';

/** Sesión del jugador (o null si es anónimo); se actualiza al iniciar o cerrar sesión. */
export const usePlayerSession = (): PlayerSession | null =>
    useSyncExternalStore(subscribeSession, currentSession, () => null);
