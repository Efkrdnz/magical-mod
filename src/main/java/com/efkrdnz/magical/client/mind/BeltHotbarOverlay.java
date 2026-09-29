package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.mind.Belt;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.HashMap;
import java.util.Map;

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
    private static final ResourceLocation OFFHAND_LEFT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final ResourceLocation OFFHAND_RIGHT = ResourceLocation.withDefaultNamespace("hud/hotbar_offhand_right");
    private static final ResourceLocation ATTACK_BACKGROUND = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_background");
    private static final ResourceLocation ATTACK_PROGRESS = ResourceLocation.withDefaultNamespace("hud/hotbar_attack_indicator_progress");
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
    /** Icons by key, so a frame builds no stacks; dropped whenever the level changes, which a resource reload also goes through. */
    private static final Map<String, ItemStack> ICONS = new HashMap<>();
    private static Level iconsFor;

    private BeltHotbarOverlay() {}

    /** The lie in hand changed: show its name again, as vanilla does for a new held item. */
    public static void selectionChanged() {
        nameTimer = NAME_TICKS;
    }

    /**
     * Whether the belt is in the hand: Daydreaming, no screen open, and the local player is who the
     * camera is and not a spectator, whose hotbar is the spectator menu. The one test the layer
     * cancel, the drawing and the number-key drain all ask, so they can never disagree.
     */
    public static boolean showing(Minecraft minecraft) {
        Player player = minecraft.player;
        return DaydreamMode.active() && minecraft.screen == null && player != null
                && !player.isSpectator() && minecraft.getCameraEntity() == player;
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
        Player player = minecraft.player;
        Belt belt = ClientMagicState.get().mind().belt();
        int selected = DaydreamMode.selected();
        int centre = graphics.guiWidth() / 2;
        int bottom = graphics.guiHeight();
        ItemStack offhand = player.getOffhandItem();
        HumanoidArm offArm = player.getMainArm().getOpposite();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, -90.0F);
        graphics.blitSprite(RenderType::guiTextured, HOTBAR, centre - 91, bottom - 22, 182, 22);
        graphics.blitSprite(RenderType::guiTextured, SELECTION, centre - 91 - 1 + selected * 20, bottom - 22 - 1, 24, 23);
        if (!offhand.isEmpty()) {
            if (offArm == HumanoidArm.LEFT) {
                graphics.blitSprite(RenderType::guiTextured, OFFHAND_LEFT, centre - 91 - 29, bottom - 23, 29, 24);
            } else {
                graphics.blitSprite(RenderType::guiTextured, OFFHAND_RIGHT, centre + 91, bottom - 23, 29, 24);
            }
        }
        graphics.pose().popPose();

        for (int i = 0; i < Belt.SIZE; i++) {
            ItemStack icon = icon(minecraft, belt.get(i));
            if (!icon.isEmpty()) {
                graphics.renderItem(icon, centre - 90 + i * 20 + 2, bottom - 16 - 3);
            }
        }
        if (!offhand.isEmpty()) {
            int x = offArm == HumanoidArm.LEFT ? centre - 91 - 26 : centre + 91 + 10;
            renderRealSlot(graphics, minecraft, player, offhand, x, bottom - 16 - 3);
        }
        renderAttackIndicator(graphics, minecraft, player, offArm, centre, bottom);
        renderName(graphics, minecraft, belt.get(selected));
    }

    private static ItemStack icon(Minecraft minecraft, String key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        if (iconsFor != minecraft.level) {
            ICONS.clear();
            iconsFor = minecraft.level;
        }
        return ICONS.computeIfAbsent(key, LieIcons::stack);
    }

    /** Vanilla's {@code Gui.renderSlot}, for the offhand: the player's real item, its pop and its decorations. */
    private static void renderRealSlot(GuiGraphics graphics, Minecraft minecraft, Player player, ItemStack stack, int x, int y) {
        float pop = stack.getPopTime() - minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (pop > 0.0F) {
            float squash = 1.0F + pop / 5.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(x + 8, y + 12, 0.0F);
            graphics.pose().scale(1.0F / squash, (squash + 1.0F) / 2.0F, 1.0F);
            graphics.pose().translate(-(x + 8), -(y + 12), 0.0F);
        }
        // Vanilla seeds the offhand one past the nine hotbar slots.
        graphics.renderItem(player, stack, x, y, Belt.SIZE + 1);
        if (pop > 0.0F) {
            graphics.pose().popPose();
        }
        graphics.renderItemDecorations(minecraft.font, stack, x, y);
    }

    /** Vanilla's hotbar attack indicator, on the side away from the offhand, when the option puts it there. */
    private static void renderAttackIndicator(GuiGraphics graphics, Minecraft minecraft, Player player, HumanoidArm offArm, int centre, int bottom) {
        if (minecraft.options.attackIndicator().get() != AttackIndicatorStatus.HOTBAR) {
            return;
        }
        float strength = player.getAttackStrengthScale(0.0F);
        if (strength >= 1.0F) {
            return;
        }
        int y = bottom - 20;
        int x = offArm == HumanoidArm.RIGHT ? centre - 91 - 22 : centre + 91 + 6;
        int filled = (int) (strength * 19.0F);
        graphics.blitSprite(RenderType::guiTextured, ATTACK_BACKGROUND, x, y, 18, 18);
        graphics.blitSprite(RenderType::guiTextured, ATTACK_PROGRESS, 18, 18, 0, 18 - filled, x, y + 18 - filled, 18, filled);
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
