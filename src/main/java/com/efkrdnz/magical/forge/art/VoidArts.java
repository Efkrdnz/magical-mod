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

/** VOID's four Arts: the blink, the gravity well, the delayed rift and the crescent's pull. */
public final class VoidArts {

    private static final double BLINK_DISTANCE = 2.0;
    /** How finely the blink path is walked; a quarter-block step cannot skip a one-block wall. */
    private static final int BLINK_STEPS = 8;
    private static final float BLINK_BONUS = 0.20f;

    private static final float ORBIT_RADIUS = 2.5f;
    private static final int ORBIT_LIFE = 30;

    private static final double RIFT_LENGTH = 4.0;
    private static final float RIFT_RADIUS = 2.0f;
    private static final int RIFT_LIFE = 14;
    private static final float RIFT_FRACTION = 0.40f;

    private static final double HORIZON_RADIUS = 4.0;
    private static final double HORIZON_PULL = 0.35;
    private static final int HORIZON_WITHER_TICKS = 60;

    private VoidArts() {}

    /**
     * Phase Pierce - the heavy thrust steps the wielder two blocks clean through the target, which
     * takes an extra fifth of the blow on the way past. The step stops short of any wall, and is
     * refused outright when there is nowhere at all to stand.
     */
    public static void phasePierce(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.PHASE_PIERCE, context)) {
            return;
        }
        ArtSupport.hurt(owner, target, context.dealtDamage() * BLINK_BONUS);
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.VOID_IMPLOSION, element,
                target.getBbWidth());
        if (!context.firstBody()) {
            return;
        }
        Vec3 step = blinkTo(level, owner, ArtSupport.direction(context));
        if (step != null) {
            owner.teleportTo(step.x, step.y, step.z);
            ArtSupport.burst(level, step, ForgeEffectStyle.VOID_IMPLOSION, element, 1.0f);
        }
    }

    /** Null Orbit - the finishing spin sinks a gravity well that drags bodies in and drinks their mana. */
    public static void nullOrbit(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.NULL_ORBIT, context) || !context.firstBody()) {
            return;
        }
        ArtSupport.zone(level, owner, owner.position(), ForgeZoneKind.NULL_ORBIT, element, ORBIT_RADIUS, ORBIT_LIFE,
                0.0f);
    }

    /** Abyss Rift - the heavy slam tears a four-block seam that collapses ten ticks later for 40%. */
    public static void abyssRift(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.ABYSS_RIFT, context) || !context.firstBody()) {
            return;
        }
        Vec3 forward = ArtSupport.direction(context);
        Vec3 ground = new Vec3(target.getX(), target.getBoundingBox().minY, target.getZ());
        Vec3 seam = ground.add(forward.x * RIFT_LENGTH * 0.5, 0.0, forward.z * RIFT_LENGTH * 0.5);
        ArtSupport.zone(level, owner, seam, ForgeZoneKind.ABYSS_RIFT, element, RIFT_RADIUS, RIFT_LIFE,
                context.dealtDamage() * RIFT_FRACTION);
    }

    /**
     * Event Horizon - the crescent drags everything within four blocks onto itself and withers it.
     *
     * <p>One horizon per press, anchored on the first body the crescent opened. The wither is the
     * touch's own and lands on everything the crescent cuts, but the pull is swept exactly once: a
     * heavy wave through five clustered mobs would otherwise pull each of them toward each of the
     * others, flinging the cluster apart instead of dragging it together and sending twenty
     * needless motion corrections down the wire.</p>
     */
    public static void eventHorizon(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.EVENT_HORIZON, context)) {
            return;
        }
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.WITHER, HORIZON_WITHER_TICKS, 0));
        if (!context.firstBody()) {
            return;
        }
        Vec3 centre = ArtSupport.centre(target);
        for (LivingEntity pulled : ArtSupport.around(level, centre, HORIZON_RADIUS, owner, target)) {
            ArtSupport.pullToward(owner, pulled, centre, HORIZON_PULL);
            ArtSupport.apply(owner, pulled, new MobEffectInstance(MobEffects.WITHER, HORIZON_WITHER_TICKS, 0));
        }
        ArtSupport.burst(level, centre, ForgeEffectStyle.VOID_IMPLOSION, element, (float) HORIZON_RADIUS * 0.5f);
    }

    /**
     * As far along the strike as the wielder's own body will actually fit, up to
     * {@link #BLINK_DISTANCE}, or {@code null} when even the first step is blocked.
     *
     * <p>Walked outward in short steps and testing the whole player box at each one, because
     * {@code ServerPlayer.teleportTo} resolves nothing: it drops the player wherever it is told. A
     * single ray at eye height passes over a two-block ledge, a stair run or a window sill and lands
     * the feet inside stone, which suffocates. Testing the swept box instead settles both questions
     * at once - nothing solid in the way, and somewhere to stand at the end - and shortening rather
     * than refusing keeps the Art useful in the cramped spaces it is most often used in.</p>
     */
    private static Vec3 blinkTo(ServerLevel level, ServerPlayer owner, Vec3 forward) {
        Vec3 origin = owner.position();
        Vec3 furthest = null;
        for (int step = 1; step <= BLINK_STEPS; step++) {
            double distance = BLINK_DISTANCE * step / (double) BLINK_STEPS;
            Vec3 candidate = origin.add(forward.x * distance, 0.0, forward.z * distance);
            if (!fits(level, owner, origin, candidate)) {
                break; // a wall: stop at the last step that fitted rather than stepping through it
            }
            furthest = candidate;
        }
        return furthest;
    }

    /** Whether the wielder's own bounding box, moved to {@code destination}, stands in open space. */
    private static boolean fits(ServerLevel level, ServerPlayer owner, Vec3 origin, Vec3 destination) {
        return level.noCollision(owner, owner.getBoundingBox().move(destination.subtract(origin)));
    }
}
