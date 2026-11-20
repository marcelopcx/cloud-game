import { useEffect, useRef, useState } from 'react';
import { ContainerLoader, GamePlayer, LoadingAnimation } from '@components';
import { useGameInputs, usePlayerSession, useStation, useStationSession, type GameInputs } from '@hooks';

export interface PlayerScreenProps {
    gameId: string;
    title: string;
    /** Ícono de la barra (images.icon del catálogo). */
    icon?: string;
    onExit: () => void;
}

type StationPlayerProps = PlayerScreenProps & {
    onSettled: (settled: boolean) => void;
    controls: GameInputs;
    stationSessionId?: string;
    saveGrant?: string;
};

/**
 * Con Game Dispatcher primero pide una estación (en cola o reservada para este
 * jugador) y espera a que se la asignen; al salir (o cerrar la pestaña) libera
 * la sesión y la estación vuelve al pool. Sin dispatcher va directo a la
 * estación fija. Los controles del juego se piden en paralelo.
 */
export const PlayerScreen = ({ gameId, title, icon, onExit }: PlayerScreenProps) => {
    const session = useStationSession(gameId);
    const player = usePlayerSession();
    const controls = useGameInputs(gameId);
    const [settled, setSettled] = useState(false);

    // Una sola animación para toda la carga (cola/estación → video), sin reiniciarse entre fases.
    const loaded = session.phase === 'error' || (session.phase === 'ready' && settled);

    return (
        <>
            {session.phase !== 'ready' ? (
                <ContainerLoader
                    title={title}
                    phase={session.phase}
                    error={session.error}
                    onBack={onExit}
                    onRetry={session.retry}
                    notice={player ? undefined : 'Sin sesión: tu progreso no se guarda al salir'}
                />
            ) : (
                <StationPlayer
                    key={session.session?.sessionId ?? gameId}
                    gameId={gameId}
                    title={title}
                    icon={icon}
                    controls={controls}
                    stationSessionId={session.session?.station?.stationSessionId}
                    saveGrant={session.session?.saveGrant ?? undefined}
                    onSettled={setSettled}
                    onExit={onExit}
                />
            )}
            <LoadingAnimation ready={loaded} />
        </>
    );
};

/**
 * Al montarse pide la partida (prepare → READY → launch; con dispatcher la
 * estación ya viene en READY y va directo a launch) y muestra el stream.
 * Volver hace POST /stop y regresa al home.
 */
const StationPlayer = ({ gameId, title, icon, controls, stationSessionId, saveGrant, onSettled, onExit }: StationPlayerProps) => {
    const station = useStation(gameId, stationSessionId, saveGrant);
    const { play, stop } = station;
    const startedRef = useRef(false);

    useEffect(() => {
        // StrictMode monta dos veces: un solo prepare por pantalla.
        if (startedRef.current) return;
        startedRef.current = true;
        void play(gameId);
    }, [play, gameId]);

    const exit = async () => {
        await stop();
        onExit();
    };

    return (
        <GamePlayer
            title={title}
            controls={controls}
            icon={icon}
            station={station}
            onBack={() => void exit()}
            onRetry={() => void play(gameId)}
            onSettled={onSettled}
        />
    );
};
