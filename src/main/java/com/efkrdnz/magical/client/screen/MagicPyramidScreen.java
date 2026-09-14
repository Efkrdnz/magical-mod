package com.efkrdnz.magical.client.screen;

import static com.efkrdnz.magical.client.screen.CodexLayout.TAB_CLASSES;
import static com.efkrdnz.magical.client.screen.CodexLayout.TAB_LOADOUTS;
import static com.efkrdnz.magical.client.screen.CodexLayout.TAB_PASSIVES;
import static com.efkrdnz.magical.client.screen.CodexLayout.TAB_SKILLS;
import static com.efkrdnz.magical.client.screen.CodexLayout.hit;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.creator.SpellCreatorScreen;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicLoadout;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.MagicSkillTuningView;
import com.efkrdnz.magical.magic.MagicSkillType;
import com.efkrdnz.magical.magic.MagicTuningStat;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import com.efkrdnz.magical.magic.menu.MagicPyramidMenu;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.OpenSpellCreatorPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Magic Codex: the pyramid of tiers, the owned skills, the four cast keys, the skill detail
 * with its tuning, and the Loadouts, Passives and Classes tabs.
 *
 * <p>It wears the frame every full-size magical screen shares ({@link ScreenChrome}): the top strip
 * carries the title, the proficiency level chip, the XP bar and Close; the four tabs replace the
 * sub-views that used to be entered by a button and left by Back. Every rectangle it draws or
 * hit-tests comes from {@link CodexLayout}, so the overlap test knows about it.
 *
 * <p>The menu behind it ({@link MagicPyramidMenu}) carries no slots - only button ids and the five
 * data slots - so this screen never calls the container's own render passes; it paints the whole
 * thing itself in three passes: chrome and view fills, then every emblem in one {@code drawSpecial}
 * through the {@link EmblemPainter}, then text and tooltips.
 */
public final class MagicPyramidScreen extends AbstractContainerScreen<MagicPyramidMenu> implements HudDebug.Captured {
    /** Display only. The real bindings live in MagicalKeyMappings and the player may rebind them. */
    private static final String[] KEY_NAMES = {"Z", "X", "C", "V"};
    private static final int KEY_ESCAPE = 256;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private static final int DEFAULT_BELOW_TIER_COUNT = 3;
    /** Layers -1..-4 have school names; -5 is Authority and carries its own label. */
    private static final int NAMED_BELOW_LAYERS = 4;
    private static final int AUTHORITY_TIER = -5;
    private static final int LINE_H = 11;
    private static final int DETAIL_SCROLL_STEP = 12;
    private static final int TUNING_ROW_H = 18;
    private static final int TOOLTIP_W = 220;
    private static final int PASSIVE_TOOLTIP_W = 230;

    private static final int GOLD_TEXT = 0xFFD67A;
    private static final int SOFT_TEXT = 0xBFD7FF;
    private static final int STAT_TEXT = 0xD8E8FF;
    private static final int CURSE_TEXT = 0xD66A6A;
    private static final int ENABLED_TEXT = 0xA6E3A1;
    private static final int GROUP_TEXT = 0x7F93B4;

    private static final int PRESS_BASE = 0xFF20445B;
    private static final int DANGER_BASE = 0xFF4A2730;
    private static final int OFF_BASE = 0xFF1A1F2B;
    private static final int NEW_BASE = 0xFF345C42;
    private static final int TREE_BASE = 0xFF3B2F5E;
    private static final int CREATOR_BASE = 0xFF3F315C;
    private static final int TOGGLE_BASE = 0xFF26354A;
    private static final int TOGGLE_BELOW_BASE = 0xFF563066;
    private static final int DISPEL_BASE = 0xFF633A6F;
    private static final int DISPEL_OFF_BASE = 0xFF2A2430;
    private static final int TUNE_BASE = 0xFF27354A;
    private static final int CARD_BASE = 0xFF1A2230;
    private static final int CARD_SELECTED_BASE = 0xFF2D5A74;
    private static final int TIER_BASE = 0xFF23465B;
    private static final int TIER_SELECTED_BASE = 0xFF2F5F7A;
    private static final int BELOW_BASE = 0xFF342143;
    private static final int BELOW_SELECTED_BASE = 0xFF6A3F84;
    private static final int AUTHORITY_BASE = 0xFF24465D;
    private static final int AUTHORITY_SELECTED_BASE = 0xFF2E6B82;
    private static final int PASSIVE_BASE = 0xFF1B2433;
    private static final int PASSIVE_ON_BASE = 0xFF17332E;
    private static final int PASSIVE_HOVER_BASE = 0xFF24445D;
    private static final int CURSE_BASE = 0xFF2D1B2D;
    private static final int CURSE_HOVER_BASE = 0xFF4B254B;
    private static final int EMPTY_FILL = 0xFF172033;

    private static final List<Component> TAB_LABELS = List.of(
            Component.translatable("screen.magical.codex.tab.skills"),
            Component.translatable("screen.magical.loadouts"),
            Component.translatable("screen.magical.passives"),
            Component.translatable("screen.magical.classes"));

    /**
     * What tells one family's block of commands apart from another's. The geometry is shared; only
     * the colours and whether the names are bold ever differed between them.
     */
    private record SubskillPalette(int header, int fill, int label, int button, int value, boolean bold) {}

    private static final SubskillPalette AEGIS_PALETTE =
            new SubskillPalette(0xFFF4B2, 0x66242010, 0xFFE9C6, 0xFF3A3416, 0xFFE9A6, false);
    private static final SubskillPalette GABRIEL_PALETTE =
            new SubskillPalette(0xFFD700, 0x66261908, 0xFFE9A6, 0xFF40300A, 0xFFD700, true);
    private static final SubskillPalette BLACK_FLAMES_PALETTE =
            new SubskillPalette(0xE43A16, 0x6614071D, 0xE6B8C8, 0xFF2A1320, 0xFF8A39, false);
    private static final SubskillPalette SPATIAL_PALETTE =
            new SubskillPalette(0x9DDAFF, 0x66101830, 0xBFD7FF, 0xFF172A46, 0xBDEBFF, false);
    private static final SubskillPalette SOUL_VOW_PALETTE =
            new SubskillPalette(0xD8F0FF, 0x6614252F, 0xD8F0FF, 0xFF193344, 0xF4FDFF, false);

    /** A parent that is a menu of commands rather than a spell, with the commands that carry its points. */
    private record SubskillFamily(Component header, SubskillPalette palette, List<MagicSkillDefinition> subSkills) {}

    /** The selected skill, its tuning, and where its scrolled content sits this frame. */
    private record DetailFrame(MagicSkillDefinition skill, MagicSkillTuning tuning, int contentTop, int contentHeight) {}

    /** One line of the passives list: either a group header or an owned passive. */
    private record PassiveRow(Component header, MagicPassiveDefinition definition, int index) {
        boolean isHeader() {
            return header != null;
        }

        int height() {
            return isHeader() ? CodexLayout.PASSIVE_HEADER_H : CodexLayout.PASSIVE_ROW_H;
        }
    }

    /** A passives row that fits on screen this frame, and the screen-local top it starts at. */
    private record PassiveEntry(PassiveRow row, int y) {}

    /** An active curse that fits on screen this frame: its index in the roster and its visible row. */
    private record CurseEntry(MagicPassiveDefinition definition, int index, int visibleRow) {}

    private enum PassiveTooltipStyle {
        NORMAL,
        CURSE,
        SIN
    }

    private final EmblemPainter emblems = new EmblemPainter();

    private int tab = TAB_SKILLS;
    private int skillListScroll;
    private int detailScroll;
    private int passiveListScroll;
    private int curseListScroll;
    /** The loadout rename field. Only drawn and only fed input while the Loadouts tab is open. */
    private EditBox loadoutNameBox;
    /** Which loadout the field currently holds, so switching rows reloads it. */
    private int nameBoxLoadout = -1;
    private boolean belowPyramidOpen;

    public MagicPyramidScreen(MagicPyramidMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ScreenChrome.PANEL_W;
        imageHeight = ScreenChrome.PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        belowPyramidOpen = menu.selectedTier() < 0;
        // addWidget, not addRenderableWidget: this screen never calls super.render, so the field is
        // drawn by hand on the Loadouts tab and would be invisible everywhere else anyway.
        Rect box = CodexLayout.nameBox();
        loadoutNameBox = new EditBox(font, leftPos + box.x(), topPos + box.y(), box.w(), box.h(),
                Component.translatable("screen.magical.loadout_name"));
        loadoutNameBox.setMaxLength(MagicLoadout.MAX_NAME_LENGTH);
        nameBoxLoadout = -1;
        addWidget(loadoutNameBox);
    }

    // ---- drawing --------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g, mouseX, mouseY, partial);
        PlayerMagicState state = ClientMagicState.get();
        int x0 = leftPos;
        int y0 = topPos;
        double lx = mouseX - x0;
        double ly = mouseY - y0;
        int accent = accent();

        MagicalGuiStyle.screenBackground(g, x0, y0, x0 + imageWidth, y0 + imageHeight);
        emblems.begin(g);
        ScreenChrome.paintHeader(g, font, x0, y0, accent, xpFraction(state), Component.translatable("screen.magical.close"));
        ScreenChrome.paintTabs(g, x0, y0, CodexLayout.TAB_COUNT, tab);
        ScreenChrome.paintBody(g, x0, y0, accent);
        switch (tab) {
            case TAB_LOADOUTS -> paintLoadouts(g, state, x0, y0);
            case TAB_PASSIVES -> paintPassives(g, state, x0, y0, lx, ly);
            case TAB_CLASSES -> paintClasses(g, state, x0, y0);
            default -> paintSkills(g, state, x0, y0, lx, ly);
        }
        emblems.flush();

