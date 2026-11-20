package api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tabla INPUT_KEYBOARD: botones del mando. El id 0 ("null") significa "sin botón". */
@Entity
@Table(name = "input_controller")
public class InputController {

    public static final int NONE_ID = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    protected InputController() {
    }

    public InputController(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** True si es la fila "null" (id 0): el juego no usa mando para esta acción. */
    public boolean isNone() {
        return id != null && id == NONE_ID;
    }
}
