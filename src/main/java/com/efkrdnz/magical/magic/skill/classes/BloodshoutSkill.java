package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * BERSERKER - TAUNT_SELF / SELF / AGGRO_SPHERE. A roar forces every hostile mob nearby onto the
 * caster for the duration (their hits land softer, and their first landed hit sears them); enemy
 * players get a lock-on cue. Sneak = aim the shout as a cone.
 */
public final class BloodshoutSkill implements SkillModule {
    private static final int WINDUP = 6;
    private static final double RADIUS = 12.0D;
    private static final double CONE_RANGE = 20.0D;
    private static final double CONE_COS = Math.cos(Math.toRadians(50.0D));
    /** Flames in the roar's ring, and how fast they run: a flame keeps 0.96 of its speed a tick. */
    private static final int RING_FLAMES = 24;
    private static final double RING_FLAME_SPEED = 0.45D;
    /** The aimed shout's fan: fewer flames, sent further down the cone's longer range. */
    private static final int CONE_FLAMES = 12;
    private static final double CONE_FLAME_SPEED = 0.6D;
    /** Angry marks over each provoked head. */
    private static final int ANGER_MARKS = 2;
    /**
     * The roar's shock ripple round the caster's feet, in blocks: its near edge lies on the floor
     * at the bottom of a level first-person view rather than under the camera.
     */
    private static final double ROAR_SHOCK_RADIUS = 3.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.BLOODSHOUT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity banner = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.0D, 0.0D), WINDUP + Math.max(40, ctx.duration()), 1.6F, ctx.look().scale(-1.0D));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.defence();
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            if (owner == null || !owner.isAlive()) {
                entity.finish();
                return;
            }
            Vec3 look = owner.getLookAngle();
            Vec3 back = new Vec3(-look.x, 0.0D, -look.z);
            back = back.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, -1.0D) : back.normalize();
            entity.setPos(owner.getX(), owner.getY() + 1.0D, owner.getZ());
            entity.setDirection(back);
            if (entity.tickCount != WINDUP) {
                return;
            }
            int taunt = Math.max(40, entity.duration());
            double range = entity.sneakMode() ? CONE_RANGE : RADIUS;
            for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, owner.position(), range)) {
                if (entity.sneakMode()) {
                    Vec3 to = hostile.position().subtract(owner.position());
                    to = new Vec3(to.x, 0.0D, to.z);
                    if (to.lengthSqr() > 0.01D && to.normalize().dot(new Vec3(look.x, 0.0D, look.z).normalize()) < CONE_COS) {
                        continue;
                    }
                }
                // vanilla's own sign of a creature provoked, over every head the roar reached
                level.sendParticles(ParticleTypes.ANGRY_VILLAGER, hostile.getX(), hostile.getY() + hostile.getBbHeight(), hostile.getZ(), ANGER_MARKS, hostile.getBbWidth() * 0.3D, 0.1D, hostile.getBbWidth() * 0.3D, 0.0D);
                if (hostile instanceof ServerPlayer target) {
                    SpellFx.overlay(target, entity.definition(), FxKinds.Overlay.HEARTBEAT, 40, 0.5F, ColorRole.HOT);
                    continue;
                }
                MagicStatusService.apply(hostile, MagicStatus.TAUNTED, taunt, 0, 0.0F, entity.definition().id(), owner);
            }
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            // The roar is a shock across the floor, not a blow on anything: a ripple laid round the
            // caster's feet and the ring of fire below racing out along the ground. It was a hit
            // cue two blocks ahead, first at the eyes and then on the floor, and a hit cue that
            // close throws its flame, its black smoke and its ember cards up off a point the camera
            // stands over, so they climbed through the caster's own view for a second after the
            // roar. Only its sound is kept.
            Vec3 feet = SpellFx.groundBelow(level, owner.position().add(0.0D, 0.5D, 0.0D), 4);
            SpellFx.zoneTickWithin(level, entity.definition(), feet, ROAR_SHOCK_RADIUS);
            ProfileCues.SoundCue cue = VisualProfiles.of(entity.definition()).sounds().impact();
            if (cue != null) {
                level.playSound(null, feet.x, feet.y, feet.z, cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch());
            }
            roarWave(level, owner, look, entity.sneakMode());
        };
    }

    /**
     * How far the roar carries, drawn on the ground: a ring of flame racing out from the caster's
     * feet, all the way round - or only down the cone, and faster, when the shout is aimed.
     */
    private static void roarWave(ServerLevel level, LivingEntity owner, Vec3 look, boolean cone) {
        double facing = Math.atan2(look.z, look.x);
        double half = cone ? Math.acos(CONE_COS) : Math.PI;
        int flames = cone ? CONE_FLAMES : RING_FLAMES;
        double speed = cone ? CONE_FLAME_SPEED : RING_FLAME_SPEED;
        for (int i = 0; i < flames; i++) {
            double a = facing - half + (i + 0.5D) * (2.0D * half / flames);
            level.sendParticles(ParticleTypes.FLAME, owner.getX(), owner.getY() + 0.15D, owner.getZ(), 0, Math.cos(a), 0.0D, Math.sin(a), speed);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.WAR_HORN).frame(9).band(GlyphKind.TOOTH_BAND, 27).band(GlyphKind.WAVE_BAND, 9).stamps(StampId.CHEVRON, 9).core(CoreKind.EMBER_PIT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("bloodshout", 1.6F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.HEARTBEAT)
                .bounds(3.0F, 4.0F, 1.0F);
    }
}
