package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The Warrior line: blood spent, ground held.
 *
 * <p>Two of these are about position rather than numbers. Single Combat only pays while you are
 * genuinely one-on-one, and Braced Stance only pays while you have stopped moving — so the line
 * rewards choosing a fight and standing in it.</p>
 */
public final class WarPassives implements ClassPassiveHandler {
    private static final float SINGLE_COMBAT_BONUS = 0.25F;
    private static final double SINGLE_COMBAT_SOLITUDE = 6.0D;
    private static final int TITHE_HIT_MANA = 2;
    private static final int TITHE_KILL_MANA = 15;
    /** Ticks of not being hit before First Wall arms again. */
    private static final long FIRST_WALL_ARM_TICKS = 120L;
    private static final float FIRST_WALL_REDUCTION = 0.6F;
    private static final float BRACED_REDUCTION = 0.2F;
    private static final long BRACED_STILL_TICKS = 40L;
    private static final int PAYMENT_MANA_PER_HEALTH = 8;
    private static final double RALLY_RADIUS = 10.0D;
    private static final int RALLY_TICKS = 60;
    private static final float SCAR_PER_STACK = 0.05F;
    private static final int SCAR_MAX_STACKS = 5;
    private static final float WOUNDS_PER_TENTH = 0.02F;
    private static final int WOUNDS_MAX_TENTHS = 10;
    private static final double HELD_GROUND_RADIUS = 4.0D;
    private static final int HELD_GROUND_BARRIER = 2;

    private final Map<UUID, War> scratch = new HashMap<>();

    private static final class War {
        UUID lastTarget;
        long lastHurtTick = Long.MIN_VALUE;
        long stillSinceTick = Long.MIN_VALUE;
        double lastX;
        double lastZ;
        int scarStacks;
        long lastScarDecayTick;
    }

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.SINGLE_COMBAT.id(),
                MagicPassiveContent.BLOOD_TITHE.id(),
                MagicPassiveContent.FIRST_WALL.id(),
                MagicPassiveContent.BRACED_STANCE.id(),
                MagicPassiveContent.RED_PAYMENT.id(),
                MagicPassiveContent.RALLY_CRY.id(),
                MagicPassiveContent.SCAR_TISSUE.id(),
                MagicPassiveContent.DEEPER_WOUNDS.id(),
                MagicPassiveContent.HELD_GROUND.id());
    }

    private War war(ServerPlayer player) {
        return scratch.computeIfAbsent(player.getUUID(), id -> new War());
    }

    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        War war = war(player);
        float result = amount;

        // Single Combat: an honour duel pays nothing once a third body joins in.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.SINGLE_COMBAT.id())
                && target.getUUID().equals(war.lastTarget)
                && PassiveHooks.hostilesNear(player, SINGLE_COMBAT_SOLITUDE).size() <= 1) {
            result *= 1.0F + SINGLE_COMBAT_BONUS;
        }

        // Deeper Wounds: a berserker is at their worst when they are winning comfortably.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.DEEPER_WOUNDS.id())) {
            int tenthsMissing = (int) ((1.0F - player.getHealth() / player.getMaxHealth()) * 10.0F);
            result *= 1.0F + WOUNDS_PER_TENTH * Math.min(WOUNDS_MAX_TENTHS, Math.max(0, tenthsMissing));
        }

        war.lastTarget = target.getUUID();
        return result;
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        War war = war(player);
        long now = player.serverLevel().getGameTime();
        float result = amount;

        // First Wall: the opening blow of an engagement, not every blow in one.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.FIRST_WALL.id())
                && now - war.lastHurtTick >= FIRST_WALL_ARM_TICKS) {
            result *= 1.0F - FIRST_WALL_REDUCTION;
            player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.4F, 0.8F);
        }

        // Braced Stance: planted feet.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BRACED_STANCE.id())
                && war.stillSinceTick != Long.MIN_VALUE && now - war.stillSinceTick >= BRACED_STILL_TICKS) {
            result *= 1.0F - BRACED_REDUCTION;
        }

        // Scar Tissue: sustained punishment hardens you, and only while it is sustained.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.SCAR_TISSUE.id())) {
            result *= 1.0F - SCAR_PER_STACK * war.scarStacks;
            war.scarStacks = Math.min(SCAR_MAX_STACKS, war.scarStacks + 1);
            war.lastScarDecayTick = now;
        }

        war.lastHurtTick = now;
        return result;
    }

    @Override
    public int payManaShortfall(ServerPlayer player, PlayerMagicState state, int missing) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.RED_PAYMENT.id())) {
            return 0;
        }
        float healthCost = missing / (float) PAYMENT_MANA_PER_HEALTH;
        // Never let the bill kill you: a Bloodletter bleeds for power, they do not pay it all.
        if (player.getHealth() - healthCost < 1.0F) {
            return 0;
        }
        player.hurt(player.damageSources().magic(), healthCost);
        PassiveHooks.puff(player, ParticleTypes.DAMAGE_INDICATOR, 5, 0.2D);
        return missing;
    }

    @Override
    public void onMeleeHit(ServerPlayer player, PlayerMagicState state, Entity target) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BLOOD_TITHE.id())) {
            state.addMana(TITHE_HIT_MANA);
        }
    }

    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        // Blood Tithe is a melee reward specifically; a Reaver is paid for the swing, not the spell.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BLOOD_TITHE.id()) && !PassiveHooks.isSpellKill(source)) {
            state.addMana(TITHE_KILL_MANA);
        }
    }

    @Override
    public void afterCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.RALLY_CRY.id())) {
            return;
        }
        // Rally Cry: everything on your side hits harder because you opened your mouth.
        boolean rallied = false;
        for (LivingEntity ally : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RALLY_RADIUS),
                other -> other != player && (other instanceof Player || PassiveHooks.isOwnedPet(player, other)))) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, RALLY_TICKS, 0, true, false));
            rallied = true;
        }
        if (rallied) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.34F, 1.35F);
        }
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        War war = war(player);
        long now = player.serverLevel().getGameTime();

        // Braced Stance tracks horizontal movement only, so looking around does not break the stance.
        double dx = player.getX() - war.lastX;
        double dz = player.getZ() - war.lastZ;
        if (dx * dx + dz * dz > 0.0025D) {
            war.stillSinceTick = Long.MIN_VALUE;
            war.lastX = player.getX();
            war.lastZ = player.getZ();
        } else if (war.stillSinceTick == Long.MIN_VALUE) {
            war.stillSinceTick = now;
        }

        if (war.scarStacks > 0 && now - war.lastScarDecayTick >= 20L) {
            war.lastScarDecayTick = now;
            war.scarStacks--;
        }

        // Held Ground: a Warden's ward knits fastest with something breathing down their neck.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.HELD_GROUND.id())
                && state.barrier() < state.maxBarrier()
                && !PassiveHooks.hostilesNear(player, HELD_GROUND_RADIUS).isEmpty()) {
            state.addBarrier(HELD_GROUND_BARRIER);
        }
    }

    @Override
    public void forget(UUID playerId) {
        scratch.remove(playerId);
    }
}
