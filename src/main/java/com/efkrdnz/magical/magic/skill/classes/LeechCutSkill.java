package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SPELLBLADE - LIFESTEAL / LOOK_LINE_INSTANT / SHORT_LINE. An instant short line from the eye: every
 * living thing in it takes dark damage ignoring armour and the caster immediately heals a share of
 * what was actually dealt; overheal becomes barrier. Sneak = stab backward.
 */
public final class LeechCutSkill implements SkillModule {
    private static final double LENGTH = 4.5D;
    private static final double HALF_WIDTH = 0.45D;
    private static final int MAX_TARGETS = 4;
    private static final float LEECH = 0.6F;
    private static final float HEAL_CAP = 20.0F;
    private static final int BARRIER_CAP = 12;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LEECH_CUT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 dir = ctx.lookOrBack();
                Vec3 from = ctx.eye();
                Vec3 to = from.add(dir.scale(LENGTH));
                List<LivingEntity> hits = SkillTargets.hostilesIn(ctx.level(), ctx.caster(), new AABB(from, to).inflate(HALF_WIDTH + 0.5D));
                hits.sort((a, b) -> Double.compare(a.distanceToSqr(from), b.distanceToSqr(from)));
                float dealt = 0.0F;
                int struck = 0;
                for (LivingEntity victim : hits) {
                    Vec3 p = victim.getBoundingBox().getCenter();
                    double along = p.subtract(from).dot(dir);
                    if (along < 0.0D || along > LENGTH || p.distanceTo(from.add(dir.scale(along))) > HALF_WIDTH + victim.getBbWidth() * 0.5D) {
                        continue;
                    }
                    float before = victim.getHealth();
                    SkillTargets.hurt(ctx.level(), ctx.caster(), victim, ctx.damage(), ctx.definition(), true);
                    dealt += Math.max(0.0F, before - victim.getHealth());
                    if (++struck >= MAX_TARGETS) {
                        break;
                    }
                }
                float heal = Math.min(HEAL_CAP, dealt * LEECH);
                if (heal > 0.0F) {
                    float missing = ctx.caster().getMaxHealth() - ctx.caster().getHealth();
                    ctx.caster().heal(Math.min(heal, missing));
                    int overheal = Math.round(Math.max(0.0F, heal - missing));
                    if (overheal > 0) {
                        ctx.state().addBarrier(Math.min(BARRIER_CAP, overheal));
                    }
                }
                SpellEffectEntity drill = SpellEffectEntity.spawn(ctx, from.add(0.0D, -0.25D, 0.0D), Math.max(8, ctx.duration()), (float) LENGTH, dir);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(0.0F, 4.0F);
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
            LivingEntity owner = entity.livingOwner();
            if (owner == null) {
                entity.finish();
                return;
            }
            entity.setPos(owner.getEyePosition().add(0.0D, -0.25D, 0.0D));
            Vec3 dir = entity.sneakMode() ? owner.getLookAngle().scale(-1.0D) : owner.getLookAngle();
            entity.setDirection(dir);
            float t = entity.tickCount / (float) Math.max(1, entity.life());
            entity.setRadius((float) (LENGTH * (t < 0.2F ? t / 0.2F : Math.max(0.0F, 1.0F - (t - 0.5F) / 0.5F))));
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.LEECH).frame(10).band(GlyphKind.WAVE_BAND, 10, ColorRole.INK).band(GlyphKind.TICK_BAND, 20).stamps(StampId.TEARDROP, 5).core(CoreKind.VOID_PIT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.HELIX, FxKinds.Filament.VEIN, 2, 0.16F, 4.5F, 1).withRole(ColorRole.INK))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.HEARTBEAT)
                .bounds(5.0F, 1.5F, 1.5F);
    }
}
