package com.efkrdnz.magical.magic.skill.water;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.Accent;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T0 - ROOT / AIM_SURFACE_INSTANT_STAMP / DISC. A frost bloom snaps onto the aimed surface:
 * everything inside is hurt, extinguished and rooted (can still turn, cast and be hit, cannot walk
 * or jump). Sneak = stamp it under your own feet.
 */
public final class RimeSnapSkill implements SkillModule {
    private static final int WINDUP = 3;
    /** Points round the bloom's edge where the frost locks, each a flake running out and a crumb of ice. */
    private static final int RIM_POINTS = 10;
    /** Crumbs of ice closing over the feet of each thing the bloom roots. */
    private static final int SHACKLE_CRUMBS = 8;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.RIME_SNAP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos;
                Vec3 normal;
                if (ctx.sneak()) {
                    pos = ctx.feet().add(0.0D, 0.04D, 0.0D);
                    normal = new Vec3(0.0D, 1.0D, 0.0D);
                } else if (ctx.aim().hitEntity()) {
                    Vec3 under = AimResolver.groundBelow(ctx.level(), ctx.aim().entity().position().add(0.0D, 0.5D, 0.0D), 6);
                    pos = under != null ? under.add(0.0D, 0.04D, 0.0D) : ctx.aim().entity().position();
                    normal = new Vec3(0.0D, 1.0D, 0.0D);
                } else if (ctx.aim().hitBlock()) {
                    pos = ctx.aim().point();
                    normal = ctx.aim().normal();
                } else {
                    pos = ctx.feet().add(0.0D, 0.04D, 0.0D);
                    normal = new Vec3(0.0D, 1.0D, 0.0D);
                }
                int root = Math.max(10, ctx.duration());
                SpellEffectEntity bloom = SpellEffectEntity.spawn(ctx, pos, WINDUP + root + 10, Math.max(1.0F, ctx.size()), normal);
                bloom.setExtra(root);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 16.0D;
            }

            @Override
            public double aimTolerance() {
                return 1.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(1.0F, 12.0F);
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
            if (entity.tickCount != WINDUP) {
                return;
            }
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            for (LivingEntity hit : SkillTargets.hostilesWithin(entity.serverLevel(), entity.owner(), entity.position(), entity.radius())) {
                SkillTargets.hurt(entity.serverLevel(), entity.owner(), hit, entity.damage(), entity.definition(), true);
                hit.clearFire();
                MagicStatusService.apply(hit, MagicStatus.ROOTED, entity.extra(), entity.definition().id(), entity.owner());
                // ice closing over the feet: the root, in matter
                entity.serverLevel().sendParticles(ice(), hit.getX(), hit.getY() + 0.15D, hit.getZ(), SHACKLE_CRUMBS, hit.getBbWidth() * 0.35D, 0.05D, hit.getBbWidth() * 0.35D, 0.05D);
            }
            SpellFx.impact(entity.serverLevel(), entity.definition(), entity.position(), entity.direction(), null, entity.owner(), 1.2F);
            snap(entity.serverLevel(), entity.position(), entity.direction(), entity.radius());
        };
    }

    /**
     * The frost racing out across the face it was stamped on: flakes skating to the edge of the
     * bloom, and a crumb of ice where each one stops.
     */
    private static void snap(ServerLevel level, Vec3 centre, Vec3 normal, float radius) {
        Vec3 n = normal.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : normal.normalize();
        Vec3 u = Math.abs(n.y) < 0.9D ? n.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize() : n.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
        Vec3 w = n.cross(u);
        Vec3 origin = centre.add(n.scale(0.12D));
        BlockParticleOption ice = ice();
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < RIM_POINTS; i++) {
            double a = offset + i * Math.PI * 2.0D / RIM_POINTS;
            Vec3 d = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
            Vec3 rim = origin.add(d.scale(radius * 0.85D));
            level.sendParticles(ParticleTypes.SNOWFLAKE, origin.x, origin.y, origin.z, 0, d.x, d.y, d.z, 0.05D * radius);
            level.sendParticles(ice, rim.x, rim.y, rim.z, 1, 0.08D, 0.02D, 0.08D, 0.0D);
        }
    }

    /** Crumbs of real ice. Built on use rather than held in a constant, so loading the skill never touches the block registry. */
    private static BlockParticleOption ice() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .accent(Accent.FROST)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.SNOWFLAKE).frame(6).band(GlyphKind.FACET_BAND, 12).band(GlyphKind.DASHED_RING, 12, com.efkrdnz.magical.magic.visual.ColorRole.BASE).stamps(StampId.SNOWFLAKE, 6).core(CoreKind.HEX_LENS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.mark(FxKinds.Mark.FROST_BLOOM, 2.4F, 6))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.FROST_BLOOM, FxKinds.Smoke.FROST_CRYSTAL, FxKinds.Overlay.FROST_EDGES)
                .bounds(3.0F, 1.5F, 1.0F);
    }
}
