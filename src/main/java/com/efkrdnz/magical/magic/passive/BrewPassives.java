package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Alchemist line: dose, reaction and tolerance.
 *
 * <p>Slag Coat is flat rather than proportional on purpose. It is the only answer to chip damage in
 * the mod and reads completely differently from the percentage resistances every other line has.</p>
 */
public final class BrewPassives implements ClassPassiveHandler {
    private static final float WILDCRAFT_CHANCE = 0.25F;
    private static final int WILDCRAFT_MANA = 5;
    private static final long WILDCRAFT_MEAL_TICKS = 80L;
    private static final float WILDCRAFT_MEAL_BONUS = 0.1F;
    private static final int CONCENTRATE_HEAVY = 40;
    private static final int CONCENTRATE_LIGHT = 20;
    private static final float CONCENTRATE_HEAVY_BONUS = 0.15F;
    private static final float CONCENTRATE_LIGHT_DISCOUNT = 0.25F;
    private static final int TOLERANCE_MAX = 5;
    private static final float TOLERANCE_PER_STACK = 0.1F;
    private static final float SLAG_FLAT = 2.0F;
    private static final float VENOM_PER_STATUS = 0.12F;
    private static final float DOSE_BONUS = 0.3F;
    private static final float DOSE_CAP = 8.0F;
    private static final float METALS_CHANCE = 0.15F;
    private static final int METALS_HOARD = 12;
    private static final double MIASMA_RADIUS = 6.0D;
    private static final int MIASMA_TICKS = 60;
    private static final float MIASMA_REDUCTION = 0.25F;
    private static final int EXCHANGE_MANA_PER_HEART = 20;
    /** Three minutes, in slow ticks, before Equivalent Exchange can save the player again. */
    private static final int EXCHANGE_COOLDOWN = 360;

    private final Map<UUID, Brew> scratch = new HashMap<>();

