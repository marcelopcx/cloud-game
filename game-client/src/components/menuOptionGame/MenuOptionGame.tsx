import { useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { setGamepadMenuOpen } from '@/gamepad/sessionGate.ts';
import './menuOptionGame.css';

import gearIcon from '../../../assets/icons/gear.svg';
import gearBlueIcon from '../../../assets/icons/gear-blue.svg';
import gamepadIcon from '../../../assets/icons/gamepad.svg';
import gamepadBlueIcon from '../../../assets/icons/gamepad-blue.svg';
import metricsIcon from '../../../assets/icons/metrics.svg';
import metricsBlueIcon from '../../../assets/icons/metrics-blue.svg';
import mouseIcon from '../../../assets/icons/mouse.svg';
import mouseBlueIcon from '../../../assets/icons/mouse-blue.svg';
import rocketIcon from '../../../assets/icons/rocket.svg';
import rocketBlueIcon from '../../../assets/icons/rocket-blue.svg';

/** Opciones que maneja el menú; el efecto real de cada una lo aplica el integrador. */
export type MenuOptionGameAction = 'controls' | 'quality' | 'metrics' | 'mouse';

/** Estado de las cuatro opciones: true = activado. */
export type MenuOptionGameValues = Record<MenuOptionGameAction, boolean>;

export interface MenuOptionGameActionDetail {
    action: MenuOptionGameAction;
    /** Estado de la opción luego del cambio (true = activado). */
    enabled: boolean;
}

export interface MenuOptionGameProps {
    /** Estado controlado de las opciones; el dueño lo actualiza vía onAction. */
    values: MenuOptionGameValues;
    /** Notifica cada cambio de opción con el nuevo estado. */
    onAction?: (detail: MenuOptionGameActionDetail) => void;
    /** Oculto con el cursor quieto; abierto nunca se oculta (lo fija el integrador). */
    hidden?: boolean;
    /** Avisa al abrir o cerrar el menú. */
    onOpenChange?: (open: boolean) => void;
    /** Cursor encima del menú: el integrador no lo oculta mientras tanto. */
    onHoverChange?: (hover: boolean) => void;
}

interface MenuOptionRow {
    action: MenuOptionGameAction;
    icon: string;
    iconOn: string;
    label: string;
    active?: boolean;
}

/**
 * Menú flotante para la vista de juego: controles, modo calidad/rendimiento,
 * métricas de transmisión y encendido del mouse. La visibilidad de las opciones
 * es puramente presentacional (animación escalonada); el estado vive en el
 * componente integrador.
 */
export const MenuOptionGame = ({ values, onAction, hidden = false, onOpenChange, onHoverChange }: MenuOptionGameProps) => {
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);

    // Con el menú abierto el mando navega las opciones y al juego le llega en reposo.
    useEffect(() => {
        setGamepadMenuOpen(open);
        onOpenChange?.(open);
        return () => setGamepadMenuOpen(false);
    }, [open, onOpenChange]);

    // Cerrar con Escape o al hacer click fuera del menú.
    useEffect(() => {
        if (!open) return;

        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === 'Escape') setOpen(false);
        };
        const onPointerDown = (event: PointerEvent) => {
            if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
        };

        window.addEventListener('keydown', onKeyDown);
        window.addEventListener('pointerdown', onPointerDown);
        return () => {
            window.removeEventListener('keydown', onKeyDown);
            window.removeEventListener('pointerdown', onPointerDown);
        };
    }, [open]);

    const toggleAction = (action: MenuOptionGameAction) => {
        onAction?.({ action, enabled: !values[action] });
    };

    const rows: MenuOptionRow[] = [
        {
            action: 'controls',
            icon: gamepadIcon,
            iconOn: gamepadBlueIcon,
            label: 'Controles',
            active: values.controls,
        },
        {
            action: 'quality',
            icon: rocketIcon,
            iconOn: rocketBlueIcon,
            label: values.quality ? 'Calidad' : 'Rendimiento',
            active: values.quality,
        },
        {
            action: 'metrics',
            icon: metricsIcon,
            iconOn: metricsBlueIcon,
            label: 'Métricas',
            active: values.metrics,
        },
        {
            action: 'mouse',
            icon: mouseIcon,
            iconOn: mouseBlueIcon,
            label: values.mouse ? 'Mouse on' : 'Mouse off',
            active: values.mouse,
        },
    ];

    return (
        <div
            ref={rootRef}
            className={`menu-option-game${open ? ' is-open' : ''}${hidden && !open ? ' is-hidden' : ''}`}
            onMouseEnter={() => onHoverChange?.(true)}
            onMouseLeave={() => onHoverChange?.(false)}
        >
            <ul
                className={`menu-option-game__list${open ? ' is-open' : ''}`}
                aria-hidden={!open}
                inert={!open}
            >
                {rows.map((row, index) => (
                    <li
                        key={row.action}
                        className="menu-option-game__item"
                        style={{ '--menu-option-i': index } as CSSProperties}
                    >
                        <button
                            type="button"
                            className="menu-option-game__option"
                            aria-pressed={Boolean(row.active)}
                            onClick={() => toggleAction(row.action)}
                        >
                            <span className="menu-option-game__icon-box">
                                <img
                                    className="menu-option-game__icon menu-option-game__icon--off"
                                    src={row.icon}
                                    alt=""
                                    draggable={false}
                                />
                                <img
                                    className="menu-option-game__icon menu-option-game__icon--on"
                                    src={row.iconOn}
                                    alt=""
                                    draggable={false}
                                />
                            </span>
                            <span className="menu-option-game__label">{row.label}</span>
                        </button>
                    </li>
                ))}
            </ul>

            <button
                type="button"
                className="menu-option-game__option menu-option-game__toggle"
                aria-expanded={open}
                aria-label={open ? 'Cerrar opciones de video' : 'Abrir opciones de video'}
                onClick={() => setOpen((prev) => !prev)}
            >
                <span className="menu-option-game__icon-box">
                    <img
                        className="menu-option-game__icon menu-option-game__icon--off"
                        src={gearIcon}
                        alt=""
                        draggable={false}
                    />
                    <img
                        className="menu-option-game__icon menu-option-game__icon--on"
                        src={gearBlueIcon}
                        alt=""
                        draggable={false}
                    />
                </span>
                <span className="menu-option-game__label">Configuración</span>
            </button>
        </div>
    );
};
