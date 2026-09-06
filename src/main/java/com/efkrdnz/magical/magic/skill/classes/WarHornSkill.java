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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * WARCALLER - SHOUT / FORWARD_CONE / PUSH_AND_HARDEN. One blast that does two jobs at once: it
 * throws back everything in front of the caller and hardens everything behind them. A Warcaller is
 * only worth having when there is somebody to call to, which is the whole point of the branch.
 * Sneak = a narrower, longer blast that reaches further down a corridor.
 */
public final class WarHornSkill implements SkillModule {
    private static final int WINDUP = 8;
    private static final double RANGE = 14.0D;
    private static final double LONG_RANGE = 22.0D;
    private static final double CONE_COS = Math.cos(Math.toRadians(45.0D));
    private static final double NARROW_COS = Math.cos(Math.toRadians(24.0D));
    private static final double ALLY_RADIUS = 12.0D;
    private static final int ALLY_TICKS = 120;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.WAR_HORN;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity blast = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.8D)),
                        WINDUP + 10, ctx.size(), ctx.look());
                SpellFx.release(ctx.caster(), ctx.definition(), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(4.0F, 14.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity caller = entity.livingOwner();
            if (caller == null || !caller.isAlive()) {
                entity.finish();
                return;
            }
            Vec3 look = caller.getLookAngle();
            entity.setPos(caller.getEyePosition().add(look.scale(0.8D)));
            entity.setDirection(look);
            if (entity.tickCount != WINDUP) {
                return;
            }

            boolean narrow = entity.sneakMode();
            double range = narrow ? LONG_RANGE : RANGE;
            double cos = narrow ? NARROW_COS : CONE_COS;
            Vec3 flatLook = new Vec3(look.x, 0.0D, look.z);
            flatLook = flatLook.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flatLook.normalize();

            for (LivingEntity hostile : SkillTargets.hostilesWithin(level, caller, caller.position(), range)) {
                Vec3 to = hostile.position().subtract(caller.position());
                Vec3 flatTo = new Vec3(to.x, 0.0D, to.z);
                if (flatTo.lengthSqr() < 0.01D || flatTo.normalize().dot(flatLook) < cos) {
                    continue;
                }
                SkillTargets.hurt(level, caller, hostile, entity.damage(), entity.definition(), true);
                SkillTargets.shove(hostile, caller.position(), 1.1D * Math.max(0.3D, entity.knockback()), 0.35D);
            }

            // The other half of the horn: everyone on your side stands a little straighter.
            for (LivingEntity ally : SkillTargets.alliesWithin(level, caller, caller.position(), ALLY_RADIUS)) {
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ALLY_TICKS, 0, true, false));
                if (ally instanceof ServerPlayer rallied) {
                    PlayerMagicState state = rallied.getData(MagicalAttachments.MAGIC_STATE);
                    state.addBarrier(entity.definition().barrierRestore());
                    state.sync(rallied);
                }
            }

            entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            SpellFx.impact(level, entity.definition(), caller.getEyePosition().add(look.scale(2.0D)), look, null, caller, 1.7F);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.CROWN).frame(11).band(GlyphKind.ARC_SWEEP, 21).band(GlyphKind.CHAIN_BAND, 7).stamps(StampId.TRIANGLE, 7).core(CoreKind.SUNBURST).spin(SpinSignature.SWEEP))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.field(Silhouette.Form.CONE_SPOT, FxKinds.Field.HOLY_GLASS, 3.0F, 1.6F))
                .silhouette(Silhouette.swarm(Silhouette.Form.FAN, FxKinds.Smoke.LENS_SPARKLE, 12, 1.4F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.DUST, FxKinds.Overlay.BLOOM_RAYS)
                .bounds(4.0F, 3.0F, 1.2F);
    }
}
