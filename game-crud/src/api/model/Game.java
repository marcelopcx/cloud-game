package api.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Tabla GAME, con sus vínculos en GAME_CATEGORY, GAME_GAMEPAD y GAME_IMAGE. */
@Entity
@Table(name = "game")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "descr", length = 500)
    private String description;

    private Double rating;

    @Column(nullable = false)
    private String path;

    @Column(name = "min_cont")
    private Integer minContainers;

    @Column(name = "max_cont")
    private Integer maxContainers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status = GameStatus.ACTIVE;

    /** Lo pone la base (DEFAULT CURRENT_TIMESTAMP); Hibernate lo relee después del INSERT. */
    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(name = "game_category",
            joinColumns = @JoinColumn(name = "id_game"),
            inverseJoinColumns = @JoinColumn(name = "id_category"))
    private Set<Category> categories = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "game_gamepad",
            joinColumns = @JoinColumn(name = "id_game"),
            inverseJoinColumns = @JoinColumn(name = "id_gamepad"))
    private Set<Gamepad> gamepads = new HashSet<>();

    /** GAME_IMAGE admite varias; la API expone solo la primera (menor id), ver {@link #mainImage()}. */
    @ManyToMany
    @JoinTable(name = "game_image",
            joinColumns = @JoinColumn(name = "id_game"),
            inverseJoinColumns = @JoinColumn(name = "id_image"))
    @OrderBy("id")
    private List<Image> images = new ArrayList<>();

    protected Game() {
    }

    public Game(String name, String description, Double rating, String path,
                Integer minContainers, Integer maxContainers) {
        this.name = name;
        this.description = description;
        this.rating = rating;
        this.path = path;
        this.minContainers = minContainers;
        this.maxContainers = maxContainers;
    }

    /** La imagen que expone la API, o null si el juego no tiene ninguna. */
    public Image mainImage() {
        return images.isEmpty() ? null : images.get(0);
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    /** min_cont admite NULL en la base; se lee como 1 (su default). */
    public int getMinContainers() {
        return minContainers == null ? 1 : minContainers;
    }

    public void setMinContainers(Integer minContainers) {
        this.minContainers = minContainers;
    }

    public Integer getMaxContainers() {
        return maxContainers;
    }

    public void setMaxContainers(Integer maxContainers) {
        this.maxContainers = maxContainers;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public Set<Gamepad> getGamepads() {
        return gamepads;
    }

    public List<Image> getImages() {
        return images;
    }
}
