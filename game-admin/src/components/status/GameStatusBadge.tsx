import type { GameStatus } from "@/types";
import { gameStatusLabel } from "./gameStatus.ts";
import "./gameStatusBadge.css";

export interface GameStatusBadgeProps {
    status: GameStatus;
    className?: string;
}

export const GameStatusBadge = ({ status, className = "" }: GameStatusBadgeProps) => (
    <span className={`game-status game-status--${status.toLowerCase().replace("_", "-")} ${className}`.trim()}>
        {gameStatusLabel(status)}
    </span>
);
