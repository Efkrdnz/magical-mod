package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
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
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * WARSMITH - HANG_THEN_DROP / AIMED_POINT / GROUND_SLAM. A forge anvil the size of a cart hangs in
 * the air over the aim point for a beat, then falls. Whatever the slam hurts is paid back to the
 * smith as barrier, so the Warsmith armours themselves by hitting things - which is the only apex on
 * the Blacksmith tree that grants an active skill at all.
 */
public final class AnvilFallSkill implements SkillModule {
    private static final int HANG = 14;
    private static final double DROP_HEIGHT = 7.0D;
    /** Share of the damage dealt that comes back to the caster as barrier. */
    private static final float BARRIER_SHARE = 0.35F;
    private static final int BARRIER_CAP = 24;
    /** Ticks between the slag drops that fall from the hanging anvil onto its mark. */
    private static final int SLAG_INTERVAL = 2;
    /** Points round the landing's reach where the floor heaves up. */
    private static final int CRACK_POINTS = 12;
    /**
     * Crumbs thrown up at each of those points. A cluster, not one: fourteen single crumbs on a
     * ring twenty blocks round were a crumb every block and a half, which read as litter on the
     * floor rather than as the ground giving way.
     */
    private static final int CRACK_CRUMBS = 4;
    /** Crumbs heaved straight up out of the dent the anvil punches in the floor. */
    private static final int HEAVE_CRUMBS = 18;
    /** How far under the aim point to look for the floor the landing is drawn on. */
    private static final int FLOOR_SEARCH = 4;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ANVIL_FALL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 ground = ctx.aim().point();
                Vec3 top = ground.add(0.0D, DROP_HEIGHT, 0.0D);
                SpellEffectEntity anvil = SpellEffectEntity.spawn(ctx, top, HANG + 12, ctx.size(), new Vec3(0.0D, -1.0D, 0.0D));
                CompoundTag data = anvil.serverData();
                data.putDouble("GX", ground.x);
                data.putDouble("GY", ground.y);
                data.putDouble("GZ", ground.z);
                // no release here: the casting service releases every successful cast itself, and a
                // second one stacked two muzzle flashes, two sprays and two recoils on the hand
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 22.0D;
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
                CompoundTag data = entity.serverData();
                Vec3 ground = new Vec3(data.getDouble("GX"), data.getDouble("GY"), data.getDouble("GZ"));
                if (entity.tickCount <= HANG) {
                    // Hanging: a slight rise so the drop reads as a release rather than a spawn.
                    entity.setPos(ground.add(0.0D, DROP_HEIGHT + entity.tickCount * 0.02D, 0.0D));
                    entity.setValue(entity.tickCount / (float) HANG);
                    if (entity.tickCount % SLAG_INTERVAL == 0) {
                        drip(entity);
                    }
                    return;
                }
                float fall = Math.min(1.0F, (entity.tickCount - HANG) / 10.0F);
                entity.setPos(ground.add(0.0D, DROP_HEIGHT * (1.0F - fall * fall), 0.0D));
                entity.setValue(1.0F);
                if (fall >= 1.0F) {
                    entity.finish();
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                CompoundTag data = entity.serverData();
                Vec3 ground = new Vec3(data.getDouble("GX"), data.getDouble("GY"), data.getDouble("GZ"));
                float dealt = 0.0F;
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), ground, entity.radius() * 1.6D)) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    SkillTargets.shove(victim, ground, 0.9D * Math.max(0.3D, entity.knockback()), 0.45D);
                    dealt += entity.damage();
                }
                // The forge takes its cut back: every blow the anvil lands plates the smith.
                if (dealt > 0.0F && entity.owner() instanceof ServerPlayer smith) {
                    PlayerMagicState state = smith.getData(MagicalAttachments.MAGIC_STATE);
                    state.addBarrier(Math.min(BARRIER_CAP, Math.round(dealt * BARRIER_SHARE)));
                    state.sync(smith);
                }
                // The aim point is usually a body's middle, so the hit is drawn on the floor under
                // it: the crack, the crumbs and the blast belong to the ground the anvil broke, not
                // to a disc hung in the air through the victim's chest.
                Vec3 floor = SpellFx.groundBelow(level, ground, FLOOR_SEARCH);
                crack(level, floor, entity.radius() * 1.6D);
                SpellFx.impact(level, entity.definition(), floor, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 2.0F);
            }
        };
    }

    /**
     * Slag dripping off the hot iron while it hangs: vanilla's falling lava, one drop every other
     * tick from under the anvil, so the spot it is about to land on is marked by what lands there
     * first. It takes the drops about as long to fall as the anvil waits.
     */
    private static void drip(SpellEffectEntity anvil) {
        anvil.serverLevel().sendParticles(ParticleTypes.FALLING_LAVA, anvil.getX(), anvil.getY() - 0.8D, anvil.getZ(),
                1, 0.45D, 0.0D, 0.45D, 0.0D);
    }

    /**
     * The ground giving way, the way vanilla's mace smash draws it: a crown of the struck block
     * heaved up out of the dent under the iron, and a ring of it thrown up where the slam stops
     * hurting. The ring is the reach, so it is what a player reads the hit's size from, and it is
     * real dirt and stone rather than more light. A slam is the one beat this skill has, so it
     * spends more crumbs than a beat usually does - about seventy, one packet a cluster.
     */
    private static void crack(ServerLevel level, Vec3 floor, double reach) {
        BlockState struck = level.getBlockState(BlockPos.containing(floor.x, floor.y - 0.5D, floor.z));
        if (struck.isAir()) {
            return;
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.DUST_PILLAR, struck);
        // the pillar keeps its own upward kick and scatter, so a small spread is all it is handed
        level.sendParticles(dust, floor.x, floor.y, floor.z, HEAVE_CRUMBS, 0.45D, 0.0D, 0.45D, 0.2D);
        double turn = level.random.nextDouble() * Math.PI * 2.0D / CRACK_POINTS;
        for (int i = 0; i < CRACK_POINTS; i++) {
            double a = turn + i * Math.PI * 2.0D / CRACK_POINTS;
            level.sendParticles(dust, floor.x + Math.cos(a) * reach, floor.y, floor.z + Math.sin(a) * reach,
                    CRACK_CRUMBS, 0.3D, 0.0D, 0.3D, 0.15D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(2)
                // struck hot iron: hammer sparks, slag and forge smoke rather than a fireball's flame
                .accent(Accent.FORGE)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.ANCHOR).frame(13).band(GlyphKind.BRAID_BAND, 24).band(GlyphKind.SOLID_RING, 6).stamps(StampId.BAR, 8).core(CoreKind.EMBER_PIT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.body(Silhouette.Form.SLAB, FxKinds.Body.METAL_BANDS, 3, 1.1F, 0.7F))
                // a glint of heat round the iron; the sparks themselves are the trail's and the landing's
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.SPARK_STREAK, 3, 0.9F).withOpacity(0.7F))
                // FUNNEL, not SLAM: a SLAM release lays its slam flash on the floor under the caster's
                // hand, and this slam lands at the mark up to 22 blocks away - in first person it was a
                // white floor across the lower half of the view. The circle on the mark closes instead.
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.SHOCK_RING)
                .bounds(3.0F, 8.0F, 1.4F);
    }
}
