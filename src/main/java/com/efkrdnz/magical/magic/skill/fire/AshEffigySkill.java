package com.efkrdnz.magical.magic.skill.fire;

import com.efkrdnz.magical.entity.fx.EffigyEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.fx.ThrownSpellEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
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
import net.minecraft.world.phys.Vec3;

/**
 * FIRE T3 - LURE / THROWN_ARC / EFFIGY. A fist of ash is lobbed; where it lands it rises into a
 * hollow effigy of the caster with its own HP that every hostile mob nearby is drawn onto. When it
 * is destroyed or expires it bursts into a choking, blinding, igniting ash cloud. Sneak = throw it
 * backward.
 */
public final class AshEffigySkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ASH_EFFIGY;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 dir = ctx.lookOrBack();
                Vec3 start = ctx.eye().add(dir.scale(0.6D)).add(0.0D, -0.2D, 0.0D);
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), start, 200, ctx.size(), dir, (int) (ctx.seed() & 63));
                template.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
                ThrownSpellEntity bundle = ThrownSpellEntity.create(ctx.level(), template, start, dir.scale(Math.max(0.6D, ctx.stats().speed())).add(0.0D, 0.12D, 0.0D), 0.05F, 0.99F, 0, 0);
                ctx.level().addFreshEntity(bundle);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.summon();
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            if (!(entity instanceof ThrownSpellEntity bundle) || !bundle.landed()) {
                return;
            }
            Vec3 pos = bundle.position();
            EffigyEntity effigy = EffigyEntity.create(bundle.serverLevel(), bundle.definition(), bundle.owner(), pos, Math.max(60, bundle.duration()), bundle.damage(), 2.0F * bundle.radius(), 12, 40.0F, bundle.seed());
            bundle.serverLevel().addFreshEntity(effigy);
            SpellFx.impact(bundle.serverLevel(), bundle.definition(), pos, bundle.landingNormal(), null, bundle.owner(), 1.0F);
            bundle.discard();
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.MASK).frame(5).band(GlyphKind.CHAIN_BAND, 14).band(GlyphKind.RUNE_BAND, 10).band(GlyphKind.DASHED_RING, 28).stamps(StampId.EYE, 7).orbit(5, 0.84F, 3).core(CoreKind.IRIS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.swarm(Silhouette.Form.FIGURE, FxKinds.Smoke.ASH_FLAKE, 90, 0.6F, 2.0F).forModes(1))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.EYE_SLIT, 0.22F, 4, 8).withOffset(1.75F).forModes(1))
                .silhouette(Silhouette.swarm(Silhouette.Form.SPHERE, FxKinds.Smoke.ASH_FLAKE, 18, 0.28F).forModes(0))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.ASH_FLAKE, 2, 0.1F, 14, 0.2F, 0, 0.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.ASH_FLAKE, FxKinds.Overlay.IRIS_CLOSE)
                .bounds(3.0F, 3.0F, 1.0F);
    }
}
