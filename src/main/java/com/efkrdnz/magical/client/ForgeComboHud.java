package com.efkrdnz.magical.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * The forged-weapon combo readout: a row of pips under the crosshair for where the chain stands,
 * a thin bar draining with the combo window, and a ring closing around the crosshair while a heavy
 * is being charged. It carries no text and shows nothing at all unless a chain is actually live.
 */
public final class ForgeComboHud {

    /** How long after the last sync the pips are still believed. */
    private static final int STALE_TICKS = 120;
    private static final int PIP = 4;
    private static final int GAP = 3;
    private static final int PIP_Y = 14;
    private static final int BAR_Y = 21;
    private static final int BAR_WIDTH = 40;
    private static final int EMPTY_PIP = 0x60101820;
    private static final int BAR_TRACK = 0x60101820;
    private static final int RING_RADIUS = 11;
    /** The wind-up ring: dim and colourless, so the real charge fill reads as a separate stage. */
    private static final int WINDUP_RING = 0x70C8D4E8;
    private static final float PULSE_SPEED = 0.22f;

    /** The window this chain started with, so the drain bar has something to drain from. */
    private static int windowPeak;
    /**
     * Sync tick the peak above was taken from. Every {@code ClientForgeCombo} sync reports a
     * freshly-opened window at its full duration — never a mid-window update — so a changed sync
     * tick is the one reliable sign the peak must jump to match it, whether the new window is
     * longer or shorter than whatever the previous one left behind.
     */
    private static long peakSyncTick = -1L;
    /** Game tick the current attack hold began, negative while the key is up. */
    private static long holdStartTick = -1L;
    /**
     * Held as a plain reference, not a {@link java.lang.ref.WeakReference}: {@link #reset()} nulls
     * it explicitly on every path that can end a world (disconnect, world change), so it never
     * outlives the {@link ClientLevel} it points at.
     */
    private static ClientLevel lastLevel;

    private ForgeComboHud() {}

    /**
     * Drops everything this class remembers between frames. Nothing here is authoritative, so a
     * stale peak or a stale hold would only ever draw a lie — the drain bar opening already partly
     * drained, or a wind-up ring that starts full.
     */
    public static void reset() {
        windowPeak = 0;
        peakSyncTick = -1L;
        holdStartTick = -1L;
        lastLevel = null;
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (minecraft.level == null) {
            // Disconnected to the main menu: nothing left to draw, and lastLevel must not go on
            // pinning the disposed ClientLevel (its chunk cache and every client entity) for the
            // rest of the process — only a later rejoin while holding a forged weapon would ever
            // touch this class again otherwise.
            if (lastLevel != null) {
                ClientForgeCombo.clear();
            }
            return;
        }
        if (minecraft.level != lastLevel) {
            // A different world: every remembered tick and peak belongs to the old one's clock, and
            // this bookkeeping must run whether or not a forged weapon is even in hand right now -
            // otherwise lastLevel keeps pinning a disposed ClientLevel through every frame spent
            // wielding something else after the change, not just the ones spent wielding nothing.
            //
            // The mirror goes with them. Its updatedAtTick is the old world's clock too, so leaving
            // a world at game time 500000 and joining one at tick 100 makes the staleness check
            // below subtract to a negative age and pass: the previous world's pips, element colour
            // and an undrained window bar would sit under the crosshair until the next sync.
            ClientForgeCombo.clear();
            lastLevel = minecraft.level;
        }
        if (minecraft.player == null || !ForgeComboInput.isForgedWeapon(minecraft.player.getMainHandItem())) {
            return;
        }
        long updated = ClientForgeCombo.updatedAtTick();
        if (updated == 0L || minecraft.level.getGameTime() - updated > STALE_TICKS) {
            return;
        }
        int centreX = guiGraphics.guiWidth() / 2;
        int centreY = guiGraphics.guiHeight() / 2;
        float age = minecraft.player.tickCount + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        drawPips(guiGraphics, centreX, centreY, age);
        drawWindow(guiGraphics, centreX, centreY);
        drawCharge(guiGraphics, minecraft, centreX, centreY);
    }

    /** One pip per link in the chain, lit up to where the next press lands. */
    private static void drawPips(GuiGraphics guiGraphics, int centreX, int centreY, float age) {
        int chain = ClientForgeCombo.chainLength();
        if (chain <= 0) {
            return;
        }
        int index = Mth.clamp(ClientForgeCombo.comboIndex(), 0, chain);
        int colour = ClientForgeCombo.elementColor();
        int left = centreX - (chain * PIP + (chain - 1) * GAP) / 2;
        int top = centreY + PIP_Y;
        for (int i = 0; i < chain; i++) {
            int x = left + i * (PIP + GAP);
            guiGraphics.fill(x, top, x + PIP, top + PIP, pipColour(i, index, colour, age));
        }
    }