    private static final class Brew {
        long mealUntilTick = Long.MIN_VALUE;
    }

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.WILDCRAFT.id(),
                MagicPassiveContent.CONCENTRATE.id(),
                MagicPassiveContent.BUILDING_TOLERANCE.id(),
                MagicPassiveContent.SLAG_COAT.id(),
                MagicPassiveContent.COMPOUNDING_VENOM.id(),
                MagicPassiveContent.MEASURED_DOSE.id(),
                MagicPassiveContent.BASE_METALS.id(),
                MagicPassiveContent.MIASMA.id(),
                MagicPassiveContent.EQUIVALENT_EXCHANGE.id());
    }

    private Brew brew(ServerPlayer player) {
        return scratch.computeIfAbsent(player.getUUID(), id -> new Brew());
    }

    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
        // Concentrate: a distiller works at the extremes and gets nothing for the middle.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.CONCENTRATE.id())) {
            if (definition.baseManaCost() > CONCENTRATE_HEAVY) {
                out.damage *= 1.0F + CONCENTRATE_HEAVY_BONUS;
            } else if (definition.baseManaCost() < CONCENTRATE_LIGHT) {
                out.mana *= 1.0F - CONCENTRATE_LIGHT_DISCOUNT;
            }
        }

        // Wildcraft: a full stomach is worth something to a herbalist.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.WILDCRAFT.id())
                && player.serverLevel().getGameTime() < brew(player).mealUntilTick) {
            out.damage *= 1.0F + WILDCRAFT_MEAL_BONUS;
        }
    }

    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        // Compounding Venom: every affliction already on the target makes the next dose land harder.
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.COMPOUNDING_VENOM.id())) {
            return amount;
        }
        int afflictions = 0;
        for (MagicStatus status : MagicStatus.values()) {
            if (MagicStatusService.has(target, status)) {
                afflictions++;
            }
        }
        if (target.hasEffect(MobEffects.POISON)) {
            afflictions++;
        }
        if (target.hasEffect(MobEffects.WITHER)) {
            afflictions++;
        }
        return amount * (1.0F + VENOM_PER_STATUS * afflictions);
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        float result = amount;

        // Slag Coat: a metallurgist simply does not feel the small stuff.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.SLAG_COAT.id())) {
            result = Math.max(0.0F, result - SLAG_FLAT);
        }

        // Miasma: whatever is choking on your fumes cannot swing straight.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.MIASMA.id())) {
            LivingEntity attacker = PassiveHooks.attacker(player, source);
            if (attacker != null && attacker.hasEffect(MobEffects.POISON)) {
                result *= 1.0F - MIASMA_REDUCTION;
            }
        }
        return result;
    }

    @Override
    public boolean cheatDeath(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.EQUIVALENT_EXCHANGE.id())) {
            return false;
        }
        ResourceLocation id = MagicPassiveContent.EQUIVALENT_EXCHANGE.id();
        if (state.passiveCounter(id) > 0) {
            return false;
        }
        // Equivalent Exchange: the whole well, traded for whatever hearts it buys.
        int hearts = state.mana() / EXCHANGE_MANA_PER_HEART;
        if (hearts <= 0) {
            return false;
        }
        state.setMana(0);
        player.setHealth(Math.min(player.getMaxHealth(), hearts * 2.0F));
        player.clearFire();
        state.setPassiveCounter(id, EXCHANGE_COOLDOWN);
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
        PassiveHooks.puff(player, ParticleTypes.WAX_OFF, 24, 0.6D);
        return true;
    }

    @Override
    public float adjustHeal(ServerPlayer player, PlayerMagicState state, float amount) {
        // Measured Dose: stronger medicine, but never more than the body can take at once.
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.MEASURED_DOSE.id())) {
            return amount;
        }
        return Math.min(DOSE_CAP, amount * (1.0F + DOSE_BONUS));
    }

    @Override
    public float statusDurationScale(ServerPlayer player, PlayerMagicState state, MagicStatus status) {
        // Building Tolerance: the body learns, permanently, one exposure at a time.
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.BUILDING_TOLERANCE.id())) {
            return 1.0F;
        }
        ResourceLocation id = MagicPassiveContent.BUILDING_TOLERANCE.id();
        int stacks = state.passiveCounter(id);
        state.bumpPassiveCounter(id, 1, 0, TOLERANCE_MAX);
        return 1.0F - TOLERANCE_PER_STACK * stacks;
    }

    @Override
    public void onHarvest(ServerPlayer player, PlayerMagicState state, BlockState broken) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.WILDCRAFT.id())
                && isPlant(broken)
                && player.getRandom().nextFloat() < WILDCRAFT_CHANCE) {
            state.addMana(WILDCRAFT_MANA);
            PassiveHooks.puff(player, ParticleTypes.COMPOSTER, 4, 0.2D);
        }
    }

    private static boolean isPlant(BlockState broken) {
        return broken.is(net.minecraft.tags.BlockTags.FLOWERS)
                || broken.is(net.minecraft.tags.BlockTags.CROPS)
                || broken.is(net.minecraft.tags.BlockTags.SAPLINGS)
                || broken.is(net.minecraft.tags.BlockTags.LEAVES)
                || broken.is(net.minecraft.tags.BlockTags.REPLACEABLE_BY_TREES);
    }

    @Override
    public void onEat(ServerPlayer player, PlayerMagicState state) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.WILDCRAFT.id())) {
            brew(player).mealUntilTick = player.serverLevel().getGameTime() + WILDCRAFT_MEAL_TICKS;
        }
    }

    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        // Base Metals: the chrysopoeian turns dead things into the beginnings of gold.
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.BASE_METALS.id()) || !PassiveHooks.isSpellKill(source)) {
            return;
        }
        if (player.getRandom().nextFloat() >= METALS_CHANCE) {
            return;
        }
        victim.spawnAtLocation(player.serverLevel(), new ItemStack(Items.GOLD_NUGGET));
        state.addGreedHoard(METALS_HOARD);
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.MIASMA.id())) {
            // The plaguedoctor walks in their own weather and is the only thing unbothered by it.
            player.removeEffect(MobEffects.POISON);
            for (LivingEntity victim : PassiveHooks.hostilesNear(player, MIASMA_RADIUS)) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, MIASMA_TICKS, 0, true, true));
            }
        }
        int exchangeCooldown = state.passiveCounter(MagicPassiveContent.EQUIVALENT_EXCHANGE.id());
        if (exchangeCooldown > 0) {
            state.setPassiveCounter(MagicPassiveContent.EQUIVALENT_EXCHANGE.id(), exchangeCooldown - 1);
        }
    }

    @Override
    public void forget(UUID playerId) {
        scratch.remove(playerId);
    }
}
