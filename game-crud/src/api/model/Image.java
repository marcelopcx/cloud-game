package api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tabla IMAGE: rutas públicas ({@code /media/...}) de las imágenes de un juego, que game-crud
 * guarda en disco ({@link api.service.ImageStorage}). Puede quedar alguna URL externa vieja
 * hasta que {@link api.service.ExternalImageImporter} la descarga.
 */
@Entity
@Table(name = "image")
public class Image {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "url_poster")
    private String poster;

    @Column(name = "url_cover")
    private String cover;

    @Column(name = "url_banner_1")
    private String banner1;

    @Column(name = "url_banner_2")
    private String banner2;

    @Column(name = "url_icon")
    private String icon;

    @Column(name = "url_background")
    private String background;

    public Image() {
    }

    public String get(ImageKind kind) {
        return switch (kind) {
            case POSTER -> poster;
            case COVER -> cover;
            case BANNER1 -> banner1;
            case BANNER2 -> banner2;
            case ICON -> icon;
            case BACKGROUND -> background;
        };
    }

    public void set(ImageKind kind, String value) {
        switch (kind) {
            case POSTER -> poster = value;
            case COVER -> cover = value;
            case BANNER1 -> banner1 = value;
            case BANNER2 -> banner2 = value;
            case ICON -> icon = value;
            case BACKGROUND -> background = value;
        }
    }

    public Integer getId() {
        return id;
    }

    public String getPoster() {
        return poster;
    }

    public String getCover() {
        return cover;
    }

    public String getBanner1() {
        return banner1;
    }

    public String getBanner2() {
        return banner2;
    }

    public String getIcon() {
        return icon;
    }

    public String getBackground() {
        return background;
    }
}
