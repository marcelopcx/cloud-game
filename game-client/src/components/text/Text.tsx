import type { HTMLAttributes, ReactNode } from "react";
import "./text.css";

export type TextTag = "h1" | "h2" | "h3" | "h4" | "p" | "span" | "strong" | "em" | "small";

export interface TextProps extends HTMLAttributes<HTMLElement> {
    as?: TextTag;
    children: ReactNode;
}

export const Text = ({ as: Tag = "p", children, className = "", ...rest }: TextProps) => {
    const textClassName = ["text", `text--${Tag}`, className].filter(Boolean).join(" ");

    return (
        <Tag className={textClassName} {...rest}>
            {children}
        </Tag>
    );
};
