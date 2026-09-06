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
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
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
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * RUNEWRIGHT - INSCRIBE / AIMED_SURFACE / PROXIMITY_TRIGGER. A rune cut into whatever you are
 * looking at and left there. It sits inert for a long time and goes off the moment something
 * hostile crosses it, rooting whatever it catches. A Runewright does not throw the spell; they
 * leave it lying around and let the fight walk into it. Sneak = a wider, shorter-lived sigil.
 */
public final class SigilForgeSkill implements SkillModule {
    /** Ticks of settling before the rune can fire, so it cannot detonate in the caster's face. */
    private static final int ARM = 20;
    private static final double TRIGGER = 2.4D;
    private static final double WIDE_TRIGGER = 4.0D;
    private static final int ROOT_TICKS = 40;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SIGIL_FORGE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                // Sneaking trades reach for patience: a wider rune that will not wait as long.
                int life = ctx.sneak() ? Math.max(80, ctx.duration() / 2) : Math.max(120, ctx.duration());
                SpellEffectEntity sigil = SpellEffectEntity.spawn(ctx, ctx.aim().point(), life, ctx.size(), new Vec3(0.0D, 1.0D, 0.0D));
                sigil.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                SpellFx.release(ctx.caster(), ctx.definition(), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 24.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(4.0F, 18.0F);
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
                if (entity.tickCount < ARM) {
                    entity.setValue(entity.tickCount / (float) ARM);
                    return;
                }
                entity.setValue(1.0F);
                ServerLevel level = entity.serverLevel();
                double trigger = entity.sneakMode() ? WIDE_TRIGGER : TRIGGER;
                List<LivingEntity> crossing = SkillTargets.hostilesWithin(level, entity.owner(), entity.position(), trigger);
                if (!crossing.isEmpty()) {
                    entity.finish();
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                double trigger = entity.sneakMode() ? WIDE_TRIGGER : TRIGGER;
                Vec3 at = entity.position();
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), at, trigger + entity.radius())) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    MagicStatusService.apply(victim, MagicStatus.ROOTED, ROOT_TICKS, entity.definition().id(), entity.owner());
                }
                SpellFx.impact(level, entity.definition(), at, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.3F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.KNOT).frame(5).band(GlyphKind.RUNE_BAND, 18).band(GlyphKind.FACET_BAND, 6).stamps(StampId.KEY, 6).core(CoreKind.HEX_LENS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.field(Silhouette.Form.GROUND_SEAM, FxKinds.Field.RUNE_PAPER, 1.5F, 0.1F))
                .silhouette(Silhouette.mark(FxKinds.Mark.LATTICE_GRID, 1.2F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.SIGIL_SLAM_FLASH, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.HEX_PULSE)
                .bounds(3.0F, 1.0F, 1.2F);
    }
}
