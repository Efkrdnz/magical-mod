package com.efkrdnz.magical.client.screen.grimoire;

import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.BREATH_LABEL_Y;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.LINES;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.LINE_STRIDE;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.MARGIN_X;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.PAGE_LABEL_Y;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.PAGE_W;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.PAGE_X;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.PAGE_Y;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.PANEL_H;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.PANEL_W;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.SHELF_LABEL_Y;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.SHELF_ROWS;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.SHELF_STRIDE;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.SHELF_W;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.SHELF_X;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.SHELF_Y;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.TAB_COUNT;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.TOOLTIP_W;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.back;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.breathMinus;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.breathPlus;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.breathValue;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.clampScroll;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.clear;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.hit;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.line;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.lineAt;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.lineStrike;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.pageHint;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.reading;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.save;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.shelfHint;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.shelfRow;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.shelfRowAt;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.shelfScrollbar;
import static com.efkrdnz.magical.client.screen.grimoire.GrimoireLayout.tabAt;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.client.screen.ScreenChrome;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.AuthorityDefinition;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.incantation.Grimoire;
import com.efkrdnz.magical.magic.incantation.Incantation;
import com.efkrdnz.magical.magic.incantation.IncantationService;
import com.efkrdnz.magical.magic.incantation.IncantationValidator;
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

/**
 * The Grimoire: where the four incantations are written by hand.
 *
 * <p>A plain {@link Screen} with no menu behind it, like the creator. It reads the synced player
 * state (the Grimoire rides in it) and the one thing it changes goes over
 * {@code SetIncantationPayload}, which the server validates again before a byte is written. Each
 * tab is one incantation slot; the shelf on the left is every verse the wielder knows, the page in
 * the middle is the lines written so far, and the margin carries the breath, the reading and the
 * buttons. The reading is the real evaluator: the pure {@code Reciter} run on a copy of the page
 * against the assumed world, so the screen says on every click exactly what the server would cast.
 *
 * <p>Edits live on the screen per slot until Save sends them; the saved copy is reloaded from the
 * state only for a slot with nothing pending, so a mana tick arriving mid-edit clobbers nothing.
 */
public final class GrimoireScreen extends Screen implements HudDebug.Captured {
    private static final int SLOTS = Grimoire.SLOTS;
    private static final int PENDING_TIMEOUT_TICKS = 60;
    private static final int ACCENT = MagicalGuiStyle.ACCENT_ARCANE;
    private static final int SAVE_BASE = 0xFF6A3F84;
    private static final int DISABLED_BASE = 0xFF27354A;
    private static final int BLOCKED_BASE = 0xFF4A2730;
    private static final int CLEAR_BASE = 0xFF2A1A20;
    private static final int STEP_BASE = 0xFF27354A;
    private static final int REFUSED_RED = 0xFF6B7B;
    private static final int FRAYED_ORANGE = 0xFFB86C;
    private static final int REFUND_GREEN = 0x8FE3A0;
    private static final int LINE_TEXT_DY = 1;
    private static final int LINE_TEXT_DX = 3;
    private static final int SWATCH = 6;
    private static final int SHELF_TEXT_DX = 16;
    private static final int LINE_PX = 11;

    private int leftPos;
    private int topPos;
    private int slot;
    private List<Verse> shelf = List.of();
    private int shelfScroll;
    private final List<List<ResourceLocation>> pages = new ArrayList<>(SLOTS);
    private final int[] breaths = new int[SLOTS];
    private final boolean[] dirty = new boolean[SLOTS];
    /** Ticks since Save sent a slot, -1 when nothing is in flight. */
    private final int[] pending = new int[SLOTS];
    private int seenVersion = Integer.MIN_VALUE;
    private RecitePlan reading;
    private IncantationValidator.Finding problem;

    private record Line(Component text, int color) {}

