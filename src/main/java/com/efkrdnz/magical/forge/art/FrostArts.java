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

/** FROST's four Arts: the shatter, the halo, the frost line and the pillar of ice. */
public final class FrostArts {

    private static final float RIME_FROZEN_BONUS = 0.5f;
    private static final double RIME_SPLASH_RADIUS = 2.0;
    private static final float RIME_SPLASH_FRACTION = 0.30f;

    private static final float HALO_RADIUS = 3.0f;
    private static final int HALO_LIFE = 20;

    private static final double HOAR_RADIUS = 1.5;
    /** The table's "10 t ground frost line": the frost itself lies on the ground for ten ticks. */
    private static final int HOAR_LINE_TICKS = 10;
    /** The line's own ten ticks plus half a second of drag on whatever was standing in it. */
    private static final int HOAR_SLOW_TICKS = HOAR_LINE_TICKS + 20;

    private static final double PILLAR_LIFT = 0.9;
    private static final int PILLAR_PIN_TICKS = 15;
    private static final int PILLAR_FREEZE_TICKS = 100;

    private FrostArts() {}

    /**
     * Rime Split - a heavy cleave into something already freezing hits half again as hard and
     * shatters it, throwing 30% of the blow onto everything standing within two blocks.
     *
     * <p>"Already freezing" is read off {@link StrikeContext#targetBefore()} rather than the live
     * entity. FROST's rider runs first and adds fifty ticks of freeze of its own, so asking the
     * target would find it frozen on every single heavy cleave and the condition would gate
     * nothing.</p>
     */
    public static void rimeSplit(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.RIME_SPLIT, context)
                || !context.targetBefore().frozen()) {
            return;
        }
        ArtSupport.hurt(owner, target, context.dealtDamage() * RIME_FROZEN_BONUS);
        float splash = context.dealtDamage() * RIME_SPLASH_FRACTION;
        Vec3 centre = ArtSupport.centre(target);
        for (LivingEntity shard : ArtSupport.around(level, centre, RIME_SPLASH_RADIUS, owner, target)) {
            ArtSupport.hurt(owner, shard, splash);
        }
        ArtSupport.burst(level, centre, ForgeEffectStyle.FROST_SHARDS, element, (float) RIME_SPLASH_RADIUS);
    }

    /** Glacial Halo - the finishing spin opens a ring of ice that slows hard and deepens the freeze. */
    public static void glacialHalo(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.GLACIAL_HALO, context) || !context.firstBody()) {
            return;
        }
        ArtSupport.zone(level, owner, owner.position(), ForgeZoneKind.GLACIAL_HALO, element, HALO_RADIUS, HALO_LIFE,
                0.0f);
    }

    /** Hoar Crescent - the crescent leaves a short line of ground frost that drags at the feet. */
    public static void hoarCrescent(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.HOAR_CRESCENT, context)) {
            return;
        }
        Vec3 ground = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HOAR_SLOW_TICKS, 0));
        for (LivingEntity chilled : ArtSupport.around(level, ground, HOAR_RADIUS, owner, target)) {
            ArtSupport.apply(owner, chilled, new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HOAR_SLOW_TICKS, 0));
        }
        ArtSupport.burst(level, ground, ForgeEffectStyle.FROST_SHARDS, element, (float) HOAR_RADIUS, HOAR_LINE_TICKS);
    }

    /** Icicle Pillar - the heavy rising cut throws the target up and pins it there, frozen. */
    public static void iciclePillar(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.ICICLE_PILLAR, context)) {
            return;
        }
        ArtSupport.push(owner, target, new Vec3(0.0, PILLAR_LIFT, 0.0));
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.LEVITATION, PILLAR_PIN_TICKS, 0));
        ArtSupport.freeze(owner, target, PILLAR_FREEZE_TICKS);
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.FROST_SHARDS, element,
                Math.max(1.0f, target.getBbHeight()));
    }
}
