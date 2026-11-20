import "./gridCard.css";

export const GridCard = ({ children, className, ...props }: React.HTMLAttributes<HTMLDivElement>) => {
    return (
        <div className={`grid-card ${className || ''}`} {...props}>
            {children}
        </div>
    );
};