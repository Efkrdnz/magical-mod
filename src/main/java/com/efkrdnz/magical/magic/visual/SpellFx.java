package com.efkrdnz.magical.magic.visual;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.visual.ProfileCues.FirstPersonPreset;
import com.efkrdnz.magical.magic.visual.ProfileCues.SoundCue;
import com.efkrdnz.magical.network.FirstPersonEffectPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.VisualCuePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side facade: the one place cast/entity/damage code goes to for visuals, first-person
 * feedback and sound. Everything resolves from the skill's VisualProfile.
 */
public final class SpellFx {
    private static final double RANGE = 64.0D;

    private SpellFx() {}

    private static int seedFor(Level level, Vec3 pos) {
        long v = level.getGameTime() * 31L + (long) (pos.x * 7.0D) + (long) (pos.z * 13.0D) + (long) (pos.y * 3.0D);
        return (int) Math.floorMod(v * 0x9E3779B97F4A7C15L >>> 40, 64L);
    }

    private static void cue(ServerLevel level, VisualProfile profile, int cue, Vec3 pos, Vec3 dir, int victimId, float scale, boolean sneak) {
        int index = VisualProfiles.indexOf(profile.skillId());
        if (index < 0) {
            return;
        }
        MagicalNetwork.sendVisualCue(level, pos, RANGE, new VisualCuePayload(index, cue, pos, dir, seedFor(level, pos), victimId, scale, sneak));
    }

    private static void play(Level level, Vec3 pos, SoundCue cue, float volumeScale, float pitchOffset) {
        if (cue == null) {
            return;
        }
        float jitter = (level.random.nextFloat() - 0.5F) * 2.0F * cue.jitter();
        level.playSound(null, pos.x, pos.y, pos.z, cue.sound(), SoundSource.PLAYERS, cue.volume() * volumeScale, Mth.clamp(cue.pitch() + jitter + pitchOffset, 0.5F, 2.0F));
    }

    private static void firstPerson(ServerPlayer player, VisualProfile profile, FirstPersonPreset preset, FxKinds.Overlay overlay, ColorRole role, int hitYaw) {
        if (preset == null || preset == FirstPersonPreset.NONE) {
            return;
        }
        FirstPersonEffectPayload payload = new FirstPersonEffectPayload(profile.color(role), preset.overlayTicks(), preset.alpha(), preset.shakeTicks(), preset.shakeStrength(), preset.freezeTicks(), preset.fovKick())
                .withOverlay(overlay.id(), Math.min(63, 30 + profile.tier().tier() * 8), hitYaw);
        MagicalNetwork.playFirstPersonEffect(player, payload);
    }

    /** A screen overlay of the skill's palette on one player (zones, statuses). */
    public static void overlay(ServerPlayer player, MagicSkillDefinition definition, FxKinds.Overlay kind, int ticks, float alpha, ColorRole role) {
        VisualProfile profile = VisualProfiles.of(definition);
        FirstPersonEffectPayload payload = new FirstPersonEffectPayload(profile.color(role), ticks, alpha, 0, 0.0F, 0, 0.0F)
                .withOverlay(kind.id(), 40, FirstPersonEffectPayload.OMNI);
        MagicalNetwork.playFirstPersonEffect(player, payload);
    }

    /** Cast committed: circle inks in at the anchor, rune motes lift, school bed + cast accent play. */
    public static void windup(LivingEntity player, MagicSkillDefinition definition, Vec3 aimPos, Vec3 aimDir, boolean sneak) {
        VisualProfile profile = VisualProfiles.of(definition);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 anchorPos = switch (profile.anchor()) {
            case EYE_FORWARD -> player.getEyePosition().add(aimDir.normalize().scale(0.9D)).add(0.0D, -0.15D, 0.0D);
            case AIM_SURFACE, SKY -> aimPos;
            case NONE -> null;
            default -> player.position().add(0.0D, 0.06D, 0.0D);
        };
        if (anchorPos != null) {
            cue(level, profile, VisualCuePayload.CUE_CAST_WINDUP, anchorPos, aimDir, -1, 1.0F, sneak);
            if (profile.anchor() == CircleAnchor.BOTH) {
                cue(level, profile, VisualCuePayload.CUE_CAST_WINDUP, player.getEyePosition().add(aimDir.normalize().scale(0.9D)), aimDir, -1, 0.5F, sneak);
            }
        }
        play(level, player.position(), profile.sounds().bed(), 0.8F, 0.0F);
        play(level, player.position(), profile.sounds().cast(), 1.0F, 0.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            firstPerson(serverPlayer, profile, profile.firstPerson().cast(), profile.firstPerson().castOverlay(), ColorRole.DIM, FirstPersonEffectPayload.OMNI);
        }
    }

    /** Release beat: muzzle flash at the hand, release accent, caster preset. */
    public static void release(LivingEntity player, MagicSkillDefinition definition, Vec3 aimDir) {
        VisualProfile profile = VisualProfiles.of(definition);
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = player.getEyePosition().add(aimDir.normalize().scale(0.8D)).add(0.0D, -0.2D, 0.0D);
        cue(level, profile, VisualCuePayload.CUE_RELEASE, hand, aimDir, -1, 1.0F, false);
        play(level, player.position(), profile.sounds().release(), 1.0F, 0.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            firstPerson(serverPlayer, profile, profile.release().casterPreset(), profile.release().casterOverlay(), ColorRole.HOT, FirstPersonEffectPayload.OMNI);
        }
    }

