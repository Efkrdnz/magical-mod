package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.SovereignAegisEntity;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * What a casting mob can see about the fight, beyond its own health bar.
 *
 * <p>The old scoring read three numbers - distance, health, mana - all of them about the caster. An
 * opponent built on that cannot be responsive, because nothing the player does appears in it. It
 * will keep throwing ordinary spells into a raised ward that deletes every one of them, and it will
 * spend its own apex answer on a threat that any barrier would have stopped.
 *
 * <p>This is the missing half: the board as it stands right now, read fresh each time a skill is
 * chosen. Cheap on purpose - one bounded entity scan - because it runs on every cast decision.
 */
public record MobCombatSense(boolean targetWarded, boolean apexThreatInbound, boolean targetApexReady) {

    /** Nothing known, which scores exactly as the old behaviour did. */
    public static final MobCombatSense BLIND = new MobCombatSense(false, false, false);

    /** How far around itself a caster notices an incoming apex spell. */
    private static final double THREAT_SCAN_RADIUS = 14.0D;

    public static MobCombatSense read(LivingEntity caster, LivingEntity target) {
        if (caster == null || target == null) {
            return BLIND;
        }
        return new MobCombatSense(
                isWarded(target),
                apexThreatNear(caster),
                hasApexAnswerReady(target));
    }

    /**
     * Whether the target is standing behind something only an apex attack gets through.
     *
     * <p>This is the single most important thing a caster can know. While it is true, every spell
     * below the apex it casts is deleted on contact - not reduced, deleted - so casting one is
     * worse than casting nothing: it spends the mana and starts the cooldown for no effect.
     */
    public static boolean isWarded(LivingEntity target) {
        if (target instanceof ServerPlayer player && SovereignAegisEntity.hasUltimate(player)) {
            return true;
        }
        return SovereignAegisEntity.isInsideOffenseBlockingSanctuary(target);
    }

    /**
     * Whether the board just flipped in a way worth abandoning a wind-up for.
     *
     * <p>A boss that finishes its cast timer before noticing the ward went up is not responsive,
     * it is on rails. Both of these are things the player did, deliberately, in the last moment -
     * and both change which spell is correct, so neither is worth spending another second of
     * wind-up on the old answer.
     */
    public boolean demandsAnswer(MobCombatSense previous) {
        return (targetWarded && !previous.targetWarded())
                || (apexThreatInbound && !previous.apexThreatInbound());
    }

    /**
     * Whether an apex spell is already in the air near the caster.
     *
     * <p>Only an apex defence turns one of these aside, so this is when a boss's own ward is worth
     * spending - and, just as importantly, when it is not.
     */
    private static boolean apexThreatNear(LivingEntity caster) {
        AABB box = caster.getBoundingBox().inflate(THREAT_SCAN_RADIUS);
        for (Entity entity : caster.level().getEntities(caster, box)) {
            if (!(entity instanceof CounterableSkillThreat threat)) {
                continue;
            }
            if (threat.counterOwner() == caster) {
                continue;
            }
            if (TierFive.is(threat.counterSkillId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the target still holds an apex skill it could answer with.
     *
     * <p>An apex attack into a player who has one is a trade rather than a hit. It does not stop
     * the caster using it - sometimes forcing that trade is the point - but it is worth knowing
     * before spending the only thing that pierces a ward.
     */
    private static boolean hasApexAnswerReady(LivingEntity target) {
        if (!(target instanceof ServerPlayer player)) {
            return false;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        for (var id : state.unlockedSkills()) {
            if (TierFive.is(id) && !state.isSkillOnCooldown(id)) {
                return true;
            }
        }
        return false;
    }
}
