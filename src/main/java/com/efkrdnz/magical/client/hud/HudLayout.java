package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.List;

/**
 * Every position the magic HUD draws at, computed from the GUI size, the anchor corner and the
 * scale option - and nothing else.
 *
 * <p>It lives outside the renderer for the reason {@code CodexLayout} does: a HUD that places its
 * rows by bare numbers inside draw calls collides with itself unnoticed. {@code HudLayoutTest}
 * sweeps every shape here for overlap, at every anchor and scale, and checks it all fits a 480x270
 * GUI - the size vanilla's "auto" scale gives a 1080p window.
 *
 * <p>The totem: a sigil of concentric rings round a core that carries the numerals, a crown of
 * sin satellites over it, a fan of cast cards hung off spokes to one side with their readouts
 * beyond the key tags, captions under the fan, and for a blood or dark mage a reflection of the
 * rings under a waterline. All values are GUI units. Radii and offsets scale; text boxes are nine
 * tall and never scale. On right-hand anchors the fan, the tags and the captions mirror across the
 * sigil so the tabs still point away from it.
 */
public final class HudLayout {

    /** A rectangle with a name, so a failed overlap assertion says which two things collided. */
    public record Rect(String name, int x, int y, int w, int h) implements Shape {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public int centreX() {
            return x + w / 2;
        }

        public int centreY() {
            return y + h / 2;
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        public boolean contains(Rect inner) {
            return inner.x >= x && inner.y >= y && inner.right() <= right() && inner.bottom() <= bottom();
        }

        @Override
        public boolean overlaps(Shape other) {
            return other instanceof Rect rect ? overlaps(rect) : ((Disc) other).overlaps(this);
        }

        @Override
        public Rect bounds() {
            return this;
        }
    }

    /** A circle with a name: cards and satellites sit on arcs and touch as discs, not as boxes. */
    public record Disc(String name, float cx, float cy, float r) implements Shape {
        public boolean overlaps(Disc other) {
            float dx = cx - other.cx;
            float dy = cy - other.cy;
            float reach = r + other.r;
            return dx * dx + dy * dy < reach * reach;
        }

        public boolean overlaps(Rect rect) {
            float nx = Math.max(rect.x, Math.min(cx, rect.right()));
            float ny = Math.max(rect.y, Math.min(cy, rect.bottom()));
            float dx = cx - nx;
            float dy = cy - ny;
            return dx * dx + dy * dy < r * r;
        }

        /** Whether every corner of the rectangle is inside the circle. */
        public boolean contains(Rect rect) {
            return inside(rect.x, rect.y) && inside(rect.right(), rect.y) && inside(rect.x, rect.bottom()) && inside(rect.right(), rect.bottom());
        }

        private boolean inside(float px, float py) {
            float dx = px - cx;
            float dy = py - cy;
            return dx * dx + dy * dy <= r * r;
        }

        @Override
        public boolean overlaps(Shape other) {
            return other instanceof Disc disc ? overlaps(disc) : overlaps((Rect) other);
        }

        @Override
        public Rect bounds() {
            int x0 = (int) Math.floor(cx - r);
            int y0 = (int) Math.floor(cy - r);
            return new Rect(name, x0, y0, (int) Math.ceil(cx + r) - x0, (int) Math.ceil(cy + r) - y0);
        }
    }