        ScreenChrome.textHeader(g, font, x0, y0, getTitle(),
                Component.translatable("screen.magical.codex.level", state.proficiencyLevel()), accent, xpLabel(state));
        ScreenChrome.textTabs(g, font, x0, y0, TAB_LABELS, tab);
        switch (tab) {
            case TAB_LOADOUTS -> textLoadouts(g, state, x0, y0, mouseX, mouseY, partial);
            case TAB_PASSIVES -> textPassives(g, state, x0, y0, mouseX, mouseY, lx, ly);
            case TAB_CLASSES -> textClasses(g, state, x0, y0);
            default -> textSkills(g, state, x0, y0);
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

    /** Nothing: {@link #render} paints the whole screen itself and never calls the container passes. */
    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    private int accent() {
        return switch (tab) {
            case TAB_LOADOUTS -> MagicalGuiStyle.ACCENT_GOLD;
            case TAB_PASSIVES -> MagicalGuiStyle.ACCENT_NATURE;
            case TAB_CLASSES -> MagicalGuiStyle.ACCENT_VIOLET;
            default -> belowPyramidOpen ? MagicalGuiStyle.ACCENT_VIOLET : MagicalGuiStyle.ACCENT_ARCANE;
        };
    }

    private static float xpFraction(PlayerMagicState state) {
        int xp = state.proficiencyXp();
        int remaining = MagicContent.xpForNextLevel(xp);
        if (remaining <= 0) {
            return 1.0F;
        }
        int into = MagicContent.xpIntoLevel(xp);
        return into / (float) (into + remaining);
    }

    private static Component xpLabel(PlayerMagicState state) {
        int xp = state.proficiencyXp();
        int remaining = MagicContent.xpForNextLevel(xp);
        if (remaining <= 0) {
            return Component.translatable("screen.magical.codex.xp_max");
        }
        int into = MagicContent.xpIntoLevel(xp);
        return Component.translatable("screen.magical.codex.xp", into, into + remaining);
    }

    // ---- the Skills tab -------------------------------------------------------------------------

    private void paintSkills(GuiGraphics g, PlayerMagicState state, int x0, int y0, double lx, double ly) {
        List<Integer> tiers = visiblePyramidTiers();
        int rows = tiers.size();
        Rect toggle = CodexLayout.belowToggle();
        button(g, x0 + toggle.x(), y0 + toggle.y(), toggle.w(), toggle.h(), belowPyramidOpen ? TOGGLE_BELOW_BASE : TOGGLE_BASE,
                Component.translatable(belowPyramidOpen ? "screen.magical.above_short" : "screen.magical.below_short"));
        for (int row = 0; row < rows; row++) {
            int tier = tiers.get(row);
            Rect block = CodexLayout.tierBlock(row, rows, belowPyramidOpen);
            boolean selected = tier == menu.selectedTier();
            boolean authority = tier == AUTHORITY_TIER;
            int base;
            if (!belowPyramidOpen) {
                base = selected ? TIER_SELECTED_BASE : TIER_BASE;
            } else if (authority) {
                base = selected ? AUTHORITY_SELECTED_BASE : AUTHORITY_BASE;
            } else {
                base = selected ? BELOW_SELECTED_BASE : BELOW_BASE;
            }
            int accent = belowPyramidOpen && !authority ? MagicalGuiStyle.ACCENT_VIOLET : MagicalGuiStyle.ACCENT_ARCANE;
            tierBlock(g, x0 + block.x(), y0 + block.y(), block.w(), block.h(), base, selected, accent);
        }

        List<MagicSkillDefinition> skills = ownedSkillsForTier(activeTierForCurrentView());
        int visible = CodexLayout.visibleSkillRows(rows);
        skillListScroll = CodexLayout.clampScroll(skillListScroll, skills.size(), visible);
        Rect list = CodexLayout.skillList(rows);
        MagicalGuiStyle.inset(g, x0 + list.x() - 2, y0 + list.y() - 2, x0 + list.right() + 2, y0 + list.bottom() + 2);
        int hovered = CodexLayout.skillRowAt(rows, lx, ly);
        for (int row = 0; row < visible && skillListScroll + row < skills.size(); row++) {
            MagicSkillDefinition skill = skills.get(skillListScroll + row);
            Rect rect = CodexLayout.skillRow(rows, row);
            boolean selected = skill.id().equals(menu.selectedSkillId());
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), selected || row == hovered,
                    0xFF000000 | emblems.tint(skill.id()));
            Rect emblem = CodexLayout.skillEmblem(rows, row);
            emblems.add(skill.id(), centreX(x0, emblem), centreY(y0, emblem), CodexLayout.SKILL_EMBLEM_HALF, 1.0F);
        }
        Rect bar = CodexLayout.skillScrollbar(rows);
        MagicalGuiStyle.scrollbar(g, x0 + bar.x() + 1, y0 + bar.y(), bar.h(), skills.size(), visible, skillListScroll);