    public GrimoireScreen() {
        super(Component.translatable("screen.magical.grimoire"));
        for (int s = 0; s < SLOTS; s++) {
            pages.add(new ArrayList<>());
            breaths[s] = ReciteCaps.MIN_BREATH;
            pending[s] = -1;
        }
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
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;
        seenVersion = ClientMagicState.version();
        refreshShelf();
        for (int s = 0; s < SLOTS; s++) {
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
        for (int s = 0; s < SLOTS; s++) {
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

    private void refreshShelf() {
        List<Verse> verses = new ArrayList<>();
        for (ResourceLocation id : state().grimoire().known()) {
            Verse verse = verse(id);
            if (verse != null) {
                verses.add(verse);
            }
        }
        verses.sort(Comparator.comparingInt((Verse verse) -> verse.type().ordinal()).thenComparing(Verse::path));
        shelf = verses;
        shelfScroll = clampScroll(shelfScroll, shelf.size(), SHELF_ROWS);
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

    /** Whether the state now holds exactly what the screen sent for a slot: the write landed. */
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
     * Reads the current page the way a press would: the validator first (the same one the server
     * runs), then the Reciter on a copy against the assumed world, so nothing here spends a use,
     * a heart or the Grimoire's toggle.
     */
    private void reread() {
        PlayerMagicState state = state();
        List<ResourceLocation> page = pages.get(slot);
        List<IncantationValidator.Finding> findings =
                IncantationValidator.problems(page, breaths[slot], state.grimoire().known(), VerseContent.CATALOGUE);
        problem = findings.isEmpty() ? null : findings.get(0);
        reading = null;
        if (problem != null || page.isEmpty()) {
            return;
        }
        Incantation copy = new Incantation();
        if (!copy.write(page, breaths[slot], VerseContent.CATALOGUE)) {
            return;
        }
        MagicSkillDefinition skill = IncantationService.skillFor(slot);
        double costScale = skill.resolve(state.tuningFor(skill.id())).costScale();
        reading = Reciter.recite(ReciteSession.of(copy, VerseContent.CATALOGUE), breaths[slot], state.mana(), costScale,
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
    }

    // ---- drawing --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        PlayerMagicState state = state();
        int x0 = leftPos;
        int y0 = topPos;
        double lx = mouseX - x0;
        double ly = mouseY - y0;

        MagicalGuiStyle.screenBackground(g, x0, y0, x0 + PANEL_W, y0 + PANEL_H);
        ScreenChrome.paintHeader(g, font, x0, y0, ACCENT, manaFraction(state), Component.translatable("screen.magical.back"));
        ScreenChrome.paintTabs(g, x0, y0, TAB_COUNT, slot);
        ScreenChrome.paintBody(g, x0, y0, ACCENT);
        paintShelf(g, x0, y0, lx, ly);
        paintPage(g, x0, y0, lx, ly);
        paintMargin(g, x0, y0);

        ScreenChrome.textHeader(g, font, x0, y0, getTitle(), authorityName(state), ACCENT, manaLabel(state));
        ScreenChrome.textTabs(g, font, x0, y0, tabLabels(), slot);
        textShelf(g, x0, y0);
        textPage(g, x0, y0, lx, ly);
        textMargin(g, x0, y0);

        List<Component> tooltip = tooltipAt(lx, ly);
        if (!tooltip.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : tooltip) {
                lines.addAll(font.split(line, TOOLTIP_W));
            }
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void paintShelf(GuiGraphics g, int x0, int y0, double lx, double ly) {
        MagicalGuiStyle.inset(g, x0 + SHELF_X - 2, y0 + SHELF_Y - 2, x0 + SHELF_X + SHELF_W + 2, y0 + SHELF_Y + SHELF_ROWS * SHELF_STRIDE);
        int visible = Math.min(SHELF_ROWS, shelf.size() - shelfScroll);
        int hovered = shelfRowAt(lx, ly);
        for (int row = 0; row < visible; row++) {
            Verse verse = shelf.get(shelfScroll + row);
            Rect rect = shelfRow(row);
            int tint = typeColor(verse.type());
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), row == hovered, 0xFF000000 | tint);
            int sx = x0 + rect.x() + 6;
            int sy = y0 + rect.y() + (rect.h() - SWATCH) / 2;
            g.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | tint);
        }
        Rect bar = shelfScrollbar();
        MagicalGuiStyle.scrollbar(g, x0 + bar.x() + 1, y0 + bar.y(), bar.h(), shelf.size(), SHELF_ROWS, shelfScroll);
    }

    private void paintPage(GuiGraphics g, int x0, int y0, double lx, double ly) {
        MagicalGuiStyle.inset(g, x0 + PAGE_X - 2, y0 + PAGE_Y - 2, x0 + PAGE_X + PAGE_W + 2, y0 + PAGE_Y + LINES * LINE_STRIDE);
        List<ResourceLocation> page = pages.get(slot);
        int hovered = lineAt(lx, ly);
        for (int i = 0; i < page.size(); i++) {
            Rect rect = line(i);
            Verse verse = verse(page.get(i));
            int tint = verse == null ? MagicalGuiStyle.TEXT_MUTED : typeColor(verse.type());
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), i == hovered, 0xFF000000 | tint);
        }
    }

