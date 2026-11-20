import type { ButtonHTMLAttributes } from "react";
import "./button.css";

export type ButtonVariant = "primary" | "transparent";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    label: string;
    variant?: ButtonVariant;
    icon?: string;
}

export const Button = ({
    label,
    variant = "primary",
    icon,
    type = "button",
    className = "",
    ...rest
}: ButtonProps) => {
    const buttonClassName = ["button", `button--${variant}`, className]
        .filter(Boolean)
        .join(" ");

    return (
        <button type={type} className={buttonClassName} {...rest}>
            <span className="button-label">{label}</span>
            {icon && <img src={icon} alt="" className="button-icon" aria-hidden="true" />}
        </button>
    );
};
