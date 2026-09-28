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
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
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
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * SPATIAL T4 - SCATTER / AIM_POINT_TELEGRAPHED / RADIAL_STARBURST. A star-crack opens with one arm
 * per hostile inside; after a counterable windup every hostile still inside is flung, one every two
 * ticks, to its own exit far out along its arm so the pack is broken. Sneak = centre the starburst
 * on your own feet (you are never thrown).
 */
public final class DiasporaSkill implements SkillModule {
    private static final int WINDUP = 20;
    private static final int THROW_SPACING = 2;
    private static final int LINGER = 60;
    private static final double RADIUS = 8.0D;
    private static final int MIN_ARMS = 3;
    private static final int MAX_ARMS = 8;
    /** Where along each arm of the star the floor is kicked up as it tears open, in blocks. */
    private static final double ARM_DUST_NEAR = 2.5D;
    private static final double ARM_DUST_FAR = 6.0D;
    /** The tear running out along each arm: motes launched from these distances, each carried this far. */
    private static final double ARM_RUN_START = 0.8D;
    private static final double ARM_RUN_STEP = 1.1D;
    private static final double ARM_RUN_LENGTH = 4.0D;
    /** Through the windup, one mote off every arm this often, never nearer the heart than this. */
    private static final int ARM_GLITTER_INTERVAL = 3;
    private static final double ARM_GLITTER_MIN = 0.8D;
    /** A flung body's path: a mote every this many blocks, at most this many, drifting toward the exit. */
    private static final double FLING_SPACING = 1.5D;
    private static final int FLING_MOTES_MAX = 12;
    private static final double FLING_DRIFT = 0.05D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.DIASPORA;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 centre = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                SpellEffectEntity star = SpellEffectEntity.spawn(ctx, centre, WINDUP + MAX_ARMS * THROW_SPACING + LINGER, (float) RADIUS, new Vec3(0.0D, 1.0D, 0.0D));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 24.0D;
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
                return MobCastProfile.attack(4.0F, 24.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static Vec3 findExit(ServerLevel level, Vec3 centre, double angle, LivingEntity victim) {
        for (double offset : new double[] {0.0D, 20.0D, -20.0D, 40.0D, -40.0D}) {
            double a = angle + Math.toRadians(offset);
            for (double dist = 20.0D; dist >= 12.0D; dist -= 1.0D) {
                Vec3 p = centre.add(Math.cos(a) * dist, 0.0D, Math.sin(a) * dist);
                Vec3 spot = SafeSpotSearch.standableNear(level, p, 4, 6, victim.getBbWidth(), victim.getBbHeight());
                if (spot != null) {
                    return spot;
                }
            }
        }
        return null;
    }

    /**
     * The star opening all the way, told by the floor it runs through: a little of the ground's own
     * dust kicked up along every arm, on the bearings the painter draws the arms at and the throws
     * leave by.
     */
    private static void tearOpen(ServerLevel level, MagicSkillDefinition definition, Vec3 centre, int arms, float baseYaw) {
        // a wide star thins each arm, so any star is one beat of 24 to 32 (two dust points and the
        // runners per arm) rather than the 45 to 48 a five- or eight-armed star used to throw
        int perPoint = arms <= 3 ? 3 : arms <= 5 ? 2 : 1;
        int runners = arms <= 4 ? 3 : 2;
        for (int i = 0; i < arms; i++) {
            Vec3 along = armDirection(i, arms, baseYaw);
            SpatialMatter.groundLine(level, centre.add(along.scale(ARM_DUST_NEAR)), centre.add(along.scale(ARM_DUST_FAR)), 2, perPoint);
            // and the crack itself running out along the arm: motes launched from near the heart
            // down the arm's bearing, so the star is seen tearing outward rather than just lit
            for (int k = 0; k < runners; k++) {
                double start = ARM_RUN_START + k * ARM_RUN_STEP;
                SpatialMatter.launch(level, definition, centre.add(along.scale(start)).add(0.0D, 0.25D, 0.0D), along, ARM_RUN_LENGTH);
            }
        }
    }

    /** The bearing of arm {@code i}: the bearing the painter draws it at and its victim is thrown along. */
    private static Vec3 armDirection(int i, int arms, float baseYaw) {
        double angle = baseYaw + i * Math.PI * 2.0D / arms;
        return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    /**
     * The star opening through the windup: a mote glittering up off each arm now and then, out to as
     * far as the painter's crack has reached, so the pattern the pack will be broken along is legible
     * on the ground before anything is thrown.
     */
    private static void openStar(ServerLevel level, MagicSkillDefinition definition, Vec3 centre, int arms, float baseYaw, int t) {
        double open = t / (double) WINDUP;
        // the painter's arms reach (0.15 + 0.85 * open) of a quad that is itself radius * open wide
        double reach = Math.max(ARM_GLITTER_MIN, RADIUS * open * (0.15D + 0.85D * open));
        for (int i = 0; i < arms; i++) {
            Vec3 along = armDirection(i, arms, baseYaw);
            SpatialMatter.seam(level, definition, centre.add(along.scale(ARM_GLITTER_MIN * 0.5D)), centre.add(along.scale(reach)), 1, 0.03D, true);
        }
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            Vec3 centre = entity.position();
            CompoundTag data = entity.serverData();
            int t = entity.tickCount;
            if (t == 1) {
                List<LivingEntity> inside = SkillTargets.hostilesWithin(level, owner, centre, RADIUS);
                int arms = Math.max(MIN_ARMS, Math.min(MAX_ARMS, inside.size()));
                float baseYaw = (entity.seed() / 64.0F) * (float) (Math.PI * 2.0D);
                ListTag victims = new ListTag();
                for (int i = 0; i < Math.min(inside.size(), MAX_ARMS); i++) {
                    CompoundTag v = new CompoundTag();
                    v.putUUID("Id", inside.get(i).getUUID());
                    v.putInt("Arm", i);
                    victims.add(v);
                }
                data.put("Victims", victims);
                data.putInt("Arms", arms);
                data.putFloat("Yaw", baseYaw);
                CompoundTag synced = new CompoundTag();
                synced.putInt("Arms", arms);
                synced.putFloat("Yaw", baseYaw);
                synced.put("Exits", new ListTag());
                entity.setSyncedData(synced);
                return;
            }
            if (t < WINDUP) {
                if (t % ARM_GLITTER_INTERVAL == 0 && data.contains("Arms")) {
                    openStar(level, entity.definition(), centre, data.getInt("Arms"), data.getFloat("Yaw"), t);
                }
                return;
            }
            if (t == WINDUP) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                tearOpen(level, entity.definition(), centre, data.getInt("Arms"), data.getFloat("Yaw"));
                // a size down: at 2.0 the strike's flash stood over the crack as a white dome three
                // blocks high, hiding the thing it was meant to open
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 1.0F);
                return;
            }
            int step = t - WINDUP;
            if (step % THROW_SPACING != 0) {
                return;
            }
            int index = step / THROW_SPACING - 1;
            ListTag victims = data.getList("Victims", Tag.TAG_COMPOUND);
            if (index < 0 || index >= victims.size()) {
                return;
            }
            CompoundTag v = victims.getCompound(index);
            Entity e = level.getEntity(v.getUUID("Id"));
            if (!(e instanceof LivingEntity victim) || !victim.isAlive() || victim.distanceToSqr(centre) > (RADIUS + 2.0D) * (RADIUS + 2.0D)) {
                return;
            }
            int arms = data.getInt("Arms");
            double angle = data.getFloat("Yaw") + v.getInt("Arm") * Math.PI * 2.0D / arms;
            Vec3 exit = findExit(level, centre, angle, victim);
            if (exit == null) {
                return;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(-(exit.x - centre.x), exit.z - centre.z));
            SpatialMatter.vacated(level, entity.definition(), victim.position(), victim.getBbWidth(), victim.getBbHeight());
            // the throw, as a path: a thread of motes from where the body stood out along its arm to
            // where it lands, stretching toward the exit, so a pack broken in two ticks reads as
            // bodies sent somewhere rather than bodies that vanished
            Vec3 lift = new Vec3(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
            // (an arm is dealt by index, not by where its body stands, so a path can run straight
            // back past the caster: the motes that would sit on their lens are left out)
            SpatialMatter.thread(level, entity.definition(), victim.position().add(lift), exit.add(lift), FLING_SPACING, FLING_MOTES_MAX, FLING_DRIFT,
                    owner != null ? owner.getEyePosition() : null);
            SafeSpotSearch.place(victim, exit, yaw, 0.0F, false);
            SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition(), true);
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            if (victim instanceof Mob mob) {
                mob.setTarget(null);
            }
            SkillTargets.shove(victim, centre, 0.3D, 0.1D);
            // open the exit portal for the painter
            CompoundTag synced = entity.syncedData().copy();
            ListTag exits = synced.getList("Exits", Tag.TAG_COMPOUND);
            CompoundTag p = new CompoundTag();
            p.putDouble("X", exit.x);
            p.putDouble("Y", exit.y);
            p.putDouble("Z", exit.z);
            p.putInt("T", t);
            exits.add(p);
            synced.put("Exits", exits);
            entity.setSyncedData(synced);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.SCATTER).frame(16, CircleScript.FrameStyle.NESTED).band(GlyphKind.DASHED_RING, 12).band(GlyphKind.TICK_BAND, 48, ColorRole.DIM).band(GlyphKind.RUNE_BAND, 24).stamps(StampId.ARROW, 12).spokes(8, 0.3F, true).orbit(7, 0.86F, 4).core(CoreKind.CROSS).stack(3, 0.6F).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("diaspora", 8.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.IRIS_CLOSE)
                .budget(3)
                .bounds(22.0F, 4.0F, 6.0F);
    }
}
