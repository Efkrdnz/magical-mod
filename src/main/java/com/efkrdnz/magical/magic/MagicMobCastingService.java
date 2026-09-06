package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Opponent-mob casting: every skill is chosen by the {@link MobCastProfile} its handler declares
 * and cast through the same registry handler players use, with a {@link CastContext} built from
 * the mob.
 */
public final class MagicMobCastingService {
    private MagicMobCastingService() {}

    public static MagicSkillDefinition chooseSkill(LivingEntity caster, PlayerMagicState state, LivingEntity target, int difficulty) {
        double distance = target == null ? 0.0D : caster.distanceTo(target);
        float healthRatio = caster.getHealth() / Math.max(1.0F, caster.getMaxHealth());
        float manaRatio = state.mana() / (float) Math.max(1, state.maxMana());
        List<MagicSkillDefinition> available = state.unlockedSkills().stream()
                .map(MagicContent::get)
                .filter(skill -> canMobUse(caster, state, skill))
                .filter(skill -> difficulty >= 2 || caster.getRandom().nextFloat() > 0.12F)
                .toList();
        if (available.isEmpty()) {
            return null;
        }

        if (difficulty <= 1) {
            if (caster.getRandom().nextFloat() < (difficulty == 0 ? 0.32F : 0.16F)) {
                return null;
            }
            if (caster.getRandom().nextFloat() < (difficulty == 0 ? 0.45F : 0.25F)) {
                return available.get(caster.getRandom().nextInt(available.size()));
            }
        }

        if (healthRatio < (0.24F + difficulty * 0.055F) || (difficulty >= 4 && state.barrier() < state.maxBarrier() * 0.35F)) {
            MagicSkillDefinition defensive = bestByScore(available, skill -> role(skill) == MobCastProfile.Role.DEFENCE, distance, healthRatio, manaRatio, difficulty);
            if (defensive != null) {
                return defensive;
            }
        }

        MagicSkillDefinition inRange = bestByScore(available, skill -> inRange(skill, distance) && role(skill) != MobCastProfile.Role.DEFENCE, distance, healthRatio, manaRatio, difficulty);
        if (inRange != null) {
            return inRange;
        }
        return bestByScore(available, skill -> true, distance, healthRatio, manaRatio, difficulty);
    }

    public static boolean hasUsableProjectile(LivingEntity caster, PlayerMagicState state) {
        return hasUsable(caster, state, skill -> profile(skill).maxRange() >= 8.0F && role(skill) == MobCastProfile.Role.ATTACK);
    }

    public static boolean hasUsableCloseBurst(LivingEntity caster, PlayerMagicState state) {
        return hasUsable(caster, state, skill -> profile(skill).minRange() <= 2.0F && role(skill) == MobCastProfile.Role.ATTACK);
    }

    public static boolean castBestDefense(LivingEntity caster, PlayerMagicState state, LivingEntity target, int difficulty) {
        List<MagicSkillDefinition> defenses = state.unlockedSkills().stream()
                .map(MagicContent::get)
                .filter(skill -> canMobUse(caster, state, skill))
                .filter(skill -> role(skill) == MobCastProfile.Role.DEFENCE)
                .toList();
        if (defenses.isEmpty()) {
            return false;
        }
        float healthRatio = caster.getHealth() / Math.max(1.0F, caster.getMaxHealth());
        float manaRatio = state.mana() / (float) Math.max(1, state.maxMana());
        MagicSkillDefinition best = bestByScore(defenses, skill -> true, target == null ? 0.0D : caster.distanceTo(target), healthRatio, manaRatio, difficulty);
        return cast(caster, state, best, target == null ? caster : target);
    }

    private static boolean hasUsable(LivingEntity caster, PlayerMagicState state, Predicate<MagicSkillDefinition> predicate) {
        return state.unlockedSkills().stream()
                .map(MagicContent::get)
                .anyMatch(skill -> canMobUse(caster, state, skill) && predicate.test(skill));
    }

    private static MagicSkillDefinition bestByScore(List<MagicSkillDefinition> skills, Predicate<MagicSkillDefinition> filter, double distance, float healthRatio, float manaRatio, int difficulty) {
        return skills.stream()
                .filter(filter)
                .max(Comparator.comparingDouble(skill -> scoreSkill(skill, distance, healthRatio, manaRatio, difficulty)))
                .orElse(null);
    }

    private static MobCastProfile profile(MagicSkillDefinition skill) {
        return SkillCastRegistry.get(skill.id()).mob();
    }

    private static MobCastProfile.Role role(MagicSkillDefinition skill) {
        return profile(skill).role();
    }

    private static boolean inRange(MagicSkillDefinition skill, double distance) {
        MobCastProfile p = profile(skill);
        return p.selfCast() || (distance >= p.minRange() && distance <= p.maxRange());
    }

