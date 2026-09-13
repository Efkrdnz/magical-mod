package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.RuleFlash.Shot;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Draws the {@code magical:rule_flash} layer from the {@link RuleFlash} shot: a plate that pops
 * in above the crosshair, the formula at twice the text size with the changed symbol tinted and
 * moving by the kind of change, one {@code RULE_MARK} quad behind that symbol playing the kind's
 * mark, and a caption under the plate. Three quads and at most five strings, only while a shot
 * is live; a pure function of the shot, the layout and the clock, allocation-free.
 *
 * <p>The timeline is the one the shader plays: the plate pops until {@code FLASH_POP_END}, the
 * symbol and the mark play until {@code FLASH_MARK_END}, everything dissolves from
 * {@code FLASH_OUT_START}. Under reduced motion nothing moves: the symbol is drawn where it ends
 * up and the mark is asked for its settled state.
 */
public final class RuleFlashRenderer {

    public static final int FORMULA_SCALE = 2;
    /** The mark quad's half-size in GUI px: the shader's unit. */
    static final float MARK_HALF = 20.0F;
    /** The formula row's top, relative to the plate's centre, so the glyph cell at 2x is centred. */
    private static final float FORMULA_TOP = -8.0F;
    /** A glyph cell is eight rows; the symbol scales about the middle of it. */
    private static final float GLYPH_CELL = 8.0F;
    private static final float POP_FROM = 0.72F;
    private static final float PLATE_ALPHA = 0.82F;
    private static final float CAPTION_ALPHA = 0.85F;
    private static final int PLATE_CORNER = 8;
    private static final int PLATE_UNDERLINE = 2;
    private static final int CAPTION_PAD = 3;
    private static final float SYMBOL_LIFT = 3.0F;
    private static final float STRUCK_ALPHA = 0.15F;
    private static final float SURGE_OVERSHOOT = 0.8F;
    private static final float SURGE_SETTLE = 0.1F;
    /** A mirrored symbol thinner than this would be a singular scale; it is skipped for a frame. */
    private static final float FLIP_MIN = 0.02F;
    private static final float EASE_BACK_C1 = 1.70158F;

    private static final HudBatch BATCH = new HudBatch();
    private static final HudText.OnGraphics TEXT = new HudText.OnGraphics();
    private static final Frame FRAME = new Frame();

    private RuleFlashRenderer() {}

    /** The layer entry point: nothing at all when no shot is live. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Shot shot = RuleFlash.current();
        HudSnapshot snapshot = HudState.snapshot();
        if (shot == null || snapshot == null || !snapshot.options().enabled()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float now = HudState.now(partial);
        FRAME.set(graphics, shot, snapshot.layout(), now, partial, snapshot.options());
        graphics.drawSpecial(FRAME);
        TEXT.begin(graphics, Minecraft.getInstance().font);
        text(TEXT, shot, snapshot.layout(), now, partial, snapshot.options());
    }

    /** The reusable {@code drawSpecial} callback: no lambda is captured per frame. */
    private static final class Frame implements Consumer<MultiBufferSource> {
        private GuiGraphics graphics;
        private Shot shot;
        private HudLayout layout;
        private float now;
        private float partial;
        private HudOptions options;

        void set(GuiGraphics graphics, Shot shot, HudLayout layout, float now, float partial, HudOptions options) {
            this.graphics = graphics;
            this.shot = shot;
            this.layout = layout;
            this.now = now;
            this.partial = partial;
            this.options = options;
        }

