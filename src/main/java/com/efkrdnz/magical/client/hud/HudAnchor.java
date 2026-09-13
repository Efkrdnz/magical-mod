package com.efkrdnz.magical.client.hud;

/** Which screen corner the HUD hangs from. No Minecraft imports: the client config and the layout both read it. */
public enum HudAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public boolean right() {
        return this == TOP_RIGHT || this == BOTTOM_RIGHT;
    }

    public boolean bottom() {
        return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
    }
}
