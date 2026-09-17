package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPrice;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodPrices;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.passive.BloodHarvestRules;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * BLOOD T-1 - bleed on purpose.
 *
 * <p>Hold to open your own vein. Every pulse costs a heart - an open wound, as any payment in
 * health is - and the blood it buys pours out ahead of you into a battery: blood set down to be
 * spent, by Coagulate, the Spear or a Vein Walk landing. It is the one way to make blood where
 * there is none, at the rate the school charges for it, so it is the setup tool and never a free
 * one. It will not take the last of you: the floor that refuses every blood payment ends the rite.
 *
 * <p>Held rather than charged, on the hold path two shipped skills already use, so the loadout-swap
 * safety they depend on comes along with it.
 */
public final class BloodRiteSkill implements SkillModule {

    /** Ticks between hearts at one point of Flow. */
    public static final int BASE_PULSE = 20;

    /** Longest a rite can be held; the floor and the wound usually end it sooner. */
    public static final int MAX_HOLD = 200;

    /** How far ahead the blood pools at one point of Reach, in blocks. */
    public static final double BASE_REACH = 4.0D;

    /** Ticks after the cast before the key has to be seen held, so the hold packet may arrive late. */
    private static final int HOLD_GRACE = 5;

    private static final int MIN_PULSE = 4;
    private static final double POOL_DROP = 4.0D;
    private static final String POOL_KEY = "pool";
    private static final String BLOOD_KEY = "blood";
    private static final String LIFE_KEY = "life";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.BLOOD_RITE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                // Refused up front if even one heart cannot be spared, so the key does nothing
                // rather than half a thing.
                if (!MagicPrice.waived(player)
                        && player.getHealth() - 1.0F < BloodService.MIN_HEALTH_AFTER_PAYMENT) {
                    player.displayClientMessage(Component.translatable("message.magical.not_enough_blood"), true);
                    return CastResult.FAILED;
                }
                SpellEffectEntity rite = SpellEffectEntity.spawn(ctx, player.position().add(0.0D, 1.0D, 0.0D),
                        MAX_HOLD + 10, 0.8F, ctx.look());
                rite.setExtra(ctx.slot());
                rite.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                CompoundTag scratch = rite.serverData();
                // Thrift runs the other way here: the cheaper the school's prices, the more blood a
                // heart pours. It is the base price divided by the factor, not multiplied.
                scratch.putInt(BLOOD_KEY, Math.max(1, Math.round(BloodPrices.base(definition().id()) / ctx.stats().costScale())));
                scratch.putInt(LIFE_KEY, Math.max(BloodHarvestRules.POOL_LIFETIME / 3, ctx.duration()));
                scratch.putDouble("reach", BASE_REACH * ctx.size());
                return CastResult.SUCCESS;
            }

            @Override
            public boolean holdable() {
                return true;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return new TuningView(false, true, true, true, true, null,
                        "screen.magical.tuning.flow", "screen.magical.tuning.reach",
                        "screen.magical.tuning.linger", "screen.magical.tuning.thrift");
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
            public void tick(SpellEffectEntity rite) {
                if (!(rite.owner() instanceof ServerPlayer player) || !player.isAlive()) {
                    stop(rite);
                    rite.finish();
                    return;
                }
                rite.setPos(player.getX(), player.getY() + 1.0D, player.getZ());
                boolean released = rite.tickCount > HOLD_GRACE && !HoldService.isHeld(player, rite.extra());
                if (rite.tickCount >= MAX_HOLD || released) {
                    stop(rite);
                    rite.finish();
                    return;
                }
                int pulse = Math.max(MIN_PULSE, Math.round(BASE_PULSE / Math.max(0.35F, rite.speed())));
                if (rite.tickCount % pulse != 1) {
                    return;
                }
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                // One heart, at the rate the school charges; refused, and the rite over, at the floor.
                if (!BloodService.payInHealthOnly(player, state, BloodService.COST_PER_HEALTH)) {
                    stop(rite);
                    rite.finish();
                    return;
                }
                CompoundTag scratch = rite.serverData();
                pool(rite, player).feed(scratch.getInt(BLOOD_KEY), player, MAX_HOLD);
                state.sync(player);
            }

            @Override
            public void onExpire(SpellEffectEntity rite) {
                stop(rite);
            }
        };
    }

    /** The battery this rite pours into, set down ahead of the caster at the first pulse. */
    private static BloodHarvestEntity pool(SpellEffectEntity rite, ServerPlayer player) {
        ServerLevel level = rite.serverLevel();
        CompoundTag scratch = rite.serverData();
        Entity existing = scratch.contains(POOL_KEY) ? level.getEntity(scratch.getInt(POOL_KEY)) : null;
        if (existing instanceof BloodHarvestEntity pool && pool.isPooled()) {
            return pool;
        }
        Vec3 look = player.getLookAngle();
        Vec3 ahead = new Vec3(look.x, 0.0D, look.z);
        ahead = ahead.lengthSqr() > 1.0E-4D ? ahead.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 at = player.position().add(ahead.scale(scratch.getDouble("reach"))).add(0.0D, 0.5D, 0.0D);
        BlockHitResult floor = level.clip(new ClipContext(at, at.add(0.0D, -POOL_DROP, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rite));
        Vec3 rest = floor.getType() == HitResult.Type.BLOCK ? floor.getLocation() : player.position();
        BloodHarvestEntity fresh = BloodHarvestEntity.spawn(level, player, new Vec3(rest.x, rest.y + 0.05D, rest.z),
                BloodHarvestRules.KIND_BATTERY, 0, scratch.getInt(LIFE_KEY));
        scratch.putInt(POOL_KEY, fresh.getId());
        return fresh;
    }

    private static void stop(SpellEffectEntity rite) {
        CompoundTag scratch = rite.serverData();
        if (scratch.contains(POOL_KEY)
                && rite.serverLevel().getEntity(scratch.getInt(POOL_KEY)) instanceof BloodHarvestEntity pool) {
            pool.endFeed();
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.HEART).frame(9)
                        .band(GlyphKind.BRAID_BAND, 12, ColorRole.HOT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.DROP, 9).core(CoreKind.DISC_GLOW).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.GROUND)
                // The blood is the pool entity's to draw: the drops fall out of the caster into it.
                .silhouette(Silhouette.swarm(Silhouette.Form.RING, FxKinds.Smoke.DROPLET, 14, 0.9F).withRole(ColorRole.HOT))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.HEARTBEAT)
                .holdable(true)
                .budget(1)
                .bounds(2.5F, 2.5F, 1.5F);
    }
}
