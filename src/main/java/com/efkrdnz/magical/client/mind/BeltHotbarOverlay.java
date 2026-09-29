package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.mind.Belt;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The belt of nine lies, drawn where vanilla's hotbar is while Daydreaming, in vanilla's own
 * sprites and at vanilla's own geometry ({@code Gui.renderItemHotbar}), so the hand looks as it
 * always does and only what is in it has changed. The real hotbar and its held-item name stand
 * down meanwhile; hearts, food and experience are other layers and stay.
 *
 * <p>The icons are pictures ({@link LieIcons}) and are never anything but drawn.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class BeltHotbarOverlay {
    private static final ResourceLocation HOTBAR = ResourceLocation.withDefaultNamespace("hud/hotbar");
    private static final ResourceLocation SELECTION = ResourceLocation.withDefaultNamespace("hud/hotbar_selection");
    /** How long a lie's name stays up after the selection moves, as vanilla holds a held item's. */
    private static final int NAME_TICKS = 40;
    /** The last this many of them it fades over. */
    private static final int NAME_FADE = 10;
    /** Vanilla's lowest line for the held item's name, measured up from the bottom of the gui. */
    private static final int NAME_FLOOR = 59;
    /** How far vanilla drops that name when there are no hearts under it to clear. */
    private static final int NAME_DROP_WITHOUT_HEARTS = 14;
    /** Under this alpha a string is drawn opaque, not faint, so it is not drawn at all. */
    private static final int MIN_ALPHA = 4;

    private static int nameTimer;

    private BeltHotbarOverlay() {}

    /** The lie in hand changed: show its name again, as vanilla does for a new held item. */
    public static void selectionChanged() {
        nameTimer = NAME_TICKS;
    }

    private static boolean showing(Minecraft minecraft) {
        return DaydreamMode.active() && minecraft.screen == null;
    }

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (showing(Minecraft.getInstance())
                && (VanillaGuiLayers.HOTBAR.equals(event.getName()) || VanillaGuiLayers.SELECTED_ITEM_NAME.equals(event.getName()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (nameTimer > 0) {
            nameTimer--;
        }
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!showing(minecraft)) {
            return;
        }
        Belt belt = ClientMagicState.get().mind().belt();
        int selected = DaydreamMode.selected();
        int centre = graphics.guiWidth() / 2;
        int bottom = graphics.guiHeight();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, -90.0F);
        graphics.blitSprite(RenderType::guiTextured, HOTBAR, centre - 91, bottom - 22, 182, 22);
        graphics.blitSprite(RenderType::guiTextured, SELECTION, centre - 91 - 1 + selected * 20, bottom - 22 - 1, 24, 23);
        graphics.pose().popPose();

        for (int i = 0; i < Belt.SIZE; i++) {
            ItemStack icon = LieIcons.stack(belt.get(i));
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, centre - 90 + i * 20 + 2, bottom - 16 - 3);
            }
        }
        renderName(graphics, minecraft, belt.get(selected));
    }

    /** Vanilla's {@code Gui.renderSelectedItemName}, for the lie in hand: same place, same fade, same shadow. */
    private static void renderName(GuiGraphics graphics, Minecraft minecraft, String key) {
        if (nameTimer <= 0 || key == null) {
            return;
        }
        int alpha = Math.min(255, (int) (nameTimer * 256.0F / NAME_FADE));
        if (alpha < MIN_ALPHA) {
            return;
        }
        Component name = MindGazeService.displayName(key);
        int width = minecraft.font.width(name);
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - Math.max(Math.max(minecraft.gui.leftHeight, minecraft.gui.rightHeight), NAME_FLOOR);
        if (minecraft.gameMode != null && !minecraft.gameMode.canHurtPlayer()) {
            y += NAME_DROP_WITHOUT_HEARTS;
        }
        graphics.drawStringWithBackdrop(minecraft.font, name, x, y, width, ARGB.color(alpha, -1));
    }
}
