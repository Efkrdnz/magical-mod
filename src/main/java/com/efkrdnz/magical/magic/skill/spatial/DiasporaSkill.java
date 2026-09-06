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
                return;
            }
            if (t == WINDUP) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.0F);
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
