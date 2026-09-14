package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodFieldData;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.passive.BloodHarvestRules;
import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * BLOOD T-1 - put blood over there.
 *
 * <p>Your blood gathers into a short straight field at your hand, flies as one body, and where it
 * lands it shatters into a battery pool: blood set down on purpose, for Coagulate, for the next
 * Spear, or to Vein Walk onto. A body it strikes takes Bite, scaled by potency. Thrown while
 * standing on a pool of your own, it drinks that pool first, so a mage can keep throwing the same
 * blood forward.
 *
 * <p>The field is anchored on the spear entity rather than on the caster, which is the one new
 * thing the voxel system needed for it; the shatter is the field's own dissolve, run over the
 * last few ticks of a life cut short at the impact.
 */
public final class CrimsonSpearSkill implements SkillModule {

    /** Length of the spear at one point of Mass, in blocks. */
    public static final float BASE_LENGTH = 2.5F;

    /** Ticks the blood takes to gather at the hand before it flies. */
    public static final int FORM_TICKS = 6;

    /** Blood the spear leaves where it shatters, at one point of Mass. */
    public static final int BASE_POOL = 12;

    /** How near one of their pools the caster must stand for the throw to drink it. */
    public static final double DRINK_RADIUS = 2.0D;

    /** Ticks the shattered spear takes to drain into its pool. */
    public static final int SHATTER_TICKS = 8;

    /** Cubes the spear may have. VoxelStyle.SPEAR's cap, which the server cannot read. */
    public static final int CUBES = 220;

