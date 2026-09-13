package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.GenericHoldInput;
import com.efkrdnz.magical.client.MagicBarrageInput;
import com.efkrdnz.magical.client.SpaceAuthorityInput;
import com.efkrdnz.magical.client.hud.HudLayout.Disc;
import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.client.hud.HudLayout.Segment;
import com.efkrdnz.magical.client.hud.HudSnapshot.Announcement;
import com.efkrdnz.magical.client.hud.HudSnapshot.Card;
import com.efkrdnz.magical.client.hud.HudSnapshot.Chip;
import com.efkrdnz.magical.client.hud.HudSnapshot.GaugeLine;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.HudSnapshot.Line;
import com.efkrdnz.magical.client.hud.HudSnapshot.Satellite;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.visual.CoreKind;
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
 * <p>Every method here is a pure function of the snapshot, the tweens and the clock. Nothing is
 * allocated per frame - the batch, the text sink and the frame callback are reused - and nothing
 * here asks {@code Minecraft} for anything but the font. {@link #emitAll} takes its inputs
 * explicitly so a test can run it into a counting sink without a game.
 */
public final class SigilRenderer {

    /** Where a card's charge fraction comes from; the live one asks the hold inputs. */
    @FunctionalInterface
    public interface ChargeSource {
        float fraction(int slot, float partialTick);
    }

    public static final ChargeSource LIVE_CHARGE = (slot, partial) -> {
        float charge = Math.max(MagicBarrageInput.chargeFraction(slot, partial), SpaceAuthorityInput.chargeFraction(slot, partial));
        return GenericHoldInput.isHeld(slot) ? Math.max(charge, 1.0F) : charge;
    };

    private static final int READY_FLASH_TICKS = 8;
    private static final float CORE_INTENSITY = 0.32F;
    private static final float PLATE_ALPHA = 0.7F;
    private static final int TAG_CORNER = 10;
    private static final int LINE_CORNER = 8;
    /** A text plate hugs its string by this much on each side. */
    private static final int PLATE_PAD = 3;

    private static final HudBatch BATCH = new HudBatch();
    private static final HudText.OnGraphics TEXT = new HudText.OnGraphics();
    private static final Frame FRAME = new Frame();
    private static int lastQuads;
    private static int lastTextDraws;

    private SigilRenderer() {}

    /** The layer entry point. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        HudSnapshot snapshot = HudState.snapshot();
        if (snapshot == null || !snapshot.options().enabled()) {
            lastQuads = 0;
            lastTextDraws = 0;
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float now = HudState.now(partial);
        FRAME.set(graphics, snapshot, now, partial);
        graphics.drawSpecial(FRAME);
        TEXT.begin(graphics, Minecraft.getInstance().font);
        text(TEXT, snapshot, now, partial);
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

        void set(GuiGraphics graphics, HudSnapshot snapshot, float now, float partial) {
            this.graphics = graphics;
            this.snapshot = snapshot;
            this.now = now;
            this.partial = partial;
        }

        @Override
        public void accept(MultiBufferSource buffers) {
            VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.hudSigil());
            BATCH.begin(consumer, graphics.pose().last().pose());
            emitAll(BATCH, snapshot, now, partial, LIVE_CHARGE);
        }
    }

    // ---- quads ----------------------------------------------------------------------------------

    /** Every quad of the sigil layer, in paint order. */
    public static void emitAll(HudBatch b, HudSnapshot s, float now, float partial, ChargeSource charge) {
        float alpha = s.options().opacity() * HudState.fade().sample(partial);
        if (alpha <= 0.003F) {
            return;
        }
        HudLayout layout = s.layout();
        rings(b, s, layout, partial, alpha);
        if (s.vessel() || s.corruption()) {
            reflection(b, s, layout, partial, alpha);
        }
        crown(b, s, partial, alpha);
        fan(b, s, layout, now, partial, alpha, charge);
        plates(b, s, layout, alpha);
        chips(b, s, now, alpha);
        announcements(b, s, now, alpha);
    }

    /** The lifecycle fraction of an announcement, 0 at ink-on, 1 gone. */
    private static float announcePhase(Announcement a, float now) {
        return Math.max(0.0F, Math.min(1.0F, (now - a.startTick()) / a.lifetime()));
    }

    private static void announcements(HudBatch b, HudSnapshot s, float now, float alpha) {
        for (Announcement a : s.announcements()) {
            float phase = announcePhase(a, now);
            if (phase >= 1.0F) {
                continue;
            }
            Rect emblem = a.emblem();
            float drawn = Math.min(1.0F, phase * 4.0F);
            b.square(emblem.centreX(), emblem.centreY(), emblem.w() / 2.0F, a.color(), alpha, HudKind.ANNOUNCE,
                    a.emblemCell() & 63, a.emblemCell() >> 6 & 1, phase, a.ink() ? HudKind.MODE_INK : 0);
            if (!s.options().compact()) {
                Rect text = a.text();
                float textAlpha = alpha * drawn * (1.0F - Math.max(0.0F, (phase - 0.75F) * 4.0F)) * PLATE_ALPHA;
                textPlate(b, text, a.title().width(), s.layout().sign(), a.color(), textAlpha, 4);
            }
        }
    }

    private static void rings(HudBatch b, HudSnapshot s, HudLayout layout, float partial, float alpha) {
        float cx = layout.cx();
        float cy = layout.cy();
        // XP hairline, filling clockwise from nine o'clock.
        float xpHalf = layout.xpOuter() / HudKind.RING_OUTER;
        b.squareTurned(cx, cy, xpHalf, 3, HudPalette.XP, alpha * (s.maxLevel() ? 0.9F : 0.75F), HudKind.RING_METER,
                0, HudKind.widthClass(layout.xpWidth(), xpHalf), HudState.xp().sample(partial), 0);
        // Mana: the school's colour, its band pattern as notches, a hot head, and a pulse when low.
        float manaHalf = layout.manaOuter() / HudKind.RING_OUTER;
        float mana = HudState.mana().sample(partial);
        b.square(cx, cy, manaHalf, s.manaColor(), alpha, HudKind.RING_METER,
                s.manaNotches(), HudKind.widthClass(layout.manaWidth(), manaHalf) | 8, mana, mana < HudState.LOW_MANA ? HudKind.MODE_ALT : 0);
        // Barrier: fixed colour, eight notches.
        float barrierHalf = layout.barrierOuter() / HudKind.RING_OUTER;
        b.square(cx, cy, barrierHalf, HudPalette.BARRIER, alpha * 0.95F, HudKind.RING_METER,
                HudLayout.BARRIER_NOTCHES, HudKind.widthClass(layout.barrierWidth(), barrierHalf) | 8, HudState.barrier().sample(partial), 0);
        // Mana charge halo, only while Gluttony is charging.
        float halo = HudState.halo().sample(partial);
        if (halo > 0.002F) {
            float haloHalf = layout.haloOuter() / HudKind.RING_OUTER;
            b.square(cx, cy, haloHalf, MagicPassiveContent.SIN_GLUTTONY.color(), alpha, HudKind.RING_METER,
                    5, HudKind.widthClass(1.0F * layout.scale(), haloHalf) | 8, halo, 0);
        }
        // The core: a dark disc so the numerals read, then the school's core glyph faintly under them.
        float coreR = layout.core().r();
        b.rect(cx - coreR, cy - coreR, cx + coreR, cy + coreR, HudPalette.CHROME_RIM, alpha * 0.92F, HudKind.PLATE, 32, 0, 1.0F, HudKind.MODE_INK);
        int coreMode = s.coreStyle() == CoreKind.VOID_PIT.id() ? HudKind.MODE_INK : 0;
        b.square(cx, cy, coreR, s.coreColor(), alpha * CORE_INTENSITY, HudKind.CORE, 1, s.coreStyle(), 0.0F, coreMode);
    }

    private static void reflection(HudBatch b, HudSnapshot s, HudLayout layout, float partial, float alpha) {
        Segment line = layout.waterline();
        b.line(line.x0(), line.y0(), line.x1(), line.y1(), 2.0F * layout.scale(), HudPalette.CHROME_RIM, alpha * 0.45F, 0, 1.0F, 0);
        float cx = layout.cx();
        float cy = layout.reflectionCy();
        if (s.vessel()) {
            float half = layout.vesselOuter() / HudKind.RING_OUTER;
            b.halfDiscTop(cx, cy, half, HudPalette.vessel().bright(), alpha, HudKind.RING_METER,
                    0, HudKind.widthClass(layout.vesselWidth(), half) | 8, HudState.vessel().sample(partial) * 0.5F, HudKind.MODE_INK);
        }
        if (s.corruption()) {
            float half = layout.corruptionOuter() / HudKind.RING_OUTER;
            float fraction = HudState.corruption().sample(partial);
            b.halfDiscTop(cx, cy, half, HudPalette.corruption().bright(), alpha, HudKind.RING_METER,
                    HudLayout.CORRUPTION_RUNGS * 2, HudKind.widthClass(layout.corruptionWidth(), half) | 8 | 16, fraction * 0.5F,
                    HudKind.MODE_INK | (fraction >= 0.999F ? HudKind.MODE_ALT : 0));
        }
    }

    private static void crown(HudBatch b, HudSnapshot s, float partial, float alpha) {
        for (Satellite satellite : s.satellites()) {
            Disc seat = satellite.bounds();
            float gauge = HudState.sinGauge(satellite.seat()).sample(partial);
            int color = satellite.rested() ? HudPalette.lift(satellite.color(), 0.35F) & 0xFFFFFF : satellite.color();
            b.square(seat.cx(), seat.cy(), seat.r(), color, alpha, HudKind.SATELLITE,
                    satellite.emblemCell() & 63, satellite.emblemCell() >> 6 & 1, gauge, gauge >= 0.999F ? HudKind.MODE_ALT : 0);
        }
    }

    private static void fan(HudBatch b, HudSnapshot s, HudLayout layout, float now, float partial, float alpha, ChargeSource charge) {
        boolean locked = now < s.lockUntilTick();
        for (Card card : s.cards()) {
            Disc disc = card.bounds();
            Segment spoke = layout.spoke(card.slot());
            b.line(spoke.x0(), spoke.y0(), spoke.x1(), spoke.y1(), 1.5F * layout.scale(),
                    card.empty() ? HudPalette.CHROME_RIM : card.color(), alpha * (card.empty() ? 0.3F : locked ? 0.35F : 0.6F),
                    card.forbidden() ? 6 : 0, 1.0F, 0);

            float remaining = cooldownRemaining(card, now);
            float sinceReady = now - card.readyAtTick();
            boolean flashing = sinceReady >= 0.0F && sinceReady < READY_FLASH_TICKS && remaining <= 0.0F;
            float chargeFraction = card.empty() ? 0.0F : charge.fraction(card.slot(), partial);
            int paramB = (card.emblemCell() >> 6 & 1) | (card.empty() ? 2 : 0) | (chargeFraction > 0.0F ? 4 : 0) | (locked ? 8 : 0);
            int mode = card.forbidden() ? HudKind.MODE_INK : 0;
            float phase = remaining;
            if (flashing) {
                mode = HudKind.MODE_ALT;
                phase = 1.0F - sinceReady / READY_FLASH_TICKS;
            }
            // Forbidden cooldowns bleed back: what remains is lit, what has elapsed is ink.
            if (card.forbidden() && !flashing) {
                phase = 1.0F - remaining;
            }
            b.square(disc.cx(), disc.cy(), disc.r(), card.color(), alpha, HudKind.CARD, Math.max(0, card.emblemCell()) & 63, paramB, phase, mode);
            if (chargeFraction > 0.0F) {
                b.square(disc.cx(), disc.cy(), disc.r(), card.color(), alpha, HudKind.CHARGE_RING, 4, 0, chargeFraction,
                        chargeFraction >= 0.999F ? HudKind.MODE_ALT : 0);
            }
            Rect tag = card.tag();
            b.rect(tag.x(), tag.y(), tag.right(), tag.bottom(), card.empty() ? HudPalette.CHROME_RIM : card.color(),
                    alpha * PLATE_ALPHA, HudKind.PLATE, TAG_CORNER, 0, 1.0F, HudKind.MODE_INK);
        }
    }

    /** The fraction of a card's cooldown still to run at {@code now}, extrapolated from the last packet. */
    private static float cooldownRemaining(Card card, float now) {
        if (!card.onCooldown()) {
            return 0.0F;
        }
        float left = card.cooldownRemaining() - (now - card.cooldownStart());
        return Math.max(0.0F, Math.min(1.0F, left / card.cooldownTotal()));
    }

    private static void plates(HudBatch b, HudSnapshot s, HudLayout layout, float alpha) {
        int sign = layout.sign();
        Rect level = s.level().at();
        b.rect(level.x(), level.y(), level.right(), level.bottom(), HudPalette.XP, alpha * PLATE_ALPHA, HudKind.PLATE, TAG_CORNER, s.maxLevel() ? 1 : 0, 1.0F, HudKind.MODE_INK);
        for (int i = 0; i < s.captions().length; i++) {
            Line caption = s.captions()[i];
            textPlate(b, caption.at(), caption.label().width(), sign, i == 0 ? HudPalette.CHROME_RIM : caption.label().color(), alpha * PLATE_ALPHA, i == 0 ? 4 : 0);
        }
        // The readouts: each on its own plate in its sin's colour, with the colour as a bar on the fan side.
        for (GaugeLine gauge : s.gauges()) {
            textPlate(b, gauge.at(), gauge.text().width(), sign, gauge.text().color(), alpha * PLATE_ALPHA, sign > 0 ? 8 : 0);
        }
    }

    /** An ink plate hugging a string that reads away from the sigil: left-aligned on the left, right-aligned on the right. */
    private static void textPlate(HudBatch b, Rect line, int textWidth, int sign, int color, float alpha, int accentBits) {
        int w = Math.min(line.w(), textWidth + 2 * PLATE_PAD);
        int x = sign > 0 ? line.x() - PLATE_PAD + 1 : line.right() - w + PLATE_PAD - 1;
        b.rect(x, line.y(), x + w, line.bottom(), color, alpha, HudKind.PLATE, LINE_CORNER, accentBits, 1.0F, HudKind.MODE_INK);
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
    public static void text(HudText text, HudSnapshot s, float now, float partial) {
        float alpha = s.options().opacity() * HudState.fade().sample(partial);
        if (alpha <= 0.02F) {
            return;
        }
        int a = Math.round(alpha * 255.0F);
        HudLayout layout = s.layout();
        for (Line line : s.coreLines()) {
            drawCentred(text, line.label(), line.at(), a);
        }
        drawCentred(text, s.level().label(), s.level().at(), a);
        for (Card card : s.cards()) {
            drawCentred(text, card.key(), card.tag(), a);
            Label seconds = HudState.cardSeconds(card.slot());
            if (seconds != null && cooldownRemaining(card, now) > 0.0F) {
                drawCentred(text, seconds, card.text(), a);
            }
        }
        for (Line caption : s.captions()) {
            drawLine(text, caption.label(), caption.at(), layout.sign(), a);
        }
        for (GaugeLine gauge : s.gauges()) {
            drawLine(text, gauge.text(), gauge.at(), layout.sign(), a);
        }
        if (!s.options().compact()) {
            for (Announcement announcement : s.announcements()) {
                float phase = announcePhase(announcement, now);
                float visible = Math.min(1.0F, phase * 4.0F) * (1.0F - Math.max(0.0F, (phase - 0.75F) * 4.0F));
                if (phase < 1.0F && visible > 0.05F) {
                    drawLine(text, announcement.title(), announcement.text(), layout.sign(), Math.round(a * visible));
                }
            }
        }
        Label debug = HudState.debugLabel();
        if (debug != null) {
            // Above the hotbar rows, so the readout never sits on the inventory it is measuring around.
            text.draw(debug.text(), layout.guiWidth() - debug.width() - 4, layout.guiHeight() - HudLayout.HOTBAR_RESERVE - HudLayout.TEXT_H, HudPalette.argb(debug.color(), 255));
        }
    }

    private static void drawCentred(HudText text, Label label, Rect in, int alpha) {
        text.drawCentered(label.text(), label.width(), in.centreX(), in.y() + (in.h() - HudLayout.TEXT_H) / 2, HudPalette.argb(label.color(), alpha));
    }

    /** A line reads from the sigil outward: left-aligned on the left, right-aligned on the right. */
    private static void drawLine(HudText text, Label label, Rect in, int sign, int alpha) {
        int x = sign > 0 ? in.x() : in.right() - label.width();
        text.draw(label.text(), x, in.y(), HudPalette.argb(label.color(), alpha));
    }
}
