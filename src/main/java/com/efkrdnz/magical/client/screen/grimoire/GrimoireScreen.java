package com.efkrdnz.magical.client.screen.grimoire;

import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.CATEGORY_COUNT;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.CAPTION_LINES;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.CATEGORY_GLYPH_GAP;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.ICON;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.LINE_H;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.RULE_H;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.SLOT_COUNT;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.TAB_COUNT;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.TAB_Y;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.approach;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.block;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.blockWidth;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.caption;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.categories;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.categoriesFitWithWords;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.categoryWidth;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.clampGridScroll;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.controls;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.ease;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.easeOut;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.gridArea;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.gridCell;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.gridColumns;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.gridIcon;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.gridIndexAt;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.gridRows;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.gridRowsVisible;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.hit;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.indexAt;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.overRow;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.reading;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.slot;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.slotAt;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.slotBar;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.slotInsertionAt;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.slotStride;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.slotSymbol;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.tabRule;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.tabs;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.tabsFitWithWords;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.hud.HudQuiet;
import com.efkrdnz.magical.client.hud.MagicalClientConfig;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.Controls;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.incantation.Incantation;
import com.efkrdnz.magical.magic.incantation.IncantationService;
import com.efkrdnz.magical.magic.incantation.IncantationValidator;
import com.efkrdnz.magical.magic.incantation.Landing;
import com.efkrdnz.magical.magic.incantation.LandingText;
import com.efkrdnz.magical.magic.incantation.PreviewReciteWorld;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.RecitePlan;
import com.efkrdnz.magical.magic.incantation.ReciteSession;
import com.efkrdnz.magical.magic.incantation.Reciter;
import com.efkrdnz.magical.magic.incantation.Verse;
import com.efkrdnz.magical.magic.incantation.VerseContent;
import com.efkrdnz.magical.magic.incantation.VerseType;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Grimoire: where the four incantations are written, over the world.
 *
 * <p>Drawn the way the Manipulate Space selector is: no panel, no plate and no frame. The world is
 * dimmed and everything is text and glyphs on it, every string with vanilla's drop shadow, which is
 * what lets it sit on daylight terrain unbacked. The modded HUD stands down while it is open
 * ({@link HudQuiet}). One line of tabs, one incantation each; one line of categories, All and the
 * eight types; the verses the wielder knows as a grid of their {@link VerseSymbols} symbols, spaced,
 * in their types' colours; a caption naming whatever the cursor is over; and the incantation itself
 * as a <b>row of twenty slots</b>, each an underline to be written on, the first {@code breath} of
 * them lit because those are what one press reads. Under it the breath, Save and Clear as words,
 * and the reading: the pure {@code Reciter} run on a copy of the row, so the screen says on every
 * change exactly what the server would cast.
 *
 * <p>Writing is drag and drop. Press a verse in the grid, or a written slot, and it is in hand; once
 * the cursor has travelled {@link #DRAG_THRESHOLD} it is a drag and a ghost of the verse rides
 * beside the cursor. Over the row the written verses <em>part</em> to make a place for it, sliding
 * one slot right of the boundary nearest the cursor, and the empty underline there brightens; let go
 * and it is set down with a small overshoot. A slot dragged elsewhere on the row is reordered the
 * same way; dragged off the row it falls away and the row closes over it. A press that never travels
 * is a click: a grid verse is written at the end, and a right-click on a slot strikes it.
 *
 * <p>Everything else that moves says what just happened and nothing more: the grid deals its
 * symbols in when the category changes, the tab mark slides to the tab that was chosen, a hovered
 * symbol grows, Save runs one bright sweep along the underlines as the incantation is read, and
 * reduced motion cuts every one of these to its final frame.
 *
 * <p>Edits live on the screen per tab until Save sends them; the saved copy is reloaded from the
 * state only for a tab with nothing pending, so a mana tick arriving mid-edit clobbers nothing.
 */
public final class GrimoireScreen extends Screen implements HudDebug.Captured, HudQuiet {

    /** One colour for the whole thing: the Authority of Mana's own light. */
    private static final int ACCENT = 0xF0F4FF;
    /** The world is dimmed rather than covered; the same scrim as the rule selector. */
    private static final int SCRIM = 0xA6060B14;
    private static final int TEXT_BRIGHT = 0xFFFFFF;
    private static final int TEXT_NEAR = 0xC9D8E6;
    private static final int TEXT_MUTED = 0x8494A6;
    private static final int TEXT_FAINT = 0x55626F;
    /** An empty slot's underline, and what a glyph fades toward: the dimmed world behind it. */
    private static final int BLANK_INK = 0x3A4450;
    private static final int BADGE_BACK = 0x0B0F16;
    private static final int REFUSED_RED = 0xFF6B7B;
    private static final int FRAYED_ORANGE = 0xFFB86C;
    private static final int REFUND_GREEN = 0x8FE3A0;

    // ---- motion, in ticks -------------------------------------------------------------------------

    private static final float FADE_IN_TICKS = 4.0F;
    /** A symbol dealt onto the grid or the row rises this far into its place. */
    private static final float DEAL_TICKS = 5.0F;
    private static final float DEAL_RISE = 5.0F;
    private static final float DEAL_STAGGER = 0.12F;
    private static final int DEAL_STAGGER_CAP = 48;
    private static final float SLOT_STAGGER = 0.5F;
    private static final float HOVER_SCALE = 1.6F;
    private static final float HOVER_HALF_LIFE = 1.2F;
    /** How fast the row parts and closes. */
    private static final float PART_HALF_LIFE = 1.0F;
    private static final float LAND_TICKS = 6.0F;
    private static final float FALL_TICKS = 5.0F;
    private static final float FALL_DROP = 8.0F;
    private static final float FOCUS_SLIDE_TICKS = 4.0F;
    private static final float SWEEP_TICKS = 3.0F;
    private static final float SWEEP_STAGGER = 0.35F;
    private static final float PULSE_PERIOD = 14.0F;
    private static final float GHOST_SCALE = 1.6F;
    private static final int GHOST_DX = 12;
    /** A press that travels this far becomes a drag; short of it, it is a click. */
    private static final double DRAG_THRESHOLD = 3.0D;
    private static final int PENDING_TIMEOUT_TICKS = 60;
    private static final float LONG_AGO = -1.0e6F;

    private static final VerseType[] TYPES = VerseType.values();
    private static final VerseSymbols.Symbol[] CATEGORY_SYMBOLS = categorySymbols();

    /** Where the press in hand came from. */
    private enum DragSource { GRID, ROW }

    private record Line(String text, int rgb) {}

    /** The block this frame: its width, the grid's shape, and where it sits on the screen. */
    private record Frame(int width, int columns, int rows, int visibleRows, int x0, int y0) {}

    private int slot;
    /** -1 for All, else the ordinal of the one type shown. */
    private int category = -1;
    /** Every verse the wielder knows, by type then name. */
    private List<Verse> library = List.of();
    /** The library, or the one type of it the category row has picked. */
    private List<Verse> shelf = List.of();
    private final int[] counts = new int[TYPES.length];
    private int gridScroll;
    /** How far each grid symbol has grown toward its hovered size, 0..1. */
    private float[] grow = new float[0];
    private final List<List<ResourceLocation>> pages = new ArrayList<>(TAB_COUNT);
    private final int[] breaths = new int[TAB_COUNT];
    private final boolean[] dirty = new boolean[TAB_COUNT];
    /** Ticks since Save sent a tab, -1 when nothing is in flight. */
    private final int[] pending = new int[TAB_COUNT];
    private int seenVersion = Integer.MIN_VALUE;
    private RecitePlan plan;
    private IncantationValidator.Finding problem;

    // clocks
    private boolean opened;
    private float openedAt;
    private float gridDealtAt;
    private float slotsDealtAt;
    private float focusAt;
    private int focusFrom;
    private float lastFrame;
    private float savedAt = LONG_AGO;
    /** Where each written symbol is drawn relative to its slot, gliding toward where it belongs. */
    private final float[] slotShift = new float[SLOT_COUNT];
    private final float[] landedAt = new float[SLOT_COUNT];
    /** A struck symbol on its way down. */
    private Verse fallen;
    private float fallX;
    private float fallY;
    private float fallAt = LONG_AGO;

    // the press in hand
    private DragSource dragSource;
    private ResourceLocation dragId;
    private int dragFrom;
    private double pressX;
    private double pressY;
    private boolean dragging;

    public GrimoireScreen() {
        super(Component.translatable("screen.magical.grimoire"));
        for (int s = 0; s < TAB_COUNT; s++) {
            pages.add(new ArrayList<>());
            breaths[s] = ReciteCaps.MIN_BREATH;
            pending[s] = -1;
        }
        Arrays.fill(landedAt, LONG_AGO);
    }

    /** The one way in. Closes whatever container is open first, which also tells the server. */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
            minecraft.player.closeContainer();
        }
        minecraft.setScreen(new GrimoireScreen());
    }

    @Override
    protected void init() {
        float now = now();
        if (!opened) {
            opened = true;
            openedAt = now;
            gridDealtAt = now;
            slotsDealtAt = now;
            focusAt = LONG_AGO;
            lastFrame = now;
        }
        seenVersion = ClientMagicState.version();
        refreshShelf();
        for (int s = 0; s < TAB_COUNT; s++) {
            if (!dirty[s] && pending[s] < 0) {
                load(s);
            }
        }
        reread();
    }

    /** The codex never paused either; a paused integrated server would never answer Save. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        boolean changed = ClientMagicState.version() != seenVersion;
        boolean rereadNeeded = changed;
        if (changed) {
            seenVersion = ClientMagicState.version();
            refreshShelf();
        }
        for (int s = 0; s < TAB_COUNT; s++) {
            if (pending[s] >= 0) {
                if (changed && matchesState(s)) {
                    pending[s] = -1;
                    dirty[s] = false;
                } else if (++pending[s] > PENDING_TIMEOUT_TICKS) {
                    // The server never answered, or it refused (it says so in chat): the truth is the state.
                    pending[s] = -1;
                    load(s);
                    rereadNeeded |= s == slot;
                }
            } else if (changed && !dirty[s]) {
                load(s);
            }
        }
        if (rereadNeeded) {
            reread();
        }
    }

    // ---- state -----------------------------------------------------------------------------------

    /** The library from the state, sorted by type then name, and the grid as the category row filters it. */
    private void refreshShelf() {
        List<Verse> all = new ArrayList<>();
        for (ResourceLocation id : state().grimoire().known()) {
            Verse verse = verse(id);
            if (verse != null) {
                all.add(verse);
            }
        }
        all.sort(Comparator.comparingInt((Verse verse) -> verse.type().ordinal()).thenComparing(Verse::path));
        Arrays.fill(counts, 0);
        for (Verse verse : all) {
            counts[verse.type().ordinal()]++;
        }
        library = all;
        if (category < 0) {
            shelf = all;
        } else {
            List<Verse> picked = new ArrayList<>(counts[category]);
            for (Verse verse : all) {
                if (verse.type().ordinal() == category) {
                    picked.add(verse);
                }
            }
            shelf = picked;
        }
        grow = new float[shelf.size()];
        Frame f = frame();
        gridScroll = clampGridScroll(gridScroll, f.rows(), f.visibleRows());
    }

    private void load(int s) {
        Incantation incantation = state().grimoire().incantation(s);
        List<ResourceLocation> page = pages.get(s);
        page.clear();
        for (Incantation.Entry entry : incantation.entries()) {
            page.add(entry.id());
        }
        breaths[s] = clampBreath(incantation.breath());
        dirty[s] = false;
    }

    /** Whether the state now holds exactly what the screen sent for a tab: the write landed. */
    private boolean matchesState(int s) {
        Incantation incantation = state().grimoire().incantation(s);
        List<Incantation.Entry> entries = incantation.entries();
        List<ResourceLocation> page = pages.get(s);
        if (entries.size() != page.size() || clampBreath(incantation.breath()) != breaths[s]) {
            return false;
        }
        for (int i = 0; i < page.size(); i++) {
            if (!entries.get(i).id().equals(page.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int clampBreath(int breath) {
        return Math.max(ReciteCaps.MIN_BREATH, Math.min(ReciteCaps.MAX_BREATH, breath));
    }

    /**
     * Reads the row the way a press would: the validator first (the same one the server runs),
     * then the Reciter on a copy against the assumed world, so nothing here spends a use, a heart
     * or the Grimoire's toggle.
     */
    private void reread() {
        PlayerMagicState state = state();
        List<ResourceLocation> page = pages.get(slot);
        List<IncantationValidator.Finding> findings =
                IncantationValidator.problems(page, breaths[slot], state.grimoire().known(), VerseContent.CATALOGUE);
        problem = findings.isEmpty() ? null : findings.get(0);
        plan = null;
        if (problem != null || page.isEmpty()) {
            return;
        }
        Incantation copy = new Incantation();
        if (!copy.write(page, breaths[slot], VerseContent.CATALOGUE)) {
            return;
        }
        MagicSkillDefinition skill = IncantationService.skillFor(slot);
        double costScale = skill.resolve(state.tuningFor(skill.id())).costScale();
        plan = Reciter.recite(ReciteSession.of(copy, VerseContent.CATALOGUE), breaths[slot], state.mana(), costScale,
                new PreviewReciteWorld(state.grimoire(), slot, VerseContent.CATALOGUE));
    }

    private void touched() {
        dirty[slot] = true;
        reread();
    }

    private void saveCurrent() {
        if (!dirty[slot] || problem != null || pending[slot] >= 0) {
            return;
        }
        List<String> ids = new ArrayList<>();
        for (ResourceLocation id : pages.get(slot)) {
            ids.add(id.toString());
        }
        MagicalNetwork.sendIncantation(slot, breaths[slot], ids);
        pending[slot] = 0;
        savedAt = now();
    }

    private void setBreath(int breath) {
        int clamped = clampBreath(breath);
        if (clamped != breaths[slot]) {
            breaths[slot] = clamped;
            touched();
        }
    }

    // ---- geometry ---------------------------------------------------------------------------------

    private Frame frame() {
        int w = blockWidth(width);
        int columns = gridColumns(w);
        int rows = gridRows(shelf.size(), columns);
        // The block is sized for everything the wielder knows, not for the category shown, so
        // picking one never moves the row or the words under it.
        int visible = gridRowsVisible(height, gridRows(library.size(), columns));
        Rect b = block(width, height, visible);
        return new Frame(w, columns, rows, visible, b.x(), b.y());
    }

    /** The incantations' names in capitals, or their numerals alone where four names will not fit the block. */
    private String[] tabLabels(Frame f) {
        String[] labels = new String[TAB_COUNT];
        for (int s = 0; s < TAB_COUNT; s++) {
            labels[s] = Component.translatable(IncantationService.skillFor(s).nameKey()).getString().toUpperCase(Locale.ROOT);
        }
        if (!tabsFitWithWords(f.width(), widths(labels))) {
            for (int s = 0; s < TAB_COUNT; s++) {
                int space = labels[s].lastIndexOf(' ');
                labels[s] = space < 0 ? labels[s] : labels[s].substring(space + 1);
            }
        }
        return labels;
    }

    private int[] widths(String[] words) {
        int[] widths = new int[words.length];
        for (int i = 0; i < words.length; i++) {
            widths[i] = font.width(words[i]);
        }
        return widths;
    }

    private List<Rect> tabRects(Frame f, String[] labels) {
        return tabs(f.width(), widths(labels));
    }

    private String categoryWord(int c) {
        return c == 0 ? Component.translatable("screen.magical.grimoire.all").getString() : typeName(TYPES[c - 1]).getString();
    }

    private int[] categoryWordWidths() {
        int[] widths = new int[CATEGORY_COUNT];
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            widths[c] = font.width(categoryWord(c));
        }
        return widths;
    }

    private boolean categoryWordsShown(Frame f) {
        return categoriesFitWithWords(f.width(), categoryWordWidths());
    }

    private List<Rect> categoryRects(Frame f) {
        int[] words = categoryWordWidths();
        boolean shown = categoriesFitWithWords(f.width(), words);
        int[] widths = new int[CATEGORY_COUNT];
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            widths[c] = shown ? categoryWidth(words[c]) : ICON;
        }
        return categories(f.width(), widths);
    }

    private String breathLabel() {
        return Component.translatable("screen.magical.grimoire.breath").getString().toUpperCase(Locale.ROOT);
    }

    private String saveLabel() {
        if (pending[slot] >= 0) {
            return Component.translatable("screen.magical.grimoire.saving").getString();
        }
        return Component.translatable(dirty[slot] ? "screen.magical.grimoire.save" : "screen.magical.grimoire.saved").getString();
    }

    private String clearLabel() {
        return Component.translatable("screen.magical.grimoire.clear").getString();
    }

    private Controls controlRects(Frame f) {
        return controls(f.width(), f.visibleRows(), font.width(breathLabel()), font.width("-"),
                font.width(String.valueOf(breaths[slot])), font.width("+"), font.width(saveLabel()), font.width(clearLabel()));
    }

    // ---- drawing --------------------------------------------------------------------------------

    /** Only the scrim: no blur and no vanilla gradient, so the world reads through as it does under the selector. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        float fade = motion(easeOut((now() - openedAt) / FADE_IN_TICKS));
        g.fill(0, 0, width, height, withAlpha(SCRIM, Math.round((SCRIM >>> 24) * fade)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        float now = now();
        float dt = Mth.clamp(now - lastFrame, 0.0F, 2.0F);
        lastFrame = now;
        float fade = motion(easeOut((now - openedAt) / FADE_IN_TICKS));
        Frame f = frame();
        double lx = mouseX - f.x0();
        double ly = mouseY - f.y0();
        int hovered = dragging ? -1 : gridIndexAt(f.width(), f.columns(), f.visibleRows(), gridScroll, shelf.size(), lx, ly);

        paintTabs(g, f, now, fade, lx, ly);
        paintCategories(g, f, fade, lx, ly);
        paintGrid(g, f, now, dt, fade, hovered);
        paintCaption(g, f, fade, hovered, lx, ly);
        paintRow(g, f, now, dt, fade, lx, ly);
        paintControls(g, f, fade, lx, ly);
        paintReading(g, f, fade);
        paintFallen(g, now, fade);
        if (dragging) {
            paintGhost(g, mouseX, mouseY);
        }
    }

    /** The four incantations as words, the open one lit, the mark part way from the last one to it, and the pool at the block's end. */
    private void paintTabs(GuiGraphics g, Frame f, float now, float fade, double lx, double ly) {
        String[] labels = tabLabels(f);
        List<Rect> rects = tabRects(f, labels);
        int hoveredTab = dragging ? -1 : indexAt(rects, lx, ly);
        for (int s = 0; s < TAB_COUNT; s++) {
            Rect r = rects.get(s);
            int rgb = s == slot ? ACCENT : s == hoveredTab ? TEXT_NEAR : TEXT_FAINT;
            String label = font.plainSubstrByWidth(labels[s], r.w());
            g.drawString(font, label, f.x0() + r.x(), f.y0() + r.y(), ink(rgb, fade), true);
            if (dirty[s] || pending[s] >= 0) {
                // A tab with unsaved writing carries a spark after its name.
                int dx = f.x0() + r.x() + font.width(label) + 3;
                int dy = f.y0() + r.y() + 3;
                g.fill(dx, dy, dx + 2, dy + 2, ink(pending[s] >= 0 ? TEXT_MUTED : ACCENT, fade));
            }
        }
        float travel = motion(easeOut((now - focusAt) / FOCUS_SLIDE_TICKS));
        Rect from = tabRule(rects.get(focusFrom));
        Rect to = tabRule(rects.get(slot));
        int x = Math.round(Mth.lerp(travel, from.x(), to.x()));
        int w = Math.round(Mth.lerp(travel, from.w(), to.w()));
        g.fill(f.x0() + x, f.y0() + to.y(), f.x0() + x + w, f.y0() + to.y() + RULE_H, ink(ACCENT, fade));

        PlayerMagicState state = state();
        String pool = Component.translatable("screen.magical.grimoire.mana_pool", state.mana(), state.maxMana()).getString();
        int poolW = font.width(pool);
        if (rects.get(TAB_COUNT - 1).right() + 12 <= f.width() - poolW) {
            g.drawString(font, pool, f.x0() + f.width() - poolW, f.y0() + TAB_Y, ink(TEXT_FAINT, fade), true);
        }
    }

    /** All and the eight types, each its glyph in its colour and its word beside it where the row is wide enough. */
    private void paintCategories(GuiGraphics g, Frame f, float fade, double lx, double ly) {
        List<Rect> rects = categoryRects(f);
        boolean words = categoryWordsShown(f);
        int hoveredCat = dragging ? -1 : indexAt(rects, lx, ly);
        for (int c = 0; c < CATEGORY_COUNT; c++) {
            Rect r = rects.get(c);
            boolean lit = c == 0 ? category < 0 : category == c - 1;
            boolean known = c == 0 ? !library.isEmpty() : counts[c - 1] > 0;
            int typeRgb = c == 0 ? TEXT_NEAR : typeColor(TYPES[c - 1]);
            float strength = lit ? 1.0F : hoveredCat == c ? 0.8F : known ? 0.5F : 0.25F;
            drawSymbol(g, CATEGORY_SYMBOLS[c], f.x0() + r.x(), f.y0() + r.y(), 1.0F, mix(BLANK_INK, typeRgb, strength), fade);
            if (words) {
                int wordRgb = lit ? TEXT_BRIGHT : hoveredCat == c ? TEXT_NEAR : known ? TEXT_MUTED : TEXT_FAINT;
                String word = font.plainSubstrByWidth(categoryWord(c), Math.max(1, r.w() - ICON - CATEGORY_GLYPH_GAP));
                g.drawString(font, word, f.x0() + r.x() + ICON + CATEGORY_GLYPH_GAP, f.y0() + r.y(), ink(wordRgb, fade), true);
            }
        }
    }

    /** The known verses as symbols, dealt in with a stagger, the hovered one grown and drawn last so it sits over its neighbours. */
    private void paintGrid(GuiGraphics g, Frame f, float now, float dt, float fade, int hovered) {
        if (grow.length != shelf.size()) {
            grow = new float[shelf.size()];
        }
        Rect area = gridArea(f.width(), f.columns(), f.visibleRows());
        int first = gridScroll * f.columns();
        int last = Math.min(shelf.size(), first + f.visibleRows() * f.columns());
        g.enableScissor(f.x0() + area.x(), f.y0() + area.y(), f.x0() + area.right(), f.y0() + area.bottom());
        for (int i = first; i < last; i++) {
            grow[i] = approach(grow[i], i == hovered ? 1.0F : 0.0F, dt, halfLife(HOVER_HALF_LIFE));
            if (i != hovered) {
                paintGridSymbol(g, f, i, now, fade);
            }
        }
        if (hovered >= first && hovered < last) {
            paintGridSymbol(g, f, hovered, now, fade);
        }
        g.disableScissor();
    }

    private void paintGridSymbol(GuiGraphics g, Frame f, int i, float now, float fade) {
        Verse verse = shelf.get(i);
        Rect icon = gridIcon(gridCell(f.width(), f.columns(), i, gridScroll));
        float deal = motion(easeOut((now - gridDealtAt - Math.min(i, DEAL_STAGGER_CAP) * DEAL_STAGGER) / DEAL_TICKS));
        float scale = 1.0F + (HOVER_SCALE - 1.0F) * grow[i];
        float cx = f.x0() + icon.x() + ICON / 2.0F;
        float cy = f.y0() + icon.y() + ICON / 2.0F + (1.0F - deal) * DEAL_RISE;
        drawSymbol(g, VerseSymbols.of(verse), cx - ICON * scale / 2.0F, cy - ICON * scale / 2.0F, scale,
                typeColor(verse.type()), fade * deal);
    }

    /** Names what the cursor is over - a grid verse, a written slot, the verse in hand - or says how to begin. */
    private void paintCaption(GuiGraphics g, Frame f, float fade, int hovered, double lx, double ly) {
        Rect r = caption(f.width(), f.visibleRows());
        int cx = f.x0() + f.width() / 2;
        int y = f.y0() + r.y();
        List<ResourceLocation> page = pages.get(slot);
        Verse verse = null;
        if (dragging) {
            verse = verse(dragId);
        } else if (hovered >= 0) {
            verse = shelf.get(hovered);
        } else {
            int over = slotAt(f.width(), f.visibleRows(), lx, ly);
            if (over >= 0 && over < page.size()) {
                verse = verse(page.get(over));
            }
        }
        if (verse != null) {
            if (dragging && dragSource == DragSource.GRID && page.size() >= SLOT_COUNT) {
                g.drawCenteredString(font, Component.translatable("screen.magical.grimoire.row_full").getString(), cx, y, ink(REFUSED_RED, fade));
            } else {
                String name = name(verse.id()).getString();
                String detail = typeAndMana(verse).getString();
                int gap = 8;
                int x = cx - (font.width(name) + gap + font.width(detail)) / 2;
                g.drawString(font, name, x, y, ink(typeColor(verse.type()), fade), true);
                g.drawString(font, detail, x + font.width(name) + gap, y, ink(TEXT_MUTED, fade), true);
            }
            // What the verse does, under its name, over the lines the caption keeps for it.
            List<FormattedCharSequence> lines = font.split(description(verse.id()), f.width());
            for (int i = 0; i < lines.size() && i < CAPTION_LINES - 1; i++) {
                g.drawCenteredString(font, lines.get(i), cx, y + (i + 1) * LINE_H, ink(TEXT_NEAR, fade));
            }
            return;
        }
        String hint;
        if (library.isEmpty()) {
            hint = Component.translatable("screen.magical.grimoire.no_verses").getString();
        } else if (shelf.isEmpty()) {
            hint = Component.translatable("screen.magical.grimoire.none_of_type", typeName(TYPES[category])).getString();
        } else {
            hint = Component.translatable("screen.magical.grimoire.hint").getString();
        }
        g.drawCenteredString(font, font.plainSubstrByWidth(hint, f.width()), cx, y, ink(TEXT_FAINT, fade));
    }

    /**
     * The twenty underlines, the first {@code breath} of them lit, and the symbols written on them.
     *
     * <p>While a drag is over the row the written symbols from the boundary nearest the cursor
     * onward are drawn one slot right, so the place the drop would take is open and its underline
     * brightens; the shift is eased, so the row parts and closes rather than jumping. A symbol
     * lifted off the row for a reorder leaves its slot empty and the ones after it close up.
     */
    private void paintRow(GuiGraphics g, Frame f, float now, float dt, float fade, double lx, double ly) {
        List<ResourceLocation> page = pages.get(slot);
        int stride = slotStride(f.width());
        boolean over = dragging && overRow(f.width(), f.visibleRows(), lx, ly);
        int lifted = dragging && dragSource == DragSource.ROW ? dragFrom : -1;
        int virtualSize = page.size() - (lifted >= 0 ? 1 : 0);
        boolean room = dragSource == DragSource.ROW || page.size() < SLOT_COUNT;
        int cursor = over ? slotInsertionAt(f.width(), lx, virtualSize) : -1;
        int part = room ? cursor : -1;
        float pulse = 0.5F + 0.5F * Mth.sin(now * (float) (2.0D * Math.PI) / PULSE_PERIOD);
        boolean reduced = MagicalClientConfig.current().reducedMotion();

        for (int j = 0; j < SLOT_COUNT; j++) {
            Rect bar = slotBar(slot(f.width(), f.visibleRows(), j));
            int rgb = j < breaths[slot] ? ACCENT : BLANK_INK;
            float sweep = now - savedAt - j * SWEEP_STAGGER;
            if (!reduced && sweep >= 0.0F && sweep < SWEEP_TICKS) {
                rgb = mix(rgb, TEXT_BRIGHT, 1.0F - sweep / SWEEP_TICKS);
            }
            int h = RULE_H;
            if (j == cursor) {
                rgb = room ? mix(ACCENT, TEXT_BRIGHT, reduced ? 1.0F : pulse) : REFUSED_RED;
                h = RULE_H + 1;
            }
            g.fill(f.x0() + bar.x(), f.y0() + bar.y() - (h - RULE_H), f.x0() + bar.right(), f.y0() + bar.y() + RULE_H, ink(rgb, fade));
        }

        for (int j = 0; j < page.size(); j++) {
            if (j == lifted) {
                continue;
            }
            Verse verse = verse(page.get(j));
            if (verse == null) {
                continue;
            }
            int virtual = lifted >= 0 && j > lifted ? j - 1 : j;
            int drawn = virtual + (part >= 0 && virtual >= part ? 1 : 0);
            slotShift[j] = approach(slotShift[j], (drawn - j) * stride, dt, halfLife(PART_HALF_LIFE));
            Rect sym = slotSymbol(slot(f.width(), f.visibleRows(), j));
            float deal = motion(easeOut((now - slotsDealtAt - j * SLOT_STAGGER) / DEAL_TICKS));
            float land = motion(ease((now - landedAt[j]) / LAND_TICKS));
            float scale = 1.0F + (GHOST_SCALE - 1.0F) * (1.0F - land);
            float cx = f.x0() + sym.x() + ICON / 2.0F + slotShift[j];
            float cy = f.y0() + sym.y() + ICON / 2.0F + (1.0F - deal) * DEAL_RISE;
            drawSymbol(g, VerseSymbols.of(verse), cx - ICON * scale / 2.0F, cy - ICON * scale / 2.0F, scale,
                    typeColor(verse.type()), fade * deal);
        }
    }

    /** The breath and its steps at the row's left end, Save and Clear at its right, each lit as far as it can be pressed. */
    private void paintControls(GuiGraphics g, Frame f, float fade, double lx, double ly) {
        Controls c = controlRects(f);
        List<ResourceLocation> page = pages.get(slot);
        int x0 = f.x0();
        int y0 = f.y0();
        g.drawString(font, breathLabel(), x0 + c.breathLabel().x(), y0 + c.breathLabel().y(), ink(TEXT_FAINT, fade), true);
        boolean canMinus = breaths[slot] > ReciteCaps.MIN_BREATH;
        boolean canPlus = breaths[slot] < ReciteCaps.MAX_BREATH;
        g.drawString(font, "-", x0 + c.minus().x(), y0 + c.minus().y(),
                ink(canMinus ? (hit(c.minus(), lx, ly) ? TEXT_BRIGHT : TEXT_MUTED) : TEXT_FAINT, fade), true);
        g.drawString(font, String.valueOf(breaths[slot]), x0 + c.value().x(), y0 + c.value().y(), ink(TEXT_BRIGHT, fade), true);
        g.drawString(font, "+", x0 + c.plus().x(), y0 + c.plus().y(),
                ink(canPlus ? (hit(c.plus(), lx, ly) ? TEXT_BRIGHT : TEXT_MUTED) : TEXT_FAINT, fade), true);

        boolean canSave = dirty[slot] && problem == null && pending[slot] < 0;
        boolean overSave = canSave && hit(c.save(), lx, ly);
        int saveRgb = pending[slot] >= 0 ? TEXT_MUTED : canSave ? (overSave ? TEXT_BRIGHT : TEXT_NEAR) : TEXT_FAINT;
        g.drawString(font, saveLabel(), x0 + c.save().x(), y0 + c.save().y(), ink(saveRgb, fade), true);
        if (overSave) {
            underline(g, f, c.save(), fade);
        }
        boolean canClear = !page.isEmpty();
        boolean overClear = canClear && hit(c.clear(), lx, ly);
        g.drawString(font, clearLabel(), x0 + c.clear().x(), y0 + c.clear().y(),
                ink(canClear ? (overClear ? TEXT_BRIGHT : TEXT_MUTED) : TEXT_FAINT, fade), true);
        if (overClear) {
            underline(g, f, c.clear(), fade);
        }
    }

    private void underline(GuiGraphics g, Frame f, Rect word, float fade) {
        int y = f.y0() + word.bottom() + 2;
        g.fill(f.x0() + word.x(), y, f.x0() + word.right(), y + RULE_H, ink(ACCENT, fade));
    }

    private void paintReading(GuiGraphics g, Frame f, float fade) {
        Rect r = reading(f.width(), f.visibleRows());
        int cx = f.x0() + f.width() / 2;
        List<Line> lines = readingLines();
        for (int i = 0; i < lines.size() && i < GrimoireLayout.READING_LINES; i++) {
            Line line = lines.get(i);
            g.drawCenteredString(font, font.plainSubstrByWidth(line.text(), f.width()), cx, f.y0() + r.y() + i * LINE_H,
                    ink(line.rgb(), fade));
        }
    }

    /** What the reading says about the row: refused, empty, or what a press would cast. */
    private List<Line> readingLines() {
        List<Line> lines = new ArrayList<>();
        List<ResourceLocation> page = pages.get(slot);
        if (page.isEmpty()) {
            lines.add(new Line(Component.translatable("screen.magical.grimoire.empty_reading").getString(), TEXT_MUTED));
            return lines;
        }
        if (problem != null) {
            String where = problem.id() == null ? String.valueOf(problem.index() + 1) : name(problem.id()).getString();
            String what = problem.problem().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            lines.add(new Line(Component.translatable("screen.magical.grimoire.refused", what, where).getString(), REFUSED_RED));
            return lines;
        }
        if (plan == null) {
            return lines;
        }
        List<ProjectilePlan> bodies = plan.bodies();
        String what;
        if (bodies.isEmpty()) {
            what = Component.translatable("screen.magical.grimoire.no_bodies").getString();
        } else {
            Map<ResourceLocation, Integer> counted = new LinkedHashMap<>();
            for (ProjectilePlan body : bodies) {
                counted.merge(body.verse(), 1, Integer::sum);
            }
            StringBuilder names = new StringBuilder();
            for (Map.Entry<ResourceLocation, Integer> entry : counted.entrySet()) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(name(entry.getKey()).getString());
                if (entry.getValue() > 1) {
                    names.append(" x").append(entry.getValue());
                }
            }
            String count = bodies.size() == 1
                    ? Component.translatable("screen.magical.grimoire.one_body").getString()
                    : Component.translatable("screen.magical.grimoire.bodies", bodies.size()).getString();
            what = count + " (" + names + ")";
        }
        int mana = plan.manaSpent();
        String bill = mana < 0
                ? Component.translatable("screen.magical.grimoire.refund", -mana).getString()
                : Component.translatable("screen.magical.grimoire.mana", mana).getString();
        String beat = Component.translatable("screen.magical.grimoire.cooldown", plan.cooldownTicks()).getString();
        lines.add(new Line(Component.translatable("screen.magical.grimoire.would_cast", what).getString()
                + " · " + bill + " · " + beat, mana < 0 ? REFUND_GREEN : TEXT_NEAR));
        Landing landing = Landing.of(plan.root());
        if (!landing.isNothing()) {
            lines.add(new Line(LandingText.describe(landing).getString(), TEXT_BRIGHT));
        }
        if (plan.frayed()) {
            lines.add(new Line(Component.translatable("screen.magical.grimoire.frayed").getString(), FRAYED_ORANGE));
        }
        return lines;
    }

    /** A struck symbol falls a little way and fades as it goes. */
    private void paintFallen(GuiGraphics g, float now, float fade) {
        if (fallen == null) {
            return;
        }
        float t = motion((now - fallAt) / FALL_TICKS);
        if (t >= 1.0F) {
            fallen = null;
            return;
        }
        drawSymbol(g, VerseSymbols.of(fallen), fallX, fallY + FALL_DROP * easeOut(t), 1.0F, typeColor(fallen.type()),
                fade * (1.0F - t));
    }

    /** The verse in hand, grown, riding beside the cursor with its name. */
    private void paintGhost(GuiGraphics g, int mouseX, int mouseY) {
        Verse verse = verse(dragId);
        if (verse == null) {
            return;
        }
        float size = ICON * GHOST_SCALE;
        drawSymbol(g, VerseSymbols.of(verse), mouseX + GHOST_DX, mouseY - size / 2.0F, GHOST_SCALE, typeColor(verse.type()), 1.0F);
        g.drawString(font, name(verse.id()).getString(), mouseX + GHOST_DX + Math.round(size) + 4, mouseY - 4, TEXT_BRIGHT, true);
    }

    /** A symbol at (x, y), grown by {@code scale}: the glyph in its colour, the marks along the top, the badge in white on a dark backing. */
    private static void drawSymbol(GuiGraphics g, VerseSymbols.Symbol symbol, float x, float y, float scale, int rgb, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int argb = ink(rgb, alpha);
        int[] rows = symbol.glyph().rows();
        for (int row = 0; row < VerseSymbols.SIZE; row++) {
            drawRuns(g, rows[row], VerseSymbols.SIZE, 0, row, argb);
        }
        int white = ink(TEXT_BRIGHT, alpha);
        if (symbol.hasMarks()) {
            drawRuns(g, VerseSymbols.marksRow(symbol.marks()), VerseSymbols.SIZE, 0, 0, white);
        }
        if (symbol.hasBadge()) {
            int corner = VerseSymbols.SIZE - VerseSymbols.BADGE_SIZE;
            g.fill(corner - 1, corner - 1, VerseSymbols.SIZE, VerseSymbols.SIZE, ink(BADGE_BACK, alpha));
            int[] badge = symbol.badge().rows();
            for (int row = 0; row < VerseSymbols.BADGE_SIZE; row++) {
                drawRuns(g, badge[row], VerseSymbols.BADGE_SIZE, corner, corner + row, white);
            }
        }
        g.pose().popPose();
    }

    /** One fill per run of lit pixels in a row of {@code width} bits, the highest bit being the leftmost. */
    private static void drawRuns(GuiGraphics g, int bits, int width, int x, int y, int argb) {
        int col = 0;
        while (col < width) {
            if ((bits >> (width - 1 - col) & 1) == 0) {
                col++;
                continue;
            }
            int start = col;
            while (col < width && (bits >> (width - 1 - col) & 1) != 0) {
                col++;
            }
            g.fill(x + start, y, x + col, y + 1, argb);
        }
    }

    // ---- input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Frame f = frame();
        double lx = mouseX - f.x0();
        double ly = mouseY - f.y0();
        List<ResourceLocation> page = pages.get(slot);
        if (button == 1) {
            int index = slotAt(f.width(), f.visibleRows(), lx, ly);
            if (index >= 0 && index < page.size()) {
                strike(f, page, index);
            }
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        release();
        int tab = indexAt(tabRects(f, tabLabels(f)), lx, ly);
        if (tab >= 0) {
            if (tab != slot) {
                focusFrom = slot;
                focusAt = now();
                slot = tab;
                slotsDealtAt = now();
                Arrays.fill(slotShift, 0.0F);
                Arrays.fill(landedAt, LONG_AGO);
                reread();
            }
            return true;
        }
        int cat = indexAt(categoryRects(f), lx, ly);
        if (cat >= 0) {
            int picked = cat - 1;
            if (picked != category) {
                category = picked;
                gridScroll = 0;
                gridDealtAt = now();
                refreshShelf();
            }
            return true;
        }
        int icon = gridIndexAt(f.width(), f.columns(), f.visibleRows(), gridScroll, shelf.size(), lx, ly);
        if (icon >= 0) {
            arm(DragSource.GRID, shelf.get(icon).id(), icon, lx, ly);
            return true;
        }
        int index = slotAt(f.width(), f.visibleRows(), lx, ly);
        if (index >= 0 && index < page.size()) {
            arm(DragSource.ROW, page.get(index), index, lx, ly);
            return true;
        }
        Controls c = controlRects(f);
        if (hit(c.minus(), lx, ly)) {
            setBreath(breaths[slot] - 1);
            return true;
        }
        if (hit(c.plus(), lx, ly)) {
            setBreath(breaths[slot] + 1);
            return true;
        }
        if (hit(c.save(), lx, ly)) {
            saveCurrent();
            return true;
        }
        if (hit(c.clear(), lx, ly)) {
            if (!page.isEmpty()) {
                page.clear();
                Arrays.fill(slotShift, 0.0F);
                touched();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (button != 0 || dragSource == null) {
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        if (!dragging) {
            Frame f = frame();
            double lx = mouseX - f.x0();
            double ly = mouseY - f.y0();
            dragging = Math.hypot(lx - pressX, ly - pressY) >= DRAG_THRESHOLD;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0 || dragSource == null) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        Frame f = frame();
        double lx = mouseX - f.x0();
        double ly = mouseY - f.y0();
        List<ResourceLocation> page = pages.get(slot);
        if (dragSource == DragSource.GRID) {
            dropFromGrid(f, page, lx, ly);
        } else if (dragFrom < page.size() && page.get(dragFrom).equals(dragId)) {
            // A sync can rewrite the row under a drag; a symbol that is no longer where it was is left alone.
            dropFromRow(f, page, lx, ly);
        }
        release();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Frame f = frame();
        double lx = mouseX - f.x0();
        double ly = mouseY - f.y0();
        int step = (int) Math.signum(scrollY);
        if (hit(gridArea(f.width(), f.columns(), f.visibleRows()), lx, ly)) {
            gridScroll = clampGridScroll(gridScroll - step, f.rows(), f.visibleRows());
            return true;
        }
        if (overRow(f.width(), f.visibleRows(), lx, ly)) {
            setBreath(breaths[slot] + step);
            return true;
        }
        return true;
    }

    /** Takes a press in hand: what it holds, where from, and where it began. */
    private void arm(DragSource source, ResourceLocation id, int from, double lx, double ly) {
        dragSource = source;
        dragId = id;
        dragFrom = from;
        pressX = lx;
        pressY = ly;
        dragging = false;
    }

    private void release() {
        dragSource = null;
        dragId = null;
        dragging = false;
    }

    /** A grid verse lands where the cursor is on the row, or at the end for a click; a full row takes nothing. */
    private void dropFromGrid(Frame f, List<ResourceLocation> page, double lx, double ly) {
        if (page.size() >= SLOT_COUNT) {
            return;
        }
        if (!dragging) {
            insertAt(f, page, page.size(), dragId);
            return;
        }
        if (!overRow(f.width(), f.visibleRows(), lx, ly)) {
            return;
        }
        insertAt(f, page, slotInsertionAt(f.width(), lx, page.size()), dragId);
    }

    /** A written verse dragged along the row moves to where the cursor is; dragged off it, it falls away; a click leaves it. */
    private void dropFromRow(Frame f, List<ResourceLocation> page, double lx, double ly) {
        if (!dragging) {
            return;
        }
        if (!overRow(f.width(), f.visibleRows(), lx, ly)) {
            strike(f, page, dragFrom);
            return;
        }
        int to = slotInsertionAt(f.width(), lx, page.size() - 1);
        if (to == dragFrom) {
            landedAt[dragFrom] = now();
            return;
        }
        float[] drawn = drawnX(f, page, dragFrom);
        float[] landed = landedAtWithout(dragFrom, page.size());
        ResourceLocation id = page.remove(dragFrom);
        page.add(to, id);
        settle(f, page, drawn, landed, to);
        touched();
    }

    /** Writes a verse at {@code at}: the ones after it are already drawn a slot right, so they keep their places and it lands between them. */
    private void insertAt(Frame f, List<ResourceLocation> page, int at, ResourceLocation id) {
        float[] drawn = drawnX(f, page, -1);
        float[] landed = landedAtWithout(-1, page.size());
        page.add(at, id);
        settle(f, page, drawn, landed, at);
        touched();
    }

    /** Strikes the verse at {@code at}: it falls away where it was drawn and the row closes over its place. */
    private void strike(Frame f, List<ResourceLocation> page, int at) {
        Verse verse = verse(page.get(at));
        Rect sym = slotSymbol(slot(f.width(), f.visibleRows(), at));
        fallen = verse;
        fallX = f.x0() + sym.x() + slotShift[at];
        fallY = f.y0() + sym.y();
        fallAt = now();
        float[] drawn = drawnX(f, page, at);
        float[] landed = landedAtWithout(at, page.size());
        page.remove(at);
        settle(f, page, drawn, landed, -1);
        touched();
    }

    /** Where each written symbol is drawn now, block-local, skipping {@code except}. */
    private float[] drawnX(Frame f, List<ResourceLocation> page, int except) {
        float[] drawn = new float[page.size()];
        int k = 0;
        for (int j = 0; j < page.size(); j++) {
            if (j == except) {
                continue;
            }
            drawn[k++] = slotSymbol(slot(f.width(), f.visibleRows(), j)).x() + slotShift[j];
        }
        return Arrays.copyOf(drawn, k);
    }

    private float[] landedAtWithout(int except, int size) {
        float[] kept = new float[size];
        int k = 0;
        for (int j = 0; j < size; j++) {
            if (j != except) {
                kept[k++] = landedAt[j];
            }
        }
        return Arrays.copyOf(kept, k);
    }

    /**
     * After the row changed, gives every symbol the drawn place it had so it glides from there to
     * its new slot rather than jumping; the one at {@code landing} is new and lands where it is.
     */
    private void settle(Frame f, List<ResourceLocation> page, float[] drawn, float[] landed, int landing) {
        float now = now();
        int old = 0;
        for (int k = 0; k < page.size(); k++) {
            if (k == landing) {
                slotShift[k] = 0.0F;
                landedAt[k] = now;
                continue;
            }
            float base = slotSymbol(slot(f.width(), f.visibleRows(), k)).x();
            slotShift[k] = old < drawn.length ? drawn[old] - base : 0.0F;
            landedAt[k] = old < landed.length ? landed[old] : LONG_AGO;
            old++;
        }
        for (int k = page.size(); k < SLOT_COUNT; k++) {
            slotShift[k] = 0.0F;
            landedAt[k] = LONG_AGO;
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    /** The clock every hold overlay uses: whole ticks plus the fraction of the one being drawn. */
    private float now() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0.0F;
        }
        return mc.player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** An eased value, or its final frame when motion is reduced. */
    private static float motion(float eased) {
        return MagicalClientConfig.current().reducedMotion() ? 1.0F : eased;
    }

    private static float halfLife(float ticks) {
        return MagicalClientConfig.current().reducedMotion() ? 0.0F : ticks;
    }

    private static VerseSymbols.Symbol[] categorySymbols() {
        VerseSymbols.Symbol[] symbols = new VerseSymbols.Symbol[CATEGORY_COUNT];
        symbols[0] = new VerseSymbols.Symbol(VerseSymbols.all(), null, 0);
        for (int c = 1; c < CATEGORY_COUNT; c++) {
            symbols[c] = new VerseSymbols.Symbol(VerseSymbols.forType(TYPES[c - 1]), null, 0);
        }
        return symbols;
    }

    /** A colour with an alpha, never below the few levels the font treats as opaque. */
    private static int ink(int rgb, float alpha) {
        int a = Mth.clamp(Math.round(alpha * 255.0F), 4, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    /** Mixes {@code to} into {@code from} by {@code amount}, both 24-bit RGB. */
    private static int mix(int from, int to, float amount) {
        float t = Mth.clamp(amount, 0.0F, 1.0F);
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (from >> shift) & 0xFF;
            int b = (to >> shift) & 0xFF;
            out |= Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }

    private static Verse verse(ResourceLocation id) {
        return id != null && VerseContent.CATALOGUE.contains(id) ? VerseContent.CATALOGUE.get(id) : null;
    }

    private static Component name(ResourceLocation id) {
        return Component.translatable("verse.magical." + id.getPath());
    }

    /** The line every verse carries in the lang file, the one the codex and the commands read too. */
    private static Component description(ResourceLocation id) {
        return Component.translatable("verse.magical." + id.getPath() + ".desc");
    }

    private static Component typeAndMana(Verse verse) {
        Component type = typeName(verse.type());
        return verse.unlimited()
                ? Component.translatable("screen.magical.grimoire.uses_unlimited", type, verse.mana())
                : Component.translatable("screen.magical.grimoire.uses", type, verse.mana(), verse.maxUses());
    }

    private static Component typeName(VerseType type) {
        return Component.translatable("screen.magical.grimoire.type." + type.name().toLowerCase(Locale.ROOT));
    }

    /** A colour per verse type, so the grid reads as a deck before a name is read. */
    private static int typeColor(VerseType type) {
        return switch (type) {
            case PROJECTILE -> 0x5FD4FF;
            case STATIC -> 0x7FE0A8;
            case MODIFIER -> 0xF7D774;
            case MULTICAST -> 0xB48AFF;
            case MATERIAL -> 0xC8A878;
            case CONTROL -> 0xFF9E6B;
            case UTILITY -> 0x9FE7E7;
            case PASSIVE -> 0xD0D0D0;
        };
    }

    private static PlayerMagicState state() {
        return ClientMagicState.get();
    }
}
