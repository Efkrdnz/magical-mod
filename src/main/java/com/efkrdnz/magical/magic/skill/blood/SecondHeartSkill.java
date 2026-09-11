package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * BLOOD T-1 - health taken out of your body now so it cannot be taken out of you later.
 *
 * <p>Banking is not a cost, it is a move: the health leaves the bar and waits in a reserve, and the
 * first blow that would have killed you spends the reserve instead. So it makes you frailer in
 * exchange for making you harder to finish - which is also what it does to potency, since a blood
 * mage who has banked is immediately casting stronger spells on less health.
 *
 * <p>The reserve is a passive counter rather than a field: it is one number keyed to one skill, and
 * {@code BloodPassives.cheatDeath} is the only thing that reads it.
 */
public final class SecondHeartSkill implements SkillModule {

    /** Health moved out of the bar and into the reserve. */
    public static final float BANKED_HEALTH = 6.0F;

    /**
     * The reserve is stored in hundredths of a health point, so it survives a round trip through an
     * int counter without losing the fractions the cheat-death refund is built on.
     */
    public static final int RESERVE_SCALE = 100;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SECOND_HEART;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                if (ctx.state().passiveCounter(definition().id()) > 0) {
                    player.displayClientMessage(Component.translatable("message.magical.second_heart_already"), true);
                    return CastResult.FAILED;
                }
                // Banking obeys the same floor as any blood cost: it must never be the thing that
                // kills you, even though it stores the health rather than burning it.
                if (!BloodService.payInHealthOnly(player, ctx.state(),
                        (int) (BANKED_HEALTH * BloodService.COST_PER_HEALTH))) {
                    return CastResult.FAILED;
                }
                ctx.state().setPassiveCounter(definition().id(), (int) (BANKED_HEALTH * RESERVE_SCALE));
                SpellEffectEntity.spawn(ctx, player.position().add(0.0D, 1.0D, 0.0D),
                        Math.max(40, ctx.duration()), 1.1F * Math.max(0.5F, ctx.size()), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }

            @Override
            public MobCastProfile mob() {
                return null;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            if (!(entity.owner() instanceof LivingEntity living) || !living.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(living.getX(), living.getY() + 1.05D, living.getZ());
            if (entity.tickCount == 1) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.INFINITY).frame(5)
                        .band(GlyphKind.CHAIN_BAND, 8, ColorRole.DIM)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.BRIGHT)
                        .stamps(StampId.RING, 5).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.BOTH)
                .silhouette(Silhouette.orb(Silhouette.Form.SPHERE, FxKinds.Orb.LIQUID_DROP, 0.7F).withRole(ColorRole.HOT))
                .silhouette(Silhouette.filament(Silhouette.Form.HELIX, FxKinds.Filament.VEIN, 4, 0.05F).withRole(ColorRole.INK))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.DROPLET, FxKinds.Overlay.IRIS_CLOSE)
                .budget(1)
                .bounds(2.0F, 2.5F, 1.5F);
    }
}
