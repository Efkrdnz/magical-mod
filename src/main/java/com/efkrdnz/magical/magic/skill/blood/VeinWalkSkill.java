package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.blood.BloodFieldData;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.passive.BloodHarvestRules;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
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
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * BLOOD T-1 - run down the vein: the escape that only goes where your blood already is.
 *
 * <p>Every other movement skill in the mod goes where you point. This one goes to your blood, in
 * this order: an enemy you have marked (bleeding under an Open Vein, or lit by Bloodscent) and are
 * looking at; the far end of a live Blood Manipulation; the furthest pool of yours in reach. So a
 * blood mage's route is decided seconds earlier, by where they chose to spill blood. Nothing in
 * reach and the button does nothing at all - refusing outright rather than falling back on a short
 * blink is the point, because a fallback would make the trail decorative.
 *
 * <p>The walk leaves two things at the origin: a trace, worth nothing, so it can be walked back
 * for Return ticks; and the vein itself, the body coming apart and running into wherever the
 * walker now is. Landing on a battery lifts it into the walker; landing on anything else spends
 * it, so one pool is never an infinite shuttle.
 */
public final class VeinWalkSkill implements SkillModule {

    /** How far a step can reach at one point of Reach, in blocks. */
    public static final double BASE_REACH = 24.0D;

    /** How straight the walker must be looking at a marked enemy to land on it. */
    private static final double LOOK_DOT = 0.92D;

    /** How far short of a marked enemy the walker lands, past the edge of their body. */
    private static final double LANDING_GAP = 0.6D;

    /** The trace waits at least this long, whatever the points say. */
    private static final int MIN_RETURN = 20;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.VEIN_WALK;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                double reach = BASE_REACH * ctx.size();
                Optional<Vec3> destination = markedEnemy(player, reach)
                        .or(() -> fieldEnd(player, reach))
                        .or(() -> BloodHarvestEntity.furthestPool(player, reach));
                if (destination.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.magical.no_blood_trail"), true);
                    // FAILED rather than a cooldown: the player pressed a button that could not have
                    // worked, and making them wait would punish the empty trail twice over.
                    return CastResult.FAILED;
                }
                if (!BloodService.pay(player, ctx.state(), BloodService.cost(ctx.stats()))) {
                    return CastResult.FAILED;
                }
                Vec3 from = player.position();
                Vec3 to = destination.get();
                // Both left before the walker leaves: the trace on the ground, worth nothing on
                // purpose (a refundable pool would make the walk free), and the vein in the air.
                BloodHarvestEntity.spawn(ctx.level(), player, from, BloodHarvestRules.KIND_TRACE, 0,
                        Math.max(MIN_RETURN, ctx.duration()));
                BloodHarvestEntity.vein(ctx.level(), player, BloodHarvestEntity.chestOf(player));
                SpellEffectEntity.spawn(ctx, from.add(0.0D, 1.0D, 0.0D), 20, 0.9F, ctx.look());
                player.teleportTo(to.x, to.y, to.z);
                player.resetFallDistance();
                BloodHarvestEntity.landOn(player, to);
                SpellEffectEntity.spawn(ctx, to.add(0.0D, 1.0D, 0.0D), 20, 0.9F, ctx.look());
                player.serverLevel().playSound(null, player.blockPosition(),
                        SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.5F, 1.3F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                // Haste is the speed stat: resolve already shortens the cooldown for it, and the
                // walk has no flight to speed up. Reach is size, Return is duration.
                return new TuningView(false, true, true, true, true, null,
                        "screen.magical.tuning.haste", "screen.magical.tuning.reach",
                        "screen.magical.tuning.return", "screen.magical.tuning.thrift");
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.NONE;
            }
        };
    }

    /**
     * An enemy the walker has marked and is looking at: bleeding under their Open Vein, or lit by
     * their Bloodscent. The landing is a step short of them rather than inside them.
     */
    private static Optional<Vec3> markedEnemy(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestDot = LOOK_DOT;
        for (LivingEntity enemy : SkillTargets.hostilesWithin(player.serverLevel(), player, player.position(), reach)) {
            if (!MagicStatusService.has(enemy, MagicStatus.REVEALED)
                    || !player.getUUID().equals(MagicStatusService.sourceOf(enemy, MagicStatus.REVEALED))) {
                continue;
            }
            Vec3 toward = BloodHarvestEntity.chestOf(enemy).subtract(eye);
            double distance = toward.length();
            if (distance > reach || distance < 1.0E-3D) {
                continue;
            }
            double dot = toward.scale(1.0D / distance).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = enemy;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        Vec3 back = player.position().subtract(best.position());
        back = back.lengthSqr() > 1.0E-4D ? back.normalize() : look.scale(-1.0D);
        return Optional.of(best.position().add(back.scale(best.getBbWidth() * 0.5D + LANDING_GAP)));
    }

    /** The far end of the walker's live Blood Manipulation, read off the field it synced. */
    private static Optional<Vec3> fieldEnd(ServerPlayer player, double reach) {
        for (SpellEffectEntity field : player.serverLevel().getEntities(MagicalEntities.SPELL_EFFECT.get(),
                player.getBoundingBox().inflate(reach + 8.0D),
                effect -> effect.owner() == player && MagicContent.BLOOD_MANIPULATION.id().equals(effect.definition().id()))) {
            BloodFieldData data = BloodFieldData.decode(field.syncedData());
            if (data == null || data.isEmpty()) {
                continue;
            }
            float yaw = data.baseYaw();
            float pitch = data.basePitch();
            if (data.keepRotating()) {
                yaw = player.getYRot();
                pitch = player.getXRot();
            }
            double[] basis = BloodShapeGeometry.basis(yaw, pitch);
            double[] out = new double[3];
            double bestRank = -1.0D;
            Vec3 best = null;
            for (int i = 0; i < data.spine().length; i++) {
                double u = data.u(i);
                double v = data.v(i);
                double rank = u * u + v * v;
                if (rank <= bestRank) {
                    continue;
                }
                BloodShapeGeometry.project(u, 0.0D, v, data.heightOffset(), basis, out);
                Vec3 point = field.position().add(out[0], out[1], out[2]);
                if (point.distanceTo(player.position()) > reach) {
                    continue;
                }
                bestRank = rank;
                best = point;
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        return Optional.empty();
    }

    @Override
    public SpellBehavior behavior() {
        // Two short-lived marks, one at each end. Nothing to drive, so nothing to tick.
        return entity -> {
            if (entity.tickCount == 1) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.KEY).frame(4)
                        .band(GlyphKind.DASHED_RING, 22, ColorRole.BRIGHT)
                        .band(GlyphKind.BRAID_BAND, 6, ColorRole.INK)
                        .stamps(StampId.LINK, 8).core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.filament(Silhouette.Form.CHAIN, FxKinds.Filament.LIQUID_ROPE, 6, 0.06F).withRole(ColorRole.HOT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.TUNNEL)
                .budget(1)
                .bounds(2.0F, 2.0F, 1.5F);
    }
}
