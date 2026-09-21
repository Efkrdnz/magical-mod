package com.efkrdnz.magical.client.screen.causality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.causality.Cause;
import com.efkrdnz.magical.magic.causality.CausalNode;
import com.efkrdnz.magical.magic.causality.Condition;
import com.efkrdnz.magical.magic.causality.Effect;
import com.efkrdnz.magical.magic.causality.Weave;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The board laid out on paper, at every screen size the game can hand us.
 *
 * <p>Nothing drawn lands on anything else, everything sits inside its block and the block inside
 * the screen, a pin dropped at a point comes back at that point, and the board keeps one scale on
 * both axes so a wall of pins is never stretched. The layout has no font, so the words here have
 * the widths the default font gives them.
 */
class CausalBoardLayoutTest {

    private static final int[] WIDTHS = {320, 427, 480, 640, 854, 1280};
    private static final int[] HEIGHTS = {240, 300, 360, 480, 720};

    /** CAUSES, CONDITIONS, EFFECTS as the words measure. */
    private static final int[] KIND_WIDTHS = {36, 52, 40};
    private static final int SAVE_W = 20;
    private static final int CLEAR_W = 25;

    private static void assertDisjoint(List<Rect> rects) {
        for (int i = 0; i < rects.size(); i++) {
            for (int j = i + 1; j < rects.size(); j++) {
                Rect a = rects.get(i);
                Rect b = rects.get(j);
                assertFalse(a.overlaps(b), a.name() + " overlaps " + b.name() + ": " + say(a) + " vs " + say(b));
            }
        }
    }

