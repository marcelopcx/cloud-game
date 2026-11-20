import React from 'react';
import { CardGame, type CardGameProps } from '@components';
import './recentGamesLayout.css';


export type GameItem = Omit<CardGameProps, 'onClick'>;

export interface RecentGamesLayoutProps {
    title?: string;
    games: GameItem[];
    onGameClick?: (id: number) => void;
    className?: string;
}

export const RecentGamesLayout: React.FC<RecentGamesLayoutProps> = ({
                                                                        title = "Juegos Recientes",
                                                                        games = [],
                                                                        onGameClick,
                                                                        className = "",
                                                                    }) => {
    if (!games || games.length === 0) {
        return (
            <section className={`recent-games-wrapper empty ${className}`}>
                <h2 className="recent-games-title">{title}</h2>
                <p className="recent-games-empty">No hay juegos recientes para mostrar.</p>
            </section>
        );
    }

    return (
        <section className={`recent-games-wrapper ${className}`}>
            <h2 className="recent-games-title">{title}</h2>
            <div className="recent-cards-wrapper">
                {games.map((game) => (
                    <CardGame
                        key={game.id}
                        {...game}
                        onClick={onGameClick}
                    />
                ))}
            </div>
        </section>
    );
};