    private static int pipColour(int slot, int index, int colour, float age) {
        if (slot < index) {
            return 0xFF000000 | colour;
        }
        if (slot != index || ClientForgeCombo.readyInTicks() > 0) {
            return EMPTY_PIP;
        }
        float pulse = 0.45f + 0.55f * (0.5f + 0.5f * Mth.sin(age * PULSE_SPEED));
        return (Math.round(255.0f * pulse) << 24) | colour;
    }

    /** A hairline that drains as the window to continue the chain runs out. */
    private static void drawWindow(GuiGraphics guiGraphics, int centreX, int centreY) {
        int left = ClientForgeCombo.windowTicksLeft();
        long syncTick = ClientForgeCombo.updatedAtTick();
        if (syncTick != peakSyncTick) {
            // A new sync arrived since the last frame drawn, which always means a freshly-opened
            // window - a chain landing on the same comboIndex with a shorter window than the last
            // -drawn residual (a mid-chain weapon swap, say) is exactly as much "new" as a longer
            // one, so the peak has to jump to match it either way rather than only on a rise.
            peakSyncTick = syncTick;
            windowPeak = left;
        }
        if (left <= 0 || windowPeak <= 0) {
            return;
        }
        int x = centreX - BAR_WIDTH / 2;
        int y = centreY + BAR_Y;
        guiGraphics.fill(x, y, x + BAR_WIDTH, y + 1, BAR_TRACK);
        int filled = Math.max(1, Math.round(BAR_WIDTH * Math.min(1.0f, left / (float) windowPeak)));
        guiGraphics.fill(x, y, x + filled, y + 1, 0xFF000000 | ClientForgeCombo.elementColor());
    }

    /**
     * Two stages around the crosshair: a dim ring filling toward the charge threshold while the key
     * is merely held, then the element-coloured charge filling over it once the charge is real.
     */
    private static void drawCharge(GuiGraphics guiGraphics, Minecraft minecraft, int centreX, int centreY) {
        float windup = windupFraction(minecraft);
        if (windup <= 0.0f) {
            return;
        }
        ring(guiGraphics, centreX, centreY, windup, WINDUP_RING);
        float charge = ForgeComboInput.chargeFraction();
        if (charge > 0.0f) {
            ring(guiGraphics, centreX, centreY, charge, 0xFF000000 | ClientForgeCombo.elementColor());
        }
    }

    /**
     * How far a held attack key is toward the charge threshold, 0 while the key is up. The HUD
     * counts this itself because {@code ForgeStrikeMath.chargeFraction} is the server's damage
     * input and reads 0 for the whole pre-threshold hold — which is exactly the stretch the
     * telegraph has to show.
     */
    private static float windupFraction(Minecraft minecraft) {
        if (minecraft.level == null || !attackHeld(minecraft)) {
            holdStartTick = -1L;
            return 0.0f;
        }
        long now = minecraft.level.getGameTime();
        if (holdStartTick < 0L || holdStartTick > now) {
            holdStartTick = now;
        }
        return Mth.clamp((now - holdStartTick) / (float) ForgeComboInput.chargeThresholdTicks(), 0.0f, 1.0f);
    }

    /** The same key state {@code ForgeComboInput} accumulates a hold from. */
    private static boolean attackHeld(Minecraft minecraft) {
        return minecraft.options.keyAttack.isDown() && minecraft.screen == null
                && (minecraft.gameMode == null || !minecraft.gameMode.isDestroying());
    }

    /** An arc of 1 px dots from the top of the crosshair, clockwise, covering {@code fraction}. */
    private static void ring(GuiGraphics guiGraphics, int centreX, int centreY, float fraction, int colour) {
        int steps = Math.max(1, Math.round(Mth.TWO_PI * RING_RADIUS * fraction));
        for (int i = 0; i <= steps; i++) {
            float angle = -Mth.HALF_PI + Mth.TWO_PI * fraction * i / steps;
            int x = centreX + Math.round(Mth.cos(angle) * RING_RADIUS);
            int y = centreY + Math.round(Mth.sin(angle) * RING_RADIUS);
            guiGraphics.fill(x, y, x + 1, y + 1, colour);
        }
    }
}
