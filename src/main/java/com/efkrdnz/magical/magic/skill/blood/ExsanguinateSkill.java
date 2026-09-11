package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.HoldService;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * BLOOD T-1 - hold to keep draining, and the Vessel fills with whatever comes out.
 *
 * <p>Held rather than charged. What a player feels is the behaviour the design asked for - the
 * longer you commit, the more you take - but it runs on the hold path two shipped skills already
 * use ({@code DelugeJetSkill}, {@code ArcaneGraspSkill}) instead of a second charge pipeline of its
 * own: no new payload, no new client input class, no new network registration, and the
 * loadout-swap safety those skills depend on comes along with it.
 *
 * <p>Each pull takes a share of the target's <em>current</em> health, so it is brutal against
 * something healthy and gentle against something nearly dead. That is deliberate: it makes
 * Exsanguinate the opener that refills you, and Scarlet Lance the thing that finishes.
 */
public final class ExsanguinateSkill implements SkillModule {

    public static final int BLOOD_COST = 10;

    /** Longest the tether can be sustained, after which it lets go on its own. */
    public static final int MAX_HOLD = 100;

    private static final int PULL_INTERVAL = 10;

    /** Share of the target's current health taken per pull. */
    public static final float DRAIN_FRACTION = 0.06F;

    /** And what share of the damage dealt becomes Vessel. */
    public static final float HARVEST_SHARE = 0.8F;

    private static final double LOCK_RANGE = 16.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.EXSANGUINATE;
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
                        ctx.eye().add(ctx.look().scale(LOCK_RANGE * 0.5D)), LOCK_RANGE * 0.5D);
                if (candidates.isEmpty()) {
                    return CastResult.FAILED;
                }
                if (!BloodService.pay(player, ctx.state(), BLOOD_COST)) {
                    return CastResult.FAILED;
                }
                SpellEffectEntity tether = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.8D)),
                        MAX_HOLD + 10, 0.8F * Math.max(0.5F, ctx.size()), ctx.look());
                tether.setTarget(candidates.get(0));
                tether.setExtra(ctx.slot());
                tether.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                return CastResult.SUCCESS;
            }

            @Override
            public boolean holdable() {
                return true;
            }

            @Override
            public double aimTolerance() {
                return 0.25D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.0F, 14.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            if (!(entity.target() instanceof LivingEntity victim) || !victim.isAlive()
                    || !(entity.owner() instanceof ServerPlayer player) || !player.isAlive()) {
                entity.finish();
                return;
            }
            // Let go the moment the key comes up, the tether over-stretches, or the hold runs out.
            if (entity.tickCount >= MAX_HOLD
                    || !HoldService.isHeld(player, entity.extra())
                    || player.position().distanceTo(victim.position()) > LOCK_RANGE * 1.5D) {
                entity.finish();
                return;
            }
            entity.setPos(victim.getEyePosition().add(0.0D, -0.3D, 0.0D));
            Vec3 toward = victim.position().subtract(player.position());
            if (toward.lengthSqr() > 1.0E-4D) {
                entity.setDirection(toward.normalize());
            }
            if (entity.tickCount % PULL_INTERVAL != 0) {
                return;
            }
            float drawn = Math.max(entity.damage(), victim.getHealth() * DRAIN_FRACTION);
            SkillTargets.hurt(level, player, victim, drawn, entity.definition().id());
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            state.addBloodVessel(Math.round(drawn * HARVEST_SHARE));
            state.sync(player);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.GEAR).frame(7)
                        .band(GlyphKind.BRAID_BAND, 18, ColorRole.HOT)
                        .band(GlyphKind.TICK_BAND, 24, ColorRole.BRIGHT)
                        .stamps(StampId.TOOTH, 12).core(CoreKind.VOID_PIT, ColorRole.INK).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.TARGET_FOLLOW)
                .silhouette(Silhouette.filament(Silhouette.Form.LINK, FxKinds.Filament.VEIN, 3, 0.09F).withRole(ColorRole.HOT))
                .silhouette(Silhouette.swarm(Silhouette.Form.TRAIL, FxKinds.Smoke.DROPLET, 16, 0.8F).withRole(ColorRole.BRIGHT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SPIRAL_DRAIN, FxKinds.Smoke.DROPLET, FxKinds.Overlay.VIGNETTE)
                .holdable(true)
                .budget(2)
                .bounds(4.0F, 2.5F, 2.0F);
    }
}
