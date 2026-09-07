package com.efkrdnz.magical.forge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.efkrdnz.magical.entity.ForgeEffectEntity;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What the weapon's element does to whatever it just cut. One rider fires at most once per target
 * per press, gated by {@link ForgeStrikeMath#procChance} except on heavy and finisher strikes,
 * which always land theirs. Every number scales off the weapon's grade ordinal.
 */
public final class ForgeRiderService {

    private static final float FIRE_SYNERGY = 1.25f;
    private static final float FROST_SYNERGY = 1.25f;
    private static final float GALE_SYNERGY = 1.30f;

    private static final double STORM_CHAIN_RANGE = 5.0;
    private static final float STORM_CHAIN_FRACTION = 0.50f;
    private static final int STORM_TWO_CHAIN_GRADE = 4;
    private static final int STORM_FORK_LIFE = 6;

    private static final int VOID_BASE_SIPHON = 2;
    private static final int VOID_BINDING_SIPHON = 2;

    /** Black flame bites deeper into anything already burning or already rotting. */
    private static final float BLACK_FLAME_SYNERGY = 1.35f;

    /** A detonation feeds on a target already alight. */
    private static final float EXPLOSION_SYNERGY = 1.20f;

    /** Share of the hit the detonation deals at its centre, falling to nothing at the rim. */
    private static final float EXPLOSION_FRACTION = 0.55f;

    /** How hard a rime gale hauls its target back toward the smith. */
    private static final double RIME_GALE_PULL = 0.35;

    private ForgeRiderService() {}

    /**
     * The multiplier applied to a hit before it is dealt, for the states an element feeds on:
     * fire bites deeper into something already burning, frost into something already freezing, and
     * a gale hits hardest when its target has nothing under its feet.
     */
    public static float preHitScale(ForgeElementKind kind, LivingEntity target) {
        return switch (kind) {
            case FIRE -> target.isOnFire() ? FIRE_SYNERGY : 1.0f;
            case FROST -> target.getTicksFrozen() > 0 ? FROST_SYNERGY : 1.0f;
            case GALE -> target.onGround() ? 1.0f : GALE_SYNERGY;
            case STORM, VOID, RADIANT, VENOM, TERRA -> 1.0f;
            // Compounds feed on what both their parents fed on.
            case BLACK_FLAME -> target.isOnFire() || target.hasEffect(MobEffects.WITHER)
                    ? BLACK_FLAME_SYNERGY : 1.0f;
            case EXPLOSION -> target.isOnFire() ? EXPLOSION_SYNERGY : 1.0f;
            case RIME_GALE -> !target.onGround() ? GALE_SYNERGY
                    : target.getTicksFrozen() > 0 ? FROST_SYNERGY : 1.0f;
            case PLASMA, MAGMA -> target.isOnFire() ? FIRE_SYNERGY : 1.0f;
            case HAILSTORM -> target.getTicksFrozen() > 0 ? FROST_SYNERGY : 1.0f;
            case BLIGHT, VERDIGRIS -> target.hasEffect(MobEffects.POISON) ? FROST_SYNERGY : 1.0f;
            case ECLIPSE -> 1.0f;
        };
    }

    public static void apply(ServerLevel level, LivingEntity owner, LivingEntity target, ForgedWeapon weapon,
            ElementDefinition element, StrikeContext ctx) {
        if (!shouldProc(owner, weapon, element, ctx)) {
            return;
        }
        int grade = weapon.grade().ordinal();
        switch (element.kind()) {
            case FIRE -> fire(owner, target, grade);
            case FROST -> frost(owner, target, grade);
            case STORM -> storm(level, owner, target, element, ctx, grade);
            case VOID -> voidRider(owner, target, grade, hasBinding(weapon));
            case RADIANT -> radiant(owner, target, grade);
            case VENOM -> venom(owner, target, grade);
            case TERRA -> terra(level, owner, target, ctx, grade);
            case GALE -> gale(owner, target, grade);
            case BLACK_FLAME -> blackFlame(owner, target, grade);
            case EXPLOSION -> explosion(level, owner, target, ctx, grade);
            case RIME_GALE -> rimeGale(owner, target, grade);
            case PLASMA -> {
                storm(level, owner, target, element, ctx, grade);
                if (ForgeTargeting.canAffect(owner, target)) {
                    target.igniteForSeconds(2.0f + grade);
                }
            }
            case MAGMA -> {
                terra(level, owner, target, ctx, grade);
                if (ForgeTargeting.canAffect(owner, target)) {
                    target.igniteForSeconds(2.0f + grade);
                }
            }
            case HAILSTORM -> {
                storm(level, owner, target, element, ctx, grade);
                frost(owner, target, grade);
            }
            case ECLIPSE -> eclipse(owner, target, grade);
            case BLIGHT -> blight(owner, target, grade);
            case VERDIGRIS -> {
                venom(owner, target, grade);
                terra(level, owner, target, ctx, grade);
            }
        }
    }

    private static boolean shouldProc(LivingEntity owner, ForgedWeapon weapon, ElementDefinition element,
            StrikeContext ctx) {
        if (ctx.heavy() || ctx.finisher()) {
            return true;
        }
        float chance = ForgeStrikeMath.procChance(element.procBase(), weapon.grade(), ForgeWeaponFlags.of(weapon));
        return owner.getRandom().nextFloat() < chance;
    }

    private static void fire(LivingEntity owner, LivingEntity target, int grade) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.igniteForSeconds(2.0f + 1.5f * grade);
    }

    private static void frost(LivingEntity owner, LivingEntity target, int grade) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        int amplifier = grade >= 3 ? 2 : 1;
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 + 15 * grade, amplifier), owner);
        target.setTicksFrozen(target.getTicksFrozen() + 50 + 20 * grade);
    }

    private static void storm(ServerLevel level, LivingEntity owner, LivingEntity target, ElementDefinition element,
            StrikeContext ctx, int grade) {
        int chains = grade >= STORM_TWO_CHAIN_GRADE ? 2 : 1;
        float damage = ctx.dealtDamage() * STORM_CHAIN_FRACTION;
        if (damage <= 0.0f) {
            return;
        }
        Set<UUID> visited = new HashSet<>();
        visited.add(target.getUUID());
        visited.add(owner.getUUID());
        LivingEntity from = target;
        for (int i = 0; i < chains; i++) {
            LivingEntity next = nearestOther(level, from, visited, STORM_CHAIN_RANGE);
            if (next == null) {
                return;
            }
            visited.add(next.getUUID());
            // The chain is a rider, not the blow itself, so it bypasses armour as magic damage.
            // The forge_strike source belongs only to the strike the player actually swung. The
            // wielder still has to be on the source, or a mob the chain finishes drops nothing.
            MagicDamageService.hurt(next, ForgeDamageTypes.magic(owner), damage, ForgeIds.id("forge_strike"));
            ForgeEffectEntity.fork(level, centre(from), centre(next), element.primaryColor(), STORM_FORK_LIFE);
            from = next;
        }
    }

    /**
     * BINDING is the rune that ties an element tighter to the blade. On a VOID weapon that shows up
     * as a deeper draw: the siphon takes two more mana on top of the grade's own share.
     */
    private static void voidRider(LivingEntity owner, LivingEntity target, int grade, boolean binding) {
        if (ForgeTargeting.canAffect(owner, target)) {
            int amplifier = grade >= 5 ? 2 : grade >= 2 ? 1 : 0;
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + 20 * grade, amplifier), owner);
        }
        // The mana siphon is the wielder's own draw, not something done to the target, so it is
        // unconditional: a Wither denied by the target gate still costs the wielder nothing extra,
        // but a legal siphon must not be held hostage by an illegal status.
        //
        // Only a player has a pool worth crediting. A mob wielding a forged weapon casts from a
        // state it carries itself, not from this attachment, so siphoning into the attachment would
        // top up something nothing ever reads.
        if (owner instanceof ServerPlayer player) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            state.addMana(VOID_BASE_SIPHON + grade + (binding ? VOID_BINDING_SIPHON : 0));
            state.sync(player);
        }
    }

    private static boolean hasBinding(ForgedWeapon weapon) {
        return ForgeWeaponFlags.of(weapon).has(ForgeModifierKind.BINDING);
    }

    private static void radiant(LivingEntity owner, LivingEntity target, int grade) {
        if (ForgeTargeting.canAffect(owner, target)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60 + 15 * grade, 0), owner);
        }
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            MagicDamageService.hurt(target, ForgeDamageTypes.magic(owner), 2.0f + 1.5f * grade,
                    ForgeIds.id("forge_strike"));
        }
        owner.heal(0.5f + 0.25f * grade);
    }

    private static void venom(LivingEntity owner, LivingEntity target, int grade) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 25 * grade, grade >= 3 ? 1 : 0), owner);
        if (grade >= 2) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40 + 15 * grade, 0), owner);
        }
    }

    /**
     * The shockwave under the blow. The slow it leaves is also the "tremor" SHATTER looks for, so
     * a terra chain sets up its own finisher.
     */
    private static void terra(ServerLevel level, LivingEntity owner, LivingEntity target, StrikeContext ctx,
            int grade) {
        float splash = ctx.dealtDamage() * (20 + 4 * grade) / 100.0f;
        double radius = 1.5 + 0.2 * grade;
        int slowTicks = 20 + 5 * grade;
        for (Entity entity : level.getEntities(target, target.getBoundingBox().inflate(radius),
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && living.onGround())) {
            if (entity == owner) {
                continue;
            }
            LivingEntity shaken = (LivingEntity) entity;
            if (splash > 0.0f) {
                MagicDamageService.hurt(shaken, ForgeDamageTypes.magic(owner), splash, ForgeIds.id("forge_strike"));
            }
            if (ForgeTargeting.canAffect(owner, shaken)) {
                shaken.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slowTicks, 1), owner);
            }
        }
    }

    private static void gale(LivingEntity owner, LivingEntity target, int grade) {
        if (ForgeTargeting.canAffect(owner, target)) {
            target.knockback(1.0 + 0.25 * grade, owner.getX() - target.getX(), owner.getZ() - target.getZ());
        }
        owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30 + 10 * grade, 0));
    }

    /**
     * fire + void. A burn that fire resistance and water do not stop, plus the rot of the void, and
     * no regeneration to grow the damage back.
     */
    private static void blackFlame(LivingEntity owner, LivingEntity target, int grade) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.igniteForSeconds(3.0f + 2.0f * grade);
        int witherAmplifier = grade >= 3 ? 2 : 1;
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + 20 * grade, witherAmplifier), owner);
        // The burn is the point, so the healing that would outrun it is taken away for as long.
        target.removeEffect(MobEffects.REGENERATION);
    }

    /**
     * fire + gale. The only rider that does not put a status on the body it hit - it detonates at
     * the point of impact and everything nearby wears it, falling off to the rim.
     *
     * <p>The wielder is left out. Self-damage would be funnier, but the forge already hands out a
     * GUARD rune that soaks incoming damage, and the two together would turn a detonation into a
     * way to charge a guard rather than a risk.
     */
    private static void explosion(ServerLevel level, LivingEntity owner, LivingEntity target, StrikeContext ctx,
            int grade) {
        double radius = 2.0 + 0.25 * grade;
        float centre = ctx.dealtDamage() * EXPLOSION_FRACTION;
        if (centre <= 0.0f) {
            return;
        }
        Vec3 origin = target.getBoundingBox().getCenter();
        for (Entity entity : level.getEntities(target, target.getBoundingBox().inflate(radius),
                candidate -> candidate instanceof LivingEntity living && living.isAlive())) {
            if (entity == owner) {
                continue;
            }
            LivingEntity caught = (LivingEntity) entity;
            double distance = caught.getBoundingBox().getCenter().distanceTo(origin);
            float falloff = (float) Math.max(0.0, 1.0 - distance / radius);
            if (falloff <= 0.0f) {
                continue;
            }
            MagicDamageService.hurt(caught, ForgeDamageTypes.magic(owner), centre * falloff,
                    ForgeIds.id("forge_strike"));
            if (!ForgeTargeting.canAffect(owner, caught)) {
                continue;
            }
            caught.knockback((1.2 + 0.2 * grade) * falloff,
                    origin.x - caught.getX(), origin.z - caught.getZ());
            caught.igniteForSeconds(2.0f + grade);
        }
        // The body that was actually struck always burns, even standing dead centre.
        if (ForgeTargeting.canAffect(owner, target)) {
            target.igniteForSeconds(2.0f + grade);
        }
    }

    /** frost + gale. Freezes, then drags the target back toward the smith rather than away. */
    private static void rimeGale(LivingEntity owner, LivingEntity target, int grade) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40 + 15 * grade, 1), owner);
        target.setTicksFrozen(target.getTicksFrozen() + 50 + 20 * grade);
        // Note the sign: a gale pushes away, a rime gale hauls in.
        target.knockback(RIME_GALE_PULL, target.getX() - owner.getX(), target.getZ() - owner.getZ());
    }

    /** void + radiant. Rot and blindness, and the undead still take the light. */
    private static void eclipse(LivingEntity owner, LivingEntity target, int grade) {
        radiant(owner, target, grade);
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + 20 * grade, grade >= 3 ? 1 : 0), owner);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30 + 10 * grade, 0), owner);
    }

    /**
     * void + venom. Poison and rot together, and - the part that makes it more than the sum - the
     * healing the target receives is cut in half for as long as it lasts.
     */
    private static void blight(LivingEntity owner, LivingEntity target, int grade) {
        venom(owner, target, grade);
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + 15 * grade, 0), owner);
        target.removeEffect(MobEffects.REGENERATION);
    }

    private static LivingEntity nearestOther(ServerLevel level, LivingEntity from, Set<UUID> visited, double range) {
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity entity : level.getEntities(from, from.getBoundingBox().inflate(range),
                candidate -> candidate instanceof LivingEntity living && living.isAlive())) {
            if (!visited.contains(entity.getUUID())) {
                candidates.add((LivingEntity) entity);
            }
        }
        LivingEntity best = null;
        double bestDistance = range * range;
        for (LivingEntity candidate : candidates) {
            double distance = candidate.distanceToSqr(from);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static Vec3 centre(LivingEntity entity) {
        return entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
    }
}
