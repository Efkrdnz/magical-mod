package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.SovereignAegisEntity;
import com.efkrdnz.magical.forge.BlacksmithForgeService;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.tower.DungeonTowerService;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = MagicalMod.MODID)
public final class MagicGameplayEvents {
    /** Shared with opponent flight, so the two can never charge different rates. */
    public static final int MANA_FLIGHT_DRAIN_INTERVAL = 10;
    public static final int MANA_FLIGHT_DRAIN_AMOUNT = 1;
    private static final int FULL_FOOD_LEVEL = 20;
    private static final float MANA_SUSTENANCE_MIN_SATURATION = 1.0F;
    /** Mana Skin: share of a hit paid out of the pool, and what each point of it costs. */
    private static final float MANA_SKIN_SHARE = 0.25F;
    private static final int MANA_SKIN_COST_PER_DAMAGE = 2;
    private static final int STEADY_BREATHING_INTERVAL = 20;
    private static final int STEADY_BREATHING_MANA = 1;
    private static final int MANA_LEAK_INTERVAL = 60;

    private MagicGameplayEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        MagicCounterService.tickPlayer(player);
        SpacePocketService.enforcePocketBounds(player);
        if (SovereignAegisEntity.isSealed(player)) {
            state.tickServer(player);
            return;
        }
        MagicSinService.tickPlayer(player, state);
        if (state.isPassiveEnabled(MagicPassiveContent.HEAT_RESISTANCE.id()) && state.passiveLevel(MagicPassiveContent.HEAT_RESISTANCE.id()) >= 5) {
            player.clearFire();
            player.setTicksFrozen(0);
        }
        if (state.isPassiveEnabled(MagicPassiveContent.POISON_RESISTANCE.id()) && state.passiveLevel(MagicPassiveContent.POISON_RESISTANCE.id()) >= 5) {
            player.removeEffect(MobEffects.POISON);
        }
        tickManaSustenance(player, state);
        tickSteadyBreathing(player, state);
        tickManaLeakCurse(player, state);
        if (player.tickCount % ClassPassiveEffects.SLOW_TICK_INTERVAL == 0) {
            ClassPassiveEffects.slowTick(player, state);
        }
        state.tickServer(player);
        tickManaFlight(player, state);
    }

    @SubscribeEvent
    public static void onChronosBlockBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level && ChronosDimensionService.isChronos(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onChronosBlockPlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level && ChronosDimensionService.isChronos(level)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            BlackFlamesService.tick(level);
        }
        DungeonTowerService.tick(event.getServer());
        ChronosSequenceService.tick();
        ForgeComboService.tick(event.getServer());
    }

    private static void tickManaFlight(ServerPlayer player, PlayerMagicState state) {
        boolean vanillaFlight = player.getAbilities().instabuild || player.isSpectator();
        if (ChronosDimensionService.grantsFreeFlight(player)) {
            // At the end of time everyone drifts freely - no mana drain, no passive needed.
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
            return;
        }
        boolean enabled = state.isPassiveEnabled(MagicPassiveContent.MANA_FLIGHT.id());
        if (!enabled) {
            if (!vanillaFlight && (player.getAbilities().mayfly || player.getAbilities().flying)) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
            return;
        }

        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
        if (!player.getAbilities().flying) {
            return;
        }

        if (vanillaFlight || player.tickCount % MANA_FLIGHT_DRAIN_INTERVAL != 0) {
            return;
        }
        if (!state.spendMana(MANA_FLIGHT_DRAIN_AMOUNT)) {
            state.togglePassive(MagicPassiveContent.MANA_FLIGHT.id());
            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;
            player.onUpdateAbilities();
            player.displayClientMessage(Component.translatable("message.magical.mana_flight_empty"), true);
        }
        state.sync(player);
    }

    private static void tickManaSustenance(ServerPlayer player, PlayerMagicState state) {
        if (!state.isPassiveEnabled(MagicPassiveContent.MANA_SUSTENANCE.id()) || player.getAbilities().instabuild || player.isSpectator()) {
            return;
        }
        FoodData foodData = player.getFoodData();
        int missingFood = FULL_FOOD_LEVEL - foodData.getFoodLevel();
        if (missingFood <= 0) {
            return;
        }
        int manaCost = Math.max(1, (missingFood + 3) / 4);
        if (!state.spendMana(manaCost)) {
            return;
        }
        foodData.setFoodLevel(FULL_FOOD_LEVEL);
        if (foodData.getSaturationLevel() < MANA_SUSTENANCE_MIN_SATURATION) {
            foodData.setSaturation(MANA_SUSTENANCE_MIN_SATURATION);
        }
        state.sync(player);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            SpaceAuthorityService.closeAllDomains(player, state, false);
            int spilled = state.takePendingLoadoutOverflow();
            if (spilled > 0) {
                player.sendSystemMessage(Component.translatable("message.magical.loadout_overflow", spilled));
            }
            int refunded = state.takePendingTuningRefunds();
            if (refunded > 0) {
                // Silently wiping somebody's builds would read as a bug. Say what changed and that
                // the points are still theirs.
                player.sendSystemMessage(Component.translatable("message.magical.tuning_refunded", refunded));
            }
            state.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClassPassiveEffects.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            SpaceAuthorityService.closeAllDomains(player, state, false);
            ForgeComboService.reset(player);
            BlacksmithForgeService.forget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            SpaceAuthorityService.closeAllDomains(player, state, false);
            ForgeComboService.reset(player);
            state.sync(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            SpaceAuthorityService.closeAllDomains(player, state, false);
            ForgeComboService.reset(player);
            state.sync(player);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        SoulAuthorityService.onSoulDamage(living, event.getSource().getEntity() == null ? event.getSource().getDirectEntity() : event.getSource().getEntity());
        float originalDamage = event.getContainer().getNewDamage();
        float damage = SovereignAegisEntity.rewriteIncomingDamage(living, event.getSource(), originalDamage);
        if (originalDamage > 0.0F && damage <= 0.0F) {
            event.getContainer().setNewDamage(0.0F);
            event.setCanceled(true);
            return;
        }
        if (!(living instanceof ServerPlayer player)) {
            // A tamed animal is not a player, but its owner's passives still speak for it.
            event.getContainer().setNewDamage(ClassPassiveEffects.petIncomingDamage(living, event.getSource(), damage));
            return;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            damage *= 1.0F - state.passiveReduction(MagicPassiveContent.HEAT_RESISTANCE.id());
        }
        if (event.getSource().is(DamageTypes.MAGIC) && player.hasEffect(MobEffects.POISON)) {
            damage *= 1.0F - state.passiveReduction(MagicPassiveContent.POISON_RESISTANCE.id());
        }
        if (event.getSource().is(DamageTypes.MAGIC) || event.getSource().is(DamageTypes.INDIRECT_MAGIC)) {
            damage *= 1.0F - state.passiveReduction(MagicPassiveContent.MAGIC_RESISTANCE.id());
        }
        // A GUARD-forged weapon soaks part of anything that lands while its window is open.
        damage *= 1.0F - ForgeComboService.guardReduction(player, player.serverLevel().getGameTime());
        damage = absorbWithManaSkin(state, damage);
        damage = MagicSinService.beforeBarrierDamage(player, state, event.getSource(), damage);
        damage = ClassPassiveEffects.incomingDamage(player, state, event.getSource(), damage);
        float beforeBarrier = damage;
        float remaining = state.absorbDamage(damage);
        ClassPassiveEffects.onBarrierAbsorb(player, state, beforeBarrier - remaining, event.getSource());
        // Last stop before a death: a passive may buy the player out of it.
        if (remaining >= player.getHealth() && ClassPassiveEffects.cheatDeath(player, state, event.getSource(), remaining)) {
            event.getContainer().setNewDamage(0.0F);
            event.setCanceled(true);
            state.sync(player);
            return;
        }
        event.getContainer().setNewDamage(remaining);
        state.sync(player);
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SovereignAegisEntity.hasUltimate(player)) {
            event.setCanceled(true);
            return;
        }
        if (SovereignAegisEntity.isSealed(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() != null && SovereignAegisEntity.isSealed(event.getEntity())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer deadPlayer) {
            DungeonTowerService.onPlayerDeath(deadPlayer);
        }
        SoulAuthorityService.onDeath(event);
        if (event.isCanceled()) {
            return;
        }
        ClassPassiveEffects.onPetDeath(event.getEntity());
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !(event.getEntity() instanceof LivingEntity killed)) {
            return;
        }
        MagicSinService.onMagicKill(player, killed);
        ClassXpService.onKill(player, event.getSource());
        ClassPassiveEffects.onKill(player, player.getData(MagicalAttachments.MAGIC_STATE), killed, event.getSource());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        SoulAuthorityService.onSoulDamage(target, player);
        ForgeComboService.notePrimaryHit(player, target);
        BlackFlamesService.onMeleeAttack(player, target);
        ClassPassiveEffects.onMeleeHit(player, player.getData(MagicalAttachments.MAGIC_STATE), target);
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        event.getToolTip().addAll(ForgedWeapons.tooltip(event.getItemStack()));
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && SpacePocketService.isProtectedPocketShell(level, event.getPos())) {
            event.setCanceled(true);
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player) {
            ClassPassiveEffects.onHarvest(player, player.getData(MagicalAttachments.MAGIC_STATE), event.getState());
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        event.setAmount(ClassPassiveEffects.adjustHeal(player, player.getData(MagicalAttachments.MAGIC_STATE), event.getAmount()));
    }

    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().has(net.minecraft.core.component.DataComponents.FOOD)) {
            ClassPassiveEffects.onEat(player, player.getData(MagicalAttachments.MAGIC_STATE));
        }
    }

    /**
     * Mana Skin held a tooltip promising a magic barrier under the player's skin and did nothing at
     * all. It now pays part of every hit out of the mana pool instead of out of health, ahead of the
     * real barrier so the two stack rather than competing.
     */
    private static float absorbWithManaSkin(PlayerMagicState state, float damage) {
        if (damage <= 0.0F || !state.isPassiveEnabled(MagicPassiveContent.MANA_SKIN.id()) || state.mana() <= 0) {
            return damage;
        }
        float wanted = damage * MANA_SKIN_SHARE;
        int affordable = state.mana() / MANA_SKIN_COST_PER_DAMAGE;
        float absorbed = Math.min(wanted, affordable);
        if (absorbed <= 0.0F) {
            return damage;
        }
        state.spendMana(Math.max(1, Math.round(absorbed * MANA_SKIN_COST_PER_DAMAGE)));
        return damage - absorbed;
    }

    /** Steady Breathing was registered and never read: a calm body refills its own pool faster. */
    private static void tickSteadyBreathing(ServerPlayer player, PlayerMagicState state) {
        if (!state.isPassiveEnabled(MagicPassiveContent.STEADY_BREATHING.id())
                || player.tickCount % STEADY_BREATHING_INTERVAL != 0) {
            return;
        }
        // Sprinting, swinging or being on fire all count as not breathing steadily.
        if (player.isSprinting() || player.swinging || player.isOnFire() || player.getTicksUsingItem() > 0) {
            return;
        }
        state.addMana(STEADY_BREATHING_MANA);
    }

    /** The mana leak curse was dispellable metadata with no effect. It now actually leaks. */
    private static void tickManaLeakCurse(ServerPlayer player, PlayerMagicState state) {
        if (!state.hasCurse(MagicPassiveContent.MANA_LEAK_CURSE.id()) || player.tickCount % MANA_LEAK_INTERVAL != 0) {
            return;
        }
        // Scaled to the pool, so a bigger reservoir does not make the curse irrelevant.
        state.spendMana(Math.max(1, state.maxMana() / 200));
    }
}
