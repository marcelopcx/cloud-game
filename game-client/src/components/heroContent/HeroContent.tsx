import type { HTMLAttributes, ReactNode } from "react";
import "./heroContent.css";

export type HeroTitleSize = "default" | "large";

export interface HeroContentProps extends HTMLAttributes<HTMLElement> {
    titleSize?: HeroTitleSize;
    children: ReactNode;
}

export const HeroContent = ({
    titleSize = "default",
    children,
    className = "",
    ...rest
}: HeroContentProps) => {
    const heroClassName = ["hero-content", `hero-content--title-${titleSize}`, className]
        .filter(Boolean)
        .join(" ");

    return (
        <section className={heroClassName} {...rest}>
            {children}
        </section>
    );
};

export interface HeroActionsProps extends HTMLAttributes<HTMLDivElement> {
    children: ReactNode;
}

export const HeroActions = ({ children, className = "", ...rest }: HeroActionsProps) => {
    const actionsClassName = ["hero-content-actions", className].filter(Boolean).join(" ");

    return (
        <div className={actionsClassName} {...rest}>
            {children}
        </div>
    );
};
