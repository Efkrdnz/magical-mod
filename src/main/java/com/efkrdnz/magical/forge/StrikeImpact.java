package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.entity.ForgeEffectEntity;
import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.StrikeTally;
import com.efkrdnz.magical.magic.ForgeComboService;
import com.efkrdnz.magical.magic.ForgeFeedback;
import com.efkrdnz.magical.magic.MagicDamageService;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Everything that happens when a forged strike touches one target: the damage it works out, the
 * damage it deals, and every consequence hanging off it. Kept out of the strike entity so the
 * entity only has to decide <em>when</em> a target was touched.
 */
public final class StrikeImpact {

    private static final ResourceLocation STRIKE_ID = ForgeIds.id("forge_strike");
    private static final double RISING_LIFT = 0.55;
    private static final double RISING_LIFT_HEAVY = 0.85;
    private static final int MAX_IMPACT_EFFECTS = 6;
    private static final int IMPACT_EFFECT_LIFE = 8;
    private static final float MIN_EFFECT_SCALE = 0.6f;
    private static final double BASE_PUSH = 0.25;
    private static final double PUSH_PER_KNOCKBACK = 0.5;

    private StrikeImpact() {}

    /** Runs one whole impact and returns the damage that was dealt. */
    public static float apply(ServerLevel level, Entity source, Entity owner, LivingEntity target,
            StrikeLoadout loadout, Vec3 direction, float damageScale, long now, RandomSource random,
            StrikeTally tally) {
        // Read before anything this press does to the target, because the element rider that runs
        // a few lines below would otherwise satisfy its own Art's condition. See TargetState.
        StrikeContext.TargetState before = stateOf(target);
        float dealt = resolveDamage(loadout, target, damageScale, now, random, tally);
        MagicDamageService.hurt(target, ForgeDamageTypes.strike(level, source, owner == null ? source : owner),
                dealt, STRIKE_ID);
        pushBack(target, owner, loadout, direction);
        if (loadout.family() == FormFamily.RISING && ForgeTargeting.canAffect(ownerPlayer(owner), target)) {
            double lift = loadout.heavy() ? RISING_LIFT_HEAVY : RISING_LIFT;
            target.setDeltaMovement(target.getDeltaMovement().add(0.0, lift, 0.0));
            target.hurtMarked = true;
        }
        tally.noteImpact();
        spawnImpactEffect(level, target, loadout, tally);
        if (owner instanceof ServerPlayer player) {
            afterDamage(level, player, target, loadout, direction, dealt, now, tally, before);
        }
        return dealt;
    }

    /** What the target was already suffering, in the two conditions an Art is allowed to read. */
    private static StrikeContext.TargetState stateOf(LivingEntity target) {
        return new StrikeContext.TargetState(target.isOnFire(), target.getTicksFrozen() > 0);
    }

    /**
     * Crit, then the element's own synergy against the target's state, then SHATTER and BRAND, and
     * finally the correction for the one target vanilla already hit through {@code player.attack}
     * on this same press - that target must not be charged the weapon's damage twice. The tally
     * consumes that correction, because a flurry pulse and a heavy slam's second ring come back
     * through here against the same body and there was only ever one vanilla hit to cancel.
     */
    private static float resolveDamage(StrikeLoadout loadout, LivingEntity target, float damageScale, long now,
            RandomSource random, StrikeTally tally) {
        float dealt = loadout.damage() * damageScale;
        if (loadout.critChance() > 0.0f && random.nextFloat() < loadout.critChance()) {
            dealt *= ForgeStrikeMath.CRIT_MULTIPLIER;
        }
        dealt *= ForgeRiderService.preHitScale(loadout.element().kind(), target);
        boolean shatter = loadout.has(ForgeModifierKind.SHATTER) && hasShatterStatus(target);
        if (loadout.finisher()) {
            int stacks = loadout.has(ForgeModifierKind.BRAND) ? ForgeBrandService.consume(target, now) : 0;
            dealt *= ForgeStrikeMath.finisherBonus(shatter, stacks, loadout.mods());
        } else {
            if (shatter) {
                dealt *= ForgeStrikeMath.shatterBonus(loadout.mods());
            }
            if (loadout.has(ForgeModifierKind.BRAND)) {
                ForgeBrandService.addStack(target, now, ForgeStrikeMath.brandMaxStacks(loadout.mods()));
            }
        }
        return tally.consumePrimaryCorrection(target.getId(), loadout.primaryTargetId())
                ? ForgeStrikeMath.primaryTargetDamage(dealt, loadout.weaponAttack())
                : dealt;
    }

