package api.model;

/** Tipos de sección del home (columna HOME_SECTION.type). */
public enum HomeSectionType {
    /** Banner principal, arriba de todo y con el header: un juego. */
    HERO,
    /** Sub banner: un juego. */
    BANNER,
    /** Carrusel con título: uno o más juegos, en orden. */
    CAROUSEL,
    /** Grid con filtro por género: los juegos elegidos, o todo el catálogo si no hay ninguno. */
    GRID,
    /** Carrusel de próximos lanzamientos: se llena solo con los juegos COMING_SOON (no guarda juegos). */
    UPCOMING
}
