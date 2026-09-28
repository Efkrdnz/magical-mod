package com.efkrdnz.magical.magic.skill.fusion;

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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * MAGIC ORIGINATOR (crucible + cleansing_ray) - RADIATE_AND_FOCUS / AIM_SURFACE_PLACE /
 * SOLID_SUN_WITH_STEERED_BEAM. After a counterable windup a solid sun of molten glass drops onto the
 * ground and burns there: heat rings purge and ignite everything around it, allies are warmed, and
 * a focal beam the caster steers by looking melts what it touches.
 */
public final class FallenSunSkill implements SkillModule {
    private static final int WINDUP = 24;
    private static final byte MODE_BODY = 2;
    private static final double RING = 8.0D;
    private static final double BEAM_RANGE = 24.0D;
    private static final double BEAM_RADIUS = 0.6D;
    /** The radius of the sun's drawn body: a sphere sitting on the ground, its core one radius up. */
    private static final double SUN_RADIUS = 1.5D;
    /**
     * The landing's shockwave: flames run out along the ground from the sun's foot, one every fifteen
     * degrees. A flame keeps 0.96 of its speed a tick, so 0.35 carries one about five blocks, most of
     * the way to the heat ring, before it burns out.
     */
    private static final int SHOCK_FLAMES = 24;
    private static final double SHOCK_FLAME_SPEED = 0.35D;
    /** Crumbs of the ground kicked up round the foot as it lands, at so many points round it. */
    private static final int SHOCK_CRUMB_POINTS = 10;
    private static final int SHOCK_CRUMBS = 2;
    /** Beads of molten skin thrown off the sun as it lands. */
    private static final int LANDING_SLAG = 5;
    /**
     * The generic impact's scale at the landing and at the end. It was 2.4 and 2.0, the ceiling of
     * the matter layer's table: a heavy fire impact that size stands a column of black smoke and a
     * blast inside the sun it announces. The shockwave and the sun itself carry the landing.
     */
    private static final float LANDING_IMPACT_SCALE = 1.2F;
    private static final float END_IMPACT_SCALE = 1.0F;
    /** How long before the landing the ground under it starts to catch, and a flame every other tick. */
    private static final int HEAT_LEAD = 16;
    /** A body in the focal beam: flames licking off where the beam enters it, and a thread of smoke. */
    private static final int BURN_FLAMES = 5;
    private static final int BURN_SMOKE = 2;
    /** A bead of the sun's molten skin spat off every so many ticks while it burns. */
    private static final int SPIT_INTERVAL = 5;
    /** The focal beam's point melting what it rests on: a lick of flame and smoke every so many ticks. */
    private static final int MELT_INTERVAL = 3;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.FALLEN_SUN;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity controller = SpellEffectEntity.spawn(ctx, ctx.aim().point(), WINDUP + Math.max(60, ctx.duration()), (float) RING, new Vec3(0.0D, 1.0D, 0.0D));
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
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(5.0F, 20.0F);
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
                if ((entity.mode() & MODE_BODY) != 0) {
                    return; // the solid sun body has no logic of its own
                }
                ServerLevel level = entity.serverLevel();
                LivingEntity owner = entity.livingOwner();
                int t = entity.tickCount;
                if (t < WINDUP) {
                    if (t >= WINDUP - HEAT_LEAD && t % 2 == 0) {
                        heat(level, entity.position(), t);
                    }
                    return;
                }
                Vec3 centre = entity.position();
                if (t == WINDUP) {
                    SolidConstructEntity sun = SolidConstructEntity.create(level, entity, centre, 3.0F, 3.0F, 400.0F, 0);
                    sun.setMode(MODE_BODY);
                    sun.setLife(entity.life() - WINDUP);
                    level.addFreshEntity(sun);
                    entity.serverData().putUUID("Sun", sun.getUUID());
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, LANDING_IMPACT_SCALE);
                    landing(level, centre);
                    for (LivingEntity crushed : SkillTargets.hostilesWithin(level, owner, centre.add(0.0D, 1.5D, 0.0D), 2.2D)) {
                        // the landing's own impact and shockwave are already on this spot: a heavy impact on
                        // each crushed body too was a second blast, puff ring and smoke column inside the sun
                        SkillTargets.hurt(level, owner, crushed, entity.damage() * 2.0F, entity.definition(), false);
                        FusionHits.land(level, entity.definition(), crushed, owner);
                        SkillTargets.shove(crushed, centre, 1.0D, 0.4D);
                    }
                }
                Vec3 core = centre.add(0.0D, 1.5D, 0.0D);
                if (t % SPIT_INTERVAL == 0) {
                    spit(level, core);
                }
                if (t % 20 == 0) {
                    for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, core, RING)) {
                        SkillTargets.hurt(level, owner, hostile, 7.0F, entity.definition().id());
                        hostile.igniteForSeconds(3.0F);
                        List<MobEffectInstance> buffs = new ArrayList<>();
                        for (MobEffectInstance effect : hostile.getActiveEffects()) {
                            if (effect.getEffect().value().isBeneficial()) {
                                buffs.add(effect);
                            }
                        }
                        for (MobEffectInstance buff : buffs) {
                            hostile.removeEffect(buff.getEffect());
                        }
                    }
                    for (LivingEntity ally : SkillTargets.alliesWithin(level, owner, core, RING)) {
                        ally.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
                    }
                    SpellFx.zoneTickWithin(level, entity.definition(), centre, RING);
                }
                // the focal beam follows the caster's aim
                if (owner == null || !owner.isAlive()) {
                    return;
                }
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), 40.0D, 0.0D, false, 0, null);
                Vec3 target = aim.point();
                Vec3 dir = target.subtract(core);
                double len = Math.min(BEAM_RANGE, dir.length());
                if (len < 1.0E-3D) {
                    return;
                }
                dir = dir.normalize();
                Vec3 end = core.add(dir.scale(len));
                CompoundTag synced = new CompoundTag();
                synced.putDouble("BX", end.x);
                synced.putDouble("BY", end.y);
                synced.putDouble("BZ", end.z);
                entity.setSyncedData(synced);
                if (t % MELT_INTERVAL == 0 && aim.hitBlock() && end.distanceToSqr(target) < 0.25D) {
                    melt(level, end, aim.normal(), t);
                }
                if (t % 10 == 0) {
                    for (LivingEntity hostile : SkillTargets.hostilesIn(level, owner, new AABB(core, end).inflate(BEAM_RADIUS + 0.5D))) {
                        Vec3 p = hostile.getBoundingBox().getCenter();
                        double along = p.subtract(core).dot(dir);
                        if (along < 0.0D || along > len || p.distanceTo(core.add(dir.scale(along))) > BEAM_RADIUS + hostile.getBbWidth() * 0.5D) {
                            continue;
                        }
                        // drawn by scorch() below: a heavy generic impact every ten ticks stacked blasts and black smoke on the body
                        SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition(), false);
                        hostile.igniteForSeconds(2.0F);
                        FusionHits.land(level, entity.definition(), hostile, owner);
                        scorch(level, hostile, dir);
                    }
                    if (aim.hitBlock()) {
                        SpellFx.decal(level, entity.definition(), end, aim.normal(), 1.2F);
                    }
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if ((entity.mode() & MODE_BODY) != 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                if (entity.serverData().hasUUID("Sun") && level.getEntity(entity.serverData().getUUID("Sun")) instanceof SolidConstructEntity sun) {
                    sun.finish();
                }
                SpellFx.impact(level, entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), END_IMPACT_SCALE);
            }
        };
    }

    /**
     * The ground under the falling sun catching before it lands: flames licking up out of the patch
     * it will cover, a few more each beat, so the windup says where and when without a flash. The
     * cast circle is the telegraph's light; this is the heat under it.
     */
    private static void heat(ServerLevel level, Vec3 centre, int t) {
        int flames = 1 + (t - (WINDUP - HEAT_LEAD)) / 6;
        for (int i = 0; i < flames; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double r = SUN_RADIUS * Math.sqrt(level.random.nextDouble());
            level.sendParticles(ParticleTypes.FLAME, centre.x + Math.cos(a) * r, centre.y + 0.1D, centre.z + Math.sin(a) * r,
                    0, 0.0D, 1.0D, 0.0D, 0.04D + 0.04D * level.random.nextDouble());
        }
    }

    /**
     * The sun landing: a ring of flame thrown out along the ground from its foot toward the heat
     * ring, crumbs of the ground kicked up round it and a few beads of its skin spat off. It used to
     * be a skirt of six of vanilla's blast sprites, each two and a half blocks across, on top of the
     * heavy impact's own blast: a wall of grey puffs round the sun that hid the sun.
     */
    private static void landing(ServerLevel level, Vec3 centre) {
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        double foot = SUN_RADIUS * 1.1D;
        for (int i = 0; i < SHOCK_FLAMES; i++) {
            double a = offset + Math.PI * 2.0D * i / SHOCK_FLAMES;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            level.sendParticles(ParticleTypes.FLAME, centre.x + cos * foot, centre.y + 0.15D, centre.z + sin * foot,
                    0, cos, 0.03D, sin, SHOCK_FLAME_SPEED);
        }
        BlockState ground = level.getBlockState(BlockPos.containing(centre.x, centre.y - 0.5D, centre.z));
        if (!ground.isAir()) {
            BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < SHOCK_CRUMB_POINTS; i++) {
                double a = offset + Math.PI * 2.0D * (i + 0.5D) / SHOCK_CRUMB_POINTS;
                level.sendParticles(crumbs, centre.x + Math.cos(a) * foot, centre.y + 0.2D, centre.z + Math.sin(a) * foot,
                        SHOCK_CRUMBS, 0.2D, 0.05D, 0.2D, 0.15D);
            }
        }
        level.sendParticles(ParticleTypes.LAVA, centre.x, centre.y + SUN_RADIUS, centre.z, LANDING_SLAG,
                SUN_RADIUS * 0.5D, 0.4D, SUN_RADIUS * 0.5D, 0.0D);
    }

    /**
     * A body standing in the focal beam, burning where the beam enters it: a lick of flame off that
     * side and a thread of smoke. The heat ring's damage and the beam's own light are the rest.
     */
    private static void scorch(ServerLevel level, LivingEntity hostile, Vec3 dir) {
        Vec3 at = hostile.getBoundingBox().getCenter().subtract(dir.scale(hostile.getBbWidth() * 0.5D));
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, BURN_FLAMES, 0.15D, hostile.getBbHeight() * 0.25D, 0.15D, 0.02D);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3D, at.z, BURN_SMOKE, 0.1D, 0.1D, 0.1D, 0.02D);
    }

    /**
     * A bead of molten skin spat off the sun's upper half: vanilla lava, which arcs, pops and
     * trails its own smoke. The body and its glow are the light; this is the only thing it sheds.
     */
    private static void spit(ServerLevel level, Vec3 core) {
        double a = level.random.nextDouble() * Math.PI * 2.0D;
        double up = level.random.nextDouble();
        double across = Math.sqrt(1.0D - up * up) * SUN_RADIUS;
        level.sendParticles(ParticleTypes.LAVA, core.x + Math.cos(a) * across, core.y + up * SUN_RADIUS, core.z + Math.sin(a) * across, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    /**
     * Where the focal beam rests on a block, the block melting under it: a lick of flame and a
     * thread of smoke off the face, now and then a bead of lava. The decal every half second is the
     * scorch it leaves; this is the burning while it is there.
     */
    private static void melt(ServerLevel level, Vec3 end, Vec3 normal, int t) {
        Vec3 p = end.add(normal.scale(0.1D));
        level.sendParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 2, 0.15D, 0.1D, 0.15D, 0.02D);
        level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0.1D, 0.05D, 0.1D, 0.02D);
        if (t % (MELT_INTERVAL * 4) == 0) {
            level.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.1D, 0.0D, 0.1D, 0.0D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.SUN).frame(16).band(GlyphKind.PETAL_BAND, 24, ColorRole.BASE).band(GlyphKind.RUNE_BAND, 24, ColorRole.HOT).band(GlyphKind.TOOTH_BAND, 36).stamps(StampId.STAR4, 12).spokes(12, 0.3F, true).orbit(7, 0.86F, 4).core(CoreKind.SUNBURST).stack(3, 0.6F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .throughTerrain(true)
                .silhouette(Silhouette.body(Silhouette.Form.SPHERE, FxKinds.Body.GOLD, 16, 1.5F).forModes(1))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.HEX_LENS, 2.6F, 6, 8).withOffset(1.5F).withOpacity(0.7F).forModes(1))
                .silhouette(Silhouette.custom("fallen_sun", 1.5F).forModes(0))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.EMBER_FIELD, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.BLOOM_RAYS)
                .budget(3)
                .bounds(26.0F, 6.0F, 2.0F);
    }
}
