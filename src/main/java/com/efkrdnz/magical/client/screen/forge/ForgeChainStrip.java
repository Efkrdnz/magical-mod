package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.forge.chain.ForgeChainBuilder.CommittedGlyph;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.chain.ForgeRules;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The committed chain, laid out in the order it was drawn: cell <i>n</i> holds the <i>n</i>th glyph.
 *
 * <p>It used to sort glyphs into fixed slots - grade, element, four form cells, temper, three
 * modifier cells. That read well while a chain was a bag of properties, but a chain is a program
 * now, and re-bucketing it would draw {@code pierce spin} and {@code spin pierce} identically when
 * they are different weapons. The strip has to show what the player actually drew.
 *
 * <p>A cell is marked when it holds a glyph the committed grade cannot pay for - the third form on
 * a two-form grade - or when it sits past the glyph budget of the whole chain.
 */
public final class ForgeChainStrip {

    /** Gap between two neighbouring cells. */
    public static final int GAP = 2;

    /** Cells in the strip: one per glyph the chain can hold. */
    public static final int CELLS = ForgeRules.MAX_GLYPHS;

    /**
     * How wide the strip may be: from its left edge to the right-hand column of the screen.
     *
     * <p>The strip has to stay one row. The band it sits in is only 22 pixels tall - the canvas
     * ends at 202 and the inventory panel is drawn from 224, eighteen above its first slot row to
     * make room for the label - so a second row would land on top of the inventory.
     */
    private static final int MAX_WIDTH = 220;

    /**
     * Side of one cell, in pixels, shrunk only as far as fitting every cell in one row demands.
     *
     * <p>Twelve cells come out at the 14 the strip has always used, so the strip looks exactly as
     * it did. A larger glyph budget buys narrower cells rather than a second row.
     */
    public static final int CELL = Math.min(14, (MAX_WIDTH - (CELLS - 1) * GAP) / CELLS);

    /** Total strip width, so the screen can hit-test the whole row in one call. */
    public static final int WIDTH = CELLS * CELL + (CELLS - 1) * GAP;

    /** Total strip height. One row, always. */
    public static final int HEIGHT = CELL;

    private static final int QUALITY_GOLD = 70;
    private static final int QUALITY_PALE = 25;
    private static final int PALE = 0xFFD8E4FF;
    private static final int POOR = 0xFFF38BA8;
    private static final int OVERFLOW_FRAME = 0xFFE06470;
    private static final int EMPTY_FRAME = 0xFF27354A;

    /** Frame of a cell holding a glyph carried over from the weapon, rather than one just drawn. */
    private static final int KEPT_FRAME = 0xFF5A4A22;

    /** How far a kept glyph icon is faded, so work drawn this session reads as the brighter. */
    private static final int KEPT_ICON_ALPHA = 0xA0;

    /** Side of the gold pip marking a kept cell in its top-left corner. */
    private static final int KEPT_PIP = 3;

    /** Draws the strip; {@code x}/{@code y} is the top-left of the first cell. */
    public void render(GuiGraphics graphics, int x, int y, List<CommittedGlyph> committed) {
        boolean[] overBudget = overBudget(committed);
        for (int cell = 0; cell < CELLS; cell++) {
            int cellX = columnX(x, cell);
            int cellY = y;
            boolean filled = cell < committed.size();
            boolean over = filled && overBudget[cell];
            boolean kept = filled && committed.get(cell).kept();
            drawFrame(graphics, cellX, cellY, over, kept);
            if (!filled) {
                continue;
            }
            CommittedGlyph glyph = committed.get(cell);
            int color = qualityColor(glyph.quality(), over);
            int iconColor = kept ? MagicalGuiStyle.withAlpha(color, KEPT_ICON_ALPHA) : color;
            ForgeGlyphLibrary.byId(glyph.id()).ifPresent(template ->
                    ForgeGlyphIcons.draw(graphics, template, cellX + 1, cellY + 1, CELL - 2, iconColor));
        }
    }

