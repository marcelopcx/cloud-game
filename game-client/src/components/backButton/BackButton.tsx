import type { ButtonHTMLAttributes } from "react";
import "./backButton.css";

export type BackButtonVariant = "default" | "ghost";

export interface BackButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, "children"> {
    label?: string;
    variant?: BackButtonVariant;
}

export const BackButton = ({
    label = "Volver",
    variant = "default",
    type = "button",
    className = "",
    ...rest
}: BackButtonProps) => (
    <button
        type={type}
        className={["back-button", `back-button--${variant}`, className].filter(Boolean).join(" ")}
        aria-label={label}
        title={label}
        {...rest}
    >
        <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true">
            <path
                fill="none"
                stroke="currentColor"
                strokeWidth="2.25"
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M15 5l-7 7 7 7"
            />
        </svg>
    </button>
);
