package com.efkrdnz.magical.magic.skill.spatial;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SPATIAL T3 - FOLD_POSITIONS / AIM_SURFACE_SEAM / HALF_DISC. A seam is scored on the ground
 * perpendicular to the look; after a counterable windup the far half-disc folds over it onto the
 * near half: every hostile there has its position mirrored across the seam plane and is slammed
 * down. Sneak = reverse polarity: the near half folds onto the far side.
 */
public final class CreaseFoldSkill implements SkillModule {
    private static final int WINDUP = 12;
    private static final int LINGER = 30;
    private static final double RADIUS = 6.0D;
    private static final double VERTICAL = 4.0D;
    /** One pillar of the floor's dust every this many blocks along the seam as it shuts... */
    private static final double SEAM_PILLAR_SPACING = 1.5D;
    /** ...and never more than this many, however wide a seam is scored. */
    private static final int SEAM_PILLARS_MAX = 11;
    /** Motes glittering up off the seam each tick of the windup. */
    private static final int SCORE_MOTES = 1;
    /** After the fold: a mote or two off the scar every few ticks, for this long. */
    private static final int SCAR_TICKS = 20;
    private static final int SCAR_INTERVAL = 2;
    private static final int SCAR_MOTES = 1;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CREASE_FOLD;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 look = ctx.look();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
                SpellEffectEntity seam = SpellEffectEntity.spawn(ctx, ctx.aim().point(), WINDUP + LINGER, (float) RADIUS * Math.max(0.5F, ctx.size() / 2.5F), flat);
                CompoundTag tag = new CompoundTag();
                tag.putBoolean("Sneak", ctx.sneak());
                seam.setSyncedData(tag);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 14.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }

            @Override
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 14.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    /** The seam's two ends, {@code reach} of the way out from its middle, square to the look. */
    private static Vec3[] seamEnds(SpellEffectEntity entity, double reach) {
        Vec3 f = entity.direction();
        Vec3 across = new Vec3(f.z, 0.0D, -f.x).scale(entity.radius() * reach);
        Vec3 centre = entity.position();
        return new Vec3[] {centre.subtract(across), centre.add(across)};
    }

    /**
     * The seam being scored through the windup: motes glittering up off it, over the width the
     * zipper has opened to, so the line a fold will shut along is on the ground before it shuts.
     */
    private static void scoreSeam(SpellEffectEntity entity, int t) {
        Vec3[] ends = seamEnds(entity, Math.max(0.15D, t / (double) WINDUP));
        SpatialMatter.seam(entity.serverLevel(), entity.definition(), ends[0], ends[1], SCORE_MOTES, 0.02D, true);
    }

    /** What is left of the seam once it has shut: a thin scar still lifting off it while the rift fades. */
    private static void scar(SpellEffectEntity entity, int t) {
        int since = t - WINDUP;
        if (since > SCAR_TICKS || since % SCAR_INTERVAL != 0) {
            return;
        }
        Vec3[] ends = seamEnds(entity, 1.0D);
        SpatialMatter.seam(entity.serverLevel(), entity.definition(), ends[0], ends[1], SCAR_MOTES, 0.03D, true);
        if (since % (SCAR_INTERVAL * 3) == 0) {
            Vec3 at = ends[0].lerp(ends[1], entity.serverLevel().random.nextDouble()).add(0.0D, 0.2D, 0.0D);
            SpatialMatter.crackle(entity.serverLevel(), at, 2, 0.2D);
        }
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            if (entity.tickCount < WINDUP) {
                scoreSeam(entity, entity.tickCount);
                return;
            }
            if (entity.tickCount > WINDUP) {
                scar(entity, entity.tickCount);
                return;
            }
            ServerLevel level = entity.serverLevel();
            Vec3 centre = entity.position();
            Vec3 f = entity.direction();
            double radius = entity.radius();
            boolean foldNear = entity.sneakMode();
            boolean folded = false;
            for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), centre, radius + 1.0D)) {
                Vec3 rel = victim.position().subtract(centre);
                double d = rel.dot(f);
                if (Math.abs(rel.y) > VERTICAL || (foldNear ? d >= 0.0D : d <= 0.0D)) {
                    continue;
                }
                Vec3 mirrored = victim.position().subtract(f.scale(2.0D * d));
                Vec3 clear = SafeSpotSearch.liftClear(level, mirrored, victim.getBbWidth(), victim.getBbHeight(), 3.0D);
                SpatialMatter.vacated(level, entity.definition(), victim.position(), victim.getBbWidth(), victim.getBbHeight());
                SafeSpotSearch.place(victim, clear != null ? clear : mirrored, victim.getYRot(), victim.getXRot(), false);
                victim.setDeltaMovement(0.0D, -0.3D, 0.0D);
                victim.hurtMarked = true;
                MagicStatusService.apply(victim, MagicStatus.ROOTED, 10, entity.definition().id(), entity.owner());
                SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                folded = true;
            }
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            // The seam shutting is the floor along it kicked up as its own dust, the whole width of
            // the fold, and a curtain of pale motes snapping up off the line with a crackle at its
            // middle: the line is the beat, and it reads on any floor, where stone dust on a stone
            // floor did not.
            Vec3[] ends = seamEnds(entity, 1.0D);
            int pillars = Math.min(SEAM_PILLARS_MAX, 2 * (int) Math.round(radius / SEAM_PILLAR_SPACING) + 1);
            SpatialMatter.groundLine(level, ends[0], ends[1], pillars, 2);
            SpatialMatter.seam(level, entity.definition(), ends[0], ends[1], pillars + 2, 0.16D, false);
            SpatialMatter.crackle(level, centre.add(0.0D, 0.3D, 0.0D), 4, 0.6D);
            // Every folded body lands with its own strike (it is a T3 hit, so a flash, a blast and a
            // ring of puffs), and a body folds from close to the seam onto close to the seam - so a
            // second strike on the seam's middle stacked two blasts on one spot. It only strikes
            // when the fold closed on nothing, so an empty fold still lands a beat.
            if (!folded) {
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.0F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.HINGE).frame(10).band(GlyphKind.CHAIN_BAND, 20).band(GlyphKind.TICK_BAND, 40).band(GlyphKind.DASHED_RING, 10).stamps(StampId.TRIANGLE, 10).mirror(1).orbit(5, 0.84F, 4).core(CoreKind.HEX_LENS).stack(2, 0.5F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("crease", 6.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.LATTICE_GRID, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.STATIC_GLITCH)
                .budget(2)
                .bounds(8.0F, 5.0F, 5.0F);
    }
}
