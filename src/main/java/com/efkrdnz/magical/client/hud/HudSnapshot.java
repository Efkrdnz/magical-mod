package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.magic.status.MagicStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Everything the HUD needs to draw a frame, resolved once when something changed. The renderer is
 * a pure function of this and the partial tick: it allocates nothing and asks no registry, no
 * font and no {@code PlayerMagicState} method for anything.
 *
 * <p>Values that move - the two pools and the join fade - live in {@link HudState}'s tweens; the
 * snapshot holds their colour. Strings are already shaped, measured and placed.
 */
public record HudSnapshot(
        int version,
        long builtAtTick,
        HudOptions options,
        HudLayout layout,
        /** The mana bar's fill: the school's colour, or the danger red below a fifth. */
        int manaFill,
        /** The counts beside the bars, placed: the mana over its maximum, and the barrier while it holds any. */
        Readout[] pools,
        /** False while all four slots are empty: no glyphs and no cells, the band's room kept. */
        boolean slotBand,
        Slot[] slots,
        Readout[] readouts,
        Chip[] chips) {

    /** A string shaped for the font and measured, once. */
    public record Label(FormattedCharSequence text, int width, int color) {}

    /**
     * One cast slot. The cooldown is the start point the client heard about, so the renderer can
     * extrapolate the wipe every frame without a map lookup; {@code readyAtTick} is when it last
     * ran out, which lights the key for a moment. {@code inkFrom} and {@code inkTo} are where the
     * glyph's ink starts and stops down its square, which is what its grey is counted over.
     */
    public record Slot(
            int slot,
            ResourceLocation skill,
            int cell,
            int ink,
            float inkFrom,
            float inkTo,
            boolean empty,
            long cooldownStart,
            int cooldownRemaining,
            int cooldownTotal,
            long readyAtTick,
            Label key,
            Rect glyph,
            Rect text) {

        public boolean onCooldown() {
            return cooldownTotal > 0 && cooldownRemaining > 0;
        }
    }

    /**
     * One reading, placed: a string at {@code at}, led by a stamp when it has one. A stamped
     * reading's text starts {@link HudLayout#STAMP_LEAD} in; the counts and the "+n" have no stamp.
     */
    public record Readout(Label text, Rect at, Stamp stamp) {
        public int textX() {
            return stamp == null ? at.x() : at.x() + HudLayout.STAMP_LEAD;
        }
    }

    /**
     * A reading's mark: an atlas cell drawn whole in its owner's hue - a skill glyph at nine units.
     * It says whose the reading is; the number beside it says how much, because a gauge counted in
     * the rows of a nine-unit mark is a gauge nobody can read.
     */
    public record Stamp(int cell, int ink) {}

    /** One synced status on the player, as a chip under the crosshair. */
    public record Chip(MagicStatus status, int emblemCell, int color, boolean harmful, int amplifier, long startTick, int initialTicks, Rect bounds) {}

    public static final Readout[] NO_READOUTS = new Readout[0];
    public static final Chip[] NO_CHIPS = new Chip[0];
}
