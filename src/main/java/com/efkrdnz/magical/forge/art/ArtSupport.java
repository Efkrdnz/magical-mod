package com.efkrdnz.magical.forge.art;

import java.util.ArrayList;
import java.util.List;

import com.efkrdnz.magical.entity.ForgeEffectEntity;
import com.efkrdnz.magical.entity.ForgeZoneEntity;
import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeDamageTypes;
import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeIds;
import com.efkrdnz.magical.forge.ForgeTargeting;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;
import com.efkrdnz.magical.magic.MagicDamageService;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The handful of moves every Art needs: finding bodies, hurting them, moving them, and putting a
 * visual on the result. Nothing here decides <em>what</em> an Art does - that lives in the eight
 * per-element classes - and nothing here ever touches a vanilla particle.
 */
public final class ArtSupport {

    /** Every Art's damage is attributed to the strike that produced it. */
    public static final ResourceLocation STRIKE_ID = ForgeIds.id("forge_strike");

    private static final int DEFAULT_EFFECT_LIFE = 8;
    private static final double MIN_DIRECTION = 1.0E-6;

    private ArtSupport() {}

    // --- finding ------------------------------------------------------------------------------

    /** Living bodies within {@code radius} of {@code centre}, minus the owner and one exclusion. */
    public static List<LivingEntity> around(ServerLevel level, Vec3 centre, double radius, Entity owner,
            Entity excluded) {
        AABB area = new AABB(centre.subtract(radius, radius, radius), centre.add(radius, radius, radius));
        List<LivingEntity> found = new ArrayList<>();
        double radiusSqr = radius * radius;
        for (Entity entity : level.getEntities((Entity) null, area, candidate -> candidate instanceof LivingEntity
                living && living.isAlive() && candidate != owner && candidate != excluded)) {
            if (entity.getBoundingBox().getCenter().distanceToSqr(centre) <= radiusSqr) {
                found.add((LivingEntity) entity);
            }
        }
        return found;
    }

