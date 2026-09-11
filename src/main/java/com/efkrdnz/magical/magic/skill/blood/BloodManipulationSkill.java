package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.forge.strike.ShapeMath;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodFieldData;
import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * BLOOD T-1 - the shape you drew, thrown.
 *
 * <p>The other six blood skills are a projectile, a burst, a barrier and a charge: shapes somebody
 * else picked. This one hands the shape to the player. They draw a path in the editor, and the blood
 * forms along exactly that path, out from the middle, damaging everything it sweeps through on the
 * way. What the shape decides is <em>where</em>; the damage per body is the same wherever it lands,
 * because a skill that paid more for a bigger drawing would only ever be drawn one way.
 *
 * <p>What it costs scales with how much was drawn, and it is charged from the clipped path the
 * server built rather than anything the client claimed - the editor's preview runs the same
 * function, so the number the player saw is the number they pay.
 *
 * <p>It passes through walls. That is deliberate for this version: the promise the editor makes is
 * that the drawing is the shape, and a raycast would quietly break that promise every time somebody
 * drew across a doorway.
 */
public final class BloodManipulationSkill implements SkillModule {

    /**
     * Half-thickness across the path. Small: a drawn line should read as a blade, not a wall - and
     * the expansion now fills this band with lanes of cubes rather than scattering one somewhere
     * inside it, so a wide band is a wide band rather than a sparser one.
     */
    public static final float THICKNESS = 0.08F;

    /** Ticks the outermost part of the shape takes to form, before the speed stat scales it. */
    public static final float BASE_FORM_TICKS = 9.0F;

    /** How far past the blood's own thickness a body still counts as caught in it. */
    private static final double HIT_MARGIN = 0.45D;