    private static void afterDamage(ServerLevel level, ServerPlayer owner, LivingEntity target,
            StrikeLoadout loadout, Vec3 direction, float dealt, long now, StrikeTally tally,
            StrikeContext.TargetState before) {
        // An echo is a repeat of a press that already paid for itself, so it gets neither the rider
        // nor the Art. Within a real press the two differ: the rider fires once per target however
        // many passes the form makes, while the Art fires on every touch and decides for itself -
        // a flurry's stacking Arts count pulses, and only the third one discharges.
        if (!loadout.echo()) {
            int touches = tally.noteTargetHit(target.getUUID());
            StrikeContext context = new StrikeContext(loadout.form().id(), loadout.family(), loadout.heavy(),
                    loadout.comboIndex(), loadout.finisher(), dealt, direction, tally.impacts(),
                    tally.passImpacts(), touches, before);
            if (touches == 1) {
                ForgeRiderService.apply(level, owner, target, loadout.weapon(), loadout.element(), context);
            }
            ForgeSpecials.lookup(loadout.element(), loadout.form())
                    .ifPresent(special -> special.apply(level, owner, target, loadout.weapon(), context));
            if (tally.impacts() == 1) {
                ForgeFeedback.send(owner, loadout.family(), loadout.heavy(), loadout.element().primaryColor());
            }
        }
        if (loadout.has(ForgeModifierKind.PIERCE)) {
            MagicDamageService.hurt(target, ForgeDamageTypes.magic(owner),
                    ForgeStrikeMath.pierceFraction(loadout.mods()) * dealt, STRIKE_ID);
        }
        if (loadout.has(ForgeModifierKind.LEECH) && isLeechTarget(loadout, target, tally)) {
            float healed = ForgeStrikeMath.leechHeal(dealt, tally.leechHealed(), loadout.mods());
            if (healed > 0.0f) {
                owner.heal(healed);
                tally.addLeechHealed(healed);
            }
        }
        if (loadout.has(ForgeModifierKind.GUARD)) {
            ForgeComboService.noteGuardHit(owner, now);
        }
    }

    /** The target vanilla struck, or - when this strike found none - the first body it opened up. */
    private static boolean isLeechTarget(StrikeLoadout loadout, LivingEntity target, StrikeTally tally) {
        return loadout.primaryTargetId() >= 0
                ? target.getId() == loadout.primaryTargetId()
                : tally.impacts() == 1;
    }

    /**
     * The shove a strike of this knockback rating gives. Public because an Art that adds to the
     * push - TERRA's Stone Edge - has to scale off the same number rather than invent its own.
     */
    public static double basePush(float knockback) {
        return BASE_PUSH + knockback * PUSH_PER_KNOCKBACK;
    }

    private static void pushBack(LivingEntity target, Entity owner, StrikeLoadout loadout, Vec3 direction) {
        if (!ForgeTargeting.canAffect(ownerPlayer(owner), target)) {
            return;
        }
        double strength = basePush(loadout.knockback());
        if (loadout.family() == FormFamily.SPIN && owner != null) {
            target.knockback(strength, owner.getX() - target.getX(), owner.getZ() - target.getZ());
            return;
        }
        target.knockback(strength, -direction.x, -direction.z);
    }

    /** {@code owner} is only ever a real player in practice, but is typed {@code Entity} this far
     *  up the call chain; {@link ForgeTargeting#canAffect} needs the narrower type. */
    private static ServerPlayer ownerPlayer(Entity owner) {
        return owner instanceof ServerPlayer player ? player : null;
    }

    private static void spawnImpactEffect(ServerLevel level, LivingEntity target, StrikeLoadout loadout,
            StrikeTally tally) {
        if (tally.impacts() > MAX_IMPACT_EFFECTS) {
            return;
        }
        Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        ForgeEffectEntity.impact(level, at, styleFor(loadout.element().kind()), loadout.element().primaryColor(),
                        loadout.element().secondaryColor(), Math.max(MIN_EFFECT_SCALE, target.getBbWidth()),
                        IMPACT_EFFECT_LIFE)
                .withHeavy(loadout.heavy());
    }

    /** The states SHATTER feeds on. TERRA's tremor is tracked as the slow it leaves behind. */
    public static boolean hasShatterStatus(LivingEntity target) {
        return target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                || target.getTicksFrozen() > 0
                || target.hasEffect(MobEffects.WITHER)
                || target.hasEffect(MobEffects.POISON);
    }

    public static ForgeEffectStyle styleFor(ForgeElementKind kind) {
        return switch (kind) {
            case FIRE -> ForgeEffectStyle.FIRE_BLOOM;
            case FROST -> ForgeEffectStyle.FROST_SHARDS;
            case STORM -> ForgeEffectStyle.STORM_FORK;
            case VOID -> ForgeEffectStyle.VOID_IMPLOSION;
            case RADIANT -> ForgeEffectStyle.RADIANT_CROSS;
            case VENOM -> ForgeEffectStyle.VENOM_DRIP;
            case TERRA -> ForgeEffectStyle.TERRA_SHARDS;
            case GALE -> ForgeEffectStyle.GALE_SWIRL;
        };
    }
}
