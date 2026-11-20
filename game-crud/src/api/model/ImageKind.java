package api.model;

import java.util.Arrays;

import api.exception.ApiException;

/** Tipos de imagen de un juego: una columna de IMAGE cada uno. El nombre es el de la API y del archivo. */
public enum ImageKind {
    POSTER("poster"),
    COVER("cover"),
    BANNER1("banner1"),
    BANNER2("banner2"),
    ICON("icon"),
    BACKGROUND("background");

    private final String key;

    ImageKind(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** "banner1" → BANNER1; 400 si no es un tipo conocido. */
    public static ImageKind of(String key) {
        return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("tipo de imagen inválido: " + key
                        + " (poster, cover, banner1, banner2, icon o background)"));
    }
}