        paintLoadout(g, state, x0, y0);
        paintDetail(g, state, x0, y0);
    }

    /** The four cast keys of the active loadout, plus the controls that edit them. */
    private void paintLoadout(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            Rect card = CodexLayout.card(slot);
            int x = x0 + card.x();
            int y = y0 + card.y();
            boolean selected = slot == menu.selectedSlot();
            ResourceLocation id = state.equippedSkill(slot);
            MagicalGuiStyle.card(g, x, y, x + card.w(), y + card.h(), selected ? CARD_SELECTED_BASE : CARD_BASE);
            if (selected) {
                g.fill(x - 1, y - 1, x + card.w() + 1, y, MagicalGuiStyle.ACCENT_GOLD);
            }
            g.fill(x, y + card.h() - 2, x + card.w(), y + card.h(), id == null ? 0xFF0A0F1B : 0xFF000000 | emblems.tint(id));
            if (id != null) {
                Rect emblem = CodexLayout.cardEmblem(slot);
                emblems.add(id, centreX(x0, emblem), centreY(y0, emblem), CodexLayout.CARD_EMBLEM_HALF, 1.0F);
            }
        }
        Rect equip = CodexLayout.equipButton();
        button(g, x0 + equip.x(), y0 + equip.y(), equip.w(), equip.h(), menu.selectedSkillId() == null ? OFF_BASE : PRESS_BASE,
                Component.translatable("screen.magical.equip"));
        Rect clear = CodexLayout.clearSlotButton();
        button(g, x0 + clear.x(), y0 + clear.y(), clear.w(), clear.h(),
                state.equippedSkill(menu.selectedSlot()) == null ? OFF_BASE : DANGER_BASE,
                Component.translatable("screen.magical.clear_slot"));
    }

    private void paintDetail(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect detail = CodexLayout.detail();
        MagicalGuiStyle.inset(g, x0 + detail.x(), y0 + detail.y(), x0 + detail.right(), y0 + detail.bottom());
        DetailFrame frame = detailFrame(state);
        if (frame == null) {
            return;
        }
        MagicSkillDefinition skill = frame.skill();
        Rect header = CodexLayout.detailHeader();
        g.fill(x0 + header.x() + 4, y0 + header.bottom() - 1, x0 + header.right() - 4, y0 + header.bottom(),
                MagicalGuiStyle.withAlpha(emblems.tint(skill.id()), 0x66));
        Rect emblem = CodexLayout.detailEmblem();
        emblems.add(skill.id(), centreX(x0, emblem), centreY(y0, emblem), CodexLayout.DETAIL_EMBLEM_HALF, 1.0F);

        beginDetailScissor(g, x0, y0);
        int left = x0 + CodexLayout.DETAIL_X + CodexLayout.DETAIL_CONTENT_DX;
        int top = y0 + tuningTop(frame);
        List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(skill);
        int budget = state.tuningLimit();
        for (int i = 0; i < statsOrder.size(); i++) {
            paintTuningButtons(g, left, top + i * TUNING_ROW_H, tuningValue(frame.tuning(), statsOrder.get(i)),
                    frame.tuning().spent(), budget, TUNE_BASE);
        }
        SubskillFamily family = familyOf(skill);
        if (family != null) {
            paintSubskillTuning(g, state, left, top + subskillOffset(skill), family);
        }
        g.disableScissor();
        Rect bar = CodexLayout.detailScrollbar();
        MagicalGuiStyle.scrollbar(g, x0 + bar.x() + 1, y0 + bar.y(), bar.h(), frame.contentHeight(),
                CodexLayout.detailContent().h(), detailScroll);
    }

    /**
     * One family's commands, each with its own point budget.
     *
     * <p>Per sub-skill, deliberately: a family parent is a menu rather than a spell, so the points
     * belong to the commands underneath it and Gabriel's four are budgeted separately.
     */
    private void paintSubskillTuning(GuiGraphics g, PlayerMagicState state, int left, int top, SubskillFamily family) {
        int budget = state.tuningLimit();
        int y = top + 14;
        for (MagicSkillDefinition subSkill : family.subSkills()) {
            MagicSkillTuning tuning = state.tuningFor(subSkill.id());
            List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(subSkill);
            g.fill(left - 3, y - 3, left + CodexLayout.DETAIL_CONTENT_W, y + 20 + statsOrder.size() * TUNING_ROW_H, family.palette().fill());
            y += 16;
            for (MagicTuningStat stat : statsOrder) {
                paintTuningButtons(g, left, y, tuningValue(tuning, stat), tuning.spent(), budget, family.palette().button());
                y += TUNING_ROW_H;
            }
            y += 8;
        }
    }

    /**
     * The minus and plus for one stat, dimmed when they would do nothing.
     *
     * <p>A press at the budget is refused by the server, so without this the button looks live and
     * silently does nothing - the wart the per-stat cap used to hide behind.
     */
    private void paintTuningButtons(GuiGraphics g, int left, int y, int value, int spent, int budget, int color) {
        button(g, left + 110, y - 2, 16, 12, value > -budget ? color : OFF_BASE, Component.literal("-"));
        button(g, left + 168, y - 2, 16, 12, spent < budget ? color : OFF_BASE, Component.literal("+"));
    }

    private void textSkills(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        List<Integer> tiers = visiblePyramidTiers();
        int rows = tiers.size();
        Rect label = CodexLayout.pyramidLabel();
        MagicalGuiStyle.sectionLabel(g, font, x0 + label.x(), y0 + label.y(),
                Component.translatable(belowPyramidOpen ? "screen.magical.below_pyramid" : "screen.magical.pyramid"),
                belowPyramidOpen ? 0xD19BFF : SOFT_TEXT);
        for (int row = 0; row < rows; row++) {
            int tier = tiers.get(row);
            Rect block = CodexLayout.tierBlock(row, rows, belowPyramidOpen);
            Component name;
            int color;
            if (!belowPyramidOpen) {
                name = Component.translatable("screen.magical.tier", tier + 1);
                color = 0xECF5FF;
            } else if (tier == AUTHORITY_TIER) {
                name = Component.translatable("authority.magical.authority_of_space");
                color = 0xE5FBFF;
            } else {
                name = negativeTierLabel(tier);
                color = 0xF3E7FF;
            }
            tierLabel(g, name, x0 + block.x() + block.w() / 2, y0 + block.y(), block.w() - 8, color);
        }

        List<MagicSkillDefinition> skills = ownedSkillsForTier(activeTierForCurrentView());
        Rect list = CodexLayout.skillList(rows);
        if (skills.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.magical.no_owned_skills"),
                    x0 + list.x() + list.w() / 2, y0 + list.y() + 16, MagicalGuiStyle.TEXT_MUTED);
        }
        int visible = CodexLayout.visibleSkillRows(rows);
        for (int row = 0; row < visible && skillListScroll + row < skills.size(); row++) {
            MagicSkillDefinition skill = skills.get(skillListScroll + row);
            Rect rect = CodexLayout.skillRow(rows, row);
            int textX = x0 + rect.x() + CodexLayout.SKILL_TEXT_DX;
            boolean bound = state.isEquippedAnywhere(skill.id());
            int nameW = rect.w() - CodexLayout.SKILL_TEXT_DX - (bound ? 16 : 4);
            String name = font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), nameW);
            if (MagicContent.GABRIEL.id().equals(skill.id())) {
                g.drawString(font, Component.literal(name).withStyle(ChatFormatting.BOLD), textX, y0 + rect.y() + 2, 0xFFD700, false);
            } else {
                g.drawString(font, name, textX, y0 + rect.y() + 2, MagicalGuiStyle.TEXT_PRIMARY, false);
            }
            g.drawString(font, font.plainSubstrByWidth(Component.translatable(skill.school().translationKey()).getString(), nameW),
                    textX, y0 + rect.y() + 11, MagicalGuiStyle.TEXT_MUTED, false);
            if (bound) {
                g.drawString(font, "◆", x0 + rect.right() - 12, y0 + rect.y() + 6, GOLD_TEXT, false);
            }
        }

        textLoadout(g, state, x0, y0);
        textDetail(g, state, x0, y0);
    }

    private void textLoadout(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect label = CodexLayout.loadoutLabel();
        MagicalGuiStyle.sectionLabel(g, font, x0 + label.x(), y0 + label.y(), Component.translatable("screen.magical.loadout"), SOFT_TEXT);
        Rect name = CodexLayout.loadoutName();
        g.drawString(font, font.plainSubstrByWidth(
                Component.translatable("screen.magical.loadout_active", state.activeLoadout().name()).getString(), name.w()),
                x0 + name.x(), y0 + name.y(), GOLD_TEXT, false);
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            Rect card = CodexLayout.card(slot);
            int x = x0 + card.x();
            int y = y0 + card.y();
            ResourceLocation id = state.equippedSkill(slot);
            MagicSkillDefinition equipped = id == null ? null : MagicContent.get(id);
            // The key, not the slot number: these are the keys the player presses.
            g.drawString(font, KEY_NAMES[slot], x + 4, y + 4, GOLD_TEXT, false);
            String skillName = equipped == null ? "-" : Component.translatable(equipped.nameKey()).getString();
            g.drawString(font, font.plainSubstrByWidth(skillName, card.w() - 8), x + 4, y + 19,
                    equipped == null ? MagicalGuiStyle.TEXT_MUTED : 0xE7F4FF, false);
        }
    }

    private void textDetail(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect label = CodexLayout.detailLabel();
        MagicalGuiStyle.sectionLabel(g, font, x0 + label.x(), y0 + label.y(), Component.translatable("screen.magical.skill_detail"), SOFT_TEXT);
        Rect detail = CodexLayout.detail();
        DetailFrame frame = detailFrame(state);
        if (frame == null) {
            g.drawCenteredString(font, Component.translatable("screen.magical.codex.no_selection"),
                    x0 + detail.x() + detail.w() / 2, y0 + detail.y() + detail.h() / 2 - 4, MagicalGuiStyle.TEXT_MUTED);
            return;
        }
        MagicSkillDefinition skill = frame.skill();
        int textX = x0 + detail.x() + CodexLayout.DETAIL_TEXT_DX;
        int textW = detail.w() - CodexLayout.DETAIL_TEXT_DX - 6;
        String name = font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), textW);
        if (MagicContent.GABRIEL.id().equals(skill.id())) {
            g.drawString(font, Component.literal(name).withStyle(ChatFormatting.BOLD), textX, y0 + detail.y() + 7, 0xFFD700, false);
        } else {
            g.drawString(font, name, textX, y0 + detail.y() + 7, emblems.tint(skill.id()), false);
        }
        g.drawString(font, font.plainSubstrByWidth(schoolAndKind(skill).getString(), textW), textX, y0 + detail.y() + 18,
                MagicalGuiStyle.TEXT_MUTED, false);

        beginDetailScissor(g, x0, y0);
        int left = x0 + CodexLayout.DETAIL_X + CodexLayout.DETAIL_CONTENT_DX;
        int top = y0 + frame.contentTop();
        top += drawWrapped(g, Component.translatable(skill.descriptionKey()), left, top, CodexLayout.DETAIL_CONTENT_W, SOFT_TEXT, LINE_H);
        drawSkillStatLines(g, skill, skill.resolve(frame.tuning()), left, top + 5);
        top = y0 + tuningTop(frame);
        int budget = state.tuningLimit();
        List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(skill);
        if (!statsOrder.isEmpty()) {
            drawPointCounter(g, left + 232, top - 8, frame.tuning().spent(), budget);
        }
        for (int i = 0; i < statsOrder.size(); i++) {
            MagicTuningStat stat = statsOrder.get(i);
            int rowY = top + i * TUNING_ROW_H;
            g.drawString(font, Component.translatable(MagicSkillTuningView.labelKey(skill, stat)), left, rowY, SOFT_TEXT, false);
            g.drawCenteredString(font, Integer.toString(tuningValue(frame.tuning(), stat)), left + 148, rowY, GOLD_TEXT);
        }
        SubskillFamily family = familyOf(skill);
        if (family != null) {
            textSubskillTuning(g, state, left, top + subskillOffset(skill), family);
        }
        g.disableScissor();
    }

    private void textSubskillTuning(GuiGraphics g, PlayerMagicState state, int left, int top, SubskillFamily family) {
        int budget = state.tuningLimit();
        SubskillPalette palette = family.palette();
        int y = top;
        g.drawString(font, family.header(), left, y, palette.header(), false);
        y += 14;
        for (MagicSkillDefinition subSkill : family.subSkills()) {
            MagicSkillTuning tuning = state.tuningFor(subSkill.id());
            Component name = Component.translatable(subSkill.nameKey());
            g.drawString(font, palette.bold() ? name.copy().withStyle(ChatFormatting.BOLD) : name, left, y, subSkill.color(), false);
            drawPointCounter(g, left + 232, y, tuning.spent(), budget);
            y += 16;
            for (MagicTuningStat stat : MagicSkillTuningView.statsFor(subSkill)) {
                g.drawString(font, Component.translatable(MagicSkillTuningView.labelKey(subSkill, stat)), left + 8, y, palette.label(), false);
                g.drawCenteredString(font, Integer.toString(tuningValue(tuning, stat)), left + 148, y, palette.value());
                y += TUNING_ROW_H;
            }
            y += 8;
        }
    }

    /**
     * The spend, right-aligned on a header row.
     *
     * <p>The old readout was "Limit: +/- N", which was true while every stat had its own cap. The
     * number is now the whole allowance for the skill, so what a player needs to see is how much of
     * it is gone.
     */
    private void drawPointCounter(GuiGraphics g, int right, int y, int spent, int budget) {
        Component text = Component.translatable("screen.magical.tuning_points", spent, budget);
        int color = spent >= budget ? 0xFFB86B : MagicalGuiStyle.TEXT_MUTED;
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    private void drawSkillStatLines(GuiGraphics g, MagicSkillDefinition skill, MagicSkillResolvedStats stats, int left, int top) {
        String costs = costLine(skill, stats);
        if (MagicContent.VAULT_OF_AVARICE.id().equals(skill.id())) {
            g.drawString(font, Component.translatable("screen.magical.vault_utility_stats"), left, top, STAT_TEXT, false);
            return;
        }
        if (MagicContent.CREATE_SUBSPACE.id().equals(skill.id())) {
            g.drawString(font, Component.translatable("screen.magical.create_subspace_stats"), left, top, STAT_TEXT, false);
            g.drawString(font, costs, left, top + 13, STAT_TEXT, false);
            return;
        }
        if (MagicContent.MANIPULATE_SPACE.id().equals(skill.id())) {
            g.drawString(font, Component.translatable("screen.magical.manipulate_space_stats"), left, top, STAT_TEXT, false);
            g.drawString(font, costs, left, top + 13, STAT_TEXT, false);
            return;
        }
        if (MagicContent.POCKET_DIMENSION.id().equals(skill.id())) {
            g.drawString(font, Component.translatable("screen.magical.pocket_dimension_stats"), left, top, STAT_TEXT, false);
            g.drawString(font, costs, left, top + 13, STAT_TEXT, false);
            return;
        }
        if (MagicContent.SPACE_WALKER.id().equals(skill.id())) {
            g.drawString(font, Component.translatable("screen.magical.space_walker_stats"), left, top, STAT_TEXT, false);
            g.drawString(font, "Mana " + stats.manaCost() + "+distance  Cooldown " + stats.cooldownTicks() + "+distance", left, top + 13, STAT_TEXT, false);
            return;
        }
        if (skill.type() == MagicSkillType.BARRIER) {
            g.drawString(font, "Barrier " + stats.barrierRestore() + "  Size " + format(stats.size()), left, top, STAT_TEXT, false);
            g.drawString(font, costs, left, top + 13, STAT_TEXT, false);
            return;
        }
        g.drawString(font, "Dmg " + format(stats.damage()) + "  Spd " + format(stats.speed()) + "  Size " + format(stats.size()), left, top, STAT_TEXT, false);
        g.drawString(font, costs, left, top + 13, STAT_TEXT, false);
    }

    /** "Mana N  Cooldown M" - or, for blood, the price in blood with the points applied. */
    private static String costLine(MagicSkillDefinition skill, MagicSkillResolvedStats stats) {
        String cooldown = "  Cooldown " + stats.cooldownTicks();
        if (skill.school() != MagicSchool.BLOOD) {
            return "Mana " + stats.manaCost() + cooldown;
        }
        if (MagicContent.BLOOD_MANIPULATION.id().equals(skill.id())) {
            // Billed by the length drawn: the base, plus so much per block of it.
            return "Blood " + BloodService.scale(stats, BloodShapeRules.BASE_COST) + " + "
                    + BloodService.scale(stats, BloodShapeRules.COST_PER_BLOCK) + " per block" + cooldown;
        }
        return "Blood " + BloodService.cost(stats) + cooldown;
    }

    // ---- the detail geometry --------------------------------------------------------------------

    /** Null when nothing is selected, or the selection belongs to the other side of the pyramid. */
    private DetailFrame detailFrame(PlayerMagicState state) {
        ResourceLocation id = menu.selectedSkillId();
        if (id == null || !selectedTierMatchesCurrentView()) {
            return null;
        }
        MagicSkillDefinition skill = MagicContent.get(id);
        if (skill == null) {
            return null;
        }
        int height = detailContentHeight(skill);
        Rect content = CodexLayout.detailContent();
        detailScroll = Mth.clamp(detailScroll, 0, Math.max(0, height - content.h()));
        return new DetailFrame(skill, state.tuningFor(id), content.y() - detailScroll, height);
    }

    private void beginDetailScissor(GuiGraphics g, int x0, int y0) {
        Rect detail = CodexLayout.detail();
        Rect content = CodexLayout.detailContent();
        g.enableScissor(x0 + detail.x() + 2, y0 + content.y(), x0 + CodexLayout.DETAIL_SCROLL_X - 2, y0 + content.bottom() + 2);
    }

    /** Screen-local top of the first tuning row: the description, then the two stat lines. */
    private int tuningTop(DetailFrame frame) {
        return frame.contentTop() + descriptionHeight(frame.skill()) + 40;
    }

    /** From the first tuning row to the family header under the parent's own rows. */
    private int subskillOffset(MagicSkillDefinition skill) {
        return Math.max(1, MagicSkillTuningView.statsFor(skill).size()) * TUNING_ROW_H + 8;
    }

    private int descriptionHeight(MagicSkillDefinition skill) {
        return font.split(Component.translatable(skill.descriptionKey()), CodexLayout.DETAIL_CONTENT_W).size() * LINE_H;
    }

    private int detailContentHeight(MagicSkillDefinition skill) {
        SubskillFamily family = familyOf(skill);
        int stats = MagicSkillTuningView.statsFor(skill).size();
        int height = descriptionHeight(skill) + 48 + (family == null ? stats : Math.max(1, stats)) * TUNING_ROW_H;
        if (family != null) {
            height += 22;
            for (MagicSkillDefinition subSkill : family.subSkills()) {
                height += 24 + MagicSkillTuningView.statsFor(subSkill).size() * TUNING_ROW_H + 8;
            }
        }
        return height;
    }

    private static SubskillFamily familyOf(MagicSkillDefinition skill) {
        ResourceLocation id = skill.id();
        if (MagicContent.SOVEREIGN_AEGIS.id().equals(id)) {
            return new SubskillFamily(Component.translatable("screen.magical.aegis_commands"), AEGIS_PALETTE, MagicContent.sovereignAegisSubSkills());
        }
        if (MagicContent.GABRIEL.id().equals(id)) {
            return new SubskillFamily(Component.translatable("screen.magical.gabriel_commands").withStyle(ChatFormatting.BOLD),
                    GABRIEL_PALETTE, MagicContent.gabrielSubSkills());
        }
        if (MagicContent.BLACK_FLAMES.id().equals(id)) {
            return new SubskillFamily(Component.translatable("screen.magical.black_flames_forms"), BLACK_FLAMES_PALETTE, MagicContent.blackFlamesSubSkills());
        }
        if (MagicContent.SPATIAL_ARSENAL.id().equals(id)) {
            return new SubskillFamily(Component.translatable("screen.magical.spatial_arsenal_commands"), SPATIAL_PALETTE, MagicContent.spatialArsenalSubSkills());
        }
        if (MagicContent.SOUL_VOW.id().equals(id)) {
            return new SubskillFamily(Component.translatable("screen.magical.soul_vow_commands"), SOUL_VOW_PALETTE, MagicContent.soulVowSubSkills());
        }
        return null;
    }

    private static int tuningValue(MagicSkillTuning tuning, MagicTuningStat stat) {
        return switch (stat) {
            case DAMAGE -> tuning.damage();
            case SPEED -> tuning.speed();
            case SIZE -> tuning.size();
            case DURATION -> tuning.duration();
            case EFFICIENCY -> tuning.efficiency();
        };
    }

    // ---- the Loadouts tab -----------------------------------------------------------------------

    /**
     * Pick a loadout on the left, edit its four keys on the right.
     *
     * <p>Picking a row switches to that loadout as well as editing it, so a skill bound here is on
     * the keys immediately without closing the codex. The two only disagree when the server refuses
     * the switch under the post-cast lock: the highlight moves, the diamond does not.
     */
    private void paintLoadouts(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        List<MagicLoadout> loadouts = state.loadouts();
        int edited = editedLoadout(loadouts);
        syncNameBox(loadouts, edited);

        Rect first = CodexLayout.loadoutRow(0);
        Rect last = CodexLayout.loadoutRow(MagicContent.MAX_LOADOUTS - 1);
        MagicalGuiStyle.inset(g, x0 + first.x() - 4, y0 + first.y() - 4, x0 + first.right() + 4, y0 + last.bottom() + 4);
        for (int index = 0; index < loadouts.size(); index++) {
            Rect row = CodexLayout.loadoutRow(index);
            boolean active = index == state.activeLoadoutIndex();
            MagicalGuiStyle.listRow(g, x0 + row.x(), y0 + row.y(), row.w(), row.h(), index == edited,
                    active ? MagicalGuiStyle.ACCENT_GOLD : MagicalGuiStyle.ACCENT_ARCANE);
        }
        Rect create = CodexLayout.newButton();
        button(g, x0 + create.x(), y0 + create.y(), create.w(), create.h(),
                loadouts.size() >= MagicContent.MAX_LOADOUTS ? OFF_BASE : NEW_BASE, Component.translatable("screen.magical.loadout_new"));
        Rect delete = CodexLayout.deleteButton();
        button(g, x0 + delete.x(), y0 + delete.y(), delete.w(), delete.h(),
                loadouts.size() <= 1 ? OFF_BASE : DANGER_BASE, Component.translatable("screen.magical.loadout_delete"));
        Rect rename = CodexLayout.renameButton();
        button(g, x0 + rename.x(), y0 + rename.y(), rename.w(), rename.h(), PRESS_BASE, Component.translatable("screen.magical.loadout_rename"));

        boolean picked = menu.selectedSkillId() != null;
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            Rect card = CodexLayout.keySlot(slot);
            MagicalGuiStyle.inset(g, x0 + card.x(), y0 + card.y(), x0 + card.right(), y0 + card.bottom());
            ResourceLocation bound = loadouts.isEmpty() ? null : loadouts.get(edited).slot(slot);
            if (bound != null) {
                Rect emblem = CodexLayout.keySlotEmblem(slot);
                emblems.add(bound, centreX(x0, emblem), centreY(y0, emblem), CodexLayout.SLOT_EMBLEM_HALF, 1.0F);
            }
            Rect bind = CodexLayout.bindButton(slot);
            button(g, x0 + bind.x(), y0 + bind.y(), bind.w(), bind.h(), picked ? PRESS_BASE : OFF_BASE,
                    Component.translatable("screen.magical.loadout_bind"));
            Rect clear = CodexLayout.clearButton(slot);
            button(g, x0 + clear.x(), y0 + clear.y(), clear.w(), clear.h(), bound == null ? OFF_BASE : DANGER_BASE,
                    Component.translatable("screen.magical.clear_slot"));
        }
    }

    private void textLoadouts(GuiGraphics g, PlayerMagicState state, int x0, int y0, int mouseX, int mouseY, float partial) {
        Rect label = CodexLayout.loadoutsLabel();
        MagicalGuiStyle.sectionLabel(g, font, x0 + label.x(), y0 + label.y(), Component.translatable("screen.magical.loadouts"), MagicalGuiStyle.TEXT_PRIMARY);

        // Bind uses whatever is selected on the Skills tab, so name it - otherwise the button is a
        // press with no visible subject.
        ResourceLocation pickedId = menu.selectedSkillId();
        MagicSkillDefinition picked = pickedId == null ? null : MagicContent.get(pickedId);
        Rect target = CodexLayout.bindTarget();
        String bindTarget = picked == null
                ? Component.translatable("screen.magical.loadout_bind_none").getString()
                : Component.translatable("screen.magical.loadout_binding", Component.translatable(picked.nameKey()).getString()).getString();
        g.drawString(font, font.plainSubstrByWidth(bindTarget, target.w()), x0 + target.x(), y0 + target.y(),
                picked == null ? MagicalGuiStyle.TEXT_MUTED : GOLD_TEXT, false);

        List<MagicLoadout> loadouts = state.loadouts();
        int edited = editedLoadout(loadouts);
        for (int index = 0; index < loadouts.size(); index++) {
            Rect row = CodexLayout.loadoutRow(index);
            g.drawString(font, font.plainSubstrByWidth(loadouts.get(index).name(), row.w() - 38), x0 + row.x() + 6, y0 + row.y() + 4,
                    MagicalGuiStyle.TEXT_PRIMARY, false);
            if (index == state.activeLoadoutIndex()) {
                g.drawString(font, "◆", x0 + row.right() - 14, y0 + row.y() + 4, GOLD_TEXT, false);
            }
        }
        Rect nameLabel = CodexLayout.nameLabel();
        g.drawString(font, Component.translatable("screen.magical.loadout_name"), x0 + nameLabel.x(), y0 + nameLabel.y(), MagicalGuiStyle.TEXT_MUTED, false);
        if (loadoutNameBox != null) {
            loadoutNameBox.render(g, mouseX, mouseY, partial);
        }

        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            Rect card = CodexLayout.keySlot(slot);
            ResourceLocation bound = loadouts.isEmpty() ? null : loadouts.get(edited).slot(slot);
            MagicSkillDefinition skill = bound == null ? null : MagicContent.get(bound);
            g.drawString(font, Component.translatable("screen.magical.loadout_key", KEY_NAMES[slot]), x0 + card.x() + 6, y0 + card.y() + 5, GOLD_TEXT, false);
            g.drawString(font, font.plainSubstrByWidth(skill == null ? "-" : Component.translatable(skill.nameKey()).getString(), CodexLayout.SLOT_TEXT_W),
                    x0 + card.x() + 6, y0 + card.y() + 15, skill == null ? MagicalGuiStyle.TEXT_MUTED : 0xE7F4FF, false);
        }
    }

    /**
     * Clamped against the live client state, not the menu's own: the menu holds the player
     * attachment, which the client never writes.
     */
    private int editedLoadout(List<MagicLoadout> loadouts) {
        return Math.max(0, Math.min(menu.editedLoadout(), loadouts.size() - 1));
    }

    /**
     * Reload the field when the chosen loadout changes, and after a rename lands.
     *
     * <p>The second case is what keeps it honest: the server sanitizes the name, so what comes back
     * can differ from what was typed, and the field should show the name the loadout actually has.
     * It is never overwritten while focused, so this cannot eat somebody mid-edit.
     */
    private void syncNameBox(List<MagicLoadout> loadouts, int edited) {
        if (loadoutNameBox == null || loadouts.isEmpty()) {
            return;
        }
        String actual = loadouts.get(edited).name();
        if (edited != nameBoxLoadout) {
            nameBoxLoadout = edited;
            loadoutNameBox.setValue(actual);
            loadoutNameBox.setFocused(false);
        } else if (!loadoutNameBox.isFocused() && !loadoutNameBox.getValue().equals(actual)) {
            loadoutNameBox.setValue(actual);
        }
    }

    private void commitLoadoutName() {
        if (loadoutNameBox == null) {
            return;
        }
        List<MagicLoadout> loadouts = ClientMagicState.get().loadouts();
        if (loadouts.isEmpty()) {
            return;
        }
        MagicalNetwork.sendRenameLoadout(editedLoadout(loadouts), loadoutNameBox.getValue());
        loadoutNameBox.setFocused(false);
        setFocused(null);
    }

    // ---- the Passives tab -----------------------------------------------------------------------

    private void paintPassives(GuiGraphics g, PlayerMagicState state, int x0, int y0, double lx, double ly) {
        List<PassiveRow> rows = passiveRows(state);
        passiveListScroll = CodexLayout.clampScroll(passiveListScroll, rows.size(), CodexLayout.visiblePassiveRows());
        Rect list = CodexLayout.passivesList();
        g.enableScissor(x0 + list.x(), y0 + list.y(), x0 + list.right(), y0 + list.bottom());
        for (PassiveEntry entry : visiblePassives(rows)) {
            int y = y0 + entry.y();
            if (entry.row().isHeader()) {
                // A thin rule with the source of everything below it, so 69 passives read as groups.
                g.fill(x0 + list.x(), y + CodexLayout.PASSIVE_HEADER_H - 3, x0 + list.x() + CodexLayout.PASSIVE_CARD_W,
                        y + CodexLayout.PASSIVE_HEADER_H - 2, 0xFF2C3B54);
                continue;
            }
            MagicPassiveDefinition definition = entry.row().definition();
            boolean enabled = state.isPassiveEnabled(definition.id());
            Rect card = CodexLayout.passiveCard(entry.y());
            boolean hovered = hit(card, lx, ly);
            MagicalGuiStyle.card(g, x0 + card.x(), y, x0 + card.right(), y0 + card.bottom(),
                    hovered ? PASSIVE_HOVER_BASE : enabled ? PASSIVE_ON_BASE : PASSIVE_BASE);
            g.fill(x0 + card.x(), y0 + card.bottom() - 2, x0 + card.right(), y0 + card.bottom(), 0xFF000000 | definition.color());
            MagicalGuiStyle.checkbox(g, x0 + card.x() + 6, y + 7, enabled);
        }
        g.disableScissor();
        Rect bar = CodexLayout.passivesScrollbar();
        MagicalGuiStyle.scrollbar(g, x0 + bar.x() + 1, y0 + bar.y(), bar.h(), rows.size(), CodexLayout.visiblePassiveRows(), passiveListScroll);
        if (rows.isEmpty()) {
            g.fill(x0 + list.x(), y0 + list.y(), x0 + list.x() + CodexLayout.PASSIVE_CARD_W, y0 + list.y() + 28, EMPTY_FILL);
        }

        int activeCurses = activeCurseCount(state);
        curseListScroll = CodexLayout.clampScroll(curseListScroll, activeCurses, CodexLayout.visibleCurseRows());
        Rect curses = CodexLayout.cursesList();
        g.enableScissor(x0 + curses.x(), y0 + curses.y(), x0 + curses.right(), y0 + curses.bottom());
        for (CurseEntry entry : visibleCurses(state)) {
            Rect card = CodexLayout.curseCard(entry.visibleRow());
            boolean hovered = hit(card, lx, ly);
            boolean canDispel = state.canDispelCurse(entry.definition().id());
            MagicalGuiStyle.card(g, x0 + card.x(), y0 + card.y(), x0 + card.right(), y0 + card.bottom(), hovered ? CURSE_HOVER_BASE : CURSE_BASE);
            g.fill(x0 + card.x(), y0 + card.bottom() - 2, x0 + card.right(), y0 + card.bottom(), 0xFF000000 | entry.definition().color());
            Rect dispel = CodexLayout.dispelButton(entry.visibleRow());
            button(g, x0 + dispel.x(), y0 + dispel.y(), dispel.w(), dispel.h(), canDispel ? DISPEL_BASE : DISPEL_OFF_BASE,
                    Component.translatable("screen.magical.dispel"));
        }
        g.disableScissor();
        Rect curseBar = CodexLayout.cursesScrollbar();
        MagicalGuiStyle.scrollbar(g, x0 + curseBar.x() + 1, y0 + curseBar.y(), curseBar.h(), activeCurses, CodexLayout.visibleCurseRows(), curseListScroll);
        if (activeCurses == 0) {
            g.fill(x0 + curses.x(), y0 + curses.y(), x0 + curses.x() + CodexLayout.CURSE_CARD_W, y0 + curses.y() + 28, EMPTY_FILL);
        }
    }

    private void textPassives(GuiGraphics g, PlayerMagicState state, int x0, int y0, int mouseX, int mouseY, double lx, double ly) {
        Rect label = CodexLayout.passivesLabel();
        MagicalGuiStyle.sectionLabel(g, font, x0 + label.x(), y0 + label.y(), Component.translatable("screen.magical.passive_skills"), SOFT_TEXT);
        Rect curseLabel = CodexLayout.cursesLabel();
        MagicalGuiStyle.sectionLabel(g, font, x0 + curseLabel.x(), y0 + curseLabel.y(), Component.translatable("screen.magical.curses"), CURSE_TEXT);

        List<PassiveRow> rows = passiveRows(state);
        Rect list = CodexLayout.passivesList();
        MagicPassiveDefinition hoveredPassive = null;
        g.enableScissor(x0 + list.x(), y0 + list.y(), x0 + list.right(), y0 + list.bottom());
        for (PassiveEntry entry : visiblePassives(rows)) {
            int x = x0 + list.x();
            int y = y0 + entry.y();
            if (entry.row().isHeader()) {
                g.drawString(font, entry.row().header(), x + 2, y + 2, GROUP_TEXT, false);
                continue;
            }
            MagicPassiveDefinition definition = entry.row().definition();
            boolean enabled = state.isPassiveEnabled(definition.id());
            boolean hovered = hit(CodexLayout.passiveCard(entry.y()), lx, ly);
            if (hovered) {
                hoveredPassive = definition;
            }
            g.drawString(font, Component.translatable(definition.nameKey()), x + 24, y + 4, enabled ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED, false);
            g.drawString(font, Component.translatable(enabled ? "screen.magical.passive_enabled" : "screen.magical.passive_disabled"), x + 24, y + 15,
                    enabled ? ENABLED_TEXT : MagicalGuiStyle.TEXT_MUTED, false);
            g.drawString(font, Component.translatable("screen.magical.hover_details"), x + 112, y + 15, hovered ? 0xF7D774 : 0x6F7F99, false);
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.magical.no_passives"), x0 + list.x() + CodexLayout.PASSIVE_CARD_W / 2, y0 + list.y() + 10, MagicalGuiStyle.TEXT_MUTED);
        }

        Rect curses = CodexLayout.cursesList();
        g.enableScissor(x0 + curses.x(), y0 + curses.y(), x0 + curses.right(), y0 + curses.bottom());
        for (CurseEntry entry : visibleCurses(state)) {
            Rect card = CodexLayout.curseCard(entry.visibleRow());
            int x = x0 + card.x();
            int y = y0 + card.y();
            boolean hovered = hit(card, lx, ly);
            boolean canDispel = state.canDispelCurse(entry.definition().id());
            if (hovered) {
                hoveredPassive = entry.definition();
            }
            g.drawString(font, Component.translatable(entry.definition().nameKey()), x + 6, y + 5, 0xF4D6FF, false);
            g.drawString(font, Component.translatable("screen.magical.hover_details"), x + 6, y + 18, hovered ? 0xF7D774 : 0x9D7BB0, false);
            g.drawString(font, curseRowText(entry.definition(), state), x + 6, y + 31, canDispel ? ENABLED_TEXT : CURSE_TEXT, false);
        }
        g.disableScissor();
        if (activeCurseCount(state) == 0) {
            g.drawCenteredString(font, Component.translatable("screen.magical.no_curses"), x0 + curses.x() + CodexLayout.CURSE_CARD_W / 2, y0 + curses.y() + 10, MagicalGuiStyle.TEXT_MUTED);
        }
        if (hoveredPassive != null) {
            // Raised on z the way vanilla renders tooltips. Row labels drawn above go through the
            // deferred font batch, which otherwise flushes on top of this panel and bleeds through.
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 400.0F);
            drawPassiveTooltip(g, hoveredPassive, state, mouseX, mouseY);
            g.pose().popPose();
        }
    }

    /** The rows that fit between the top of the list and its bottom this frame, with their tops. */
    private List<PassiveEntry> visiblePassives(List<PassiveRow> rows) {
        List<PassiveEntry> entries = new ArrayList<>();
        Rect list = CodexLayout.passivesList();
        int y = list.y();
        for (int i = passiveListScroll; i < rows.size(); i++) {
            PassiveRow row = rows.get(i);
            if (y + row.height() > list.bottom()) {
                break;
            }
            entries.add(new PassiveEntry(row, y));
            y += row.height();
        }
        return entries;
    }

    private List<CurseEntry> visibleCurses(PlayerMagicState state) {
        List<CurseEntry> entries = new ArrayList<>();
        List<MagicPassiveDefinition> curses = MagicPassiveContent.curses();
        int row = 0;
        for (int index = 0; index < curses.size(); index++) {
            MagicPassiveDefinition definition = curses.get(index);
            if (!state.hasCurse(definition.id())) {
                continue;
            }
            if (row++ < curseListScroll) {
                continue;
            }
            int visibleRow = row - curseListScroll - 1;
            if (visibleRow >= CodexLayout.visibleCurseRows()) {
                break;
            }
            entries.add(new CurseEntry(definition, index, visibleRow));
        }
        return entries;
    }

    private void drawPassiveTooltip(GuiGraphics g, MagicPassiveDefinition definition, PlayerMagicState state, int mouseX, int mouseY) {
        PassiveTooltipStyle style = passiveTooltipStyle(definition);
        int width = PASSIVE_TOOLTIP_W;
        int textWidth = width - 22;
        int descriptionHeight = wrappedHeight(Component.translatable(definition.descriptionKey()), textWidth, LINE_H);
        int extraHeight = definition.curse() ? (state.linkedSinPassiveForCurse(definition.id()) == null ? 34 : 48) : 22;
        int height = Math.max(82, 42 + descriptionHeight + extraHeight);
        int x = Mth.clamp(mouseX + 14, leftPos + 8, leftPos + imageWidth - width - 8);
        int y = Mth.clamp(mouseY + 14, topPos + 8, topPos + imageHeight - height - 8);
        drawPassiveTooltipFrame(g, x, y, width, height, style, definition.color());

        int titleColor = switch (style) {
            case CURSE -> 0xF4A6FF;
            case SIN -> 0xFFD166;
            case NORMAL -> MagicalGuiStyle.TEXT_PRIMARY;
        };
        g.drawString(font, Component.translatable(definition.nameKey()), x + 11, y + 10, titleColor, false);
        // Say which node handed this out, so a passive can always be traced back to its class.
        ResourceLocation grantedBy = MagicalClasses.classGranting(definition.id());
        if (grantedBy != null && MagicalClasses.get(grantedBy) != null) {
            Component from = Component.translatable("screen.magical.class_passive_source",
                    Component.translatable(MagicalClasses.get(grantedBy).nameKey()));
            g.drawString(font, from, x + width - 11 - font.width(from), y + 10, 0x8FA6C4, false);
        }
        if (style == PassiveTooltipStyle.SIN) {
            g.drawString(font, Component.literal("§k" + "SIN" + "§r"), x + width - 38, y + 10, 0xFF4D4D, false);
        } else if (definition.curse()) {
            g.drawString(font, Component.translatable("screen.magical.curse_label"), x + width - 48, y + 10, 0xFF5C8A, false);
        }

        int statusColor = definition.curse() ? CURSE_TEXT : state.isPassiveEnabled(definition.id()) ? ENABLED_TEXT : MagicalGuiStyle.TEXT_MUTED;
        Component status = definition.curse()
                ? Component.translatable("screen.magical.curse_active")
                : Component.translatable(state.isPassiveEnabled(definition.id()) ? "screen.magical.passive_enabled" : "screen.magical.passive_disabled");
        g.drawString(font, status, x + 11, y + 25, statusColor, false);
        if (!definition.curse()) {
            g.drawString(font, Component.translatable("screen.magical.passive_level", state.passiveLevel(definition.id()), definition.maxLevel()), x + 132, y + 25, GOLD_TEXT, false);
        }

        int textY = y + 42;
        textY += drawWrapped(g, Component.translatable(definition.descriptionKey()), x + 11, textY, textWidth, style == PassiveTooltipStyle.CURSE ? 0xE6C1F2 : STAT_TEXT, LINE_H);
        if (definition.curse()) {
            boolean canDispel = state.canDispelCurse(definition.id());
            ResourceLocation linkedSinPassive = state.linkedSinPassiveForCurse(definition.id());
            if (linkedSinPassive != null) {
                MagicPassiveDefinition passive = MagicPassiveContent.get(linkedSinPassive);
                Component passiveName = passive == null ? Component.literal(linkedSinPassive.getPath()) : Component.translatable(passive.nameKey());
                g.drawString(font, Component.translatable("screen.magical.sin_curse_requirement", passiveName), x + 11, textY + 8, 0xF7D774, false);
                g.drawString(font, curseRowText(definition, state), x + 11, textY + 21, canDispel ? 0xB9F6CA : 0xE6A6A6, false);
                g.drawString(font, Component.translatable(canDispel ? "screen.magical.curse_dispel_ready" : "screen.magical.sin_curse_waiting"), x + 11, textY + 34, canDispel ? 0xB9F6CA : 0xE6A6A6, false);
            } else {
                g.drawString(font, Component.translatable("screen.magical.dispel_cost", definition.requiredProficiencyToDispel(), definition.dispelManaCost()), x + 11, textY + 8, canDispel ? ENABLED_TEXT : 0xF38BA8, false);
                g.drawString(font, Component.translatable(canDispel ? "screen.magical.curse_dispel_ready" : "screen.magical.curse_dispel_blocked"), x + 11, textY + 21, canDispel ? 0xB9F6CA : 0xE6A6A6, false);
            }
        } else {
            g.drawString(font, Component.translatable("screen.magical.passive_toggle_hint"), x + 11, textY + 9, 0x8FA6C6, false);
        }
    }

    private Component curseRowText(MagicPassiveDefinition definition, PlayerMagicState state) {
        ResourceLocation linkedSinPassive = state.linkedSinPassiveForCurse(definition.id());
        if (linkedSinPassive == null) {
            return Component.translatable("screen.magical.dispel_cost", definition.requiredProficiencyToDispel(), definition.dispelManaCost());
        }
        if (!state.hasPassive(linkedSinPassive)) {
            return Component.translatable("screen.magical.sin_curse_unlock_required");
        }
        if (state.isPassiveEnabled(linkedSinPassive)) {
            return Component.translatable("screen.magical.sin_curse_disable_required");
        }
        long remaining = state.sinCurseDispelRemainingMillis(definition.id());
        if (remaining <= 0L) {
            return Component.translatable("screen.magical.sin_curse_ready");
        }
        return Component.translatable("screen.magical.sin_curse_remaining", formatDurationSeconds((int) Math.ceil(remaining / 1000.0D)));
    }

    private static String formatDurationSeconds(int seconds) {
        int minutes = Math.max(0, seconds) / 60;
        int remainder = Math.max(0, seconds) % 60;
        return minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    private void drawPassiveTooltipFrame(GuiGraphics g, int x, int y, int width, int height, PassiveTooltipStyle style, int accent) {
        int background = switch (style) {
            case CURSE -> 0xF01B0A1E;
            case SIN -> 0xF0120507;
            case NORMAL -> 0xF00B1624;
        };
        int frame = switch (style) {
            case CURSE -> 0xFF9C4BA6;
            case SIN -> 0xFFC98B2B;
            case NORMAL -> 0xFF4C9CCF;
        };
        g.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xDD000000);
        g.fill(x, y, x + width, y + height, background);
        g.fill(x, y, x + width, y + 2, frame);
        g.fill(x, y + height - 2, x + width, y + height, frame);
        g.fill(x, y, x + 2, y + height, frame);
        g.fill(x + width - 2, y, x + width, y + height, frame);
        g.fill(x + 6, y + 36, x + width - 6, y + 37, 0xAA000000 | accent);
        int pulse = (int) ((System.currentTimeMillis() / 90L) % 18L);
        for (int i = 0; i < 5; i++) {
            int stripeY = y + 8 + Math.floorMod(pulse + i * 17, Math.max(1, height - 16));
            int stripeColor = switch (style) {
                case CURSE -> 0x24633A6F;
                case SIN -> 0x2AAC1F24;
                case NORMAL -> 0x2436688E;
            };
            g.fill(x + 5 + i * 3, stripeY, x + width - 5 - i * 4, stripeY + 1, stripeColor);
        }
        if (style == PassiveTooltipStyle.SIN) {
            for (int i = 0; i < 6; i++) {
                int glyphX = x + 12 + i * 34;
                int glyphY = y + height - 16 - Math.floorMod(pulse + i * 5, 10);
                g.drawString(font, Component.literal("§k" + "###" + "§r"), glyphX, glyphY, 0x884E0000, false);
            }
        } else if (style == PassiveTooltipStyle.CURSE) {
            for (int i = 0; i < 6; i++) {
                int crackX = x + 12 + i * 33;
                int crackY = y + 45 + Math.floorMod(pulse + i * 7, Math.max(1, height - 58));
                g.fill(crackX, crackY, crackX + 1, crackY + 8, 0x77D66A6A);
                g.fill(crackX - 2, crackY + 4, crackX + 3, crackY + 5, 0x55D66A6A);
            }
        }
    }

    private static PassiveTooltipStyle passiveTooltipStyle(MagicPassiveDefinition definition) {
        String path = definition.id().getPath();
        if (path.startsWith("sin_") || path.contains("_sin")) {
            return PassiveTooltipStyle.SIN;
        }
        return definition.curse() ? PassiveTooltipStyle.CURSE : PassiveTooltipStyle.NORMAL;
    }

    /**
     * The owned passives, grouped by where they came from.
     *
     * <p>Declaration order in {@code MagicPassiveContent} already runs general, then the sins, then
     * the five class lines in tree order, so emitting a header whenever the group changes is enough
     * and there is no separate sort that could fall out of step with it.
     */
    private static List<PassiveRow> passiveRows(PlayerMagicState state) {
        List<PassiveRow> rows = new ArrayList<>();
        String group = null;
        List<MagicPassiveDefinition> all = MagicPassiveContent.normalPassives();
        for (int index = 0; index < all.size(); index++) {
            MagicPassiveDefinition definition = all.get(index);
            if (!state.hasPassive(definition.id())) {
                continue;
            }
            String next = passiveGroupKey(definition);
            if (!next.equals(group)) {
                group = next;
                rows.add(new PassiveRow(passiveGroupLabel(definition, next), null, -1));
            }
            rows.add(new PassiveRow(null, definition, index));
        }
        return rows;
    }

    private static String passiveGroupKey(MagicPassiveDefinition definition) {
        if (MagicPassiveContent.isSinPassive(definition.id())) {
            return "sins";
        }
        ResourceLocation source = MagicalClasses.classGranting(definition.id());
        if (source == null || !MagicPassiveContent.isClassPassive(definition.id())) {
            return "general";
        }
        return MagicalClasses.baseOf(source).toString();
    }

    private static Component passiveGroupLabel(MagicPassiveDefinition definition, String key) {
        if ("sins".equals(key)) {
            return Component.translatable("screen.magical.passive_group_sins");
        }
        if ("general".equals(key)) {
            return Component.translatable("screen.magical.passive_group_general");
        }
        MagicalClassDefinition base = MagicalClasses.get(MagicalClasses.baseOf(MagicalClasses.classGranting(definition.id())));
        return base == null ? Component.translatable("screen.magical.passive_group_general") : Component.translatable(base.nameKey());
    }

    private static int activeCurseCount(PlayerMagicState state) {
        int count = 0;
        for (MagicPassiveDefinition definition : MagicPassiveContent.curses()) {
            if (state.hasCurse(definition.id())) {
                count++;
            }
        }
        return count;
    }

    // ---- the Classes tab ------------------------------------------------------------------------

    private void paintClasses(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        List<MagicalClassDefinition> visibleClasses = visibleRootClasses(state);
        if (visibleClasses.isEmpty()) {
            Rect empty = CodexLayout.emptyClasses();
            MagicalGuiStyle.inset(g, x0 + empty.x(), y0 + empty.y(), x0 + empty.right(), y0 + empty.bottom());
            if (!state.hasAnyRootClass()) {
                Rect choose = CodexLayout.chooseClassButton();
                button(g, x0 + choose.x(), y0 + choose.y(), choose.w(), choose.h(), TREE_BASE, Component.translatable("screen.magical.class_select_open"));
            }
        } else {
            // The full graph lives in its own screen; this tab stays the summary and the launcher.
            Rect tree = CodexLayout.classTreeButton();
            button(g, x0 + tree.x(), y0 + tree.y(), tree.w(), tree.h(), TREE_BASE, Component.translatable("screen.magical.open_class_tree"));
            // A read-only summary of the trees you own. Evolving happens in Paths of Power, which is
            // the only place that can express converging branches.
            for (int index = 0; index < Math.min(visibleClasses.size(), CodexLayout.CLASS_MAX_ROWS); index++) {
                Rect row = CodexLayout.classRow(index);
                MagicalGuiStyle.listRow(g, x0 + row.x(), y0 + row.y(), row.w(), row.h(), false, MagicalGuiStyle.ACCENT_VIOLET);
            }
        }
        // The class-specific workshops. They used to hang off the per-class tree page that is now gone.
        if (state.hasClass(MagicalClasses.BLACKSMITH)) {
            Rect forge = CodexLayout.forgeButton();
            button(g, x0 + forge.x(), y0 + forge.y(), forge.w(), forge.h(), NEW_BASE, Component.translatable("screen.magical.open_forge"));
        }
        if (state.hasClass(MagicalClasses.SPELL_CREATOR)) {
            Rect creator = CodexLayout.creatorButton();
            button(g, x0 + creator.x(), y0 + creator.y(), creator.w(), creator.h(), CREATOR_BASE, Component.translatable("screen.magical.open_spell_creator"));
        }
    }

    private void textClasses(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect label = CodexLayout.classesLabel();
        MagicalGuiStyle.sectionLabel(g, font, x0 + label.x(), y0 + label.y(), Component.translatable("screen.magical.classes"), MagicalGuiStyle.TEXT_PRIMARY);
        List<MagicalClassDefinition> visibleClasses = visibleRootClasses(state);
        if (visibleClasses.isEmpty()) {
            Rect empty = CodexLayout.emptyClasses();
            int centre = x0 + empty.x() + empty.w() / 2;
            g.drawCenteredString(font, Component.translatable("screen.magical.no_classes"), centre, y0 + empty.y() + 8, MagicalGuiStyle.TEXT_MUTED);
            if (!state.hasAnyRootClass()) {
                g.drawCenteredString(font, Component.translatable("screen.magical.class_select_prompt"), centre, y0 + empty.y() + 22, SOFT_TEXT);
            }
            return;
        }
        for (int index = 0; index < Math.min(visibleClasses.size(), CodexLayout.CLASS_MAX_ROWS); index++) {
            MagicalClassDefinition definition = visibleClasses.get(index);
            Rect row = CodexLayout.classRow(index);
            int x = x0 + row.x();
            int y = y0 + row.y();
            g.drawString(font, Component.translatable(definition.nameKey()), x + 8, y + 4, MagicalGuiStyle.TEXT_PRIMARY, false);
            g.drawString(font, Component.translatable("screen.magical.class_xp", state.classXpPool(definition.id())), x + 8, y + 15, GOLD_TEXT, false);
            g.drawString(font, Component.translatable("screen.magical.class_owned_nodes",
                    ownedInTree(state, definition.id()), MagicalClasses.treeOf(definition.id()).size()), x + 250, y + 10, SOFT_TEXT, false);
        }
    }

    private static int ownedInTree(PlayerMagicState state, ResourceLocation baseId) {
        int owned = 0;
        for (MagicalClassDefinition definition : MagicalClasses.treeOf(baseId)) {
            if (state.hasClass(definition.id())) {
                owned++;
            }
        }
        return owned;
    }

    private static List<MagicalClassDefinition> visibleRootClasses(PlayerMagicState state) {
        return MagicalClasses.roots().stream()
                .filter(definition -> state.hasClass(definition.id()))
                .toList();
    }

    // ---- tooltips -------------------------------------------------------------------------------

    private List<Component> tooltipAt(PlayerMagicState state, double lx, double ly) {
        if (hit(ScreenChrome.xpBar(), lx, ly)) {
            int xp = state.proficiencyXp();
            int remaining = MagicContent.xpForNextLevel(xp);
            return List.of(remaining <= 0
                    ? Component.translatable("screen.magical.codex.xp_tooltip_max", state.proficiencyLevel(), xp)
                    : Component.translatable("screen.magical.codex.xp_tooltip", state.proficiencyLevel(), xp, remaining));
        }
        if (tab == TAB_SKILLS) {
            int rows = visiblePyramidTiers().size();
            int row = CodexLayout.skillRowAt(rows, lx, ly);
            if (row >= 0) {
                List<MagicSkillDefinition> skills = ownedSkillsForTier(activeTierForCurrentView());
                int index = skillListScroll + row;
                return index < skills.size() ? skillTooltip(skills.get(index)) : List.of();
            }
            int slot = CodexLayout.cardAt(lx, ly);
            if (slot >= 0) {
                MagicSkillDefinition skill = state.equippedSkill(slot) == null ? null : MagicContent.get(state.equippedSkill(slot));
                if (skill == null) {
                    return List.of();
                }
                List<Component> lines = new ArrayList<>(skillTooltip(skill));
                lines.add(Component.translatable("screen.magical.codex.equipped", KEY_NAMES[slot]).withColor(GOLD_TEXT));
                return lines;
            }
        }
        if (tab == TAB_LOADOUTS) {
            List<MagicLoadout> loadouts = state.loadouts();
            int slot = CodexLayout.keySlotAt(lx, ly);
            if (slot >= 0 && !loadouts.isEmpty() && hit(CodexLayout.keySlotEmblem(slot), lx, ly)) {
                ResourceLocation bound = loadouts.get(editedLoadout(loadouts)).slot(slot);
                MagicSkillDefinition skill = bound == null ? null : MagicContent.get(bound);
                return skill == null ? List.of() : skillTooltip(skill);
            }
        }
        return List.of();
    }

    private List<Component> skillTooltip(MagicSkillDefinition skill) {
        return List.of(
                Component.translatable(skill.nameKey()).withColor(emblems.tint(skill.id())),
                schoolAndKind(skill).withColor(MagicalGuiStyle.TEXT_MUTED),
                Component.translatable(skill.descriptionKey()));
    }

    /** School, the tier the pyramid shows (hidden tiers below zero get none), and the kind of cast. */
    private static MutableComponent schoolAndKind(MagicSkillDefinition skill) {
        MutableComponent line = Component.translatable(skill.school().translationKey());
        if (skill.tier() >= 0) {
            line.append(" - ").append(Component.translatable("screen.magical.tier", skill.tier() + 1));
        }
        return line.append(" - ").append(Component.translatable("skilltype.magical." + skill.type().name().toLowerCase(Locale.ROOT)));
    }

    // ---- input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        if (hit(ScreenChrome.cornerButton(), lx, ly)) {
            onClose();
            return true;
        }
        int picked = CodexLayout.tabAt(lx, ly);
        if (picked >= 0) {
            selectTab(picked);
            return true;
        }
        return switch (tab) {
            case TAB_LOADOUTS -> handleLoadoutsClick(mouseX, mouseY, lx, ly, button);
            case TAB_PASSIVES -> handlePassivesClick(lx, ly);
            case TAB_CLASSES -> handleClassesClick(lx, ly);
            default -> handleSkillsClick(lx, ly);
        };
    }

    private void selectTab(int next) {
        if (next == tab) {
            return;
        }
        if (tab == TAB_LOADOUTS && loadoutNameBox != null) {
            loadoutNameBox.setFocused(false);
            setFocused(null);
        }
        tab = next;
    }

    /**
     * While the name field has focus it eats the keyboard.
     *
     * <p>Without this, typing a loadout name containing the inventory key closes the codex
     * mid-word, because AbstractContainerScreen treats that key as close whenever no child claimed
     * it - and a plain letter is never claimed by keyPressed, only by charTyped.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBoxFocused()) {
            if (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
                commitLoadoutName();
                return true;
            }
            if (keyCode == KEY_ESCAPE) {
                // Drop focus rather than closing the whole codex.
                loadoutNameBox.setFocused(false);
                setFocused(null);
                return true;
            }
            if (loadoutNameBox.keyPressed(keyCode, scanCode, modifiers) || loadoutNameBox.canConsumeInput()) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameBoxFocused()) {
            return loadoutNameBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    private boolean nameBoxFocused() {
        return tab == TAB_LOADOUTS && loadoutNameBox != null && loadoutNameBox.isFocused();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        int notches = (int) Math.signum(scrollY);
        if (tab == TAB_SKILLS) {
            int rows = visiblePyramidTiers().size();
            if (hit(CodexLayout.skillList(rows), lx, ly)) {
                skillListScroll = CodexLayout.clampScroll(skillListScroll - notches,
                        ownedSkillsForTier(activeTierForCurrentView()).size(), CodexLayout.visibleSkillRows(rows));
                return true;
            }
            if (hit(CodexLayout.detail(), lx, ly)) {
                // The upper bound is applied against the content height when the frame is built.
                detailScroll = Math.max(0, detailScroll - notches * DETAIL_SCROLL_STEP);
                return true;
            }
        } else if (tab == TAB_PASSIVES) {
            PlayerMagicState state = ClientMagicState.get();
            if (hit(CodexLayout.passivesList(), lx, ly)) {
                passiveListScroll = CodexLayout.clampScroll(passiveListScroll - notches, passiveRows(state).size(), CodexLayout.visiblePassiveRows());
                return true;
            }
            if (hit(CodexLayout.cursesList(), lx, ly)) {
                curseListScroll = CodexLayout.clampScroll(curseListScroll - notches, activeCurseCount(state), CodexLayout.visibleCurseRows());
                return true;
            }
        }
        // Swallowed anywhere over the panel so the wheel does not move the hotbar behind the codex.
        return hit(ScreenChrome.panel(), lx, ly) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean handleSkillsClick(double lx, double ly) {
        List<Integer> tiers = visiblePyramidTiers();
        int rows = tiers.size();
        if (hit(CodexLayout.belowToggle(), lx, ly)) {
            belowPyramidOpen = !belowPyramidOpen;
            detailScroll = 0;
            skillListScroll = 0;
            List<Integer> flipped = visiblePyramidTiers();
            if (!flipped.isEmpty()) {
                press(tierButtonId(flipped.get(0)));
            }
            return true;
        }
        int tierRow = CodexLayout.tierRowAt(rows, belowPyramidOpen, lx, ly);
        if (tierRow >= 0) {
            detailScroll = 0;
            skillListScroll = 0;
            press(tierButtonId(tiers.get(tierRow)));
            return true;
        }
        int skillRow = CodexLayout.skillRowAt(rows, lx, ly);
        if (skillRow >= 0) {
            List<MagicSkillDefinition> skills = ownedSkillsForTier(activeTierForCurrentView());
            int index = skillListScroll + skillRow;
            if (index < skills.size()) {
                detailScroll = 0;
                press(MagicPyramidMenu.BUTTON_SKILL_BASE + MagicContent.skillIndex(skills.get(index).id()));
            }
            return true;
        }
        int slot = CodexLayout.cardAt(lx, ly);
        if (slot >= 0) {
            press(MagicPyramidMenu.BUTTON_SLOT_BASE + slot);
            return true;
        }
        if (hit(CodexLayout.equipButton(), lx, ly)) {
            press(MagicPyramidMenu.BUTTON_EQUIP_SELECTED);
            return true;
        }
        if (hit(CodexLayout.clearSlotButton(), lx, ly)) {
            press(MagicPyramidMenu.BUTTON_CLEAR_SLOT);
            return true;
        }
        handleDetailClick(lx, ly);
        return true;
    }

    private void handleDetailClick(double lx, double ly) {
        DetailFrame frame = detailFrame(ClientMagicState.get());
        if (frame == null || !hit(CodexLayout.detailContent(), lx, ly)) {
            return;
        }
        MagicSkillDefinition skill = frame.skill();
        int left = CodexLayout.DETAIL_X + CodexLayout.DETAIL_CONTENT_DX;
        int top = tuningTop(frame);
        SubskillFamily family = familyOf(skill);
        if (family != null) {
            handleSubskillTuningClick(lx, ly, left, top + subskillOffset(skill), family);
            return;
        }
        List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(skill);
        for (int i = 0; i < statsOrder.size(); i++) {
            int delta = tuningPress(lx, ly, left, top + i * TUNING_ROW_H);
            if (delta != 0) {
                press(MagicPyramidMenu.BUTTON_TUNE_BASE + statsOrder.get(i).ordinal() * 10 + (delta > 0 ? 1 : 0));
                return;
            }
        }
    }

    private void handleSubskillTuningClick(double lx, double ly, int left, int top, SubskillFamily family) {
        int perSubskill = MagicTuningStat.values().length * 10;
        int y = top + 14;
        for (int subIndex = 0; subIndex < family.subSkills().size(); subIndex++) {
            y += 16;
            for (MagicTuningStat stat : MagicSkillTuningView.statsFor(family.subSkills().get(subIndex))) {
                int delta = tuningPress(lx, ly, left, y);
                if (delta != 0) {
                    press(MagicPyramidMenu.BUTTON_AEGIS_TUNE_BASE + subIndex * perSubskill + stat.ordinal() * 10 + (delta > 0 ? 1 : 0));
                    return;
                }
                y += TUNING_ROW_H;
            }
            y += 8;
        }
    }

    /** -1 on the minus of a tuning row at {@code y}, +1 on its plus, 0 elsewhere; the same numbers {@link #paintTuningButtons} draws. */
    private static int tuningPress(double lx, double ly, int left, int y) {
        if (hit(new Rect("minus", left + 110, y - 2, 16, 12), lx, ly)) {
            return -1;
        }
        return hit(new Rect("plus", left + 168, y - 2, 16, 12), lx, ly) ? 1 : 0;
    }

    private boolean handleLoadoutsClick(double mouseX, double mouseY, double lx, double ly, int button) {
        // Focus follows the click. Clicking anywhere else drops it, so the next keypress goes back
        // to being a keypress rather than another character in somebody's loadout name.
        if (loadoutNameBox != null) {
            boolean onBox = loadoutNameBox.isMouseOver(mouseX, mouseY);
            loadoutNameBox.setFocused(onBox);
            setFocused(onBox ? loadoutNameBox : null);
            if (onBox) {
                return loadoutNameBox.mouseClicked(mouseX, mouseY, button);
            }
        }
        if (hit(CodexLayout.renameButton(), lx, ly)) {
            commitLoadoutName();
            return true;
        }
        int row = CodexLayout.loadoutRowAt(lx, ly);
        if (row >= 0) {
            if (row < ClientMagicState.get().loadouts().size()) {
                press(MagicPyramidMenu.BUTTON_LOADOUT_SELECT_BASE + row);
            }
            return true;
        }
        if (hit(CodexLayout.newButton(), lx, ly)) {
            press(MagicPyramidMenu.BUTTON_LOADOUT_NEW);
            return true;
        }
        if (hit(CodexLayout.deleteButton(), lx, ly)) {
            press(MagicPyramidMenu.BUTTON_LOADOUT_DELETE);
            return true;
        }
        int slot = CodexLayout.keySlotAt(lx, ly);
        if (slot >= 0) {
            if (hit(CodexLayout.bindButton(slot), lx, ly)) {
                press(MagicPyramidMenu.BUTTON_LOADOUT_BIND_BASE + slot);
            } else if (hit(CodexLayout.clearButton(slot), lx, ly)) {
                press(MagicPyramidMenu.BUTTON_LOADOUT_CLEAR_BASE + slot);
            }
        }
        return true;
    }

    private boolean handlePassivesClick(double lx, double ly) {
        PlayerMagicState state = ClientMagicState.get();
        // Walks the exact same row model the draw pass uses, so headers cannot desync the hitboxes.
        for (PassiveEntry entry : visiblePassives(passiveRows(state))) {
            if (!entry.row().isHeader() && hit(CodexLayout.passiveCard(entry.y()), lx, ly)) {
                press(MagicPyramidMenu.BUTTON_PASSIVE_TOGGLE_BASE + entry.row().index());
                return true;
            }
        }
        for (CurseEntry entry : visibleCurses(state)) {
            if (hit(CodexLayout.dispelButton(entry.visibleRow()), lx, ly)) {
                press(MagicPyramidMenu.BUTTON_CURSE_DISPEL_BASE + entry.index());
                return true;
            }
        }
        return true;
    }

    private boolean handleClassesClick(double lx, double ly) {
        PlayerMagicState state = ClientMagicState.get();
        if (state.hasClass(MagicalClasses.BLACKSMITH) && hit(CodexLayout.forgeButton(), lx, ly)) {
            // The server opens the Runeforge menu, which replaces this screen.
            MagicalNetwork.sendOpenForgeRequest();
            return true;
        }
        if (state.hasClass(MagicalClasses.SPELL_CREATOR) && hit(CodexLayout.creatorButton(), lx, ly)) {
            // Its own screen now; it closes this menu itself on the way in.
            SpellCreatorScreen.open(OpenSpellCreatorPayload.TAB_CREATE, null, null);
            return true;
        }
        if (visibleRootClasses(state).isEmpty()) {
            if (!state.hasAnyRootClass() && hit(CodexLayout.chooseClassButton(), lx, ly)) {
                press(MagicPyramidMenu.BUTTON_OPEN_CLASS_SELECT);
            }
            return true;
        }
        if (hit(CodexLayout.classTreeButton(), lx, ly)) {
            press(MagicPyramidMenu.BUTTON_OPEN_CLASS_TREE);
        }
        // Rows are a read-only summary; evolving lives in Paths of Power.
        return true;
    }

    // ---- the pyramid model ----------------------------------------------------------------------

    private static List<MagicSkillDefinition> ownedSkillsForTier(int tier) {
        PlayerMagicState state = ClientMagicState.get();
        List<MagicSkillDefinition> source = tier == AUTHORITY_TIER ? MagicContent.authoritySkills() : MagicContent.skillsForTier(tier);
        return source.stream()
                .filter(skill -> state.hasUnlocked(skill.id()))
                .toList();
    }

    /**
     * The rows below the line are named, not numbered - one layer, one school, one price - so the
     * pyramid reads as four kinds of forbidden magic rather than four negative integers.
     *
     * <p>-5 never reaches here; it has its own authority label. Anything deeper than the four named
     * layers falls back to the old signed number, so a future row can never render a raw key.
     */
    private static Component negativeTierLabel(int tier) {
        int layer = -tier;
        return layer >= 1 && layer <= NAMED_BELOW_LAYERS
                ? Component.translatable("tier.magical.below." + layer)
                : Component.translatable("screen.magical.negative_tier", tier);
    }

    private List<Integer> visiblePyramidTiers() {
        List<Integer> tiers = new ArrayList<>();
        if (belowPyramidOpen) {
            for (int index = 0; index < belowTierCount(); index++) {
                int tier = -1 - index;
                if (tierHasOwnedSkill(tier)) {
                    tiers.add(tier);
                }
            }
            return tiers;
        }
        for (int tier = Math.max(0, MagicContent.maxTier()); tier >= 0; tier--) {
            if (tierHasOwnedSkill(tier)) {
                tiers.add(tier);
            }
        }
        return tiers;
    }

    private int activeTierForCurrentView() {
        if (selectedTierMatchesCurrentView()) {
            return menu.selectedTier();
        }
        List<Integer> tiers = visiblePyramidTiers();
        return tiers.isEmpty() ? Integer.MIN_VALUE : tiers.get(0);
    }

    private boolean selectedTierMatchesCurrentView() {
        int selectedTier = menu.selectedTier();
        return belowPyramidOpen ? selectedTier < 0 && tierHasOwnedSkill(selectedTier) : selectedTier >= 0 && tierHasOwnedSkill(selectedTier);
    }

    private static boolean tierHasOwnedSkill(int tier) {
        return !ownedSkillsForTier(tier).isEmpty();
    }

    private static int tierButtonId(int tier) {
        return tier < 0 ? MagicPyramidMenu.BUTTON_BELOW_TIER_BASE + (-1 - tier) : MagicPyramidMenu.BUTTON_TIER_BASE + tier;
    }

    private static int belowTierCount() {
        int count = Math.max(DEFAULT_BELOW_TIER_COUNT, -Math.min(MagicContent.minTier(), -1));
        return tierHasOwnedSkill(AUTHORITY_TIER) ? Math.max(count, 5) : count;
    }

    // ---- drawing helpers ------------------------------------------------------------------------

    private void button(GuiGraphics g, int x, int y, int width, int height, int color, Component label) {
        MagicalGuiStyle.button(g, font, x, y, width, height, color, label);
    }

    /**
     * A tier name centred in its block: one line when it fits, else the first two lines of the
     * wrapped name. The named layers below zero are long ("Primordial · Eldritch") and the blocks
     * up there are the narrow end of the pyramid, so a single capped line lost the end of the word.
     */
    private void tierLabel(GuiGraphics g, Component name, int centreX, int top, int width, int color) {
        List<FormattedCharSequence> lines = font.split(name, width);
        if (lines.size() <= 1) {
            g.drawCenteredString(font, font.plainSubstrByWidth(name.getString(), width), centreX, top + 6, color);
            return;
        }
        g.drawCenteredString(font, lines.get(0), centreX, top + 2, color);
        g.drawCenteredString(font, lines.get(1), centreX, top + 11, color);
    }

    private static void tierBlock(GuiGraphics g, int x, int y, int width, int height, int color, boolean selected, int accent) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, selected ? MagicalGuiStyle.withAlpha(accent, 0xE6) : 0xFF060A12);
        g.fillGradient(x, y, x + width, y + height, MagicalGuiStyle.brighten(color, 1.28F), MagicalGuiStyle.brighten(color, 0.72F));
        g.fill(x, y, x + width, y + 1, 0x3CFFFFFF);
        g.fill(x, y + height - 1, x + width, y + height, 0x55000000);
        if (selected) {
            g.fill(x, y, x + 3, y + height, accent);
            g.fill(x + width - 3, y, x + width, y + height, accent);
        }
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static float centreX(int x0, Rect rect) {
        return x0 + rect.x() + rect.w() / 2.0F;
    }

    private static float centreY(int y0, Rect rect) {
        return y0 + rect.y() + rect.h() / 2.0F;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int color, int lineHeight) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
        }
        return lines.size() * lineHeight;
    }

    private int wrappedHeight(Component text, int width, int lineHeight) {
        return font.split(text, width).size() * lineHeight;
    }
}