        @Override
        public void accept(MultiBufferSource buffers) {
            VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.hudSigil());
            BATCH.begin(consumer, graphics.pose().last().pose());
            emit(BATCH, shot, layout, now, partial, options);
        }
    }

    // ---- the timeline ---------------------------------------------------------------------------

    private static float clamp01(float t) {
        return t < 0.0F ? 0.0F : Math.min(1.0F, t);
    }

    private static float easeOut(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u;
    }

    private static float easeOutBack(float t) {
        float u = t - 1.0F;
        return 1.0F + (EASE_BACK_C1 + 1.0F) * u * u * u + EASE_BACK_C1 * u * u;
    }

    static float pop(float phase) {
        return clamp01(phase / HudKind.FLASH_POP_END);
    }

    /** The mark's own window of the timeline; under reduced motion the mark is always settled. */
    static float mark(float phase, boolean reduced) {
        return reduced ? 1.0F : clamp01((phase - HudKind.FLASH_POP_END) / (HudKind.FLASH_MARK_END - HudKind.FLASH_POP_END));
    }

    static float out(float phase) {
        return clamp01((phase - HudKind.FLASH_OUT_START) / (1.0F - HudKind.FLASH_OUT_START));
    }

    /** The whole flash's opacity: in with the pop, out with the dissolve, under the HUD's own envelope. */
    static float alpha(float phase, HudOptions options, float partial) {
        return easeOut(pop(phase)) * (1.0F - out(phase)) * options.opacity() * HudState.fade().sample(partial);
    }

    /** The plate's scale about its centre as it pops; reduced motion skips the overshoot. */
    static float popScale(float phase, boolean reduced) {
        return reduced ? 1.0F : POP_FROM + (1.0F - POP_FROM) * easeOutBack(pop(phase));
    }

    /** The formula's width at its drawn size. */
    static int contentWidth(Shot shot) {
        return FORMULA_SCALE * (shot.before().width() + shot.symbol().width() + shot.after().width());
    }

    /** The changed symbol's centre, relative to the plate's centre, before the pop scale. */
    private static float symbolOffset(Shot shot) {
        return -contentWidth(shot) * 0.5F + FORMULA_SCALE * (shot.before().width() + shot.symbol().width() * 0.5F);
    }

    // ---- quads ----------------------------------------------------------------------------------

    /** The plate, the mark behind the symbol, the caption's plate; in paint order. */
    public static void emit(HudBatch b, Shot shot, HudLayout layout, float now, float partial, HudOptions options) {
        float phase = shot.phase(now);
        if (phase >= 1.0F || !options.enabled()) {
            return;
        }
        float alpha = alpha(phase, options, partial);
        if (alpha <= 0.003F) {
            return;
        }
        boolean reduced = options.reducedMotion();
        float ps = popScale(phase, reduced);
        Rect plate = layout.ruleFlashPlate(contentWidth(shot));
        float cx = plate.centreX();
        float cy = plate.centreY();
        float hw = plate.w() * 0.5F * ps;
        float hh = plate.h() * 0.5F * ps;
        b.rect(cx - hw, cy - hh, cx + hw, cy + hh, shot.tint(), alpha * PLATE_ALPHA, HudKind.PLATE,
                PLATE_CORNER, PLATE_UNDERLINE, 1.0F, HudKind.MODE_INK);

        float mx = cx + symbolOffset(shot) * ps;
        float half = MARK_HALF * ps;
        int count = Math.min(63, FORMULA_SCALE * shot.symbol().width());
        int paramB = shot.change().id() | shot.variant() << 3;
        b.rect(mx - half, cy - half, mx + half, cy + half, shot.tint(), alpha, HudKind.RULE_MARK,
                count, paramB, phase, shot.seed(), reduced ? HudKind.MODE_ALT : 0);

        Rect caption = layout.ruleFlashCaption();
        int w = Math.min(caption.w(), shot.caption().width() + 2 * CAPTION_PAD);
        int x = caption.centreX() - w / 2;
        b.rect(x, caption.y(), x + w, caption.bottom(), HudPalette.CHROME_RIM, alpha * CAPTION_ALPHA * PLATE_ALPHA, HudKind.PLATE,
                PLATE_CORNER, 0, 1.0F, HudKind.MODE_INK);
    }

    // ---- text -----------------------------------------------------------------------------------

    /** The formula in three pieces at twice the size, the symbol moving by kind, then the caption. */
    public static void text(HudText text, Shot shot, HudLayout layout, float now, float partial, HudOptions options) {
        float phase = shot.phase(now);
        if (phase >= 1.0F || !options.enabled()) {
            return;
        }
        float alpha = alpha(phase, options, partial);
        if (alpha <= 0.02F) {
            return;
        }
        int a = Math.round(alpha * 255.0F);
        boolean reduced = options.reducedMotion();
        float ps = popScale(phase, reduced);
        float scale = FORMULA_SCALE * ps;
        int content = contentWidth(shot);
        Rect plate = layout.ruleFlashPlate(content);
        float x = plate.centreX() - content * 0.5F * ps;
        float y = plate.centreY() + FORMULA_TOP * ps;

        Label before = shot.before();
        if (before.width() > 0) {
            text.drawScaled(before.text(), x, y, scale, scale, HudPalette.argb(before.color(), a));
        }
        float sx = x + before.width() * scale;
        symbol(text, shot, sx, y, scale, mark(phase, reduced), a);
        Label after = shot.after();
        if (after.width() > 0) {
            text.drawScaled(after.text(), sx + shot.symbol().width() * scale, y, scale, scale, HudPalette.argb(after.color(), a));
        }

        Label caption = shot.caption();
        Rect line = layout.ruleFlashCaption();
        text.drawCentered(caption.text(), caption.width(), line.centreX(), line.y(), HudPalette.argb(caption.color(), Math.round(a * CAPTION_ALPHA)));
    }

    /** The changed symbol, moved the way its kind of change moves it. */
    private static void symbol(HudText text, Shot shot, float x, float y, float scale, float mark, int a) {
        Label symbol = shot.symbol();
        float w = symbol.width() * scale;
        float h = GLYPH_CELL * scale;
        switch (shot.change()) {
            case RAISE -> text.drawScaled(symbol.text(), x, y - SYMBOL_LIFT * easeOut(mark), scale, scale, HudPalette.argb(symbol.color(), a));
            case LOWER -> text.drawScaled(symbol.text(), x, y + SYMBOL_LIFT * easeOut(mark), scale, scale, HudPalette.argb(symbol.color(), a));
            case SURGE -> {
                // Overshoots and settles a little larger than the rest of the formula.
                float s = 1.0F + SURGE_OVERSHOOT * (float) Math.sin(mark * Math.PI) + SURGE_SETTLE * mark;
                text.drawScaled(symbol.text(), x + w * (1.0F - s) * 0.5F, y + h * (1.0F - s) * 0.5F, scale * s, scale * s, HudPalette.argb(symbol.color(), a));
            }
            case FLIP -> {
                // Turns over about its own centre: squashes to a hairline as the mirror plane flares,
                // then grows back. It lands the right way round because the text render type culls
                // back faces, so a mirrored glyph would simply not draw.
                float k = Math.abs((float) Math.cos(mark * Math.PI));
                if (k >= FLIP_MIN) {
                    text.drawScaled(symbol.text(), x + w * (1.0F - k) * 0.5F, y, scale * k, scale, HudPalette.argb(symbol.color(), a));
                }
            }
            case ZERO -> {
                // Struck through and gone to a ghost, while a zero takes its place.
                text.drawScaled(symbol.text(), x, y, scale, scale, HudPalette.argb(symbol.color(), Math.round(a * (1.0F - (1.0F - STRUCK_ALPHA) * mark))));
                if (mark > 0.02F) {
                    Label zero = shot.zero();
                    text.drawScaled(zero.text(), x + (w - zero.width() * scale) * 0.5F, y, scale, scale, HudPalette.argb(zero.color(), Math.round(a * mark)));
                }
            }
            // Drains from the tint back to white: the rule is gone and the symbol is ordinary again.
            case RESTORE -> text.drawScaled(symbol.text(), x, y, scale, scale, HudPalette.argb(HudPalette.lift(symbol.color(), mark) & 0xFFFFFF, a));
            case LOCK, AIM -> text.drawScaled(symbol.text(), x, y, scale, scale, HudPalette.argb(symbol.color(), a));
        }
    }
}
