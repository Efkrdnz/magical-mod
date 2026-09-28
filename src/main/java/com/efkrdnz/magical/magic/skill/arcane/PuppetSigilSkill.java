package com.efkrdnz.magical.magic.skill.arcane;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T3 - PUPPET / HITSCAN_STRING_FAN / FIVE_STRING_FAN. Five rune-threads string the first
 * living entity along the look ray; for the duration its legs walk it toward wherever the caster
 * looks (sneak: away). Walking it into another entity strikes both; cutting the strings hurts it.
 */
public final class PuppetSigilSkill implements SkillModule {
    private static final int WINDUP = 10;

    /** Magic-crit sparks at each of the five places a string is tied, as the strings bite in. */
    private static final int STRING_SPARKS = 4;

    /** The control cross's thickness (its rod is 0.22 of this across); it was 0.45. */
    private static final float CONTROL_CROSS_SIZE = 0.26F;

    /** The control cross's length along the aim; it was 0.9. */
    private static final float CONTROL_CROSS_LENGTH = 0.55F;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.PUPPET_SIGIL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    return CastResult.CONSUMED_NO_COOLDOWN; // a retry, not a fail state
                }
                int ticks = WINDUP + Math.max(20, ctx.duration());
                SpellEffectEntity sigil = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.9D)), ticks, 1.0F, ctx.look());
                sigil.setTarget(victim);
                MagicStatusService.apply(victim, MagicStatus.PUPPETED, ticks, ctx.definition().id(), ctx.caster());
                if (victim instanceof Mob mob) {
                    mob.setNoAi(true);
                }
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 16.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.9D;
            }

            @Override
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(4.0F, 16.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                LivingEntity owner = entity.livingOwner();
                Entity target = entity.target();
                if (owner == null || !owner.isAlive() || !(target instanceof LivingEntity puppet) || !puppet.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.9D)).add(0.0D, -0.2D, 0.0D));
                entity.setDirection(owner.getLookAngle());
                if (entity.tickCount < WINDUP) {
                    return;
                }
                if (entity.tickCount == WINDUP) {
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SkillTargets.hurt(level, owner, puppet, 0.5F, entity.definition(), false);
                    stringsBite(level, entity.definition(), puppet);
                }
                if (owner.distanceTo(puppet) > 24.0D) {
                    entity.finish();
                    return;
                }
                // walk toward (or away from) the caster's aim point
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 32.0D, 0.0D, true, 12, null);
                Vec3 goal = aim.point();
                Vec3 dir = goal.subtract(puppet.position());
                dir = new Vec3(dir.x, 0.0D, dir.z);
                if (entity.sneakMode()) {
                    dir = dir.scale(-1.0D);
                }
                if (dir.lengthSqr() > 0.6D) {
                    Vec3 step = dir.normalize().scale(0.22D * Math.max(0.5D, entity.speed()));
                    Vec3 v = puppet.getDeltaMovement();
                    puppet.setDeltaMovement(step.x, v.y, step.z);
                    puppet.hurtMarked = true;
                    float yaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
                    puppet.setYRot(yaw);
                    puppet.yBodyRot = yaw;
                    if (puppet.horizontalCollision && puppet.onGround()) {
                        puppet.setDeltaMovement(puppet.getDeltaMovement().add(0.0D, 0.42D, 0.0D));
                    }
                }
                // collision strikes: walking the puppet into another hostile hurts both
                if (entity.tickCount % 10 == 0) {
                    for (LivingEntity other : SkillTargets.hostilesIn(level, owner, puppet.getBoundingBox().inflate(0.2D))) {
                        if (other == puppet) {
                            continue;
                        }
                        SkillTargets.hurt(level, owner, other, entity.damage() * 0.5F, entity.definition(), true);
                        SkillTargets.hurt(level, owner, puppet, entity.damage() * 0.5F, entity.definition(), false);
                        break;
                    }
                }
                entity.setValue(entity.tickCount / (float) entity.life());
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (target instanceof LivingEntity puppet && puppet.isAlive()) {
                    MagicStatusService.clear(puppet, MagicStatus.PUPPETED);
                    if (puppet instanceof Mob mob) {
                        mob.setNoAi(false);
                    }
                    if (entity.tickCount >= WINDUP) {
                        SkillTargets.hurt(entity.serverLevel(), entity.owner(), puppet, entity.damage(), entity.definition(), true);
                    }
                }
            }
        };
    }

    /**
     * The strings biting in as the puppet is taken: a pinch of vanilla's magic-crit sparks at the
     * head, both shoulders and both knees, where a marionette's strings are tied.
     *
     * <p>This half-heart sting used to fire the whole impact cue, and at this tier every impact is a
     * heavy one: an explosion sprite and a ring of white poofs, which hung round the puppet's head
     * as grey squares long after it had walked off. The cue's sound is kept, and so is the overlay
     * a strung player sees; the explosion is left to the hits that earn it - the collisions and
     * the cut.
     */
    private static void stringsBite(ServerLevel level, MagicSkillDefinition definition, LivingEntity puppet) {
        double height = puppet.getBbHeight();
        double half = puppet.getBbWidth() * 0.45D;
        double yaw = Math.toRadians(puppet.yBodyRot);
        // square to the way the body faces, so the shoulders are where its shoulders are
        double sx = Math.cos(yaw);
        double sz = Math.sin(yaw);
        double[][] anchors = {
                {0.0D, height * 0.95D},
                {half, height * 0.72D},
                {-half, height * 0.72D},
                {half * 0.6D, height * 0.3D},
                {-half * 0.6D, height * 0.3D},
        };
        for (double[] anchor : anchors) {
            onlookers(level, puppet, ParticleTypes.ENCHANTED_HIT, puppet.getX() + sx * anchor[0], puppet.getY() + anchor[1], puppet.getZ() + sz * anchor[0],
                    STRING_SPARKS, 0.04D, 0.04D, 0.04D, 0.3D);
        }
        VisualProfile profile = VisualProfiles.of(definition);
        ProfileCues.SoundCue sound = profile.sounds().impact();
        if (sound != null) {
            // as SpellFx plays an impact cue's sound, pitch jitter and all
            float jitter = (level.random.nextFloat() - 0.5F) * 2.0F * sound.jitter();
            level.playSound(null, puppet.getX(), puppet.getY() + height * 0.5D, puppet.getZ(), sound.sound(), SoundSource.PLAYERS,
                    sound.volume(), Mth.clamp(sound.pitch() + jitter, 0.5F, 2.0F));
        }
        ProfileCues.FirstPersonPreset felt = profile.impact().victimPreset();
        if (puppet instanceof ServerPlayer strung && felt != null && felt != ProfileCues.FirstPersonPreset.NONE) {
            SpellFx.overlay(strung, definition, profile.impact().victimOverlay(), felt.overlayTicks(), felt.alpha(), ColorRole.DIM);
        }
    }

    /**
     * The bite's sparks, for everybody but the puppet. Mobs cast this on players, and a strung
     * player's camera sits just under the head string's knot, so its sparks would burst out of the
     * eye. That player has the overlay instead; anyone else is sent the sparks as usual.
     */
    private static <T extends ParticleOptions> void onlookers(ServerLevel level, LivingEntity puppet, T options,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (!(puppet instanceof ServerPlayer subject)) {
            level.sendParticles(options, x, y, z, count, dx, dy, dz, speed);
            return;
        }
        for (ServerPlayer viewer : level.players()) {
            if (viewer != subject) {
                level.sendParticles(viewer, options, false, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        // The second silhouette is the marionette's control cross, a rod held out along the aim. It
        // hangs 0.9 of a block in front of the caster's eyes for the whole puppeting, and from there
        // it is seen end-on: at its old size a grey star a tenth of the frame wide sat right under
        // the crosshair the caster steers the puppet with. It is drawn smaller and shorter now -
        // still a rod in the hand to anybody watching, a small mark under the crosshair to the caster.
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.HAND).frame(11).frameRotated(45.0F).band(GlyphKind.BRAID_BAND, 5).band(GlyphKind.RUNE_BAND, 10).stamps(StampId.BONE, 5).orbit(5, 0.82F, 4).core(CoreKind.CROSS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.LINK, FxKinds.Filament.THREAD_KNOTS, 5, 0.05F))
                .silhouette(Silhouette.body(Silhouette.Form.CROSSED_BLADES, FxKinds.Body.BONE_IVORY, 2, CONTROL_CROSS_SIZE, CONTROL_CROSS_LENGTH))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.IRIS_CLOSE)
                .bounds(20.0F, 4.0F, 4.0F);
    }
}
