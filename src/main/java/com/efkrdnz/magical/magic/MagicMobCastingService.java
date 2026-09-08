package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.ascendant.AscendantTier;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Opponent-mob casting: every skill is chosen by the {@link MobCastProfile} its handler declares
 * and cast through the same registry handler players use, with a {@link CastContext} built from
 * the mob.
 */
public final class MagicMobCastingService {

    /**
     * The one rule that overrides every other consideration, as four numbers.
     *
     * <p>A warded target deletes everything below the apex, so the apex is not merely better
     * there - it is the only thing that does anything at all.
     */
    private static final double APEX_PIERCE_BONUS = 60.0D;

    /** And its opposite: an ordinary spell into a ward is worse than casting nothing. */
    private static final double WARDED_WASTE_PENALTY = -80.0D;

    /** An apex spell already in the air is the one thing an apex guard exists for. */
    private static final double APEX_GUARD_BONUS = 45.0D;

    /** Enough to lose a tie while nothing is stopping an ordinary spell. Not enough to hoard. */
    private static final double APEX_RESERVE_PENALTY = -10.0D;

    /** They still hold an answer. Worth forcing sometimes, never worth preferring. */
    private static final double APEX_CONTESTED_PENALTY = -6.0D;

    private MagicMobCastingService() {}

    public static MagicSkillDefinition chooseSkill(LivingEntity caster, PlayerMagicState state, LivingEntity target, int difficulty) {
        return chooseSkill(caster, state, target, difficulty, MobCombatSense.read(caster, target));
    }

    public static MagicSkillDefinition chooseSkill(LivingEntity caster, PlayerMagicState state,
            LivingEntity target, int difficulty, MobCombatSense sense) {
        double distance = target == null ? 0.0D : caster.distanceTo(target);
        float healthRatio = caster.getHealth() / Math.max(1.0F, caster.getMaxHealth());
        float manaRatio = state.mana() / (float) Math.max(1, state.maxMana());
        List<MagicSkillDefinition> available = state.unlockedSkills().stream()
                .map(MagicContent::get)
                .filter(skill -> canMobUse(caster, state, skill, difficulty))
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
            MagicSkillDefinition defensive = bestByScore(available, skill -> role(skill) == MobCastProfile.Role.DEFENCE, distance, healthRatio, manaRatio, difficulty, sense);
            if (defensive != null) {
                return defensive;
            }
        }

