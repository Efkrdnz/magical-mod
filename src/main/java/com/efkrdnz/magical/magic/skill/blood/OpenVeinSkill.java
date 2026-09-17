package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * BLOOD T-1 - make blood out of them.
 *
 * <p>The harvest engine of the school. A vein opened in something living bleeds at every bite for
 * Linger ticks, and what it sheds pools at its feet - a harvest pool, so it comes to the caster
 * when the feed is over and they are near. A victim that runs starts a new pool every couple of
 * blocks, so it leaves a trail: Bloodscent's name made literal, and a ladder for Vein Walk. The
 * bleeding thing is lit through walls for as long as it bleeds, and is where a Vein Walk lands
 * first.
 *
 * <p>The shed is per bite, not per victim or per tick, so a crowd is not worth more than the
 * whole rest of the school and a long fight is worth exactly its bites.
 */
public final class OpenVeinSkill implements SkillModule {

    /** How far a vein can be opened at one point of Reach, in blocks. */
    public static final double BASE_REACH = 20.0D;

    public static final int BITE_INTERVAL = 10;

    /** Blood the wound sheds into the pool at each bite. */
    public static final int SHED_PER_BITE = 4;

    /** How much more it sheds when Bloodscent had already lit the victim. */
    public static final float BLOODSCENT_SHED_SCALE = 1.5F;

    /** How far the victim may move from its last pool before its blood starts a new one: the trail. */
    public static final double TRAIL_STEP = 2.0D;

    private static final double AIM_TOLERANCE = 1.0D;
    private static final double POOL_LIFT = 0.05D;
    private static final int EXTRA_BLOODSCENT = 1;
    private static final String POOL_KEY = "pool";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.OPEN_VEIN;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                // Aimed again here rather than through the dispatcher, because Reach is a tuned
                // number and the dispatcher's range is a constant.
                double reach = BASE_REACH * ctx.size();
                AimResolver.Result aim = AimResolver.resolve(ctx.level(), player, ctx.look(), reach,
                        AIM_TOLERANCE, false, 8, null);
                LivingEntity victim = aim.living();
                if (victim == null || !SkillTargets.isHostile(player, victim)) {
                    player.displayClientMessage(Component.translatable("message.magical.no_vein_target"), true);
                    return CastResult.FAILED;
                }
                // Bloodscent's mark is read before this one goes on, or the bonus would pay itself.
                boolean lit = MagicStatusService.has(victim, MagicStatus.REVEALED)
                        && ClassPassiveEffects.on(ctx.state(), MagicPassiveContent.BLOODSCENT.id());
                if (!BloodService.pay(player, ctx.state(), BloodService.cost(ctx.stats()))) {
                    return CastResult.FAILED;
                }
                int linger = Math.max(BITE_INTERVAL, ctx.duration());
                SpellEffectEntity wound = SpellEffectEntity.spawn(ctx, BloodHarvestEntity.chestOf(victim),
                        linger, 0.6F, ctx.look());
                wound.setTarget(victim);
                wound.setExtra(lit ? EXTRA_BLOODSCENT : 0);
                wound.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                MagicStatusService.apply(victim, MagicStatus.REVEALED, linger, definition().id(), player);
                ctx.level().playSound(null, victim.blockPosition(), SoundEvents.HONEY_BLOCK_BREAK,
                        SoundSource.PLAYERS, 0.7F, 0.6F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return BASE_REACH;
            }

            @Override
            public double aimTolerance() {
                return AIM_TOLERANCE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED.labels("screen.magical.tuning.bite", null,
                        "screen.magical.tuning.reach", "screen.magical.tuning.linger",
                        "screen.magical.tuning.thrift");
            }

            @Override
            public MobCastProfile mob() {
                // A mob has no Vessel to bleed anything into.
                return MobCastProfile.NONE;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity wound) {
                ServerLevel level = wound.serverLevel();
                if (!(wound.target() instanceof LivingEntity victim) || !victim.isAlive()
                        || !(wound.owner() instanceof ServerPlayer player) || !player.isAlive()) {
                    stopFeeding(wound);
                    wound.finish();
                    return;
                }
                wound.setPos(BloodHarvestEntity.chestOf(victim));
                if (wound.tickCount % BITE_INTERVAL != 0) {
                    return;
                }
                SkillTargets.hurt(level, player, victim, wound.damage() * BloodService.potency(player),
                        wound.definition().id());
                int shed = wound.extra() == EXTRA_BLOODSCENT
                        ? Math.round(SHED_PER_BITE * BLOODSCENT_SHED_SCALE) : SHED_PER_BITE;
                pool(wound, level, player, victim).feed(shed, victim, wound.life() - wound.tickCount);
            }

            @Override
            public void onExpire(SpellEffectEntity wound) {
                stopFeeding(wound);
            }
        };
    }

    /** The pool this wound bleeds into: the last one, unless the victim has walked away from it. */
    private static BloodHarvestEntity pool(SpellEffectEntity wound, ServerLevel level, ServerPlayer owner,
            LivingEntity victim) {
        CompoundTag scratch = wound.serverData();
        Entity existing = scratch.contains(POOL_KEY) ? level.getEntity(scratch.getInt(POOL_KEY)) : null;
        if (existing instanceof BloodHarvestEntity pool && pool.isPooled()
                && pool.position().distanceTo(victim.position()) <= TRAIL_STEP) {
            return pool;
        }
        if (existing instanceof BloodHarvestEntity left) {
            left.endFeed();
        }
        BloodHarvestEntity fresh = BloodHarvestEntity.spawn(level, owner,
                victim.position().add(0.0D, POOL_LIFT, 0.0D), 0);
        scratch.putInt(POOL_KEY, fresh.getId());
        return fresh;
    }

    private static void stopFeeding(SpellEffectEntity wound) {
        CompoundTag scratch = wound.serverData();
        if (scratch.contains(POOL_KEY)
                && wound.serverLevel().getEntity(scratch.getInt(POOL_KEY)) instanceof BloodHarvestEntity pool) {
            pool.endFeed();
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.TREE).frame(6)
                        .band(GlyphKind.WAVE_BAND, 14, ColorRole.HOT)
                        .band(GlyphKind.TICK_BAND, 20, ColorRole.DIM)
                        .stamps(StampId.TOOTH, 12).core(CoreKind.RIPPLE, ColorRole.INK).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.TARGET_FOLLOW)
                // The blood is the pool entity's to draw; the wound itself is only a few drops.
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.DROPLET, 10, 0.5F).withRole(ColorRole.HOT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SPIRAL_DRAIN, FxKinds.Smoke.DROPLET, FxKinds.Overlay.INK_BLEED)
                .budget(1)
                .bounds(2.0F, 2.5F, 2.0F);
    }
}
