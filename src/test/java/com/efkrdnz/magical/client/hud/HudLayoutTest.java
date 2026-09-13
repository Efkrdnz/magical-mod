package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.hud.HudLayout.Disc;
import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.client.hud.HudLayout.Segment;
import com.efkrdnz.magical.client.hud.HudLayout.Shape;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The HUD laid out on paper, at every anchor and scale, so a collision fails here instead of in
 * the game. The same doctrine as {@code CodexLayoutTest}: a HUD that places its rows by numbers
 * inside draw calls collides with itself unnoticed.
 *
 * <p>The GUI it has to fit is 480x270, which is what vanilla's "auto" scale gives a 1080p window.
 * Vanilla's own furniture is reserved as rectangles: the hotbar band with the health and food rows
 * above it, the boss bar, and the potion icons in the top-right corner.
 */
class HudLayoutTest {

    private static final int W = 480;
    private static final int H = 270;
    private static final float[] SCALES = {0.75F, 1.0F, 1.25F};
    private static final int ALL_SINS = (1 << HudLayout.CROWN_SEATS) - 1;
    private static final int[] SIN_MASKS = {0, ALL_SINS, 0b1010101, 0b0101010, 0b1000001};
    private static final int KEY_TEXT_W = 10;
    /** The widest each core line gets: "999" over "999". */
    private static final int[] CORE_LINE_W = {18, 18};

    private static final Rect HOTBAR_BAND = new Rect("vanilla hotbar band", 149, 220, 182, 50);
    private static final Rect BOSS_BAR = new Rect("vanilla boss bar", 149, 0, 182, 20);
    private static final Rect POTIONS = new Rect("vanilla potion icons", W - 130, 0, 130, 52);
    private static final Rect SCREEN = new Rect("screen", 0, 0, W, H);

    private static void assertDisjoint(List<Shape> shapes, String where) {
        for (int i = 0; i < shapes.size(); i++) {
            for (int j = i + 1; j < shapes.size(); j++) {
                Shape a = shapes.get(i);
                Shape b = shapes.get(j);
                assertFalse(a.overlaps(b), where + ": " + a.name() + " overlaps " + b.name()
                        + ": " + describe(a) + " vs " + describe(b));
            }
        }
    }

    private static String describe(Shape s) {
        Rect r = s.bounds();
        return "[" + r.x() + "," + r.y() + " .. " + r.right() + "," + r.bottom() + "]";
    }

    private static String where(HudAnchor anchor, float scale) {
        return anchor + " @" + scale;
    }

