package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.BlackFlamesInput;
import com.efkrdnz.magical.client.BloodShapeInput;
import com.efkrdnz.magical.client.ClientCounterPrompt;
import com.efkrdnz.magical.client.ClientUnwakingEncounter;
import com.efkrdnz.magical.client.FirstPersonEffects;
import com.efkrdnz.magical.client.ForgeComboHud;
import com.efkrdnz.magical.client.MagicWheelOverlay;
import com.efkrdnz.magical.client.SoulVowInput;
import com.efkrdnz.magical.client.SovereignAegisInput;
import com.efkrdnz.magical.client.SpaceManipulationOverlay;
import com.efkrdnz.magical.client.SpaceOffenseInput;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The magic HUD as named GUI layers, in place of the one {@code RenderGuiEvent.Post} handler that
 * used to call eleven overlays in a hardcoded order.
 *
 * <p>Modded layers are never wrapped by vanilla's {@code hideGui} guard: {@code Gui} applies it to
 * its own two groups only ({@code Gui.java:265}), and {@code RegisterGuiLayersEvent} inserts ours
 * into the same list bare. That is what {@link #FIRST_PERSON} wants - a hit flash should survive
 * F1 - and what every other layer has to do for itself in {@link #gated}.
 *
 * <p>Anchored above the boss overlay, so the HUD draws over the hotbar, the effects and the boss
 * bar, and under the debug overlay, the scoreboard, titles, chat and the tab list. The sigil
 * replaces the old panel; the forged-weapon readout, the counter window and every hold overlay
 * are the originals, drawn where the old handler drew them.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HudLayers {
    public static final ResourceLocation FIRST_PERSON = id("first_person");
    public static final ResourceLocation SIGIL = id("sigil");
    /** The formula that pops when a space rule lands; over the sigil, under the dials that close as it fires. */
    public static final ResourceLocation RULE_FLASH = id("rule_flash");
    public static final ResourceLocation ENCOUNTER = id("encounter");
    public static final ResourceLocation SELECTOR = id("selector");

    private HudLayers() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, FIRST_PERSON, FirstPersonEffects::renderLayer);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, SIGIL, gated("magical_hud_sigil", HudLayers::renderSigil));
        event.registerAbove(SIGIL, RULE_FLASH, gated("magical_hud_rule_flash", RuleFlashRenderer::render));
        event.registerAbove(RULE_FLASH, ENCOUNTER, gated("magical_hud_encounter", HudLayers::renderEncounter));
        event.registerAbove(ENCOUNTER, SELECTOR, gated("magical_hud_selector", HudLayers::renderSelector));
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        onConfig(event);
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        onConfig(event);
    }

    private static void onConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() == MagicalClientConfig.SPEC) {
            HudState.setOptions(MagicalClientConfig.current());
        }
    }

    /** Game-bus half: the HUD forgets everything at the edges of a session. */
    @EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
    public static final class Session {
        private Session() {}

        @SubscribeEvent
        public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
            HudAnnouncer.reset();
            RuleFlash.reset();
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            HudAnnouncer.reset();
            RuleFlash.reset();
            HudState.markDirty();
        }
    }

    /** The hideGui / no-player guard the vanilla groups get for free, plus a profiler section. */
    static LayeredDraw.Layer gated(String section, LayeredDraw.Layer inner) {
        return (graphics, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            // A HudQuiet screen (the Grimoire) dims the world with no plate under it, so every
            // layer steps aside while it is open rather than reading through its glyphs.
            if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                    || minecraft.screen instanceof HudQuiet) {
                return;
            }
            ProfilerFiller profiler = Profiler.get();
            profiler.push(section);
            inner.render(graphics, delta);
            profiler.pop();
        };
    }

    /** The sigil, then the forged-weapon combo readout and the counter window as they always were. */
    private static void renderSigil(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        // The rule selector covers the screen and carries no plate of its own, so the sigil would
        // read straight through its columns - the loadout caption and any announcement land on top
        // of them. It steps aside while a law is being written and comes back when the key is let
        // go. The small radials below do not need this: they sit round the crosshair, clear of it.
        // The Sword Stance picker is the same case and stands the sigil down for the same reason.
        if (!SpaceManipulationOverlay.active()
                && !com.efkrdnz.magical.client.SwordStanceOverlay.isActive()) {
            SigilRenderer.render(graphics, delta);
        }
        ForgeComboHud.render(graphics, minecraft);
        ClientCounterPrompt.render(graphics, minecraft);
    }

    private static void renderEncounter(GuiGraphics graphics, DeltaTracker delta) {
        ClientUnwakingEncounter.renderHud(graphics, Minecraft.getInstance());
    }

    /** The space dials, the loadout switcher, the sub-skill wheels, the blood strip; each draws itself only while held. */
    private static void renderSelector(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        SpaceManipulationOverlay.render(graphics, minecraft);
        com.efkrdnz.magical.client.FractureOverlay.render(graphics, minecraft);
        com.efkrdnz.magical.client.CausalAnchorOverlay.render(graphics, minecraft);
        com.efkrdnz.magical.client.SwordStanceOverlay.render(graphics, minecraft);
        MagicWheelOverlay.render(graphics, minecraft);
        SovereignAegisInput.render(graphics, minecraft);
        BlackFlamesInput.render(graphics, minecraft);
        SpaceOffenseInput.render(graphics, minecraft);
        SoulVowInput.render(graphics, minecraft);
        BloodShapeInput.render(graphics, minecraft);
    }
}
