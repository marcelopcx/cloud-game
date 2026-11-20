import { useCallback, useLayoutEffect, useRef, useState } from 'react';
import { HomeScreen, LoginScreen, PlayerScreen } from '@screens';
import { SplashAnimation } from '@components';
import { usePageAssetsReady } from '@hooks';
import { GamepadNav } from '@/gamepad/GamepadNav.tsx';
import { enterDocumentFullscreen, exitFullscreen } from '@services';
import introAnimation from '../../assets/animations/intro-gamecloud.json';
import './App.css';

type View =
    | { name: 'home' }
    | { name: 'login' }
    | { name: 'player'; gameId: string; title: string; icon?: string };

function App() {
    const [view, setView] = useState<View>({ name: 'home' });
    // Cambiar la key vuelve a montar el splash, que reproduce la intro otra vez.
    const [splashRun, setSplashRun] = useState(0);
    const contentRef = useRef<HTMLDivElement>(null);

    // El sitio se monta detrás del splash. El splash no se puede saltar hasta que el home tiene sus
    // datos y todas sus imágenes (incluidas las lazy, que el hook pasa a eager) están descargadas.
    const [homeDataReady, setHomeDataReady] = useState(false);
    const handleHomeDataReady = useCallback(() => setHomeDataReady(true), []);
    const pageReady = usePageAssetsReady(contentRef, homeDataReady || view.name !== 'home');

    // El juego ocupa todo el viewport: se bloquea el scroll del documento (y con él la barra) desde
    // antes del primer pintado, igual que hace el splash con splash-locked.
    useLayoutEffect(() => {
        if (view.name !== 'player') return;
        const root = document.documentElement;
        root.classList.add('player-locked');
        return () => root.classList.remove('player-locked');
    }, [view.name]);

    return (
        <GamepadNav>
            <div className="app-container">
                <SplashAnimation key={splashRun} animationData={introAnimation} ready={pageReady} />
                <div ref={contentRef} style={{ display: 'contents' }}>
                    {view.name === 'player' ? (
                        <PlayerScreen
                            gameId={view.gameId}
                            title={view.title}
                            icon={view.icon}
                            onExit={() => {
                                void exitFullscreen();
                                setView({ name: 'home' });
                            }}
                        />
                    ) : view.name === 'login' ? (
                        <LoginScreen
                            onBack={() => setView({ name: 'home' })}
                            onLoggedIn={() => {
                                setSplashRun((run) => run + 1);
                                setView({ name: 'home' });
                            }}
                        />
                ) : (
                        <HomeScreen
                            onLogin={() => setView({ name: 'login' })}
                            onDataReady={handleHomeDataReady}
                            onPlay={(gameId, title, icon) => {
                                void enterDocumentFullscreen();
                                setView({ name: 'player', gameId, title, icon });
                            }}
                        />
                    )}
                </div>
            </div>
        </GamepadNav>
    );
}

export default App;
