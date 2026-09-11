package com.efkrdnz.magical.magic.skill.dark;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.DarkService;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * DARK T-2 - a doll that remembers, and hands it all back at the end.
 *
 * <p>Bind something to a straw figure and walk away. The effigy never moves and never needs to see
 * its victim again: every wound the bound target takes from <em>anything</em> - you, your allies,
 * a fall, a creeper it wandered into - is written into the doll at {@link #ECHO_SHARE}. When the
 * doll's time runs out, the whole ledger lands at once.
 *
 * <p>So the skill rewards a fight you were going to win anyway, and rewards it hardest when the
 * target is being hurt by things that are not you. It is also the only damage in the mod with no
 * range at all: the doll is here and the target is wherever it ran to.
 *
 * <p>Watching health rather than hooking a damage event is deliberate. There is no per-entity
 * damage hook to attach to, and health is the honest measure anyway - it counts what actually got
 * through armour, resistances and barriers instead of what was swung.
 */
public final class EffigySkill implements SkillModule {

    /** Corruption signed for at the moment of binding. */
    public static final int CORRUPTION = 8;

    /** Share of every wound the bound target takes that the doll keeps. */
    public static final float ECHO_SHARE = 0.35F;

    /** How far away something can be and still be bindable. */
    public static final double BIND_RANGE = 24.0D;

    /** Below this the ledger is not worth a hit, and the doll just crumbles. */
    private static final float MIN_PAYOUT = 1.0F;

    /** Slot in the entity's server scratch where last tick's health is kept. */
    private static final String LAST_HEALTH = "effigyLastHealth";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.EFFIGY;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                List<LivingEntity> candidates = SkillTargets.hostilesWithin(ctx.level(), player,
                        ctx.eye().add(ctx.look().scale(BIND_RANGE * 0.5D)), BIND_RANGE * 0.5D);
                if (candidates.isEmpty()) {
                    return CastResult.FAILED;
                }
                LivingEntity bound = candidates.get(0);
                SpellEffectEntity effigy = SpellEffectEntity.spawn(ctx, ctx.feet(),
                        Math.max(80, ctx.duration()), 0.7F * Math.max(0.5F, ctx.size()), ctx.look());
                effigy.setTarget(bound);
                effigy.setValue(0.0F);
                effigy.serverData().putFloat(LAST_HEALTH, bound.getHealth());
                effigy.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                DarkService.corrupt(player, ctx.state(), CORRUPTION);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(),
                        SoundSource.PLAYERS, 0.7F, 0.7F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.3D;
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
            ServerLevel level = entity.serverLevel();
            if (!(entity.target() instanceof LivingEntity bound) || !bound.isAlive()) {
                // The target died, or left the dimension and took the reference with it. Either way
                // the doll has nothing left to collect: the debt dies with the debtor.
                entity.finish();
                return;
            }
            float last = entity.serverData().getFloat(LAST_HEALTH);
            float now = bound.getHealth();
            if (now < last) {
                entity.setValue(entity.value() + (last - now) * ECHO_SHARE);
                level.sendParticles(ParticleTypes.SCULK_SOUL, entity.getX(), entity.getY() + 0.6D, entity.getZ(),
                        3, 0.18D, 0.25D, 0.18D, 0.01D);
            }
            // Healing is written down too, or a target that regenerates between hits would let the
            // ledger charge for the same wound twice.
            entity.serverData().putFloat(LAST_HEALTH, now);
            if (entity.tickCount < entity.life() - 1) {
                return;
            }
            float owed = entity.value();
            if (owed >= MIN_PAYOUT) {
                SkillTargets.hurt(level, entity.owner(), bound, owed, entity.definition().id());
                level.playSound(null, bound.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                        SoundSource.HOSTILE, 0.8F, 1.4F);
                level.sendParticles(ParticleTypes.SOUL, bound.getX(), bound.getY() + 1.0D, bound.getZ(),
                        18, 0.4D, 0.6D, 0.4D, 0.02D);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.DARK)
                .circle(CircleScript.of(SchoolMaterial.DARK).emblem(EmblemId.EFFIGY_DOLL).frame(10)
                        .band(GlyphKind.TOOTH_BAND, 10, ColorRole.HOT)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.DIM)
                        // VOID_PIT is a dark core, so CircleScript forces its role to INK whatever
                        // is asked for - the HOT layer every profile needs has to be a band here.
                        .stamps(StampId.BONE, 10).core(CoreKind.VOID_PIT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.body(Silhouette.Form.FIGURE, FxKinds.Body.WOOD_VINE, 5, 0.85F).withRole(ColorRole.INK))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.ASH_FLAKE, FxKinds.Overlay.VIGNETTE)
                .budget(2)
                .bounds(2.0F, 2.5F, 1.5F);
    }
}
