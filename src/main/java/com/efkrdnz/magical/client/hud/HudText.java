package com.efkrdnz.magical.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

/**
 * The only writer of HUD text. Text goes through here rather than straight to {@link GuiGraphics}
 * so the frame can be counted, and so a test can render a snapshot into a counting stub without a
 * GL context.
 *
 * <p>Every string the HUD draws is a {@link FormattedCharSequence} cached in the snapshot with its
 * width already measured; nothing is formatted or measured per frame.
 */
public interface HudText {

    void draw(FormattedCharSequence text, int x, int y, int argb);

    /** Centres a string whose width was measured when the snapshot was built. */
    default void drawCentered(FormattedCharSequence text, int width, int centreX, int y, int argb) {
        draw(text, centreX - width / 2, y, argb);
    }

    /**
     * A string scaled about its own top-left corner, which lands at (x, y) in GUI units. The two
     * axes scale separately so a symbol can squash; keep both positive, since the text render type
     * culls back faces and a mirrored string does not draw.
     */
    void drawScaled(FormattedCharSequence text, float x, float y, float scaleX, float scaleY, int argb);

    int draws();

    /**
     * The real one: draws through the GUI's deferred text batch, after the sigil quads.
     *
     * <p>A string whose alpha is under four is not drawn at all, because vanilla's font reads such
     * an alpha as "no alpha given" and draws the string opaque: a key at half of a fading HUD would
     * flash solid white for a frame at the start of every fade-in.
     */
    final class OnGraphics implements HudText {
        /** Font.adjustColor treats an alpha below this as missing and substitutes 255. */
        static final int MIN_ALPHA = 4;

        private GuiGraphics graphics;
        private Font font;
        private int draws;

        public OnGraphics begin(GuiGraphics graphics, Font font) {
            this.graphics = graphics;
            this.font = font;
            this.draws = 0;
            return this;
        }

        @Override
        public void draw(FormattedCharSequence text, int x, int y, int argb) {
            if (argb >>> 24 < MIN_ALPHA) {
                return;
            }
            // With the drop shadow, as vanilla's HUD draws: it is what keeps a numeral legible over sky.
            graphics.drawString(font, text, x, y, argb, true);
            draws++;
        }

        @Override
        public void drawScaled(FormattedCharSequence text, float x, float y, float scaleX, float scaleY, int argb) {
            if (argb >>> 24 < MIN_ALPHA) {
                return;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(scaleX, scaleY, 1.0F);
            graphics.drawString(font, text, 0.0F, 0.0F, argb, true);
            graphics.pose().popPose();
            draws++;
        }

        @Override
        public int draws() {
            return draws;
        }
    }

    /** Counts and discards; for the budget test. */
    final class Counting implements HudText {
        private int draws;

        @Override
        public void draw(FormattedCharSequence text, int x, int y, int argb) {
            draws++;
        }

        @Override
        public void drawScaled(FormattedCharSequence text, float x, float y, float scaleX, float scaleY, int argb) {
            draws++;
        }

        @Override
        public int draws() {
            return draws;
        }
    }
}
