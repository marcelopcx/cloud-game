package api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tabla USERS (USER es palabra reservada en PostgreSQL): jugadores de game-client. Email y
 * username son UNIQUE. Los administradores del panel van aparte, en {@link Admin}.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** Elegido entre los de AVATAR; null si nunca eligió o se borró el que tenía. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_avatar")
    private Avatar avatar;

    /** bcrypt; null en cuentas creadas antes del registro propio (no pueden iniciar sesión). */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    protected User() {
    }

    public User(String email, String username) {
        this.email = email;
        this.username = username;
    }

    public Integer getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Avatar getAvatar() {
        return avatar;
    }

    public void setAvatar(Avatar avatar) {
        this.avatar = avatar;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
