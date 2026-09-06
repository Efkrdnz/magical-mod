package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Everything a cast handler needs. {@code caster} is whoever casts (a player or an opponent mob);
 * {@code player} is the same entity when it is a ServerPlayer, else null, so handlers reach for it
 * only for player-only plumbing (messages, hold keys). {@code aim} is resolved by the dispatcher
 * using the handler range/tolerance so it never fails (entity near the ray, block face, ground,
 * ray end); {@code aimDirection} is the look vector for players and the direction to the target
 * for mobs.
 */
public record CastContext(
        LivingEntity caster,
        ServerPlayer player,
        PlayerMagicState state,
        MagicSkillDefinition definition,
        MagicSkillResolvedStats stats,
        boolean sneak,
        int slot,
        VisualProfile profile,
        AimResolver.Result aim,
        long seed,
        Vec3 aimDirection) {

    public static CastContext forPlayer(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, MagicSkillResolvedStats stats, boolean sneak, int slot, VisualProfile profile, AimResolver.Result aim, long seed) {
        return new CastContext(player, player, state, definition, stats, sneak, slot, profile, aim, seed, player.getLookAngle().normalize());
    }

    public static CastContext forMob(LivingEntity caster, PlayerMagicState state, MagicSkillDefinition definition, MagicSkillResolvedStats stats, VisualProfile profile, AimResolver.Result aim, long seed, Vec3 aimDirection) {
        return new CastContext(caster, caster instanceof ServerPlayer p ? p : null, state, definition, stats, false, -1, profile, aim, seed, aimDirection.normalize());
    }

    public ServerLevel level() {
        return (ServerLevel) caster.level();
    }

    public Vec3 look() {
        return aimDirection;
    }

    /** Look vector, reversed when the sneak variation of the skill is "aim it backward". */
    public Vec3 lookOrBack() {
        return sneak ? aimDirection.scale(-1.0D) : aimDirection;
    }

    public Vec3 eye() {
        return caster.getEyePosition();
    }

    public Vec3 feet() {
        return caster.position();
    }

    public int skillIndex() {
        return com.efkrdnz.magical.magic.MagicContent.skillIndex(definition.id());
    }

    public float damage() {
        return stats.damage();
    }

    public float size() {
        return stats.size();
    }

    public int duration() {
        return stats.durationTicks();
    }
}
