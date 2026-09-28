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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T0 - CONVEY / GROUND_LANE_ALONG_LOOK / LANE. A flowing water strip from the caster's feet
 * along the look yaw: allies ride it, enemies are dragged along it and ground down, and anything
 * carried off the far end is flung. Sneak = reverse the flow (drag-to-me / retreat lane).
 */
public final class RipCurrentSkill implements SkillModule {
    private static final double LENGTH = 10.0D;
    /** Wakes set sliding downstream over the strip each tick: the water, moving. */
    private static final int WAKES_PER_TICK = 2;
    /** The first stretch of the strip carries no wakes, so none slide about under the caster's own view. */
    private static final double WAKE_CLEARANCE = 1.5D;
    /** About how far a wake slides before it fades: none is set closer than this to the downstream end, or the strip would seem to run on past it. */
    private static final double WAKE_RUN = 4.0D;
    /** Ticks between the spray kicked up round the feet of whatever the current is carrying. */
    private static final int CARRY_SPLASH_INTERVAL = 5;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.RIP_CURRENT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 look = ctx.look();
                Vec3 axis = new Vec3(look.x, 0.0D, look.z);
                axis = axis.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : axis.normalize();
                Vec3 start = ctx.feet().add(0.0D, 0.05D, 0.0D);
                SpellEffectEntity lane = SpellEffectEntity.spawn(ctx, start, Math.max(30, ctx.duration()), (float) LENGTH, axis);
                lane.setValue(Math.max(0.6F, ctx.size() * 0.5F));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 10.0F);
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
            Vec3 axis = entity.direction();
            Vec3 start = entity.position();
            double halfWidth = entity.value();
            double flowSpeed = Math.max(0.15D, entity.speed());
            boolean reverse = entity.sneakMode();
            Vec3 flow = reverse ? axis.scale(-1.0D) : axis;
            AABB box = new AABB(start, start.add(axis.scale(LENGTH))).inflate(halfWidth + 0.5D, 2.5D, halfWidth + 0.5D);
            current(level, start, axis, reverse, halfWidth, flowSpeed);
            for (Entity e : level.getEntities(entity, box, en -> en instanceof LivingEntity l && l.isAlive())) {
                LivingEntity living = (LivingEntity) e;
                Vec3 rel = living.position().subtract(start);
                double along = rel.dot(axis);
                double lateral = rel.subtract(axis.scale(along)).length();
                if (along < -0.3D || along > LENGTH + 0.3D || lateral > halfWidth + living.getBbWidth() * 0.5D) {
                    continue;
                }
                Vec3 ground = AimResolver.groundBelow(level, living.position().add(0.0D, 0.5D, 0.0D), 2);
                if (ground == null || living.getY() - ground.y > 1.2D) {
                    continue;
                }
                Vec3 v = living.getDeltaMovement();
                Vec3 pushed = new Vec3(v.x + flow.x * flowSpeed, v.y, v.z + flow.z * flowSpeed);
                double horiz = Math.sqrt(pushed.x * pushed.x + pushed.z * pushed.z);
                if (horiz > 0.5D) {
                    pushed = new Vec3(pushed.x / horiz * 0.5D, pushed.y, pushed.z / horiz * 0.5D);
                }
                boolean hostile = SkillTargets.isHostile(entity.owner(), living);
                boolean offEnd = reverse ? along < 0.4D : along > LENGTH - 0.4D;
                if (offEnd) {
                    pushed = pushed.add(flow.scale(0.6D)).add(0.0D, 0.25D, 0.0D);
                }
                living.setDeltaMovement(pushed);
                living.hurtMarked = true;
                if (entity.tickCount % CARRY_SPLASH_INTERVAL == 0 && living != entity.owner()) {
                    double spread = living.getBbWidth() * 0.3D;
                    level.sendParticles(ParticleTypes.SPLASH, living.getX(), living.getY() + 0.1D, living.getZ(), 4, spread, 0.0D, spread, 0.0D);
                }
                if (hostile && entity.tickCount % 10 == 0) {
                    SkillTargets.hurt(level, entity.owner(), living, entity.damage(), entity.definition().id());
                }
            }
        };
    }

    /** The water itself: wakes set on the strip at random, sliding off downstream at the current's speed. */
    private static void current(ServerLevel level, Vec3 start, Vec3 axis, boolean reverse, double halfWidth, double flowSpeed) {
        Vec3 across = new Vec3(-axis.z, 0.0D, axis.x);
        Vec3 flow = reverse ? axis.scale(-1.0D) : axis;
        // set upstream of where they fade, so the wakes run the strip rather than the ground beyond its end
        double near = reverse ? WAKE_CLEARANCE + WAKE_RUN : WAKE_CLEARANCE;
        double far = reverse ? LENGTH : LENGTH - WAKE_RUN;
        for (int i = 0; i < WAKES_PER_TICK; i++) {
            double along = near + level.random.nextDouble() * (far - near);
            double side = (level.random.nextDouble() * 2.0D - 1.0D) * halfWidth;
            Vec3 p = start.add(axis.scale(along)).add(across.scale(side));
            // a wake keeps exactly the speed it is handed and feels no gravity, so it slides along the floor
            level.sendParticles(ParticleTypes.FISHING, p.x, p.y + 0.08D, p.z, 0, flow.x, 0.0D, flow.z, flowSpeed * 1.4D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.WAVE).frame(5).band(GlyphKind.WAVE_BAND, 8).band(GlyphKind.TICK_BAND, 20, com.efkrdnz.magical.magic.visual.ColorRole.BASE).stamps(StampId.ARROW, 5).core(CoreKind.RIPPLE).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.field(Silhouette.Form.LANE, FxKinds.Field.RIPPLE_WATER, 0.9F, 10.0F, 6, 6).withOpacity(0.75F))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.DROPLET, 1, 0.1F, 10, 0.4F, 0, 1.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.WATER_DROPLETS)
                .bounds(11.0F, 2.0F, 2.0F);
    }
}
