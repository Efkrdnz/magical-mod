package com.efkrdnz.magical.client.screen.creator;

import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.INGREDIENTS_LABEL_Y;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.INGREDIENT_ROWS;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.LIST_W;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.LIST_X;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.LIST_Y;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.NOTE_H;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.PANEL_H;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.PANEL_W;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.RESULT_EMBLEM_HALF;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.RESULT_TEXT_H;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.REVEAL_TICKS;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.ROW_STRIDE;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.SLOT_EMBLEM_HALF;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.SLOT_TEXT_DX;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.TAB_COUNT;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.TAB_Y;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.TITLE_W;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.TOOLTIP_W;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.back;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.body;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.clampScroll;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.classChip;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.createButton;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.hit;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.ingredientEmblem;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.ingredientHint;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.ingredientRow;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.ingredientRowAt;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.ingredientScrollbar;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.note;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.plus;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.result;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.resultEmblem;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.resultText;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.slot;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.slotAt;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.slotClear;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.slotEmblem;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.tab;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.tabAt;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.title;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.xpBar;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.hud.HudPalette;
import com.efkrdnz.magical.client.hud.MagicalClientConfig;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicFusionService;
import com.efkrdnz.magical.magic.MagicFusionService.FormulaState;
import com.efkrdnz.magical.magic.MagicFusionService.FusionRecipe;
import com.efkrdnz.magical.magic.MagicFusionService.Status;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.OpenSpellCreatorPayload;
import com.efkrdnz.magical.registry.MagicalSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Spell Creator: where two owned skills become a third.
 *
 * <p>A plain {@link Screen} with no menu behind it. The state it reads is the synced player state
 * and the one thing it changes goes over {@code CreateSkillPayload}, so a container would buy
 * nothing but a band of button ids and a strip of inventory slots nobody uses. It closes whatever
 * container was open on the way in (which tells the server), and Back asks the server for the codex.
 *
 * <p>Every frame paints in three passes: chrome fills, then every emblem in one {@code drawSpecial}
 * through the {@link EmblemPainter}, then text. Fills before the flush land under the emblems and
 * text after lands over them; nothing here is drawn out of that order.
 */
public final class SpellCreatorScreen extends Screen implements HudDebug.Captured {
    private static final int PENDING_TIMEOUT_TICKS = 60;
    private static final int BACK_BASE = 0xFF27354A;
    private static final int DISABLED_BASE = 0xFF27354A;
    private static final int BLOCKED_BASE = 0xFF4A2730;
    private static final int CREATE_BASE = 0xFF6A3F84;
    private static final int CLEAR_BASE = 0xFF2A1A20;
    private static final int WARNING_ORANGE = 0xFFB86C;
    private static final float DIM_ALPHA = 0.55F;
    private static final float REVEAL_SWELL = 0.45F;
    private static final float CUE_VOLUME = 0.7F;

    private final EmblemPainter emblems = new EmblemPainter();
    private final FormulaListPanel formulas = new FormulaListPanel();

    private int leftPos;
    private int topPos;
    private int tab;
    private ResourceLocation first;
    private ResourceLocation second;
    private int ingredientScroll;
    private List<MagicSkillDefinition> ingredients = List.of();
    private int seenVersion = Integer.MIN_VALUE;
    /** The created skills the player owned at the last refresh; a newcomer is the reveal. */
    private Set<ResourceLocation> createdOwned = Set.of();
    private boolean primed;
    /** The output a Create click is waiting for, so a double click cannot send the pair twice. */
    private ResourceLocation pending;
    private int pendingTicks;
    private ResourceLocation revealed;
    private int revealTicks = -1;

    public SpellCreatorScreen(int tab, ResourceLocation first, ResourceLocation second) {
        super(Component.translatable("screen.magical.spell_creator"));
        this.tab = tab == OpenSpellCreatorPayload.TAB_FORMULAS ? OpenSpellCreatorPayload.TAB_FORMULAS : OpenSpellCreatorPayload.TAB_CREATE;
        this.first = first;
        this.second = second;
    }

