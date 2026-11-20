import { type ReactNode, useEffect, useRef, useState } from "react";
import { useFriendSearch, useFriends } from "@hooks";
import type { FriendEntry, FriendSearchResult } from "@/types";
import { UserAvatar } from "../userAvatar/UserAvatar.tsx";
import "./friendsPanel.css";

type Tab = "friends" | "requests";

export interface FriendsPanelProps {
    /** Token del jugador con sesión: sin token el botón no se muestra. */
    token: string | null;
}

/**
 * Botón de amigos del header (al lado del usuario) con el centro de amigos: la lista de amigos
 * con el buscador para enviar solicitudes arriba, y la bandeja de solicitudes recibidas. Quién está jugando
 * se ve en el carrusel de amigos del home, no acá.
 */
export const FriendsPanel = ({ token }: FriendsPanelProps) => {
    const [open, setOpen] = useState(false);
    const [tab, setTab] = useState<Tab>("friends");
    const [query, setQuery] = useState("");
    const rootRef = useRef<HTMLDivElement>(null);
    const { requests, friends, friendsError, respond, reloadRequests, reloadFriends } = useFriends(token, open && tab === "friends");
    const search = useFriendSearch(token, open && tab === "friends" ? query : "");

    // Se cierra al tocar fuera o con Escape.
    useEffect(() => {
        if (!open) return;
        const onPointer = (event: PointerEvent) => {
            if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
        };
        const onKey = (event: KeyboardEvent) => {
            if (event.key === "Escape") setOpen(false);
        };
        document.addEventListener("pointerdown", onPointer);
        document.addEventListener("keydown", onKey);
        return () => {
            document.removeEventListener("pointerdown", onPointer);
            document.removeEventListener("keydown", onKey);
        };
    }, [open]);

    if (!token) return null;

    const pendingCount = requests.length;
    const label = pendingCount > 0 ? `Amigos · ${pendingCount} solicitudes pendientes` : "Amigos";

    const toggle = () => {
        // Con solicitudes pendientes se abre directo en la bandeja.
        if (!open && pendingCount > 0) setTab("requests");
        setOpen((o) => !o);
    };

    const sendTo = async (user: FriendSearchResult) => {
        await search.send(user.userId);
        // Si él ya había enviado una, quedó aceptada: sale de la bandeja y entra a la lista.
        if (user.relation === "RECEIVED") {
            reloadRequests();
            reloadFriends();
        }
    };

    return (
        <div className="friends" ref={rootRef}>
            <button
                type="button"
                className={`friends-button${open ? " friends-button--open" : ""}`}
                aria-label={label}
                title={label}
                aria-expanded={open}
                aria-haspopup="dialog"
                onClick={toggle}
            >
                <FriendsIcon />
                {pendingCount > 0 && <span className="friends-badge">{pendingCount > 9 ? "9+" : pendingCount}</span>}
            </button>

            {open && (
                <div className="friends-panel" role="dialog" aria-label="Centro de amigos">
                    <div className="friends-tabs" role="tablist">
                        <TabButton tab="friends" current={tab} onSelect={setTab}>Amigos</TabButton>
                        <TabButton tab="requests" current={tab} onSelect={setTab}>
                            Solicitudes
                            {pendingCount > 0 && <span className="friends-tab-count">{pendingCount}</span>}
                        </TabButton>
                    </div>

                    {tab === "friends" && (
                        <div className="friends-body">
                            <input
                                type="search"
                                className="friends-search"
                                placeholder="Buscar jugador para agregar"
                                aria-label="Buscar jugador para agregar"
                                value={query}
                                onChange={(event) => setQuery(event.target.value)}
                                autoComplete="off"
                                autoFocus
                            />

                            {query.trim() === "" ? (
                                <FriendList friends={friends} error={friendsError} />
                            ) : (
                                <section className="friends-section" aria-label="Resultados de la búsqueda">
                                    {search.loading ? (
                                        <p className="friends-empty">Buscando…</p>
                                    ) : search.results.length === 0 ? (
                                        <p className="friends-empty">No hay jugadores con ese nombre.</p>
                                    ) : (
                                        <ul className="friends-list">
                                            {search.results.map((user) => (
                                                <li key={user.userId} className="friends-row">
                                                    <UserAvatar username={user.username} src={user.avatar} className="friends-avatar" />
                                                    <span className="friends-name">{user.username}</span>
                                                    <SearchAction
                                                        user={user}
                                                        busy={search.pending.has(user.userId)}
                                                        onSend={() => void sendTo(user)}
                                                    />
                                                </li>
                                            ))}
                                        </ul>
                                    )}
                                </section>
                            )}
                        </div>
                    )}

                    {tab === "requests" && (
                        <div className="friends-body">
                            {requests.length === 0 ? (
                                <p className="friends-empty">No tienes solicitudes pendientes.</p>
                            ) : (
                                <ul className="friends-list" aria-label="Solicitudes recibidas">
                                    {requests.map((request) => (
                                        <li key={request.id} className="friends-row">
                                            <UserAvatar username={request.username} src={request.avatar} className="friends-avatar" />
                                            <span className="friends-name">{request.username}</span>
                                            <span className="friends-actions">
                                                <button type="button" className="friends-action friends-action--primary" onClick={() => void respond(request, true)}>
                                                    Aceptar
                                                </button>
                                                <button type="button" className="friends-action" onClick={() => void respond(request, false)}>
                                                    Rechazar
                                                </button>
                                            </span>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
};

const TabButton = ({ tab, current, onSelect, children }: {
    tab: Tab;
    current: Tab;
    onSelect: (tab: Tab) => void;
    children: ReactNode;
}) => (
    <button
        type="button"
        role="tab"
        aria-selected={tab === current}
        className={`friends-tab${tab === current ? " friends-tab--active" : ""}`}
        onClick={() => onSelect(tab)}
    >
        {children}
    </button>
);

const FriendList = ({ friends, error }: { friends: FriendEntry[] | null; error: boolean }) => {
    if (friends === null) {
        return <p className="friends-empty">{error ? "No se pudo cargar tu lista de amigos." : "Cargando…"}</p>;
    }
    if (friends.length === 0) {
        return <p className="friends-empty">Todavía no tienes amigos. Busca a un jugador por su nombre para agregarlo.</p>;
    }
    return (
        <section className="friends-section" aria-label="Tus amigos">
            <h3 className="friends-heading">{friends.length === 1 ? "1 amigo" : `${friends.length} amigos`}</h3>
            <ul className="friends-list">
                {[...friends]
                    .sort((a, b) => a.username.localeCompare(b.username))
                    .map((friend) => (
                        <li key={friend.userId} className="friends-row">
                            <UserAvatar username={friend.username} src={friend.avatar} className="friends-avatar" />
                            <span className="friends-name">{friend.username}</span>
                        </li>
                    ))}
            </ul>
        </section>
    );
};

const SearchAction = ({ user, busy, onSend }: { user: FriendSearchResult; busy: boolean; onSend: () => void }) => {
    switch (user.relation) {
        case "FRIEND":
            return <span className="friends-status">Amigos</span>;
        case "SENT":
            return <span className="friends-status">Enviada</span>;
        case "RECEIVED":
            return (
                <button type="button" className="friends-action friends-action--primary" disabled={busy} onClick={onSend}>
                    Aceptar
                </button>
            );
        case "NONE":
            return (
                <button type="button" className="friends-action friends-action--primary" disabled={busy} onClick={onSend}>
                    {busy ? "Enviando…" : "Agregar"}
                </button>
            );
    }
};

const FriendsIcon = () => (
    <svg className="friends-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <circle cx="9" cy="8" r="3.5" />
        <path d="M2.5 19.5c0-3.3 2.9-5.5 6.5-5.5s6.5 2.2 6.5 5.5" />
        <circle cx="17" cy="9" r="2.5" />
        <path d="M17 14c2.6 0 4.5 1.6 4.5 4" />
    </svg>
);
