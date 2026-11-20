import React from "react";
import "./footer.css";

import footerLogo from "../../../assets/logos/footer.svg";
import githubIcon from "../../../assets/icons/github.svg";

const SOCIAL_LINKS = [
    { id: "github", url: "https://github.com/marcelopcx/cloud-game", icon: githubIcon, label: "GitHub" },
];

const NAV_LINKS_COLUMN_1 = [
    { label: "Documentación", href: "https://github.com/marcelopcx/cloud-game#readme" },
    { label: "Código fuente", href: "https://github.com/marcelopcx/cloud-game" },
];

const NAV_LINKS_COLUMN_2 = [
    { label: "Autor", href: "https://marcelopcx.me" },
];

const LEGAL_LINKS: { label: string; href: string }[] = [];

export const Footer: React.FC = () => {
    return (
        <footer className="footer">
            <div className="footer-content">

                <div className="footer-column footer-brand">
                    <img src={footerLogo} alt="Cloud Game" className="footer-logo" />
                    <address className="footer-address">
                        Plataforma de cloud gaming · proyecto de pasantías
                    </address>
                    <div className="footer-social-links">
                        {SOCIAL_LINKS.map((social) => (
                            <a
                                key={social.id}
                                href={social.url}
                                target="_blank"
                                rel="noopener noreferrer"
                                aria-label={`Visitar nuestro ${social.label}`}
                            >
                                <img src={social.icon} alt={social.label} />
                            </a>
                        ))}
                    </div>
                </div>

                <nav className="footer-column" aria-label="Enlaces principales">
                    {NAV_LINKS_COLUMN_1.map((link) => (
                        <a key={link.label} href={link.href} className="footer-link">
                            {link.label}
                        </a>
                    ))}
                </nav>

                <nav className="footer-column" aria-label="Enlaces secundarios">
                    {NAV_LINKS_COLUMN_2.map((link) => (
                        <a key={link.label} href={link.href} className="footer-link">
                            {link.label}
                        </a>
                    ))}
                </nav>
            </div>

            <div className="footer-bottom">
                <nav className="footer-bottom-links" aria-label="Enlaces legales">
                    {LEGAL_LINKS.map((link, index) => (
                        <React.Fragment key={link.label}>
                            <a href={link.href} className="footer-link legal-link">{link.label}</a>
                            {index < LEGAL_LINKS.length - 1 && <span className="separator" aria-hidden="true">|</span>}
                        </React.Fragment>
                    ))}
                </nav>
                <div className="footer-bottom-content">
                    <p className="footer-text">
                        © 2026 Marcelo Perozo
                    </p>
                </div>
            </div>
        </footer>
    );
};