    /** The one way in. Closes whatever container is open first, which also tells the server. */
    public static void open(int tab, ResourceLocation first, ResourceLocation second) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
            minecraft.player.closeContainer();
        }
        minecraft.setScreen(new SpellCreatorScreen(tab, first, second));
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;
        refresh();
    }

    /** The codex never paused either; a paused integrated server would never answer Create. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (ClientMagicState.version() != seenVersion) {
            refresh();
        }
        if (pending != null && ++pendingTicks > PENDING_TIMEOUT_TICKS) {
            pending = null;
        }
        if (revealTicks >= 0 && ++revealTicks > REVEAL_TICKS) {
            revealTicks = -1;
        }
    }

    private void refresh() {
        PlayerMagicState state = state();
        seenVersion = ClientMagicState.version();
        ingredients = MagicFusionService.eligibleInputs(state);
        ingredientScroll = clampScroll(ingredientScroll, ingredients.size(), INGREDIENT_ROWS);
        formulas.refresh(state);
        Set<ResourceLocation> owned = new HashSet<>();
        for (ResourceLocation id : MagicContent.CREATED_SKILLS) {
            if (state.hasUnlocked(id)) {
                owned.add(id);
            }
        }
        if (primed) {
            for (ResourceLocation id : owned) {
                if (!createdOwned.contains(id)) {
                    reveal(id);
                }
            }
        }
        createdOwned = owned;
        primed = true;
    }

    private void reveal(ResourceLocation id) {
        revealed = id;
        revealTicks = 0;
        pending = null;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(MagicalSounds.CREATION.get(), 1.0F, CUE_VOLUME));
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
        emblems.begin(g);
        paintHeader(g, state, x0, y0);
        paintTabs(g, x0, y0);
        Rect body = body();
        MagicalGuiStyle.panel(g, x0 + body.x(), y0 + body.y(), x0 + body.right(), y0 + body.bottom(), accent(state));
        if (tab == OpenSpellCreatorPayload.TAB_CREATE) {
            paintCreate(g, state, x0, y0, lx, ly, partial);
        } else {
            formulas.paint(g, emblems, x0, y0, lx, ly);
        }
        emblems.flush();

        textHeader(g, state, x0, y0);
        textTabs(g, x0, y0);
        if (tab == OpenSpellCreatorPayload.TAB_CREATE) {
            textCreate(g, state, x0, y0);
        } else {
            formulas.text(g, font, x0, y0, state);
        }

        List<Component> tooltip = tooltipAt(state, lx, ly);
        if (!tooltip.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : tooltip) {
                lines.addAll(font.split(line, TOOLTIP_W));
            }
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void paintHeader(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect chip = classChip();
        MagicalGuiStyle.inset(g, x0 + chip.x(), y0 + chip.y(), x0 + chip.right(), y0 + chip.bottom());
        g.fill(x0 + chip.x(), y0 + chip.y(), x0 + chip.x() + 2, y0 + chip.bottom(), accent(state));
        Rect xp = xpBar();
        MagicalGuiStyle.inset(g, x0 + xp.x(), y0 + xp.y(), x0 + xp.right(), y0 + xp.bottom());
        int fill = Math.round((xp.w() - 2) * xpFraction(state));
        if (fill > 0) {
            g.fillGradient(x0 + xp.x() + 1, y0 + xp.y() + 1, x0 + xp.x() + 1 + fill, y0 + xp.bottom() - 1,
                    MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0xAA), MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0x55));
        }
        Rect back = back();
        MagicalGuiStyle.button(g, font, x0 + back.x(), y0 + back.y(), back.w(), back.h(), BACK_BASE,
                Component.translatable("screen.magical.back"));
        g.fill(x0 + 10, y0 + TAB_Y - 4, x0 + PANEL_W - 10, y0 + TAB_Y - 3, MagicalGuiStyle.withAlpha(accent(state), 0x3A));
    }

    private void paintTabs(GuiGraphics g, int x0, int y0) {
        for (int index = 0; index < TAB_COUNT; index++) {
            Rect rect = tab(index);
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), index == tab, MagicalGuiStyle.ACCENT_ARCANE);
        }
    }

    private void paintCreate(GuiGraphics g, PlayerMagicState state, int x0, int y0, double lx, double ly, float partial) {
        MagicalGuiStyle.inset(g, x0 + LIST_X - 2, y0 + LIST_Y - 2, x0 + LIST_X + LIST_W + 2, y0 + LIST_Y + INGREDIENT_ROWS * ROW_STRIDE);
        int visible = Math.min(INGREDIENT_ROWS, ingredients.size() - ingredientScroll);
        int hovered = ingredientRowAt(lx, ly);
        for (int row = 0; row < visible; row++) {
            MagicSkillDefinition skill = ingredients.get(ingredientScroll + row);
            Rect rect = ingredientRow(row);
            boolean loaded = skill.id().equals(first) || skill.id().equals(second);
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), loaded || row == hovered, 0xFF000000 | nameTint(skill));
            Rect emblem = ingredientEmblem(row);
            emblems.add(skill.id(), centreX(x0, emblem), centreY(y0, emblem), SLOT_EMBLEM_HALF, 1.0F);
        }
        Rect bar = ingredientScrollbar();
        MagicalGuiStyle.scrollbar(g, x0 + bar.x() + 1, y0 + bar.y(), bar.h(), ingredients.size(), INGREDIENT_ROWS, ingredientScroll);

        paintSlot(g, state, x0, y0, 0, first, lx, ly);
        paintSlot(g, state, x0, y0, 1, second, lx, ly);
        paintResult(g, state, x0, y0, partial);
    }

    private void paintSlot(GuiGraphics g, PlayerMagicState state, int x0, int y0, int index, ResourceLocation id, double lx, double ly) {
        Rect card = slot(index);
        if (id == null) {
            MagicalGuiStyle.inset(g, x0 + card.x(), y0 + card.y(), x0 + card.right(), y0 + card.bottom());
            return;
        }
        MagicalGuiStyle.panel(g, x0 + card.x(), y0 + card.y(), x0 + card.right(), y0 + card.bottom(), accent(state));
        Rect clear = slotClear(index);
        g.fill(x0 + clear.x(), y0 + clear.y(), x0 + clear.right(), y0 + clear.bottom(),
                hit(clear, lx, ly) ? MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_BLOOD, 0xCC) : CLEAR_BASE);
        Rect emblem = slotEmblem(index);
        emblems.add(id, centreX(x0, emblem), centreY(y0, emblem), SLOT_EMBLEM_HALF, 1.0F, !state.hasUnlocked(id));
    }

    private void paintResult(GuiGraphics g, PlayerMagicState state, int x0, int y0, float partial) {
        Rect card = result();
        MagicalGuiStyle.inset(g, x0 + card.x(), y0 + card.y(), x0 + card.right(), y0 + card.bottom());
        FormulaState formula = formula(state);
        // After a consuming fusion the slots may be cleared; the card keeps showing what was made.
        ResourceLocation shown = formula != null ? formula.recipe().outputSkill() : revealed;
        float t = revealTicks < 0 ? 1.0F : Math.min(1.0F, (revealTicks + partial) / REVEAL_TICKS);
        if (revealTicks >= 0) {
            g.fill(x0 + card.x(), y0 + card.y(), x0 + card.right(), y0 + card.bottom(),
                    MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, Math.round(0x90 * (1.0F - t))));
            g.fill(x0 + card.x() - 1, y0 + card.y() - 1, x0 + card.right() + 1, y0 + card.y(),
                    MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, Math.round(0xFF * (1.0F - t))));
        }
        if (shown != null) {
            boolean lit = formula == null || formula.status() == Status.READY || formula.status() == Status.CREATED;
            float half = RESULT_EMBLEM_HALF;
            if (revealTicks >= 0 && !MagicalClientConfig.current().reducedMotion()) {
                half *= 1.0F + REVEAL_SWELL * (float) Math.sin(Math.PI * t);
            }
            Rect emblem = resultEmblem();
            emblems.add(shown, centreX(x0, emblem), centreY(y0, emblem), half, lit ? 1.0F : DIM_ALPHA, !lit);
        }
        Rect button = createButton();
        MagicalGuiStyle.button(g, font, x0 + button.x(), y0 + button.y(), button.w(), button.h(),
                buttonBase(formula), buttonLabel(formula));
    }

    private int buttonBase(FormulaState formula) {
        if (pending != null || formula == null || formula.status() == Status.CREATED) {
            return DISABLED_BASE;
        }
        return formula.status() == Status.READY ? CREATE_BASE : BLOCKED_BASE;
    }

    private Component buttonLabel(FormulaState formula) {
        if (pending != null) {
            return Component.translatable("screen.magical.creator.creating");
        }
        if (formula != null && formula.status() == Status.CREATED) {
            return Component.translatable("screen.magical.created");
        }
        return Component.translatable("screen.magical.create");
    }

    private void textHeader(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect titleRect = title();
        g.drawString(font, font.plainSubstrByWidth(getTitle().getString(), TITLE_W), x0 + titleRect.x(), y0 + titleRect.y(),
                MagicalGuiStyle.TEXT_PRIMARY, false);
        Rect chip = classChip();
        boolean any = state.hasClass(MagicalClasses.SPELL_CREATOR);
        g.drawCenteredString(font, font.plainSubstrByWidth(className(state).getString(), chip.w() - 8),
                x0 + chip.x() + chip.w() / 2 + 1, y0 + chip.y() + 3, any ? accent(state) : MagicalGuiStyle.TEXT_MUTED);
        Rect xp = xpBar();
        g.drawCenteredString(font, font.plainSubstrByWidth(xpLabel(state).getString(), xp.w() - 6),
                x0 + xp.x() + xp.w() / 2, y0 + xp.y() + 3, MagicalGuiStyle.TEXT_PRIMARY);
    }

    private void textTabs(GuiGraphics g, int x0, int y0) {
        for (int index = 0; index < TAB_COUNT; index++) {
            Rect rect = tab(index);
            String label = Component.translatable(index == 0 ? "screen.magical.creator.tab.create" : "screen.magical.creator.tab.formulas").getString();
            g.drawCenteredString(font, font.plainSubstrByWidth(label, rect.w() - 6), x0 + rect.x() + rect.w() / 2, y0 + rect.y() + 3,
                    index == tab ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED);
        }
    }

    private void textCreate(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        MagicalGuiStyle.sectionLabel(g, font, x0 + LIST_X, y0 + INGREDIENTS_LABEL_Y,
                Component.translatable("screen.magical.creator.ingredients"), MagicalGuiStyle.TEXT_PRIMARY);
        if (ingredients.isEmpty()) {
            drawWrapped(g, Component.translatable("screen.magical.creator.no_ingredients"),
                    x0 + LIST_X + 4, y0 + LIST_Y + 8, LIST_W - 8, MagicalGuiStyle.TEXT_MUTED, 6);
        }
        int visible = Math.min(INGREDIENT_ROWS, ingredients.size() - ingredientScroll);
        for (int row = 0; row < visible; row++) {
            MagicSkillDefinition skill = ingredients.get(ingredientScroll + row);
            Rect rect = ingredientRow(row);
            int textX = x0 + rect.x() + 24;
            int loadedIn = skill.id().equals(first) ? 1 : skill.id().equals(second) ? 2 : 0;
            String tag = loadedIn == 0 ? "" : loadedIn == 1 ? "I" : "II";
            int tagW = tag.isEmpty() ? 0 : font.width(tag) + 6;
            int nameW = rect.right() - 4 - tagW - (rect.x() + 24);
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), nameW),
                    textX, y0 + rect.y() + 2, MagicalGuiStyle.TEXT_PRIMARY, false);
            g.drawString(font, font.plainSubstrByWidth(schoolAndTier(skill).getString(), nameW),
                    textX, y0 + rect.y() + 11, MagicalGuiStyle.TEXT_MUTED, false);
            if (!tag.isEmpty()) {
                g.drawString(font, tag, x0 + rect.right() - 4 - font.width(tag), y0 + rect.y() + 6, MagicalGuiStyle.ACCENT_GOLD, false);
            }
        }
        Rect hint = ingredientHint();
        g.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.magical.creator.ingredient_hint").getString(), hint.w()),
                x0 + hint.x(), y0 + hint.y(), MagicalGuiStyle.TEXT_MUTED, false);

        textSlot(g, state, x0, y0, 0, first);
        textSlot(g, state, x0, y0, 1, second);
        Rect plusRect = plus();
        g.drawCenteredString(font, "+", x0 + plusRect.x() + plusRect.w() / 2, y0 + plusRect.y() + 5, MagicalGuiStyle.ACCENT_GOLD);
        textResult(g, state, x0, y0);
        drawWrapped(g, Component.translatable("screen.magical.creator.note"), x0 + note().x(), y0 + note().y(), note().w(),
                MagicalGuiStyle.TEXT_MUTED, NOTE_H / 11);
    }

    private void textSlot(GuiGraphics g, PlayerMagicState state, int x0, int y0, int index, ResourceLocation id) {
        Rect card = slot(index);
        Component label = Component.translatable(index == 0 ? "screen.magical.creator.slot_one" : "screen.magical.creator.slot_two");
        g.drawString(font, label, x0 + card.x() + 6, y0 + card.y() + 5, MagicalGuiStyle.TEXT_MUTED, false);
        if (id == null) {
            g.drawString(font, Component.translatable("screen.magical.creator.empty_slot"),
                    x0 + card.x() + SLOT_TEXT_DX, y0 + card.y() + 24, MagicalGuiStyle.TEXT_MUTED, false);
            return;
        }
        Rect clear = slotClear(index);
        g.drawString(font, "x", x0 + clear.x() + 3, y0 + clear.y() + 1, MagicalGuiStyle.TEXT_PRIMARY, false);
        int color = state.hasUnlocked(id) ? MagicalGuiStyle.TEXT_PRIMARY : FormulaListPanel.MISSING_RED & 0xFFFFFF;
        drawWrapped(g, FormulaListPanel.skillName(id), x0 + card.x() + SLOT_TEXT_DX, y0 + card.y() + 19,
                card.w() - SLOT_TEXT_DX - 6, color, 2);
    }

    private void textResult(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect text = resultText();
        int x = x0 + text.x();
        int y = y0 + text.y();
        FormulaState formula = formula(state);
        if (first == null || second == null) {
            if (revealed != null && formula == null) {
                g.drawString(font, FormulaListPanel.skillName(revealed), x, y, FormulaListPanel.CREATED_GREEN, false);
                g.drawString(font, Component.translatable("screen.magical.creator.status.created"), x, y + 12, FormulaListPanel.CREATED_GREEN, false);
                return;
            }
            g.drawString(font, Component.translatable("screen.magical.creator.result"), x, y, MagicalGuiStyle.TEXT_MUTED, false);
            g.drawString(font, Component.translatable("screen.magical.creator.pick_two"), x, y + 12, MagicalGuiStyle.TEXT_MUTED, false);
            return;
        }
        if (formula == null) {
            g.drawString(font, Component.translatable("screen.magical.creator.result"), x, y, MagicalGuiStyle.TEXT_MUTED, false);
            drawWrapped(g, Component.translatable("screen.magical.creator.no_formula"), x, y + 12, text.w(), FormulaListPanel.MISSING_RED & 0xFFFFFF, 2);
            return;
        }
        int accent = FormulaListPanel.accent(formula.status()) & 0xFFFFFF;
        g.drawString(font, font.plainSubstrByWidth(FormulaListPanel.outputName(formula).getString(), text.w()), x, y, accent, false);
        int line = y + 12;
        line += drawWrapped(g, FormulaListPanel.statusLine(formula), x, line, text.w(), accent, 2);
        line += 3;
        line += drawWrapped(g, formula.recipe().requirement(), x, line, text.w(), MagicalGuiStyle.TEXT_MUTED,
                Math.max(1, (y + RESULT_TEXT_H - line) / 11 - 2));
        int lossColor = formula.recipe().consumesInputs() ? WARNING_ORANGE : MagicalGuiStyle.TEXT_MUTED;
        drawWrapped(g, formula.recipe().lossWarning(), x, line + 2, text.w(), lossColor, Math.max(1, (y + RESULT_TEXT_H - line - 2) / 11));
    }

    /** Draws at most {@code maxLines} wrapped lines and returns the height used. */
    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color, int maxLines) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int shown = Math.min(lines.size(), Math.max(1, maxLines));
        for (int i = 0; i < shown; i++) {
            g.drawString(font, lines.get(i), x, y + i * 11, color, false);
        }
        return shown * 11;
    }

    // ---- tooltips ---------------------------------------------------------------------------------

    private List<Component> tooltipAt(PlayerMagicState state, double lx, double ly) {
        if (hit(xpBar(), lx, ly)) {
            MagicalClassDefinition originator = MagicalClasses.get(MagicalClasses.MAGIC_ORIGINATOR);
            int cost = originator == null ? 0 : originator.xpCost();
            return List.of(Component.translatable("screen.magical.creator.xp_tooltip",
                    FormulaListPanel.className(MagicalClasses.MAGIC_ORIGINATOR), state.classXpPool(MagicalClasses.SPELL_CREATOR), cost));
        }
        if (tab == OpenSpellCreatorPayload.TAB_FORMULAS) {
            return formulas.tooltipAt(lx, ly);
        }
        int row = ingredientRowAt(lx, ly);
        if (row >= 0 && ingredientScroll + row < ingredients.size()) {
            return skillTooltip(ingredients.get(ingredientScroll + row));
        }
        int slotIndex = slotAt(lx, ly);
        if (slotIndex >= 0) {
            MagicSkillDefinition skill = MagicContent.get(slotIndex == 0 ? first : second);
            return skill == null ? List.of() : skillTooltip(skill);
        }
        FormulaState formula = formula(state);
        if (formula != null && hit(result(), lx, ly)) {
            MagicSkillDefinition output = MagicContent.get(formula.recipe().outputSkill());
            List<Component> lines = new ArrayList<>(output == null ? List.of() : skillTooltip(output));
            lines.add(FormulaListPanel.statusLine(formula).copy().withColor(FormulaListPanel.accent(formula.status()) & 0xFFFFFF));
            return lines;
        }
        return List.of();
    }

    private static List<Component> skillTooltip(MagicSkillDefinition skill) {
        return List.of(
                Component.translatable(skill.nameKey()).withColor(nameTint(skill)),
                schoolAndTier(skill).copy().withColor(MagicalGuiStyle.TEXT_MUTED),
                Component.translatable(skill.descriptionKey()));
    }

    /** The tint its emblem is drawn with; never the raw skill colour, which for Black Flames is nearly black. */
    private static int nameTint(MagicSkillDefinition skill) {
        return HudPalette.cardTint(VisualProfiles.of(skill.id())) & 0xFFFFFF;
    }

    /** School, and the tier the codex would show; hidden tiers below zero get the school alone. */
    private static Component schoolAndTier(MagicSkillDefinition skill) {
        MutableComponent line = Component.translatable(skill.school().translationKey());
        if (skill.tier() >= 0) {
            line.append(" - ").append(Component.translatable("screen.magical.tier", skill.tier() + 1));
        }
        return line;
    }

    // ---- input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        if (hit(back(), lx, ly)) {
            MagicalNetwork.sendOpenCodexRequest();
            return true;
        }
        int tabHit = tabAt(lx, ly);
        if (tabHit >= 0) {
            tab = tabHit;
            return true;
        }
        if (tab == OpenSpellCreatorPayload.TAB_FORMULAS) {
            FormulaState formula = formulas.rowAt(lx, ly);
            if (formula != null && formula.status() == Status.READY) {
                first = formula.recipe().firstInputId();
                second = formula.recipe().secondInputId();
                tab = OpenSpellCreatorPayload.TAB_CREATE;
            }
            return true;
        }
        for (int index = 0; index < 2; index++) {
            if (hit(slotClear(index), lx, ly)) {
                if (index == 0) {
                    first = null;
                } else {
                    second = null;
                }
                return true;
            }
        }
        int row = ingredientRowAt(lx, ly);
        if (row >= 0 && ingredientScroll + row < ingredients.size()) {
            place(ingredients.get(ingredientScroll + row).id());
            return true;
        }
        if (hit(createButton(), lx, ly)) {
            submit(state());
        }
        return true;
    }

    /** A click on a loaded ingredient clears it; otherwise it takes the first empty slot, or replaces Slot II. */
    private void place(ResourceLocation id) {
        if (id.equals(first)) {
            first = null;
        } else if (id.equals(second)) {
            second = null;
        } else if (first == null) {
            first = id;
        } else {
            second = id;
        }
    }

    private void submit(PlayerMagicState state) {
        if (pending != null) {
            return;
        }
        FormulaState formula = formula(state);
        if (formula == null || formula.status() != Status.READY) {
            return;
        }
        MagicalNetwork.sendCreateSkill(first, second);
        pending = formula.recipe().outputSkill();
        pendingTicks = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        if (tab == OpenSpellCreatorPayload.TAB_FORMULAS) {
            formulas.mouseScrolled(lx, ly, scrollY);
            return true;
        }
        Rect list = new Rect("ingredient list", LIST_X, LIST_Y, LIST_W, INGREDIENT_ROWS * ROW_STRIDE);
        if (hit(list, lx, ly)) {
            ingredientScroll = clampScroll(ingredientScroll - (int) Math.signum(scrollY), ingredients.size(), INGREDIENT_ROWS);
        }
        return true;
    }

    // ---- state helpers ------------------------------------------------------------------------------

    private FormulaState formula(PlayerMagicState state) {
        FusionRecipe recipe = MagicFusionService.recipeFor(first, second);
        return recipe == null ? null : MagicFusionService.status(state, recipe);
    }

    private static int accent(PlayerMagicState state) {
        return state.hasClass(MagicalClasses.MAGIC_ORIGINATOR) ? MagicalGuiStyle.ACCENT_GOLD : MagicalGuiStyle.ACCENT_VIOLET;
    }

    private static Component className(PlayerMagicState state) {
        if (state.hasClass(MagicalClasses.MAGIC_ORIGINATOR)) {
            return FormulaListPanel.className(MagicalClasses.MAGIC_ORIGINATOR);
        }
        if (state.hasClass(MagicalClasses.SPELL_CREATOR)) {
            return FormulaListPanel.className(MagicalClasses.SPELL_CREATOR);
        }
        return Component.translatable("screen.magical.creator.status.locked", FormulaListPanel.className(MagicalClasses.SPELL_CREATOR));
    }

    private static Component xpLabel(PlayerMagicState state) {
        if (state.hasClass(MagicalClasses.MAGIC_ORIGINATOR)) {
            return FormulaListPanel.className(MagicalClasses.MAGIC_ORIGINATOR);
        }
        MagicalClassDefinition originator = MagicalClasses.get(MagicalClasses.MAGIC_ORIGINATOR);
        int cost = originator == null ? 0 : originator.xpCost();
        return Component.translatable("screen.magical.creator.xp", state.classXpPool(MagicalClasses.SPELL_CREATOR), cost);
    }

    private static float xpFraction(PlayerMagicState state) {
        if (state.hasClass(MagicalClasses.MAGIC_ORIGINATOR)) {
            return 1.0F;
        }
        MagicalClassDefinition originator = MagicalClasses.get(MagicalClasses.MAGIC_ORIGINATOR);
        int cost = originator == null ? 0 : originator.xpCost();
        return cost <= 0 ? 0.0F : Math.min(1.0F, state.classXpPool(MagicalClasses.SPELL_CREATOR) / (float) cost);
    }

    private static float centreX(int x0, Rect rect) {
        return x0 + rect.x() + rect.w() / 2.0F;
    }

    private static float centreY(int y0, Rect rect) {
        return y0 + rect.y() + rect.h() / 2.0F;
    }

    private static PlayerMagicState state() {
        return ClientMagicState.get();
    }
}
