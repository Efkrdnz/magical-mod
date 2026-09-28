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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
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
    /** Chips of ice off each bolt that lands: three bolts every other tick, so three chips a tick. */
    private static final int SHATTER_CHIPS = 2;
    /**
     * The white of a hailstone bursting on the floor, beside its ice: vanilla's snowball grit.
     * With the chips that is six particles a tick for as long as the rain lasts, over the budget a
     * lingering emitter keeps, because a hailstorm is a thing made of particles and three chips a
     * tick scattered over a disc four blocks across read as nothing falling at all.
     */
    private static final int SHATTER_SNOW = 2;
    /**
     * The warning, told the way weather tells it: the marked disc starts to snow before the hail
     * comes. Snowflakes a tick, how high over the disc they start, and how fast they are sent down.
     */
    private static final int WARNING_FLAKES = 2;
    private static final double WARNING_FLAKE_HEIGHT = 3.2D;
    private static final double WARNING_FLAKE_DROP = 0.07D;
    /** How far round the caster the warning keeps from snowing, when the disc is under them. */
    private static final double OWN_CLEAR = 0.8D;

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
            if (t < WARNING) {
                snowfall(level, entity);
            }
            shatter(level, entity, t, rain);
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

    /**
     * Every bolt breaks on what it hits: a couple of chips of ice where it lands. A bolt is struck
     * the tick it is fired but drawn falling from the sky sigil for as long as the painter's
     * {@code fallTicks}, so the chips are thrown for the wave that is seen landing now - thrown on
     * the firing tick they burst out of bare ground before any bolt had come down.
     */
    private static void shatter(ServerLevel level, SpellEffectEntity entity, int t, int rain) {
        int fall = Math.max(1, Math.round(Math.max(2.0F, entity.value()) / BOLT_SPEED));
        int wave = t - fall;
        if (wave < WARNING || wave >= WARNING + rain || (wave - WARNING) % BOLT_SPACING != 0) {
            return;
        }
        double skyY = entity.getY() + entity.value();
        BlockParticleOption ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
        for (int i = 0; i < BOLTS_PER_WAVE; i++) {
            Vec3 off = boltOffset(entity.seed(), wave, i, entity.radius());
            Vec3 from = new Vec3(entity.getX() + off.x, skyY, entity.getZ() + off.z);
            Vec3 to = new Vec3(from.x, entity.getY() - 2.0D, from.z);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 end = hit.getLocation();
                level.sendParticles(ice, end.x, end.y + 0.05D, end.z, SHATTER_CHIPS, 0.1D, 0.02D, 0.1D, 0.12D);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, end.x, end.y + 0.1D, end.z, SHATTER_SNOW, 0.08D, 0.02D, 0.08D, 0.08D);
            }
        }
    }

    /**
     * Snow drifting down over the marked disc while the warning runs, under a sky sigil that sits
     * twelve blocks up and out of a level view: the ground under the hail is where the warning has
     * to be seen. Capped under the sky sigil's own height, so a disc under a low ceiling snows from
     * the ceiling rather than through it.
     */
    private static void snowfall(ServerLevel level, SpellEffectEntity entity) {
        double radius = entity.radius();
        double height = Math.min(WARNING_FLAKE_HEIGHT, Math.max(1.0D, entity.value() - 0.3D));
        LivingEntity owner = entity.livingOwner();
        for (int i = 0; i < WARNING_FLAKES; i++) {
            double r = radius * Math.sqrt(level.random.nextDouble());
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double x = entity.getX() + Math.cos(a) * r;
            double z = entity.getZ() + Math.sin(a) * r;
            // a disc centred on the caster (sneak) snows round them, not down across their eyes
            if (owner != null) {
                double dx = x - owner.getX();
                double dz = z - owner.getZ();
                if (dx * dx + dz * dz < OWN_CLEAR * OWN_CLEAR) {
                    continue;
                }
            }
            double y = entity.getY() + height * (0.6D + 0.4D * level.random.nextDouble());
            level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 0, 0.0D, -1.0D, 0.0D, WARNING_FLAKE_DROP);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(1)
                // hail is ice, not a splash of water
                .accent(Accent.FROST)
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