    /** The committed index under the cursor, or -1 when the cursor is not over a filled cell. */
    public int cellAt(double mouseX, double mouseY, int x, int y, List<CommittedGlyph> committed) {
        for (int cell = 0; cell < CELLS; cell++) {
            int cellX = columnX(x, cell);
            int cellY = y;
            if (mouseX >= cellX && mouseX < cellX + CELL && mouseY >= cellY && mouseY < cellY + CELL) {
                return cell < committed.size() ? cell : -1;
            }
        }
        return -1;
    }

    /** Centre of the cell holding {@code committedIndex}, for the commit fly-in animation. */
    public Optional<int[]> cellCentre(int committedIndex, int x, int y, List<CommittedGlyph> committed) {
        if (committedIndex < 0 || committedIndex >= Math.min(committed.size(), CELLS)) {
            return Optional.empty();
        }
        return Optional.of(new int[] {
                columnX(x, committedIndex) + CELL / 2,
                y + CELL / 2});
    }

    private static int columnX(int x, int cell) {
        return x + cell * (CELL + GAP);
    }

    /**
     * Which committed glyphs the grade cannot pay for.
     *
     * <p>Counted in draw order, so the later duplicate lights up rather than the whole category: on
     * a two-form grade it is the third form drawn that the player has to take back out.
     */
    static boolean[] overBudget(List<CommittedGlyph> committed) {
        boolean[] over = new boolean[Math.max(CELLS, committed.size())];
        ForgeGrade grade = committedGrade(committed);
        Map<GlyphCategory, Integer> used = new EnumMap<>(GlyphCategory.class);
        for (int index = 0; index < committed.size(); index++) {
            GlyphCategory category = committed.get(index).category();
            int taken = used.merge(category, 1, Integer::sum);
            over[index] = index >= ForgeRules.MAX_GLYPHS || taken > budget(category, grade);
        }
        return over;
    }

    private static int budget(GlyphCategory category, ForgeGrade grade) {
        return switch (category) {
            case GRADE, TEMPER -> 1;
            // Two at Mythic and above, so a fused pair does not read as a mistake.
            case ELEMENT -> grade.elementSlots();
            case FORM -> grade.formSlots();
            case MODIFIER -> grade.modifierSlots();
            case OPERATOR -> grade.operatorSlots();
        };
    }

    /** The grade already committed, or CRUDE so an untouched strip still shows its cheapest shape. */
    private static ForgeGrade committedGrade(List<CommittedGlyph> committed) {
        for (CommittedGlyph glyph : committed) {
            if (glyph.category() == GlyphCategory.GRADE) {
                Optional<ForgeGrade> grade = ForgeGrade.byName(glyph.id());
                if (grade.isPresent()) {
                    return grade.get();
                }
            }
        }
        return ForgeGrade.CRUDE;
    }

    /**
     * A kept cell gets a warmer frame and a gold corner pip on top of the ordinary cell art, so the
     * player can tell at a glance what came off the weapon from what they drew. The over-budget
     * frame still wins: a glyph the grade cannot pay for is the more urgent thing to say.
     */
    private static void drawFrame(GuiGraphics graphics, int x, int y, boolean over, boolean kept) {
        int frame = over ? OVERFLOW_FRAME : kept ? KEPT_FRAME : EMPTY_FRAME;
        graphics.fill(x - 1, y - 1, x + CELL + 1, y + CELL + 1, frame);
        graphics.fillGradient(x, y, x + CELL, y + CELL, 0xFF0A101D, 0xFF0E1526);
        if (over) {
            graphics.fill(x, y, x + CELL, y + CELL, 0x66050810);
        }
        if (kept) {
            graphics.fill(x, y, x + KEPT_PIP, y + KEPT_PIP,
                    MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0xCC));
        }
    }

    private static int qualityColor(int quality, boolean dimmed) {
        int base = quality >= QUALITY_GOLD ? MagicalGuiStyle.ACCENT_GOLD : quality >= QUALITY_PALE ? PALE : POOR;
        return dimmed ? MagicalGuiStyle.withAlpha(base, 0x77) : base;
    }
}