    private void paintMargin(GuiGraphics g, int x0, int y0) {
        Rect minus = breathMinus();
        Rect plus = breathPlus();
        MagicalGuiStyle.button(g, font, x0 + minus.x(), y0 + minus.y(), minus.w(), minus.h(), STEP_BASE, Component.literal("-"));
        MagicalGuiStyle.button(g, font, x0 + plus.x(), y0 + plus.y(), plus.w(), plus.h(), STEP_BASE, Component.literal("+"));
        Rect box = reading();
        MagicalGuiStyle.inset(g, x0 + box.x(), y0 + box.y(), x0 + box.right(), y0 + box.bottom());
        Rect saveButton = save();
        MagicalGuiStyle.button(g, font, x0 + saveButton.x(), y0 + saveButton.y(), saveButton.w(), saveButton.h(), saveBase(), saveLabel());
        Rect clearButton = clear();
        MagicalGuiStyle.button(g, font, x0 + clearButton.x(), y0 + clearButton.y(), clearButton.w(), clearButton.h(), CLEAR_BASE,
                Component.translatable("screen.magical.grimoire.clear"));
    }

    private int saveBase() {
        if (pending[slot] >= 0 || !dirty[slot]) {
            return DISABLED_BASE;
        }
        return problem == null ? SAVE_BASE : BLOCKED_BASE;
    }

    private Component saveLabel() {
        if (pending[slot] >= 0) {
            return Component.translatable("screen.magical.grimoire.saving");
        }
        return Component.translatable(dirty[slot] ? "screen.magical.grimoire.save" : "screen.magical.grimoire.saved");
    }

