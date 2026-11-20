package api.model;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Tabla HOME_SECTION con sus juegos en HOME_SECTION_GAME. Los juegos van como
 * colección con sort_order propio (y no @OrderColumn) porque borrar un juego deja
 * huecos en el orden, y @OrderColumn los leería como null.
 */
@Entity
@Table(name = "home_section")
public class HomeSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HomeSectionType type;

    @Column(length = 100)
    private String title;

    @ElementCollection
    @CollectionTable(name = "home_section_game", joinColumns = @JoinColumn(name = "id_section"))
    @OrderBy("sortOrder")
    private List<HomeSectionGame> games = new ArrayList<>();

    protected HomeSection() {
    }

    public HomeSection(int sortOrder, HomeSectionType type, String title) {
        this.sortOrder = sortOrder;
        this.type = type;
        this.title = title;
    }

    public Integer getId() {
        return id;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public HomeSectionType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public List<HomeSectionGame> getGames() {
        return games;
    }
}
