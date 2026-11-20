import { UserAvatar } from "../userAvatar/UserAvatar.tsx";
import "./friend.css";

export interface FriendProps {
    username: string;
    /** Ruta del avatar del amigo; sin avatar se muestra la inicial. */
    avatar?: string | null;
    /** Juego que está jugando; sin juego se muestra "Desconectado" y el ícono en gris. */
    gameName?: string | null;
    /** URL del ícono del juego que está jugando: se muestra en el avatar. */
    gameIcon?: string | null;
    onClick?: () => void;
}

/** Es un botón para que el mando y el teclado puedan recorrerlo y abrir el juego del amigo. */
export const Friend = ({ username, avatar, gameName, gameIcon, onClick }: FriendProps) => {
    const playing = Boolean(gameName);

    return (
        <button type="button" className={`friend${playing ? " friend--playing" : ""}`} onClick={onClick}>
            <div className="friend-avatar">
                {playing && gameIcon ? (
                    <img className="friend-game-icon" src={gameIcon} alt="" draggable={false} />
                ) : (
                    // Sin juego (o sin ícono) se muestra su avatar, o la inicial si no tiene, como en el perfil.
                    <UserAvatar username={username} src={avatar} className="friend-initial" />
                )}
                <svg className="friend-badge" viewBox="0 0 51 51" role="img" aria-label={playing ? "Jugando" : "Desconectado"}>
                    <path className="friend-badge-bg" d="M22.3648 0C10.0268 0 0 10.0387 0 22.3648V28.3501C0 40.6763 10.0387 50.715 22.3648 50.715H28.3501C40.6881 50.715 50.715 40.6763 50.715 28.3501V22.3648C50.715 10.0268 40.6763 0 28.3501 0H22.3648Z" />
                    <path d="M33.1403 16.555C34.6604 16.5513 36.1251 17.125 37.2382 18.1602C38.3513 19.1954 39.0297 20.6148 39.1361 22.1311L39.9905 34.1228C40.0256 34.5754 39.9633 35.0302 39.8078 35.4567C39.6523 35.8832 39.4072 36.2715 39.089 36.5953C38.7709 36.9191 38.3871 37.171 37.9634 37.3341C37.5397 37.4971 37.086 37.5674 36.6328 37.5404C35.9041 37.4744 35.2169 37.1722 34.6758 36.6797C34.1346 36.1873 33.7691 35.5315 33.6349 34.8123L32.4957 29.7308C32.4226 29.3899 32.2329 29.0852 31.9593 28.869C31.6857 28.6529 31.3454 28.5389 30.9968 28.5466H21.9131C21.5645 28.5389 21.2242 28.6529 20.9506 28.869C20.677 29.0852 20.4873 29.3899 20.4142 29.7308L19.3199 34.8123C19.1857 35.5315 18.8202 36.1873 18.2791 36.6797C17.7379 37.1722 17.0507 37.4744 16.322 37.5404C15.8725 37.5608 15.4238 37.4855 15.0056 37.3196C14.5874 37.1536 14.2092 36.9007 13.896 36.5776C13.5828 36.2545 13.3418 35.8686 13.189 35.4454C13.0361 35.0222 12.9749 34.5714 13.0093 34.1228L13.8637 22.1311C13.9701 20.6148 14.6485 19.1954 15.7616 18.1602C16.8747 17.125 18.3395 16.5513 19.8595 16.555H33.1403Z" fill="#fff" stroke="#fff" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
            </div>
            <strong className="friend-name">{username}</strong>
            <span className="friend-status">{gameName || "Desconectado"}</span>
        </button>
    );
};
