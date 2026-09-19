package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What a body does to what it hits beyond damage: the port of {@code game_effect_entities}. The
 * list is the body's own effect first (Ember's BURN, Arc Bolt's SHOCK, a ring's pulse) and then
 * what a Wreath, Uplift or Displace stamped on it, each once, in the order written; the spawner
 * applies both, or those bodies silently lose their effect.
 */
public final class VerseHitEffects {

    public static final float BURN_SECONDS = 4.0F;
    public static final int FREEZE_TICKS = 60;
    public static final int SHOCK_TICKS = 10;
    public static final int WITHER_TICKS = 80;
    public static final int UPLIFT_TICKS = 30;
    public static final double SHOCK_SHOVE = 0.45D;
    public static final double DISPLACE_BLOCKS = 4.0D;

    private VerseHitEffects() {
    }

    /** On a hit: the prototype's {@code hit()} first, then the stamped effects, each once. */
    public static List<HitEffect> effectsOf(ProjectilePlan body) {
        return ordered(body.prototype().hit(), body);
    }

    /** On a pulse: the prototype's {@code pulse()} first, then the stamped effects, each once. */
    public static List<HitEffect> pulseEffectsOf(ProjectilePlan body) {
        return ordered(body.prototype().pulse(), body);
    }

    private static List<HitEffect> ordered(HitEffect own, ProjectilePlan body) {
        Set<HitEffect> effects = new LinkedHashSet<>();
        if (own != null) {
            effects.add(own);
        }
        effects.addAll(body.stamped().hitEffects());
        return List.copyOf(effects);
    }

    /**
     * BURN sets fire, FREEZE applies slowness and puts a fire out, SHOCK is a short stun through
     * knockback and a heavy slow, WITHER the wither effect, UPLIFT levitation, DISPLACE moves the
     * target a short random distance to standable ground, through the one placement path the mod
     * has (so a player is moved by the teleport packet and nothing else).
     */
    public static void apply(HitEffect effect, LivingEntity target, Entity source, Vec3 from, RandomSource random) {
        switch (effect) {
            case BURN -> target.igniteForSeconds(BURN_SECONDS);
            case FREEZE -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_TICKS, 1, false, true), source);
                target.clearFire();
            }
            case SHOCK -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SHOCK_TICKS, 4, false, true), source);
                SkillTargets.shove(target, from, SHOCK_SHOVE, 0.12D);
            }
            case WITHER -> target.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0, false, true), source);
            case UPLIFT -> target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, UPLIFT_TICKS, 0, false, true), source);
            case DISPLACE -> displace(target, random);
        }
    }

    private static void displace(LivingEntity target, RandomSource random) {
        double angle = random.nextDouble() * Math.PI * 2.0D;
        Vec3 wanted = target.position().add(Math.cos(angle) * DISPLACE_BLOCKS, 0.0D, Math.sin(angle) * DISPLACE_BLOCKS);
        Vec3 feet = SafeSpotSearch.standableNear(target.level(), wanted, 2, 3, target.getBbWidth(), target.getBbHeight());
        if (feet != null) {
            SafeSpotSearch.place(target, feet, target.getYRot(), target.getXRot(), false);
        }
    }
}
