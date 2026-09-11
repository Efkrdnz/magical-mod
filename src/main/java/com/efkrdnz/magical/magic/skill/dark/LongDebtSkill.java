package com.efkrdnz.magical.magic.skill.dark;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.DarkService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
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
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * DARK T-2 - name a thing, take everything now, and be right about it.
 *
 * <p>Every cooldown you own resets on the spot and stays reset for the length of the bargain: while
 * the debt runs, {@code DarkPassives.slowTick} wipes the board twice a second, so you are casting
 * your whole kit as fast as you can press it. There is no mana relief and no damage bonus - the
 * gift is purely that nothing has to wait.
 *
 * <p>The condition is the named target dying before the timer does. Kill it and the bargain closes
 * for the {@link #CORRUPTION_HONOURED} you signed at the start. Fail, and
 * {@link #CORRUPTION_DEFAULTED} lands at once - enough to move you a rung and a half on its own,
 * and enough, from far enough up the ladder, to attach the curse.
 *
 * <p>It is the one skill in the school whose price is not settled when you cast it. Everything else
 * in Dark is expensive; this one is a wager.
 */
public final class LongDebtSkill implements SkillModule {

    /** Signed at the moment of naming, win or lose. */
    public static final int CORRUPTION_HONOURED = 4;

    /** And what the whole bargain costs when the named thing outlives it. */
    public static final int CORRUPTION_DEFAULTED = 38;

    public static final double NAME_RANGE = 28.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LONG_DEBT;
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
                        ctx.eye().add(ctx.look().scale(NAME_RANGE * 0.5D)), NAME_RANGE * 0.5D);
                if (candidates.isEmpty()) {
                    return CastResult.FAILED;
                }
                LivingEntity named = candidates.get(0);
                int term = Math.max(60, ctx.duration());
                SpellEffectEntity bond = SpellEffectEntity.spawn(ctx, ctx.feet(), term,
                        1.2F * Math.max(0.5F, ctx.size()), ctx.look());
                bond.setTarget(named);
                bond.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                PlayerMagicState state = ctx.state();
                state.passiveCounters().put(definition().id(), term);
                // Cleared here as well as in the slow tick, so the gift lands on the frame the
                // player pressed the button rather than up to half a second later.
                state.clearCooldowns();
                MagicStatusService.apply(named, MagicStatus.REVEALED, term, definition().id(), player);
                DarkService.corrupt(player, state, CORRUPTION_HONOURED);
                player.displayClientMessage(Component.translatable("message.magical.long_debt_named",
                        named.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE), true);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                        SoundSource.PLAYERS, 0.9F, 0.7F);
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
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            if (!(entity.target() instanceof LivingEntity named) || !named.isAlive()) {
                settle(level, player, state, true);
                entity.finish();
                return;
            }
            entity.setPos(player.getX(), player.getY() + 0.05D, player.getZ());
            if (entity.tickCount % 5 == 0) {
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, player.getX(), player.getY() + 1.2D, player.getZ(),
                        2, 0.35D, 0.35D, 0.35D, 0.0D);
            }
            if (entity.tickCount >= entity.life() - 1) {
                settle(level, player, state, false);
            }
        };
    }

    /** Closes the bargain either way, and is the only place the balloon payment is charged. */
    private static void settle(ServerLevel level, ServerPlayer player, PlayerMagicState state, boolean honoured) {
        state.passiveCounters().remove(MagicContent.LONG_DEBT.id());
        if (honoured) {
            player.displayClientMessage(Component.translatable("message.magical.long_debt_honoured")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM,
                    SoundSource.PLAYERS, 0.8F, 1.4F);
            state.sync(player);
            return;
        }
        DarkService.corrupt(player, state, CORRUPTION_DEFAULTED);
        player.displayClientMessage(Component.translatable("message.magical.long_debt_defaulted")
                .withStyle(ChatFormatting.DARK_RED), false);
        level.playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.PLAYERS, 1.0F, 0.5F);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
                24, 0.4D, 0.7D, 0.4D, 0.02D);
        state.sync(player);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.DARK)
                .circle(CircleScript.of(SchoolMaterial.DARK).emblem(EmblemId.TALLY).frame(14)
                        .band(GlyphKind.RUNE_BAND, 28, ColorRole.DIM)
                        .band(GlyphKind.TICK_BAND, 14, ColorRole.INK)
                        .stamps(StampId.BAR, 14).core(CoreKind.DISC_GLOW, ColorRole.HOT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.BOTH)
                .silhouette(Silhouette.filament(Silhouette.Form.CHAIN, FxKinds.Filament.RUNE_THREAD, 5, 0.04F).withRole(ColorRole.DIM))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.CLOCK_SPOKES, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.HEARTBEAT)
                .budget(2)
                .bounds(2.5F, 3.0F, 1.5F);
    }
}
