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
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SPATIAL T1 - SCALE / AIM_POINT_SNAP / CONTRACTING_HULL. A three-ring gimbal snaps shut around the
 * living thing nearest the aim point and ratchets its scale down in three clicks (0.70, 0.50, 0.35),
 * damaging on the third; the victim stays shrunk for the duration. Sneak = reverse polarity: it is
 * enlarged instead (1.15, 1.35, 1.6).
 */
public final class CompressionSkill implements SkillModule {
    private static final float[] SHRINK = {0.70F, 0.50F, 0.35F};
    private static final float[] GROW = {1.15F, 1.35F, 1.60F};
    private static final int CLICKS = 3;
    private static final int CLICK_SPACING = 2;
    /** Motes in the shell each click closes (or, reversed, throws open) round the body. */
    private static final int CLICK_SHELL = 8;
    /** How far outside the body the click's shell starts, in blocks. */
    private static final double CLICK_SHELL_REACH = 1.3D;
    /** While the body is held small: a couple of motes drawn in toward it every this many ticks. */
    private static final int HELD_INTERVAL = 4;
    private static final int HELD_MOTES = 2;

    /**
     * A click of the gimbal, told by the space round the body: a shell of pale motes snapping in onto
     * it, or thrown out from it when the polarity is reversed. The gauge rings say the scale; this
     * says which way the space went.
     */
    private static void clickMatter(SpellEffectEntity entity, LivingEntity living, int click) {
        Vec3 centre = living.getBoundingBox().getCenter();
        double body = Math.max(0.3D, living.getBbWidth() * 0.5D);
        double outer = body + CLICK_SHELL_REACH * (1.0D - 0.15D * (click - 1));
        if (entity.sneakMode()) {
            SpatialMatter.converge(entity.serverLevel(), entity.definition(), centre, body * 0.6D, outer, CLICK_SHELL, false);
        } else {
            SpatialMatter.converge(entity.serverLevel(), entity.definition(), centre, outer, body * 0.4D, CLICK_SHELL, false);
        }
    }

    /** The hold: a thin trickle of motes still being drawn in toward the body (out of it, reversed). */
    private static void heldMatter(SpellEffectEntity entity, LivingEntity living) {
        Vec3 centre = living.getBoundingBox().getCenter();
        double body = Math.max(0.3D, living.getBbWidth() * 0.5D);
        double outer = body + 0.9D;
        if (entity.sneakMode()) {
            SpatialMatter.converge(entity.serverLevel(), entity.definition(), centre, body * 0.8D, outer, HELD_MOTES, false);
        } else {
            SpatialMatter.converge(entity.serverLevel(), entity.definition(), centre, outer, body * 0.5D, HELD_MOTES, false);
        }
    }

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.COMPRESSION;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    Vec3 point = ctx.aim().point();
                    List<LivingEntity> near = ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(1.4D), e -> e != ctx.caster() && e.isAlive() && SkillTargets.isHostile(ctx.caster(), e));
                    near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(point)));
                    victim = near.isEmpty() ? null : near.get(0);
                }
                if (victim == null) {
                    SpellFx.decal(ctx.level(), ctx.definition(), ctx.aim().point(), ctx.aim().normal(), 0.6F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                int life = CLICKS * CLICK_SPACING + Math.max(20, ctx.duration());
                SpellEffectEntity gauge = SpellEffectEntity.spawn(ctx, victim.getBoundingBox().getCenter(), life, 1.0F, new Vec3(0.0D, 1.0D, 0.0D));
                gauge.setTarget(victim);
                gauge.setValue(1.0F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 14.0D;
            }

            @Override
            public double aimTolerance() {
                return 1.4D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(2.0F, 14.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(living.getBoundingBox().getCenter());
                int t = entity.tickCount;
                if (t % CLICK_SPACING == 0 && t / CLICK_SPACING >= 1 && t / CLICK_SPACING <= CLICKS) {
                    int click = t / CLICK_SPACING;
                    float scale = (entity.sneakMode() ? GROW : SHRINK)[click - 1];
                    MagicStatusService.apply(living, MagicStatus.COMPRESSED, entity.life() - t, click, scale, entity.definition().id(), entity.owner());
                    entity.setValue(scale);
                    entity.setExtra(click);
                    SpellFx.barrierHit(entity.serverLevel(), entity.definition(), living.getBoundingBox().getCenter(), new Vec3(0.0D, 1.0D, 0.0D));
                    clickMatter(entity, living, click);
                    if (click == CLICKS) {
                        entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                        SkillTargets.hurt(entity.serverLevel(), entity.owner(), living, entity.damage(), entity.definition(), true);
                    }
                } else if (t > CLICKS * CLICK_SPACING && t % HELD_INTERVAL == 0) {
                    heldMatter(entity, living);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity.target() instanceof LivingEntity living) {
                    MagicStatusService.clear(living, MagicStatus.COMPRESSED);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.CALIPER).frame(3, CircleScript.FrameStyle.NESTED).band(GlyphKind.TICK_BAND, 36).band(GlyphKind.FACET_BAND, 9).stamps(StampId.RING, 3).core(CoreKind.HEX_LENS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("compression", 2.6F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.SHOCK_RING)
                .bounds(4.0F, 4.0F, 4.0F);
    }
}
