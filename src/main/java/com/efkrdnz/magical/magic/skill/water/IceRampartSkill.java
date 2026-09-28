package com.efkrdnz.magical.magic.skill.water;

import com.efkrdnz.magical.entity.fx.SolidConstructEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T2 (BARRIER) - OBSTRUCT / AIM_SURFACE_RAISE / WALL. Five solid ice segments rise at the
 * aimed point perpendicular to the look: real collision, broken line of sight, projectiles stop,
 * each segment chips and shatters. Sneak = the wall runs parallel to the look, far end at the aim.
 */
public final class IceRampartSkill implements SkillModule {
    private static final int SEGMENTS = 5;
    private static final byte MODE_SEGMENT = 2;
    /** Crumbs of ice breaking off the faces of each segment as it punches out of the ground; two more of the ground itself. */
    private static final int RISE_CRUMBS = 4;
    /** Crumbs of ice a segment breaks into when it shatters or thaws out. */
    private static final int SHATTER_CRUMBS = 8;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ICE_RAMPART;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 look = ctx.look();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
                Vec3 across = new Vec3(-flat.z, 0.0D, flat.x);
                Vec3 axis = ctx.sneak() ? flat : across;
                Vec3 facing = ctx.sneak() ? across : flat;
                Vec3 centre = ctx.aim().point();
                if (ctx.sneak()) {
                    centre = centre.subtract(flat.scale(SEGMENTS * 0.5D));
                }
                int life = Math.max(60, ctx.duration());
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), centre, life, 0.5F, facing, (int) (ctx.seed() & 63));
                template.setMode(MODE_SEGMENT);
                for (int i = 0; i < SEGMENTS; i++) {
                    Vec3 p = centre.add(axis.scale(i - (SEGMENTS - 1) * 0.5D));
                    Vec3 ground = AimResolver.groundBelow(ctx.level(), p.add(0.0D, 2.0D, 0.0D), 4);
                    if (ground != null && Math.abs(ground.y - centre.y) <= 2.0D) {
                        p = ground;
                    }
                    for (LivingEntity hit : SkillTargets.hostilesIn(ctx.level(), ctx.caster(), new net.minecraft.world.phys.AABB(p.x - 0.5D, p.y, p.z - 0.5D, p.x + 0.5D, p.y + 3.2D, p.z + 0.5D))) {
                        SkillTargets.hurt(ctx.level(), ctx.caster(), hit, ctx.damage(), ctx.definition(), true);
                        SkillTargets.shove(hit, p, 0.9D * ctx.stats().knockback() + 0.3D, 0.25D);
                    }
                    SolidConstructEntity segment = SolidConstructEntity.create(ctx.level(), template, p, 1.0F, 3.2F, 24.0F, i);
                    segment.setMode(MODE_SEGMENT);
                    segment.setDirection(facing);
                    ctx.level().addFreshEntity(segment);
                    rise(ctx.level(), p, axis, facing);
                }
                SpellFx.impact(ctx.level(), ctx.definition(), centre.add(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, ctx.caster(), 1.4F);
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
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                if (!(entity instanceof SolidConstructEntity segment)) {
                    return;
                }
                // stop hostile projectiles that touch the segment
                for (net.minecraft.world.entity.Entity projectile : com.efkrdnz.magical.magic.service.SpellIntercept.hostileProjectiles(entity.serverLevel(), segment.getBoundingBox().getCenter(), 1.2D, entity.owner())) {
                    if (projectile.getBoundingBox().intersects(segment.getBoundingBox().inflate(0.2D))) {
                        com.efkrdnz.magical.magic.service.SpellIntercept.erase(projectile);
                        SpellFx.barrierHit(entity.serverLevel(), entity.definition(), projectile.position(), entity.direction());
                    }
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                SpellFx.impact(entity.serverLevel(), entity.definition(), entity.position().add(0.0D, 1.6D, 0.0D), entity.direction(), null, entity.owner(), 0.9F);
                // the segment comes apart down its whole height, not just at the point the impact marks
                entity.serverLevel().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                        entity.getX(), entity.getY() + 1.6D, entity.getZ(), SHATTER_CRUMBS, 0.3D, 0.8D, 0.3D, 0.1D);
            }
        };
    }

    /**
     * Ice punching up out of the ground where a segment stands: crumbs of ice breaking off its two
     * faces, and crumbs of the ground it broke through kicked off its foot.
     *
     * <p>Plain block crumbs, not a dust pillar: a pillar re-rolls its lift with half a block a tick
     * of noise, so some of every spurt shot ten blocks over the wall and hung in the sky as dark
     * specks. A crumb takes the direction it is handed and hops at its own small speed, so the
     * matter stays on the ice it came off.
     */
    private static void rise(ServerLevel level, Vec3 base, Vec3 axis, Vec3 facing) {
        BlockParticleOption ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        for (int i = 0; i < RISE_CRUMBS; i++) {
            // on the two faces, never inside: the segment is drawn solid and would hide them
            double along = (level.random.nextDouble() - 0.5D) * 0.8D;
            double out = i % 2 == 0 ? 0.6D : -0.6D;
            double up = 0.3D + level.random.nextDouble() * 1.4D;
            level.sendParticles(ice, base.x + axis.x * along + facing.x * out, base.y + up, base.z + axis.z * along + facing.z * out, 0, facing.x * out, 0.4D, facing.z * out, 1.0D);
        }
        BlockState ground = level.getBlockState(BlockPos.containing(base.x, base.y - 0.5D, base.z));
        if (!ground.isAir()) {
            BlockParticleOption rubble = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int side = -1; side <= 1; side += 2) {
                double along = (level.random.nextDouble() - 0.5D) * 0.7D;
                double out = side * 0.65D;
                level.sendParticles(rubble, base.x + axis.x * along + facing.x * out, base.y + 0.1D, base.z + axis.z * along + facing.z * out, 0, facing.x * out, 0.3D, facing.z * out, 1.0D);
            }
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .accent(Accent.FROST)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.SHELL).frame(8).band(GlyphKind.TOOTH_BAND, 16).band(GlyphKind.RUNE_BAND, 8, com.efkrdnz.magical.magic.visual.ColorRole.BASE).stamps(StampId.HEX, 8).core(CoreKind.CROSS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.body(Silhouette.Form.PLATE_FAN, FxKinds.Body.ICE, 4, 0.55F, 3.2F).forModes(1))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.FROST_BLOOM, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.CRACKED_GLASS)
                .bounds(2.0F, 4.0F, 1.0F);
    }
}
