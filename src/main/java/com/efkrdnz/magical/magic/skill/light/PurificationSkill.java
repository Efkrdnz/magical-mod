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
import com.efkrdnz.magical.magic.visual.SpellFx;
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
import net.minecraft.world.phys.Vec3;

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

    /** End rods in the ring that runs out along the floor from each subject when the rite lands. */
    private static final int RING_SPARKS = 16;

    /**
     * How fast that ring runs out. An end rod keeps 0.91 of its speed a tick, so this carries it
     * about three and a half blocks: far enough that the subject, looking level, sees the front of
     * it settle on the floor ahead of them. At 0.12 it stopped a block and a half out, which is under
     * the bottom edge of a level first-person view, and the caster saw none of their own rite land.
     */
    private static final double RING_SPEED = 0.3D;

    /** End rods lifting off the rim of the rite each tick while it builds. */
    private static final int RIM_SPARKS = 2;

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
                return MobCastProfile.NONE;
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
            if (entity.tickCount <= RITE_TICKS - 2) {
                rim(level, caster, entity.radius());
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
        blessing(level, subject);
    }

    /**
     * What the rite leaves on each person it lands on: a ring of light running out along the floor
     * from their feet and a few sparks hanging over their head. It was twenty end rods at chest
     * height, which read as a blob to everyone watching and, for the caster - always one of the
     * subjects - put a handful of sprites a hand's width from their own eyes.
     *
     * <p>The halo goes to everybody but the person it crowns. Half a block over the head is still
     * inside the top of a first-person view once the scatter carries a spark a little forward, and
     * a spark that near the eye is drawn as a pale pane across the top of the frame.
     */
    private static void blessing(ServerLevel level, ServerPlayer subject) {
        double x = subject.getX();
        double y = subject.getY();
        double z = subject.getZ();
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < RING_SPARKS; i++) {
            double a = offset + i * Math.PI * 2.0D / RING_SPARKS;
            double dx = Math.cos(a);
            double dz = Math.sin(a);
            // a count of 0 sends one particle and reads the offsets as its velocity, times the speed
            level.sendParticles(ParticleTypes.END_ROD, x + dx * 0.4D, y + 0.25D, z + dz * 0.4D, 0, dx, 0.0D, dz, RING_SPEED);
        }
        double crown = y + subject.getBbHeight() + 0.45D;
        for (ServerPlayer viewer : level.players()) {
            if (viewer != subject) {
                level.sendParticles(viewer, ParticleTypes.END_ROD, false, false, x, crown, z, 8, 0.3D, 0.08D, 0.3D, 0.005D);
            }
        }
    }

    /**
     * Light lifting off the edge of the rite while it builds: where its reach ends, so the people it
     * is performed over can see whether they are standing inside it. It was six end rods every third
     * tick scattered at random round the caster's shins, which said nothing about the reach and, in
     * a daylight capture, read as a few stray sparks on the floor. On the rim they stand as a thin
     * curtain a block high, and an end rod hangs for three seconds, so the circle is drawn in full by
     * the time the rite lands.
     */
    private static void rim(ServerLevel level, ServerPlayer caster, float radius) {
        for (int i = 0; i < RIM_SPARKS; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            Vec3 edge = new Vec3(caster.getX() + Math.cos(a) * radius, caster.getY() + 0.5D, caster.getZ() + Math.sin(a) * radius);
            Vec3 floor = SpellFx.groundBelow(level, edge, 4);
            // an end rod keeps 0.91 of its speed a tick: this lifts it about a block and it hangs there
            double lift = 0.07D + 0.05D * level.random.nextDouble();
            level.sendParticles(ParticleTypes.END_ROD, floor.x, floor.y + 0.05D, floor.z, 0, 0.0D, lift, 0.0D, 1.0D);
        }
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
