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
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * DARK T-2 - the cheapest button in the school to press and the most expensive to have pressed.
 *
 * <p>A thrown cut that does almost nothing to anything healthy. Against something already under
 * {@link #EXECUTE_FRACTION} of its health it does not deal damage at all: it ends the thread, and
 * writes down Corruption equal to every point of health it did not have to go through.
 *
 * <p>So the price is exactly the work skipped. Finishing something that was one hit from dead is
 * nearly free; reaching for a big health bar the moment it dips is most of a rung in one press.
 * That is the whole skill, and it is why {@code ledger} exists.
 *
 * <p>Large health bars are exempt outright. A max-health gate rather than a boss tag on purpose:
 * this mod's own bosses are not in {@code EntityTypeTags.BOSSES}, and a rule that reads the health
 * bar covers anything added later without a registry to keep in step.
 */
public final class SeverTheThreadSkill implements SkillModule {

    /** At or below this share of its maximum, a thread can be cut. */
    public static final float EXECUTE_FRACTION = 0.15F;

    /** Anything with a maximum this high is beyond cutting, whatever its current health. */
    public static final float BOSS_HEALTH = 150.0F;

    /** Corruption for each point of health the cut skipped, before the school's own modifiers. */
    public static final float CORRUPTION_PER_HEALTH = 1.0F;

    private static final double SPEED = 1.35D;
    private static final double HIT_RADIUS = 0.9D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SEVER_THE_THREAD;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity cut = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.6D)),
                        Math.max(20, ctx.duration()), 0.5F * Math.max(0.5F, ctx.size()), ctx.look());
                cut.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.15D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.0F, 20.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Vec3 step = entity.direction().scale(SPEED);
            Vec3 to = entity.position().add(step);
            entity.setPos(to.x, to.y, to.z);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, to.x, to.y, to.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            List<LivingEntity> hit = SkillTargets.hostilesWithin(level, entity.owner(), to, HIT_RADIUS);
            if (hit.isEmpty()) {
                return;
            }
            LivingEntity victim = hit.get(0);
            if (cuttable(victim)) {
                sever(level, entity, victim);
            } else {
                // Everything else gets the blade's own weight, which is deliberately almost nothing.
                SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition().id());
            }
            entity.finish();
        };
    }

    /** The gate, in one place so the test and the cast agree on it. */
    public static boolean cuttable(LivingEntity victim) {
        float max = victim.getMaxHealth();
        return max > 0.0F && max <= BOSS_HEALTH && victim.getHealth() <= max * EXECUTE_FRACTION;
    }

    private static void sever(ServerLevel level, SpellEffectEntity entity, LivingEntity victim) {
        float skipped = victim.getHealth();
        // Through the normal damage path rather than kill(), so the killer is credited, drops and
        // experience land, and onKill passives - including Blood's - still see it.
        SkillTargets.hurt(level, entity.owner(), victim, skipped + 1.0F, entity.definition().id());
        if (entity.owner() instanceof ServerPlayer player) {
            DarkService.corrupt(player, player.getData(MagicalAttachments.MAGIC_STATE),
                    Math.round(skipped * CORRUPTION_PER_HEALTH));
        }
        level.playSound(null, victim.blockPosition(), SoundEvents.SCULK_SHRIEKER_BREAK,
                SoundSource.HOSTILE, 0.9F, 1.6F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY() + 0.9D, victim.getZ(),
                12, 0.25D, 0.4D, 0.25D, 0.01D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.DARK)
                .circle(CircleScript.of(SchoolMaterial.DARK).emblem(EmblemId.CUT_THREAD).frame(4)
                        .band(GlyphKind.TICK_BAND, 16, ColorRole.DIM)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .stamps(StampId.NEEDLE, 4).core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.rift(Silhouette.Form.VERTICAL_PANE, FxKinds.Rift.BLADE, 0.8F, 0.05F,
                        FxKinds.RiftInterior.VOID_BLACK).withRole(ColorRole.HOT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.CRACKED_GLASS)
                .budget(2)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
