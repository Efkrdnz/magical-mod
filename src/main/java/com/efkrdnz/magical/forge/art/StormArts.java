package com.efkrdnz.magical.forge.art;

import java.util.ArrayList;
import java.util.List;

import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** STORM's four Arts: the rail, the coil, the delayed bolt and the flurry's third-tick discharge. */
public final class StormArts {

    private static final float RAIL_LENGTH = 8.0f;
    private static final double RAIL_HALF_WIDTH = 1.0;
    private static final int RAIL_CHAINS = 2;
    private static final float RAIL_FRACTION = 0.60f;

    private static final double COIL_RANGE = 5.0;
    private static final int COIL_CHAINS = 4;
    private static final float COIL_FRACTION = 0.45f;

    private static final double SKYFALL_LIFT = 0.75;
    private static final float SKYFALL_RADIUS = 2.0f;
    private static final int SKYFALL_LIFE = 12;
    private static final float SKYFALL_FRACTION = 0.80f;

    private static final int BURST_PULSE = 3;
    private static final double BURST_RADIUS = 2.0;
    private static final float BURST_FRACTION = 0.50f;
    private static final int BOLT_LIFE = 6;

    private StormArts() {}

    /** Rail Lance - the thrust becomes a rail, arcing to two more bodies standing on its eight-block line. */
    public static void railLance(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.RAIL_LANCE, context) || !context.firstBody()) {
            return;
        }
        float damage = context.dealtDamage() * RAIL_FRACTION;
        Vec3 from = ArtSupport.centre(target);
        for (LivingEntity next : ArtSupport.alongLine(level, owner.getEyePosition(), ArtSupport.direction(context),
                RAIL_LENGTH, RAIL_HALF_WIDTH, owner, target, RAIL_CHAINS)) {
            ArtSupport.hurt(owner, next, damage);
            ArtSupport.bolt(level, from, ArtSupport.centre(next), element, BOLT_LIFE);
            from = ArtSupport.centre(next);
        }
    }

    /** Tempest Coil - the finishing spin jumps to four bodies inside five blocks, each for 45%. */
    public static void tempestCoil(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.TEMPEST_COIL, context) || !context.firstBody()) {
            return;
        }
        float damage = context.dealtDamage() * COIL_FRACTION;
        List<LivingEntity> visited = new ArrayList<>();
        visited.add(target);
        LivingEntity from = target;
        for (int chain = 0; chain < COIL_CHAINS; chain++) {
            LivingEntity next = nearestUnvisited(level, owner, from, visited);
            if (next == null) {
                return;
            }
            visited.add(next);
            ArtSupport.hurt(owner, next, damage);
            ArtSupport.bolt(level, ArtSupport.centre(from), ArtSupport.centre(next), element, BOLT_LIFE);
            from = next;
        }
    }

    /** Skyfall - the heavy rising cut launches, and ten ticks later the sky answers for 80%. */
    public static void skyfall(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.SKYFALL, context)) {
            return;
        }
        ArtSupport.push(owner, target, new Vec3(0.0, SKYFALL_LIFT, 0.0));
        ArtSupport.zone(level, owner, ArtSupport.centre(target), ForgeZoneKind.SKYFALL_BOLT, element, SKYFALL_RADIUS,
                SKYFALL_LIFE, context.dealtDamage() * SKYFALL_FRACTION);
    }

    /** Static Burst - the charge builds over the flurry and lets go on the third pulse. */
    public static void staticBurst(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.STATIC_BURST, context)
                || context.targetHitIndex() != BURST_PULSE) {
            return;
        }
        float damage = context.dealtDamage() * BURST_FRACTION;
        Vec3 centre = ArtSupport.centre(target);
        for (LivingEntity caught : ArtSupport.around(level, centre, BURST_RADIUS, owner, target)) {
            ArtSupport.hurt(owner, caught, damage);
            ArtSupport.bolt(level, centre, ArtSupport.centre(caught), element, BOLT_LIFE);
        }
        ArtSupport.hurt(owner, target, damage);
    }

    /** The closest body the coil has not already jumped to, so the chain never doubles back. */
    private static LivingEntity nearestUnvisited(ServerLevel level, ServerPlayer owner, LivingEntity from,
            List<LivingEntity> visited) {
        Vec3 centre = ArtSupport.centre(from);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : ArtSupport.around(level, centre, COIL_RANGE, owner, null)) {
            double distance = ArtSupport.centre(candidate).distanceToSqr(centre);
            if (!visited.contains(candidate) && distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
