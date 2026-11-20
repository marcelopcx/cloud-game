import gamepadIcon from "../../../assets/icons/gamepad.svg";
import mouseIcon from "../../../assets/icons/computer-mouse.svg";
import phoneIcon from "../../../assets/icons/phone.svg";

export type GamePlatform = "gamepad" | "mouse" | "mobile";

export const PLATFORM_ICONS: Record<GamePlatform, { icon: string; label: string; shortLabel: string }> = {
    gamepad: { icon: gamepadIcon, label: "Compatible con mando", shortLabel: "Mando" },
    mouse: { icon: mouseIcon, label: "Compatible con teclado y ratón", shortLabel: "Teclado y ratón" },
    mobile: { icon: phoneIcon, label: "Compatible con móvil", shortLabel: "Móvil" },
};
