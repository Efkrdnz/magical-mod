package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicLoadout;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The loadout switcher: hold the wheel key, scroll to a loadout, release to take it.
 *
 * <p>This was a radial of individual skills. Loadouts are named by the player, and a radial cannot
 * hold something like Defensive Rotation legibly at any radius, so it is a vertical column against
 * the left edge instead - names read straight and the list grows downward rather than crowding.
 *
 * <p>The key is shared with the parry prompt, which always wins: {@link MagicalClientEvents} asks
 * {@link ClientCounterPrompt} first and only passes the key through when the counter did not
 * consume it. During a counter the window is a handful of ticks, and a list opening instead of a
 * parry would lose the exchange.
 */
public final class MagicWheelOverlay {
    private static final int ROW_H = 22;
    private static final int PANEL_W = 148;
    private static final int MARGIN = 10;
    private static final int PIP = 5;
    private static final float FADE_STEP = 0.22F;
    private static final int ACCENT_OPEN = 0xFF5FD4FF;
    private static final int ACCENT_LOCKED = 0xFFE06470;
    private static final int ACCENT_ACTIVE = 0xFFF7D774;
    private static final int PIP_EMPTY = 0xFF2A3446;

    private static boolean active;
    private static boolean keyWasDown;
    private static int highlighted;
    private static float fade;

    private MagicWheelOverlay() {}

    public static boolean isActive() {
        return active;
    }

    /**
     * Scroll moves the highlight while the switcher is open.
     *
     * @return true when the scroll was consumed, so the hotbar does not move as well
     */
    public static boolean handleScroll(double scrollDeltaY) {
        if (!active || scrollDeltaY == 0.0D) {
            return false;
        }
        int count = loadouts().size();
        if (count > 1) {
            // Scrolling up walks up the list, which is the opposite sign to the vanilla hotbar.
            highlighted = Math.floorMod(highlighted - (int) Math.signum(scrollDeltaY), count);
        }
        return true;
    }

    public static void tick(Minecraft minecraft, boolean keyDown) {
        if (minecraft.player == null || minecraft.screen != null) {
            active = false;
            fade = 0.0F;
            keyWasDown = keyDown;
            return;
        }
        if (keyDown && !keyWasDown) {
            active = true;
            highlighted = ClientMagicState.get().activeLoadoutIndex();
        } else if (!keyDown && keyWasDown && active) {
            commit();
        }
        fade = active ? Math.min(1.0F, fade + FADE_STEP) : Math.max(0.0F, fade - FADE_STEP);
        keyWasDown = keyDown;
    }

    /**
     * Take the highlighted loadout.
     *
     * <p>Every charge handler is cancelled first. Charge state is keyed by slot number, so a switch
     * mid-charge would otherwise leave a handler charging the skill that used to be on that key -
     * you would wind up one spell and release another.
     *
     * <p>The server decides whether the switch happens at all; it refuses one within a second of a
     * cast. This is a request, not the change itself.
     */
    private static void commit() {
        active = false;
        List<MagicLoadout> loadouts = loadouts();
        if (highlighted < 0 || highlighted >= loadouts.size()
                || highlighted == ClientMagicState.get().activeLoadoutIndex()) {
            return;
        }
        SovereignAegisInput.cancel();
        BlackFlamesInput.cancel();
        SpaceOffenseInput.cancel();
        SoulVowInput.cancel();
        MagicBarrageInput.cancel();
        SpaceAuthorityInput.cancel();
        BloodShapeInput.cancel();
        MagicalNetwork.sendSelectLoadout(highlighted);
    }

    private static List<MagicLoadout> loadouts() {
        return ClientMagicState.get().loadouts();
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (fade <= 0.01F || minecraft.player == null) {
            return;
        }
        List<MagicLoadout> loadouts = loadouts();
        if (loadouts.isEmpty()) {
            return;
        }
        int alpha = (int) (fade * 255.0F);
        int height = loadouts.size() * ROW_H + 24;
        int x = MARGIN;
        int y = (guiGraphics.guiHeight() - height) / 2;

        // The lock is predicted here rather than streamed: the server sent its value with the sync
        // the cast already triggered, and it counts down locally instead of costing a packet a tick.
        boolean locked = ClientMagicState.get().loadoutSwapLockTicks() > 0;

        MagicalGuiStyle.panel(guiGraphics, x, y, x + PANEL_W, y + height,
                MagicalGuiStyle.withAlpha(locked ? ACCENT_LOCKED : ACCENT_OPEN, alpha));
        guiGraphics.drawString(minecraft.font,
                Component.translatable(locked ? "screen.magical.loadout_locked" : "screen.magical.loadout_switch"),
                x + 8, y + 7, MagicalGuiStyle.withAlpha(MagicalGuiStyle.TEXT_MUTED, alpha), false);

        int activeIndex = ClientMagicState.get().activeLoadoutIndex();
        for (int index = 0; index < loadouts.size(); index++) {
            MagicLoadout loadout = loadouts.get(index);
            int rowY = y + 20 + index * ROW_H;
            boolean chosen = index == highlighted;
            MagicalGuiStyle.listRow(guiGraphics, x + 5, rowY, PANEL_W - 10, ROW_H - 3, chosen,
                    MagicalGuiStyle.withAlpha(index == activeIndex ? ACCENT_ACTIVE : ACCENT_OPEN, alpha));
            guiGraphics.drawString(minecraft.font,
                    minecraft.font.plainSubstrByWidth(loadout.name(), PANEL_W - 52),
                    x + 11, rowY + 4,
                    MagicalGuiStyle.withAlpha(chosen ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED, alpha),
                    false);
            drawPips(guiGraphics, loadout, x + PANEL_W - 13 - MagicContent.LOADOUT_SIZE * (PIP + 2), rowY + 6, alpha);
        }
    }

    /**
     * Four colour dots per row, one per slot.
     *
     * <p>They make a loadout recognisable without reading it, which is the point of holding the key
     * rather than cycling blind - and an empty slot shows as a dim dot rather than as nothing, so a
     * half-built loadout is visibly half-built.
     */
    private static void drawPips(GuiGraphics guiGraphics, MagicLoadout loadout, int x, int y, int alpha) {
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            ResourceLocation id = loadout.slot(slot);
            MagicSkillDefinition skill = id == null ? null : MagicContent.get(id);
            int rgb = skill == null ? PIP_EMPTY : skill.color();
            int dotX = x + slot * (PIP + 2);
            guiGraphics.fill(dotX, y, dotX + PIP, y + PIP, MagicalGuiStyle.withAlpha(rgb, alpha));
        }
    }
}
