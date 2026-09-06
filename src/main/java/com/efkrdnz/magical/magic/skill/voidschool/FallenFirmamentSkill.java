package com.efkrdnz.magical.magic.skill.voidschool;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.service.SpellIntercept;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * VOID T4 - DESCEND_CRUSH / SKY_CEILING / HORIZONTAL_PLANE. An inverted black disc forms high over
 * the aim point and descends until it hangs just above the ground, holds, then dissolves. Hostile
 * projectiles touching it are erased; everything under it is hushed and worn down; anything taller
 * than the gap is pinned against the sky. You can crouch under it.
 */
public final class FallenFirmamentSkill implements SkillModule {
    private static final int WINDUP = 20;
    private static final int DISSOLVE = 20;
    private static final double START_HEIGHT = 18.0D;
    private static final double GAP = 1.5D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.FALLEN_FIRMAMENT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int hold = Math.max(60, ctx.duration());
                double descent = (START_HEIGHT - GAP) / Math.max(0.1D, ctx.stats().speed());
                Vec3 ground = ctx.aim().point();
                SpellEffectEntity disc = SpellEffectEntity.spawn(ctx, ground.add(0.0D, START_HEIGHT, 0.0D), WINDUP + (int) descent + hold + DISSOLVE, 4.0F * Math.max(1.0F, ctx.size()), new Vec3(0.0D, -1.0D, 0.0D));
                disc.setExtra(hold);
                disc.setValue((float) START_HEIGHT);
                disc.serverData().putDouble("GroundY", ground.y);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 20.0D;
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
                return MobCastProfile.attack(4.0F, 20.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            double groundY = entity.serverData().getDouble("GroundY");
            if (entity.tickCount < WINDUP) {
                return;
            }
            if (entity.tickCount == WINDUP) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }
            double height = entity.getY() - groundY;
            if (height > GAP) {
                double step = Math.max(0.1D, entity.speed());
                entity.setPos(entity.getX(), Math.max(groundY + GAP, entity.getY() - step), entity.getZ());
                height = entity.getY() - groundY;
            }
            entity.setValue((float) height);
            int hold = Math.max(60, entity.extra());
            boolean dissolving = entity.tickCount > entity.life() - DISSOLVE;
            if (dissolving) {
                entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
                return;
            }
            Vec3 centre = entity.position();
            double radius = entity.radius();
            for (Entity projectile : SpellIntercept.hostileProjectiles(level, centre, radius, owner)) {
                if (Math.abs(projectile.getY() - centre.y) < 0.6D) {
                    SpellIntercept.erase(projectile);
                }
            }
            Vec3 base = new Vec3(centre.x, groundY, centre.z);
            for (LivingEntity hostile : SkillTargets.hostilesInCylinder(level, owner, base, radius, height + 1.0D)) {
                MagicStatusService.apply(hostile, MagicStatus.SILENCED, 12, entity.definition().id(), owner);
                boolean pinned = hostile.getY() + hostile.getBbHeight() > centre.y - 0.1D;
                if (pinned) {
                    Vec3 v = hostile.getDeltaMovement();
                    hostile.setDeltaMovement(v.x, Math.min(v.y, -0.08D), v.z);
                    hostile.hurtMarked = true;
                    if (entity.tickCount % 10 == 0) {
                        SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition().id());
                    }
                } else if (entity.tickCount % 10 == 0) {
                    SkillTargets.hurt(level, owner, hostile, 1.5F, entity.definition().id());
                }
            }
            if (entity.tickCount % 20 == 0) {
                SpellFx.zoneTick(level, entity.definition(), base, (float) radius * 0.5F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.MOON).frame(12).band(GlyphKind.TICK_BAND, 60, ColorRole.DIM).band(GlyphKind.RUNE_BAND, 24, ColorRole.INK).band(GlyphKind.DASHED_RING, 12).stamps(StampId.EYE, 12).orbit(7, 0.88F, 4).core(CoreKind.VOID_PIT).stack(3, 0.6F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.SKY)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("firmament", 12.0F))
                .silhouette(Silhouette.rift(Silhouette.Form.DISC, FxKinds.Rift.RING_WOUND, 12.0F, 12.0F, FxKinds.RiftInterior.VOID_BLACK))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.VIGNETTE)
                .budget(3)
                .bounds(14.0F, 2.0F, 22.0F);
    }
}
