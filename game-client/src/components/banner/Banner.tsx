import type { CSSProperties, HTMLAttributes, ReactNode } from "react";
import "./banner.css";
import { useImageTint } from "./useImageTint.ts";

export type BannerVariant = "featured" | "highlight";

export interface BannerProps extends HTMLAttributes<HTMLElement> {
    backgroundImage: string;
    variant?: BannerVariant;
    header?: ReactNode;
    children?: ReactNode;
}

export const Banner = ({
    backgroundImage,
    variant = "featured",
    header,
    children,
    className = "",
    style,
    ...rest
}: BannerProps) => {
    // El degradado de la variante highlight toma el color dominante de la imagen.
    const tint = useImageTint(backgroundImage, variant === "highlight");

    const bannerClassName = ["banner", `banner--${variant}`, className]
        .filter(Boolean)
        .join(" ");

    // En highlight la imagen va como <img> para que el degradado siga su borde derecho real.
    const imageLayer = variant === "highlight";

    const bannerStyle: CSSProperties = {
        ...style,
        ...(!imageLayer && { backgroundImage: `url(${backgroundImage})` }),
        ...(tint && { "--banner-tint": tint }),
    } as CSSProperties;

    return (
        <section className={bannerClassName} style={bannerStyle} {...rest}>
            {imageLayer && <img className="banner-image" src={backgroundImage} alt="" aria-hidden="true" draggable={false} />}
            {header && <div className="banner-header">{header}</div>}
            <div className="banner-body">{children}</div>
        </section>
    );
};
