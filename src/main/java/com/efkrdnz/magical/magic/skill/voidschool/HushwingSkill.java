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
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * VOID T0 - SILENCE / RELEASED_FLOCK / CLOUD. A flock of ink moths drifts from the hand, veers
 * toward the nearest hostile and clings around its head; everything inside the flock is hushed
 * (cannot cast) and nibbled. Sneak = hold the flock as a hushing halo around yourself.
 */
public final class HushwingSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HUSHWING;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 start = ctx.eye().add(ctx.look().scale(0.8D));
                SpellEffectEntity flock = SpellEffectEntity.spawn(ctx, ctx.sneak() ? ctx.feet().add(0.0D, 1.0D, 0.0D) : start, Math.max(40, ctx.duration()), 1.6F * Math.max(0.5F, ctx.size()), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(2.0F, 12.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            if (entity.sneakMode()) {
                if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(living.getX(), living.getY() + 1.1D, living.getZ());
            } else {
                Entity target = entity.target();
                if (entity.tickCount % 10 == 0) {
                    List<LivingEntity> near = SkillTargets.hostilesWithin(level, owner, entity.position(), 7.0D);
                    entity.setTarget(near.isEmpty() ? null : near.get(0));
                    target = entity.target();
                }
                double step = 0.32D * Math.max(0.3D, entity.speed() * 3.0D);
                if (target instanceof LivingEntity living && living.isAlive()) {
                    Vec3 head = living.getEyePosition();
                    Vec3 to = head.subtract(entity.position());
                    if (to.length() > 1.2D) {
                        entity.setPos(entity.position().add(to.normalize().scale(step)));
                    } else {
                        entity.setPos(head.add(0.0D, -0.2D, 0.0D));
                    }
                    if (to.lengthSqr() > 1.0E-4D) {
                        entity.setDirection(to.normalize());
                    }
                } else {
                    entity.setPos(entity.position().add(entity.direction().scale(step)));
                }
            }
            for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, entity.position(), entity.radius())) {
                MagicStatusService.apply(hostile, MagicStatus.SILENCED, 12, entity.definition().id(), owner);
                if (entity.tickCount % 5 == 0) {
                    SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition().id());
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.MOTH).frame(6).band(GlyphKind.WAVE_BAND, 9, ColorRole.DIM).band(GlyphKind.DASHED_RING, 18).stamps(StampId.FEATHER, 6).core(CoreKind.RIPPLE, ColorRole.INK).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.ASH_FLAKE, 44, 1.6F).withRole(ColorRole.INK))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.ASH_FLAKE, FxKinds.Overlay.STATIC_GLITCH)
                .budget(1)
                .bounds(3.0F, 3.0F, 2.0F);
    }
}
