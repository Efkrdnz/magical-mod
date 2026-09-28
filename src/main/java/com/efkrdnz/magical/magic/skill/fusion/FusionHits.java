package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.network.FirstPersonEffectPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A hit a fusion skill lands on the same body over and over - a wheel grinding it, a beam resting
 * on it - drawn by the skill itself rather than by the generic impact.
 *
 * <p>From tier three up every generic impact is heavy: vanilla's blast, a ring of puffs and the
 * school's smoke rising off the body. Once that is a hit it reads as one; every half second on
 * the one body it is a stack of blasts and a column of black squares standing on whatever is being
 * burned. So the skill throws its own few particles where the hit lands, and this keeps the rest
 * of what {@code SpellFx.impact} did for it: the impact's sound, the caster's confirm and the
 * victim's jolt, from the same profile and with the same numbers.
 */
final class FusionHits {

    private FusionHits() {
    }

    /** The sound and both first-person beats of a hit on {@code victim}, and no burst. */
    static void land(ServerLevel level, MagicSkillDefinition definition, LivingEntity victim, Entity attacker) {
        VisualProfile profile = VisualProfiles.of(definition);
        Vec3 at = victim.getBoundingBox().getCenter();
        ProfileCues.SoundCue cue = profile.sounds().impact();
        if (cue != null) {
            float jitter = (level.random.nextFloat() - 0.5F) * 2.0F * cue.jitter();
            level.playSound(null, at.x, at.y, at.z, cue.sound(), SoundSource.PLAYERS, cue.volume(), Mth.clamp(cue.pitch() + jitter, 0.5F, 2.0F));
        }
        ProfileCues.ImpactSpec impact = profile.impact();
        if (attacker instanceof ServerPlayer caster) {
            jolt(caster, profile, impact.casterPreset(), FxKinds.Overlay.VIGNETTE, ColorRole.BRIGHT, FirstPersonEffectPayload.OMNI);
        }
        if (victim instanceof ServerPlayer target) {
            int yawBucket = FirstPersonEffectPayload.OMNI;
            if (attacker != null) {
                Vec3 to = attacker.position().subtract(target.position());
                float hitYaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z)) - target.getYRot();
                yawBucket = Math.floorMod(Math.round(Mth.wrapDegrees(hitYaw) / 360.0F * 32.0F), 32) & 31;
            }
            jolt(target, profile, impact.victimPreset(), impact.victimOverlay(), ColorRole.DIM, yawBucket);
        }
    }

    private static void jolt(ServerPlayer player, VisualProfile profile, ProfileCues.FirstPersonPreset preset, FxKinds.Overlay overlay, ColorRole role, int yawBucket) {
        if (preset == null || preset == ProfileCues.FirstPersonPreset.NONE) {
            return;
        }
        FirstPersonEffectPayload payload = new FirstPersonEffectPayload(profile.color(role), preset.overlayTicks(), preset.alpha(),
                preset.shakeTicks(), preset.shakeStrength(), preset.freezeTicks(), preset.fovKick())
                .withOverlay(overlay.id(), Math.min(63, 30 + profile.tier().tier() * 8), yawBucket);
        MagicalNetwork.playFirstPersonEffect(player, payload);
    }
}
