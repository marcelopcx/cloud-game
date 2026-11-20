import { useState, type FormEvent } from "react";
import { AvatarPicker, BackButton } from "@components";
import { useAvatars } from "@hooks";
import { AuthError, login, register, saveSession } from "@services";
import headerLogo from "../../assets/logos/header.svg";
import userIcon from "../../assets/icons/user.svg";
import passwordIcon from "../../assets/icons/password.svg";
import hideIcon from "../../assets/icons/hide.svg";
import eyeIcon from "../../assets/icons/eye.svg";
import "./loginScreen.css";

export interface LoginScreenProps {
    onBack: () => void;
    /** Sesión iniciada. */
    onLoggedIn: () => void;
}

// login: correo y contraseña · register: cuenta nueva con username y avatar
type Mode = "login" | "register";

const USERNAME_MAX_LENGTH = 100;
const PASSWORD_MIN_LENGTH = 8;

const loginMessage = (err: unknown) => {
    if (err instanceof AuthError) {
        if (err.status === 400 || err.status === 401 || err.status === 403) return "Correo o contraseña incorrectos";
        if (err.code === "AUTH_DISABLED") return "El inicio de sesión no está habilitado en este servidor";
        return err.message;
    }
    return "No se pudo iniciar sesión";
};

const registerMessage = (err: unknown) => {
    if (err instanceof AuthError) {
        if (err.code === "CONFLICT") {
            return err.message.toLowerCase().includes("email") ? "Ya existe una cuenta con ese correo" : "Ese nombre de usuario ya está en uso";
        }
        if (err.code === "BAD_REQUEST" || err.status === 400) {
            if (err.message.startsWith("avatarId")) return "Ese avatar ya no está disponible, elige otro";
            if (err.message.startsWith("password")) return `La contraseña debe tener al menos ${PASSWORD_MIN_LENGTH} caracteres`;
            return "Revisa los datos e inténtalo de nuevo";
        }
        if (err.code === "AUTH_DISABLED") return "El registro no está habilitado en este servidor";
        return err.message;
    }
    return "No se pudo crear la cuenta";
};

/**
 * Inicio de sesión y registro con cuentas propias de game-crud. Con sesión, el progreso de las
 * partidas se guarda; sin ella se juega igual, pero se borra al salir.
 */
export const LoginScreen = ({ onBack, onLoggedIn }: LoginScreenProps) => {
    const [mode, setMode] = useState<Mode>("login");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [username, setUsername] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [showPassword, setShowPassword] = useState(false);
    const [pickedAvatarId, setAvatarId] = useState<number | null>(null);
    const { avatars, error: avatarsError } = useAvatars(mode === "register");
    // Arranca con el primero elegido: registrarse no queda trabado si no toca ninguno.
    const avatarId = avatars?.some((a) => a.id === pickedAvatarId) ? pickedAvatarId : (avatars?.[0]?.id ?? null);

    const switchMode = (next: Mode) => {
        setMode(next);
        setError(null);
        setShowPassword(false);
    };

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setBusy(true);
        setError(null);
        try {
            const session = mode === "login"
                ? await login(email.trim(), password)
                : await register(email.trim(), password, username.trim(), avatarId);
            saveSession(session);
            setPassword("");
            onLoggedIn();
        } catch (err) {
            setError(mode === "login" ? loginMessage(err) : registerMessage(err));
        } finally {
            setBusy(false);
        }
    };

    const isRegister = mode === "register";

    return (
        <main className="login-screen">
            <BackButton className="login-screen__back" onClick={onBack} />

            <section className="login-screen__hero">
                <div className="login-screen__brand">
                    <img src={headerLogo} alt="Cloud Game" className="login-screen__logo" />
                    <p className="login-screen__product">Juega desde tu navegador</p>
                </div>
            </section>

            <section className="login-screen__panel">
                <form key={mode} className="login-screen__form" onSubmit={submit}>
                    <h2 className="login-screen__title">{isRegister ? "Crear cuenta" : "Iniciar sesión"}</h2>

                    {isRegister && (
                        <label className="login-screen__field">
                            <img src={userIcon} alt="" aria-hidden="true" />
                            <input
                                id="username"
                                name="username"
                                type="text"
                                required
                                autoComplete="nickname"
                                autoCapitalize="off"
                                autoCorrect="off"
                                spellCheck={false}
                                enterKeyHint="next"
                                maxLength={USERNAME_MAX_LENGTH}
                                placeholder="Nombre de usuario"
                                aria-label="Nombre de usuario"
                                value={username}
                                onChange={(event) => setUsername(event.target.value)}
                            />
                        </label>
                    )}

                    <label className="login-screen__field">
                        <img src={userIcon} alt="" aria-hidden="true" />
                        <input
                            id="email"
                            name="email"
                            type="email"
                            required
                            autoComplete="username"
                            spellCheck={false}
                            enterKeyHint="next"
                            placeholder="Correo electrónico"
                            aria-label="Correo electrónico"
                            value={email}
                            onChange={(event) => setEmail(event.target.value)}
                        />
                    </label>

                    <label className="login-screen__field">
                        <img src={passwordIcon} alt="" aria-hidden="true" />
                        <input
                            id={isRegister ? "new-password" : "current-password"}
                            name="password"
                            type={showPassword ? "text" : "password"}
                            required
                            minLength={isRegister ? PASSWORD_MIN_LENGTH : undefined}
                            autoComplete={isRegister ? "new-password" : "current-password"}
                            enterKeyHint={isRegister ? "next" : "done"}
                            placeholder={isRegister ? `Contraseña (mín. ${PASSWORD_MIN_LENGTH} caracteres)` : "Contraseña"}
                            aria-label="Contraseña"
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                        />
                        <button
                            type="button"
                            className="login-screen__toggle"
                            aria-label={showPassword ? "Ocultar contraseña" : "Mostrar contraseña"}
                            aria-pressed={showPassword}
                            onClick={() => setShowPassword((visible) => !visible)}
                        >
                            <img src={showPassword ? eyeIcon : hideIcon} alt="" aria-hidden="true" />
                        </button>
                    </label>

                    {isRegister && (
                        <div className="login-screen__avatars">
                            <p className="login-screen__label">Elige tu avatar</p>
                            {avatars ? (
                                <AvatarPicker
                                    avatars={avatars}
                                    selectedId={avatarId}
                                    onSelect={(avatar) => setAvatarId(avatar.id)}
                                    disabled={busy}
                                />
                            ) : (
                                <p className="login-screen__hint">
                                    {avatarsError ? "No se pudieron cargar los avatares; podrás elegirlo después en tu perfil." : "Cargando avatares…"}
                                </p>
                            )}
                        </div>
                    )}

                    {error && <p className="login-screen__error" role="alert">{error}</p>}

                    <button
                        type="submit"
                        className="login-screen__submit"
                        disabled={busy || (isRegister && avatars === null && !avatarsError)}
                    >
                        {busy ? (isRegister ? "Creando cuenta…" : "Verificando…") : (isRegister ? "Crear cuenta" : "Ingresar")}
                    </button>

                    <p className="login-screen__switch">
                        {isRegister ? "¿Ya tienes cuenta?" : "¿No tienes cuenta?"}{" "}
                        <button type="button" onClick={() => switchMode(isRegister ? "login" : "register")}>
                            {isRegister ? "Inicia sesión" : "Regístrate"}
                        </button>
                    </p>
                </form>
            </section>
        </main>
    );
};