    /**
     * Living bodies lying within {@code halfWidth} of the ray from {@code from} out {@code length}
     * blocks along {@code dir}, nearest first. The lances, rifts and flying blades all sweep with
     * this. A {@code limit} of zero or less means every body on the line.
     */
    public static List<LivingEntity> alongLine(ServerLevel level, Vec3 from, Vec3 dir, double length,
            double halfWidth, Entity owner, Entity excluded, int limit) {
        Vec3 forward = normalize(dir);
        Vec3 to = from.add(forward.scale(length));
        AABB area = new AABB(from, to).inflate(halfWidth + 1.0);
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, area, candidate -> candidate instanceof LivingEntity
                living && living.isAlive() && candidate != owner && candidate != excluded)) {
            Vec3 offset = entity.getBoundingBox().getCenter().subtract(from);
            double along = offset.dot(forward);
            if (along < 0.0 || along > length) {
                continue;
            }
            if (offset.subtract(forward.scale(along)).length() <= halfWidth + entity.getBbWidth() * 0.5) {
                found.add((LivingEntity) entity);
            }
        }
        found.sort((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)));
        return limit <= 0 || limit >= found.size() ? found : new ArrayList<>(found.subList(0, limit));
    }

    // --- acting -------------------------------------------------------------------------------

    /**
     * Magic damage credited to the wielder. With no owner online there is nobody to credit, so
     * nothing happens.
     *
     * <p>The source carries the wielder as its causing entity: an Art's splash and a zone's tick are
     * as much the player's kill as the swing that started them, and without it the mob they finish
     * drops no experience and no looting-scaled loot. See {@link ForgeDamageTypes#magic}.</p>
     */
    public static void hurt(ServerPlayer owner, LivingEntity target, float amount) {
        if (owner == null || amount <= 0.0f || !target.isAlive() || !ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        MagicDamageService.hurt(target, ForgeDamageTypes.magic(owner), amount, STRIKE_ID);
    }

    public static void apply(ServerPlayer owner, LivingEntity target, MobEffectInstance effect) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.addEffect(effect, owner);
    }

    /** Sets {@code target} alight for {@code seconds}, the fire Arts' own status. */
    public static void ignite(ServerPlayer owner, Entity target, float seconds) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.igniteForSeconds(seconds);
    }

    /** Deepens {@code target}'s freeze by {@code ticks}, the frost Arts' own status. */
    public static void freeze(ServerPlayer owner, Entity target, int ticks) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.setTicksFrozen(target.getTicksFrozen() + ticks);
    }

    /** Adds velocity and marks the target dirty, so the client sees the shove. */
    public static void push(ServerPlayer owner, Entity target, Vec3 delta) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.setDeltaMovement(target.getDeltaMovement().add(delta));
        target.hurtMarked = true;
    }

    /** Raises {@code target}'s rise to at least {@code lift}, never slowing one already faster. */
    public static void liftTo(ServerPlayer owner, Entity target, double lift) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(movement.x, Math.max(movement.y, lift), movement.z);
        target.hurtMarked = true;
    }

    /** Vanilla knockback away from {@code (x, z)}, so armour and the toughness attribute still apply. */
    public static void knockback(ServerPlayer owner, LivingEntity target, double strength, double x, double z) {
        if (!ForgeTargeting.canAffect(owner, target)) {
            return;
        }
        target.knockback(strength, x, z);
    }

    /** Pulls {@code target} toward {@code centre} at {@code strength} blocks per tick. */
    public static void pullToward(ServerPlayer owner, Entity target, Vec3 centre, double strength) {
        Vec3 offset = centre.subtract(target.getBoundingBox().getCenter());
        if (offset.lengthSqr() < MIN_DIRECTION) {
            return;
        }
        push(owner, target, offset.normalize().scale(strength));
    }

    /** Throws {@code target} away from {@code centre} at {@code strength} blocks per tick. */
    public static void pushFrom(ServerPlayer owner, Entity target, Vec3 centre, double strength) {
        Vec3 offset = target.getBoundingBox().getCenter().subtract(centre);
        Vec3 away = offset.lengthSqr() < MIN_DIRECTION ? new Vec3(0.0, 1.0, 0.0) : offset.normalize();
        push(owner, target, away.scale(strength));
    }

    // --- visuals ------------------------------------------------------------------------------

    public static void burst(ServerLevel level, Vec3 at, ForgeEffectStyle style, ElementDefinition element,
            float scale) {
        burst(level, at, style, element, scale, DEFAULT_EFFECT_LIFE);
    }

    public static void burst(ServerLevel level, Vec3 at, ForgeEffectStyle style, ElementDefinition element,
            float scale, int life) {
        ForgeEffectEntity.impact(level, at, style, element.primaryColor(), element.secondaryColor(), scale, life);
    }

    public static void bolt(ServerLevel level, Vec3 from, Vec3 to, ElementDefinition element, int life) {
        ForgeEffectEntity.fork(level, from, to, element.primaryColor(), life);
    }

    public static void zone(ServerLevel level, ServerPlayer owner, Vec3 at, ForgeZoneKind kind,
            ElementDefinition element, float radius, int life, float power) {
        ForgeZoneEntity.open(level, owner, at, kind, element.primaryColor(), element.secondaryColor(),
                radius, life, power);
    }

    // --- context ------------------------------------------------------------------------------

    /** Whether this press matches the Art's trigger column; a mismatch means the Art does nothing at all. */
    public static boolean triggered(ForgeArt art, StrikeContext context) {
        return art.trigger().matches(context.heavy(), context.finisher());
    }

    /** The weapon's element, or {@code null} when the weapon carries an id this build cannot resolve. */
    public static ElementDefinition element(ForgedWeapon weapon) {
        return ForgeElements.get(weapon.element()).orElse(null);
    }

    public static int grade(ForgedWeapon weapon) {
        return weapon.grade().ordinal();
    }

    public static Vec3 centre(Entity entity) {
        return entity.getBoundingBox().getCenter();
    }

    /** The strike's own facing, never zero-length. */
    public static Vec3 direction(StrikeContext context) {
        return normalize(context.direction());
    }

    private static Vec3 normalize(Vec3 dir) {
        return dir == null || dir.lengthSqr() < MIN_DIRECTION ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
    }
}
