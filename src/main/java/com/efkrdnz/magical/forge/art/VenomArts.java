package com.efkrdnz.magical.forge.art;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** VENOM's four Arts: the deep sting, the bloom, the lingering miasma and the flurry's fangs. */
public final class VenomArts {

    private static final int ENVENOM_POISON_TICKS = 100;
    private static final int ENVENOM_POISON_AMPLIFIER = 1;
    private static final int ENVENOM_WEAKNESS_TICKS = 60;

    private static final double BLOOM_RADIUS = 3.0;
    private static final int BLOOM_POISON_TICKS = 100;
    private static final int BLOOM_SLOW_TICKS = 40;
    private static final int BLOOM_SLOW_AMPLIFIER = 1;
    private static final float BLOOM_SPLASH_FRACTION = 0.40f;

    private static final float MIASMA_RADIUS = 1.5f;
    private static final int MIASMA_LIFE = 60;

    private static final int FANG_POISON_TICKS = 80;

    private VenomArts() {}

    /** Envenom - the heavy thrust drives the venom deep: poison II for five seconds and a lasting weakness. */
    public static void envenom(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.ENVENOM, context)) {
            return;
        }
        ArtSupport.apply(owner, target,
                new MobEffectInstance(MobEffects.POISON, ENVENOM_POISON_TICKS, ENVENOM_POISON_AMPLIFIER));
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.WEAKNESS, ENVENOM_WEAKNESS_TICKS, 0));
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.VENOM_DRIP, element, target.getBbWidth());
    }

    /** Toxic Bloom - the finishing slam bursts a three-block bloom of poison, slow and splashed damage. */
    public static void toxicBloom(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.TOXIC_BLOOM, context) || !context.firstBody()) {
            return;
        }
        Vec3 ground = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
        float splash = context.dealtDamage() * BLOOM_SPLASH_FRACTION;
        for (LivingEntity caught : ArtSupport.around(level, ground, BLOOM_RADIUS, owner, null)) {
            if (caught != target) {
                ArtSupport.hurt(owner, caught, splash);
            }
            ArtSupport.apply(owner, caught, new MobEffectInstance(MobEffects.POISON, BLOOM_POISON_TICKS, 0));
            ArtSupport.apply(owner, caught,
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, BLOOM_SLOW_TICKS, BLOOM_SLOW_AMPLIFIER));
        }
        ArtSupport.burst(level, ground, ForgeEffectStyle.VENOM_DRIP, element, (float) BLOOM_RADIUS);
    }

    /** Miasma Crescent - the crescent leaves a low cloud that keeps poisoning whatever walks into it. */
    public static void miasmaCrescent(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.MIASMA_CRESCENT, context) || !context.firstBody()) {
            return;
        }
        Vec3 ground = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
        ArtSupport.zone(level, owner, ground, ForgeZoneKind.MIASMA_CLOUD, element, MIASMA_RADIUS, MIASMA_LIFE, 0.0f);
    }

    /**
     * Fang Storm - every pulse of the flurry sinks another fang into the same body, up to four, and
     * the poison deepens by one every second fang. The stack-to-amplifier rule lives in
     * {@link ArtMath#fangAmplifier}, where a plain unit test can hold it to the table.
     */
    public static void fangStorm(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.FANG_STORM, context)) {
            return;
        }
        int stacks = ArtMath.stacks(context.targetHitIndex(), ArtMath.FANG_MAX_STACKS);
        int amplifier = ArtMath.fangAmplifier(stacks);
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.POISON, FANG_POISON_TICKS, amplifier));
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.VENOM_DRIP, element,
                target.getBbWidth() * stacks / (float) ArtMath.FANG_MAX_STACKS);
    }
}
