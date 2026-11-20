import type { ButtonHTMLAttributes, ReactNode } from "react";
import "./button.css";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: "primary" | "secondary" | "ghost" | "danger";
    size?: "sm" | "md";
    icon?: ReactNode;
    /** Solo ícono: el texto (children) queda para lectores de pantalla y tooltip. */
    iconOnly?: boolean;
}

export const Button = ({
    variant = "secondary",
    size = "md",
    icon,
    iconOnly = false,
    type = "button",
    className = "",
    children,
    ...rest
}: ButtonProps) => (
    <button
        type={type}
        className={["button", `button--${variant}`, `button--${size}`, iconOnly && "button--icon", className]
            .filter(Boolean)
            .join(" ")}
        title={iconOnly && typeof children === "string" ? children : undefined}
        {...rest}
    >
        {icon}
        {iconOnly ? <span className="sr-only">{children}</span> : children}
    </button>
);
