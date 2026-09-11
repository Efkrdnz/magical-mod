package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
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
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * BLOOD T-1 - area damage and area harvest in the same button.
 *
 * <p>The field opens where you aim and keeps everything inside it bleeding. What separates it from
 * any other damage-over-time zone is where the blood goes: every tick it takes, it gives back, so a
 * Hemorrhage dropped on a crowd is how a blood mage refills without spending their own body.
 *
 * <p>The harvest is per tick rather than per victim on purpose - paying out per body would make one
 * big group worth more than the whole rest of the school put together.
 */
public final class HemorrhageSkill implements SkillModule {

    /** What the field costs to open. Small: it is meant to pay for itself. */
    public static final int BLOOD_COST = 18;

    private static final int BLEED_INTERVAL = 10;

    /** Vessel returned per bleeding tick, whatever the crowd size. */
    public static final int HARVEST_PER_TICK = 4;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HEMORRHAGE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (ctx.caster() instanceof ServerPlayer player
                        && !BloodService.pay(player, ctx.state(), BLOOD_COST)) {
                    return CastResult.FAILED;
                }
                float potency = BloodService.potency(ctx.caster());
                SpellEffectEntity field = SpellEffectEntity.spawn(ctx, ctx.aim().point(),
                        Math.max(40, ctx.duration()), 2.2F * Math.max(0.6F, ctx.size()) * potency, ctx.look());
                field.setPhase(SpellEffectEntity.PHASE_ACTIVE);
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
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.5F, 16.0F);
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
            if (entity.tickCount % BLEED_INTERVAL != 0) {
                return;
            }
            ServerLevel level = entity.serverLevel();
            var bleeding = SkillTargets.hostilesInCylinder(level, entity.owner(), entity.position(),
                    entity.radius(), 3.0D);
            for (LivingEntity victim : bleeding) {
                SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition().id());
            }
            if (!bleeding.isEmpty() && entity.owner() instanceof ServerPlayer player) {
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                state.addBloodVessel(HARVEST_PER_TICK);
                state.sync(player);
            }
            SpellFx.zoneTick(level, entity.definition(), entity.position(), (float) entity.radius());
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.TREE).frame(6)
                        .band(GlyphKind.WAVE_BAND, 14, ColorRole.HOT)
                        .band(GlyphKind.TICK_BAND, 20, ColorRole.DIM)
                        .stamps(StampId.WAVE, 14).core(CoreKind.RIPPLE, ColorRole.INK).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.field(Silhouette.Form.POOL, FxKinds.Field.ORGANIC_CELLS, 2.4F, 0.4F, 4, 5).withOpacity(0.85F))
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.DROPLET, 30, 2.0F).withRole(ColorRole.HOT))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SPIRAL_DRAIN, FxKinds.Smoke.DROPLET, FxKinds.Overlay.INK_BLEED)
                .budget(2)
                .bounds(5.0F, 2.0F, 2.0F);
    }
}
