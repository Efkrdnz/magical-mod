package com.efkrdnz.magical.forge.art;

import java.util.List;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** FIRE's four Arts: the long lance, the flame disc, the burning ring and the flurry's kindling. */
public final class FireArts {

    private static final float LANCE_LENGTH = 6.0f;
    private static final double LANCE_HALF_WIDTH = 0.8;
    private static final int LANCE_EXTRA_TARGETS = 2;
    private static final float LANCE_IGNITE_SECONDS = 5.0f;
    private static final float BURNING_BONUS = 0.20f;

    private static final float WHEEL_RADIUS = 2.5f;
    private static final int WHEEL_LIFE = 20;
    private static final float WHEEL_FRACTION = 0.25f;

    private static final float ERUPTION_RADIUS = 3.0f;
    private static final int ERUPTION_LIFE = 80;

    private static final float KINDLING_STACK_SECONDS = 1.0f;
    private static final float KINDLING_FINISHER_PER_STACK = 0.05f;

    private FireArts() {}

    /**
     * Cinder Lance - the heavy thrust runs six blocks out instead of stopping at the first body,
     * setting everything on the line alight for five seconds and biting deeper into whatever was
     * already burning.
     *
     * <p>"Already burning" is read off {@link StrikeContext#targetBefore()} rather than the live
     * entity. FIRE's rider runs first and ignites, and {@code isOnFire()} reads the fire ticks it
     * just set, so asking the target would find it burning on every single heavy thrust and the
     * bonus would be unconditional.</p>
     */
    public static void cinderLance(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.CINDER_LANCE, context)) {
            return;
        }
        if (context.targetBefore().burning()) {
            ArtSupport.hurt(owner, target, context.dealtDamage() * BURNING_BONUS);
        }
        target.igniteForSeconds(LANCE_IGNITE_SECONDS);
        if (!context.firstBody()) {
            return;
        }
        List<LivingEntity> line = ArtSupport.alongLine(level, owner.getEyePosition(), ArtSupport.direction(context),
                LANCE_LENGTH, LANCE_HALF_WIDTH, owner, target, LANCE_EXTRA_TARGETS);
        for (LivingEntity impaled : line) {
            ArtSupport.hurt(owner, impaled, context.dealtDamage());
            impaled.igniteForSeconds(LANCE_IGNITE_SECONDS);
            ArtSupport.burst(level, ArtSupport.centre(impaled), ForgeEffectStyle.FIRE_BLOOM, element,
                    impaled.getBbWidth());
        }
    }

    /** Pyre Wheel - the spin leaves a flame disc turning underfoot for a second. */
    public static void pyreWheel(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.PYRE_WHEEL, context) || !context.firstBody()) {
            return;
        }
        ArtSupport.zone(level, owner, owner.position(), ForgeZoneKind.PYRE_WHEEL, element, WHEEL_RADIUS, WHEEL_LIFE,
                context.dealtDamage() * WHEEL_FRACTION);
    }

    /** Ember Eruption - the slam opens a burning ring that keeps relighting whatever stands in it. */
    public static void emberEruption(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.EMBER_ERUPTION, context) || !context.firstBody()) {
            return;
        }
        Vec3 ground = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
        ArtSupport.zone(level, owner, ground, ForgeZoneKind.EMBER_ERUPTION, element, ERUPTION_RADIUS, ERUPTION_LIFE,
                0.0f);
    }

    /**
     * Kindling - every pulse of the flurry lays another burn stack on the same body, up to three,
     * and each one the press is carrying adds 5% to a finishing flurry's bite.
     */
    public static void kindling(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.KINDLING, context)) {
            return;
        }
        int stacks = ArtMath.stacks(context.targetHitIndex(), ArtMath.KINDLING_MAX_STACKS);
        target.igniteForSeconds(KINDLING_STACK_SECONDS * stacks);
        if (context.finisher()) {
            ArtSupport.hurt(owner, target, context.dealtDamage() * KINDLING_FINISHER_PER_STACK * stacks);
        }
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.FIRE_BLOOM, element,
                target.getBbWidth() * stacks / (float) ArtMath.KINDLING_MAX_STACKS);
    }
}
