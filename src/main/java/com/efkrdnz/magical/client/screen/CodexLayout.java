package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.magic.MagicContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

/**
 * Geometry for the Magic Codex: every rectangle the screen draws or hit-tests, on every tab.
 *
 * <p>It lives outside the screen so it can be checked without a render context. The screen both
 * draws and hit-tests from these numbers, and {@code CodexLayoutTest} asserts per tab that nothing
 * drawn lands on anything else and that everything sits inside the body the shared chrome gives it.
 *
 * <p>That test exists because the check was missing: when the cast row grew from three keys to
 * four, the fourth card was placed straight through the panel that sat beside it, and nothing
 * failed - the collision was only visible by launching the game. Anything positioned by arithmetic
 * over {@code LOADOUT_SIZE} or {@code MAX_LOADOUTS} belongs here rather than inline in a draw call,
 * so the next change to either constant breaks a test instead of the screen.
 *
 * <p>The frame - panel, top strip, tabs, body - is {@link ScreenChrome}, shared with the Spell
 * Creator. All coordinates are screen-local: add {@code leftPos} / {@code topPos} to place them.
 */
public final class CodexLayout {

    /** A rectangle with a name, so a failed overlap assertion says which two things collided. */
    public record Rect(String name, int x, int y, int w, int h) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        public boolean contains(Rect inner) {
            return inner.x >= x && inner.y >= y && inner.right() <= right() && inner.bottom() <= bottom();
        }
    }

    // ---- the tabs -------------------------------------------------------------------------------

    public static final int TAB_SKILLS = 0;
    public static final int TAB_LOADOUTS = 1;
    public static final int TAB_PASSIVES = 2;
    public static final int TAB_CLASSES = 3;
    public static final int TAB_COUNT = 4;

    /** Every section label sits on this line, the first thing inside the body. */
    public static final int SECTION_Y = 50;
    public static final int LABEL_H = 10;
    public static final int SCROLLBAR_W = 5;

    // ---- the Skills tab: the left column --------------------------------------------------------

    public static final int LEFT_X = 18;
    public static final int LEFT_W = 142;

    /** The Below / Above toggle, beside the section label. */
    public static final int BELOW_X = 108;
    public static final int BELOW_Y = 48;
    public static final int BELOW_W = 52;
    public static final int BELOW_H = 14;

    public static final int PYRAMID_Y = 66;
    public static final int PYRAMID_ROW_H = 20;
    public static final int PYRAMID_ROW_STEP = 24;
    public static final int PYRAMID_MAX_W = 130;
    public static final int PYRAMID_WIDTH_STEP = 14;
    public static final int PYRAMID_MAX_ROWS = 6;
    /** The list never climbs above where a three-tier pyramid ends, so it does not jump as tiers appear. */
    public static final int PYRAMID_MIN_ROWS = 3;

    public static final int SKILL_LIST_X = 18;
    public static final int SKILL_LIST_W = 138;
    public static final int SKILL_ROW_H = 20;
    public static final int SKILL_ROW_STRIDE = 22;
    public static final int SKILL_LIST_BOTTOM = 320;
    public static final int SKILL_SCROLL_X = 159;
    public static final int SKILL_EMBLEM_DX = 11;
    public static final int SKILL_EMBLEM_HALF = 9;
    public static final int SKILL_TEXT_DX = 24;

    // ---- the Skills tab: the right column -------------------------------------------------------

    public static final int RIGHT_X = 172;
    public static final int RIGHT_W = 254;

    public static final int LOADOUT_NAME_Y = 62;

    public static final int CARD_Y = 74;
    public static final int CARD_W = 56;
    public static final int CARD_H = 30;
    public static final int CARD_STRIDE = 62;
    public static final int CARD_EMBLEM_DX = 44;
    public static final int CARD_EMBLEM_DY = 11;
    public static final int CARD_EMBLEM_HALF = 8;

    public static final int ACTION_Y = 110;
    public static final int ACTION_W = 88;
    public static final int ACTION_H = 16;
    public static final int CLEAR_DX = 96;

    public static final int DETAIL_LABEL_Y = 134;
    public static final int DETAIL_X = 172;
    public static final int DETAIL_Y = 146;
    public static final int DETAIL_W = 254;
    public static final int DETAIL_H = 174;
    /** The name, school and emblem stay put; only what is under them scrolls. */
    public static final int DETAIL_HEADER_H = 30;
    public static final int DETAIL_EMBLEM_DX = 16;
    public static final int DETAIL_EMBLEM_DY = 15;
    public static final int DETAIL_EMBLEM_HALF = 10;
    public static final int DETAIL_TEXT_DX = 32;
    public static final int DETAIL_CONTENT_DX = 6;
    public static final int DETAIL_CONTENT_W = 236;
    public static final int DETAIL_SCROLL_X = 420;

    // ---- the Loadouts tab -----------------------------------------------------------------------

    public static final int LIST_X = 18;
    public static final int LIST_Y = 66;
    public static final int LIST_W = 166;
    public static final int LIST_ROW_H = 17;
    public static final int LIST_ROW_STRIDE = 20;

    public static final int LIST_ACTION_Y = LIST_Y + MagicContent.MAX_LOADOUTS * LIST_ROW_STRIDE + 8;
    public static final int LIST_ACTION_W = 80;
    public static final int LIST_ACTION_H = 18;
    public static final int DELETE_X = LIST_X + 86;

    /**
     * The rename row, under New/Delete. It sits in the list column rather than beside the keys,
     * because it names the loadout the list is pointing at, not any one key.
     */
    public static final int NAME_LABEL_Y = LIST_ACTION_Y + LIST_ACTION_H + 6;
    public static final int NAME_LABEL_W = 60;
    public static final int NAME_BOX_Y = NAME_LABEL_Y + 12;
    public static final int NAME_BOX_W = 110;
    public static final int NAME_BOX_H = 16;
    public static final int NAME_SAVE_X = LIST_X + 116;
    public static final int NAME_SAVE_W = 50;

    /** Which skill a Bind press would use, on the section line above the key column. */
    public static final int BIND_TARGET_X = 210;
    public static final int BIND_TARGET_W = 216;

    public static final int SLOT_X = 210;
    public static final int SLOT_Y = 66;
    public static final int SLOT_W = 216;
    public static final int SLOT_H = 38;
    public static final int SLOT_STRIDE = 44;
    /** 12 high, not 10: the font is 9, and a 10px button clips its own label top and bottom. */
    public static final int SLOT_BUTTON_DY = 25;
    public static final int SLOT_BUTTON_W = 70;
    public static final int SLOT_BUTTON_H = 12;
    public static final int SLOT_BIND_DX = 6;
    public static final int SLOT_CLEAR_DX = 82;
    public static final int SLOT_EMBLEM_DX = SLOT_W - 18;
    public static final int SLOT_EMBLEM_DY = 13;
    public static final int SLOT_EMBLEM_HALF = 9;
    public static final int SLOT_TEXT_W = SLOT_EMBLEM_DX - SLOT_EMBLEM_HALF - 12;

    // ---- the Passives tab -----------------------------------------------------------------------

    public static final int LISTS_Y = 64;
    public static final int LISTS_BOTTOM = 320;

    public static final int PASSIVES_X = 18;
    public static final int PASSIVES_W = 184;
    public static final int PASSIVES_SCROLL_X = 203;
    public static final int PASSIVE_CARD_W = 180;
    /** Row metrics for the passives list; the draw and click passes must agree on both. */
    public static final int PASSIVE_ROW_H = 32;
    public static final int PASSIVE_HEADER_H = 15;

    public static final int CURSES_X = 220;
    public static final int CURSES_W = 176;
    public static final int CURSES_SCROLL_X = 397;
    public static final int CURSE_ROW_STRIDE = 48;
    public static final int CURSE_CARD_W = 172;
    public static final int CURSE_CARD_H = 42;
    public static final int DISPEL_DX = 96;
    public static final int DISPEL_DY = 22;
    public static final int DISPEL_W = 66;
    public static final int DISPEL_H = 15;

    // ---- the Classes tab ------------------------------------------------------------------------

    public static final int CLASS_LIST_X = 18;
    public static final int CLASS_LIST_Y = 66;
    public static final int CLASS_ROW_W = 408;
    public static final int CLASS_ROW_H = 26;
    public static final int CLASS_ROW_STEP = 30;
    /** Six root rows fit above the workshop buttons; there are fewer roots in play. */
    public static final int CLASS_MAX_ROWS = 6;

    public static final int CLASS_TREE_X = 318;
    public static final int CLASS_TREE_Y = 48;
    public static final int CLASS_TREE_W = 108;
    public static final int CLASS_TREE_H = 16;

    public static final int TOOLS_Y = 302;
    public static final int TOOL_H = 18;
    public static final int FORGE_X = 18;
    public static final int FORGE_W = 92;
    public static final int CREATOR_X = 116;
    public static final int CREATOR_W = 116;

    public static final int EMPTY_H = 64;
    public static final int CHOOSE_DX = 134;
    public static final int CHOOSE_DY = 38;
    public static final int CHOOSE_W = 140;
    public static final int CHOOSE_H = 18;

    private CodexLayout() {}

    // ---- the frame ------------------------------------------------------------------------------

    public static boolean hit(Rect rect, double lx, double ly) {
        return ScreenChrome.hit(rect, lx, ly);
    }

    public static int tabAt(double lx, double ly) {
        return ScreenChrome.tabAt(TAB_COUNT, lx, ly);
    }

    public static int clampScroll(int scroll, int total, int visible) {
        return Mth.clamp(scroll, 0, Math.max(0, total - visible));
    }

    private static Rect sectionLabel(String name, int x, int w) {
        return new Rect(name, x, SECTION_Y, w, LABEL_H);
    }

    private static Rect square(String name, int cx, int cy, int half) {
        return new Rect(name, cx - half, cy - half, half * 2, half * 2);
    }

    /** Which cell of a strip of {@code count} cells, {@code size} long every {@code stride}, a coordinate lands in; -1 in a gap or outside. */
    private static int stripIndex(double along, int size, int stride, int count, boolean across) {
        if (!across || along < 0.0D) {
            return -1;
        }
        int index = (int) (along / stride);
        if (index >= count || along - index * stride >= size) {
            return -1;
        }
        return index;
    }

    // ---- the Skills tab -------------------------------------------------------------------------

    public static Rect pyramidLabel() {
        return sectionLabel("pyramid label", LEFT_X, 80);
    }

    public static Rect belowToggle() {
        return new Rect("below toggle", BELOW_X, BELOW_Y, BELOW_W, BELOW_H);
    }

    /** The space {@code rows} tier blocks take, with the gap after the last one left off. */
    public static Rect pyramidArea(int rows) {
        return new Rect("pyramid", LEFT_X, PYRAMID_Y, LEFT_W, Math.max(1, rows) * PYRAMID_ROW_STEP - (PYRAMID_ROW_STEP - PYRAMID_ROW_H));
    }

    public static Rect pyramidArea() {
        return pyramidArea(PYRAMID_MAX_ROWS);
    }

    /** Row {@code row} of {@code rows}: the widest at the bottom above the line, at the top below it. */
    public static Rect tierBlock(int row, int rows, boolean below) {
        int shrink = below ? row : rows - 1 - row;
        int width = PYRAMID_MAX_W - shrink * PYRAMID_WIDTH_STEP;
        return new Rect("tier block " + row, LEFT_X + (LEFT_W - width) / 2, PYRAMID_Y + row * PYRAMID_ROW_STEP, width, PYRAMID_ROW_H);
    }

    public static int tierRowAt(int rows, boolean below, double lx, double ly) {
        for (int row = 0; row < rows; row++) {
            if (hit(tierBlock(row, rows, below), lx, ly)) {
                return row;
            }
        }
        return -1;
    }

    public static int skillListTop(int rows) {
        return PYRAMID_Y + Mth.clamp(rows, PYRAMID_MIN_ROWS, PYRAMID_MAX_ROWS) * PYRAMID_ROW_STEP + 8;
    }

    public static Rect skillList(int rows) {
        int top = skillListTop(rows);
        return new Rect("skill list", SKILL_LIST_X, top, SKILL_LIST_W, SKILL_LIST_BOTTOM - top);
    }

    /** How many whole rows fit between the pyramid and the bottom of the list. */
    public static int visibleSkillRows(int rows) {
        return (SKILL_LIST_BOTTOM - skillListTop(rows) + (SKILL_ROW_STRIDE - SKILL_ROW_H)) / SKILL_ROW_STRIDE;
    }

    public static Rect skillRow(int rows, int visibleRow) {
        return new Rect("skill row " + visibleRow, SKILL_LIST_X, skillListTop(rows) + visibleRow * SKILL_ROW_STRIDE, SKILL_LIST_W, SKILL_ROW_H);
    }

    public static Rect skillEmblem(int rows, int visibleRow) {
        Rect row = skillRow(rows, visibleRow);
        return square("skill emblem " + visibleRow, row.x() + SKILL_EMBLEM_DX, row.y() + SKILL_ROW_H / 2, SKILL_EMBLEM_HALF);
    }

    public static Rect skillScrollbar(int rows) {
        Rect list = skillList(rows);
        return new Rect("skill scrollbar", SKILL_SCROLL_X, list.y(), SCROLLBAR_W, list.h());
    }

    public static int skillRowAt(int rows, double lx, double ly) {
        return stripIndex(ly - skillListTop(rows), SKILL_ROW_H, SKILL_ROW_STRIDE, visibleSkillRows(rows),
                lx >= SKILL_LIST_X && lx < SKILL_LIST_X + SKILL_LIST_W);
    }

    public static Rect loadoutLabel() {
        return sectionLabel("loadout label", RIGHT_X, 80);
    }

    public static Rect loadoutName() {
        return new Rect("loadout name", RIGHT_X, LOADOUT_NAME_Y, RIGHT_W, LABEL_H);
    }

    public static Rect card(int slot) {
        return new Rect("card " + slot, RIGHT_X + slot * CARD_STRIDE, CARD_Y, CARD_W, CARD_H);
    }

    public static Rect cardEmblem(int slot) {
        Rect card = card(slot);
        return square("card emblem " + slot, card.x() + CARD_EMBLEM_DX, card.y() + CARD_EMBLEM_DY, CARD_EMBLEM_HALF);
    }

    public static int cardAt(double lx, double ly) {
        return stripIndex(lx - RIGHT_X, CARD_W, CARD_STRIDE, MagicContent.LOADOUT_SIZE, ly >= CARD_Y && ly < CARD_Y + CARD_H);
    }

    public static Rect equipButton() {
        return new Rect("equip", RIGHT_X, ACTION_Y, ACTION_W, ACTION_H);
    }

    public static Rect clearSlotButton() {
        return new Rect("clear slot", RIGHT_X + CLEAR_DX, ACTION_Y, ACTION_W, ACTION_H);
    }

    public static Rect detailLabel() {
        return new Rect("detail label", RIGHT_X, DETAIL_LABEL_Y, 100, LABEL_H);
    }

    public static Rect detail() {
        return new Rect("detail", DETAIL_X, DETAIL_Y, DETAIL_W, DETAIL_H);
    }

    public static Rect detailHeader() {
        return new Rect("detail header", DETAIL_X, DETAIL_Y, DETAIL_W, DETAIL_HEADER_H);
    }

    public static Rect detailEmblem() {
        return square("detail emblem", DETAIL_X + DETAIL_EMBLEM_DX, DETAIL_Y + DETAIL_EMBLEM_DY, DETAIL_EMBLEM_HALF);
    }

    public static Rect detailContent() {
        return new Rect("detail content", DETAIL_X + DETAIL_CONTENT_DX, DETAIL_Y + DETAIL_HEADER_H, DETAIL_CONTENT_W, DETAIL_H - DETAIL_HEADER_H - 4);
    }

    public static Rect detailScrollbar() {
        return new Rect("detail scrollbar", DETAIL_SCROLL_X, DETAIL_Y + DETAIL_HEADER_H, SCROLLBAR_W, DETAIL_H - DETAIL_HEADER_H - 4);
    }

    /** Every rectangle the Skills tab draws or hit-tests when the pyramid has {@code rows} tiers. */
    public static List<Rect> skillsTabRects(int rows) {
        List<Rect> rects = new ArrayList<>();
        rects.add(pyramidLabel());
        rects.add(belowToggle());
        rects.add(pyramidArea(rows));
        rects.add(skillList(rows));
        rects.add(skillScrollbar(rows));
        rects.add(loadoutLabel());
        rects.add(loadoutName());
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            rects.add(card(slot));
        }
        rects.add(equipButton());
        rects.add(clearSlotButton());
        rects.add(detailLabel());
        rects.add(detail());
        return rects;
    }

    // ---- the Loadouts tab -----------------------------------------------------------------------

    public static Rect loadoutsLabel() {
        return sectionLabel("loadouts label", LIST_X, 100);
    }

    public static Rect bindTarget() {
        return new Rect("bind target", BIND_TARGET_X, SECTION_Y, BIND_TARGET_W, LABEL_H);
    }

    public static Rect loadoutRow(int index) {
        return new Rect("loadout row " + index, LIST_X, LIST_Y + index * LIST_ROW_STRIDE, LIST_W, LIST_ROW_H);
    }

    public static int loadoutRowAt(double lx, double ly) {
        return stripIndex(ly - LIST_Y, LIST_ROW_H, LIST_ROW_STRIDE, MagicContent.MAX_LOADOUTS, lx >= LIST_X && lx < LIST_X + LIST_W);
    }

    public static Rect newButton() {
        return new Rect("new", LIST_X, LIST_ACTION_Y, LIST_ACTION_W, LIST_ACTION_H);
    }

    public static Rect deleteButton() {
        return new Rect("delete", DELETE_X, LIST_ACTION_Y, LIST_ACTION_W, LIST_ACTION_H);
    }

    public static Rect nameLabel() {
        return new Rect("name label", LIST_X, NAME_LABEL_Y, NAME_LABEL_W, LABEL_H);
    }

    public static Rect nameBox() {
        return new Rect("name box", LIST_X, NAME_BOX_Y, NAME_BOX_W, NAME_BOX_H);
    }

    public static Rect renameButton() {
        return new Rect("rename", NAME_SAVE_X, NAME_BOX_Y, NAME_SAVE_W, NAME_BOX_H);
    }

    public static Rect keySlot(int slot) {
        return new Rect("key slot " + slot, SLOT_X, SLOT_Y + slot * SLOT_STRIDE, SLOT_W, SLOT_H);
    }

    public static Rect keySlotEmblem(int slot) {
        Rect card = keySlot(slot);
        return square("key slot emblem " + slot, card.x() + SLOT_EMBLEM_DX, card.y() + SLOT_EMBLEM_DY, SLOT_EMBLEM_HALF);
    }

    public static Rect bindButton(int slot) {
        Rect card = keySlot(slot);
        return new Rect("bind " + slot, card.x() + SLOT_BIND_DX, card.y() + SLOT_BUTTON_DY, SLOT_BUTTON_W, SLOT_BUTTON_H);
    }

    public static Rect clearButton(int slot) {
        Rect card = keySlot(slot);
        return new Rect("clear " + slot, card.x() + SLOT_CLEAR_DX, card.y() + SLOT_BUTTON_DY, SLOT_BUTTON_W, SLOT_BUTTON_H);
    }

    public static int keySlotAt(double lx, double ly) {
        return stripIndex(ly - SLOT_Y, SLOT_H, SLOT_STRIDE, MagicContent.LOADOUT_SIZE, lx >= SLOT_X && lx < SLOT_X + SLOT_W);
    }

    /** Every rectangle the Loadouts tab draws or hit-tests. */
    public static List<Rect> loadoutsTabRects() {
        List<Rect> rects = new ArrayList<>();
        rects.add(loadoutsLabel());
        rects.add(bindTarget());
        for (int index = 0; index < MagicContent.MAX_LOADOUTS; index++) {
            rects.add(loadoutRow(index));
        }
        rects.add(newButton());
        rects.add(deleteButton());
        rects.add(nameLabel());
        rects.add(nameBox());
        rects.add(renameButton());
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            rects.add(keySlot(slot));
        }
        return rects;
    }

    // ---- the Passives tab -----------------------------------------------------------------------

    public static Rect passivesLabel() {
        return sectionLabel("passives label", PASSIVES_X, 100);
    }

    public static Rect passivesList() {
        return new Rect("passives list", PASSIVES_X, LISTS_Y, PASSIVES_W, LISTS_BOTTOM - LISTS_Y);
    }

    public static Rect passivesScrollbar() {
        return new Rect("passives scrollbar", PASSIVES_SCROLL_X, LISTS_Y, SCROLLBAR_W, LISTS_BOTTOM - LISTS_Y);
    }

    public static int visiblePassiveRows() {
        return (LISTS_BOTTOM - LISTS_Y) / PASSIVE_ROW_H;
    }

    /** A passive card at the row top {@code y} the walk over the grouped rows arrived at. */
    public static Rect passiveCard(int y) {
        return new Rect("passive card", PASSIVES_X, y, PASSIVE_CARD_W, PASSIVE_ROW_H - 4);
    }

    public static Rect cursesLabel() {
        return sectionLabel("curses label", CURSES_X, 100);
    }

    public static Rect cursesList() {
        return new Rect("curses list", CURSES_X, LISTS_Y, CURSES_W, LISTS_BOTTOM - LISTS_Y);
    }

    public static Rect cursesScrollbar() {
        return new Rect("curses scrollbar", CURSES_SCROLL_X, LISTS_Y, SCROLLBAR_W, LISTS_BOTTOM - LISTS_Y);
    }

    public static int visibleCurseRows() {
        return (LISTS_BOTTOM - LISTS_Y) / CURSE_ROW_STRIDE;
    }

    public static Rect curseCard(int visibleRow) {
        return new Rect("curse card " + visibleRow, CURSES_X, LISTS_Y + visibleRow * CURSE_ROW_STRIDE, CURSE_CARD_W, CURSE_CARD_H);
    }

    public static Rect dispelButton(int visibleRow) {
        Rect card = curseCard(visibleRow);
        return new Rect("dispel " + visibleRow, card.x() + DISPEL_DX, card.y() + DISPEL_DY, DISPEL_W, DISPEL_H);
    }

    /** The two columns and their scrollbars; the cards inside are checked against their lists. */
    public static List<Rect> passivesTabRects() {
        return List.of(passivesLabel(), passivesList(), passivesScrollbar(), cursesLabel(), cursesList(), cursesScrollbar());
    }

    // ---- the Classes tab ------------------------------------------------------------------------

    public static Rect classesLabel() {
        return sectionLabel("classes label", CLASS_LIST_X, 100);
    }

    public static Rect classTreeButton() {
        return new Rect("class tree", CLASS_TREE_X, CLASS_TREE_Y, CLASS_TREE_W, CLASS_TREE_H);
    }

    public static Rect classRow(int row) {
        return new Rect("class row " + row, CLASS_LIST_X, CLASS_LIST_Y + row * CLASS_ROW_STEP, CLASS_ROW_W, CLASS_ROW_H);
    }

    public static int classRowAt(int count, double lx, double ly) {
        return stripIndex(ly - CLASS_LIST_Y, CLASS_ROW_H, CLASS_ROW_STEP, Math.min(count, CLASS_MAX_ROWS),
                lx >= CLASS_LIST_X && lx < CLASS_LIST_X + CLASS_ROW_W);
    }

    public static Rect forgeButton() {
        return new Rect("forge", FORGE_X, TOOLS_Y, FORGE_W, TOOL_H);
    }

    public static Rect creatorButton() {
        return new Rect("creator", CREATOR_X, TOOLS_Y, CREATOR_W, TOOL_H);
    }

    public static Rect emptyClasses() {
        return new Rect("no classes", CLASS_LIST_X, CLASS_LIST_Y, CLASS_ROW_W, EMPTY_H);
    }

    public static Rect chooseClassButton() {
        return new Rect("choose class", CLASS_LIST_X + CHOOSE_DX, CLASS_LIST_Y + CHOOSE_DY, CHOOSE_W, CHOOSE_H);
    }

    /** Every rectangle the Classes tab draws or hit-tests with {@code rows} root classes owned. */
    public static List<Rect> classesTabRects(int rows) {
        List<Rect> rects = new ArrayList<>();
        rects.add(classesLabel());
        rects.add(classTreeButton());
        for (int row = 0; row < Math.min(rows, CLASS_MAX_ROWS); row++) {
            rects.add(classRow(row));
        }
        rects.add(forgeButton());
        rects.add(creatorButton());
        return rects;
    }

    /** The tab with no root class owned yet: the prompt in place of the rows. */
    public static List<Rect> emptyClassesRects() {
        return List.of(classesLabel(), emptyClasses(), forgeButton(), creatorButton());
    }
}
