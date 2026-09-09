package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicLoadout;
import com.efkrdnz.magical.magic.MagicFusionService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.MagicSkillTuningView;
import com.efkrdnz.magical.magic.MagicTuningStat;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.menu.MagicPyramidMenu;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public final class MagicPyramidScreen extends AbstractContainerScreen<MagicPyramidMenu> {
    /** Display only. The real bindings live in MagicalKeyMappings and the player may rebind them. */
    private static final String[] KEY_NAMES = {"Z", "X", "C", "V"};
    private static final int BG = 0xEE111827;
    private static final int PANEL = 0xFF172033;
    private static final int PANEL_ALT = 0xFF101827;
    private static final int LEFT_PANEL_X = 10;
    private static final int LEFT_PANEL_Y = 30;
    private static final int LEFT_PANEL_W = 142;
    private static final int LEFT_PANEL_H = 286;
    // The loadout panel and the Loadouts tab are positioned by CodexLayout, which is unit-tested
    // for overlap. These aliases keep every existing use site pointed at the tested numbers.
    private static final int LOADOUT_PANEL_X = CodexLayout.PANEL_X;
    private static final int LOADOUT_PANEL_Y = CodexLayout.PANEL_Y;
    private static final int LOADOUT_PANEL_W = CodexLayout.PANEL_W;
    private static final int LOADOUT_PANEL_H = CodexLayout.PANEL_H;
    private static final int DETAIL_PANEL_X = 160;
    private static final int DETAIL_PANEL_Y = 146;
    private static final int DETAIL_PANEL_W = 274;
    private static final int DETAIL_PANEL_H = 170;
    private static final int SKILL_LIST_Y_OFFSET = 112;
    private static final int SKILL_ROW_H = 18;
    private static final int DETAIL_LINE_H = 11;
    private static final int WHEEL_EDITOR_X = CodexLayout.EDITOR_X;
    private static final int WHEEL_EDITOR_Y = CodexLayout.EDITOR_Y;
    private static final int WHEEL_EDITOR_W = CodexLayout.EDITOR_W;
    private static final int WHEEL_EDITOR_H = CodexLayout.EDITOR_H;
    private static final int CLASS_ROW_H = 26;
    private static final int CLASS_ROW_STEP = 30;
    private static final int WHEEL_ROW_H = CodexLayout.LIST_ROW_STRIDE;
    private static final int DEFAULT_BELOW_TIER_COUNT = 3;
    private static final int PYRAMID_ROW_STEP = 29;
    private static final int PYRAMID_MAX_WIDTH = 110;
    private static final int PYRAMID_WIDTH_STEP = 12;

    private int skillListScroll;
    private int detailScroll;
    private int passiveListScroll;
    private int curseListScroll;
    private boolean wheelEditorOpen;
    private boolean classViewOpen;
    private boolean spellCreatorOpen;
    private ResourceLocation fusionFirstInput;
    private ResourceLocation fusionSecondInput;
    private int fusionPickingSlot = -1;
    private int fusionInputScroll;
    private boolean passivesOpen;
    private boolean belowPyramidOpen;

    public MagicPyramidScreen(MagicPyramidMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 444;
        imageHeight = 340;
        inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = 12;
        titleLabelY = 10;
        belowPyramidOpen = menu.selectedTier() < 0;
        // Paths of Power can ask the codex to open straight into a class-specific workshop.
        switch (menu.pendingView()) {
            case 2 -> {
                spellCreatorOpen = true;
                classViewOpen = false;
            }
            default -> { }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        if (spellCreatorOpen) {
            int left = leftPos;
            int top = topPos;
            MagicalGuiStyle.screenBackground(guiGraphics, left, top, left + imageWidth, top + imageHeight);
            drawSpellCreator(guiGraphics);
            return;
        }
        if (wheelEditorOpen || classViewOpen || passivesOpen) {
            int left = leftPos;
            int top = topPos;
            MagicalGuiStyle.screenBackground(guiGraphics, left, top, left + imageWidth, top + imageHeight);
            return;
        }

        int left = leftPos;
        int top = topPos;
        MagicalGuiStyle.screenBackground(guiGraphics, left, top, left + imageWidth, top + imageHeight);
        guiGraphics.fill(left + 10, top + 22, left + imageWidth - 10, top + 23, MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_ARCANE, 0x3A));
        MagicalGuiStyle.panel(guiGraphics, left + LEFT_PANEL_X, top + LEFT_PANEL_Y, left + LEFT_PANEL_X + LEFT_PANEL_W, top + LEFT_PANEL_Y + LEFT_PANEL_H,
                belowPyramidOpen ? MagicalGuiStyle.ACCENT_VIOLET : MagicalGuiStyle.ACCENT_ARCANE);
        MagicalGuiStyle.panel(guiGraphics, left + LOADOUT_PANEL_X, top + LOADOUT_PANEL_Y, left + LOADOUT_PANEL_X + LOADOUT_PANEL_W, top + LOADOUT_PANEL_Y + LOADOUT_PANEL_H, MagicalGuiStyle.ACCENT_GOLD);
        MagicalGuiStyle.panel(guiGraphics, left + DETAIL_PANEL_X, top + DETAIL_PANEL_Y, left + DETAIL_PANEL_X + DETAIL_PANEL_W, top + DETAIL_PANEL_Y + DETAIL_PANEL_H, MagicalGuiStyle.ACCENT_ARCANE);
        drawPyramid(guiGraphics, left + 18, top + 40);
        drawLoadout(guiGraphics, left + CodexLayout.ORIGIN_X, top + CodexLayout.ORIGIN_Y);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        if (spellCreatorOpen) {
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }
        if (classViewOpen) {
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
            drawClassView(guiGraphics);
            renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }
        if (passivesOpen) {
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
            drawPassivesView(guiGraphics, mouseX, mouseY);
            renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }
        if (wheelEditorOpen) {
            renderBg(guiGraphics, partialTick, mouseX, mouseY);
            drawLoadoutEditor(guiGraphics);
            renderTooltip(guiGraphics, mouseX, mouseY);
            return;
        }

        renderBg(guiGraphics, partialTick, mouseX, mouseY);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(leftPos, topPos, 0.0F);
        renderLabels(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();
        drawDetails(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (wheelEditorOpen || classViewOpen || spellCreatorOpen || passivesOpen) {
            return;
        }

        PlayerMagicState state = ClientMagicState.get();
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xF4F8FF, false);
        guiGraphics.drawString(font, font.plainSubstrByWidth(Component.translatable("screen.magical.proficiency", state.proficiencyLevel(), state.proficiencyXp()).getString(), 126), 170, 10, 0xFFD67A, false);
        MagicalGuiStyle.legend(guiGraphics, font, 16, 27, Component.translatable(belowPyramidOpen ? "screen.magical.below_pyramid" : "screen.magical.pyramid"), belowPyramidOpen ? 0xD19BFF : 0xBFD7FF);
        MagicalGuiStyle.legend(guiGraphics, font, 170, 27, Component.translatable("screen.magical.loadout"), 0xBFD7FF);
        MagicalGuiStyle.legend(guiGraphics, font, 170, 143, Component.translatable("screen.magical.skill_detail"), 0xBFD7FF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (spellCreatorOpen) {
            return handleSpellCreatorClick(mouseX, mouseY);
        }
        if (classViewOpen) {
            return handleClassViewClick(mouseX, mouseY);
        }
        if (passivesOpen) {
            return handlePassivesClick(mouseX, mouseY);
        }
        if (wheelEditorOpen) {
            return handleLoadoutEditorClick(mouseX, mouseY);
        }
        if (handlePyramidClick(mouseX, mouseY) || handleLoadoutClick(mouseX, mouseY) || handleDetailClick(mouseX, mouseY)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (wheelEditorOpen) {
            // Every loadout fits on screen at once, so there is nothing to scroll here - but the
            // wheel is still swallowed so it does not move the hotbar behind the open codex.
            return true;
        }
        if (passivesOpen && inside(mouseX, mouseY, leftPos + WHEEL_EDITOR_X + 14, topPos + WHEEL_EDITOR_Y + 38, 188, passiveListHeight())) {
            passiveListScroll = clampScroll(passiveListScroll - (int) Math.signum(scrollY), passiveRows().size(), visiblePassiveRows());
            return true;
        }
        if (passivesOpen && inside(mouseX, mouseY, leftPos + WHEEL_EDITOR_X + 210, topPos + WHEEL_EDITOR_Y + 38, 180, curseListHeight())) {
            curseListScroll = clampScroll(curseListScroll - (int) Math.signum(scrollY), activeCurseCount(), visibleCurseRows());
            return true;
        }
        if (spellCreatorOpen && inside(mouseX, mouseY, fusionListX(), fusionListY(), fusionListW(), fusionListH())) {
            fusionInputScroll = clampScroll(fusionInputScroll - (int) Math.signum(scrollY), MagicFusionService.eligibleInputs(ClientMagicState.get()).size(), visibleFusionInputRows());
            return true;
        }
        if (spellCreatorOpen) {
            return true;
        }
        if (passivesOpen) {
            return true;
        }
        if (inside(mouseX, mouseY, skillListX(), skillListY(), 128, skillListHeight())) {
            skillListScroll = clampScroll(skillListScroll - (int) Math.signum(scrollY), ownedSkillsForTier(activeTierForCurrentView()).size(), visibleSkillRows());
            return true;
        }
        if (inside(mouseX, mouseY, detailX(), detailY(), DETAIL_PANEL_W - 12, DETAIL_PANEL_H - 18)) {
            detailScroll = Math.max(0, detailScroll - (int) Math.signum(scrollY) * 12);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void drawPyramid(GuiGraphics guiGraphics, int left, int top) {
        button(guiGraphics, left + 82, top - 16, 46, 14, belowPyramidOpen ? 0xFF563066 : 0xFF26354A, Component.translatable(belowPyramidOpen ? "screen.magical.above_short" : "screen.magical.below_short"));
        List<Integer> visibleTiers = visiblePyramidTiers();
        if (belowPyramidOpen) {
            for (int row = 0; row < visibleTiers.size(); row++) {
                int tier = visibleTiers.get(row);
                int tierWidth = PYRAMID_MAX_WIDTH - row * PYRAMID_WIDTH_STEP;
                int x = left + (PYRAMID_MAX_WIDTH - tierWidth) / 2;
                int y = top + row * PYRAMID_ROW_STEP;
                boolean authorityTier = tier == -5;
                boolean selected = tier == menu.selectedTier();
                int color = selected
                        ? authorityTier ? 0xFF2E6B82 : 0xFF6A3F84
                        : authorityTier ? 0xFF24465D : 0xFF342143;
                drawTierBlock(guiGraphics, x, y, tierWidth, color, selected, authorityTier ? MagicalGuiStyle.ACCENT_ARCANE : MagicalGuiStyle.ACCENT_VIOLET);
                guiGraphics.drawCenteredString(font, authorityTier ? Component.translatable("authority.magical.authority_of_space") : Component.translatable("screen.magical.negative_tier", tier), x + tierWidth / 2, y + 6, authorityTier ? 0xE5FBFF : 0xF3E7FF);
            }
        } else {
            for (int row = 0; row < visibleTiers.size(); row++) {
                int tier = visibleTiers.get(row);
                int tierWidth = PYRAMID_MAX_WIDTH - (visibleTiers.size() - 1 - row) * PYRAMID_WIDTH_STEP;
                int x = left + (PYRAMID_MAX_WIDTH - tierWidth) / 2;
                int y = top + row * PYRAMID_ROW_STEP;
                boolean selected = tier == menu.selectedTier();
                drawTierBlock(guiGraphics, x, y, tierWidth, selected ? 0xFF2F5F7A : 0xFF23465B, selected, MagicalGuiStyle.ACCENT_ARCANE);
                guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.tier", tier + 1), x + tierWidth / 2, y + 6, 0xECF5FF);
            }
        }

        int activeTier = activeTierForCurrentView();
        List<MagicSkillDefinition> skills = ownedSkillsForTier(activeTier);
        skillListScroll = clampScroll(skillListScroll, skills.size(), visibleSkillRows());
        if (skills.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.no_owned_skills"), skillListX() + 62, skillListY() + 34, 0x8292AB);
            return;
        }
        guiGraphics.enableScissor(skillListX(), skillListY(), skillListX() + 128, skillListY() + skillListHeight());
        for (int row = 0; row < visibleSkillRows(); row++) {
            int index = skillListScroll + row;
            if (index >= skills.size()) {
                break;
            }
            MagicSkillDefinition skill = skills.get(index);
            int y = skillListY() + row * SKILL_ROW_H;
            boolean selected = skill.id().equals(menu.selectedSkillId());
            MagicalGuiStyle.listRow(guiGraphics, skillListX(), y, 124, 15, selected, 0xFF000000 | skill.color());
            boolean bound = ClientMagicState.get().isEquippedAnywhere(skill.id());
            String label = font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), bound ? 104 : 112);
            if (MagicContent.GABRIEL.id().equals(skill.id())) {
                guiGraphics.drawString(font, Component.literal(label).withStyle(ChatFormatting.BOLD), skillListX() + 6, y + 4, 0xFFD700, false);
            } else {
                guiGraphics.drawString(font, label, skillListX() + 6, y + 4, 0xF4F9FF, false);
            }
            if (bound) {
                guiGraphics.drawString(font, "\u25C6", skillListX() + 114, y + 4, 0xFFD67A, false);
            }
        }
        guiGraphics.disableScissor();
        drawScrollbar(guiGraphics, skillListX() + 126, skillListY(), skillListHeight(), skills.size(), visibleSkillRows(), skillListScroll);
    }

    /**
     * The four cast keys of the active loadout, plus the controls that edit them.
     *
     * <p>Every rectangle here comes from {@link CodexLayout}, so the overlap test knows about it.
     * The loadout name gets its own line: a name the player chose needs the panel's full width, and
     * squeezing it into the corner beside the keys is exactly what drove a label through the fourth
     * card when the row grew from three keys to four.
     */
    private void drawLoadout(GuiGraphics guiGraphics, int left, int top) {
        PlayerMagicState state = ClientMagicState.get();
        guiGraphics.drawString(font,
                font.plainSubstrByWidth(
                        Component.translatable("screen.magical.loadout_active", state.activeLoadout().name()).getString(),
                        CodexLayout.NAME_MAX_W),
                left, top + CodexLayout.NAME_DY, 0xFFD67A, false);

        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            int x = left + slot * CodexLayout.CARD_STRIDE;
            int y = top + CodexLayout.CARD_DY;
            int w = CodexLayout.CARD_W;
            int h = CodexLayout.CARD_H;
            boolean selected = slot == menu.selectedSlot();
            ResourceLocation skillId = state.equippedSkill(slot);
            MagicSkillDefinition equipped = skillId == null ? null : MagicContent.get(skillId);
            MagicalGuiStyle.card(guiGraphics, x, y, x + w, y + h, selected ? 0xFF2D5A74 : 0xFF1A2230);
            if (selected) {
                guiGraphics.fill(x - 1, y - 1, x + w + 1, y, MagicalGuiStyle.ACCENT_GOLD);
            }
            guiGraphics.fill(x, y + h - 2, x + w, y + h, equipped == null ? 0xFF0A0F1B : 0xFF000000 | equipped.color());
            // The key, not the slot number: these are the keys the player presses.
            guiGraphics.drawString(font, KEY_NAMES[slot], x + 4, y + 4, 0xFFD67A, false);
            String label = equipped == null ? "-" : Component.translatable(equipped.nameKey()).getString();
            guiGraphics.drawString(font, font.plainSubstrByWidth(label, w - 12), x + 6, y + 14, 0xE7F4FF, false);
        }

        int actionY = top + CodexLayout.ACTION_DY;
        button(guiGraphics, left, actionY, CodexLayout.ACTION_W, CodexLayout.ACTION_H, 0xFF20445B,
                Component.translatable("screen.magical.equip"));
        button(guiGraphics, left + CodexLayout.CLEAR_DX, actionY, CodexLayout.ACTION_W, CodexLayout.ACTION_H,
                0xFF4A2730, Component.translatable("screen.magical.clear_slot"));
        button(guiGraphics, left, top + CodexLayout.TAB_DY, CodexLayout.TAB_W, CodexLayout.TAB_H, 0xFF24384E,
                Component.translatable("screen.magical.loadouts"));
        button(guiGraphics, left + CodexLayout.PASSIVES_DX, top + CodexLayout.HEADER_DY, CodexLayout.PASSIVES_W,
                CodexLayout.ACTION_H, 0xFF234C44, Component.translatable("screen.magical.passives"));
        button(guiGraphics, left + CodexLayout.CLASSES_DX, top + CodexLayout.HEADER_DY, CodexLayout.CLASSES_W,
                CodexLayout.ACTION_H, 0xFF3A2E5A, Component.translatable("screen.magical.classes"));
    }

    private void drawDetails(GuiGraphics guiGraphics) {
        ResourceLocation selectedId = menu.selectedSkillId();
        if (selectedId == null) {
            return;
        }
        PlayerMagicState state = ClientMagicState.get();
        MagicSkillDefinition skill = MagicContent.get(selectedId);
        MagicSkillTuning tuning = state.tuningFor(selectedId);
        MagicSkillResolvedStats stats = skill.resolve(tuning);
        int left = detailX();
        int contentHeight = detailContentHeight(skill);
        if (!selectedTierMatchesCurrentView()) {
            return;
        }
        detailScroll = Math.min(detailScroll, Math.max(0, contentHeight - (DETAIL_PANEL_H - 20)));
        int top = detailY() - detailScroll;

        guiGraphics.enableScissor(detailX(), detailY(), detailX() + DETAIL_PANEL_W - 10, detailY() + DETAIL_PANEL_H - 20);
        guiGraphics.drawString(font, font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), 138), left, top, skill.color(), false);
        guiGraphics.drawString(font, font.plainSubstrByWidth(Component.translatable(skill.school().translationKey()).getString(), 72), left + 166, top, skill.color(), false);
        top += 14;
        top += drawWrapped(guiGraphics, Component.translatable(skill.descriptionKey()), left, top, 248, 0xBFD7FF, DETAIL_LINE_H);
        drawSkillStatLines(guiGraphics, skill, stats, left, top + 5);
        top += 40;
        int budget = state.tuningLimit();
        List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(skill);
        if (!statsOrder.isEmpty()) {
            drawPointCounter(guiGraphics, left + 232, top - 8, tuning.spent(), budget);
        }
        for (int i = 0; i < statsOrder.size(); i++) {
            MagicTuningStat stat = statsOrder.get(i);
            int rowY = top + i * 18;
            guiGraphics.drawString(font, Component.translatable(MagicSkillTuningView.labelKey(skill, stat)), left, rowY, 0xBFD7FF, false);
            int value = tuningValue(tuning, stat);
            drawTuningRowButtons(guiGraphics, left, rowY, value, tuning.spent(), budget, 0xFF27354A);
            guiGraphics.drawCenteredString(font, Integer.toString(value), left + 148, rowY, 0xFFD67A);
        }
        int subskillTop = top + Math.max(1, statsOrder.size()) * 18 + 8;
        if (MagicContent.SOVEREIGN_AEGIS.id().equals(skill.id())) {
            drawSubskillTuning(guiGraphics, state, left, subskillTop,
                    Component.translatable("screen.magical.aegis_commands"), AEGIS_PALETTE, MagicContent.sovereignAegisSubSkills());
        }
        if (MagicContent.GABRIEL.id().equals(skill.id())) {
            drawSubskillTuning(guiGraphics, state, left, subskillTop,
                    Component.translatable("screen.magical.gabriel_commands").withStyle(ChatFormatting.BOLD), GABRIEL_PALETTE, MagicContent.gabrielSubSkills());
        }
        if (MagicContent.BLACK_FLAMES.id().equals(skill.id())) {
            drawSubskillTuning(guiGraphics, state, left, subskillTop,
                    Component.translatable("screen.magical.black_flames_forms"), BLACK_FLAMES_PALETTE, MagicContent.blackFlamesSubSkills());
        }
        if (MagicContent.SPATIAL_ARSENAL.id().equals(skill.id())) {
            drawSubskillTuning(guiGraphics, state, left, subskillTop,
                    Component.translatable("screen.magical.spatial_arsenal_commands"), SPATIAL_PALETTE, MagicContent.spatialArsenalSubSkills());
        }
        if (MagicContent.SOUL_VOW.id().equals(skill.id())) {
            drawSubskillTuning(guiGraphics, state, left, subskillTop,
                    Component.translatable("screen.magical.soul_vow_commands"), SOUL_VOW_PALETTE, MagicContent.soulVowSubSkills());
        }
        guiGraphics.disableScissor();
        drawPixelScrollbar(guiGraphics, detailX() + DETAIL_PANEL_W - 8, detailY(), DETAIL_PANEL_H - 20, contentHeight, DETAIL_PANEL_H - 20, detailScroll);
    }


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

    /** A button the player cannot afford to press. */
    private static final int SPENT_BUTTON = 0xFF1A1F2B;

    /**
     * One family's commands, each with its own point budget.
     *
     * <p>Per sub-skill, deliberately: a family parent is a menu rather than a spell, so the points
     * belong to the commands underneath it and Gabriel's four are budgeted separately.
     */
    private void drawSubskillTuning(GuiGraphics guiGraphics, PlayerMagicState state, int left, int top,
            Component header, SubskillPalette palette, List<MagicSkillDefinition> subSkills) {
        int budget = state.tuningLimit();
        int y = top;
        guiGraphics.drawString(font, header, left, y, palette.header(), false);
        y += 14;
        for (MagicSkillDefinition subSkill : subSkills) {
            MagicSkillTuning tuning = state.tuningFor(subSkill.id());
            List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(subSkill);
            guiGraphics.fill(left - 3, y - 3, left + 236, y + 20 + statsOrder.size() * 18, palette.fill());
            Component name = Component.translatable(subSkill.nameKey());
            guiGraphics.drawString(font, palette.bold() ? name.copy().withStyle(ChatFormatting.BOLD) : name,
                    left, y, subSkill.color(), false);
            drawPointCounter(guiGraphics, left + 232, y, tuning.spent(), budget);
            y += 16;
            for (MagicTuningStat stat : statsOrder) {
                guiGraphics.drawString(font, Component.translatable(MagicSkillTuningView.labelKey(subSkill, stat)), left + 8, y, palette.label(), false);
                int value = tuningValue(tuning, stat);
                drawTuningRowButtons(guiGraphics, left, y, value, tuning.spent(), budget, palette.button());
                guiGraphics.drawCenteredString(font, Integer.toString(value), left + 148, y, palette.value());
                y += 18;
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
    private void drawPointCounter(GuiGraphics guiGraphics, int right, int y, int spent, int budget) {
        Component text = Component.translatable("screen.magical.tuning_points", spent, budget);
        int color = spent >= budget ? 0xFFB86B : 0x8292AB;
        guiGraphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    /**
     * The minus and plus for one stat, dimmed when they would do nothing.
     *
     * <p>A press at the budget is refused by the server, so without this the button looks live and
     * silently does nothing - the wart the per-stat cap used to hide behind.
     */
    private void drawTuningRowButtons(GuiGraphics guiGraphics, int left, int y, int value, int spent, int budget, int color) {
        button(guiGraphics, left + 110, y - 2, 16, 12, value > -budget ? color : SPENT_BUTTON, Component.literal("-"));
        button(guiGraphics, left + 168, y - 2, 16, 12, spent < budget ? color : SPENT_BUTTON, Component.literal("+"));
    }

    /**
     * The Loadouts tab: pick a loadout on the left, edit its four keys on the right.
     *
     * <p>Picking a row switches to that loadout as well as editing it, so a skill bound here is on
     * the keys immediately without closing the codex. The two only disagree when the server refuses
     * the switch under the post-cast lock: the highlight moves, the diamond does not.
     */
    private void drawLoadoutEditor(GuiGraphics guiGraphics) {
        int left = leftPos + CodexLayout.EDITOR_X;
        int top = topPos + CodexLayout.EDITOR_Y;
        PlayerMagicState state = ClientMagicState.get();
        List<MagicLoadout> loadouts = state.loadouts();
        // Clamped against the live client state, not the menu's own: the menu holds the player
        // attachment, which the client never writes.
        int edited = Math.max(0, Math.min(menu.editedLoadout(), loadouts.size() - 1));

        MagicalGuiStyle.panel(guiGraphics, left, top, left + CodexLayout.EDITOR_W, top + CodexLayout.EDITOR_H,
                MagicalGuiStyle.ACCENT_ARCANE);
        MagicalGuiStyle.sectionLabel(guiGraphics, font, left + 10, top + 8,
                Component.translatable("screen.magical.loadout_editor"), 0xF4F8FF);
        button(guiGraphics, left + CodexLayout.BACK_DX, top + CodexLayout.BACK_DY, CodexLayout.BACK_W,
                CodexLayout.BACK_H, 0xFF27354A, Component.translatable("screen.magical.back"));

        // Bind uses whatever is selected back on the main page, so name it - otherwise the button
        // is a press with no visible subject.
        ResourceLocation pickedId = menu.selectedSkillId();
        MagicSkillDefinition picked = pickedId == null ? null : MagicContent.get(pickedId);
        String bindTarget = picked == null
                ? Component.translatable("screen.magical.loadout_bind_none").getString()
                : Component.translatable("screen.magical.loadout_binding",
                        Component.translatable(picked.nameKey()).getString()).getString();
        guiGraphics.drawString(font, font.plainSubstrByWidth(bindTarget, CodexLayout.BIND_TARGET_MAX_W),
                left + CodexLayout.BIND_TARGET_DX, top + CodexLayout.BIND_TARGET_DY,
                picked == null ? 0x8292AB : 0xFFD67A, false);

        int listX = left + CodexLayout.LIST_DX;
        int listY = top + CodexLayout.LIST_DY;
        MagicalGuiStyle.inset(guiGraphics, listX - 4, listY - 4, listX + CodexLayout.LIST_W + 4,
                listY + MagicContent.MAX_LOADOUTS * CodexLayout.LIST_ROW_STRIDE + 4);
        for (int index = 0; index < loadouts.size(); index++) {
            MagicLoadout loadout = loadouts.get(index);
            int y = listY + index * CodexLayout.LIST_ROW_STRIDE;
            boolean isActive = index == state.activeLoadoutIndex();
            MagicalGuiStyle.listRow(guiGraphics, listX, y, CodexLayout.LIST_W, CodexLayout.LIST_ROW_H,
                    index == edited, isActive ? 0xFFF7D774 : 0xFF5FD4FF);
            guiGraphics.drawString(font, font.plainSubstrByWidth(loadout.name(), CodexLayout.LIST_W - 38),
                    listX + 6, y + 4, 0xF4F9FF, false);
            if (isActive) {
                guiGraphics.drawString(font, "\u25C6", listX + CodexLayout.LIST_W - 14, y + 4, 0xFFD67A, false);
            }
        }
        int actionY = top + CodexLayout.LIST_ACTION_DY;
        button(guiGraphics, listX, actionY, CodexLayout.LIST_ACTION_W, CodexLayout.LIST_ACTION_H,
                loadouts.size() >= MagicContent.MAX_LOADOUTS ? 0xFF1A1F2B : 0xFF345C42,
                Component.translatable("screen.magical.loadout_new"));
        button(guiGraphics, left + CodexLayout.DELETE_DX, actionY, CodexLayout.LIST_ACTION_W,
                CodexLayout.LIST_ACTION_H, loadouts.size() <= 1 ? 0xFF1A1F2B : 0xFF4A2730,
                Component.translatable("screen.magical.loadout_delete"));

        int slotX = left + CodexLayout.SLOT_DX;
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            int y = top + CodexLayout.slotY(slot);
            ResourceLocation bound = loadouts.get(edited).slot(slot);
            MagicSkillDefinition skill = bound == null ? null : MagicContent.get(bound);
            MagicalGuiStyle.inset(guiGraphics, slotX, y, slotX + CodexLayout.SLOT_W, y + CodexLayout.SLOT_H);
            guiGraphics.drawString(font, Component.translatable("screen.magical.loadout_key", KEY_NAMES[slot]),
                    slotX + 6, y + 5, 0xFFD67A, false);
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(skill == null ? "-" : Component.translatable(skill.nameKey()).getString(),
                            CodexLayout.SLOT_W - 12),
                    slotX + 6, y + 15, skill == null ? 0x8292AB : 0xE7F4FF, false);
            button(guiGraphics, left + CodexLayout.SLOT_BIND_DX, y + CodexLayout.SLOT_BUTTON_DY,
                    CodexLayout.SLOT_BUTTON_W, CodexLayout.SLOT_BUTTON_H,
                    picked == null ? 0xFF1A1F2B : 0xFF20445B, Component.translatable("screen.magical.loadout_bind"));
            button(guiGraphics, left + CodexLayout.SLOT_CLEAR_DX, y + CodexLayout.SLOT_BUTTON_DY,
                    CodexLayout.SLOT_BUTTON_W, CodexLayout.SLOT_BUTTON_H,
                    bound == null ? 0xFF1A1F2B : 0xFF4A2730, Component.translatable("screen.magical.clear_slot"));
        }
    }

    private void drawClassView(GuiGraphics guiGraphics) {
        int left = leftPos + WHEEL_EDITOR_X;
        int top = topPos + WHEEL_EDITOR_Y;
        PlayerMagicState state = ClientMagicState.get();
        MagicalGuiStyle.panel(guiGraphics, left, top, left + WHEEL_EDITOR_W, top + WHEEL_EDITOR_H, MagicalGuiStyle.ACCENT_VIOLET);
        MagicalGuiStyle.sectionLabel(guiGraphics, font, left + 10, top + 8, Component.translatable("screen.magical.classes"), 0xF4F8FF);
        button(guiGraphics, left + WHEEL_EDITOR_W - 54, top + 6, 44, 16, 0xFF27354A, Component.translatable("screen.magical.back"));

        int listX = left + 14;
        int listY = top + 38;
        List<MagicalClassDefinition> visibleClasses = visibleRootClasses(state);
        if (visibleClasses.isEmpty()) {
            boolean needsStartingClass = !state.hasAnyRootClass();
            int height = needsStartingClass ? 64 : 34;
            MagicalGuiStyle.inset(guiGraphics, listX, listY, listX + 360, listY + height);
            guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.no_classes"), listX + 180, listY + 8, 0x8292AB);
            if (needsStartingClass) {
                guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.class_select_prompt"), listX + 180, listY + 22, 0xBFD7FF);
                button(guiGraphics, listX + 110, listY + 38, 140, 18, 0xFF3B2F5E, Component.translatable("screen.magical.class_select_open"));
            }
            return;
        }
        // The full graph lives in its own screen; this tab stays the summary and the launcher.
        button(guiGraphics, left + WHEEL_EDITOR_W - 168, top + 6, 108, 16, 0xFF3B2F5E,
                Component.translatable("screen.magical.open_class_tree"));
        // A read-only summary of the trees you own. Evolving happens in Paths of Power, which is
        // the only place that can express converging branches.
        for (int index = 0; index < visibleClasses.size(); index++) {
            MagicalClassDefinition definition = visibleClasses.get(index);
            int y = listY + index * CLASS_ROW_STEP;
            MagicalGuiStyle.listRow(guiGraphics, listX, y, 360, CLASS_ROW_H, false, MagicalGuiStyle.ACCENT_VIOLET);
            guiGraphics.drawString(font, Component.translatable(definition.nameKey()), listX + 8, y + 4, 0xF4F9FF, false);
            guiGraphics.drawString(font, Component.translatable("screen.magical.class_xp", state.classXpPool(definition.id())),
                    listX + 8, y + 15, 0xFFD67A, false);
            guiGraphics.drawString(font, Component.translatable("screen.magical.class_owned_nodes",
                            ownedInTree(state, definition.id()), MagicalClasses.treeOf(definition.id()).size()),
                    listX + 250, y + 10, 0xBFD7FF, false);
        }
        drawClassToolButtons(guiGraphics, state, left, top);
    }

    /** The class-specific workshops. They used to hang off the per-class tree page that is now gone. */
    private void drawClassToolButtons(GuiGraphics guiGraphics, PlayerMagicState state, int left, int top) {
        int y = top + WHEEL_EDITOR_H - 26;
        if (state.hasClass(MagicalClasses.BLACKSMITH)) {
            button(guiGraphics, left + 14, y, 92, 18, 0xFF345C42, Component.translatable("screen.magical.open_forge"));
        }
        if (state.hasClass(MagicalClasses.SPELL_CREATOR)) {
            button(guiGraphics, left + 112, y, 116, 18, 0xFF3F315C, Component.translatable("screen.magical.open_spell_creator"));
        }
    }

    private int ownedInTree(PlayerMagicState state, ResourceLocation baseId) {
        int owned = 0;
        for (MagicalClassDefinition definition : MagicalClasses.treeOf(baseId)) {
            if (state.hasClass(definition.id())) {
                owned++;
            }
        }
        return owned;
    }

    /** Row metrics for the passives list; the draw and click passes must agree on both. */
    private static final int PASSIVE_ROW_H = 32;
    private static final int PASSIVE_HEADER_H = 15;

    private void drawPassivesView(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = leftPos + WHEEL_EDITOR_X;
        int top = topPos + WHEEL_EDITOR_Y;
        PlayerMagicState state = ClientMagicState.get();
        MagicPassiveDefinition hoveredPassive = null;
        MagicalGuiStyle.panel(guiGraphics, left, top, left + WHEEL_EDITOR_W, top + WHEEL_EDITOR_H, MagicalGuiStyle.ACCENT_NATURE);
        MagicalGuiStyle.sectionLabel(guiGraphics, font, left + 10, top + 8, Component.translatable("screen.magical.passives"), 0xF4F8FF);
        button(guiGraphics, left + WHEEL_EDITOR_W - 54, top + 6, 44, 16, 0xFF27354A, Component.translatable("screen.magical.back"));

        int passiveX = left + 14;
        int passiveY = top + 38;
        MagicalGuiStyle.sectionLabel(guiGraphics, font, passiveX, passiveY - 12, Component.translatable("screen.magical.passive_skills"), 0xBFD7FF);
        List<PassiveRow> rows = passiveRows();
        passiveListScroll = clampScroll(passiveListScroll, rows.size(), visiblePassiveRows());
        guiGraphics.enableScissor(passiveX, passiveY, passiveX + 184, passiveY + passiveListHeight());
        int rowY = passiveY;
        for (int i = passiveListScroll; i < rows.size(); i++) {
            PassiveRow entry = rows.get(i);
            if (rowY + entry.height() > passiveY + passiveListHeight()) {
                break;
            }
            if (entry.isHeader()) {
                // A thin rule with the source of everything below it, so 69 passives read as groups.
                guiGraphics.fill(passiveX, rowY + PASSIVE_HEADER_H - 3, passiveX + 180, rowY + PASSIVE_HEADER_H - 2, 0xFF2C3B54);
                guiGraphics.drawString(font, entry.header(), passiveX + 2, rowY + 2, 0x7F93B4, false);
                rowY += entry.height();
                continue;
            }
            MagicPassiveDefinition definition = entry.definition();
            boolean enabled = state.isPassiveEnabled(definition.id());
            boolean hovered = inside(mouseX, mouseY, passiveX, rowY, 180, PASSIVE_ROW_H - 4);
            if (hovered) {
                hoveredPassive = definition;
            }
            MagicalGuiStyle.card(guiGraphics, passiveX, rowY, passiveX + 180, rowY + PASSIVE_ROW_H - 4, hovered ? 0xFF24445D : enabled ? 0xFF17332E : 0xFF1B2433);
            guiGraphics.fill(passiveX, rowY + PASSIVE_ROW_H - 6, passiveX + 180, rowY + PASSIVE_ROW_H - 4, 0xFF000000 | definition.color());
            drawCheckbox(guiGraphics, passiveX + 6, rowY + 7, enabled);
            guiGraphics.drawString(font, Component.translatable(definition.nameKey()), passiveX + 24, rowY + 4, enabled ? 0xF4F9FF : 0x8292AB, false);
            guiGraphics.drawString(font, Component.translatable(enabled ? "screen.magical.passive_enabled" : "screen.magical.passive_disabled"), passiveX + 24, rowY + 15, enabled ? 0xA6E3A1 : 0x8292AB, false);
            guiGraphics.drawString(font, Component.translatable("screen.magical.hover_details"), passiveX + 112, rowY + 15, hovered ? 0xF7D774 : 0x6F7F99, false);
            rowY += entry.height();
        }
        guiGraphics.disableScissor();
        drawScrollbar(guiGraphics, passiveX + 184, passiveY, passiveListHeight(), rows.size(), visiblePassiveRows(), passiveListScroll);
        if (rows.isEmpty()) {
            guiGraphics.fill(passiveX, passiveY, passiveX + 180, passiveY + 28, 0xFF172033);
            guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.no_passives"), passiveX + 90, passiveY + 10, 0x8292AB);
        }

        int curseX = left + 210;
        int curseY = top + 38;
        MagicalGuiStyle.sectionLabel(guiGraphics, font, curseX, curseY - 12, Component.translatable("screen.magical.curses"), 0xD66A6A);
        int curseRow = 0;
        int visibleCurseRow = 0;
        int activeCurses = activeCurseCount();
        curseListScroll = clampScroll(curseListScroll, activeCurses, visibleCurseRows());
        guiGraphics.enableScissor(curseX, curseY, curseX + 176, curseY + curseListHeight());
        for (int index = 0; index < MagicPassiveContent.curses().size(); index++) {
            MagicPassiveDefinition definition = MagicPassiveContent.curses().get(index);
            if (!state.hasCurse(definition.id())) {
                continue;
            }
            if (curseRow++ < curseListScroll) {
                continue;
            }
            if (visibleCurseRow >= visibleCurseRows()) {
                break;
            }
            int y = curseY + visibleCurseRow * 48;
            boolean canDispel = state.canDispelCurse(definition.id());
            boolean hovered = inside(mouseX, mouseY, curseX, y, 172, 42);
            if (hovered) {
                hoveredPassive = definition;
            }
            MagicalGuiStyle.card(guiGraphics, curseX, y, curseX + 172, y + 42, hovered ? 0xFF4B254B : 0xFF2D1B2D);
            guiGraphics.fill(curseX, y + 40, curseX + 172, y + 42, 0xFF000000 | definition.color());
            guiGraphics.drawString(font, Component.translatable(definition.nameKey()), curseX + 6, y + 5, 0xF4D6FF, false);
            guiGraphics.drawString(font, Component.translatable("screen.magical.hover_details"), curseX + 6, y + 18, hovered ? 0xF7D774 : 0x9D7BB0, false);
            button(guiGraphics, curseX + 96, y + 22, 66, 15, canDispel ? 0xFF633A6F : 0xFF2A2430, Component.translatable("screen.magical.dispel"));
            guiGraphics.drawString(font, curseRowText(definition, state), curseX + 6, y + 31, canDispel ? 0xA6E3A1 : 0xD66A6A, false);
            visibleCurseRow++;
        }
        guiGraphics.disableScissor();
        drawScrollbar(guiGraphics, curseX + 176, curseY, curseListHeight(), activeCurses, visibleCurseRows(), curseListScroll);
        if (activeCurses == 0) {
            guiGraphics.fill(curseX, curseY, curseX + 172, curseY + 28, 0xFF172033);
            guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.no_curses"), curseX + 86, curseY + 10, 0x8292AB);
        }
        if (hoveredPassive != null) {
            // Raised on z the way vanilla renders tooltips. Row labels drawn above go through the
            // deferred font batch, which otherwise flushes on top of this panel and bleeds through.
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 400.0F);
            drawPassiveTooltip(guiGraphics, hoveredPassive, state, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    private void drawPassiveTooltip(GuiGraphics guiGraphics, MagicPassiveDefinition definition, PlayerMagicState state, int mouseX, int mouseY) {
        PassiveTooltipStyle style = passiveTooltipStyle(definition);
        int width = 230;
        int textWidth = width - 22;
        int descriptionHeight = drawWrappedPreviewHeight(Component.translatable(definition.descriptionKey()), textWidth, 11);
        int extraHeight = definition.curse() ? (state.linkedSinPassiveForCurse(definition.id()) == null ? 34 : 48) : 22;
        int height = Math.max(82, 42 + descriptionHeight + extraHeight);
        int panelLeft = leftPos + WHEEL_EDITOR_X;
        int panelTop = topPos + WHEEL_EDITOR_Y;
        int x = Mth.clamp(mouseX + 14, panelLeft + 8, panelLeft + WHEEL_EDITOR_W - width - 8);
        int y = Mth.clamp(mouseY + 14, panelTop + 8, panelTop + WHEEL_EDITOR_H - height - 8);
        drawPassiveTooltipFrame(guiGraphics, x, y, width, height, style, definition.color());

        int titleColor = switch (style) {
            case CURSE -> 0xF4A6FF;
            case SIN -> 0xFFD166;
            case NORMAL -> 0xF4F9FF;
        };
        guiGraphics.drawString(font, Component.translatable(definition.nameKey()), x + 11, y + 10, titleColor, false);
        // Say which node handed this out, so a passive can always be traced back to its class.
        ResourceLocation grantedBy = MagicalClasses.classGranting(definition.id());
        if (grantedBy != null && MagicalClasses.get(grantedBy) != null) {
            Component from = Component.translatable("screen.magical.class_passive_source",
                    Component.translatable(MagicalClasses.get(grantedBy).nameKey()));
            guiGraphics.drawString(font, from, x + width - 11 - font.width(from), y + 10, 0x8FA6C4, false);
        }
        if (style == PassiveTooltipStyle.SIN) {
            guiGraphics.drawString(font, Component.literal("\u00A7k" + "SIN" + "\u00A7r"), x + width - 38, y + 10, 0xFF4D4D, false);
        } else if (definition.curse()) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.curse_label"), x + width - 48, y + 10, 0xFF5C8A, false);
        }

        int statusColor = definition.curse() ? 0xD66A6A : state.isPassiveEnabled(definition.id()) ? 0xA6E3A1 : 0x8292AB;
        Component status = definition.curse()
                ? Component.translatable("screen.magical.curse_active")
                : Component.translatable(state.isPassiveEnabled(definition.id()) ? "screen.magical.passive_enabled" : "screen.magical.passive_disabled");
        guiGraphics.drawString(font, status, x + 11, y + 25, statusColor, false);
        if (!definition.curse()) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.passive_level", state.passiveLevel(definition.id()), definition.maxLevel()), x + 132, y + 25, 0xFFD67A, false);
        }

        int textY = y + 42;
        textY += drawWrapped(guiGraphics, Component.translatable(definition.descriptionKey()), x + 11, textY, textWidth, style == PassiveTooltipStyle.CURSE ? 0xE6C1F2 : 0xD8E8FF, 11);
        if (definition.curse()) {
            boolean canDispel = state.canDispelCurse(definition.id());
            ResourceLocation linkedSinPassive = state.linkedSinPassiveForCurse(definition.id());
            if (linkedSinPassive != null) {
                MagicPassiveDefinition passive = MagicPassiveContent.get(linkedSinPassive);
                Component passiveName = passive == null ? Component.literal(linkedSinPassive.getPath()) : Component.translatable(passive.nameKey());
                guiGraphics.drawString(font, Component.translatable("screen.magical.sin_curse_requirement", passiveName), x + 11, textY + 8, 0xF7D774, false);
                guiGraphics.drawString(font, curseRowText(definition, state), x + 11, textY + 21, canDispel ? 0xB9F6CA : 0xE6A6A6, false);
                guiGraphics.drawString(font, Component.translatable(canDispel ? "screen.magical.curse_dispel_ready" : "screen.magical.sin_curse_waiting"), x + 11, textY + 34, canDispel ? 0xB9F6CA : 0xE6A6A6, false);
            } else {
                guiGraphics.drawString(font, Component.translatable("screen.magical.dispel_cost", definition.requiredProficiencyToDispel(), definition.dispelManaCost()), x + 11, textY + 8, canDispel ? 0xA6E3A1 : 0xF38BA8, false);
                guiGraphics.drawString(font, Component.translatable(canDispel ? "screen.magical.curse_dispel_ready" : "screen.magical.curse_dispel_blocked"), x + 11, textY + 21, canDispel ? 0xB9F6CA : 0xE6A6A6, false);
            }
        } else {
            guiGraphics.drawString(font, Component.translatable("screen.magical.passive_toggle_hint"), x + 11, textY + 9, 0x8FA6C6, false);
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

    private String formatDurationSeconds(int seconds) {
        int minutes = Math.max(0, seconds) / 60;
        int remainder = Math.max(0, seconds) % 60;
        return minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
    }

    private void drawPassiveTooltipFrame(GuiGraphics guiGraphics, int x, int y, int width, int height, PassiveTooltipStyle style, int accent) {
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
        guiGraphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xDD000000);
        guiGraphics.fill(x, y, x + width, y + height, background);
        guiGraphics.fill(x, y, x + width, y + 2, frame);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, frame);
        guiGraphics.fill(x, y, x + 2, y + height, frame);
        guiGraphics.fill(x + width - 2, y, x + width, y + height, frame);
        guiGraphics.fill(x + 6, y + 36, x + width - 6, y + 37, 0xAA000000 | accent);
        int pulse = (int) ((System.currentTimeMillis() / 90L) % 18L);
        for (int i = 0; i < 5; i++) {
            int stripeY = y + 8 + Math.floorMod(pulse + i * 17, Math.max(1, height - 16));
            int stripeColor = switch (style) {
                case CURSE -> 0x24633A6F;
                case SIN -> 0x2AAC1F24;
                case NORMAL -> 0x2436688E;
            };
            guiGraphics.fill(x + 5 + i * 3, stripeY, x + width - 5 - i * 4, stripeY + 1, stripeColor);
        }
        if (style == PassiveTooltipStyle.SIN) {
            for (int i = 0; i < 6; i++) {
                int glyphX = x + 12 + i * 34;
                int glyphY = y + height - 16 - Math.floorMod(pulse + i * 5, 10);
                guiGraphics.drawString(font, Component.literal("\u00A7k" + "###" + "\u00A7r"), glyphX, glyphY, 0x884E0000, false);
            }
        } else if (style == PassiveTooltipStyle.CURSE) {
            for (int i = 0; i < 6; i++) {
                int crackX = x + 12 + i * 33;
                int crackY = y + 45 + Math.floorMod(pulse + i * 7, Math.max(1, height - 58));
                guiGraphics.fill(crackX, crackY, crackX + 1, crackY + 8, 0x77D66A6A);
                guiGraphics.fill(crackX - 2, crackY + 4, crackX + 3, crackY + 5, 0x55D66A6A);
            }
        }
    }

    private PassiveTooltipStyle passiveTooltipStyle(MagicPassiveDefinition definition) {
        String path = definition.id().getPath();
        if (path.startsWith("sin_") || path.contains("_sin")) {
            return PassiveTooltipStyle.SIN;
        }
        return definition.curse() ? PassiveTooltipStyle.CURSE : PassiveTooltipStyle.NORMAL;
    }

    private enum PassiveTooltipStyle {
        NORMAL,
        CURSE,
        SIN
    }

    private void drawCheckbox(GuiGraphics guiGraphics, int x, int y, boolean checked) {
        MagicalGuiStyle.checkbox(guiGraphics, x, y, checked);
    }

    private void drawSpellCreator(GuiGraphics guiGraphics) {
        int left = leftPos + WHEEL_EDITOR_X;
        int top = topPos + WHEEL_EDITOR_Y;
        PlayerMagicState state = ClientMagicState.get();
        MagicalGuiStyle.panel(guiGraphics, left, top, left + WHEEL_EDITOR_W, top + WHEEL_EDITOR_H, MagicalGuiStyle.ACCENT_VIOLET);
        MagicalGuiStyle.sectionLabel(guiGraphics, font, left + 10, top + 8, Component.translatable("screen.magical.spell_creator"), 0xF4F8FF);
        button(guiGraphics, left + WHEEL_EDITOR_W - 54, top + 6, 44, 16, 0xFF27354A, Component.translatable("screen.magical.back"));
        MagicalGuiStyle.inset(guiGraphics, left + 12, top + 34, left + WHEEL_EDITOR_W - 12, top + 74);
        drawWrapped(guiGraphics, Component.translatable("screen.magical.spell_creator_hint"), left + 22, top + 44, WHEEL_EDITOR_W - 44, 0xBFD7FF, 11);

        int slotY = top + 86;
        drawFusionSlot(guiGraphics, 0, left + 20, slotY, fusionFirstInput);
        guiGraphics.drawCenteredString(font, Component.literal("+"), left + WHEEL_EDITOR_W / 2, slotY + 13, 0xF7D774);
        drawFusionSlot(guiGraphics, 1, left + 216, slotY, fusionSecondInput);

        int resultY = top + 130;
        MagicalGuiStyle.inset(guiGraphics, left + 14, resultY, left + WHEEL_EDITOR_W - 14, resultY + 54);
        MagicFusionService.FusionRecipe recipe = MagicFusionService.recipeFor(fusionFirstInput, fusionSecondInput);
        if (fusionFirstInput == null || fusionSecondInput == null) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.fusion_incomplete"), left + 26, resultY + 10, 0x8292AB, false);
            guiGraphics.drawString(font, Component.translatable("screen.magical.fusion_pick_hint"), left + 26, resultY + 26, 0xBFD7FF, false);
        } else if (recipe == null) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.fusion_no_formula"), left + 26, resultY + 10, 0xD66A6A, false);
            guiGraphics.drawString(font, Component.translatable("screen.magical.fusion_try_other_pair"), left + 26, resultY + 26, 0x8292AB, false);
        } else {
            MagicSkillDefinition output = MagicContent.get(recipe.outputSkill());
            if (output == null) {
                guiGraphics.drawString(font, Component.translatable("screen.magical.fusion_no_formula"), left + 26, resultY + 10, 0xD66A6A, false);
                drawFusionPicker(guiGraphics, left, top);
                return;
            }
            boolean owned = output != null && state.hasUnlocked(output.id());
            boolean canCreate = MagicFusionService.canCreate(state, fusionFirstInput, fusionSecondInput);
            int color = output == null ? 0x8292AB : output.color();
            guiGraphics.fill(left + 14, resultY, left + 18, resultY + 54, 0xFF000000 | color);
            guiGraphics.drawString(font, Component.translatable("screen.magical.fusion_output", Component.translatable(output.nameKey())), left + 26, resultY + 8, owned ? 0xA6E3A1 : 0xF7D774, false);
            guiGraphics.drawString(font, recipe.lossWarning(), left + 26, resultY + 22, recipe.consumesFirstInput() || recipe.consumesSecondInput() ? 0xFFB86C : 0xBFD7FF, false);
            guiGraphics.drawString(font, recipe.requirement(), left + 26, resultY + 36, canCreate || owned ? 0x8292AB : 0xD66A6A, false);
            button(guiGraphics, left + WHEEL_EDITOR_W - 96, resultY + 18, 70, 18, owned ? 0xFF27354A : canCreate ? 0xFF6A3F84 : 0xFF4A2730, Component.translatable(owned ? "screen.magical.created" : "screen.magical.create"));
        }

        drawFusionPicker(guiGraphics, left, top);
    }

    private void drawFusionSlot(GuiGraphics guiGraphics, int slot, int x, int y, ResourceLocation skillId) {
        boolean picking = fusionPickingSlot == slot;
        guiGraphics.fill(x - 1, y - 1, x + 161, y + 33, picking ? MagicalGuiStyle.ACCENT_ARCANE : 0xFF060A12);
        guiGraphics.fill(x, y, x + 160, y + 32, picking ? 0xFF315A74 : 0xFF1A2230);
        guiGraphics.fillGradient(x + 2, y + 2, x + 158, y + 30, 0xFF0D1424, 0xFF101B2E);
        guiGraphics.drawString(font, Component.translatable(slot == 0 ? "screen.magical.fusion_slot_one" : "screen.magical.fusion_slot_two"), x + 8, y + 5, 0xBFD7FF, false);
        MagicSkillDefinition skill = MagicContent.get(skillId);
        Component label = skill == null ? Component.translatable("screen.magical.fusion_empty_slot") : Component.translatable(skill.nameKey());
        int color = skill == null ? 0x8292AB : skill.color();
        guiGraphics.drawString(font, font.plainSubstrByWidth(label.getString(), 132), x + 8, y + 18, color, false);
        if (skill != null) {
            guiGraphics.drawString(font, Component.literal("x"), x + 146, y + 5, 0xD66A6A, false);
        }
    }

    private void drawFusionPicker(GuiGraphics guiGraphics, int left, int top) {
        List<MagicSkillDefinition> inputs = MagicFusionService.eligibleInputs(ClientMagicState.get());
        int listX = left + 14;
        int listY = top + 196;
        int listW = WHEEL_EDITOR_W - 28;
        int listH = 88;
        MagicalGuiStyle.inset(guiGraphics, listX, listY - 18, listX + listW, listY + listH);
        Component title = fusionPickingSlot >= 0
                ? Component.translatable("screen.magical.fusion_select_slot", fusionPickingSlot + 1)
                : Component.translatable("screen.magical.fusion_select_prompt");
        guiGraphics.drawString(font, title, listX + 8, listY - 13, 0xBFD7FF, false);
        if (inputs.isEmpty()) {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.fusion_no_inputs"), listX + listW / 2, listY + 34, 0x8292AB);
            return;
        }
        fusionInputScroll = clampScroll(fusionInputScroll, inputs.size(), visibleFusionInputRows());
        guiGraphics.enableScissor(listX, listY, listX + listW - 8, listY + listH);
        for (int row = 0; row < visibleFusionInputRows(); row++) {
            int index = fusionInputScroll + row;
            if (index >= inputs.size()) {
                break;
            }
            MagicSkillDefinition skill = inputs.get(index);
            int y = listY + row * 17;
            boolean selected = skill.id().equals(fusionFirstInput) || skill.id().equals(fusionSecondInput);
            MagicalGuiStyle.listRow(guiGraphics, listX + 4, y, listW - 18, 14, selected, 0xFF000000 | skill.color());
            guiGraphics.drawString(font, font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), 180), listX + 12, y + 3, 0xF4F9FF, false);
            guiGraphics.drawString(font, Component.translatable(skill.school().translationKey()), listX + 230, y + 3, skill.color(), false);
            guiGraphics.drawString(font, Component.translatable("screen.magical.tier", skill.tier() + 1), listX + 306, y + 3, 0x8292AB, false);
        }
        guiGraphics.disableScissor();
        drawScrollbar(guiGraphics, listX + listW - 8, listY, listH, inputs.size(), visibleFusionInputRows(), fusionInputScroll);
    }

    private boolean handlePyramidClick(double mouseX, double mouseY) {
        int left = leftPos + 18;
        int top = topPos + 40;
        if (inside(mouseX, mouseY, left + 82, top - 16, 46, 14)) {
            belowPyramidOpen = !belowPyramidOpen;
            detailScroll = 0;
            skillListScroll = 0;
            List<Integer> tiers = visiblePyramidTiers();
            if (!tiers.isEmpty()) {
                press(tierButtonId(tiers.get(0)));
            }
            return true;
        }
        List<Integer> visibleTiers = visiblePyramidTiers();
        for (int row = 0; row < visibleTiers.size(); row++) {
            int tierWidth = belowPyramidOpen
                    ? PYRAMID_MAX_WIDTH - row * PYRAMID_WIDTH_STEP
                    : PYRAMID_MAX_WIDTH - (visibleTiers.size() - 1 - row) * PYRAMID_WIDTH_STEP;
            int x = left + (PYRAMID_MAX_WIDTH - tierWidth) / 2;
            int y = top + row * PYRAMID_ROW_STEP;
            if (inside(mouseX, mouseY, x, y, tierWidth, 21)) {
                detailScroll = 0;
                skillListScroll = 0;
                press(tierButtonId(visibleTiers.get(row)));
                return true;
            }
        }
        List<MagicSkillDefinition> skills = ownedSkillsForTier(activeTierForCurrentView());
        for (int row = 0; row < visibleSkillRows(); row++) {
            int index = skillListScroll + row;
            if (index >= skills.size()) {
                break;
            }
            int y = skillListY() + row * SKILL_ROW_H;
            if (inside(mouseX, mouseY, skillListX(), y, 124, 15)) {
                detailScroll = 0;
                press(MagicPyramidMenu.BUTTON_SKILL_BASE + MagicContent.skillIndex(skills.get(index).id()));
                return true;
            }
        }
        return false;
    }

    private boolean handleLoadoutClick(double mouseX, double mouseY) {
        int left = leftPos + CodexLayout.ORIGIN_X;
        int top = topPos + CodexLayout.ORIGIN_Y;
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            if (inside(mouseX, mouseY, left + slot * CodexLayout.CARD_STRIDE, top + CodexLayout.CARD_DY,
                    CodexLayout.CARD_W, CodexLayout.CARD_H)) {
                press(MagicPyramidMenu.BUTTON_SLOT_BASE + slot);
                return true;
            }
        }
        if (inside(mouseX, mouseY, left, top + CodexLayout.ACTION_DY, CodexLayout.ACTION_W, CodexLayout.ACTION_H)) {
            press(MagicPyramidMenu.BUTTON_EQUIP_SELECTED);
            return true;
        }
        if (inside(mouseX, mouseY, left + CodexLayout.CLEAR_DX, top + CodexLayout.ACTION_DY,
                CodexLayout.ACTION_W, CodexLayout.ACTION_H)) {
            press(MagicPyramidMenu.BUTTON_CLEAR_SLOT);
            return true;
        }
        if (inside(mouseX, mouseY, left, top + CodexLayout.TAB_DY, CodexLayout.TAB_W, CodexLayout.TAB_H)) {
            wheelEditorOpen = true;
            return true;
        }
        if (inside(mouseX, mouseY, left + CodexLayout.PASSIVES_DX, top + CodexLayout.HEADER_DY,
                CodexLayout.PASSIVES_W, CodexLayout.ACTION_H)) {
            passivesOpen = true;
            return true;
        }
        if (inside(mouseX, mouseY, left + CodexLayout.CLASSES_DX, top + CodexLayout.HEADER_DY,
                CodexLayout.CLASSES_W, CodexLayout.ACTION_H)) {
            classViewOpen = true;
            return true;
        }
        return false;
    }

    private boolean handleDetailClick(double mouseX, double mouseY) {
        ResourceLocation selectedId = menu.selectedSkillId();
        if (selectedId == null || !selectedTierMatchesCurrentView()) {
            return false;
        }
        if (!inside(mouseX, mouseY, detailX(), detailY(), DETAIL_PANEL_W - 12, DETAIL_PANEL_H - 18)) {
            return false;
        }
        MagicSkillDefinition skill = MagicContent.get(selectedId);
        if (MagicContent.SOVEREIGN_AEGIS.id().equals(selectedId)) {
            return handleParentSubskillTuningClick(mouseX, mouseY, skill, MagicContent.sovereignAegisSubSkills());
        }
        if (MagicContent.GABRIEL.id().equals(selectedId)) {
            return handleParentSubskillTuningClick(mouseX, mouseY, skill, MagicContent.gabrielSubSkills());
        }
        if (MagicContent.BLACK_FLAMES.id().equals(selectedId)) {
            return handleParentSubskillTuningClick(mouseX, mouseY, skill, MagicContent.blackFlamesSubSkills());
        }
        if (MagicContent.SPATIAL_ARSENAL.id().equals(selectedId)) {
            return handleParentSubskillTuningClick(mouseX, mouseY, skill, MagicContent.spatialArsenalSubSkills());
        }
        if (MagicContent.SOUL_VOW.id().equals(selectedId)) {
            return handleParentSubskillTuningClick(mouseX, mouseY, skill, MagicContent.soulVowSubSkills());
        }
        int top = detailY() - detailScroll + 14 + drawWrappedPreviewHeight(Component.translatable(skill.descriptionKey()), 248, DETAIL_LINE_H) + 40;
        List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(skill);
        for (int i = 0; i < statsOrder.size(); i++) {
            MagicTuningStat stat = statsOrder.get(i);
            int rowY = top + i * 18;
            if (inside(mouseX, mouseY, detailX() + 110, rowY - 2, 16, 12)) {
                press(MagicPyramidMenu.BUTTON_TUNE_BASE + stat.ordinal() * 10);
                return true;
            }
            if (inside(mouseX, mouseY, detailX() + 168, rowY - 2, 16, 12)) {
                press(MagicPyramidMenu.BUTTON_TUNE_BASE + stat.ordinal() * 10 + 1);
                return true;
            }
        }
        return false;
    }

    private boolean handleParentSubskillTuningClick(double mouseX, double mouseY, MagicSkillDefinition parentSkill, List<MagicSkillDefinition> subSkills) {
        int y = detailY() - detailScroll + 14 + drawWrappedPreviewHeight(Component.translatable(parentSkill.descriptionKey()), 248, DETAIL_LINE_H) + 40;
        y += Math.max(1, MagicSkillTuningView.statsFor(parentSkill).size()) * 18 + 8 + 14;
        for (int subIndex = 0; subIndex < subSkills.size(); subIndex++) {
            MagicSkillDefinition subSkill = subSkills.get(subIndex);
            y += 16;
            List<MagicTuningStat> statsOrder = MagicSkillTuningView.statsFor(subSkill);
            for (MagicTuningStat stat : statsOrder) {
                if (inside(mouseX, mouseY, detailX() + 110, y - 2, 16, 12)) {
                    press(MagicPyramidMenu.BUTTON_AEGIS_TUNE_BASE + subIndex * MagicTuningStat.values().length * 10 + stat.ordinal() * 10);
                    return true;
                }
                if (inside(mouseX, mouseY, detailX() + 168, y - 2, 16, 12)) {
                    press(MagicPyramidMenu.BUTTON_AEGIS_TUNE_BASE + subIndex * MagicTuningStat.values().length * 10 + stat.ordinal() * 10 + 1);
                    return true;
                }
                y += 18;
            }
            y += 8;
        }
        return false;
    }

    private boolean handleLoadoutEditorClick(double mouseX, double mouseY) {
        int left = leftPos + CodexLayout.EDITOR_X;
        int top = topPos + CodexLayout.EDITOR_Y;
        if (inside(mouseX, mouseY, left + CodexLayout.BACK_DX, top + CodexLayout.BACK_DY,
                CodexLayout.BACK_W, CodexLayout.BACK_H)) {
            wheelEditorOpen = false;
            return true;
        }
        int listX = left + CodexLayout.LIST_DX;
        int listY = top + CodexLayout.LIST_DY;
        List<MagicLoadout> loadouts = ClientMagicState.get().loadouts();
        for (int index = 0; index < loadouts.size(); index++) {
            if (inside(mouseX, mouseY, listX, listY + index * CodexLayout.LIST_ROW_STRIDE,
                    CodexLayout.LIST_W, CodexLayout.LIST_ROW_H)) {
                press(MagicPyramidMenu.BUTTON_LOADOUT_SELECT_BASE + index);
                return true;
            }
        }
        int actionY = top + CodexLayout.LIST_ACTION_DY;
        if (inside(mouseX, mouseY, listX, actionY, CodexLayout.LIST_ACTION_W, CodexLayout.LIST_ACTION_H)) {
            press(MagicPyramidMenu.BUTTON_LOADOUT_NEW);
            return true;
        }
        if (inside(mouseX, mouseY, left + CodexLayout.DELETE_DX, actionY,
                CodexLayout.LIST_ACTION_W, CodexLayout.LIST_ACTION_H)) {
            press(MagicPyramidMenu.BUTTON_LOADOUT_DELETE);
            return true;
        }
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            int y = top + CodexLayout.slotY(slot) + CodexLayout.SLOT_BUTTON_DY;
            if (inside(mouseX, mouseY, left + CodexLayout.SLOT_BIND_DX, y,
                    CodexLayout.SLOT_BUTTON_W, CodexLayout.SLOT_BUTTON_H)) {
                press(MagicPyramidMenu.BUTTON_LOADOUT_BIND_BASE + slot);
                return true;
            }
            if (inside(mouseX, mouseY, left + CodexLayout.SLOT_CLEAR_DX, y,
                    CodexLayout.SLOT_BUTTON_W, CodexLayout.SLOT_BUTTON_H)) {
                press(MagicPyramidMenu.BUTTON_LOADOUT_CLEAR_BASE + slot);
                return true;
            }
        }
        return true;
    }

    private boolean handlePassivesClick(double mouseX, double mouseY) {
        int left = leftPos + WHEEL_EDITOR_X;
        int top = topPos + WHEEL_EDITOR_Y;
        if (inside(mouseX, mouseY, left + WHEEL_EDITOR_W - 54, top + 6, 44, 16)) {
            passivesOpen = false;
            return true;
        }

        int passiveX = left + 14;
        int passiveY = top + 38;
        // Walks the exact same row model the draw pass uses, so headers cannot desync the hitboxes.
        List<PassiveRow> rows = passiveRows();
        int y = passiveY;
        for (int i = passiveListScroll; i < rows.size(); i++) {
            PassiveRow entry = rows.get(i);
            if (y + entry.height() > passiveY + passiveListHeight()) {
                break;
            }
            if (!entry.isHeader() && inside(mouseX, mouseY, passiveX, y, 180, PASSIVE_ROW_H - 4)) {
                press(MagicPyramidMenu.BUTTON_PASSIVE_TOGGLE_BASE + entry.index());
                return true;
            }
            y += entry.height();
        }

        int curseX = left + 210;
        int curseY = top + 38;
        int curseRow = 0;
        for (int index = 0; index < MagicPassiveContent.curses().size(); index++) {
            MagicPassiveDefinition definition = MagicPassiveContent.curses().get(index);
            if (!ClientMagicState.get().hasCurse(definition.id())) {
                continue;
            }
            if (curseRow++ < curseListScroll) {
                continue;
            }
            int visibleRow = curseRow - curseListScroll - 1;
            if (visibleRow >= visibleCurseRows()) {
                break;
            }
            if (inside(mouseX, mouseY, curseX + 96, curseY + visibleRow * 48 + 22, 66, 15)) {
                press(MagicPyramidMenu.BUTTON_CURSE_DISPEL_BASE + index);
                return true;
            }
        }
        return true;
    }

    private boolean handleClassViewClick(double mouseX, double mouseY) {
        int left = leftPos + WHEEL_EDITOR_X;
        int top = topPos + WHEEL_EDITOR_Y;
        if (inside(mouseX, mouseY, left + WHEEL_EDITOR_W - 54, top + 6, 44, 16)) {
            classViewOpen = false;
            return true;
        }
        PlayerMagicState clientState = ClientMagicState.get();
        int toolsY = top + WHEEL_EDITOR_H - 26;
        if (clientState.hasClass(MagicalClasses.BLACKSMITH) && inside(mouseX, mouseY, left + 14, toolsY, 92, 18)) {
            // The server opens the Runeforge menu, which replaces this screen.
            MagicalNetwork.sendOpenForgeRequest();
            return true;
        }
        if (clientState.hasClass(MagicalClasses.SPELL_CREATOR) && inside(mouseX, mouseY, left + 112, toolsY, 116, 18)) {
            classViewOpen = false;
            spellCreatorOpen = true;
            return true;
        }
        int listX = left + 14;
        int listY = top + 38;
        List<MagicalClassDefinition> visibleClasses = visibleRootClasses(ClientMagicState.get());
        if (visibleClasses.isEmpty()) {
            if (!ClientMagicState.get().hasAnyRootClass() && inside(mouseX, mouseY, listX + 110, listY + 38, 140, 18)) {
                press(MagicPyramidMenu.BUTTON_OPEN_CLASS_SELECT);
            }
            return true;
        }
        if (inside(mouseX, mouseY, left + WHEEL_EDITOR_W - 168, top + 6, 108, 16)) {
            press(MagicPyramidMenu.BUTTON_OPEN_CLASS_TREE);
            return true;
        }
        // Rows are a read-only summary now; evolving lives in Paths of Power.
        return true;
    }

    private boolean handleSpellCreatorClick(double mouseX, double mouseY) {
        int left = leftPos + WHEEL_EDITOR_X;
        int top = topPos + WHEEL_EDITOR_Y;
        if (inside(mouseX, mouseY, left + WHEEL_EDITOR_W - 54, top + 6, 44, 16)) {
            spellCreatorOpen = false;
            classViewOpen = true;
            fusionPickingSlot = -1;
            return true;
        }
        int slotY = top + 86;
        if (inside(mouseX, mouseY, left + 20, slotY, 160, 32)) {
            if (fusionFirstInput != null && inside(mouseX, mouseY, left + 20 + 140, slotY, 20, 16)) {
                fusionFirstInput = null;
                if (fusionPickingSlot == 0) {
                    fusionPickingSlot = -1;
                }
            } else {
                fusionPickingSlot = 0;
                fusionInputScroll = 0;
            }
            return true;
        }
        if (inside(mouseX, mouseY, left + 216, slotY, 160, 32)) {
            if (fusionSecondInput != null && inside(mouseX, mouseY, left + 216 + 140, slotY, 20, 16)) {
                fusionSecondInput = null;
                if (fusionPickingSlot == 1) {
                    fusionPickingSlot = -1;
                }
            } else {
                fusionPickingSlot = 1;
                fusionInputScroll = 0;
            }
            return true;
        }
        if (fusionPickingSlot >= 0 && inside(mouseX, mouseY, fusionListX(), fusionListY(), fusionListW(), fusionListH())) {
            List<MagicSkillDefinition> inputs = MagicFusionService.eligibleInputs(ClientMagicState.get());
            for (int row = 0; row < visibleFusionInputRows(); row++) {
                int index = fusionInputScroll + row;
                if (index >= inputs.size()) {
                    break;
                }
                int rowY = fusionListY() + row * 17;
                if (inside(mouseX, mouseY, fusionListX() + 4, rowY, fusionListW() - 14, 14)) {
                    if (fusionPickingSlot == 0) {
                        fusionFirstInput = inputs.get(index).id();
                    } else {
                        fusionSecondInput = inputs.get(index).id();
                    }
                    fusionPickingSlot = -1;
                    return true;
                }
            }
            return true;
        }
        MagicFusionService.FusionRecipe recipe = MagicFusionService.recipeFor(fusionFirstInput, fusionSecondInput);
        if (recipe != null && inside(mouseX, mouseY, left + WHEEL_EDITOR_W - 96, top + 148, 70, 18)) {
            if (MagicFusionService.canCreate(ClientMagicState.get(), fusionFirstInput, fusionSecondInput)) {
                int buttonId = fusionCreateButtonId(fusionFirstInput, fusionSecondInput);
                if (buttonId >= 0) {
                    press(buttonId);
                }
                return true;
            }
        }
        return true;
    }

    private void button(GuiGraphics guiGraphics, int x, int y, int width, int height, int color, Component label) {
        MagicalGuiStyle.button(guiGraphics, font, x, y, width, height, color, label);
    }

    private void drawTierBlock(GuiGraphics guiGraphics, int x, int y, int width, int color, boolean selected, int accent) {
        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + 22, selected ? MagicalGuiStyle.withAlpha(accent, 0xE6) : 0xFF060A12);
        guiGraphics.fillGradient(x, y, x + width, y + 21, MagicalGuiStyle.brighten(color, 1.28F), MagicalGuiStyle.brighten(color, 0.72F));
        guiGraphics.fill(x, y, x + width, y + 1, 0x3CFFFFFF);
        guiGraphics.fill(x, y + 20, x + width, y + 21, 0x55000000);
        if (selected) {
            guiGraphics.fill(x, y, x + 3, y + 21, accent);
            guiGraphics.fill(x + width - 3, y, x + width, y + 21, accent);
        }
    }

    private void drawSkillStatLines(GuiGraphics guiGraphics, MagicSkillDefinition skill, MagicSkillResolvedStats stats, int left, int top) {
        if (MagicContent.VAULT_OF_AVARICE.id().equals(skill.id())) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.vault_utility_stats"), left, top, 0xD8E8FF, false);
            return;
        }
        if (MagicContent.CREATE_SUBSPACE.id().equals(skill.id())) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.create_subspace_stats"), left, top, 0xD8E8FF, false);
            guiGraphics.drawString(font, Component.literal("Mana " + stats.manaCost() + "  Cooldown " + stats.cooldownTicks()), left, top + 13, 0xD8E8FF, false);
            return;
        }
        if (MagicContent.MANIPULATE_SPACE.id().equals(skill.id())) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.manipulate_space_stats"), left, top, 0xD8E8FF, false);
            guiGraphics.drawString(font, Component.literal("Mana " + stats.manaCost() + "  Cooldown " + stats.cooldownTicks()), left, top + 13, 0xD8E8FF, false);
            return;
        }
        if (MagicContent.POCKET_DIMENSION.id().equals(skill.id())) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.pocket_dimension_stats"), left, top, 0xD8E8FF, false);
            guiGraphics.drawString(font, Component.literal("Mana " + stats.manaCost() + "  Cooldown " + stats.cooldownTicks()), left, top + 13, 0xD8E8FF, false);
            return;
        }
        if (MagicContent.SPACE_WALKER.id().equals(skill.id())) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_stats"), left, top, 0xD8E8FF, false);
            guiGraphics.drawString(font, Component.literal("Mana " + stats.manaCost() + "+distance  Cooldown " + stats.cooldownTicks() + "+distance"), left, top + 13, 0xD8E8FF, false);
            return;
        }
        if (skill.type() == com.efkrdnz.magical.magic.MagicSkillType.BARRIER) {
            guiGraphics.drawString(font, Component.literal("Barrier " + stats.barrierRestore() + "  Size " + format(stats.size())), left, top, 0xD8E8FF, false);
            guiGraphics.drawString(font, Component.literal("Mana " + stats.manaCost() + "  Cooldown " + stats.cooldownTicks()), left, top + 13, 0xD8E8FF, false);
            return;
        }
        guiGraphics.drawString(font, Component.literal("Dmg " + format(stats.damage()) + "  Spd " + format(stats.speed()) + "  Size " + format(stats.size())), left, top, 0xD8E8FF, false);
        guiGraphics.drawString(font, Component.literal("Mana " + stats.manaCost() + "  Cooldown " + stats.cooldownTicks()), left, top + 13, 0xD8E8FF, false);
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

    private void drawScrollbar(GuiGraphics guiGraphics, int x, int y, int height, int totalRows, int visibleRows, int scroll) {
        MagicalGuiStyle.scrollbar(guiGraphics, x, y, height, totalRows, visibleRows, scroll);
    }

    private void drawPixelScrollbar(GuiGraphics guiGraphics, int x, int y, int height, int totalHeight, int visibleHeight, int scroll) {
        MagicalGuiStyle.scrollbar(guiGraphics, x, y, height, totalHeight, visibleHeight, scroll);
    }

    private List<MagicSkillDefinition> ownedSkillsForTier(int tier) {
        PlayerMagicState state = ClientMagicState.get();
        List<MagicSkillDefinition> source = tier == -5 ? MagicContent.authoritySkills() : MagicContent.skillsForTier(tier);
        return source.stream()
                .filter(skill -> state.hasUnlocked(skill.id()))
                .toList();
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

    private boolean tierHasOwnedSkill(int tier) {
        return !ownedSkillsForTier(tier).isEmpty();
    }

    private int tierButtonId(int tier) {
        return tier < 0 ? MagicPyramidMenu.BUTTON_BELOW_TIER_BASE + (-1 - tier) : MagicPyramidMenu.BUTTON_TIER_BASE + tier;
    }

    private List<MagicalClassDefinition> visibleRootClasses(PlayerMagicState state) {
        return MagicalClasses.roots().stream()
                .filter(definition -> state.hasClass(definition.id()))
                .toList();
    }

    private int detailContentHeight(MagicSkillDefinition skill) {
        if (MagicContent.SOVEREIGN_AEGIS.id().equals(skill.id())) {
            return parentSubskillDetailHeight(skill, MagicContent.sovereignAegisSubSkills());
        }
        if (MagicContent.GABRIEL.id().equals(skill.id())) {
            return parentSubskillDetailHeight(skill, MagicContent.gabrielSubSkills());
        }
        if (MagicContent.BLACK_FLAMES.id().equals(skill.id())) {
            return parentSubskillDetailHeight(skill, MagicContent.blackFlamesSubSkills());
        }
        if (MagicContent.SPATIAL_ARSENAL.id().equals(skill.id())) {
            return parentSubskillDetailHeight(skill, MagicContent.spatialArsenalSubSkills());
        }
        if (MagicContent.SOUL_VOW.id().equals(skill.id())) {
            return parentSubskillDetailHeight(skill, MagicContent.soulVowSubSkills());
        }
        return 14 + drawWrappedPreviewHeight(Component.translatable(skill.descriptionKey()), 248, DETAIL_LINE_H) + 48 + MagicSkillTuningView.statsFor(skill).size() * 18;
    }

    private int parentSubskillDetailHeight(MagicSkillDefinition skill, List<MagicSkillDefinition> subSkills) {
        int height = 14 + drawWrappedPreviewHeight(Component.translatable(skill.descriptionKey()), 248, DETAIL_LINE_H) + 48 + Math.max(1, MagicSkillTuningView.statsFor(skill).size()) * 18 + 22;
        for (MagicSkillDefinition subSkill : subSkills) {
            height += 24 + MagicSkillTuningView.statsFor(subSkill).size() * 18 + 8;
        }
        return height;
    }

    private int clampScroll(int current, int totalRows, int visibleRows) {
        return Mth.clamp(current, 0, Math.max(0, totalRows - visibleRows));
    }

    private int visibleSkillRows() {
        return skillListHeight() / SKILL_ROW_H;
    }

    private int visiblePassiveRows() {
        return passiveListHeight() / PASSIVE_ROW_H;
    }

    /** One line of the passives list: either a group header or an owned passive. */
    private record PassiveRow(Component header, MagicPassiveDefinition definition, int index) {
        boolean isHeader() {
            return header != null;
        }

        int height() {
            return isHeader() ? PASSIVE_HEADER_H : PASSIVE_ROW_H;
        }
    }

    /**
     * The owned passives, grouped by where they came from.
     *
     * <p>Declaration order in {@code MagicPassiveContent} already runs general, then the sins, then
     * the five class lines in tree order, so emitting a header whenever the group changes is enough
     * and there is no separate sort that could fall out of step with it.</p>
     */
    private List<PassiveRow> passiveRows() {
        PlayerMagicState state = ClientMagicState.get();
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

    private String passiveGroupKey(MagicPassiveDefinition definition) {
        if (MagicPassiveContent.isSinPassive(definition.id())) {
            return "sins";
        }
        ResourceLocation source = MagicalClasses.classGranting(definition.id());
        if (source == null || !MagicPassiveContent.isClassPassive(definition.id())) {
            return "general";
        }
        return MagicalClasses.baseOf(source).toString();
    }

    private Component passiveGroupLabel(MagicPassiveDefinition definition, String key) {
        if ("sins".equals(key)) {
            return Component.translatable("screen.magical.passive_group_sins");
        }
        if ("general".equals(key)) {
            return Component.translatable("screen.magical.passive_group_general");
        }
        MagicalClassDefinition base = MagicalClasses.get(MagicalClasses.baseOf(MagicalClasses.classGranting(definition.id())));
        return base == null ? Component.translatable("screen.magical.passive_group_general") : Component.translatable(base.nameKey());
    }

    private int passiveListHeight() {
        return WHEEL_EDITOR_H - 58;
    }

    private int visibleCurseRows() {
        return curseListHeight() / 48;
    }

    private int visibleFusionInputRows() {
        return fusionListH() / 17;
    }

    private int fusionListX() {
        return leftPos + WHEEL_EDITOR_X + 14;
    }

    private int fusionListY() {
        return topPos + WHEEL_EDITOR_Y + 196;
    }

    private int fusionListW() {
        return WHEEL_EDITOR_W - 28;
    }

    private int fusionListH() {
        return 88;
    }

    private int fusionCreateButtonId(ResourceLocation firstInput, ResourceLocation secondInput) {
        int firstIndex = MagicContent.skillIndex(firstInput);
        int secondIndex = MagicContent.skillIndex(secondInput);
        int skillCount = MagicContent.orderedSkillIds().size();
        if (firstIndex < 0 || secondIndex < 0) {
            return -1;
        }
        return MagicPyramidMenu.BUTTON_FUSION_CREATE_BASE + firstIndex * skillCount + secondIndex;
    }

    private int curseListHeight() {
        return WHEEL_EDITOR_H - 58;
    }


    private int activeCurseCount() {
        PlayerMagicState state = ClientMagicState.get();
        int count = 0;
        for (MagicPassiveDefinition definition : MagicPassiveContent.curses()) {
            if (state.hasCurse(definition.id())) {
                count++;
            }
        }
        return count;
    }

    private int belowTierCount() {
        int count = Math.max(DEFAULT_BELOW_TIER_COUNT, -Math.min(MagicContent.minTier(), -1));
        return tierHasOwnedSkill(-5) ? Math.max(count, 5) : count;
    }

    private int skillListX() {
        return leftPos + 18;
    }

    private int skillListY() {
        int pyramidTop = topPos + 40;
        int fixedTop = pyramidTop + SKILL_LIST_Y_OFFSET;
        int pyramidBottom = pyramidTop + visiblePyramidTiers().size() * PYRAMID_ROW_STEP + 8;
        return Math.max(fixedTop, pyramidBottom);
    }

    private int skillListHeight() {
        return Math.max(SKILL_ROW_H, topPos + LEFT_PANEL_Y + LEFT_PANEL_H - skillListY() - 12);
    }

    private int detailX() {
        return leftPos + DETAIL_PANEL_X + 10;
    }

    private int detailY() {
        return topPos + DETAIL_PANEL_Y + 14;
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private int drawWrapped(GuiGraphics guiGraphics, Component text, int x, int y, int width, int color, int lineHeight) {
        List<FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(font, lines.get(i), x, y + i * lineHeight, color, false);
        }
        return lines.size() * lineHeight;
    }

    private int drawWrappedPreviewHeight(Component text, int width, int lineHeight) {
        return font.split(text, width).size() * lineHeight;
    }
}