    private static final String FRONT_KEY = "front";
    private static final String HIT_KEY = "hit";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.BLOOD_MANIPULATION;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                PlayerMagicState state = ctx.state();
                BloodShape shape = state.bloodShapes().shape(state.selectedBloodShape());
                if (shape.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("message.magical.blood_shape_empty"), true);
                    return CastResult.FAILED;
                }

                // The reach clip happens here and nowhere else. Not at submit time: a player who
                // respecs out of reach should find the far half of their drawing ignored, not
                // deleted, and buying the reach back has to bring it straight back.
                double halfExtent = BloodShapeRules.halfExtentBlocks(ctx.size());
                List<double[]> clipped = BloodShapeGeometry.clip(shape, halfExtent);
                if (clipped.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("message.magical.blood_shape_out_of_reach"), true);
                    return CastResult.FAILED;
                }

                double arcLength = BloodShapeGeometry.arcLength(shape, halfExtent);
                if (!BloodService.pay(player, state, BloodShapeRules.bloodCost(arcLength))) {
                    return CastResult.FAILED;
                }

                // Signed: which way the blood stands off the drawn plane is the spread slider's
                // second job, and the caster's own height is what its ends are measured against.
                float wallHeight = (float) BloodShapeRules.wallHeightBlocks(
                        shape.spreadPercent(), player.getBbHeight());
                double pitch = BloodShapeRules.voxelPitch(arcLength, wallHeight, THICKNESS,
                        clipped.size());
                List<double[]> spine = new ArrayList<>(clipped.size());
                for (double[] line : clipped) {
                    spine.add(BloodShapeGeometry.resample(line, pitch));
                }

                float speed = Math.max(0.35F, ctx.stats().speed());
                float formTicks = BASE_FORM_TICKS / speed;
                int life = Math.max(Math.round(formTicks) + 12, ctx.duration());

                BloodFieldData data = BloodFieldData.of(spine,
                        (float) BloodShapeRules.heightOffsetBlocks(shape.heightPercent(),
                                player.getBbHeight()),
                        wallHeight, THICKNESS, (float) pitch,
                        BloodShapeGeometry.readingYaw(shape, player.getYRot()),
                        BloodShapeGeometry.readingPitch(shape, player.getXRot()),
                        formTicks, shape.flags(), player.getId());

                SpellEffectEntity field = SpellEffectEntity.spawn(ctx, ctx.feet(), life,
                        data.reach() + Math.abs(wallHeight), ctx.look());
                field.setSyncedData(data.encode());
                field.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_SLIDE,
                        SoundSource.PLAYERS, 0.7F, 0.6F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                // Mobs have no editor to draw in, so there is no shape for them to cast.
                return null;
            }

            @Override
            public TuningView tuning() {
                // Reach rides on the size stat under its own label. A sixth tuning stat would widen
                // the codex button bands past the loadout range and change the tuning save format;
                // relabelling is what beam_radius and seal_reach already do.
                return TuningView.NO_SPEED.labels(null, null,
                        "screen.magical.tuning.blood_reach", null);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            BloodFieldData data = BloodFieldData.decode(entity.syncedData());
            if (data == null || data.isEmpty()) {
                entity.finish();
                return;
            }
            Entity owner = entity.owner();
            if (owner != null && owner.isAlive()) {
                // The field is drawn relative to the caster, so it travels with them.
                entity.setPos(owner.getX(), owner.getY(), owner.getZ());
            }

            CompoundTag scratch = entity.serverData();
            float front = data.formedFraction(entity.tickCount);
            float previous = scratch.getFloat(FRONT_KEY);
            if (front <= previous) {
                return;
            }
            scratch.putFloat(FRONT_KEY, front);
            sweep(level, entity, data, owner, previous, front, scratch);
        };
    }

    /**
     * Damages everything the blood reached this tick.
     *
     * <p>The front is a radius rather than a position along the drawn path, because a radius is the
     * order the blood actually forms in. Walking the path instead would put the damage somewhere the
     * player can see the blood is not, on any shape that doubles back.
     */
    private static void sweep(ServerLevel level, SpellEffectEntity entity, BloodFieldData data,
            Entity owner, float from, float to, CompoundTag scratch) {
        float reach = data.reach();
        if (reach <= 1.0E-4F) {
            return;
        }
        float yaw = data.baseYaw();
        float pitch = data.basePitch();
        if (data.keepRotating() && owner != null) {
            yaw = owner.getYRot();
            pitch = owner.getXRot();
        }
        double[] basis = BloodShapeGeometry.basis(yaw, pitch);
        double[] base = new double[3];
        double[] top = new double[3];
        double radius = data.thickness() + HIT_MARGIN;

        List<Integer> struck = new ArrayList<>();
        for (int i = 0; i < data.spine().length; i++) {
            double u = data.u(i);
            double v = data.v(i);
            float rank = (float) (Math.sqrt(u * u + v * v) / reach);
            if (rank <= from || rank > to) {
                continue;
            }
            BloodShapeGeometry.project(u, 0.0D, v, data.heightOffset(), basis, base);
            BloodShapeGeometry.project(u, data.wallHeight(), v, data.heightOffset(), basis, top);

            double ax = entity.getX() + base[0];
            double ay = entity.getY() + base[1];
            double az = entity.getZ() + base[2];
            double bx = entity.getX() + top[0];
            double by = entity.getY() + top[1];
            double bz = entity.getZ() + top[2];

            AABB reachBox = new AABB(Math.min(ax, bx) - radius, Math.min(ay, by) - radius,
                    Math.min(az, bz) - radius, Math.max(ax, bx) + radius, Math.max(ay, by) + radius,
                    Math.max(az, bz) + radius);
            for (LivingEntity victim : SkillTargets.hostilesIn(level, owner, reachBox)) {
                if (alreadyHit(scratch, struck, victim.getId())) {
                    continue;
                }
                if (ShapeMath.capsuleDistance(ax, ay, az, bx, by, bz,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D,
                        victim.getZ()) > radius + victim.getBbWidth() * 0.5D) {
                    continue;
                }
                struck.add(victim.getId());
                SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition().id());
            }
        }
        if (!struck.isEmpty()) {
            recordHits(scratch, struck);
        }
    }

    /**
     * Whether this body has already taken the strike.
     *
     * <p>A shape that doubles back passes over the same spot twice, and the promise is that the blood
     * hits everything once. The record lives on the entity rather than in the behaviour because a
     * behaviour is one shared stateless lambda for every cast of the skill.
     */
    private static boolean alreadyHit(CompoundTag scratch, List<Integer> pending, int id) {
        if (pending.contains(id)) {
            return true;
        }
        for (int seen : scratch.getIntArray(HIT_KEY)) {
            if (seen == id) {
                return true;
            }
        }
        return false;
    }

    private static void recordHits(CompoundTag scratch, List<Integer> struck) {
        int[] existing = scratch.getIntArray(HIT_KEY);
        int[] merged = new int[existing.length + struck.size()];
        System.arraycopy(existing, 0, merged, 0, existing.length);
        for (int i = 0; i < struck.size(); i++) {
            merged[existing.length + i] = struck.get(i);
        }
        scratch.putIntArray(HIT_KEY, merged);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.SPLATTER).frame(11)
                        .band(GlyphKind.DASHED_RING, 18, ColorRole.HOT)
                        .band(GlyphKind.TICK_BAND, 26, ColorRole.DIM)
                        .stamps(StampId.THORN, 7).core(CoreKind.IRIS, ColorRole.BRIGHT)
                        .spin(SpinSignature.SWEEP))
                .anchor(CircleAnchor.GROUND)
                .throughTerrain(true)
                // The whole body of the effect is the voxel field, so the silhouette is the painter
                // and nothing else. Its id is unique to this skill on purpose: the authoring lint
                // skips the collision check for custom silhouettes, so a shared id fails in silence.
                .silhouette(Silhouette.custom("blood_manipulation", 8.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DROPLET, FxKinds.Overlay.WATER_DROPLETS)
                .budget(3)
                .bounds(22.0F, 4.0F, 2.0F);
    }
}
