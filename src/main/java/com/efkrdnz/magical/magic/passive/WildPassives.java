package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.MagicalMod;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Ranger line: distance kept, and the pack that closes it for you.
 *
 * <p>The two ends of this tree want opposite things. Broadhead into Distant Eye is the sharpest
 * damage curve in the mod and punishes you for being in melee at all; Kennel Bond into Second Pack
 * hands the fighting to your animals. Nothing rewards standing in the middle.</p>
 */
public final class WildPassives implements ClassPassiveHandler {
    private static final ResourceLocation STRIDE_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "long_stride_speed");
    private static final ResourceLocation PACK_HEALTH_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "second_pack_health");
    private static final ResourceLocation PACK_DAMAGE_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "second_pack_damage");

    private static final long TRAIL_TICKS = 160L;
    private static final float TRAIL_BONUS = 0.15F;
    private static final float BROADHEAD_FAR_BONUS = 0.25F;
    private static final float BROADHEAD_NEAR_PENALTY = 0.1F;
    private static final double BROADHEAD_FAR = 12.0D;
    private static final double BROADHEAD_NEAR = 4.0D;
    private static final float LOOSE_GROUND_CHANCE = 0.25F;
    private static final int LOOSE_GROUND_TICKS = 20;
    private static final float KENNEL_REDUCTION = 0.4F;
    private static final long KENNEL_GRIEF_TICKS = 120L;
    private static final float KENNEL_GRIEF_BONUS = 0.2F;
    private static final double STRIDE_SPEED = 0.15D;
    private static final float STRIDE_SAFE_FALL = 8.0F;
    private static final long BREATH_STILL_TICKS = 30L;
    private static final float BREATH_BONUS = 0.5F;
    private static final double SHARE_RADIUS = 24.0D;
    private static final float SHARE_HEAL_SHARE = 0.2F;
    private static final float DISTANT_EYE_PER_BLOCK = 0.02F;
    private static final double DISTANT_EYE_FROM = 10.0D;
    private static final double DISTANT_EYE_DANGER = 3.0D;
    private static final float DISTANT_EYE_PENALTY = 0.2F;
    private static final double PACK_HEALTH = 0.3D;
    private static final double PACK_DAMAGE = 0.25D;

    private final Map<UUID, Wild> scratch = new HashMap<>();

    private static final class Wild {
        final Map<UUID, Long> marks = new HashMap<>();
        long griefUntilTick = Long.MIN_VALUE;
        long stillSinceTick = Long.MIN_VALUE;
        double lastX;
        double lastZ;
        boolean breathHeld;
    }

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.BLOOD_TRAIL.id(),
                MagicPassiveContent.BROADHEAD.id(),
                MagicPassiveContent.LOOSE_GROUND.id(),
                MagicPassiveContent.KENNEL_BOND.id(),
                MagicPassiveContent.LONG_STRIDE.id(),
                MagicPassiveContent.HELD_BREATH.id(),
                MagicPassiveContent.SHARE_THE_KILL.id(),
                MagicPassiveContent.DISTANT_EYE.id(),
                MagicPassiveContent.SECOND_PACK.id());
    }

    private Wild wild(ServerPlayer player) {
        return scratch.computeIfAbsent(player.getUUID(), id -> new Wild());
    }

    @Override
    public float outgoingSpellDamage(ServerPlayer player, PlayerMagicState state, LivingEntity target,
            ResourceLocation skillId, float amount) {
        Wild wild = wild(player);
        long now = player.serverLevel().getGameTime();
        double distance = player.distanceTo(target);
        float result = amount;

        // Blood Trail: the mark pays out once, on the follow-up, then re-arms.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BLOOD_TRAIL.id())) {
            Long markedAt = wild.marks.get(target.getUUID());
            if (markedAt != null && now - markedAt <= TRAIL_TICKS) {
                result *= 1.0F + TRAIL_BONUS;
            }
            wild.marks.put(target.getUUID(), now);
            wild.marks.entrySet().removeIf(entry -> now - entry.getValue() > TRAIL_TICKS);
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) TRAIL_TICKS, 0, true, false));
        }

        // Broadhead: a heavy head flies well and handles badly up close.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BROADHEAD.id())) {
            if (distance > BROADHEAD_FAR) {
                result *= 1.0F + BROADHEAD_FAR_BONUS;
            } else if (distance < BROADHEAD_NEAR) {
                result *= 1.0F - BROADHEAD_NEAR_PENALTY;
            }
        }

        // Distant Eye: uncapped on purpose. The melee penalty in incomingDamage is the price.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.DISTANT_EYE.id()) && distance > DISTANT_EYE_FROM) {
            result *= 1.0F + DISTANT_EYE_PER_BLOCK * (float) (distance - DISTANT_EYE_FROM);
        }

        // Kennel Bond: grief is brief and sharp.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.KENNEL_BOND.id()) && now < wild.griefUntilTick) {
            result *= 1.0F + KENNEL_GRIEF_BONUS;
        }

        // Loose Ground: a trapper leaves the footing worse than they found it.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.LOOSE_GROUND.id())
                && player.getRandom().nextFloat() < LOOSE_GROUND_CHANCE) {
            MagicStatusService.apply(target, MagicStatus.ROOTED, LOOSE_GROUND_TICKS, skillId, player);
        }

        return result;
    }

    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
        // Held Breath: one shot, spent by the next cast whatever that cast is.
        Wild wild = wild(player);
        if (ClassPassiveEffects.on(state, MagicPassiveContent.HELD_BREATH.id()) && wild.breathHeld) {
            out.damage *= 1.0F + BREATH_BONUS;
            wild.breathHeld = false;
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.3F, 1.6F);
        }
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        float result = amount;

        // Long Stride: a pathfinder does not turn an ankle on an eight block drop.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.LONG_STRIDE.id())
                && source.is(DamageTypeTags.IS_FALL) && player.fallDistance < STRIDE_SAFE_FALL) {
            return 0.0F;
        }

        // Distant Eye: everything close enough to touch you hits harder.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.DISTANT_EYE.id())) {
            LivingEntity attacker = PassiveHooks.attacker(player, source);
            if (attacker != null && player.distanceTo(attacker) < DISTANT_EYE_DANGER) {
                result *= 1.0F + DISTANT_EYE_PENALTY;
            }
        }
        return result;
    }

    @Override
    public float petIncomingDamage(ServerPlayer owner, PlayerMagicState state, LivingEntity pet, DamageSource source, float amount) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.KENNEL_BOND.id())) {
            return amount * (1.0F - KENNEL_REDUCTION);
        }
        return amount;
    }

    @Override
    public void onPetDeath(ServerPlayer owner, PlayerMagicState state, LivingEntity pet) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.KENNEL_BOND.id())) {
            wild(owner).griefUntilTick = owner.serverLevel().getGameTime() + KENNEL_GRIEF_TICKS;
            PassiveHooks.puff(owner, ParticleTypes.ANGRY_VILLAGER, 6, 0.3D);
        }
    }

    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        // Share The Kill: the pack eats first.
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.SHARE_THE_KILL.id())) {
            return;
        }
        for (TamableAnimal pet : PassiveHooks.ownedPets(player, SHARE_RADIUS)) {
            pet.heal(pet.getMaxHealth() * SHARE_HEAL_SHARE);
        }
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        Wild wild = wild(player);
        long now = player.serverLevel().getGameTime();

        // Held Breath arms only after standing genuinely still.
        double dx = player.getX() - wild.lastX;
        double dz = player.getZ() - wild.lastZ;
        if (dx * dx + dz * dz > 0.0025D) {
            wild.stillSinceTick = Long.MIN_VALUE;
            wild.lastX = player.getX();
            wild.lastZ = player.getZ();
        } else {
            if (wild.stillSinceTick == Long.MIN_VALUE) {
                wild.stillSinceTick = now;
            } else if (!wild.breathHeld && now - wild.stillSinceTick >= BREATH_STILL_TICKS
                    && ClassPassiveEffects.on(state, MagicPassiveContent.HELD_BREATH.id())) {
                wild.breathHeld = true;
                PassiveHooks.puff(player, ParticleTypes.END_ROD, 3, 0.15D);
            }
        }

        applyOrClear(player, Attributes.MOVEMENT_SPEED, STRIDE_SPEED_MODIFIER, STRIDE_SPEED,
                ClassPassiveEffects.on(state, MagicPassiveContent.LONG_STRIDE.id()));

        // Second Pack: the animals themselves get bigger and hit harder.
        boolean pack = ClassPassiveEffects.on(state, MagicPassiveContent.SECOND_PACK.id());
        for (TamableAnimal pet : PassiveHooks.ownedPets(player, 32.0D)) {
            applyOrClear(pet, Attributes.MAX_HEALTH, PACK_HEALTH_MODIFIER, PACK_HEALTH, pack);
            applyOrClear(pet, Attributes.ATTACK_DAMAGE, PACK_DAMAGE_MODIFIER, PACK_DAMAGE, pack);
        }
    }

    /**
     * Transient attribute modifiers do not survive a relog, so they are re-applied on every slow
     * tick and removed the moment the passive is switched off in the codex.
     */
    private static void applyOrClear(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id, double amount, boolean wanted) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (!wanted) {
            instance.removeModifier(id);
            return;
        }
        if (instance.getModifier(id) == null) {
            instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    @Override
    public void forget(UUID playerId) {
        scratch.remove(playerId);
    }
}
