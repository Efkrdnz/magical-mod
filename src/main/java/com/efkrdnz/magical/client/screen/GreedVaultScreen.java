package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.menu.GreedVaultMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public final class GreedVaultScreen extends AbstractContainerScreen<GreedVaultMenu> {
    private static final int GOLD = 0xD7F75B;
    private static final int TEXT = 0xF4F9FF;
    private static final int MUTED = 0x93A388;
    private static final int LIST_X = 186;
    private static final int LIST_Y = 48;
    private static final int CARD_W = 170;
    private static final int CARD_H = 24;
    private static final int CARD_STEP = 27;
    private static final int VISIBLE_CONTRACTS = 6;

    private int contractScroll;

    public GreedVaultScreen(GreedVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 380;
        imageHeight = 248;
        inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        // The screen draws its own gold title; park the vanilla container label off-screen.
        titleLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        PlayerMagicState state = ClientMagicState.get();

        guiGraphics.fill(left - 1, top - 1, left + imageWidth + 1, top + imageHeight + 1, 0xFF040703);
        guiGraphics.fillGradient(left, top, left + imageWidth, top + imageHeight, 0xF8101707, 0xF8070B04);
        guiGraphics.fill(left, top, left + imageWidth, top + 1, 0xFF3D4E1E);
        MagicalGuiStyle.sectionLabel(guiGraphics, font, left + 14, top + 9, title, 0xFF000000 | GOLD);

        // --- Left panel: balance and upgrades ---
        greedPanel(guiGraphics, left + 12, top + 26, left + 172, top + 236);
        guiGraphics.drawString(font, Component.translatable("screen.magical.vault_balance", state.manaVault()), left + 20, top + 36, TEXT, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.vault_tier", state.greedShopTier()), left + 20, top + 49, 0xFF000000 | GOLD, false);
        guiGraphics.fill(left + 20, top + 60, left + 164, top + 61, 0x334D5E22);
        guiGraphics.drawString(font, Component.translatable("screen.magical.vault_max_mana", state.maxMana(), state.maxManaBonus()), left + 20, top + 66, 0xD8E8FF, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.vault_max_barrier", state.maxBarrier(), state.maxBarrierBonus()), left + 20, top + 79, 0x7AF1FF, false);
        MagicalGuiStyle.button(guiGraphics, font, left + 20, top + 96, 140, 18,
                state.mana() > 0 ? 0xFF455E1A : 0xFF27301D, Component.translatable("screen.magical.deposit_all"));
        guiGraphics.fill(left + 20, top + 122, left + 164, top + 123, 0x334D5E22);

        int manaPrice = PlayerMagicState.greedUpgradePrice(state.manaBoostPurchases());
        int barrierPrice = PlayerMagicState.greedUpgradePrice(state.barrierBoostPurchases());
        MagicalGuiStyle.button(guiGraphics, font, left + 20, top + 132, 140, 20,
                state.manaVault() >= manaPrice ? 0xFF3E5C1A : 0xFF30291B, Component.translatable("screen.magical.buy_max_mana", manaPrice));
        MagicalGuiStyle.button(guiGraphics, font, left + 20, top + 158, 140, 20,
                state.manaVault() >= barrierPrice ? 0xFF3E5C1A : 0xFF30291B, Component.translatable("screen.magical.buy_max_barrier", barrierPrice));

        // --- Right panel: passive contracts ---
        greedPanel(guiGraphics, left + 180, top + 26, left + 368, top + 236);
        MagicalGuiStyle.sectionLabel(guiGraphics, font, left + 188, top + 33, Component.translatable("screen.magical.vault_passives"), 0xFF000000 | GOLD);

        List<ShopEntry> entries = shopEntries(state);
        contractScroll = Mth.clamp(contractScroll, 0, Math.max(0, entries.size() - VISIBLE_CONTRACTS));
        for (int row = 0; row < VISIBLE_CONTRACTS; row++) {
            int index = contractScroll + row;
            if (index >= entries.size()) {
                break;
            }
            drawContract(guiGraphics, state, entries.get(index), left + LIST_X, top + LIST_Y + row * CARD_STEP);
        }
        MagicalGuiStyle.scrollbar(guiGraphics, left + LIST_X + CARD_W + 3, top + LIST_Y, VISIBLE_CONTRACTS * CARD_STEP - 3,
                entries.size(), VISIBLE_CONTRACTS, contractScroll);
    }

    private void drawContract(GuiGraphics guiGraphics, PlayerMagicState state, ShopEntry entry, int x, int y) {
        MagicPassiveDefinition definition = entry.definition();
        boolean owned = state.hasPassive(definition.id());
        boolean unlockedTier = state.greedShopTier() >= definition.shopTier();
        boolean affordable = state.manaVault() >= definition.shopCost();
        int base = owned ? 0xFF274325 : unlockedTier && affordable ? 0xFF3E5C1A : 0xFF30291B;
        MagicalGuiStyle.card(guiGraphics, x, y, x + CARD_W, y + CARD_H, base);
        int accent = owned ? 0xFFA6E3A1 : unlockedTier ? 0xFF000000 | GOLD : 0xFF6B6455;
        guiGraphics.fill(x, y, x + 2, y + CARD_H, accent);

        String name = font.plainSubstrByWidth(Component.translatable(definition.nameKey()).getString(), CARD_W - 14);
        guiGraphics.drawString(font, name, x + 7, y + 3, owned ? 0xA6E3A1 : TEXT, false);
        if (owned) {
            guiGraphics.drawString(font, Component.translatable("screen.magical.owned"), x + 7, y + 13, 0xA6E3A1, false);
        } else {
            guiGraphics.drawString(font, "T" + definition.shopTier(), x + 7, y + 13, unlockedTier ? 0xFF000000 | GOLD : MUTED, false);
            String cost = Integer.toString(definition.shopCost());
            guiGraphics.drawString(font, cost, x + CARD_W - 8 - font.width(cost), y + 13, affordable && unlockedTier ? 0xFF000000 | GOLD : MUTED, false);
        }
    }

    private void greedPanel(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1) {
        guiGraphics.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF050803);
        guiGraphics.fillGradient(x0, y0, x1, y1, 0xFF1C2710, 0xFF111909);
        guiGraphics.fill(x0, y0, x1, y0 + 1, MagicalGuiStyle.withAlpha(GOLD, 0x66));
        int tick = MagicalGuiStyle.withAlpha(GOLD, 0xBB);
        guiGraphics.fill(x0, y0, x0 + 5, y0 + 1, tick);
        guiGraphics.fill(x0, y0, x0 + 1, y0 + 5, tick);
        guiGraphics.fill(x1 - 5, y0, x1, y0 + 1, tick);
        guiGraphics.fill(x1 - 1, y0, x1, y0 + 5, tick);
        guiGraphics.fill(x0, y1 - 1, x0 + 5, y1, tick);
        guiGraphics.fill(x0, y1 - 5, x0 + 1, y1, tick);
        guiGraphics.fill(x1 - 5, y1 - 1, x1, y1, tick);
        guiGraphics.fill(x1 - 1, y1 - 5, x1, y1, tick);
    }

    private List<ShopEntry> shopEntries(PlayerMagicState state) {
        List<ShopEntry> entries = new ArrayList<>();
        for (int index = 0; index < MagicPassiveContent.normalPassives().size(); index++) {
            MagicPassiveDefinition definition = MagicPassiveContent.normalPassives().get(index);
            if (definition.shopTier() > 0) {
                entries.add(new ShopEntry(index, definition));
            }
        }
        return entries;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y, CARD_W + 6, VISIBLE_CONTRACTS * CARD_STEP)) {
            int total = shopEntries(ClientMagicState.get()).size();
            contractScroll = Mth.clamp(contractScroll - (int) Math.signum(scrollY), 0, Math.max(0, total - VISIBLE_CONTRACTS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = leftPos;
        int top = topPos;
        if (inside(mouseX, mouseY, left + 20, top + 96, 140, 18)) {
            press(GreedVaultMenu.BUTTON_DEPOSIT_ALL);
            return true;
        }
        if (inside(mouseX, mouseY, left + 20, top + 132, 140, 20)) {
            press(GreedVaultMenu.BUTTON_BUY_MAX_MANA);
            return true;
        }
        if (inside(mouseX, mouseY, left + 20, top + 158, 140, 20)) {
            press(GreedVaultMenu.BUTTON_BUY_MAX_BARRIER);
            return true;
        }
        List<ShopEntry> entries = shopEntries(ClientMagicState.get());
        for (int row = 0; row < VISIBLE_CONTRACTS; row++) {
            int index = contractScroll + row;
            if (index >= entries.size()) {
                break;
            }
            if (inside(mouseX, mouseY, left + LIST_X, top + LIST_Y + row * CARD_STEP, CARD_W, CARD_H)) {
                press(GreedVaultMenu.BUTTON_BUY_PASSIVE_BASE + entries.get(index).passiveIndex());
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

    private record ShopEntry(int passiveIndex, MagicPassiveDefinition definition) {}
}
