import './progressBar.css';

export const ProgressBar = ({ progress }: { progress: number }) => (
    <div className="progress-bar">
        <div className="progress-bar__header">
            <span className="progress-bar__label">Carga del Sistema</span>
            <span className="progress-bar__value">{progress}%</span>
        </div>
        <div className="progress-bar__track">
            <div className="progress-bar__fill" style={{ width: `${progress}%` }} />
        </div>
    </div>
);
