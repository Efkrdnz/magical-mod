package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.RollingBodyEntity;
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
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SPELL CREATOR (slag_roller + puppet_sigil) - RIDE / SELF_MOUNT / STEERED_ROLLING_WHEEL. A molten
 * spoked wheel the caster mounts and steers by looking; everything it rolls over is burned and
 * bowled aside. Pressing the slot again or sneaking dismounts, leaving an ember ring.
 */
public final class CinderChariotSkill implements SkillModule {
    /** Slower than this the wheel is only turning over, not grinding. */
    private static final double GRIND_SPEED = 0.2D;
    private static final int GRIND_INTERVAL = 3;
    private static final int GRIND_CRUMBS = 2;
    /** Flames in the dismount ring, one every eighteen degrees. */
    private static final int RING_FLAMES = 20;
    private static final int RING_SLAG = 4;
    /** Flames fanned out of a body the wheel bowls aside, and how fast: 0.18 carries one about three blocks. */
    private static final int BOWL_FLAMES = 7;
    private static final double BOWL_FLAME_SPEED = 0.18D;
    private static final int BOWL_SMOKE = 2;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CINDER_CHARIOT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                List<RollingBodyEntity> riding = ctx.level().getEntitiesOfClass(RollingBodyEntity.class, ctx.caster().getBoundingBox().inflate(4.0D), w -> w.isVehicleMode() && ctx.caster().getUUID().equals(w.ownerUuid()));
                if (!riding.isEmpty()) {
                    riding.get(0).finish();
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                Vec3 look = ctx.look();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
                Vec3 start = ctx.feet().add(0.0D, 0.2D, 0.0D);
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), start, Math.max(60, ctx.duration()), 1.1F, flat, (int) (ctx.seed() & 63));
                RollingBodyEntity wheel = RollingBodyEntity.create(ctx.level(), template, start, Math.max(1.4F, ctx.size()), flat.scale(0.2D), true);
                wheel.setPhysics(0.08F, 0.9F, 0.3F);
                ctx.level().addFreshEntity(wheel);
                ctx.caster().startRiding(wheel, true);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.NONE;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                if (!(entity instanceof RollingBodyEntity wheel)) {
                    return;
                }
                ServerLevel level = wheel.serverLevel();
                LivingEntity rider = wheel.getControllingPassenger();
                if (wheel.tickCount > 5 && (rider == null || rider.isShiftKeyDown())) {
                    wheel.finish();
                    return;
                }
                Vec3 v = wheel.getDeltaMovement();
                double speed = Math.sqrt(v.x * v.x + v.z * v.z);
                if (speed < 0.05D) {
                    return;
                }
                grind(level, wheel, speed);
                CompoundTag data = wheel.serverData();
                AABB tread = wheel.getBoundingBox().inflate(0.3D, 0.0D, 0.3D);
                for (LivingEntity hit : SkillTargets.hostilesIn(level, wheel.owner(), tread)) {
                    if (hit == rider) {
                        continue;
                    }
                    String key = "icd_" + hit.getId();
                    if (data.getInt(key) > wheel.tickCount) {
                        continue;
                    }
                    data.putInt(key, wheel.tickCount + 10);
                    // drawn by bowl() below: the generic heavy impact every ten ticks piled blasts and black smoke on the body
                    SkillTargets.hurt(level, wheel.owner(), hit, wheel.damage(), wheel.definition(), false);
                    hit.igniteForSeconds(3.0F);
                    Vec3 dir = wheel.direction();
                    Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                    double sign = hit.position().subtract(wheel.position()).dot(side) >= 0.0D ? 1.0D : -1.0D;
                    hit.setDeltaMovement(side.scale(sign * 0.7D * Math.max(0.5D, wheel.knockback())).add(0.0D, 0.3D, 0.0D));
                    hit.hurtMarked = true;
                    bowl(level, wheel, hit, side.scale(sign));
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                entity.ejectPassengers();
                Vec3 centre = entity.position();
                for (LivingEntity hit : SkillTargets.hostilesWithin(level, entity.owner(), centre, 3.0D)) {
                    SkillTargets.hurt(level, entity.owner(), hit, 4.0F, entity.definition(), false);
                    hit.igniteForSeconds(2.0F);
                }
                emberRing(level, entity, centre);
                SpellFx.decal(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), 3.0F);
            }
        };
    }

    /**
     * The wheel's rim biting the ground it rolls over: crumbs of that ground kicked up behind the
     * tread and now and then a spit of slag. The flame and smoke it trails come from its accent.
     */
    private static void grind(ServerLevel level, RollingBodyEntity wheel, double speed) {
        if (speed < GRIND_SPEED || wheel.tickCount % GRIND_INTERVAL != 0) {
            return;
        }
        Vec3 back = wheel.position().subtract(wheel.direction().scale(0.3D));
        BlockState ground = level.getBlockState(BlockPos.containing(back.x, back.y - 0.2D, back.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), back.x, back.y + 0.1D, back.z, GRIND_CRUMBS, 0.2D, 0.05D, 0.2D, 0.15D);
        }
        if (wheel.tickCount % (GRIND_INTERVAL * 3) == 0) {
            level.sendParticles(ParticleTypes.LAVA, back.x, back.y + 0.2D, back.z, 1, 0.15D, 0.0D, 0.15D, 0.0D);
        }
    }

    /**
     * A body the wheel runs down: a fan of flame and a spit of slag thrown out of the side it is
     * bowled from, the way it goes, and a breath of smoke off the burn - with the impact's sound and
     * both screens' jolt from {@link FusionHits}. The generic heavy impact went off here, and a wheel
     * grinds a body every ten ticks it stays in the tread, so its blast, its ring of puffs and its
     * rising black smoke stacked up on whatever the wheel was stuck against.
     */
    private static void bowl(ServerLevel level, RollingBodyEntity wheel, LivingEntity hit, Vec3 away) {
        FusionHits.land(level, wheel.definition(), hit, wheel.owner());
        // the face the rim struck, low on the body where the wheel meets it
        double half = hit.getBbWidth() * 0.5D;
        Vec3 face = hit.position().add(-away.x * half, Math.min(1.0D, hit.getBbHeight() * 0.4D), -away.z * half);
        Vec3 along = wheel.direction();
        for (int i = 0; i < BOWL_FLAMES; i++) {
            double lift = 0.3D + 0.5D * level.random.nextDouble();
            double drift = (level.random.nextDouble() - 0.5D) * 0.8D;
            Vec3 d = away.add(along.scale(drift)).add(0.0D, lift, 0.0D).normalize();
            level.sendParticles(ParticleTypes.FLAME, face.x, face.y, face.z, 0, d.x, d.y, d.z, BOWL_FLAME_SPEED);
        }
        level.sendParticles(ParticleTypes.LAVA, face.x, face.y, face.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        level.sendParticles(ParticleTypes.SMOKE, face.x, face.y + 0.2D, face.z, BOWL_SMOKE, 0.15D, 0.15D, 0.15D, 0.02D);
    }

    /**
     * The ember ring left where the rider steps off: flames running out along the ground to the
     * edge of the burn, a few pops of slag and a low ring of smoke. The generic impact used to go
     * off here, and "here" is the rider's own feet - its flash, blast and rising smoke filled their
     * whole view the moment they dismounted. Its sound is kept; the scorch decal still lands.
     */
    private static void emberRing(ServerLevel level, SpellEffectEntity entity, Vec3 centre) {
        ProfileCues.SoundCue cue = VisualProfiles.of(entity.definition()).sounds().impact();
        if (cue != null) {
            level.playSound(null, centre.x, centre.y, centre.z, cue.sound(), SoundSource.PLAYERS, cue.volume(), cue.pitch());
        }
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < RING_FLAMES; i++) {
            double a = offset + Math.PI * 2.0D * i / RING_FLAMES;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            // a flame keeps 0.96 of its speed a tick: 0.2 runs it out about three blocks, the burn's reach
            level.sendParticles(ParticleTypes.FLAME, centre.x + cos * 0.6D, centre.y + 0.15D, centre.z + sin * 0.6D, 0, cos, 0.08D, sin, 0.2D);
            if ((i & 1) == 0) {
                level.sendParticles(ParticleTypes.SMOKE, centre.x + cos * 0.5D, centre.y + 0.1D, centre.z + sin * 0.5D, 0, cos, 0.05D, sin, 0.12D);
            }
        }
        level.sendParticles(ParticleTypes.LAVA, centre.x, centre.y + 0.2D, centre.z, RING_SLAG, 1.2D, 0.0D, 1.2D, 0.0D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.WHEEL).frame(8).band(GlyphKind.FACET_BAND, 12).band(GlyphKind.BRAID_BAND, 6).band(GlyphKind.TOOTH_BAND, 24).stamps(StampId.SPIRAL, 6).spokes(12, 0.2F, false).orbit(5, 0.84F, 3).core(CoreKind.EMBER_PIT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("cinder_chariot", 1.1F))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.EMBER_CLUSTER, 3, 0.15F, 12, 0.2F, 0, 0.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.EMBER_FIELD, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.EMBER_DRIFT)
                .budget(2)
                .bounds(4.0F, 3.0F, 1.0F);
    }
}