        MagicSkillDefinition inRange = bestByScore(available, skill -> inRange(skill, distance) && role(skill) != MobCastProfile.Role.DEFENCE, distance, healthRatio, manaRatio, difficulty, sense);
        if (inRange != null) {
            return inRange;
        }
        return bestByScore(available, skill -> true, distance, healthRatio, manaRatio, difficulty, sense);
    }

    public static boolean hasUsableProjectile(LivingEntity caster, PlayerMagicState state, int difficulty) {
        return hasUsable(caster, state, difficulty, skill -> profile(skill).maxRange() >= 8.0F && role(skill) == MobCastProfile.Role.ATTACK);
    }

    public static boolean hasUsableCloseBurst(LivingEntity caster, PlayerMagicState state, int difficulty) {
        return hasUsable(caster, state, difficulty, skill -> profile(skill).minRange() <= 2.0F && role(skill) == MobCastProfile.Role.ATTACK);
    }

    /**
     * Casts the best escape it has, if it has one.
     *
     * <p>Shares the shape of {@link #castBestDefense} but filters on MOBILITY: a boss that eats a
     * heavy hit should blink out of the follow-up rather than stand in it.
     */
    public static boolean castBestEscape(LivingEntity caster, PlayerMagicState state, LivingEntity target, int difficulty) {
        List<MagicSkillDefinition> escapes = state.unlockedSkills().stream()
                .map(MagicContent::get)
                .filter(skill -> canMobUse(caster, state, skill, difficulty))
                .filter(skill -> role(skill) == MobCastProfile.Role.MOBILITY)
                .toList();
        if (escapes.isEmpty()) {
            return false;
        }
        float healthRatio = caster.getHealth() / Math.max(1.0F, caster.getMaxHealth());
        float manaRatio = state.mana() / (float) Math.max(1, state.maxMana());
        MagicSkillDefinition best = bestByScore(escapes, skill -> true,
                target == null ? 0.0D : caster.distanceTo(target), healthRatio, manaRatio, difficulty,
                MobCombatSense.read(caster, target));
        return cast(caster, state, best, target == null ? caster : target, difficulty);
    }

    public static boolean castBestDefense(LivingEntity caster, PlayerMagicState state, LivingEntity target, int difficulty) {
        List<MagicSkillDefinition> defenses = state.unlockedSkills().stream()
                .map(MagicContent::get)
                .filter(skill -> canMobUse(caster, state, skill, difficulty))
                .filter(skill -> role(skill) == MobCastProfile.Role.DEFENCE)
                .toList();
        if (defenses.isEmpty()) {
            return false;
        }
        float healthRatio = caster.getHealth() / Math.max(1.0F, caster.getMaxHealth());
        float manaRatio = state.mana() / (float) Math.max(1, state.maxMana());
        MagicSkillDefinition best = bestByScore(defenses, skill -> true, target == null ? 0.0D : caster.distanceTo(target), healthRatio, manaRatio, difficulty, MobCombatSense.read(caster, target));
        return cast(caster, state, best, target == null ? caster : target, difficulty);
    }

    private static boolean hasUsable(LivingEntity caster, PlayerMagicState state, int difficulty,
            Predicate<MagicSkillDefinition> predicate) {
        return state.unlockedSkills().stream()
                .map(MagicContent::get)
                .anyMatch(skill -> canMobUse(caster, state, skill, difficulty) && predicate.test(skill));
    }

    private static MagicSkillDefinition bestByScore(List<MagicSkillDefinition> skills, Predicate<MagicSkillDefinition> filter, double distance, float healthRatio, float manaRatio, int difficulty, MobCombatSense sense) {
        return skills.stream()
                .filter(filter)
                .max(Comparator.comparingDouble(skill -> scoreSkill(skill, distance, healthRatio, manaRatio, difficulty, sense)))
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

    private static double scoreSkill(MagicSkillDefinition skill, double distance, float healthRatio, float manaRatio, int difficulty, MobCombatSense sense) {
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
            case MOBILITY -> {
                // Worth casting for reasons that have nothing to do with the target. Scored by how
                // much trouble it is in, so a healthy boss does not blink around aimlessly.
                score += (1.0F - healthRatio) * (difficulty >= 4 ? 30.0D : 12.0D);
                score -= healthRatio * 14.0D;
            }
            case UTILITY -> score -= 6.0D;
            default -> { }
        }
        return score + apexAdjustment(skill, p, sense);
    }

    /**
     * The apex rule, as the caster sees it.
     *
     * <p>Everything above this line is about the caster - its distance, its health, its mana. None
     * of it changes when the player does something, which is why the old opponent could not be
     * responsive: nothing the player did appeared in the arithmetic at all.
     *
     * <p>Three readings, in the order they matter.
     *
     * <p>A warded target deletes every spell below the apex, so casting one is worse than casting
     * nothing - it spends the mana and starts the cooldown for no effect. That gets the largest
     * penalty in the whole function, because it is the only case where the correct play is to cast
     * one specific thing or to hold.
     *
     * <p>An apex spell already in the air is the only thing an apex defence is for. Anything else
     * raised against it is simply gone through.
     *
     * <p>And when neither is true the apex is worth holding. A boss that opens with its one
     * piercing spell has nothing left for the moment the ward goes up - which is precisely the
     * moment it needed it.
     */
    private static double apexAdjustment(MagicSkillDefinition skill, MobCastProfile profile, MobCombatSense sense) {
        boolean apex = TierFive.is(skill);
        boolean offensive = profile.role() == MobCastProfile.Role.ATTACK
                || profile.role() == MobCastProfile.Role.CONTROL;
        double adjustment = 0.0D;
        if (sense.targetWarded() && offensive) {
            adjustment += apex ? APEX_PIERCE_BONUS : WARDED_WASTE_PENALTY;
        }
        if (sense.apexThreatInbound() && profile.role() == MobCastProfile.Role.DEFENCE) {
            // A non-apex guard is not useless here - it still eats whatever else is being thrown -
            // so it gets a share rather than nothing.
            adjustment += apex ? APEX_GUARD_BONUS : APEX_GUARD_BONUS * 0.35D;
        }
        if (apex && offensive && !sense.targetWarded()) {
            adjustment += APEX_RESERVE_PENALTY;
        }
        if (apex && offensive && sense.targetApexReady()) {
            adjustment += APEX_CONTESTED_PENALTY;
        }
        return adjustment;
    }

    /** Cast a skill through its registry handler with a mob-built context. */
    public static boolean cast(LivingEntity caster, PlayerMagicState state, MagicSkillDefinition definition,
            LivingEntity target, int difficulty) {
        if (!(caster.level() instanceof ServerLevel level) || definition == null || target == null || !target.isAlive()) {
            return false;
        }
        if (!canMobUse(caster, state, definition, difficulty)) {
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

    /** Ordinary mobs, which reach no further than they ever have. */
    public static boolean canMobUse(LivingEntity caster, PlayerMagicState state, MagicSkillDefinition definition) {
        return canMobUse(caster, state, definition, 0);
    }

    /**
     * Whether {@code caster} may cast this, at this difficulty.
     *
     * <p>Ordinary mobs and clones are held to the roster they always were. Ascendants reach past it
     * - the authority skills, the sub-skills and the created fusions - because a boss that can only
     * cast what a zombie can cast is not a boss.
     */
    public static boolean canMobUse(LivingEntity caster, PlayerMagicState state,
            MagicSkillDefinition definition, int difficulty) {
        if (definition == null || !state.hasUnlocked(definition.id()) || state.isSkillOnCooldown(definition.id())) {
            return false;
        }
        if (isNeverCastableByMobs(definition.id())) {
            return false;
        }
        if (!AscendantTier.isAscendant(difficulty)) {
            if (MagicContent.isAuthoritySkill(definition.id()) || MagicContent.isSubSkill(definition.id())) {
                return false;
            }
            if (MagicContent.CREATED_SKILLS.contains(definition.id())) {
                return false;
            }
        }
        if (!SkillCastRegistry.has(definition.id()) || !SkillCastRegistry.get(definition.id()).mob().usable()) {
            return false;
        }
        MagicSkillResolvedStats stats = definition.resolve(state.tuningFor(definition.id()));
        return state.mana() >= stats.manaCost();
    }

    /**
     * The permanent denylist, which no difficulty lifts.
     *
     * <p>Both Perfect Seals end a fight rather than shape it: a sealed player cannot cast at all,
     * and a sealed boss cannot be damaged. Either direction leaves nobody playing. Vault of Avarice
     * is mana storage with no combat meaning on a mob.
     *
     * <p>The pair of seal ids mirrors {@code MagicCastingService.isPerfectSealSubSkill} - there are
     * two of them, and missing either one is the whole failure.
     */
    public static boolean isNeverCastableByMobs(ResourceLocation id) {
        return MagicContent.GABRIEL_PERFECT_SEAL.id().equals(id)
                || MagicContent.AEGIS_PERFECT_SEAL.id().equals(id)
                || MagicContent.VAULT_OF_AVARICE.id().equals(id);
    }
}
