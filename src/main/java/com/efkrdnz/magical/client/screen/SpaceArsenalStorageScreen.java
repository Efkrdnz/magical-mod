package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.magic.SpaceArsenalStorage;
import com.efkrdnz.magical.magic.menu.SpaceArsenalStorageMenu;

import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SpaceArsenalStorageScreen extends AbstractContainerScreen<SpaceArsenalStorageMenu> implements MenuAccess<SpaceArsenalStorageMenu> {
    private static final int ROWS = 6;

    public SpaceArsenalStorageScreen(SpaceArsenalStorageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageHeight = 114 + ROWS * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // Drawn with fills rather than a texture, matching the mod's other screens. The 1.21.4
        // blit signature differs from the one this was written against, and a spatial rift should
        // not look like a vanilla chest anyway.
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int storageBottom = y + ROWS * 18 + 17;

        guiGraphics.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF04070C);
        guiGraphics.fill(x, y, x + imageWidth, storageBottom, 0xFF101B27);
        guiGraphics.fill(x, storageBottom, x + imageWidth, y + imageHeight, 0xFF0C1520);

        // Accent rules: top edge, the seam between rift and inventory, and the bottom edge.
        guiGraphics.fill(x, y, x + imageWidth, y + 1, 0xFF63C7FF);
        guiGraphics.fill(x, storageBottom - 1, x + imageWidth, storageBottom, 0x8863C7FF);
        guiGraphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0x5588DFFF);

        // Slot wells, so items read against the flat panel.
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                int slotX = x + 8 + column * 18;
                int slotY = y + 18 + row * 18;
                guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF060B12);
            }
        }
    }

    @Override
    protected void renderSlotContents(GuiGraphics guiGraphics, ItemStack itemStack, Slot slot, @Nullable String countString) {
        String displayCount = countString;
        if (countString == null && slot.index < SpaceArsenalStorage.SLOT_COUNT) {
            int realCount = menu.displayCount(slot.index);
            displayCount = realCount > 1 ? Integer.toString(realCount) : null;
        }

        int x = slot.x;
        int y = slot.y;
        int seed = slot.x + slot.y * imageWidth;
        if (slot.isFake()) {
            guiGraphics.renderFakeItem(itemStack, x, y, seed);
        } else {
            guiGraphics.renderItem(itemStack, x, y, seed);
        }
        guiGraphics.renderItemDecorations(font, itemStack, x, y, displayCount);
    }
}
