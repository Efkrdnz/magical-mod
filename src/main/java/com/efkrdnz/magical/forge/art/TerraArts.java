package com.efkrdnz.magical.forge.art;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeForms;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;
import com.efkrdnz.magical.forge.StrikeImpact;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** TERRA's four Arts: the sunder, the fault line, the quake and the pillar of stone. */
public final class TerraArts {

    private static final float SUNDER_DAMAGE = 1.0f;
    /** Half of the slash's own shove again, so the total push comes out at one and a half. */
    private static final double SUNDER_EXTRA_PUSH = 0.5;

    private static final double FAULT_LENGTH = 6.0;
    private static final double FAULT_HALF_WIDTH = 1.5;
    private static final float FAULT_FRACTION = 0.50f;
    private static final double FAULT_LAUNCH = 0.3;
    private static final int FAULT_SPIKES = 3;

    private static final double QUAKE_RADIUS = 4.0;
    private static final float QUAKE_FRACTION = 0.60f;
    private static final int QUAKE_STAGGER_TICKS = 20;
    private static final int QUAKE_STAGGER_AMPLIFIER = 3;

    private static final double UPHEAVAL_LAUNCH = 1.2;

    private TerraArts() {}

    /**
     * Stone Edge - the slash lands a blunt sunder that armour does not stop, and hits half again as
     * hard on its feet, reading the extra shove off the strike's own push rather than inventing one.
     */
    public static void stoneEdge(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.STONE_EDGE, context)) {
            return;
        }
        ArtSupport.hurt(owner, target, SUNDER_DAMAGE);
        Vec3 forward = ArtSupport.direction(context);
        ArtSupport.knockback(owner, target, SUNDER_EXTRA_PUSH * StrikeImpact.basePush(ForgeForms.SLASH.knockback()),
                -forward.x, -forward.z);
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.TERRA_SHARDS, element,
                target.getBbWidth());
    }

    /** Fault Line - the cleave cracks the ground open for six blocks, throwing whatever it runs under. */
    public static void faultLine(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.FAULT_LINE, context) || !context.firstBody()) {
            return;
        }
        Vec3 forward = ArtSupport.direction(context);
        Vec3 from = owner.position();
        float damage = context.dealtDamage() * FAULT_FRACTION;
        // The struck body already took the cleave itself; the fault line is what runs on past it,
        // exactly as Quake and Toxic Bloom hold their splash off their own primary target.
        for (LivingEntity caught : ArtSupport.alongLine(level, from, forward, FAULT_LENGTH, FAULT_HALF_WIDTH,
                owner, target, 0)) {
            ArtSupport.hurt(owner, caught, damage);
            ArtSupport.push(owner, caught, new Vec3(0.0, FAULT_LAUNCH, 0.0));
        }
        for (int spike = 1; spike <= FAULT_SPIKES; spike++) {
            double along = FAULT_LENGTH * spike / (double) FAULT_SPIKES;
            ArtSupport.burst(level, from.add(forward.x * along, 0.0, forward.z * along),
                    ForgeEffectStyle.TERRA_SHARDS, element, 1.0f);
        }
    }

    /** Quake - the slam shakes four blocks of ground, staggering everything still standing on it. */
    public static void quake(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.QUAKE, context) || !context.firstBody()) {
            return;
        }
        Vec3 ground = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
        float splash = context.dealtDamage() * QUAKE_FRACTION;
        for (LivingEntity shaken : ArtSupport.around(level, ground, QUAKE_RADIUS, owner, null)) {
            if (!shaken.onGround()) {
                continue; // a quake reaches whatever the ground reaches, and nothing else
            }
            if (shaken != target) {
                ArtSupport.hurt(owner, shaken, splash);
            }
            ArtSupport.apply(owner, shaken, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    QUAKE_STAGGER_TICKS, QUAKE_STAGGER_AMPLIFIER));
        }
        ArtSupport.burst(level, ground, ForgeEffectStyle.TERRA_SHARDS, element, (float) QUAKE_RADIUS);
    }

    /** Upheaval - the heavy rising cut drives a stone pillar up under the target and throws it clear. */
    public static void upheaval(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.UPHEAVAL, context)) {
            return;
        }
        ArtSupport.liftTo(owner, target, UPHEAVAL_LAUNCH);
        ArtSupport.burst(level, new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ()),
                ForgeEffectStyle.TERRA_SHARDS, element, Math.max(1.0f, target.getBbHeight()));
    }
}
