package com.efkrdnz.magical.magic.skill.eldritch;

import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.eldritch.EldritchPrices;
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
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ELDRITCH T-5 - hold to call. A great eye opens above you and, at every pulse, a tentacle erupts
 * under something hostile in reach and holds it.
 *
 * <p>Every pulse is paid again - the mana and the Notice - so the longest call is the loudest.
 * The eye is the controller (anchor OWNER, above the head); the grasps are Grasp of the Deep's
 * eruption as children, briefly, with the caller's stats.
 */
public final class CallOfTheDeepSkill implements SkillModule {
    public static final double BASE_REACH = 10.0D;
    public static final int BASE_PULSE = 30;
    public static final int HOLD_GRACE = 5;
    public static final double EYE_HEIGHT = 2.6D;
    public static final int GRASP_TICKS = 30;
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CALL_OF_THE_DEEP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                float potency = EldritchService.potency(ctx.state());
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity eye = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_EYE,
                        EldritchConstructEntity.ANCHOR_OWNER, ctx.feet().add(0.0D, EYE_HEIGHT, 0.0D), ctx.duration(),
                        (float) (BASE_REACH * ctx.size() * potency), 2.5F, ctx.look());
                eye.setExtra(ctx.slot());
                eye.serverData().putFloat(KEY_POTENCY, potency);
                eye.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 1.0F, 0.35F);
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
                return TuningView.DEFAULT.labels("screen.magical.tuning.wrath", "screen.magical.tuning.cadence",
                        "screen.magical.tuning.reach", "screen.magical.tuning.call", "screen.magical.tuning.thrift");
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity construct)) {
                    return;
                }
                if (EldritchConstructEntity.MODEL_TENTACLE.equals(construct.model())) {
                    // A child grasp: hold what it took for its short life, one crush in the middle.
                    LivingEntity held = construct.livingTarget();
                    if (held != null && held.isAlive() && construct.tickCount == GRASP_TICKS / 2) {
                        float potency = construct.serverData().contains(KEY_POTENCY) ? construct.serverData().getFloat(KEY_POTENCY) : 1.0F;
                        SkillTargets.hurt(construct.serverLevel(), construct.owner(), held, construct.damage() * potency, construct.skillId());
                    }
                    return;
                }
                if (!(construct.owner() instanceof ServerPlayer player) || !player.isAlive()) {
                    construct.finish();
                    return;
                }
                construct.setPos(player.getX(), player.getY() + EYE_HEIGHT, player.getZ());
                boolean released = construct.tickCount > HOLD_GRACE && !HoldService.isHeld(player, construct.extra());
                if (released) {
                    construct.finish();
                    return;
                }
                int pulse = Math.max(10, Math.round(BASE_PULSE / Math.max(0.35F, construct.speed())));
                if (construct.tickCount % pulse != 1 || construct.tickCount == 1) {
                    return;
                }
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                if (!EldritchService.manaWaived(state) && !MagicSinService.spendManaForSkill(player, state, construct.definition().baseManaCost())) {
                    construct.finish();
                    return;
                }
                EldritchService.notice(player, state, EldritchPrices.base(construct.skillId()));
                state.sync(player);
                List<LivingEntity> hostiles = SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), construct.radius());
                if (hostiles.isEmpty()) {
                    return;
                }
                LivingEntity chosen = hostiles.get(player.getRandom().nextInt(hostiles.size()));
                construct.setTarget(chosen);
                float potency = construct.serverData().contains(KEY_POTENCY) ? construct.serverData().getFloat(KEY_POTENCY) : 1.0F;
                GraspOfTheDeepSkill.grasp(player.serverLevel(), construct, null, GraspOfTheDeepSkill.beside(chosen, player.position()), chosen, GRASP_TICKS, potency);
            }

            // No clear on expiry, as in Grasp of the Deep: the hold is applied for the grasp's whole
            // life and lapses with it, so pulses that land on the same victim do not undo each other.
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.DEEP_CALL).frame(9)
                        .band(GlyphKind.RUNE_BAND, 18, ColorRole.BRIGHT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.RING, 9).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.SKY)
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.INK_BLOOM, 24, 1.6F).withRole(ColorRole.DIM))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.VORTEX_SPIRAL, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.HEARTBEAT)
                .holdable(true)
                .budget(3)
                .bounds(4.0F, 4.0F, 1.0F);
    }
}
