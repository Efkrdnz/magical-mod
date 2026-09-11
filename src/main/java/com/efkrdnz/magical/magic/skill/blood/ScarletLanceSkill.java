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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * BLOOD T-1 - the desperation nuke, and the one skill in the school the Vessel is barred from.
 *
 * <p>Harvested blood cannot pay for this. That is the whole design: Scarlet Lance scales harder
 * with potency than anything else Blood has, so letting a full Vessel stand in for the cost would
 * let a mage at one health throw the strongest spell in the school for free, forever. Charging the
 * body keeps the reward and the risk on the same axis.
 *
 * <p>Below a quarter health it stops stopping - the lance runs through whatever it kills and keeps
 * going, which is the payoff for having been that low in the first place.
 */
public final class ScarletLanceSkill implements SkillModule {

    /** Charged to the body alone. See the class note - the Vessel is deliberately not allowed. */
    public static final int BLOOD_COST = 40;

    /** Below this share of health the lance pierces instead of stopping on its first victim. */
    public static final float PIERCE_HEALTH_FRACTION = 0.25F;

    private static final double STEP = 0.9D;
    private static final double HIT_RADIUS = 1.1D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SCARLET_LANCE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (ctx.caster() instanceof ServerPlayer player
                        && !BloodService.payInHealthOnly(player, ctx.state(), BLOOD_COST)) {
                    return CastResult.FAILED;
                }
                // Potency is read after the payment, so the blood this cast just spilled is already
                // making it stronger. That is the intended feedback, not an accident of ordering.
                SpellEffectEntity lance = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.9D)),
                        Math.max(30, ctx.duration()), 0.6F * Math.max(0.5F, ctx.size()), ctx.look());
                lance.setExtra(pierces(ctx.caster()) ? 1 : 0);
                lance.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 24.0F);
            }
        };
    }

    private static boolean pierces(LivingEntity caster) {
        float max = caster.getMaxHealth();
        return max > 0.0F && caster.getHealth() / max < PIERCE_HEALTH_FRACTION;
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            double step = STEP * Math.max(0.4D, entity.speed());
            entity.setPos(entity.position().add(entity.direction().scale(step)));

            boolean piercing = entity.extra() == 1;
            float potency = entity.owner() instanceof LivingEntity living ? BloodService.potency(living) : 1.0F;
            for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), entity.position(), HIT_RADIUS)) {
                SkillTargets.hurt(level, entity.owner(), victim, entity.damage() * potency, entity.definition(), true);
                SkillTargets.shove(victim, entity.position(), entity.knockback(), 0.15D);
                if (!piercing) {
                    entity.finish();
                    return;
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.BONE).frame(3)
                        .band(GlyphKind.TOOTH_BAND, 10, ColorRole.BRIGHT)
                        .band(GlyphKind.DASHED_RING, 16, ColorRole.HOT)
                        .stamps(StampId.NEEDLE, 6).core(CoreKind.SUNBURST, ColorRole.BRIGHT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.TRAIL, FxKinds.Filament.VEIN, 5, 0.07F, 2.4F, 3).withRole(ColorRole.HOT))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LIQUID_DROP, 0.5F).withRole(ColorRole.BRIGHT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DROPLET, FxKinds.Overlay.FLASH)
                .budget(2)
                .bounds(3.0F, 2.0F, 2.0F);
    }
}
