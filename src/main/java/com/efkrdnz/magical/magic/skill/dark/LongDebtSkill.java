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
import com.efkrdnz.magical.magic.visual.sigil.Sigil;
import com.efkrdnz.magical.magic.visual.sigil.SigilInk;
import com.efkrdnz.magical.magic.visual.sigil.SigilMark;
import com.efkrdnz.magical.magic.visual.sigil.Sigils;
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

    /** Ticks between reprints of the crown that marks the named thing for as long as the debt runs. */
    private static final int NAMED_MARK_INTERVAL = 10;

    /**
     * Glyphs in the crown written over the named thing. The whole ring is written at the naming;
     * each reprint writes only the share of it the term has left, so the crown is a tally that
     * thins to nothing as the debt comes due. Drawn only.
     */
    private static final int CROWN_GLYPHS = 8;

    /** The crown in the sigil library: the eight runes, one to a slot, at a size and a half. */
    private static final SigilMark CROWN = SigilMark.of(Sigil.runes()).scale(1.5F);

    /** Sculk souls let off the named body as the catalyst blooms. Drawn only. */
    private static final int BLOOM_SOULS = 5;

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
                // The name lands where it was aimed: a catalyst's bloom of souls lifting off the
                // thing that now has to die, and the whole of its crown written over its head, so
                // the one body the bargain is about is marked the moment it is.
                bloom(ctx.level(), named);
                crown(ctx.level(), named, ctx.profile().color(ColorRole.BASE), CROWN_GLYPHS);
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
                return MobCastProfile.NONE;
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
                // The debt's pulse runs along the thread at the debtor's feet. It used to pop at the
                // chest, a few inches under a first-person camera, where every pop was drawn large
                // across the bottom of the view for the whole of the fight the gift is for.
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, player.getX(), player.getY() + 0.2D, player.getZ(),
                        2, 0.3D, 0.08D, 0.3D, 0.0D);
            }
            if (entity.tickCount % NAMED_MARK_INTERVAL == 0) {
                // And what is owed hangs over the named thing. Nothing else in the world says which
                // body the timer is on, and it is the only one in the fight that has to die - so the
                // crown is kept written, and keeps only as many glyphs as the term has left.
                int left = Math.max(0, entity.life() - entity.tickCount);
                int glyphs = (int) Math.ceil(CROWN_GLYPHS * left / (double) Math.max(1, entity.life()));
                crown(level, named, entity.profile().color(ColorRole.BASE), glyphs);
            }
            if (entity.tickCount >= entity.life() - 1) {
                settle(level, player, state, false);
            }
        };
    }

    /**
     * A catalyst's bloom off the named body: sculk souls thrown straight up out of it at even
     * bearings. Handed a random spread instead, they hung on the target as a still clump of white
     * squares for the first second of the bargain.
     */
    private static void bloom(ServerLevel level, LivingEntity named) {
        double ring = named.getBbWidth() * 0.3D;
        double chest = named.getY() + named.getBbHeight() * 0.6D;
        for (int i = 0; i < BLOOM_SOULS; i++) {
            double angle = Math.PI * 2.0D * i / BLOOM_SOULS;
            double cx = Math.cos(angle);
            double cz = Math.sin(angle);
            level.sendParticles(ParticleTypes.SCULK_SOUL, named.getX() + cx * ring, chest, named.getZ() + cz * ring,
                    0, cx * 0.3D, 1.0D, cz * 0.3D, 0.05D);
        }
    }

    /**
     * The debt's crown: glyphs written in a level ring just over the named thing's head, each in a
     * fixed slot that always wears the same rune, so a reprint lands the same glyph where the last
     * one was and the ring holds its shape while it lifts and fades. A slot the term has used up is
     * simply not written again, so the ring empties one glyph at a time. Inked from the skill's own
     * colour - a pale core in its violet - which reads against sky and ground.
     */
    private static void crown(ServerLevel level, LivingEntity named, int rgb, int glyphs) {
        if (glyphs <= 0) {
            return;
        }
        Sigils.crown(level, named, CROWN.ink(SigilInk.from(rgb)), CROWN_GLYPHS, glyphs);
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
        // Out of the ground round the debtor and up past them, rather than born in a cloud about
        // their head where a first-person view sees nothing else.
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.3D, player.getZ(),
                24, 0.5D, 0.2D, 0.5D, 0.02D);
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
                // FUNNEL, not LIFT: a lift raises both circles 1.2 blocks, which puts the one at the
                // feet round the caster's chest and the one at the eyes over their head - two rings
                // across a first-person view. Folding them shut in place is the bargain sealing.
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.CLOCK_SPOKES, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.HEARTBEAT)
                .budget(2)
                .bounds(2.5F, 3.0F, 1.5F);
    }
}
