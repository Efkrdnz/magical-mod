package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.MagicalMod;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Stands vanilla's own layers down under a {@link HudQuiet} screen.
 *
 * <p>{@link HudLayers#gated} keeps this mod's layers off such a screen; this does the same for the
 * hotbar, the crosshair, the bars and the rest of what is said about the player, which would
 * otherwise show through the scrim under the Grimoire's glyphs. What is said about the world
 * stays: chat (a refused save is answered there), titles, the tab list, the debug overlay, the
 * saving indicator.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class HudQuietGate {
    private static final Set<ResourceLocation> STOOD_DOWN = Set.of(
            VanillaGuiLayers.CAMERA_OVERLAYS, VanillaGuiLayers.CROSSHAIR, VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH,
            VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.SPECTATOR_TOOLTIP,
            VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.EFFECTS, VanillaGuiLayers.BOSS_OVERLAY,
            VanillaGuiLayers.SCOREBOARD_SIDEBAR);

    private HudQuietGate() {}

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof HudQuiet && STOOD_DOWN.contains(event.getName())) {
            event.setCanceled(true);
        }
    }
}