    @Test
    void theTotemNeverOverlapsItselfAtAnyAnchorScaleOrState() {
        for (HudAnchor anchor : HudAnchor.values()) {
            for (float scale : SCALES) {
                HudLayout layout = HudLayout.of(W, H, anchor, scale);
                for (int mask : SIN_MASKS) {
                    for (int extras = 0; extras < 4; extras++) {
                        for (int lines = 0; lines <= HudLayout.GAUGE_LINES_MAX; lines += HudLayout.GAUGE_LINES_MAX) {
                            String state = where(anchor, scale) + " sins=" + Integer.toBinaryString(mask) + " extras=" + extras + " lines=" + lines;
                            List<Shape> shapes = layout.totemShapes((extras & 1) != 0, (extras & 2) != 0, mask, lines, true, KEY_TEXT_W);
                            assertDisjoint(shapes, state);
                            Disc sigil = layout.sigil();
                            for (Shape shape : shapes) {
                                if (shape == shapes.get(0)) {
                                    continue;
                                }
                                assertFalse(shape.overlaps(sigil), state + ": " + shape.name() + " sits on the sigil: " + describe(shape));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void everythingFitsA480By270GuiAndStaysOffVanillasFurniture() {
        for (HudAnchor anchor : HudAnchor.values()) {
            for (float scale : SCALES) {
                HudLayout layout = HudLayout.of(W, H, anchor, scale);
                for (Shape shape : layout.totemShapes(true, true, ALL_SINS, HudLayout.GAUGE_LINES_MAX, true, KEY_TEXT_W)) {
                    Rect bounds = shape.bounds();
                    assertTrue(SCREEN.contains(bounds), where(anchor, scale) + ": " + shape.name() + " leaves the screen: " + describe(shape));
                    // The top-right corner pays a fifty-pixel reserve for the potion icons; above
                    // the design scale that plus the fan runs out of height on a 270-tall GUI.
                    if (!(anchor == HudAnchor.TOP_RIGHT && scale > 1.0F)) {
                        assertFalse(shape.overlaps(HOTBAR_BAND), where(anchor, scale) + ": " + shape.name() + " sits on the hotbar band: " + describe(shape));
                    }
                    // Above the design scale the top card grazes the boss bar's corner; the player
                    // asked for a bigger HUD and the bar is rare, so the reserve holds to 1.0 only.
                    if (!anchor.bottom() && scale <= 1.0F) {
                        assertFalse(shape.overlaps(BOSS_BAR), where(anchor, scale) + ": " + shape.name() + " sits on the boss bar: " + describe(shape));
                    }
                    if (anchor == HudAnchor.TOP_RIGHT) {
                        assertFalse(shape.overlaps(POTIONS), where(anchor, scale) + ": " + shape.name() + " sits on the potion icons: " + describe(shape));
                    }
                }
            }
        }
    }

    @Test
    void theCoreHoldsItsNumerals() {
        for (float scale : new float[] {1.0F, 1.25F}) {
            HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, scale);
            Disc core = layout.core();
            for (int n = 2; n <= HudLayout.CORE_LINES_MAX; n++) {
                for (int i = 0; i < n; i++) {
                    Rect line = layout.coreLine(i, n, CORE_LINE_W[i]);
                    // The glyphs use eight of the nine rows.
                    Rect glyphs = new Rect(line.name(), line.x(), line.y(), line.w(), HudLayout.TEXT_H - 1);
                    assertTrue(core.contains(glyphs), "at " + scale + " with " + n + " lines, " + line.name() + " leaves the core: " + describe(glyphs));
                }
            }
        }
    }

    @Test
    void theCardsNeverTouchTheSigilOrEachOther() {
        for (int cards = 3; cards <= 5; cards++) {
            float fan = HudLayout.fanRadius(cards);
            double halfStep = Math.toRadians(HudLayout.fanStepDegrees(cards) / 2.0F);
            float neighbourGap = (float) (2.0 * fan * Math.sin(halfStep)) - 2.0F * HudLayout.CARD_R;
            assertTrue(neighbourGap >= HudLayout.CARD_GAP, cards + " cards: neighbours on the arc are only " + neighbourGap + " apart");
            assertTrue(fan - HudLayout.CARD_R - HudLayout.SIGIL_R >= HudLayout.SPOKE_MIN, cards + " cards: the spoke is too short to read");
        }
        assertEquals(68.0F, HudLayout.fanRadius(4));
        assertEquals(81.0F, HudLayout.fanRadius(5));
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        Disc sigil = layout.sigil();
        for (int k = 0; k < layout.cardCount(); k++) {
            assertFalse(layout.card(k).overlaps(sigil), "card " + k + " touches the sigil");
            assertTrue(layout.card(k).contains(layout.cardText(k)), "card " + k + " cannot hold its seconds");
        }
    }

    @Test
    void theCrownSeatsAreStableWhateverIsEnabled() {
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        for (int i = 0; i < HudLayout.CROWN_SEATS; i++) {
            Disc seat = layout.crownSeat(i);
            // The seat is a pure function of its index; no subset of enabled sins moves it.
            assertEquals(seat, layout.crownSeat(i));
            if (i > 0) {
                Disc previous = layout.crownSeat(i - 1);
                float dx = seat.cx() - previous.cx();
                float dy = seat.cy() - previous.cy();
                assertTrue(Math.sqrt(dx * dx + dy * dy) >= 2.0F * HudLayout.SATELLITE_R, "seats " + (i - 1) + " and " + i + " are closer than a satellite");
            }
            assertFalse(seat.overlaps(layout.sigil()), "crown seat " + i + " sits on the sigil");
            for (int k = 0; k < layout.cardCount(); k++) {
                Segment spoke = layout.spoke(k);
                assertTrue(spoke.distance(seat.cx(), seat.cy()) >= seat.r() + 0.5F,
                        "spoke " + k + " runs through crown seat " + i);
            }
        }
    }

    @Test
    void theKeyTagsAlwaysPointAwayFromTheSigilAndTheReadoutsSitBeyondThem() {
        for (HudAnchor anchor : HudAnchor.values()) {
            HudLayout layout = HudLayout.of(W, H, anchor, 1.0F);
            for (int k = 0; k < layout.cardCount(); k++) {
                Disc card = layout.card(k);
                Rect tag = layout.keyTag(k, KEY_TEXT_W);
                float away = Math.signum(tag.centreX() - card.cx());
                assertEquals(anchor.right() ? -1.0F : 1.0F, away, anchor + ": tag " + k + " points at the sigil");
                assertTrue(Math.signum(card.cx() - layout.cx()) == away, anchor + ": card " + k + " is on the wrong side");
            }
            assertTrue(layout.gaugeLinesMax() >= HudLayout.GAUGE_LINES_MAX, anchor + ": only " + layout.gaugeLinesMax() + " readouts fit at the design scale");
            for (int i = 0; i < HudLayout.GAUGE_LINES_MAX; i++) {
                Rect line = layout.gaugeLine(i);
                float reach = anchor.right() ? layout.cx() - line.right() : line.x() - layout.cx();
                assertTrue(reach >= layout.fanEdge(), anchor + ": readout " + i + " starts inside the fan");
            }
        }
    }

    @Test
    void theCentreGroupIsDisjointAndInsideTheScreen() {
        HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F);
        List<Shape> shapes = layout.centreShapes(HudLayout.STATUS_CHIPS_MAX);
        assertDisjoint(shapes, "centre group");
        for (Shape shape : shapes) {
            assertTrue(SCREEN.contains(shape.bounds()), shape.name() + " leaves the screen");
            assertFalse(shape.overlaps(HOTBAR_BAND), shape.name() + " sits on the hotbar band");
        }
    }

    /** The rule flash sits over the crosshair like a totem pop: fixed size, every anchor, never on the crosshair itself. */
    @Test
    void theRuleFlashSitsAboveTheCrosshairAtEveryScaleAndAnchor() {
        for (HudAnchor anchor : HudAnchor.values()) {
            for (float scale : SCALES) {
                HudLayout layout = HudLayout.of(W, H, anchor, scale);
                Rect plate = layout.ruleFlashPlate(HudLayout.RULE_FLASH_MAX_W);
                Rect caption = layout.ruleFlashCaption();
                String state = where(anchor, scale);
                assertTrue(SCREEN.contains(plate), state + ": the flash plate leaves the screen: " + describe(plate));
                assertTrue(SCREEN.contains(caption), state + ": the flash caption leaves the screen: " + describe(caption));
                assertTrue(caption.bottom() <= layout.centreY() - 8, state + ": the caption crowds the crosshair: " + describe(caption));
                assertTrue(plate.bottom() <= caption.y(), state + ": the caption sits inside the plate");
                assertFalse(plate.overlaps(BOSS_BAR), state + ": the flash sits on the boss bar: " + describe(plate));
                assertEquals(2 * HudLayout.TEXT_H + 2 * HudLayout.RULE_FLASH_PAD, plate.h(), state + ": the plate scales with the HUD");
                assertEquals(HudLayout.of(W, H, HudAnchor.TOP_LEFT, 1.0F).ruleFlashPlate(HudLayout.RULE_FLASH_MAX_W), plate,
                        state + ": the flash moved with the anchor or the scale");
                Rect narrow = layout.ruleFlashPlate(60);
                assertEquals(plate.centreX(), narrow.centreX(), state + ": a narrow formula is off centre");
                assertTrue(narrow.w() < plate.w(), state + ": a narrow formula gets the widest plate");
                assertTrue(plate.w() <= HudLayout.RULE_FLASH_MAX_W + 2 * HudLayout.RULE_FLASH_PAD, state + ": the plate ignores its cap");
            }
        }
    }

    @Test
    void textBoxesNeverScale() {
        for (float scale : SCALES) {
            HudLayout layout = HudLayout.of(W, H, HudAnchor.TOP_LEFT, scale);
            assertEquals(HudLayout.TEXT_H, layout.ruleFlashCaption().h());
            assertEquals(HudLayout.TEXT_H, layout.caption().h());
            assertEquals(HudLayout.TEXT_H, layout.captionLine(HudLayout.CAPTIONS_MAX - 1).h());
            assertEquals(HudLayout.TEXT_H, layout.keyTag(0, KEY_TEXT_W).h());
            assertEquals(HudLayout.TEXT_H, layout.announceText().h());
            assertEquals(HudLayout.TEXT_H, layout.gaugeLine(0).h());
            assertEquals(HudLayout.TEXT_H, layout.coreLine(0, 2, 18).h());
            assertEquals(HudLayout.TEXT_H, layout.levelTag(20).h());
            assertTrue(layout.gaugeLinesMax() >= 6, "at " + scale + " only " + layout.gaugeLinesMax() + " readouts fit");
        }
    }

    @Test
    void theBottomAnchorsNeverEnterTheHotbarBand() {
        for (HudAnchor anchor : new HudAnchor[] {HudAnchor.BOTTOM_LEFT, HudAnchor.BOTTOM_RIGHT}) {
            HudLayout layout = HudLayout.of(W, H, anchor, 1.0F);
            for (Shape shape : layout.totemShapes(true, true, ALL_SINS, HudLayout.GAUGE_LINES_MAX, true, KEY_TEXT_W)) {
                Rect b = shape.bounds();
                boolean beside = b.right() <= HOTBAR_BAND.x() || b.x() >= HOTBAR_BAND.right();
                boolean above = b.bottom() <= HOTBAR_BAND.y();
                assertTrue(beside || above, anchor + ": " + shape.name() + " is in the hotbar band: " + describe(shape));
            }
        }
    }
}