    /** A line between two points, for the spokes and the waterline. */
    public record Segment(String name, float x0, float y0, float x1, float y1) {
        /** Distance from a point to this segment. */
        public float distance(float px, float py) {
            float dx = x1 - x0;
            float dy = y1 - y0;
            float len2 = dx * dx + dy * dy;
            float t = len2 <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, ((px - x0) * dx + (py - y0) * dy) / len2));
            float qx = x0 + dx * t - px;
            float qy = y0 + dy * t - py;
            return (float) Math.sqrt(qx * qx + qy * qy);
        }
    }

    public sealed interface Shape permits Rect, Disc {
        String name();

        boolean overlaps(Shape other);

        Rect bounds();
    }

    // ---- the totem: sigil, crown, fan, reflection ---------------------------------------------

    public static final int MARGIN = 6;
    /** The sigil's collision radius: the XP hairline plus its glow. */
    public static final float SIGIL_R = 37.0F;
    /** The core holds the two pool numerals - the current mana over the current barrier; the rings say the rest. */
    public static final float CORE_R = 15.0F;
    public static final int CORE_LINES_MAX = 2;
    public static final float HALO_R_IN = 15.75F;
    public static final float HALO_R_OUT = 16.75F;
    public static final float BARRIER_R_IN = 17.5F;
    public static final float BARRIER_R_OUT = 21.5F;
    public static final int BARRIER_NOTCHES = 8;
    /**
     * The Array's draw arc, in the hairline gap the barrier and mana rings leave between them.
     *
     * <p>Outside the barrier because it is not a pool of the wielder's: it is the ledger the
     * Array keeps of how much of its conserved Edge the shape standing in the world is spending,
     * and it belongs beside the pools rather than among them. It is also the only ring on the
     * sigil that is absent most of the time - a wielder with no Array draws nothing here at all.
     */
    public static final float DRAW_R_IN = 21.9F;
    public static final float DRAW_R_OUT = 23.1F;
    public static final float MANA_R_IN = 23.5F;
    public static final float MANA_R_OUT = 32.0F;
    public static final float XP_R_IN = 34.5F;
    public static final float XP_R_OUT = 35.5F;
    /** The level, in a tag hung under the XP ring at six o'clock. */
    public static final float LEVEL_TAG_GAP = 1.0F;
    public static final int LEVEL_TAG_H = 9;
    public static final int LEVEL_TAG_MAX_W = 26;
    public static final int LEVEL_TAG_PAD = 4;

    /** The hairline the reflection hangs under, and where it hangs. */
    public static final float WATERLINE_DY = 49.0F;
    public static final float WATERLINE_HALF = 34.0F;
    public static final float REFLECTION_DY = 86.0F;
    public static final float VESSEL_R_IN = 24.0F;
    public static final float VESSEL_R_OUT = 31.0F;
    public static final float CORRUPTION_R_IN = 13.0F;
    public static final float CORRUPTION_R_OUT = 19.0F;
    public static final int CORRUPTION_RUNGS = 4;

    /** Seven fixed seats on an arc above the sigil, one per sin in registration order. */
    public static final float CROWN_R = 46.0F;
    public static final int CROWN_SEATS = 7;
    public static final float CROWN_START_DEG = -150.0F;
    public static final float CROWN_SPAN_DEG = 100.0F;
    public static final float SATELLITE_R = 6.5F;

    /** The cast cards, on an arc on the interior side, hung off spokes. */
    public static final float CARD_R = 13.0F;
    /** The least room between neighbouring cards on the arc. */
    public static final float CARD_GAP = 2.0F;
    /** The least spoke length: the sigil's rim to the card's plate. */
    public static final float SPOKE_MIN = 8.0F;
    /** The gap between a card's rim and its key tag. */
    public static final float KEY_TAG_GAP = 1.0F;
    public static final int KEY_TAG_H = 9;
    public static final int KEY_TAG_MAX_W = 14;
    public static final int KEY_TAG_PAD = 4;
    /** The cooldown numeral's box, centred on the card. */
    public static final int CARD_TEXT_W = 20;

    /** The readouts, beyond the key tags: the vault, one line per lit satellite in crown order, the mana charge. */
    public static final int GAUGE_GAP = 4;
    public static final int GAUGE_W = 100;
    public static final int GAUGE_STRIDE = 10;
    /** The vault, six sins with a gauge (Lust has none) and the mana charge. */
    public static final int GAUGE_LINES_MAX = 8;
    /** The column starts level with the crown's shoulder and ends before the caption row: what fits is {@link #gaugeLinesMax}. */
    public static final float GAUGE_TOP_DY = -38.0F;
    public static final int GAUGE_CAPTION_GAP = 4;

    /** The captions under the fan: the loadout's name, then the vessel, the corruption and the arcane preset as they apply. */
    public static final float CAPTION_DX = 44.0F;
    public static final float CAPTION_DY = 56.0F;
    public static final int CAPTION_W = 108;
    public static final int CAPTIONS_MAX = 3;
    /** Each caption hangs this far under the one above; text boxes never scale, so the gap is what scales. */
    public static final float CAPTION_GAP = 2.0F;
    public static final int TEXT_H = 9;

    /** What the player just gained: an emblem inking on beyond the caption, on its row, with a line beside it. */
    public static final int ANNOUNCE_GAP = 4;
    public static final int ANNOUNCE_EMBLEM = 24;
    public static final int ANNOUNCE_TEXT_W = 160;

    /** Potion effect icons live in the top-right corner; that anchor gives them the room, always. */
    public static final int POTION_RESERVE = 50;
    /** The boss bar hangs across the top; the readouts start under it at any scale. */
    public static final int BOSS_BAR_RESERVE = 20;
    /** The hotbar with the health and food rows above it; bottom anchors sit above it. */
    public static final int HOTBAR_RESERVE = 50;
    /** How far the totem reaches below the sigil centre at a bottom anchor: the reflection's bottom. */
    public static final float TOTEM_BELOW = REFLECTION_DY + VESSEL_R_OUT;
    /** How far the crown reaches above the sigil centre. */
    public static final float TOTEM_ABOVE = CROWN_R + SATELLITE_R + 0.5F;
    /** How far the crown's outermost seat reaches beside the sigil centre. */
    public static final float TOTEM_BESIDE = 47.0F;

    // ---- the centre group -----------------------------------------------------------------------

    public static final int STATUS_CHIP = 14;
    public static final int STATUS_CHIP_DY = 26;
    public static final int STATUS_CHIP_STRIDE = 18;
    public static final int STATUS_CHIPS_MAX = 6;
    public static final int UNWAKING_CUE_DY = 46;
    public static final int UNWAKING_CUE_W = 240;

    /**
     * The rule flash: a formula plate this far above the crosshair, two text rows tall for the
     * formula at twice the text size, with a caption row under it. Like a totem pop it is the same
     * size at every HUD scale and ignores the anchor.
     */
    public static final int RULE_FLASH_DY = 52;
    public static final int RULE_FLASH_PAD = 5;
    public static final int RULE_FLASH_MAX_W = 220;
    public static final int RULE_FLASH_CAPTION_GAP = 3;

    private final int guiWidth;
    private final int guiHeight;
    private final HudAnchor anchor;
    private final float scale;
    private final int sign;
    private final float cx;
    private final float cy;

    private HudLayout(int guiWidth, int guiHeight, HudAnchor anchor, float scale) {
        this.guiWidth = guiWidth;
        this.guiHeight = guiHeight;
        this.anchor = anchor;
        this.scale = scale;
        this.sign = anchor.right() ? -1 : 1;
        float beside = MARGIN + s(TOTEM_BESIDE);
        this.cx = anchor.right() ? guiWidth - beside : beside;
        float y;
        if (anchor.bottom()) {
            // Above the hotbar rows; on a short GUI at a large scale the crown wins and the
            // reflection, which sits beside the hotbar and not over it, dips toward the rows.
            y = Math.max(MARGIN + (float) Math.ceil(s(TOTEM_ABOVE)), guiHeight - HOTBAR_RESERVE - (float) Math.ceil(s(TOTEM_BELOW)));
        } else {
            y = MARGIN + (float) Math.ceil(s(TOTEM_ABOVE));
            if (anchor == HudAnchor.TOP_RIGHT) {
                y += POTION_RESERVE;
            }
        }
        this.cy = y;
    }

    public static HudLayout of(int guiWidth, int guiHeight, HudAnchor anchor, float scale) {
        return new HudLayout(guiWidth, guiHeight, anchor, Math.max(0.5F, Math.min(1.5F, scale)));
    }

    public int guiWidth() {
        return guiWidth;
    }

    public int guiHeight() {
        return guiHeight;
    }

    public HudAnchor anchor() {
        return anchor;
    }

    public float scale() {
        return scale;
    }

    /** +1 when the fan opens to the right of the sigil, -1 when it mirrors. */
    public int sign() {
        return sign;
    }

    public float cx() {
        return cx;
    }

    public float cy() {
        return cy;
    }

    public float s(float v) {
        return v * scale;
    }

    private int round(float v) {
        return Math.round(v);
    }

    // ---- sigil ----------------------------------------------------------------------------------

    public Disc sigil() {
        return new Disc("sigil", cx, cy, s(SIGIL_R));
    }

    public Disc core() {
        return new Disc("core", cx, cy, s(CORE_R));
    }

    public float haloOuter() {
        return s(HALO_R_OUT);
    }

    public float barrierOuter() {
        return s(BARRIER_R_OUT);
    }

    public float barrierWidth() {
        return s(BARRIER_R_OUT - BARRIER_R_IN);
    }

    public float drawOuter() {
        return s(DRAW_R_OUT);
    }

    public float drawWidth() {
        return s(DRAW_R_OUT - DRAW_R_IN);
    }

    public float manaOuter() {
        return s(MANA_R_OUT);
    }

    public float manaWidth() {
        return s(MANA_R_OUT - MANA_R_IN);
    }

    public float xpOuter() {
        return s(XP_R_OUT);
    }

    public float xpWidth() {
        return s(XP_R_OUT - XP_R_IN);
    }

    /** Line {@code i} of {@code n} in the core, stacked round its centre: the mana over the barrier. */
    public Rect coreLine(int i, int n, int width) {
        int top = (int) Math.floor(cy - n * TEXT_H / 2.0F);
        return new Rect("core line " + i, round(cx) - width / 2, top + i * TEXT_H, width, TEXT_H);
    }

    /** The level's tag, hung under the sigil at six o'clock. */
    public Rect levelTag(int textWidth) {
        int w = Math.min(LEVEL_TAG_MAX_W, textWidth + LEVEL_TAG_PAD);
        return new Rect("level tag", round(cx) - w / 2, round(cy + s(SIGIL_R + LEVEL_TAG_GAP)), w, LEVEL_TAG_H);
    }

    // ---- the reflection below the waterline ----------------------------------------------------

    public Segment waterline() {
        float y = cy + s(WATERLINE_DY);
        return new Segment("waterline", cx - s(WATERLINE_HALF), y, cx + s(WATERLINE_HALF), y);
    }

    public float reflectionCy() {
        return cy + s(REFLECTION_DY);
    }

    public float vesselOuter() {
        return s(VESSEL_R_OUT);
    }

    public float vesselWidth() {
        return s(VESSEL_R_OUT - VESSEL_R_IN);
    }

    public float corruptionOuter() {
        return s(CORRUPTION_R_OUT);
    }

    public float corruptionWidth() {
        return s(CORRUPTION_R_OUT - CORRUPTION_R_IN);
    }

    /** The upper half of the mirrored rings, from the waterline down to their centre line. */
    public Rect reflection() {
        int x0 = round(cx - s(VESSEL_R_OUT));
        int y0 = round(cy + s(REFLECTION_DY - VESSEL_R_OUT));
        return new Rect("reflection", x0, y0, round(2.0F * s(VESSEL_R_OUT)), round(s(VESSEL_R_OUT)));
    }

    // ---- crown ----------------------------------------------------------------------------------

    /** Seat {@code i} (registration order: Pride, Greed, Lust, Envy, Gluttony, Wrath, Sloth). Stable whatever is enabled. */
    public Disc crownSeat(int i) {
        float deg = CROWN_START_DEG + i * (CROWN_SPAN_DEG / (CROWN_SEATS - 1));
        if (sign < 0) {
            deg = 180.0F - deg;
        }
        double rad = Math.toRadians(deg);
        float r = s(CROWN_R);
        return new Disc("crown seat " + i, cx + (float) (r * Math.cos(rad)), cy + (float) (r * Math.sin(rad)), s(SATELLITE_R));
    }

    // ---- fan ------------------------------------------------------------------------------------

    public int cardCount() {
        return MagicContent.LOADOUT_SIZE;
    }

    public static float fanStepDegrees(int cards) {
        return cards <= 4 ? 24.0F : 20.0F;
    }

    /** The orbit the cards sit on: far enough out that neighbours on the arc never touch and the spokes read. */
    public static float fanRadius(int cards) {
        double halfStep = Math.toRadians(fanStepDegrees(cards) / 2.0F);
        float apart = (float) Math.ceil((2.0F * CARD_R + CARD_GAP) / (2.0 * Math.sin(halfStep)));
        return Math.max(SIGIL_R + CARD_R + SPOKE_MIN, apart);
    }

    public float fanRadius() {
        return s(fanRadius(cardCount()));
    }

    /** The angle of card {@code k}, in degrees from the fan's axis; Z at the top, V at the bottom. */
    public float cardDegrees(int k) {
        int n = cardCount();
        float deg = (k - (n - 1) / 2.0F) * fanStepDegrees(n);
        return sign < 0 ? 180.0F - deg : deg;
    }

    public Disc card(int k) {
        double rad = Math.toRadians(cardDegrees(k));
        float r = fanRadius();
        return new Disc("card " + k, cx + (float) (r * Math.cos(rad)), cy + (float) (r * Math.sin(rad)), s(CARD_R));
    }

    /** The one-pixel line from the sigil's edge to the card's plate. */
    public Segment spoke(int k) {
        double rad = Math.toRadians(cardDegrees(k));
        float from = s(SIGIL_R);
        float to = fanRadius() - s(CARD_R);
        return new Segment("spoke " + k,
                cx + (float) (from * Math.cos(rad)), cy + (float) (from * Math.sin(rad)),
                cx + (float) (to * Math.cos(rad)), cy + (float) (to * Math.sin(rad)));
    }

    /** The key letter's tab, hanging off the far side of the card so it points away from the sigil. */
    public Rect keyTag(int k, int textWidth) {
        Disc card = card(k);
        int w = Math.min(KEY_TAG_MAX_W, textWidth + KEY_TAG_PAD);
        int x = round(card.cx() + sign * (card.r() + s(KEY_TAG_GAP) + w / 2.0F)) - w / 2;
        return new Rect("key tag " + k, x, round(card.cy()) - KEY_TAG_H / 2, w, KEY_TAG_H);
    }

    /** The seconds left, over a cooling card's plate. */
    public Rect cardText(int k) {
        Disc card = card(k);
        return new Rect("card text " + k, round(card.cx()) - CARD_TEXT_W / 2, round(card.cy()) - TEXT_H / 2, CARD_TEXT_W, TEXT_H);
    }

    /** The tags' far edge: where the readouts begin. */
    public float fanEdge() {
        float far = 0.0F;
        for (int k = 0; k < cardCount(); k++) {
            Rect tag = keyTag(k, KEY_TAG_MAX_W);
            far = Math.max(far, sign > 0 ? tag.right() - cx : cx - tag.x());
        }
        return far;
    }

    // ---- readouts and captions ------------------------------------------------------------------

    private int lineX(float dx, int width) {
        return sign > 0 ? round(cx + s(dx)) : round(cx - s(dx)) - width;
    }

    /** Readout line {@code i}, beside the fan: the vault, a sin's name and value, or the mana charge. */
    public Rect gaugeLine(int i) {
        float reach = fanEdge() + GAUGE_GAP;
        int x = sign > 0 ? round(cx + reach) : round(cx - reach) - GAUGE_W;
        int top = round(cy + s(GAUGE_TOP_DY));
        if (!anchor.bottom()) {
            top = Math.max(top, BOSS_BAR_RESERVE);
        }
        return new Rect("gauge line " + i, x, top + i * GAUGE_STRIDE, GAUGE_W, TEXT_H);
    }

    /** How many readout lines fit above the caption row at this scale; text does not shrink with the sigil. */
    public int gaugeLinesMax() {
        int room = caption().y() - GAUGE_CAPTION_GAP - gaugeLine(0).y();
        return Math.max(0, Math.min(GAUGE_LINES_MAX, room / GAUGE_STRIDE));
    }

    /** Caption {@code i} under the fan; line 0 is the active loadout's name. */
    public Rect captionLine(int i) {
        return new Rect("caption " + i, lineX(CAPTION_DX, CAPTION_W), round(cy + s(CAPTION_DY)) + i * (TEXT_H + round(s(CAPTION_GAP))), CAPTION_W, TEXT_H);
    }

    public Rect caption() {
        return captionLine(0);
    }

    // ---- announcement ---------------------------------------------------------------------------

    public Rect announceEmblem() {
        Rect caption = caption();
        int x = sign > 0 ? caption.right() + ANNOUNCE_GAP : caption.x() - ANNOUNCE_GAP - ANNOUNCE_EMBLEM;
        return new Rect("announce emblem", x, caption.y(), ANNOUNCE_EMBLEM, ANNOUNCE_EMBLEM);
    }

    public Rect announceText() {
        Rect emblem = announceEmblem();
        int x = sign > 0 ? emblem.right() + ANNOUNCE_GAP : emblem.x() - ANNOUNCE_GAP - ANNOUNCE_TEXT_W;
        return new Rect("announce text", x, emblem.y() + (ANNOUNCE_EMBLEM - TEXT_H) / 2, ANNOUNCE_TEXT_W, TEXT_H);
    }

    // ---- centre group ---------------------------------------------------------------------------

    public int centreX() {
        return guiWidth / 2;
    }

    public int centreY() {
        return guiHeight / 2;
    }

    public Rect statusChip(int i, int n) {
        int shown = Math.min(n, STATUS_CHIPS_MAX);
        int x = Math.round(centreX() + (i - (shown - 1) / 2.0F) * STATUS_CHIP_STRIDE) - STATUS_CHIP / 2;
        return new Rect("status chip " + i, x, centreY() + STATUS_CHIP_DY, STATUS_CHIP, STATUS_CHIP);
    }

    public Rect unwakingCue() {
        return new Rect("unwaking cue", centreX() - UNWAKING_CUE_W / 2, centreY() + UNWAKING_CUE_DY, UNWAKING_CUE_W, TEXT_H);
    }

    /** The rule flash's plate, hugging a formula {@code contentWidth} wide (already at its drawn size). */
    public Rect ruleFlashPlate(int contentWidth) {
        int w = Math.min(RULE_FLASH_MAX_W, Math.max(0, contentWidth)) + 2 * RULE_FLASH_PAD;
        int h = 2 * TEXT_H + 2 * RULE_FLASH_PAD;
        return new Rect("rule flash plate", centreX() - w / 2, centreY() - RULE_FLASH_DY - h / 2, w, h);
    }

    /** The caption under the flash's plate: category, operation and target in one muted row. */
    public Rect ruleFlashCaption() {
        Rect plate = ruleFlashPlate(RULE_FLASH_MAX_W);
        return new Rect("rule flash caption", centreX() - RULE_FLASH_MAX_W / 2, plate.bottom() + RULE_FLASH_CAPTION_GAP, RULE_FLASH_MAX_W, TEXT_H);
    }

    // ---- for the overlap sweep ------------------------------------------------------------------

    /** Every shape the totem draws in the given state, for {@code HudLayoutTest}. */
    public List<Shape> totemShapes(boolean vessel, boolean corruption, int sinMask, int gaugeLines, boolean announcement, int keyTextWidth) {
        List<Shape> shapes = new ArrayList<>();
        shapes.add(sigil());
        shapes.add(levelTag(LEVEL_TAG_MAX_W - LEVEL_TAG_PAD));
        if (vessel || corruption) {
            shapes.add(reflection());
        }
        for (int i = 0; i < CROWN_SEATS; i++) {
            if ((sinMask & (1 << i)) != 0) {
                shapes.add(crownSeat(i));
            }
        }
        for (int k = 0; k < cardCount(); k++) {
            shapes.add(card(k));
            shapes.add(keyTag(k, keyTextWidth));
        }
        for (int i = 0; i < Math.min(gaugeLines, gaugeLinesMax()); i++) {
            shapes.add(gaugeLine(i));
        }
        for (int i = 0; i < CAPTIONS_MAX; i++) {
            shapes.add(captionLine(i));
        }
        if (announcement) {
            shapes.add(announceEmblem());
            shapes.add(announceText());
        }
        return shapes;
    }

    /** Everything drawn around the crosshair while nothing modal is open. */
    public List<Shape> centreShapes(int statusChips) {
        List<Shape> shapes = new ArrayList<>();
        for (int i = 0; i < Math.min(statusChips, STATUS_CHIPS_MAX); i++) {
            shapes.add(statusChip(i, statusChips));
        }
        shapes.add(unwakingCue());
        shapes.add(ruleFlashPlate(RULE_FLASH_MAX_W));
        shapes.add(ruleFlashCaption());
        return shapes;
    }
}