    private static double scoreSkill(MagicSkillDefinition skill, double distance, float healthRatio, float manaRatio, int difficulty) {
        MagicSkillResolvedStats base = skill.resolve(MagicSkillTuning.DEFAULT);
        MobCastProfile p = profile(skill);
        double score = Math.max(0.0D, base.damage()) * (difficulty >= 3 ? 1.25D : 0.85D);
        score += Math.max(0, skill.tier()) * (difficulty >= 4 ? 4.0D : 2.0D);
        score += base.knockback() * (distance < 4.0D ? 5.5D : 1.5D);
        score -= base.manaCost() * (difficulty >= 3 && manaRatio < 0.45F ? 0.22D : 0.05D);
        score += inRange(skill, distance) ? 12.0D : -14.0D;
        switch (p.role()) {
            case DEFENCE -> {
                score += base.barrierRestore() * (healthRatio < 0.45F ? 1.2D : 0.45D);
                score += (1.0F - healthRatio) * (difficulty >= 3 ? 24.0D : 10.0D);
            }
            case CONTROL -> score += difficulty >= 2 ? 6.0D : 1.0D;
            case SUMMON -> score += difficulty >= 3 ? 8.0D : 3.0D;
            case UTILITY -> score -= 6.0D;
            default -> { }
        }
        return score;
    }

    /** Cast a skill through its registry handler with a mob-built context. */
    public static boolean cast(LivingEntity caster, PlayerMagicState state, MagicSkillDefinition definition, LivingEntity target) {
        if (!(caster.level() instanceof ServerLevel level) || definition == null || target == null || !target.isAlive()) {
            return false;
        }
        if (!canMobUse(caster, state, definition)) {
            return false;
        }
        SkillCastHandler handler = SkillCastRegistry.get(definition.id());
        MobCastProfile profile = handler.mob();
        MagicSkillResolvedStats stats = definition.resolve(state.tuningFor(definition.id()));
        if (!state.spendMana(stats.manaCost())) {
            return false;
        }
        Vec3 origin = caster.getEyePosition();
        Vec3 goal = profile.needsGroundTarget() ? target.position() : target.getEyePosition();
        Vec3 aimDir = goal.subtract(origin);
        aimDir = aimDir.lengthSqr() < 1.0E-6D ? caster.getLookAngle() : aimDir.normalize();
        caster.lookAt(EntityAnchorArgument.Anchor.EYES, goal);
        AimResolver.Result aim = AimResolver.resolve(level, caster, aimDir, handler.aimRange(), handler.aimTolerance(), handler.aimDropsToGround(), 8, e -> e != caster);
        long seed = level.getGameTime() * 31L + caster.getId();
        CastContext ctx = CastContext.forMob(caster, state, definition, stats, VisualProfiles.of(definition), aim, seed, aimDir);
        SpellFx.windup(caster, definition, aim.point(), aimDir, false);
        CastResult result;
        try {
            result = handler.cast(ctx);
        } catch (RuntimeException exception) {
            MagicalMod.LOGGER.error("Mob cast handler for {} threw", definition.id(), exception);
            result = CastResult.FAILED;
        }
        switch (result) {
            case FAILED -> {
                state.setMana(state.mana() + stats.manaCost());
                return false;
            }
            case CONSUMED_NO_COOLDOWN -> {
                SpellFx.release(caster, definition, aimDir);
                state.setSkillCooldown(definition.id(), Math.min(40, stats.cooldownTicks()));
                return true;
            }
            case SUCCESS -> {
                SpellFx.release(caster, definition, aimDir);
                state.setSkillCooldown(definition.id(), stats.cooldownTicks());
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    /** Mobs never need a held weapon in the new roster. */
    public static boolean needsWeapon(PlayerMagicState state) {
        return false;
    }

    public static boolean canMobUse(LivingEntity caster, PlayerMagicState state, MagicSkillDefinition definition) {
        if (definition == null || !state.hasUnlocked(definition.id()) || state.isSkillOnCooldown(definition.id())) {
            return false;
        }
        if (MagicContent.isAuthoritySkill(definition.id()) || MagicContent.isSubSkill(definition.id())) {
            return false;
        }
        if (MagicContent.CREATED_SKILLS.contains(definition.id()) || MagicContent.VAULT_OF_AVARICE.id().equals(definition.id())) {
            return false;
        }
        if (!SkillCastRegistry.has(definition.id()) || !SkillCastRegistry.get(definition.id()).mob().usable()) {
            return false;
        }
        MagicSkillResolvedStats stats = definition.resolve(state.tuningFor(definition.id()));
        return state.mana() >= stats.manaCost();
    }
}
