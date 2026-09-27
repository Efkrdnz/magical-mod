package com.efkrdnz.magical.client.screen.sword;

import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.menu.SwordRackMenu;
import com.efkrdnz.magical.magic.sword.rack.SwordRack;
import com.efkrdnz.magical.magic.sword.rack.SwordRackLayout;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * The rack, drawn: twelve sockets on a ring round the stance they fly in.
 *
 * <p>Three kinds of socket and they differ by their rims. <b>Gold</b> is a sword the stance fields
 * - what is racked there is what flies. <b>Slate</b> is a socket the rung has opened that the
 * stance does not reach: it is kept for the stance that does. <b>Dark and dimmed</b> is a socket a
 * higher rung opens; it takes nothing, and gives back anything already in it.
 */
@OnlyIn(Dist.CLIENT)
public final class SwordRackScreen extends AbstractContainerScreen<SwordRackMenu> implements HudDebug.Captured {

    private static final int RIM_FIELDED = 0xFFE6C66A;
    private static final int RIM_RESTING = 0xFF5B6A82;
    private static final int RIM_LOCKED = 0xFF262D3B;
    private static final int LOCKED_SHADE = 0xAA05080F;
    private static final int RIM_INVENTORY = 0xFF2A3346;
    private static final int RING_DOT = 0x40E6C66A;
    private static final int GOLD_TEXT = 0xE6C66A;

    /** Dots on the ring between the sockets, every this many degrees. */
    private static final int RING_DOT_STEP = 6;

    public SwordRackScreen(SwordRackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SwordRackLayout.IMAGE_WIDTH;
        imageHeight = SwordRackLayout.IMAGE_HEIGHT;
        titleLabelX = SwordRackLayout.TITLE_X;
        titleLabelY = SwordRackLayout.TITLE_Y;
        inventoryLabelX = SwordRackLayout.INVENTORY_X;
        inventoryLabelY = SwordRackLayout.INVENTORY_LABEL_Y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Slot hovered = hoveredSlot;
        if (hovered != null && hovered.index < SwordRack.SIZE && !hovered.hasItem() && menu.getCarried().isEmpty()) {
            graphics.renderComponentTooltip(font, emptySocketLines(hovered.index), mouseX, mouseY);
            return;
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        MagicalGuiStyle.panel(graphics, left, top, left + imageWidth, top + imageHeight, MagicalGuiStyle.ACCENT_GOLD);
        for (int degrees = 0; degrees < 360; degrees += RING_DOT_STEP) {
            double radians = Math.toRadians(degrees);
            int x = left + (int) Math.round(SwordRackLayout.RING_X + SwordRackLayout.RING_RADIUS * Math.cos(radians));
            int y = top + (int) Math.round(SwordRackLayout.RING_Y + SwordRackLayout.RING_RADIUS * Math.sin(radians));
            graphics.fill(x, y, x + 1, y + 1, RING_DOT);
        }
        for (int socket = 0; socket < SwordRack.SIZE; socket++) {
            int x = left + SwordRackLayout.socketX(socket);
            int y = top + SwordRackLayout.socketY(socket);
            MagicalGuiStyle.slot(graphics, x, y, rim(socket));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                MagicalGuiStyle.slot(graphics, left + SwordRackLayout.INVENTORY_X + column * 18,
                        top + SwordRackLayout.INVENTORY_Y + row * 18, RIM_INVENTORY);
            }
        }
        for (int column = 0; column < 9; column++) {
            MagicalGuiStyle.slot(graphics, left + SwordRackLayout.INVENTORY_X + column * 18,
                    top + SwordRackLayout.HOTBAR_Y, RIM_INVENTORY);
        }
    }

    /** The shade over a locked socket goes over its item too, so it is drawn after the slots. */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int socket = menu.unlocked(); socket < SwordRack.SIZE; socket++) {
            int x = SwordRackLayout.socketX(socket);
            int y = SwordRackLayout.socketY(socket);
            graphics.fill(x, y, x + SwordRackLayout.ITEM, y + SwordRackLayout.ITEM, LOCKED_SHADE);
        }
        graphics.drawString(font, title, titleLabelX, titleLabelY, MagicalGuiStyle.TEXT_PRIMARY, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                MagicalGuiStyle.TEXT_MUTED, false);
        SwordStance stance = SwordStance.byOrdinal(menu.stanceOrdinal());
        centred(graphics, Component.translatable(stance.nameKey()), SwordRackLayout.STANCE_LINE_Y, GOLD_TEXT);
        centred(graphics, Component.translatable("screen.magical.sword_rack.flying", menu.fielded(), menu.unlocked()),
                SwordRackLayout.COUNT_LINE_Y, MagicalGuiStyle.TEXT_MUTED);
    }

    private void centred(GuiGraphics graphics, Component text, int y, int color) {
        List<FormattedCharSequence> lines = font.split(text, SwordRackLayout.CENTRE_TEXT_WIDTH);
        if (lines.isEmpty()) {
            return;
        }
        FormattedCharSequence line = lines.get(0);
        int x = SwordRackLayout.RING_X - font.width(line) / 2;
        graphics.drawString(font, line, x, y, color, false);
    }

    private int rim(int socket) {
        if (socket >= menu.unlocked()) {
            return RIM_LOCKED;
        }
        return socket < menu.fielded() ? RIM_FIELDED : RIM_RESTING;
    }

    private List<Component> emptySocketLines(int socket) {
        List<Component> lines = new ArrayList<>();
        if (socket >= menu.unlocked()) {
            lines.add(Component.translatable("screen.magical.sword_rack.locked"));
            return lines;
        }
        lines.add(Component.translatable(socket < menu.fielded()
                ? "screen.magical.sword_rack.empty" : "screen.magical.sword_rack.resting"));
        lines.add(Component.translatable(menu.weaponGod()
                ? "screen.magical.sword_rack.takes_weapons" : "screen.magical.sword_rack.takes_swords")
                .withStyle(style -> style.withColor(MagicalGuiStyle.TEXT_MUTED)));
        return lines;
    }
}
