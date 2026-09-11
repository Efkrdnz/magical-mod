package com.efkrdnz.magical.magic.skill.light;

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
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * LIGHT, display Tier 4 - the only thing in the mod that takes Corruption back off a player.
 *
 * <p>Dark magic writes its price down and nothing ever erases it on its own: no decay, no timer, no
 * respawn. This is the eraser, and it is deliberately the only one. That makes a Light caster worth
 * standing next to for a reason that has nothing to do with damage, and it makes the dark mage's
 * way out of the debt something they have to go and find.
 *
 * <p>It clears exactly one rung of the ladder per cast, from everyone in the circle rather than
 * only the caster - the fantasy is a rite performed over people. The curse at the top of the ladder
 * comes off as part of that, through {@link DarkService#cleanse}, so any future way of paying the
 * debt down lifts it too.
 *
 * <p>Stripping harmful effects is the baseline that keeps it from being a dead button in a party
 * with no dark mage in it. That is the mirror of {@code cleansing_ray}, which strips effects from
 * enemies: this is the same idea pointed the other way.
 *
 * <p><b>Not yet:</b> the plan gives this skill a second job - reducing Regard, the Eldritch price -
 * at its own rate. Eldritch is deferred, so that half has nothing to call.
 */
public final class PurificationSkill implements SkillModule {

    /** Corruption removed per cast: exactly one rung, so the effect is countable without a tooltip. */
    public static final int CLEARED = DarkService.THRESHOLD_STEP;

    /** How far the rite reaches. Generous - it is meant to be cast over a group. */
    public static final double RADIUS = 7.0D;

    private static final int RITE_TICKS = 30;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.PURIFICATION;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                SpellEffectEntity rite = SpellEffectEntity.spawn(ctx, ctx.feet(), RITE_TICKS,
                        (float) RADIUS * Math.max(0.5F, ctx.size()), ctx.look());
                rite.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS, 0.8F, 1.5F);
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
            ServerLevel level = entity.serverLevel();
            if (!(entity.owner() instanceof ServerPlayer caster) || !caster.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(caster.getX(), caster.getY() + 0.05D, caster.getZ());
            if (entity.tickCount % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, caster.getX(), caster.getY() + 0.4D, caster.getZ(),
                        6, entity.radius() * 0.4D, 0.3D, entity.radius() * 0.4D, 0.01D);
            }
            // The whole rite lands on one tick near the end, so a caster who dies part-way through
            // does not get a half-priced cleansing out of it.
            if (entity.tickCount != RITE_TICKS - 2) {
                return;
            }
            for (ServerPlayer subject : congregation(level, caster, entity.radius())) {
                cleanse(level, subject);
            }
            level.playSound(null, caster.blockPosition(), SoundEvents.BEACON_POWER_SELECT,
                    SoundSource.PLAYERS, 1.0F, 1.2F);
        };
    }

    /**
     * The caster and every allied player in reach. Players only: Corruption is a thing only players
     * carry, and stripping a tamed wolf's effects is not what this skill is about.
     */
    private static List<ServerPlayer> congregation(ServerLevel level, ServerPlayer caster, float radius) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(caster);
        for (LivingEntity ally : SkillTargets.alliesWithin(level, caster, caster.position(), radius)) {
            if (ally instanceof ServerPlayer player && player != caster) {
                out.add(player);
            }
        }
        return out;
    }

    private static void cleanse(ServerLevel level, ServerPlayer subject) {
        DarkService.cleanse(subject, subject.getData(MagicalAttachments.MAGIC_STATE), CLEARED);
        subject.getData(MagicalAttachments.MAGIC_STATE).sync(subject);
        // Copied out first: removeEffect mutates the same collection getActiveEffects() is a view of.
        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (MobEffectInstance effect : subject.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(effect.getEffect());
            }
        }
        for (Holder<MobEffect> effect : harmful) {
            subject.removeEffect(effect);
        }
        level.sendParticles(ParticleTypes.END_ROD, subject.getX(), subject.getY() + 1.0D, subject.getZ(),
                20, 0.3D, 0.6D, 0.3D, 0.02D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                // Palette variant 1 rather than the school default: it is the second-palest step
                // of the Light ramp, and it is also what keeps this rite's victim overlay distinct
                // from Gabriel's, which uses the same BLOOM_RAYS on variant 0.
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.CHALICE).frame(13)
                        .band(GlyphKind.PETAL_BAND, 22, ColorRole.HOT)
                        .band(GlyphKind.DASHED_RING, 33, ColorRole.BRIGHT)
                        .stamps(StampId.RING, 13).core(CoreKind.SUNBURST, ColorRole.BRIGHT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.GROUND)
                .throughTerrain(true)
                .silhouette(Silhouette.field(Silhouette.Form.DISC, FxKinds.Field.HOLY_GLASS, 7.0F, 0.1F).withRole(ColorRole.BRIGHT))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.BLOOM_RAYS)
                .budget(3)
                .bounds(16.0F, 6.0F, 2.0F);
    }
}
