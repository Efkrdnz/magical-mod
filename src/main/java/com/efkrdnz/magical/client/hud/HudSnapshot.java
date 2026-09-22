package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.hud.HudLayout.Disc;
import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.status.MagicStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Everything the HUD needs to draw a frame, resolved once when something changed. The renderer is
 * a pure function of this and the partial tick: it allocates nothing and asks no registry, no
 * font and no {@code PlayerMagicState} method for anything.
 *
 * <p>Values that move - pools, gauges, fades - live in {@link HudState}'s tweens; the snapshot
 * holds their shape and colour. Strings are already shaped, measured and placed.
 */
public record HudSnapshot(
        int version,
        long builtAtTick,
        HudOptions options,
        HudLayout layout,
        MagicSchool school,
        int manaColor,
        int manaHot,
        int manaNotches,
        int coreStyle,
        int coreColor,
        boolean maxLevel,
        boolean vessel,
        boolean corruption,
        /** The Sword Array is standing: the draw arc is drawn and its reading is a caption. */
        boolean drawArc,
        /** The arc's colour, already decided: pewter inside the draw, cinnabar past it. */
        int drawColor,
        Line[] coreLines,
        Line level,
        Card[] cards,
        Satellite[] satellites,
        GaugeLine[] gauges,
        Chip[] chips,
        Announcement[] announcements,
        Line[] captions,
        long lockUntilTick) {

    /** A string shaped for the font and measured, once. */
    public record Label(FormattedCharSequence text, int width, int color) {}

    /** A string placed in its box. */
    public record Line(Label label, Rect at) {}

    /**
     * One cast slot. The cooldown is the start point the client heard about, so the renderer can
     * extrapolate the sweep every frame without a map lookup; {@code readyAtTick} is when it last
     * ran out, for the flash.
     */
    public record Card(
            int slot,
            ResourceLocation skill,
            int emblemCell,
            int color,
            boolean forbidden,
            boolean empty,
            long cooldownStart,
            int cooldownRemaining,
            int cooldownTotal,
            long readyAtTick,
            Label key,
            Disc bounds,
            Rect tag,
            Rect text) {

        public boolean onCooldown() {
            return cooldownTotal > 0 && cooldownRemaining > 0;
        }
    }

    /** One enabled sin on its crown seat; the gauge itself is a tween in {@link HudState}. */
    public record Satellite(int seat, ResourceLocation sin, int emblemCell, int color, boolean rested, Disc bounds) {}

    /** One readout line beside the fan: a sin's name and value, or the mana charge. */
    public record GaugeLine(int tween, Label text, Rect at) {}

    /** One synced status on the player, as a chip under the crosshair. */
    public record Chip(MagicStatus status, int emblemCell, int color, boolean harmful, int amplifier, long startTick, int initialTicks, Rect bounds) {}

    /** One thing the player just gained, inking on under the readouts. */
    public record Announcement(int emblemCell, int color, boolean ink, Label title, long startTick, int lifetime, Rect emblem, Rect text) {}

    public static final Line[] NO_LINES = new Line[0];
    public static final Card[] NO_CARDS = new Card[0];
    public static final Satellite[] NO_SATELLITES = new Satellite[0];
    public static final GaugeLine[] NO_GAUGES = new GaugeLine[0];
    public static final Chip[] NO_CHIPS = new Chip[0];
    public static final Announcement[] NO_ANNOUNCEMENTS = new Announcement[0];
}
