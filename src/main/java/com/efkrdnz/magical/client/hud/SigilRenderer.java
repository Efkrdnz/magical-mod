package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.GenericHoldInput;
import com.efkrdnz.magical.client.MagicBarrageInput;
import com.efkrdnz.magical.client.SpaceAuthorityInput;
import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.client.hud.HudSnapshot.Chip;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.HudSnapshot.Readout;
import com.efkrdnz.magical.client.hud.HudSnapshot.Slot;
import com.efkrdnz.magical.client.hud.HudSnapshot.Stamp;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Draws the {@code magical:sigil} layer from a {@link HudSnapshot}: one {@code drawSpecial} block of
 * quads on the sigil render type, then the cached strings through the GUI's own text batch.
 *
 * <p>The corner block is two bars, up to four glyphs and a stamp in front of each reading;
 * everything else in it is text. Nothing glows, blinks or turns: a cooling glyph is greyed from
 * the bottom and its key gives way to the seconds, a charging one fills from the bottom, a stamp
 * is a glyph at nine units drawn whole, and the only things that move are the two bars easing
 * toward their pools.
 *
 * <p>Every method here is a pure function of the snapshot, the tweens and the clock. Nothing is
 * allocated per frame - the batch, the text sink and the frame callback are reused - and nothing
 * here asks {@code Minecraft} for anything but the font. {@link #emitAll} and {@link #text} take
 * their inputs explicitly so a test can run them into a counting sink without a game.
 */
public final class SigilRenderer {

    /** Where a slot's charge fraction comes from; the live one asks the hold inputs. */
    @FunctionalInterface
    public interface ChargeSource {
        float fraction(int slot, float partialTick);
    }

    public static final ChargeSource LIVE_CHARGE = (slot, partial) -> {
        float charge = Math.max(MagicBarrageInput.chargeFraction(slot, partial), SpaceAuthorityInput.chargeFraction(slot, partial));
        return GenericHoldInput.isHeld(slot) ? Math.max(charge, 1.0F) : charge;
    };

    /** An empty slot's key is drawn at this fraction of the text's opacity. */
    private static final float EMPTY_KEY_ALPHA = 0.5F;
    /** SLOT's paramB bit for a charging glyph; the two below it carry the cell's high bits. */
    private static final int SLOT_CHARGING = 4;

    private static final HudBatch BATCH = new HudBatch();
    private static final HudText.OnGraphics TEXT = new HudText.OnGraphics();
    private static final Frame FRAME = new Frame();
    private static int lastQuads;
    private static int lastTextDraws;

    private SigilRenderer() {}

    /**
     * The layer entry point. With {@code corner} false the corner block stands down and only what
     * sits round the crosshair is drawn - the case of a bottom corner while the loadout rail is up.
     */
    public static void render(GuiGraphics graphics, DeltaTracker delta, boolean corner) {
        HudSnapshot snapshot = HudState.snapshot();
        if (snapshot == null || !snapshot.options().enabled()) {
            lastQuads = 0;
            lastTextDraws = 0;
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float now = HudState.now(partial);
        FRAME.set(graphics, snapshot, now, partial, corner);
        graphics.drawSpecial(FRAME);
        TEXT.begin(graphics, Minecraft.getInstance().font);
        text(TEXT, snapshot, now, partial, LIVE_CHARGE, corner);
        lastQuads = BATCH.quads();
        lastTextDraws = TEXT.draws();
    }

    public static int lastQuads() {
        return lastQuads;
    }

    public static int lastTextDraws() {
        return lastTextDraws;
    }

    /** The reusable {@code drawSpecial} callback: no lambda is captured per frame. */
    private static final class Frame implements Consumer<MultiBufferSource> {
        private GuiGraphics graphics;
        private HudSnapshot snapshot;
        private float now;
        private float partial;
        private boolean corner;

        void set(GuiGraphics graphics, HudSnapshot snapshot, float now, float partial, boolean corner) {
            this.graphics = graphics;
            this.snapshot = snapshot;
            this.now = now;
            this.partial = partial;
            this.corner = corner;
        }

        @Override
        public void accept(MultiBufferSource buffers) {
            VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.hudSigil());
            BATCH.begin(consumer, graphics.pose().last().pose());
            emitAll(BATCH, snapshot, now, partial, LIVE_CHARGE, corner);
        }
    }

    // ---- quads ----------------------------------------------------------------------------------

    /** Every quad of the sigil layer, in paint order: the two bars, the glyphs, the stamps, the status chips. */
    public static void emitAll(HudBatch b, HudSnapshot s, float now, float partial, ChargeSource charge) {
        emitAll(b, s, now, partial, charge, true);
    }

    /** The same, with the corner block left out when {@code corner} is false; the chips are always drawn. */
    public static void emitAll(HudBatch b, HudSnapshot s, float now, float partial, ChargeSource charge, boolean corner) {
        float alpha = s.options().opacity() * HudState.fade().sample(partial);
        if (alpha <= 0.003F) {
            return;
        }
        if (corner) {
            cornerBlock(b, s, now, partial, charge, alpha);
        }
        chips(b, s, now, alpha);
    }

    private static void cornerBlock(HudBatch b, HudSnapshot s, float now, float partial, ChargeSource charge, float alpha) {
        HudLayout layout = s.layout();
        bar(b, layout.manaBar(), s.manaFill(), alpha, HudState.mana().sample(partial));
        bar(b, layout.barrierBar(), HudPalette.BARRIER, alpha, HudState.barrier().sample(partial));
        if (s.slotBand()) {
            int side = Math.min(63, layout.glyphSize());
            for (Slot slot : s.slots()) {
                if (!slot.empty()) {
                    glyph(b, slot, side, alpha, now, charge.fraction(slot.slot(), partial));
                }
            }
        }
        for (Readout readout : s.readouts()) {
            if (readout.stamp() != null) {
                stamp(b, readout, alpha);
            }
        }
    }

    /** A reading's stamp: a skill glyph at nine units, whole, one unit above its line at the token's left end. */
    private static void stamp(HudBatch b, Readout readout, float alpha) {
        Stamp stamp = readout.stamp();
        int x = readout.at().x();
        int y = readout.at().y() - 1;
        b.rect(x, y, x + HudLayout.STAMP, y + HudLayout.STAMP, stamp.ink(), alpha, HudKind.SLOT,
                stamp.cell() & 63, stamp.cell() >> 6 & 3, 0.0F, HudLayout.STAMP, 0);
    }

    private static void bar(HudBatch b, Rect bar, int rgb, float alpha, float fill) {
        b.rect(bar.x(), bar.y(), bar.right(), bar.bottom(), rgb, alpha, HudKind.BAR, 0, 0, fill, 0, 0);
    }

    /**
     * A slot's glyph: greyed from the bottom while cooling, lit from the bottom while charging. The
     * cooldown wins: a hold input counts a held key whether or not the skill can fire, and a glyph
     * lit whole by a key held through its cooldown reads as ready when it is not.
     */
    private static void glyph(HudBatch b, Slot slot, int side, float alpha, float now, float charge) {
        float cooling = cooldownRemaining(slot, now);
        boolean charging = cooling <= 0.0F && charge > 0.0F;
        float phase = inkPhase(slot, charging ? Math.min(1.0F, charge) : cooling);
        int paramB = (slot.cell() >> 6 & 3) | (charging ? SLOT_CHARGING : 0);
        Rect at = slot.glyph();
        b.rect(at.x(), at.y(), at.right(), at.bottom(), slot.ink(), alpha, HudKind.SLOT, slot.cell() & 63, paramB, phase, side, 0);
    }

    /**
     * The phase that puts the shader's line between grey and lit a {@code fraction} of the way
     * across the rows the glyph's ink covers, rather than across its square, so the whole of a
     * cooldown is spent crossing ink. A bar across the middle of its square is a ninth of it tall:
     * counted over the square, it read as ready for the last two fifths of its cooldown.
     */
    static float inkPhase(Slot slot, float fraction) {
        if (fraction <= 0.0F) {
            return 0.0F;
        }
        return 1.0F - slot.inkTo() + (slot.inkTo() - slot.inkFrom()) * fraction;
    }

    /** The fraction of a slot's cooldown still to run at {@code now}, extrapolated from the last packet. */
    static float cooldownRemaining(Slot slot, float now) {
        if (!slot.onCooldown()) {
            return 0.0F;
        }
        float left = slot.cooldownRemaining() - (now - slot.cooldownStart());
        return Math.max(0.0F, Math.min(1.0F, left / slot.cooldownTotal()));
    }

    private static void chips(HudBatch b, HudSnapshot s, float now, float alpha) {
        for (Chip chip : s.chips()) {
            Rect bounds = chip.bounds();
            float left = chip.initialTicks() - (now - chip.startTick());
            float remaining = chip.initialTicks() <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, left / chip.initialTicks()));
            int paramB = (chip.emblemCell() >> 6 & 1) | (Math.min(chip.amplifier(), 15) << 1);
            int mode = (chip.harmful() ? HudKind.MODE_INK : 0) | (left < 40.0F ? HudKind.MODE_ALT : 0);
            b.square(bounds.centreX(), bounds.centreY(), bounds.w() / 2.0F, chip.color(), alpha, HudKind.CHIP,
                    chip.emblemCell() & 63, paramB, remaining, mode);
        }
    }

    // ---- text -----------------------------------------------------------------------------------

    /** Every string of the sigil layer, after the quads so it lands on top of them. */
    public static void text(HudText text, HudSnapshot s, float now, float partial, ChargeSource charge) {
        text(text, s, now, partial, charge, true);
    }

    /** The same, with the corner block's strings left out when {@code corner} is false. */
    public static void text(HudText text, HudSnapshot s, float now, float partial, ChargeSource charge, boolean corner) {
        float alpha = s.options().opacity() * HudState.fade().sample(partial);
        if (alpha <= 0.02F) {
            return;
        }
        int a = Math.round(alpha * 255.0F);
        HudLayout layout = s.layout();
        if (corner) {
            for (Readout count : s.pools()) {
                text.draw(count.text().text(), count.at().x(), count.at().y(), HudPalette.argb(count.text().color(), a));
            }
            if (s.slotBand()) {
                for (Slot slot : s.slots()) {
                    cell(text, s, slot, now, partial, charge, a);
                }
            }
            for (Readout readout : s.readouts()) {
                text.draw(readout.text().text(), readout.textX(), readout.at().y(), HudPalette.argb(readout.text().color(), a));
            }
        }
        Label debug = HudState.debugLabel();
        if (debug != null) {
            // Above the hotbar rows, so the readout never sits on the inventory it is measuring around.
            text.draw(debug.text(), layout.guiWidth() - debug.width() - 4, layout.guiHeight() - HudLayout.HOTBAR_RESERVE - HudLayout.TEXT_H, HudPalette.argb(debug.color(), 255));
        }
    }

    /**
     * What sits under a glyph: the seconds while it cools, otherwise its key - muted, lit while it
     * charges and for a moment after it comes back, half-faded when nothing is bound to it. Compact
     * mode keeps the seconds and drops the keys.
     */
    private static void cell(HudText text, HudSnapshot s, Slot slot, float now, float partial, ChargeSource charge, int alpha) {
        Rect at = slot.text();
        Label seconds = HudState.slotSeconds(slot.slot());
        if (seconds != null && cooldownRemaining(slot, now) > 0.0F) {
            text.drawCentered(seconds.text(), seconds.width(), at.centreX(), at.y(), HudPalette.argb(seconds.color(), alpha));
            return;
        }
        if (s.options().compact()) {
            return;
        }
        Label key = slot.key();
        int color = key.color();
        int keyAlpha = alpha;
        if (slot.empty()) {
            keyAlpha = Math.round(alpha * EMPTY_KEY_ALPHA);
        } else if (charge.fraction(slot.slot(), partial) > 0.0F || now - slot.readyAtTick() < HudState.READY_KEY_TICKS) {
            color = HudPalette.TEXT_PRIMARY;
        }
        text.drawCentered(key.text(), key.width(), at.centreX(), at.y(), HudPalette.argb(color, keyAlpha));
    }
}
