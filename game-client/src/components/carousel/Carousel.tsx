import {
    Children,
    useCallback,
    useEffect,
    useRef,
    useState,
    type CSSProperties,
    type HTMLAttributes,
    type ReactNode,
} from "react";
import "./carousel.css";

export interface CarouselProps extends HTMLAttributes<HTMLElement> {
    title?: string;
    itemsPerView?: number;
    maxItemWidth?: number;
    children: ReactNode;
}

export const Carousel = ({
    title,
    itemsPerView = 4,
    maxItemWidth = 260,
    children,
    className = "",
    style,
    ...rest
}: CarouselProps) => {
    const trackRef = useRef<HTMLUListElement>(null);
    const [canScrollPrev, setCanScrollPrev] = useState(false);
    const [canScrollNext, setCanScrollNext] = useState(false);

    const carouselClassName = ["carousel", className].filter(Boolean).join(" ");

    const carouselStyle = {
        ...style,
        "--carousel-items": itemsPerView,
        "--carousel-item-max": `${maxItemWidth}px`,
    } as CSSProperties;

    const updateScrollState = useCallback(() => {
        const track = trackRef.current;
        if (!track) return;

        setCanScrollPrev(track.scrollLeft > 1);
        setCanScrollNext(track.scrollLeft + track.clientWidth < track.scrollWidth - 1);
    }, []);

    useEffect(() => {
        const track = trackRef.current;
        if (!track) return;

        updateScrollState();

        const observer = new ResizeObserver(updateScrollState);
        observer.observe(track);
        track.addEventListener("scroll", updateScrollState, { passive: true });

        return () => {
            observer.disconnect();
            track.removeEventListener("scroll", updateScrollState);
        };
    }, [updateScrollState, children]);

    const scroll = (direction: 1 | -1) => {
        const track = trackRef.current;
        if (!track) return;

        track.scrollBy({ left: direction * track.clientWidth, behavior: "smooth" });
    };

    const showControls = canScrollPrev || canScrollNext;

    return (
        <section className={carouselClassName} style={carouselStyle} {...rest}>
            {(title || showControls) && (
                <div className="carousel-header">
                    {title && <h2 className="carousel-title">{title}</h2>}

                    {showControls && (
                        <div className="carousel-controls">
                            <button
                                type="button"
                                className="carousel-arrow carousel-arrow--prev"
                                aria-label="Anterior"
                                disabled={!canScrollPrev}
                                onClick={() => scroll(-1)}
                            />
                            <button
                                type="button"
                                className="carousel-arrow carousel-arrow--next"
                                aria-label="Siguiente"
                                disabled={!canScrollNext}
                                onClick={() => scroll(1)}
                            />
                        </div>
                    )}
                </div>
            )}

            <ul ref={trackRef} className="carousel-track" aria-label={title}>
                {Children.toArray(children).map((child, index) => (
                    <li key={index} className="carousel-item">
                        {child}
                    </li>
                ))}
            </ul>
        </section>
    );
};
