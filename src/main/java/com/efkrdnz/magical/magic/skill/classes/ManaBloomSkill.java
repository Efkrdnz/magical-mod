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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * EVOKER - SLOW_BLOOM / AIMED_POINT / GATHER_THEN_BURST. A bud of raw mana opens where you point
 * it, dragging everything nearby steadily inward for the whole bloom, and only pays out when it
 * finally opens. It is the Evoker skill because it is the one spell in the tree that rewards
 * casting early and trusting it. Sneak = a tighter, faster bloom.
 */
public final class ManaBloomSkill implements SkillModule {
    private static final double PULL = 0.06D;
    private static final int PULL_INTERVAL = 4;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.MANA_BLOOM;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int life = ctx.sneak() ? Math.max(20, ctx.duration() / 2) : Math.max(40, ctx.duration());
                SpellEffectEntity bud = SpellEffectEntity.spawn(ctx, ctx.aim().point(), life, ctx.size(), ctx.look());
                bud.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                SpellFx.release(ctx.caster(), ctx.definition(), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 26.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(5.0F, 22.0F);
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
                float openness = Math.min(1.0F, entity.tickCount / (float) Math.max(1, entity.life()));
                entity.setValue(openness);
                if (entity.tickCount % PULL_INTERVAL != 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                Vec3 centre = entity.position();
                // The pull tightens as the bloom opens, so late escapes are harder than early ones.
                double strength = PULL * (0.5D + openness);
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), centre, entity.radius() * 2.0D)) {
                    Vec3 toward = centre.subtract(victim.position());
                    if (toward.lengthSqr() < 0.04D) {
                        continue;
                    }
                    victim.setDeltaMovement(victim.getDeltaMovement().add(toward.normalize().scale(strength)));
                    victim.hurtMarked = true;
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                Vec3 centre = entity.position();
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), centre, entity.radius() * 1.5D)) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    SkillTargets.shove(victim, centre, 0.7D, 0.3D);
                }
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.6F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.LEAF).frame(14).band(GlyphKind.PETAL_BAND, 20).band(GlyphKind.BRAID_BAND, 5).stamps(StampId.TEARDROP, 10).core(CoreKind.RIPPLE).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.orb(Silhouette.Form.SPHERE, FxKinds.Orb.CHARGE_SPHERE, 0.9F, 1, 3))
                .silhouette(Silhouette.swarm(Silhouette.Form.RING, FxKinds.Smoke.PETAL, 14, 1.6F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.SPIRAL_DRAIN, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.PRISM_RING)
                .bounds(4.0F, 3.0F, 1.4F);
    }
}
