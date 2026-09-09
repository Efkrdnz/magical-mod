package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.menu.RaceSelectMenu;
import com.efkrdnz.magical.race.MagicalRace;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The race chooser, shown once at first spawn and ahead of the class chooser.
 *
 * <p>Modal for the same reason its class counterpart is: a character with no race has no awakening
 * waiting behind it. The server re-opens this if a client closes the container itself, so refusing
 * Escape here is a courtesy rather than the guarantee.
 *
 * <p>Every card states what the choice actually costs and pays - the skill you wake up holding, the
 * passive you can never switch off, and the pool bonuses - because this decision cannot be undone
 * and a player has no other screen to compare them on.
 */
public final class RaceSelectScreen extends AbstractContainerScreen<RaceSelectMenu> {
    private static final int CARD_X = 12;
    private static final int CARD_Y = 48;
    private static final int CARD_W = 340;
    private static final int CARD_H = 34;
    private static final int CARD_STEP = 38;

    public RaceSelectScreen(RaceSelectMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 364;
        imageHeight = 292;
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
        guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.race_select"),
                left + imageWidth / 2, top + 14, MagicalGuiStyle.TEXT_PRIMARY);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.race_select_hint"),
                left + imageWidth / 2, top + 28, MagicalGuiStyle.TEXT_MUTED);

        List<MagicalRace> choices = RaceSelectMenu.choices();
        for (int index = 0; index < choices.size(); index++) {
            MagicalRace race = choices.get(index);
            int x = left + CARD_X;
            int y = top + CARD_Y + index * CARD_STEP;
            int accent = race.color();
            boolean hovered = inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
            MagicalGuiStyle.listRow(guiGraphics, x, y, CARD_W, CARD_H, hovered, accent);
            guiGraphics.drawString(font, Component.translatable(race.nameKey()), x + 9, y + 4, accent, false);
            guiGraphics.drawString(font, affinityLine(race), x + CARD_W - 9 - font.width(affinityLine(race)),
                    y + 4, MagicalGuiStyle.TEXT_MUTED, false);
            guiGraphics.drawString(font,
                    font.plainSubstrByWidth(Component.translatable(race.descriptionKey()).getString(), CARD_W - 18),
                    x + 9, y + 14, MagicalGuiStyle.TEXT_PRIMARY, false);
            guiGraphics.drawString(font, font.plainSubstrByWidth(grantsLine(race), CARD_W - 18),
                    x + 9, y + 24, MagicalGuiStyle.TEXT_MUTED, false);
        }
    }

    /** "Earth, Metal" - the design document's word for what a race leans toward. */
    private String affinityLine(MagicalRace race) {
        List<String> names = new ArrayList<>();
        race.affinities().forEach(affinity -> {
            String raw = affinity.name().toLowerCase(Locale.ROOT);
            names.add(Character.toUpperCase(raw.charAt(0)) + raw.substring(1));
        });
        return String.join(", ", names);
    }

    /** The three things a race actually hands over, in one line. */
    private String grantsLine(MagicalRace race) {
        List<String> parts = new ArrayList<>();
        MagicSkillDefinition starter = MagicContent.get(race.starterSkill());
        if (starter != null) {
            parts.add(Component.translatable(starter.nameKey()).getString());
        }
        parts.add(Component.translatable(race.passiveNameKey()).getString());
        if (race.bonusMaxMana() > 0) {
            parts.add("+" + race.bonusMaxMana() + " " + Component.translatable("screen.magical.race_mana").getString());
        }
        if (race.bonusMaxBarrier() > 0) {
            parts.add("+" + race.bonusMaxBarrier() + " " + Component.translatable("screen.magical.race_barrier").getString());
        }
        return String.join("  -  ", parts);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<MagicalRace> choices = RaceSelectMenu.choices();
        for (int index = 0; index < choices.size(); index++) {
            if (inside(mouseX, mouseY, leftPos + CARD_X, topPos + CARD_Y + index * CARD_STEP, CARD_W, CARD_H)) {
                press(RaceSelectMenu.BUTTON_CHOOSE_RACE + index);
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
