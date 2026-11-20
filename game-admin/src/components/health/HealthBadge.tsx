import type { CrudHealth } from "@services";
import "./healthBadge.css";

const LABELS: Record<CrudHealth | "checking", { text: string; title: string }> = {
    checking: { text: "Verificando…", title: "Consultando GET /health de game-crud" },
    up: { text: "game-crud en línea", title: "game-crud y PostgreSQL responden" },
    "db-down": { text: "Base de datos caída", title: "game-crud responde, pero no llega a PostgreSQL" },
    down: { text: "game-crud sin conexión", title: "game-crud no responde" },
};

export const HealthBadge = ({ health }: { health: CrudHealth | null }) => {
    const state = health ?? "checking";
    return (
        <span className={`health-badge health-badge--${state}`} title={LABELS[state].title} role="status">
            <span className="health-badge__dot" aria-hidden="true" />
            {LABELS[state].text}
        </span>
    );
};