    private static String say(Rect r) {
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    @Test
    void theBlockFitsEveryScreenAndIsCentredOnIt() {
        for (int w : WIDTHS) {
            for (int h : HEIGHTS) {
                Rect block = CausalBoardLayout.block(w, h);
                assertTrue(block.x() >= 0 && block.right() <= w, w + "x" + h + " " + say(block));
                assertTrue(block.y() >= 0 && block.bottom() <= h, w + "x" + h + " " + say(block));
                assertEquals(w - block.right(), block.x(), "centred across " + w);
            }
        }
    }

    @Test
    void nothingDrawnUnderTheBoardLandsOnAnythingElse() {
        for (int w : WIDTHS) {
            for (int h : HEIGHTS) {
                int blockW = CausalBoardLayout.blockWidth(w);
                int boardH = CausalBoardLayout.boardHeight(h);
                List<Rect> rects = new ArrayList<>(CausalBoardLayout.kinds(blockW, KIND_WIDTHS));
                rects.add(CausalBoardLayout.board(blockW, boardH));
                rects.add(CausalBoardLayout.caption(blockW, boardH, 0));
                rects.add(CausalBoardLayout.caption(blockW, boardH, 1));
                rects.addAll(CausalBoardLayout.inspector(blockW, boardH, SAVE_W, CLEAR_W).all());
                rects.add(CausalBoardLayout.reading(blockW, boardH, 0));
                rects.add(CausalBoardLayout.reading(blockW, boardH, 1));
                assertDisjoint(rects);
            }
        }
    }

    @Test
    void everythingSitsInsideTheBlockAndTheBlockInsideTheScreen() {
        for (int w : WIDTHS) {
            for (int h : HEIGHTS) {
                int blockW = CausalBoardLayout.blockWidth(w);
                int boardH = CausalBoardLayout.boardHeight(h);
                Rect block = new Rect("block", 0, 0, blockW, CausalBoardLayout.blockHeight(boardH));
                List<Rect> rects = new ArrayList<>(CausalBoardLayout.kinds(blockW, KIND_WIDTHS));
                rects.addAll(CausalBoardLayout.palette(blockW, Effect.values().length));
                rects.add(CausalBoardLayout.board(blockW, boardH));
                rects.addAll(CausalBoardLayout.inspector(blockW, boardH, SAVE_W, CLEAR_W).all());
                rects.add(CausalBoardLayout.reading(blockW, boardH, 1));
                for (Rect r : rects) {
                    assertTrue(block.contains(r), r.name() + " escapes the block at " + w + "x" + h
                            + ": " + say(r) + " outside " + say(block));
                }
            }
        }
    }

    @Test
    void thePaletteHoldsTheLongestVocabularyWithoutOverlapping() {
        int longest = Math.max(Cause.values().length,
                Math.max(Condition.values().length, Effect.values().length));
        for (int w : WIDTHS) {
            int blockW = CausalBoardLayout.blockWidth(w);
            List<Rect> rects = CausalBoardLayout.palette(blockW, longest);
            assertEquals(longest, rects.size());
            assertDisjoint(rects);
            assertTrue(rects.get(rects.size() - 1).right() <= blockW, "the strip runs off the block at " + w);
        }
    }

    @Test
    void everyPaletteGlyphAnswersToItsOwnCentreAndToNothingInTheGaps() {
        for (int w : WIDTHS) {
            int blockW = CausalBoardLayout.blockWidth(w);
            int count = Cause.values().length;
            List<Rect> rects = CausalBoardLayout.palette(blockW, count);
            for (int i = 0; i < count; i++) {
                Rect r = rects.get(i);
                assertEquals(i, CausalBoardLayout.paletteIndexAt(blockW, count,
                        r.x() + r.w() / 2.0D, r.y() + r.h() / 2.0D), "at " + w);
            }
            assertEquals(-1, CausalBoardLayout.paletteIndexAt(blockW, count, -5.0D, 0.0D));
            assertEquals(-1, CausalBoardLayout.paletteIndexAt(blockW, count,
                    rects.get(0).x() + 1.0D, CausalBoardLayout.PALETTE_Y - 3.0D), "above the strip");
        }
    }

    @Test
    void theBoardKeepsOneScaleOnBothAxes() {
        // A wall of pins built on a wide screen and reopened on a narrow one has to be the same
        // drawing. A hundred units across is drawn the same length as a hundred units down.
        for (int w : WIDTHS) {
            for (int h : HEIGHTS) {
                int blockW = CausalBoardLayout.blockWidth(w);
                int boardH = CausalBoardLayout.boardHeight(h);
                Rect origin = CausalBoardLayout.pinAt(blockW, boardH, 0, 0, "a");
                Rect across = CausalBoardLayout.pinAt(blockW, boardH, 100, 0, "b");
                Rect down = CausalBoardLayout.pinAt(blockW, boardH, 0, 100, "c");
                assertEquals(across.x() - origin.x(), down.y() - origin.y(), 1,
                        "a hundred across is a hundred down at " + w + "x" + h);
            }
        }
    }

    @Test
    void aPinDroppedAtAPointComesBackAtThatPoint() {
        for (int w : WIDTHS) {
            for (int h : HEIGHTS) {
                int blockW = CausalBoardLayout.blockWidth(w);
                int boardH = CausalBoardLayout.boardHeight(h);
                for (int bx : new int[] {0, 40, 160, Weave.BOARD_W}) {
                    for (int by : new int[] {0, 25, 90, Weave.BOARD_H}) {
                        Rect drawn = CausalBoardLayout.pinAt(blockW, boardH, bx, by, "pin");
                        double cx = drawn.x() + CausalBoardLayout.ICON / 2.0D;
                        double cy = drawn.y() + CausalBoardLayout.ICON / 2.0D;
                        assertEquals(bx, CausalBoardLayout.boardXFrom(blockW, boardH, cx), 1,
                                "x round trip at " + w + "x" + h);
                        assertEquals(by, CausalBoardLayout.boardYFrom(blockW, boardH, cy), 1,
                                "y round trip at " + w + "x" + h);
                    }
                }
            }
        }
    }

    @Test
    void everyCornerOfTheBoardIsDrawnInsideTheBoardArea() {
        for (int w : WIDTHS) {
            for (int h : HEIGHTS) {
                int blockW = CausalBoardLayout.blockWidth(w);
                int boardH = CausalBoardLayout.boardHeight(h);
                Rect area = CausalBoardLayout.board(blockW, boardH);
                for (int bx : new int[] {0, Weave.BOARD_W}) {
                    for (int by : new int[] {0, Weave.BOARD_H}) {
                        Rect pin = CausalBoardLayout.pinAt(blockW, boardH, bx, by, "corner");
                        assertTrue(area.contains(pin), "a corner pin escapes the board at " + w + "x" + h
                                + ": " + say(pin) + " outside " + say(area));
                    }
                }
            }
        }
    }

    @Test
    void aPressOnAPinIsToldApartFromAPressOnItsPorts() {
        Rect pin = CausalBoardLayout.pinAt(400, 160, 100, 60, "pin");
        assertTrue(CausalBoardLayout.onPin(pin, pin.x() + 4.0D, pin.y() + 4.0D));
        assertFalse(CausalBoardLayout.onPin(pin, pin.x() - CausalBoardLayout.PIN_GRAB - 2.0D, pin.y() + 4.0D));
        Rect out = CausalBoardLayout.outPort(pin, 0);
        assertTrue(out.x() >= pin.right(), "string leaves from the right");
        Rect in = CausalBoardLayout.inPort(pin);
        assertTrue(in.right() <= pin.x(), "and arrives at the left");
        assertTrue(CausalBoardLayout.onPort(out, out.x() + 1.0D, out.y() + 1.0D));
    }

    /**
     * The bug this pins was invisible to every other test and obvious in one screenshot: a pin's
     * name is drawn to the right of its glyph, the string to the next pin was tied at the glyph, and
     * so a long name was painted straight over its own outgoing run. A chain read as unconnected at
     * exactly the pin that connected it. The knot belongs after the name.
     */
    @Test
    void theStringLeavingAPinClearsThePinOwnName() {
        Rect pin = CausalBoardLayout.pinAt(480, 180, 20, 40, "from");
        for (int measured = 0; measured <= 240; measured += 7) {
            int drawn = CausalBoardLayout.nameWidth(measured);
            int ends = pin.right() + (drawn <= 0 ? 0 : CausalBoardLayout.LABEL_GAP + drawn);
            assertTrue(CausalBoardLayout.outPort(pin, measured).x() >= ends,
                    "a name " + measured + " wide runs to " + ends
                            + " but the string is tied at " + CausalBoardLayout.outPort(pin, measured).x());
        }
        assertEquals(CausalBoardLayout.LABEL_W_MAX, CausalBoardLayout.nameWidth(1000),
                "however long the word, the board only ever gives it so much");
    }

    @Test
    void aPieceOfStringIsCutAtItsMiddleAndNotWhereItLeavesAPin() {
        Rect from = CausalBoardLayout.pinAt(480, 180, 20, 40, "from");
        Rect to = CausalBoardLayout.pinAt(480, 180, 220, 40, "to");
        double[] middle = CausalBoardLayout.wireMiddle(from, 40, to);
        assertTrue(CausalBoardLayout.onWire(from, 40, to, middle[0], middle[1]));
        assertFalse(CausalBoardLayout.onWire(from, 40, to, from.x(), from.y()),
                "a press on a pin is a press on the pin, not on the string leaving it");
        assertTrue(middle[1] > from.y(), "string sags, so two level pins are joined by a curve");
    }

    @Test
    void theInspectorGivesUpItsWordsBeforeItLetsThemOverlap() {
        int narrow = CausalBoardLayout.blockWidth(320);
        CausalBoardLayout.Inspector cramped = CausalBoardLayout.inspector(narrow, 120, 60, 70);
        assertDisjoint(cramped.all());
        assertFalse(CausalBoardLayout.inspectorFitsWords(narrow, 60, 70),
                "and the screen is told to fall back rather than left to guess");
        assertTrue(CausalBoardLayout.inspectorFitsWords(CausalBoardLayout.blockWidth(1280), 60, 70));
    }

    @Test
    void theBoardIsAlwaysWorthDrawingOn() {
        for (int h : HEIGHTS) {
            assertTrue(CausalBoardLayout.boardHeight(h) >= CausalBoardLayout.BOARD_H_MIN, "at height " + h);
        }
    }

    @Test
    void aPinKnowsWhereItIsFromTheWeaveAlone() {
        CausalNode node = CausalNode.of(3, Cause.HURT, 80, 50);
        Rect direct = CausalBoardLayout.pin(500, 150, node);
        Rect byHand = CausalBoardLayout.pinAt(500, 150, 80, 50, direct.name());
        assertEquals(byHand.x(), direct.x());
        assertEquals(byHand.y(), direct.y());
    }
}