    private void textShelf(GuiGraphics g, int x0, int y0) {
        MagicalGuiStyle.sectionLabel(g, font, x0 + SHELF_X, y0 + SHELF_LABEL_Y,
                Component.translatable("screen.magical.grimoire.shelf"), MagicalGuiStyle.TEXT_PRIMARY);
        if (shelf.isEmpty()) {
            drawWrapped(g, Component.translatable("screen.magical.grimoire.no_verses"),
                    x0 + SHELF_X + 4, y0 + SHELF_Y + 8, SHELF_W - 8, MagicalGuiStyle.TEXT_MUTED, 5);
        }
        int visible = Math.min(SHELF_ROWS, shelf.size() - shelfScroll);
        for (int row = 0; row < visible; row++) {
            Verse verse = shelf.get(shelfScroll + row);
            Rect rect = shelfRow(row);
            int textX = x0 + rect.x() + SHELF_TEXT_DX;
            int nameW = rect.w() - SHELF_TEXT_DX - 4;
            g.drawString(font, font.plainSubstrByWidth(name(verse.id()).getString(), nameW),
                    textX, y0 + rect.y() + 2, MagicalGuiStyle.TEXT_PRIMARY, false);
            g.drawString(font, font.plainSubstrByWidth(shelfLine(verse).getString(), nameW),
                    textX, y0 + rect.y() + 11, MagicalGuiStyle.TEXT_MUTED, false);
        }
        Rect hint = shelfHint();
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.magical.grimoire.shelf_hint").getString(), hint.w()),
                x0 + hint.x(), y0 + hint.y(), MagicalGuiStyle.TEXT_MUTED, false);
    }

    private void textPage(GuiGraphics g, int x0, int y0, double lx, double ly) {
        List<ResourceLocation> page = pages.get(slot);
        MagicalGuiStyle.sectionLabel(g, font, x0 + PAGE_X, y0 + PAGE_LABEL_Y,
                Component.translatable("screen.magical.grimoire.page", page.size(), LINES), MagicalGuiStyle.TEXT_PRIMARY);
        if (page.isEmpty()) {
            drawWrapped(g, Component.translatable("screen.magical.grimoire.empty_page"),
                    x0 + PAGE_X + 4, y0 + PAGE_Y + 6, PAGE_W - 8, MagicalGuiStyle.TEXT_MUTED, 4);
        }
        int hovered = lineAt(lx, ly);
        for (int i = 0; i < page.size(); i++) {
            Rect rect = line(i);
            Rect strike = lineStrike(i);
            String text = (i + 1) + ". " + name(page.get(i)).getString();
            g.drawString(font, font.plainSubstrByWidth(text, strike.x() - rect.x() - LINE_TEXT_DX - 3),
                    x0 + rect.x() + LINE_TEXT_DX, y0 + rect.y() + LINE_TEXT_DY, MagicalGuiStyle.TEXT_PRIMARY, false);
            int strikeColor = i == hovered ? MagicalGuiStyle.ACCENT_BLOOD & 0xFFFFFF : MagicalGuiStyle.TEXT_MUTED;
            g.drawString(font, "x", x0 + strike.x() + 3, y0 + strike.y() + LINE_TEXT_DY, strikeColor, false);
        }
        Rect hint = pageHint();
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.magical.grimoire.page_hint").getString(), hint.w()),
                x0 + hint.x(), y0 + hint.y(), MagicalGuiStyle.TEXT_MUTED, false);
    }

    private void textMargin(GuiGraphics g, int x0, int y0) {
        MagicalGuiStyle.sectionLabel(g, font, x0 + MARGIN_X, y0 + BREATH_LABEL_Y,
                Component.translatable("screen.magical.grimoire.breath"), MagicalGuiStyle.TEXT_PRIMARY);
        Rect value = breathValue();
        g.drawCenteredString(font, String.valueOf(breaths[slot]), x0 + value.x() + value.w() / 2, y0 + value.y() + 3, MagicalGuiStyle.TEXT_PRIMARY);
        Rect box = reading();
        int x = x0 + box.x() + 4;
        int y = y0 + box.y() + 4;
        int w = box.w() - 8;
        int maxLines = (box.h() - 8) / LINE_PX;
        int shown = 0;
        for (Line line : readingLines()) {
            if (shown >= maxLines) {
                break;
            }
            shown += drawWrapped(g, line.text(), x, y + shown * LINE_PX, w, line.color(), maxLines - shown) / LINE_PX;
        }
    }

    /** What the margin says about the page: refused, empty, or the reading of a press. */
    private List<Line> readingLines() {
        List<Line> lines = new ArrayList<>();
        List<ResourceLocation> page = pages.get(slot);
        if (page.isEmpty()) {
            lines.add(new Line(Component.translatable("screen.magical.grimoire.empty_reading"), MagicalGuiStyle.TEXT_MUTED));
            return lines;
        }
        if (problem != null) {
            String where = problem.id() == null ? String.valueOf(problem.index() + 1) : name(problem.id()).getString();
            String what = problem.problem().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            lines.add(new Line(Component.translatable("screen.magical.grimoire.refused", what, where), REFUSED_RED));
            return lines;
        }
        if (reading == null) {
            return lines;
        }
        lines.add(new Line(Component.translatable("screen.magical.grimoire.reading"), MagicalGuiStyle.TEXT_MUTED));
        List<ProjectilePlan> bodies = reading.bodies();
        if (bodies.isEmpty()) {
            lines.add(new Line(Component.translatable("screen.magical.grimoire.no_bodies"), MagicalGuiStyle.TEXT_PRIMARY));
        } else {
            Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
            for (ProjectilePlan body : bodies) {
                counts.merge(body.verse(), 1, Integer::sum);
            }
            Component headline = bodies.size() == 1
                    ? Component.translatable("screen.magical.grimoire.one_body")
                    : Component.translatable("screen.magical.grimoire.bodies", bodies.size());
            lines.add(new Line(headline, MagicalGuiStyle.TEXT_PRIMARY));
            for (Map.Entry<ResourceLocation, Integer> entry : counts.entrySet()) {
                String count = entry.getValue() > 1 ? " x" + entry.getValue() : "";
                lines.add(new Line(Component.literal("  " + name(entry.getKey()).getString() + count), MagicalGuiStyle.TEXT_PRIMARY));
            }
        }
        int mana = reading.manaSpent();
        lines.add(mana < 0
                ? new Line(Component.translatable("screen.magical.grimoire.refund", -mana), REFUND_GREEN)
                : new Line(Component.translatable("screen.magical.grimoire.mana", mana), MagicalGuiStyle.TEXT_PRIMARY));
        lines.add(new Line(Component.translatable("screen.magical.grimoire.cooldown", reading.cooldownTicks()), MagicalGuiStyle.TEXT_PRIMARY));
        if (reading.frayed()) {
            lines.add(new Line(Component.translatable("screen.magical.grimoire.frayed"), FRAYED_ORANGE));
        }
        return lines;
    }

    /** Draws at most {@code maxLines} wrapped lines and returns the height used. */
    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int shown = Math.min(lines.size(), Math.max(1, maxLines));
        for (int i = 0; i < shown; i++) {
            g.drawString(font, lines.get(i), x, y + i * LINE_PX, color, false);
        }
        return shown * LINE_PX;
    }

    // ---- tooltips ---------------------------------------------------------------------------------

    private List<Component> tooltipAt(double lx, double ly) {
        int row = shelfRowAt(lx, ly);
        if (row >= 0 && shelfScroll + row < shelf.size()) {
            return verseTooltip(shelf.get(shelfScroll + row));
        }
        int index = lineAt(lx, ly);
        List<ResourceLocation> page = pages.get(slot);
        if (index >= 0 && index < page.size()) {
            Verse verse = verse(page.get(index));
            return verse == null ? List.of() : verseTooltip(verse);
        }
        if (hit(breathValue(), lx, ly) || hit(breathMinus(), lx, ly) || hit(breathPlus(), lx, ly)) {
            return List.of(Component.translatable("screen.magical.grimoire.breath_tooltip"));
        }
        return List.of();
    }

    private static List<Component> verseTooltip(Verse verse) {
        return List.of(
                name(verse.id()).copy().withColor(typeColor(verse.type())),
                typeAndMana(verse).copy().withColor(MagicalGuiStyle.TEXT_MUTED),
                Component.translatable("verse.magical." + verse.path() + ".desc"));
    }

    // ---- input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        List<ResourceLocation> page = pages.get(slot);
        if (button == 1) {
            int index = lineAt(lx, ly);
            if (index > 0 && index < page.size()) {
                page.add(index - 1, page.remove(index));
                touched();
            }
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (hit(back(), lx, ly)) {
            MagicalNetwork.sendOpenCodexRequest();
            return true;
        }
        int tabHit = tabAt(lx, ly);
        if (tabHit >= 0) {
            if (tabHit != slot) {
                slot = tabHit;
                reread();
            }
            return true;
        }
        int row = shelfRowAt(lx, ly);
        if (row >= 0 && shelfScroll + row < shelf.size()) {
            if (page.size() < LINES) {
                page.add(shelf.get(shelfScroll + row).id());
                touched();
            }
            return true;
        }
        int index = lineAt(lx, ly);
        if (index >= 0 && index < page.size()) {
            page.remove(index);
            touched();
            return true;
        }
        if (hit(breathMinus(), lx, ly)) {
            breaths[slot] = clampBreath(breaths[slot] - 1);
            touched();
            return true;
        }
        if (hit(breathPlus(), lx, ly)) {
            breaths[slot] = clampBreath(breaths[slot] + 1);
            touched();
            return true;
        }
        if (hit(save(), lx, ly)) {
            saveCurrent();
            return true;
        }
        if (hit(clear(), lx, ly)) {
            if (!page.isEmpty()) {
                page.clear();
                touched();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        Rect list = new Rect("shelf", SHELF_X, SHELF_Y, SHELF_W, SHELF_ROWS * SHELF_STRIDE);
        if (hit(list, lx, ly)) {
            shelfScroll = clampScroll(shelfScroll - (int) Math.signum(scrollY), shelf.size(), SHELF_ROWS);
        }
        return true;
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static Verse verse(ResourceLocation id) {
        return VerseContent.CATALOGUE.contains(id) ? VerseContent.CATALOGUE.get(id) : null;
    }

    private static Component name(ResourceLocation id) {
        return Component.translatable("verse.magical." + id.getPath());
    }

    /** The shelf row's second line: type and mana; the uses are in the tooltip, where there is room. */
    private static Component shelfLine(Verse verse) {
        return Component.translatable("screen.magical.grimoire.type_mana", typeName(verse.type()), verse.mana());
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

    /** A colour per verse type, so the shelf reads as a deck before a name is read. */
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

    private static List<Component> tabLabels() {
        List<Component> labels = new ArrayList<>(TAB_COUNT);
        for (int s = 0; s < TAB_COUNT; s++) {
            labels.add(Component.translatable(IncantationService.skillFor(s).nameKey()));
        }
        return labels;
    }

    private static Component authorityName(PlayerMagicState state) {
        AuthorityDefinition held = state.authorityId() == null ? null : AuthorityContent.get(state.authorityId());
        return Component.translatable(held == null ? AuthorityContent.AUTHORITY_OF_MANA.nameKey() : held.nameKey());
    }

    private static float manaFraction(PlayerMagicState state) {
        return state.maxMana() <= 0 ? 0.0F : Math.min(1.0F, state.mana() / (float) state.maxMana());
    }

    private static Component manaLabel(PlayerMagicState state) {
        return Component.translatable("screen.magical.grimoire.mana_pool", state.mana(), state.maxMana());
    }

    private static PlayerMagicState state() {
        return ClientMagicState.get();
    }
}
