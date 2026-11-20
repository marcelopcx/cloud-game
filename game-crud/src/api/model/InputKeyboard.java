package api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla INPUT_KEYBOARD: teclas y botones del ratón. El id 0 ("null") significa "sin tecla". */
@Entity
@Table(name = "input_keyboard")
public class InputKeyboard {

    public static final int NONE_ID = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected InputKeyboard() {
    }

    public InputKeyboard(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** True si es la fila "null" (id 0): el juego no usa teclado para esta acción. */
    public boolean isNone() {
        return id != null && id == NONE_ID;
    }
}
