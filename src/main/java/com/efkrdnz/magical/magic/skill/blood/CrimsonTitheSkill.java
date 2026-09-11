package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * BLOOD T-1 - the opener, and the skill that teaches the school.
 *
 * <p>It deals no damage at all. While it runs, blood you spill on others comes back to you as
 * Vessel, and blood spilled out of you comes back faster still - so the right way to play a blood
 * mage is to be in the fight taking hits, not standing behind it.
 *
 * <p>The window lives in a passive counter rather than on the entity, because
 * {@code BloodPassives} is what reads it on the damage hooks, and an entity the handler would have
 * to go hunting for every time something took damage is a worse answer than a number.
 */
public final class CrimsonTitheSkill implements SkillModule {

    /** What a point of damage dealt returns to the Vessel. */
    public static final float HARVEST_DEALT = 0.6F;

    /** And what a point of damage taken returns. Higher, deliberately: this is a pact, not a buff. */
    public static final float HARVEST_TAKEN = 1.2F;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CRIMSON_TITHE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    // A mob has no Vessel, so the pact would have nothing to pay into.
                    return CastResult.FAILED;
                }
                // Free to cast. The tithe's cost is the fight you have to take to collect on it.
                ctx.state().setPassiveCounter(definition().id(), Math.max(20, ctx.duration()));
                SpellEffectEntity.spawn(ctx, player.position().add(0.0D, 1.0D, 0.0D),
                        Math.max(20, ctx.duration()), 1.4F * Math.max(0.5F, ctx.size()), ctx.look());
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
        // Purely the mark that says the pact is running; the counter is the real state.
        return entity -> {
            if (!(entity.owner() instanceof LivingEntity living) || !living.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(living.getX(), living.getY() + 1.0D, living.getZ());
            if (entity.tickCount == 1) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }
        };
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
                .silhouette(Silhouette.swarm(Silhouette.Form.RING, FxKinds.Smoke.DROPLET, 22, 1.2F).withRole(ColorRole.HOT))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.HEARTBEAT)
                .budget(1)
                .bounds(2.5F, 2.5F, 1.5F);
    }
}
