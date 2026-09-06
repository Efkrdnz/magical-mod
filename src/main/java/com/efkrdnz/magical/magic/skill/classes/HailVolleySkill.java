package com.efkrdnz.magical.magic.skill.classes;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * RANGER - ARTILLERY_RAIN / AIM_POINT_DELAYED / FALLING_BOLT_COLUMN. After a warning, ice bolts drop
 * from a sky sigil onto random points of the marked disc; every bolt dies on the first block it
 * meets, so cover is safe and the open is not. Sneak = centre the disc on yourself.
 */
public final class HailVolleySkill implements SkillModule {
    public static final int WARNING = 20;
    public static final int RAIN = 60;
    public static final int BOLT_SPACING = 2;
    public static final int BOLTS_PER_WAVE = 3;
    public static final float BOLT_SPEED = 1.4F;
    public static final double SKY_HEIGHT = 12.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HAIL_VOLLEY;
    }

    /** Deterministic bolt offset shared by the server and the painter. */
    public static Vec3 boltOffset(int seed, int tick, int index, float radius) {
        int n = seed * 7919 + tick * 104729 + index * 1299709;
        n = (n ^ 61) ^ (n >>> 16);
        n *= 9;
        n = n ^ (n >>> 4);
        n *= 0x27d4eb2d;
        n = n ^ (n >>> 15);
        float a = (n & 0xFFFF) / (float) 0xFFFF;
        float r = ((n >>> 16) & 0xFFFF) / (float) 0xFFFF;
        double angle = a * Math.PI * 2.0D;
        double dist = Math.sqrt(r) * radius;
        return new Vec3(Math.cos(angle) * dist, 0.0D, Math.sin(angle) * dist);
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 ground = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                // the sky sigil sits 12 up, or under the first ceiling (never lower than 2)
                double skyY = ground.y + SKY_HEIGHT;
                BlockHitResult up = ctx.level().clip(new ClipContext(ground.add(0.0D, 1.0D, 0.0D), ground.add(0.0D, SKY_HEIGHT, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (up.getType() == HitResult.Type.BLOCK) {
                    skyY = Math.max(ground.y + 2.0D, up.getLocation().y - 0.3D);
                }
                SpellEffectEntity volley = SpellEffectEntity.spawn(ctx, ground, WARNING + Math.max(20, ctx.duration()) + 8, Math.max(1.0F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                volley.setValue((float) (skyY - ground.y));
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
            public MobCastProfile mob() {
                return MobCastProfile.attack(4.0F, 20.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            int t = entity.tickCount;
            int rain = Math.max(20, entity.duration());
            if (t < WARNING || t >= WARNING + rain || (t - WARNING) % BOLT_SPACING != 0) {
                if (t == WARNING) {
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                }
                return;
            }
            double skyY = entity.getY() + entity.value();
            for (int i = 0; i < BOLTS_PER_WAVE; i++) {
                Vec3 off = boltOffset(entity.seed(), t, i, entity.radius());
                Vec3 from = new Vec3(entity.getX() + off.x, skyY, entity.getZ() + off.z);
                Vec3 to = new Vec3(from.x, entity.getY() - 2.0D, from.z);
                BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
                Vec3 end = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : to;
                AABB column = new AABB(from, end).inflate(0.5D);
                for (LivingEntity victim : SkillTargets.hostilesIn(level, entity.owner(), column)) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition().id());
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
                    victim.setDeltaMovement(victim.getDeltaMovement().add(0.0D, -0.05D, 0.0D));
                    SkillTargets.shove(victim, from, 0.35D * Math.max(0.3D, entity.knockback()), 0.0D);
                }
                if (i == 0 && (t - WARNING) % 10 == 0) {
                    SpellFx.burst(level, entity.definition(), end, new Vec3(0.0D, 1.0D, 0.0D), 0.6F);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.QUIVER).frame(10).band(GlyphKind.DASHED_RING, 30).band(GlyphKind.FACET_BAND, 10).stamps(StampId.BAR, 10).core(CoreKind.RIPPLE).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.SKY)
                .silhouette(Silhouette.custom("hail_volley", 2.0F))
                .silhouette(Silhouette.mark(FxKinds.Mark.FROST_BLOOM, 2.0F, 6).withOpacity(0.7F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.FROST_BLOOM, FxKinds.Smoke.FROST_CRYSTAL, FxKinds.Overlay.FROST_EDGES)
                .budget(2)
                .bounds(4.0F, 14.0F, 2.0F);
    }
}
