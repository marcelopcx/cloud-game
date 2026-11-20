import "./cardGame.css";
import starIcon from "../../../assets/icons/star.svg";
import { PLATFORM_ICONS, type GamePlatform } from "./platformIcons.ts";

export type CardGameVariant = "default" | "compact";

export type { GamePlatform };

export interface CardGameProps {
    id: number;
    title: string;
    image: string;
    rating: number;
    gender?: string[];
    platforms?: GamePlatform[];
    variant?: CardGameVariant;
    /** Visible en el catálogo pero todavía no jugable: imagen atenuada y cinta «Próximamente». */
    comingSoon?: boolean;
    className?: string;
    onClick?: (id: number) => void;
}

export const CardGame = ({
    id,
    title,
    image,
    rating,
    gender = [],
    platforms = [],
    variant = "default",
    comingSoon = false,
    className = "",
    onClick,
}: CardGameProps) => {

    const cardClassName = [
        "card-game",
        `card-game--${variant}`,
        comingSoon && "card-game--coming-soon",
        className,
    ]
        .filter(Boolean)
        .join(" ");

    const handleClick = () => {
        onClick?.(id);
    };

    return (
        <article
            className={cardClassName}
            onClick={handleClick}
            role={onClick ? "button" : undefined}
            tabIndex={onClick ? 0 : undefined}
            onKeyDown={(event) => {
                if (!onClick) return;

                if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onClick(id);
                }
            }}
        >
            <div className="card-img-wrapper">
                <img
                    className="card-img"
                    src={image}
                    alt={title}
                    loading="lazy"
                />
                {comingSoon && <span className="card-coming-soon">Próximamente</span>}
            </div>

            {variant === "default" && (
                <>
                    <div className="card-info-wrapper">
                        <img
                            className="card-star"
                            src={starIcon}
                            alt=""
                            aria-hidden="true"
                        />

                        <span className="card-rating">
                            {rating.toFixed(1)}
                        </span>
                    </div>

                    {gender.length > 0 && (
                        <div className="card-gender">
                            <span className="card-gender-text">
                                {gender[0]}
                            </span>
                        </div>
                    )}
                </>
            )}

            {variant === "compact" && (
                <div className="card-info-wrapper card-info-wrapper--compact">
                    <ul className="card-platforms" aria-label="Plataformas compatibles">
                        {platforms.map((platform) => (
                            <li key={platform} className="card-platform">
                                <img
                                    className={`card-platform-icon card-platform-icon--${platform}`}
                                    src={PLATFORM_ICONS[platform].icon}
                                    alt={PLATFORM_ICONS[platform].label}
                                    title={PLATFORM_ICONS[platform].label}
                                />
                            </li>
                        ))}
                    </ul>

                    <div className="card-rating-wrapper">
                        <img
                            className="card-star"
                            src={starIcon}
                            alt=""
                            aria-hidden="true"
                        />

                        <span className="card-rating">
                            {rating.toFixed(1)}
                        </span>
                    </div>
                </div>
            )}
        </article>
    );
};
