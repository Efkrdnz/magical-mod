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

    int draws();

    /** The real one: draws through the GUI's deferred text batch, after the sigil quads. */
    final class OnGraphics implements HudText {
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
            // With the drop shadow, as vanilla's HUD draws: it is what keeps a numeral legible over sky.
            graphics.drawString(font, text, x, y, argb, true);
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
        public int draws() {
            return draws;
        }
    }
}