    private static final double STEP = 0.9D;
    private static final double HIT_RADIUS = 0.6D;
    private static final float THICKNESS = 0.06F;
    private static final float WALL = 0.12F;
    private static final double BASE_PITCH = 0.06D;
    private static final double HAND_FORWARD = 0.6D;
    private static final double HAND_SIDE = 0.35D;
    private static final double HAND_DROP = 0.25D;
    private static final double POOL_DROP = 4.0D;
    private static final int EXTRA_SHATTERED = 1;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CRIMSON_SPEAR;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (ctx.caster() instanceof ServerPlayer player) {
                    PlayerMagicState state = ctx.state();
                    // Standing on your own blood, the throw drinks it: the pool pays before the
                    // Vessel does, and anything over goes into the Vessel.
                    BloodHarvestEntity.nearestPool(player, DRINK_RADIUS).ifPresent(pool -> {
                        int drunk = pool.drink(player);
                        if (drunk > 0) {
                            state.addBloodVessel(drunk);
                        }
                    });
                    if (!BloodService.pay(player, state, BloodService.cost(ctx.stats()))) {
                        return CastResult.FAILED;
                    }
                }
                float mass = ctx.size();
                float length = BASE_LENGTH * mass;
                double pitch = pitchFor(length);
                List<double[]> spine = List.of(BloodShapeGeometry.resample(
                        new double[] {0.0D, 0.0D, 0.0D, length}, pitch));
                BloodFieldData data = BloodFieldData.of(spine, 0.0F, WALL, THICKNESS, (float) pitch,
                        ctx.caster().getYRot(), ctx.caster().getXRot(), FORM_TICKS, 0, ctx.caster().getId())
                        .withAnchor(BloodFieldData.ANCHOR_ENTITY);
                SpellEffectEntity spear = SpellEffectEntity.spawn(ctx, hand(ctx.caster(), ctx.look()),
                        Math.max(30, ctx.duration()), (float) (HIT_RADIUS * mass), ctx.look());
                spear.setSyncedData(data.encode());
                spear.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, ctx.caster().blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE,
                        SoundSource.PLAYERS, 0.7F, 0.9F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return new TuningView(true, true, true, false, true,
                        "screen.magical.tuning.bite", "screen.magical.tuning.flow",
                        "screen.magical.tuning.mass", null, "screen.magical.tuning.thrift");
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 24.0F);
            }
        };
    }

    /**
     * Where the spear forms: just ahead of the eyes, off to the throwing side and a little below,
     * so it reads as held rather than sighted down - and flies as a line beside the aim, not a dot
     * on it.
     */
    private static Vec3 hand(LivingEntity caster, Vec3 look) {
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() > 1.0E-6D ? right.normalize() : Vec3.ZERO;
        return caster.getEyePosition().add(look.scale(HAND_FORWARD)).add(right.scale(HAND_SIDE))
                .add(0.0D, -HAND_DROP, 0.0D);
    }

    /**
     * The coarsest pitch that keeps a spear of this length inside the style's cap. A long spear
     * gets bigger cubes rather than a shorter field: the expansion would otherwise run out of cap
     * partway along and the spear would simply stop.
     */
    static double pitchFor(float length) {
        double pitch = BASE_PITCH;
        for (int step = 0; step < 24; step++) {
            int points = (int) Math.round(length / pitch) + 1;
            if (BloodShapeGeometry.voxelDemand(points, WALL, THICKNESS, pitch) <= CUBES) {
                return pitch;
            }
            pitch *= 1.15D;
        }
        return pitch;
    }

    @Override
    public SpellBehavior behavior() {
        return spear -> {
            if (spear.extra() == EXTRA_SHATTERED) {
                // Draining into the ground; the short life left runs the dissolve.
                return;
            }
            ServerLevel level = spear.serverLevel();
            if (spear.tickCount <= FORM_TICKS) {
                // Gathering at the hand: it rides the hand until it is whole, then flies.
                if (spear.owner() instanceof LivingEntity caster && caster.isAlive()) {
                    spear.setPos(hand(caster, spear.direction()));
                }
                return;
            }
            if (spear.tickCount >= spear.life() - SHATTER_TICKS - 1) {
                // Nothing struck before the throw ran out: the blood still pools where it falls,
                // so a missed spear is a battery set down at range rather than blood lost.
                shatter(spear, level, spear.position());
                return;
            }
            Vec3 from = spear.position();
            Vec3 to = from.add(spear.direction().scale(STEP * Math.max(0.4D, spear.speed())));
            BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, spear));
            if (wall.getType() == HitResult.Type.BLOCK) {
                shatter(spear, level, wall.getLocation());
                return;
            }
            spear.setPos(to);
            float potency = spear.owner() instanceof LivingEntity living ? BloodService.potency(living) : 1.0F;
            for (LivingEntity victim : SkillTargets.hostilesWithin(level, spear.owner(), to, spear.radius())) {
                SkillTargets.hurt(level, spear.owner(), victim, spear.damage() * potency, spear.definition(), true);
                SkillTargets.shove(victim, to, spear.knockback(), 0.15D);
                shatter(spear, level, victim.position());
                return;
            }
        };
    }

    /**
     * The spear breaks: it stops where it is, drains over {@link #SHATTER_TICKS} through the
     * field's own dissolve, and what it was pools on the ground under the impact as a battery.
     */
    private static void shatter(SpellEffectEntity spear, ServerLevel level, Vec3 at) {
        spear.setExtra(EXTRA_SHATTERED);
        spear.setLife(spear.tickCount + SHATTER_TICKS);
        if (spear.owner() instanceof ServerPlayer owner) {
            PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
            float mass = (float) (spear.radius() / HIT_RADIUS);
            int blood = Math.max(1, Math.round(BASE_POOL * mass));
            boolean clotting = ClassPassiveEffects.on(state, MagicPassiveContent.CLOTTING.id());
            BloodHarvestEntity.spawn(level, owner, ground(level, spear, at), BloodHarvestRules.KIND_BATTERY,
                    blood, BloodHarvestRules.lifetime(clotting));
        }
        level.playSound(null, spear.blockPosition(), SoundEvents.HONEY_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    /** The ground under the impact, within a few blocks, so a spear broken mid-air still pools somewhere to stand. */
    private static Vec3 ground(ServerLevel level, SpellEffectEntity spear, Vec3 at) {
        BlockHitResult floor = level.clip(new ClipContext(at, at.add(0.0D, -POOL_DROP, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, spear));
        Vec3 rest = floor.getType() == HitResult.Type.BLOCK ? floor.getLocation() : at;
        return new Vec3(rest.x, rest.y + 0.05D, rest.z);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.BONE).frame(3)
                        .band(GlyphKind.TOOTH_BAND, 10, ColorRole.BRIGHT)
                        .band(GlyphKind.DASHED_RING, 16, ColorRole.HOT)
                        .stamps(StampId.NEEDLE, 6).core(CoreKind.SUNBURST, ColorRole.BRIGHT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                // The whole body of the spear is the voxel field; the id is unique on purpose, since
                // the authoring lint skips the collision check for custom silhouettes.
                .silhouette(Silhouette.custom("crimson_spear", 4.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DROPLET, FxKinds.Overlay.FLASH)
                // The whole cap: the budget class scales it, and the pitch is chosen against it.
                .budget(3)
                .bounds(9.0F, 2.0F, 2.0F);
    }
}
