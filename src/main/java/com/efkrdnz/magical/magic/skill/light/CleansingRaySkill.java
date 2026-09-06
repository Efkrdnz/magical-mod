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
import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * LIGHT T2 - PURGE / HITSCAN_FAN / THREE_RAY_FAN. After a short windup three piercing rays fan
 * out from the eyes: every hostile crossed is hurt, has every potion effect removed, and is
 * unhallowed (cannot receive new effects) for the duration. Sneak = the fan stands vertical.
 */
public final class CleansingRaySkill implements SkillModule {
    private static final int WINDUP = 6;
    private static final double RANGE = 18.0D;
    private static final float SPREAD = 10.0F;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CLEANSING_RAY;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity fan = SpellEffectEntity.spawn(ctx, ctx.eye().add(0.0D, -0.15D, 0.0D), WINDUP + 14, (float) RANGE, ctx.look());
                fan.setExtra(Math.max(40, ctx.duration()));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 16.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    /** The three ray directions from a look vector: yaw fan, or pitch fan when sneaking. */
    public static Vec3[] rays(Vec3 look, boolean vertical) {
        Vec3[] out = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            float off = (i - 1) * SPREAD;
            out[i] = vertical ? look.xRot((float) Math.toRadians(off)) : look.yRot((float) Math.toRadians(off));
        }
        return out;
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            LivingEntity owner = entity.livingOwner();
            if (owner != null && entity.tickCount < WINDUP) {
                entity.setPos(owner.getEyePosition().add(0.0D, -0.15D, 0.0D));
                entity.setDirection(owner.getLookAngle());
            }
            if (entity.tickCount != WINDUP) {
                return;
            }
            ServerLevel level = entity.serverLevel();
            Vec3 origin = entity.position();
            Vec3[] rays = rays(entity.direction(), entity.sneakMode());
            CompoundTag data = new CompoundTag();
            data.putBoolean("Vertical", entity.sneakMode());
            Set<Integer> struck = new HashSet<>();
            for (int i = 0; i < rays.length; i++) {
                Vec3 end = origin.add(rays[i].scale(RANGE));
                BlockHitResult hit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner != null ? owner : entity));
                double len = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(origin) : RANGE;
                data.putFloat("L" + i, (float) len);
                AABB sweep = new AABB(origin, origin.add(rays[i].scale(len))).inflate(0.6D);
                for (LivingEntity hostile : SkillTargets.hostilesIn(level, entity.owner(), sweep)) {
                    Vec3 rel = hostile.getBoundingBox().getCenter().subtract(origin);
                    double along = rel.dot(rays[i]);
                    if (along < 0.0D || along > len || rel.subtract(rays[i].scale(along)).length() > 0.5D + hostile.getBbWidth() * 0.5D) {
                        continue;
                    }
                    if (!struck.add(hostile.getId())) {
                        continue;
                    }
                    hostile.removeAllEffects();
                    MagicStatusService.apply(hostile, MagicStatus.UNHALLOWED, entity.extra(), entity.definition().id(), entity.owner());
                    SkillTargets.hurt(level, entity.owner(), hostile, entity.damage(), entity.definition(), true);
                    SpellFx.followingCircle(level, entity.definition(), com.efkrdnz.magical.entity.MagicCircleEffectEntity.ROLE_TARGET, hostile, 0.5F, entity.extra());
                }
                if (hit.getType() == HitResult.Type.BLOCK) {
                    SpellFx.decal(level, entity.definition(), hit.getLocation(), new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ()), 0.5F);
                }
            }
            entity.setSyncedData(data);
            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.SCRIPTURE).frame(8).band(GlyphKind.RUNE_BAND, 24).stamps(StampId.CROSS, 3).spokes(3, 0.3F, true).orbit(3, 0.84F, 4).core(CoreKind.SUNBURST).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("cleansing_ray", 18.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.STATIC_GLITCH)
                .bounds(19.0F, 4.0F, 4.0F);
    }
}
