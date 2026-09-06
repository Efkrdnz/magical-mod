package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.menu.ClassSelectMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The starting class chooser shown once at first spawn. Deliberately modal: neither Escape nor the
 * inventory key dismiss it, because a player without a class has no progression. The server
 * re-opens it anyway if a client closes the container on its own, so this is convenience rather
 * than the actual guarantee.
 */
public final class ClassSelectScreen extends AbstractContainerScreen<ClassSelectMenu> {
    private static final int CARD_X = 12;
    private static final int CARD_Y = 48;
    private static final int CARD_W = 340;
    private static final int CARD_H = 38;
    private static final int CARD_STEP = 42;
    private static final int[] ACCENTS = {
            MagicalGuiStyle.ACCENT_GOLD,
            MagicalGuiStyle.ACCENT_BLOOD,
            MagicalGuiStyle.ACCENT_NATURE,
            MagicalGuiStyle.ACCENT_ARCANE,
            MagicalGuiStyle.ACCENT_VIOLET};

    public ClassSelectScreen(ClassSelectMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 364;
        imageHeight = 272;
    }

    @Override
    protected void init() {
        super.init();
        // The screen draws its own heading; park both vanilla container labels off-screen.
        titleLabelY = 10000;
        inventoryLabelY = 10000;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // AbstractContainerScreen closes on the inventory key before anything else; swallow it.
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        MagicalGuiStyle.screenBackground(guiGraphics, left, top, left + imageWidth, top + imageHeight);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.class_select"),
                left + imageWidth / 2, top + 14, MagicalGuiStyle.TEXT_PRIMARY);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.class_select_hint"),
                left + imageWidth / 2, top + 28, MagicalGuiStyle.TEXT_MUTED);

        List<MagicalClassDefinition> choices = ClassSelectMenu.choices();
        for (int index = 0; index < choices.size(); index++) {
            MagicalClassDefinition definition = choices.get(index);
            int x = left + CARD_X;
            int y = top + CARD_Y + index * CARD_STEP;
            int accent = ACCENTS[index % ACCENTS.length];
            boolean hovered = inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
            MagicalGuiStyle.listRow(guiGraphics, x, y, CARD_W, CARD_H, hovered, accent);
            guiGraphics.drawString(font, Component.translatable(definition.nameKey()), x + 9, y + 5, accent, false);
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(definition.descriptionKey()).getString(), CARD_W - 18),
                    x + 9, y + 16, MagicalGuiStyle.TEXT_PRIMARY, false);
            String grants = grantsLine(definition);
            if (!grants.isEmpty()) {
                guiGraphics.drawString(font, font.plainSubstrByWidth(grants, CARD_W - 18),
                        x + 9, y + 27, MagicalGuiStyle.TEXT_MUTED, false);
            }
        }
    }

    private String grantsLine(MagicalClassDefinition definition) {
        List<String> names = new ArrayList<>();
        for (ResourceLocation skillId : definition.rewardSkills()) {
            MagicSkillDefinition skill = MagicContent.get(skillId);
            if (skill != null) {
                names.add(Component.translatable(skill.nameKey()).getString());
            }
        }
        if (names.isEmpty()) {
            return "";
        }
        return Component.translatable("screen.magical.class_select_grants", String.join(", ", names)).getString();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<MagicalClassDefinition> choices = ClassSelectMenu.choices();
        for (int index = 0; index < choices.size(); index++) {
            if (inside(mouseX, mouseY, leftPos + CARD_X, topPos + CARD_Y + index * CARD_STEP, CARD_W, CARD_H)) {
                press(ClassSelectMenu.BUTTON_CHOOSE_BASE + index);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
