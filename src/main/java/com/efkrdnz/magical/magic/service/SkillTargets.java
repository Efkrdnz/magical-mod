package com.efkrdnz.magical.magic.service;

import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.visual.SpellFx;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The one hostility filter and damage helper every new skill uses. */
public final class SkillTargets {
    private SkillTargets() {}

    /** Alive, not the caster, not allied by team, not the caster's tamed animal, not a decoration. */
    public static boolean isHostile(Entity caster, Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive() || target == caster) {
            return false;
        }
        if (target instanceof ArmorStand) {
            return false;
        }
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        if (caster != null) {
            if (caster.isAlliedTo(target)) {
                return false;
            }
            if (target instanceof TamableAnimal tamed && tamed.isOwnedBy(caster instanceof LivingEntity l ? l : null)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isAlly(Entity caster, Entity target) {
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (target == caster) {
            return true;
        }
        if (caster == null) {
            return false;
        }
        if (caster.isAlliedTo(target)) {
            return true;
        }
        return target instanceof TamableAnimal tamed && caster instanceof LivingEntity l && tamed.isOwnedBy(l);
    }

    public static List<LivingEntity> hostilesIn(ServerLevel level, Entity caster, AABB box) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity entity : level.getEntities(caster, box, e -> isHostile(caster, e))) {
            out.add((LivingEntity) entity);
        }
        return out;
    }

    public static List<LivingEntity> hostilesWithin(ServerLevel level, Entity caster, Vec3 centre, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(centre, centre).inflate(radius);
        for (Entity entity : level.getEntities(caster, box, e -> isHostile(caster, e))) {
            if (entity.getBoundingBox().getCenter().distanceToSqr(centre) <= radius * radius) {
                out.add((LivingEntity) entity);
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(centre)));
        return out;
    }

    public static List<LivingEntity> alliesWithin(ServerLevel level, Entity caster, Vec3 centre, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(centre, centre).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, e -> isAlly(caster, e))) {
            if (entity.getBoundingBox().getCenter().distanceToSqr(centre) <= radius * radius) {
                out.add((LivingEntity) entity);
            }
        }
        return out;
    }

    /** Living entities inside a vertical cylinder (radius, y from base to base+height). */
    public static List<LivingEntity> hostilesInCylinder(ServerLevel level, Entity caster, Vec3 base, double radius, double height) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = new AABB(base.x - radius, base.y - 0.5D, base.z - radius, base.x + radius, base.y + height, base.z + radius);
        for (Entity entity : level.getEntities(caster, box, e -> isHostile(caster, e))) {
            double dx = entity.getX() - base.x;
            double dz = entity.getZ() - base.z;
            if (dx * dx + dz * dz <= radius * radius) {
                out.add((LivingEntity) entity);
            }
        }
        return out;
    }

    /** Magic damage attributed to the skill, with the profile's impact grammar at the hit point. */
    public static void hurt(ServerLevel level, Entity attacker, LivingEntity target, float amount, MagicSkillDefinition definition, boolean impactFx) {
        Entity source = attacker != null ? attacker : target;
        MagicDamageService.hurt(target, level.damageSources().indirectMagic(source, source), amount, definition.id());
        if (impactFx) {
            Vec3 hit = target.getBoundingBox().getCenter();
            Vec3 normal = attacker != null ? attacker.position().subtract(hit).normalize() : new Vec3(0.0D, 1.0D, 0.0D);
            SpellFx.impact(level, definition, hit, normal, target, attacker, Math.min(2.0F, 0.6F + amount / 20.0F));
        }
    }

    public static void hurt(ServerLevel level, Entity attacker, LivingEntity target, float amount, ResourceLocation skillId) {
        Entity source = attacker != null ? attacker : target;
        MagicDamageService.hurt(target, level.damageSources().indirectMagic(source, source), amount, skillId);
    }

    /** Push away from (or toward, negative) a point with an upward component. */
    public static void shove(LivingEntity target, Vec3 from, double strength, double up) {
        Vec3 d = target.position().subtract(from);
        d = new Vec3(d.x, 0.0D, d.z);
        if (d.lengthSqr() < 1.0E-4D) {
            d = new Vec3(1.0D, 0.0D, 0.0D);
        }
        d = d.normalize().scale(strength);
        target.push(d.x, up, d.z);
        target.hurtMarked = true;
    }
}
