import { useState, type FormEvent, type ReactNode } from "react";
import { GameIcon } from "../gameIcon/GameIcon.tsx";
import "./header.css";

export interface SearchSuggestion {
    id: number | string;
    title: string;
    icon?: string;
    /** Etiqueta a la derecha del título (p. ej. «Próximamente»). */
    badge?: string;
}

export interface HeaderProps {
    logo: string;
    searchIcon: string;
    logoAlt?: string;
    logoHref?: string;
    searchPlaceholder?: string;
    className?: string;
    onSearch?: (query: string) => void;
    onSearchChange?: (query: string) => void;
    /** Opciones bajo el buscador: solo título e ícono. */
    suggestions?: SearchSuggestion[];
    /** Mientras es true, el desplegable muestra tres puntos de espera en lugar de las opciones. */
    searching?: boolean;
    onSelectSuggestion?: (suggestion: SearchSuggestion) => void;
    /** Icono de usuario (arriba a la derecha): se muestra solo si hay `userIcon` y `onUserClick`. */
    userIcon?: string;
    userLabel?: string;
    onUserClick?: () => void;
    /** Botones a la izquierda del de usuario (p. ej. el de amigos). */
    actions?: ReactNode;
}

export const Header = ({
    logo,
    searchIcon,
    logoAlt = "Logo",
    logoHref = "/",
    searchPlaceholder = "Busqueda",
    className = "",
    onSearch,
    onSearchChange,
    suggestions = [],
    searching = false,
    onSelectSuggestion,
    userIcon,
    userLabel = "Iniciar sesión",
    onUserClick,
    actions,
}: HeaderProps) => {
    const [query, setQuery] = useState("");
    const [focused, setFocused] = useState(false);
    const open = focused && query.trim() !== "";
    const showLoading = open && searching;
    const showSuggestions = open && !searching && suggestions.length > 0;

    const headerClassName = ["header", className].filter(Boolean).join(" ");

    const handleChange = (value: string) => {
        setQuery(value);
        onSearchChange?.(value);
    };

    const select = (suggestion: SearchSuggestion) => {
        setFocused(false);
        setQuery("");
        onSearchChange?.("");
        onSelectSuggestion?.(suggestion);
    };

    const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        onSearch?.(query.trim());
    };

    return (
        <header className={headerClassName}>
            <a href={logoHref} className="header-logo-link">
                <img src={logo} alt={logoAlt} className="header-logo" />
            </a>

            <form
                className="header-search"
                role="search"
                onSubmit={handleSubmit}
                onFocus={() => setFocused(true)}
                onBlur={(event) => {
                    if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false);
                }}
                onKeyDown={(event) => {
                    if (event.key === "Escape") setFocused(false);
                }}
            >
                <button type="submit" className="header-search-button" aria-label="Buscar">
                    <img src={searchIcon} alt="" className="header-search-icon" />
                </button>
                <input
                    type="search"
                    className="header-search-input"
                    placeholder={searchPlaceholder}
                    aria-label={searchPlaceholder}
                    value={query}
                    onChange={(event) => handleChange(event.target.value)}
                    autoComplete="off"
                />

                {showLoading && (
                    <div className="header-suggestions header-search-loading" role="status" aria-label="Buscando">
                        <span className="header-search-dot" />
                        <span className="header-search-dot" />
                        <span className="header-search-dot" />
                    </div>
                )}

                {showSuggestions && (
                    <ul className="header-suggestions">
                        {suggestions.map((suggestion) => (
                            <li key={suggestion.id}>
                                <button type="button" className="header-suggestion" onClick={() => select(suggestion)}>
                                    <GameIcon src={suggestion.icon} name={suggestion.title} size="sm" />
                                    <span className="header-suggestion-title">{suggestion.title}</span>
                                    {suggestion.badge && <span className="header-suggestion-badge">{suggestion.badge}</span>}
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </form>

            {(actions || (userIcon && onUserClick)) && (
                <div className="header-actions">
                    {actions}
                    {userIcon && onUserClick && (
                        <button type="button" className="header-user" aria-label={userLabel} title={userLabel} onClick={onUserClick}>
                            <img src={userIcon} alt="" className="header-user-icon" />
                        </button>
                    )}
                </div>
            )}
        </header>
    );
};