    /** Impact grammar at a point: flash, mark on the face, delivery-circle stamp, matter burst, presets, sound. */
    public static void impact(ServerLevel level, MagicSkillDefinition definition, Vec3 hitPos, Vec3 faceNormal, Entity victim, Entity attacker, float scale) {
        VisualProfile profile = VisualProfiles.of(definition);
        cue(level, profile, VisualCuePayload.CUE_IMPACT, hitPos, faceNormal, victim != null ? victim.getId() : -1, Math.max(0.3F, scale), false);
        play(level, hitPos, profile.sounds().impact(), 1.0F, 0.0F);
        if (attacker instanceof ServerPlayer caster) {
            firstPerson(caster, profile, profile.impact().casterPreset(), FxKinds.Overlay.VIGNETTE, ColorRole.BRIGHT, FirstPersonEffectPayload.OMNI);
        }
        if (victim instanceof ServerPlayer target) {
            int yawBucket = FirstPersonEffectPayload.OMNI;
            if (attacker != null) {
                Vec3 to = attacker.position().subtract(target.position());
                float hitYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z)) - target.getYRot();
                yawBucket = Math.floorMod(Math.round(Mth.wrapDegrees(hitYaw) / 360.0F * 32.0F), 32) & 31;
            }
            firstPerson(target, profile, profile.impact().victimPreset(), profile.impact().victimOverlay(), ColorRole.DIM, yawBucket);
        }
    }

    public static void impact(ServerLevel level, MagicSkillDefinition definition, Vec3 hitPos, Vec3 faceNormal, Entity victim, Entity attacker) {
        impact(level, definition, hitPos, faceNormal, victim, attacker, 1.0F);
    }

    /** A persistent decal at a surface (scorch, frost, ink) that outlives the flash. */
    public static void decal(ServerLevel level, MagicSkillDefinition definition, Vec3 pos, Vec3 normal, float scale) {
        cue(level, VisualProfiles.of(definition), VisualCuePayload.CUE_DECAL, pos, normal, -1, scale, false);
    }

    public static void barrierHit(ServerLevel level, MagicSkillDefinition definition, Vec3 pos, Vec3 normal) {
        cue(level, VisualProfiles.of(definition), VisualCuePayload.CUE_BARRIER_HIT, pos, normal, -1, 1.0F, false);
    }

    public static void zoneTick(ServerLevel level, MagicSkillDefinition definition, Vec3 pos, float scale) {
        cue(level, VisualProfiles.of(definition), VisualCuePayload.CUE_ZONE_TICK, pos, new Vec3(0.0D, 1.0D, 0.0D), -1, scale, false);
    }

    public static void burst(ServerLevel level, MagicSkillDefinition definition, Vec3 pos, Vec3 dir, float scale) {
        cue(level, VisualProfiles.of(definition), VisualCuePayload.CUE_PARTICLE_BURST, pos, dir, -1, scale, false);
    }

    /** Persistent, entity-backed circle for HOLD / TARGET / LINGER roles. */
    public static com.efkrdnz.magical.entity.MagicCircleEffectEntity scriptedCircle(ServerLevel level, MagicSkillDefinition definition, byte role, Vec3 pos, float radius, int life, float yaw, float pitch) {
        VisualProfile profile = VisualProfiles.of(definition);
        int index = VisualProfiles.indexOf(definition.id());
        var circle = com.efkrdnz.magical.entity.MagicCircleEffectEntity.createScripted(level, index, role, pos, radius, profile.color(ColorRole.BASE), life, yaw, pitch, 0.0F, com.efkrdnz.magical.entity.MagicCircleEffectEntity.detailForTier(definition.tier()));
        level.addFreshEntity(circle);
        return circle;
    }

    public static com.efkrdnz.magical.entity.MagicCircleEffectEntity followingCircle(ServerLevel level, MagicSkillDefinition definition, byte role, LivingEntity target, float radius, int life) {
        VisualProfile profile = VisualProfiles.of(definition);
        int index = VisualProfiles.indexOf(definition.id());
        var circle = com.efkrdnz.magical.entity.MagicCircleEffectEntity.createScriptedFollowing(level, index, role, target, radius, profile.color(ColorRole.BASE), life, com.efkrdnz.magical.entity.MagicCircleEffectEntity.detailForTier(definition.tier()));
        level.addFreshEntity(circle);
        return circle;
    }

    /** Ground position under a point (for anchors that must sit on terrain). */
    public static Vec3 groundBelow(Level level, Vec3 pos, int maxDrop) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(pos).mutable();
        for (int i = 0; i < maxDrop; i++) {
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return new Vec3(pos.x, cursor.getY() + 1.0D + 0.04D, pos.z);
            }
            cursor.move(0, -1, 0);
        }
        return pos;
    }

    public static MagicSkillDefinition definition(net.minecraft.resources.ResourceLocation id) {
        MagicSkillDefinition definition = MagicContent.get(id);
        return definition != null ? definition : MagicContent.get(MagicContent.STARTER_SKILL);
    }
}
