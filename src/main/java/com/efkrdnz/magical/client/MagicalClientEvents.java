package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.renderer.AbyssalDischargeRenderer;
import com.efkrdnz.magical.client.renderer.AstralGateRenderer;
import com.efkrdnz.magical.client.renderer.AstralGateSpecialRenderer;
import com.efkrdnz.magical.client.renderer.AstralStepRenderer;
import com.efkrdnz.magical.client.renderer.ChronosEndRenderer;
import com.efkrdnz.magical.client.renderer.BlackFlameArcRenderer;
import com.efkrdnz.magical.client.renderer.BlackFlameBrandRenderer;
import com.efkrdnz.magical.client.renderer.BlackFlameFieldRenderer;
import com.efkrdnz.magical.client.renderer.BlackFlameProjectileRenderer;
import com.efkrdnz.magical.client.renderer.DimensionalGuillotineRenderer;
import com.efkrdnz.magical.client.renderer.GabrielHolyFieldRenderer;
import com.efkrdnz.magical.client.renderer.MeleeArcRenderer;
import com.efkrdnz.magical.client.renderer.JudgementBeamRenderer;
import com.efkrdnz.magical.client.renderer.MagicCircleRenderer;
import com.efkrdnz.magical.client.renderer.MagicOpponentRenderer;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.efkrdnz.magical.client.renderer.SacrificialCoreRenderer;
import com.efkrdnz.magical.client.renderer.SacrificialCoreSpecialRenderer;
import com.efkrdnz.magical.client.screen.MagicPyramidScreen;
import com.efkrdnz.magical.client.screen.ArcaneWorkbenchScreen;
import com.efkrdnz.magical.client.screen.GreedVaultScreen;
import com.efkrdnz.magical.client.screen.SpaceWalkerScreen;
import com.efkrdnz.magical.client.renderer.SkillClashEffectRenderer;
import com.efkrdnz.magical.client.renderer.SingularityRenderer;
import com.efkrdnz.magical.client.renderer.SoulBondRenderer;
import com.efkrdnz.magical.client.renderer.SovereignAegisRenderer;
import com.efkrdnz.magical.client.renderer.SpacePocketPortalRenderer;
import com.efkrdnz.magical.client.renderer.SpacePocketRoomEffectRenderer;
import com.efkrdnz.magical.client.renderer.SpaceSubspaceRenderer;
import com.efkrdnz.magical.client.renderer.TowerAuraRenderer;
import com.efkrdnz.magical.entity.GabrielHolyFieldEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalBlockEntities;
import com.efkrdnz.magical.registry.MagicalEntities;
import com.efkrdnz.magical.registry.MagicalMenus;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MagicalClientEvents {
    private MagicalClientEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MagicalMenus.ARCANE_WORKBENCH.get(), ArcaneWorkbenchScreen::new);
        event.register(MagicalMenus.MAGIC_PYRAMID.get(), MagicPyramidScreen::new);
        event.register(MagicalMenus.GREED_VAULT.get(), GreedVaultScreen::new);
        event.register(MagicalMenus.SPACE_WALKER.get(), SpaceWalkerScreen::new);
        event.register(MagicalMenus.CLASS_SELECT.get(), com.efkrdnz.magical.client.screen.ClassSelectScreen::new);
        event.register(MagicalMenus.CLASS_TREE.get(), com.efkrdnz.magical.client.screen.ClassTreeScreen::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        for (var mapping : MagicalKeyMappings.CAST_SLOTS) {
            event.register(mapping);
        }
        event.register(MagicalKeyMappings.OPEN_CODEX);
        event.register(MagicalKeyMappings.OPEN_WHEEL);
        event.register(MagicalKeyMappings.CONFIRM_WHEEL);
        event.register(MagicalKeyMappings.REFILL_BARRIER);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        MagicalRenderTypes.registerShaders(event);
        com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes.registerShaders(event);
    }

    @SubscribeEvent
    public static void registerRenderBuffers(net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent event) {
        com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes.registerRenderBuffers(event);
    }

    @SubscribeEvent
    public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(com.efkrdnz.magical.client.renderer.fx.FxTextures::register);
        com.efkrdnz.magical.client.renderer.fx.paint.custom.MagicalPainters.register();
    }

    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "chronos_end"),
                new ChronosSkyEffects());
        event.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "tower_liminal"),
                new LiminalSkyEffects());
    }

    @SubscribeEvent
    public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sacrificial_core"),
                SacrificialCoreSpecialRenderer.Unbaked.MAP_CODEC);
        event.register(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "astral_gate"),
                AstralGateSpecialRenderer.Unbaked.MAP_CODEC);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MagicalEntities.MAGIC_OPPONENT.get(), MagicOpponentRenderer::new);
        event.registerEntityRenderer(MagicalEntities.BLACK_FLAME_PROJECTILE.get(), BlackFlameProjectileRenderer::new);
        event.registerEntityRenderer(MagicalEntities.BLACK_FLAME_FIELD.get(), BlackFlameFieldRenderer::new);
        event.registerEntityRenderer(MagicalEntities.BLACK_FLAME_ARC.get(), BlackFlameArcRenderer::new);
        event.registerEntityRenderer(MagicalEntities.BLACK_FLAME_BRAND.get(), BlackFlameBrandRenderer::new);
        event.registerEntityRenderer(MagicalEntities.MAGIC_CIRCLE_EFFECT.get(), MagicCircleRenderer::new);
        event.registerEntityRenderer(MagicalEntities.JUDGEMENT_BEAM.get(), JudgementBeamRenderer::new);
        event.registerEntityRenderer(MagicalEntities.GABRIEL_HOLY_FIELD.get(), GabrielHolyFieldRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SKILL_CLASH_EFFECT.get(), SkillClashEffectRenderer::new);
        event.registerEntityRenderer(MagicalEntities.ABYSSAL_DISCHARGE.get(), AbyssalDischargeRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SPACE_SUBSPACE.get(), SpaceSubspaceRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SPACE_POCKET_PORTAL.get(), SpacePocketPortalRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SPACE_POCKET_ROOM_EFFECT.get(), SpacePocketRoomEffectRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SINGULARITY.get(), SingularityRenderer::new);
        event.registerEntityRenderer(MagicalEntities.DIMENSIONAL_GUILLOTINE.get(), DimensionalGuillotineRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SOUL_BOND.get(), SoulBondRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SOVEREIGN_AEGIS.get(), SovereignAegisRenderer::new);
        event.registerEntityRenderer(MagicalEntities.TOWER_AURA.get(), TowerAuraRenderer::new);
        event.registerEntityRenderer(MagicalEntities.MELEE_ARC.get(), MeleeArcRenderer::new);
        event.registerEntityRenderer(MagicalEntities.SPELL_EFFECT.get(), com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell::new);
        event.registerEntityRenderer(MagicalEntities.SOLID_CONSTRUCT.get(), com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell::new);
        event.registerEntityRenderer(MagicalEntities.THROWN_SPELL.get(), com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell::new);
        event.registerEntityRenderer(MagicalEntities.ROLLING_BODY.get(), com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell::new);
        event.registerEntityRenderer(MagicalEntities.EFFIGY.get(), com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell::new);
        event.registerEntityRenderer(MagicalEntities.SPIRIT_WOLF.get(), com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell::new);
        event.registerEntityRenderer(MagicalEntities.POLYMORPH_SHELL.get(), com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell::new);
        event.registerEntityRenderer(MagicalEntities.WRENCHED_ITEM.get(), com.efkrdnz.magical.client.renderer.fx.WrenchedItemRenderer::new);
        event.registerBlockEntityRenderer(MagicalBlockEntities.ASTRAL_GATE.get(), AstralGateRenderer::new);
        event.registerBlockEntityRenderer(MagicalBlockEntities.SACRIFICIAL_CORE.get(), SacrificialCoreRenderer::new);
    }

    @EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
    public static final class Hud {
        private static final float FLIGHT_FADE_SPEED = 0.08F;
        private static float manaFlightFade;
        private static Vec3 lastManaFlightPosition = Vec3.ZERO;

        private Hud() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                ClientCounterPrompt.tickAndConsumeWheel(minecraft, false);
                MagicWheelOverlay.tick(minecraft, false);
                if (SpaceManipulationOverlay.active()) {
                    SpaceManipulationOverlay.finish();
                }
                FirstPersonEffects.tick(minecraft);
                com.efkrdnz.magical.client.fx.TransientVisuals.tick();
                com.efkrdnz.magical.client.fx.SpellParticles.tick();
                return;
            }
            FirstPersonEffects.tick(minecraft);
            com.efkrdnz.magical.client.fx.TransientVisuals.tick();
            com.efkrdnz.magical.client.fx.SpellParticles.tick();
            ClientStatusState.tick(minecraft);
            GenericHoldInput.tick(minecraft);
            for (int i = 0; i < MagicalKeyMappings.CAST_SLOTS.length; i++) {
                if (SpaceAuthorityInput.tickSlot(minecraft, i)) {
                    while (MagicalKeyMappings.CAST_SLOTS[i].consumeClick()) {
                        // Authority skills use hold/release instead of press-to-cast.
                    }
                    continue;
                }
                if (SpaceOffenseInput.tickSlot(minecraft, i)) {
                    while (MagicalKeyMappings.CAST_SLOTS[i].consumeClick()) {
                        // Spatial Arsenal uses hold/release subskill selection.
                    }
                    continue;
                }
                if (SoulVowInput.tickSlot(minecraft, i)) {
                    while (MagicalKeyMappings.CAST_SLOTS[i].consumeClick()) {
                        // Soul Vow uses hold/release subskill selection.
                    }
                    continue;
                }
                if (SovereignAegisInput.tickSlot(minecraft, i)) {
                    while (MagicalKeyMappings.CAST_SLOTS[i].consumeClick()) {
                        // Sovereign Aegis uses hold/release subskill selection.
                    }
                    continue;
                }
                if (BlackFlamesInput.tickSlot(minecraft, i)) {
                    while (MagicalKeyMappings.CAST_SLOTS[i].consumeClick()) {
                        // Black Flames uses hold/release subskill selection.
                    }
                    continue;
                }
                while (MagicalKeyMappings.CAST_SLOTS[i].consumeClick()) {
                    MagicalNetwork.sendCastRequest(i, minecraft.player != null && minecraft.player.isShiftKeyDown());
                }
            }
            while (MagicalKeyMappings.OPEN_CODEX.consumeClick()) {
                MagicalNetwork.sendOpenCodexRequest();
            }
            while (MagicalKeyMappings.CONFIRM_WHEEL.consumeClick()) {
                MagicWheelOverlay.castChosenSkill();
            }
            boolean confirmWheelDown = MagicalKeyMappings.CONFIRM_WHEEL.isDown();
            SovereignAegisInput.tickWheelCast(confirmWheelDown);
            BlackFlamesInput.tickWheelCast(confirmWheelDown);
            SpaceOffenseInput.tickWheelCast(confirmWheelDown);
            SoulVowInput.tickWheelCast(confirmWheelDown);
            boolean counterConsumesWheel = ClientCounterPrompt.tickAndConsumeWheel(minecraft, MagicalKeyMappings.OPEN_WHEEL.isDown());
            MagicWheelOverlay.tick(minecraft, counterConsumesWheel ? false : MagicalKeyMappings.OPEN_WHEEL.isDown());
            while (MagicalKeyMappings.REFILL_BARRIER.consumeClick()) {
                MagicalNetwork.sendBarrierRefillRequest();
            }
        }

        @SubscribeEvent
        public static void renderHud(RenderGuiEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            GuiGraphics guiGraphics = event.getGuiGraphics();
            FirstPersonEffects.renderOverlay(guiGraphics);
            if (minecraft.options.hideGui) {
                return;
            }

            MagicalHudOverlay.render(guiGraphics, minecraft, ClientMagicState.get());
            ClientCounterPrompt.render(guiGraphics, minecraft);
            SpaceManipulationOverlay.render(guiGraphics, minecraft);
            MagicWheelOverlay.render(guiGraphics, minecraft);
            SovereignAegisInput.render(guiGraphics, minecraft);
            BlackFlamesInput.render(guiGraphics, minecraft);
            SpaceOffenseInput.render(guiGraphics, minecraft);
            SoulVowInput.render(guiGraphics, minecraft);
        }

        @SubscribeEvent
        public static void renderLevelOverlays(RenderLevelStageEvent event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return;
            }
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
                renderHolyFieldsBeforeEntities(event, minecraft);
                com.efkrdnz.magical.client.fx.TransientVisuals.renderThroughTerrain(event, minecraft);
                return;
            }
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
                return;
            }
            AstralStepRenderer.render(event, minecraft);
            ChronosEndRenderer.render(event, minecraft);
            com.efkrdnz.magical.client.fx.TransientVisuals.render(event, minecraft);
            {
                Vec3 cameraPosition = event.getCamera().getPosition();
                float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                event.getPoseStack().pushPose();
                com.efkrdnz.magical.client.fx.SpellParticles.render(event.getPoseStack(), minecraft.renderBuffers().bufferSource(), event.getCamera(), partial);
                event.getPoseStack().popPose();
            }
            if (minecraft.options.hideGui) {
                return;
            }
            PlayerMagicState data = ClientMagicState.get();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            boolean activeFlight = data.isPassiveEnabled(MagicPassiveContent.MANA_FLIGHT.id()) && minecraft.player.getAbilities().flying;
            if (activeFlight) {
                manaFlightFade = Math.min(1.0F, manaFlightFade + FLIGHT_FADE_SPEED);
                lastManaFlightPosition = minecraft.player.getPosition(partialTick);
            } else {
                manaFlightFade = Math.max(0.0F, manaFlightFade - FLIGHT_FADE_SPEED);
                if (manaFlightFade <= 0.0F) {
                    return;
                }
            }
            Vec3 cameraPosition = event.getCamera().getPosition();
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(lastManaFlightPosition.x - cameraPosition.x, lastManaFlightPosition.y - cameraPosition.y + 0.08D, lastManaFlightPosition.z - cameraPosition.z);
            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
            float easedFade = manaFlightFade * manaFlightFade * (3.0F - 2.0F * manaFlightFade);
            MagicCircleRenderer.renderManaFlightCircle(event.getPoseStack(), buffer, 0.8F + easedFade * 0.08F, 0.88F * easedFade, minecraft.player.tickCount + partialTick);
            buffer.endBatch();
            event.getPoseStack().popPose();
        }

        private static void renderHolyFieldsBeforeEntities(RenderLevelStageEvent event, Minecraft minecraft) {
            Vec3 cameraPosition = event.getCamera().getPosition();
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
            boolean rendered = false;
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (!(entity instanceof GabrielHolyFieldEntity field) || !field.shouldRender(cameraPosition.x, cameraPosition.y, cameraPosition.z)) {
                    continue;
                }
                double extent = Math.max(10.0D, field.radius() * 3.0D + 5.0D);
                if (!event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(
                        field.getX() - extent, field.getY() - 1.0D, field.getZ() - extent,
                        field.getX() + extent, field.getY() + 24.0D, field.getZ() + extent))) {
                    continue;
                }
                GabrielHolyFieldRenderer.renderThroughBlocksBeforeEntities(field, event.getPoseStack(), buffer, cameraPosition, partialTick);
                rendered = true;
            }
            if (rendered) {
                buffer.endBatch(MagicalRenderTypes.magicCircle());
            }
        }

        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            FirstPersonEffects.applyCamera(event);
            ChronosEndRenderer.applyCutShake(event);
        }

        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            FirstPersonEffects.applyFov(event);
        }

        @SubscribeEvent
        public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
            if (SovereignAegisInput.handleScroll(event.getScrollDeltaY()) || BlackFlamesInput.handleScroll(event.getScrollDeltaY()) || SpaceOffenseInput.handleScroll(event.getScrollDeltaY()) || SoulVowInput.handleScroll(event.getScrollDeltaY()) || SpaceManipulationOverlay.handleScroll(event.getScrollDeltaY()) || MagicWheelOverlay.handleScroll(event.getScrollDeltaY())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onMouseButton(InputEvent.MouseButton.Pre event) {
            if (SpaceManipulationOverlay.handleMouseButton(event.getButton(), event.getAction())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (!event.isAttack() || event.getHand() != InteractionHand.MAIN_HAND || minecraft.player == null || minecraft.screen != null) {
                return;
            }
            ItemStack held = minecraft.player.getMainHandItem();
            if (!(held.getItem() instanceof SwordItem) && !(held.getItem() instanceof AxeItem)) {
                return;
            }
            if (ClientMagicState.get().hasBlackFlamesImbue() && held.getItem() instanceof SwordItem) {
                MagicalNetwork.sendBlackFlamesSwing();
            }
            // Every full-strength swing throws a flying slash forward. The whiff flag (no entity under
            // the crosshair) tells the server to also snap a real vanilla hit onto a nearby enemy, so
            // a direct hit still routes through vanilla exactly once while a miss stays forgiving.
            if (minecraft.player.getAttackStrengthScale(0.5F) > 0.9F) {
                MagicalNetwork.sendMeleeSwing(minecraft.crosshairPickEntity == null);
            }
        }
    }
}
