package com.efkrdnz.magical.magic.skill.light;

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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * LIGHT T1 - REVEAL / SELF_PULSE / ROTATING_SECTOR. A planted lantern sweeps a lighthouse blade a
 * full turn: every hostile the bearing passes (walls or not) is revealed (glowing, invisibility
 * stripped) for the duration and hurt once. Sneak = sweep the other way.
 */
public final class RevelationSkill implements SkillModule {
    private static final int SHOOT = 3;
    private static final int SWEEP_TICKS = 24;

    /** Motes caught in the blade each tick of the sweep. */
    private static final int BEAM_MOTES = 3;

    /**
     * The nearest a mote hangs to the lantern. The lantern is at the caster's feet, so a mote any
     * nearer is a blot a couple of blocks from their eyes as the blade passes their view.
     */
    private static final double MOTE_NEAR = 3.0D;

    /** A mote's size: at three to eight blocks, a speck of gold that still reads over daylight stone. */
    private static final float MOTE_SCALE = 1.3F;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.REVELATION;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity lantern = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 0.05D, 0.0D), SHOOT + SWEEP_TICKS + 20, Math.max(8.0F, ctx.size()), ctx.look());
                lantern.setExtra(Math.max(40, ctx.duration()));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 20.0F);
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
            Vec3 look = entity.direction();
            float startYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            int sweepStep = entity.tickCount - SHOOT;
            float sign = entity.sneakMode() ? -1.0F : 1.0F;
            float bearing = sweepStep < 0 ? startYaw : startYaw + sign * (360.0F / SWEEP_TICKS) * Math.min(sweepStep, SWEEP_TICKS);
            entity.setValue(bearing);
            if (sweepStep < 0 || sweepStep > SWEEP_TICKS) {
                return;
            }
            float prev = startYaw + sign * (360.0F / SWEEP_TICKS) * Math.max(0, sweepStep - 1);
            Vec3 origin = entity.position();
            for (LivingEntity hostile : SkillTargets.hostilesWithin(entity.serverLevel(), entity.owner(), origin, entity.radius())) {
                if (MagicStatusService.has(hostile, MagicStatus.REVEALED) && MagicStatusService.sourceOf(hostile, MagicStatus.REVEALED) != null
                        && MagicStatusService.sourceOf(hostile, MagicStatus.REVEALED).equals(entity.getUUID())) {
                    continue;
                }
                Vec3 rel = hostile.position().subtract(origin);
                float yaw = (float) Math.toDegrees(Math.atan2(-rel.x, rel.z));
                float a = Mth.wrapDegrees(yaw - prev) * sign;
                float span = 360.0F / SWEEP_TICKS + 0.5F;
                if (a < -0.5F || a > span) {
                    continue;
                }
                MagicStatusService.apply(hostile, MagicStatus.REVEALED, entity.extra(), entity.definition().id(), entity);
                hostile.addEffect(new MobEffectInstance(MobEffects.GLOWING, entity.extra(), 0, false, false));
                hostile.removeEffect(MobEffects.INVISIBILITY);
                SkillTargets.hurt(entity.serverLevel(), entity.owner(), hostile, entity.damage(), entity.definition(), true);
            }
            motes(entity, origin, prev, bearing);
        };
    }

    /**
     * Dust caught in the blade: a few gold motes in the sector it swept this tick, hung at the
     * height of the beam. A mote lives under a second, so the blade leaves a short fading wake of
     * them behind it and the turn reads as a turn - including the half of it behind the caster,
     * which the caster only ever sees as that wake coming round the edge of the view.
     */
    private static void motes(SpellEffectEntity entity, Vec3 origin, float from, float to) {
        ServerLevel level = entity.serverLevel();
        RandomSource random = level.random;
        double reach = Math.max(MOTE_NEAR + 1.0D, entity.radius());
        TintedParticleOptions mote = new TintedParticleOptions(MagicalParticles.MOTE.get(), VisualProfiles.of(entity.definition()).color(ColorRole.BASE), MOTE_SCALE);
        for (int i = 0; i < BEAM_MOTES; i++) {
            double yaw = Math.toRadians(from + (to - from) * random.nextDouble());
            // outward-weighted, so the sector fills evenly rather than crowding at the lantern
            double d = MOTE_NEAR + (reach - MOTE_NEAR) * Math.sqrt(random.nextDouble());
            double h = 0.2D + random.nextDouble();
            // yaw is read the way the reveal reads it, atan2(-x, z): its direction is (-sin, cos)
            level.sendParticles(mote, origin.x - Math.sin(yaw) * d, origin.y + h, origin.z + Math.cos(yaw) * d, 0, 0.0D, 1.0D, 0.0D, 0.01D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.BEACON).frame(9).band(GlyphKind.TICK_BAND, 72).stamps(StampId.ARROW, 4).arcSweep(0.3F, ColorRole.BRIGHT).core(CoreKind.DISC_GLOW).spin(SpinSignature.SWEEP))
                .anchor(CircleAnchor.GROUND)
                // The blade is hinged on a lantern planted at the caster's feet, so at full strength
                // it swept a sheet of white across the bottom of their view and clipped to white over
                // daylight for everyone else; under half it is still the brightest thing on the field
                // and keeps its gold.
                .silhouette(Silhouette.filament(Silhouette.Form.FAN, FxKinds.Filament.RIBBON, 1, 2.5F, 24.0F, 0).withOffset(0.3F).withOpacity(0.45F))
                .silhouette(Silhouette.glyph(1.6F).withRole(ColorRole.DIM))
                .silhouette(Silhouette.body(Silhouette.Form.CAGE, FxKinds.Body.PEARL, 1, 0.25F, 0.5F).withOffset(0.1F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.TUNNEL)
                .bounds(26.0F, 8.0F, 1.0F);
    }
}
