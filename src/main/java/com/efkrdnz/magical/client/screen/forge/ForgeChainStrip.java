package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.forge.chain.ForgeChainBuilder.CommittedGlyph;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The committed chain, laid out as {@code [G][E] | [F F F F] | [T] | [M M M]} plus two overflow
 * cells for glyphs that do not fit their group. Cells the committed grade cannot pay for are
 * dimmed, so the player sees the shape of the recipe before the preview panel spells it out.
 */
public final class ForgeChainStrip {

    /** Side of one cell, in pixels. */
    public static final int CELL = 14;

    /** Gap between two neighbouring cells. */
    public static final int GAP = 2;

    /** Cells in the strip: eight category slots, one temper, and two overflow cells. */
    public static final int CELLS = 12;

    /** Total strip width, so the screen can hit-test the whole row in one call. */
    public static final int WIDTH = CELLS * CELL + (CELLS - 1) * GAP;

    private static final int GRADE_CELL = 0;
    private static final int ELEMENT_CELL = 1;
    private static final int FORM_FIRST = 2;
    private static final int FORM_COUNT = 4;
    private static final int TEMPER_CELL = 6;
    private static final int MODIFIER_FIRST = 7;
    private static final int MODIFIER_COUNT = 3;
    private static final int OVERFLOW_FIRST = 10;

    private static final int QUALITY_GOLD = 70;
    private static final int QUALITY_PALE = 25;
    private static final int PALE = 0xFFD8E4FF;
    private static final int POOR = 0xFFF38BA8;
    private static final int OVERFLOW_FRAME = 0xFFE06470;
    private static final int EMPTY_FRAME = 0xFF27354A;
    private static final int DIM_FRAME = 0xFF1A2333;

    /** Frame of a cell holding a glyph carried over from the weapon, rather than one just drawn. */
    private static final int KEPT_FRAME = 0xFF5A4A22;

    /** How far a kept glyph's icon is faded, so this session's drawn work reads as the brighter. */
    private static final int KEPT_ICON_ALPHA = 0xA0;

    /** Side of the gold pip marking a kept cell in its top-left corner. */
    private static final int KEPT_PIP = 3;

    /** Draws the strip; {@code x}/{@code y} is the top-left of the first cell. */
    public void render(GuiGraphics graphics, int x, int y, List<CommittedGlyph> committed) {
        int[] cells = assign(committed);
        ForgeGrade grade = committedGrade(committed);
        for (int cell = 0; cell < CELLS; cell++) {
            int cellX = x + cell * (CELL + GAP);
            boolean dimmed = isDimmed(cell, grade);
            boolean kept = cells[cell] >= 0 && committed.get(cells[cell]).kept();
            drawFrame(graphics, cellX, y, cell, dimmed, kept);
            if (cells[cell] < 0) {
                continue;
            }
            CommittedGlyph glyph = committed.get(cells[cell]);
            int color = qualityColor(glyph.quality(), dimmed);
            int iconColor = kept ? MagicalGuiStyle.withAlpha(color, KEPT_ICON_ALPHA) : color;
            ForgeGlyphLibrary.byId(glyph.id()).ifPresent(template ->
                    ForgeGlyphIcons.draw(graphics, template, cellX + 1, y + 1, CELL - 2, iconColor));
        }
    }

    /** The committed index under the cursor, or -1 when the cursor is not over a filled cell. */
    public int cellAt(double mouseX, double mouseY, int x, int y, List<CommittedGlyph> committed) {
        if (mouseY < y || mouseY >= y + CELL) {
            return -1;
        }
        int[] cells = assign(committed);
        for (int cell = 0; cell < CELLS; cell++) {
            int cellX = x + cell * (CELL + GAP);
            if (mouseX >= cellX && mouseX < cellX + CELL) {
                return cells[cell];
            }
        }
        return -1;
    }

    /** Centre of the cell holding {@code committedIndex}, for the commit fly-in animation. */
    public Optional<int[]> cellCentre(int committedIndex, int x, int y, List<CommittedGlyph> committed) {
        int[] cells = assign(committed);
        for (int cell = 0; cell < CELLS; cell++) {
            if (cells[cell] == committedIndex) {
                return Optional.of(new int[] {x + cell * (CELL + GAP) + CELL / 2, y + CELL / 2});
            }
        }
        return Optional.empty();
    }

    /** Cell assignment in draw order: each glyph takes the next free cell of its category. */
    static int[] assign(List<CommittedGlyph> committed) {
        int[] cells = new int[CELLS];
        Arrays.fill(cells, -1);
        for (int index = 0; index < committed.size(); index++) {
            int cell = nextFreeCell(cells, committed.get(index).category());
            if (cell >= 0) {
                cells[cell] = index;
            }
        }
        return cells;
    }

    private static int nextFreeCell(int[] cells, GlyphCategory category) {
        int free = switch (category) {
            case GRADE -> firstFree(cells, GRADE_CELL, 1);
            case ELEMENT -> firstFree(cells, ELEMENT_CELL, 1);
            case FORM -> firstFree(cells, FORM_FIRST, FORM_COUNT);
            case TEMPER -> firstFree(cells, TEMPER_CELL, 1);
            case MODIFIER -> firstFree(cells, MODIFIER_FIRST, MODIFIER_COUNT);
        };
        return free >= 0 ? free : firstFree(cells, OVERFLOW_FIRST, CELLS - OVERFLOW_FIRST);
    }

    private static int firstFree(int[] cells, int from, int count) {
        for (int cell = from; cell < from + count; cell++) {
            if (cells[cell] < 0) {
                return cell;
            }
        }
        return -1;
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

    private static boolean isDimmed(int cell, ForgeGrade grade) {
        if (cell >= FORM_FIRST && cell < FORM_FIRST + FORM_COUNT) {
            return cell - FORM_FIRST >= grade.formSlots();
        }
        if (cell >= MODIFIER_FIRST && cell < MODIFIER_FIRST + MODIFIER_COUNT) {
            return cell - MODIFIER_FIRST >= grade.modifierSlots();
        }
        return false;
    }

    /**
     * A kept cell gets a warmer frame and a gold corner pip on top of the ordinary cell art, so the
     * player can tell at a glance what came off the weapon from what they drew. The overflow frame
     * still wins: a glyph that does not fit its group is the more urgent thing to say.
     */
    private static void drawFrame(GuiGraphics graphics, int x, int y, int cell, boolean dimmed, boolean kept) {
        int frame = cell >= OVERFLOW_FIRST ? OVERFLOW_FRAME : dimmed ? DIM_FRAME : kept ? KEPT_FRAME : EMPTY_FRAME;
        graphics.fill(x - 1, y - 1, x + CELL + 1, y + CELL + 1, frame);
        graphics.fillGradient(x, y, x + CELL, y + CELL, 0xFF0A101D, 0xFF0E1526);
        if (dimmed) {
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
