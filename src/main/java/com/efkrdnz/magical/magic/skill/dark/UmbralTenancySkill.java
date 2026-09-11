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
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * DARK T-2 - ride in something's shadow, and come out of it behind them.
 *
 * <p>You are not teleported and you do not become a spectator: you are dragged along a step behind
 * the host, invisible, with anything that was hunting you made to forget you. The whole time you
 * are inside, you cannot be where you want to be - you are where <em>it</em> goes - and that is the
 * cost the skill charges in play rather than in currency.
 *
 * <p>Coming out is the payoff. On emergence the host is {@code REVEALED} and your next spell inside
 * {@link #EMERGENCE_TICKS} lands at {@link #EMERGENCE_BONUS}, applied in
 * {@code DarkPassives.outgoingSpellDamage}. A tenancy that ends because the host died pays nothing:
 * there is no back left to appear behind.
 */
public final class UmbralTenancySkill implements SkillModule {

    public static final int CORRUPTION = 6;

    /** How far out a shadow can be entered from. Short - this is a commitment, not an escape. */
    public static final double ENTRY_RANGE = 12.0D;

    /** How long the window after emergence lasts. */
    public static final int EMERGENCE_TICKS = 60;

    /** And what a spell cast inside it is multiplied by. */
    public static final float EMERGENCE_BONUS = 1.9F;

    /** How far behind the host you are carried, in blocks. */
    private static final double TRAIL_DISTANCE = 0.9D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.UMBRAL_TENANCY;
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
                        ctx.eye().add(ctx.look().scale(ENTRY_RANGE * 0.5D)), ENTRY_RANGE * 0.5D);
                if (candidates.isEmpty()) {
                    return CastResult.FAILED;
                }
                LivingEntity host = candidates.get(0);
                SpellEffectEntity tenancy = SpellEffectEntity.spawn(ctx, host.position(),
                        Math.max(40, ctx.duration()), 1.0F * Math.max(0.5F, ctx.size()), ctx.look());
                tenancy.setTarget(host);
                tenancy.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                // Invisibility outlasts the tenancy by a second, so the last tick of the ride is
                // not spent standing visibly inside something's hitbox.
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, tenancy.life() + 20, 0, false, false));
                DarkService.corrupt(player, ctx.state(), CORRUPTION);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_CHARGE,
                        SoundSource.PLAYERS, 0.8F, 0.8F);
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
            if (!(entity.owner() instanceof ServerPlayer player) || !player.isAlive()) {
                entity.finish();
                return;
            }
            if (!(entity.target() instanceof LivingEntity host) || !host.isAlive()) {
                // The host died underneath you. You are simply standing where it was, with nothing
                // to emerge behind and no window to show for it.
                entity.finish();
                return;
            }
            Vec3 facing = host.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
            Vec3 behind = facing.lengthSqr() > 1.0E-4D ? facing.normalize().scale(-TRAIL_DISTANCE) : Vec3.ZERO;
            Vec3 seat = host.position().add(behind);
            player.teleportTo(seat.x, seat.y, seat.z);
            player.resetFallDistance();
            entity.setPos(host.getX(), host.getY() + 0.05D, host.getZ());
            // Anything already hunting you loses the thread, every tick, for as long as you are in
            // there. Done per tick rather than once on cast so that a mob which acquires you
            // mid-ride does not get to keep you.
            for (Mob hunter : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24.0D))) {
                if (hunter.getTarget() == player) {
                    hunter.setTarget(null);
                }
            }
            if (entity.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.SQUID_INK, host.getX(), host.getY() + 0.1D, host.getZ(),
                        4, 0.3D, 0.05D, 0.3D, 0.0D);
            }
            if (entity.tickCount < entity.life() - 1) {
                return;
            }
            emerge(level, player, host);
        };
    }

    /** The whole payoff, in one place: the host is lit up and your next spell is loaded. */
    private static void emerge(ServerLevel level, ServerPlayer player, LivingEntity host) {
        player.getData(MagicalAttachments.MAGIC_STATE)
                .passiveCounters().put(MagicContent.UMBRAL_TENANCY.id(), EMERGENCE_TICKS);
        player.removeEffect(MobEffects.INVISIBILITY);
        MagicStatusService.apply(host, MagicStatus.REVEALED, EMERGENCE_TICKS,
                MagicContent.UMBRAL_TENANCY.id(), player);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_BREAK,
                SoundSource.PLAYERS, 0.9F, 1.2F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
                16, 0.3D, 0.5D, 0.3D, 0.01D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.DARK)
                .circle(CircleScript.of(SchoolMaterial.DARK).emblem(EmblemId.CAST_SHADOW).frame(6)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.INK)
                        .band(GlyphKind.DASHED_RING, 18, ColorRole.DIM)
                        .stamps(StampId.CRESCENT, 6).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.TARGET_FOLLOW)
                .silhouette(Silhouette.field(Silhouette.Form.POOL, FxKinds.Field.VOID_INK, 1.6F, 0.05F).withRole(ColorRole.INK))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SPIRAL_DRAIN, FxKinds.Smoke.MIST_WISP, FxKinds.Overlay.TUNNEL)
                .budget(2)
                .bounds(3.0F, 2.0F, 1.5F);
    }
}
