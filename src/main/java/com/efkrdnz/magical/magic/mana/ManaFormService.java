package com.efkrdnz.magical.magic.mana;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Putting the body down, and being the pool instead.
 *
 * <p>There is no region to stand in and nothing to stray out of. The wielder stops having a body
 * and starts having a <em>quantity</em>: while the form holds, the mana bar is simultaneously the
 * health bar and the clock. It drains on its own, every blow that lands is subtracted from it
 * instead of from the flesh, and the instant it empties the body is back. Nothing kills a wielder
 * in this state; running out does.
 *
 * <p>That makes it interlock with the rest of the Authority without a single line saying so. A
 * wielder who legislated COST upward for everyone legislated it upward for themselves, so every
 * spell they cast out of the form eats the seconds they are standing on. The Ledger never asked who
 * was casting.
 */
public final class ManaFormService {

    /** Mana burned per tick simply for existing as mana. A deeper pool is a longer form. */
    public static final int DRAIN_PER_TICK = 2;

    /** Mana spent per point of damage that would otherwise have landed on the body. */
    public static final float MANA_PER_DAMAGE = 6.0F;

    /** The form refuses to open on fumes: a quarter of the pool, or it is not worth being. */
    public static final float MIN_ENTRY_FRACTION = 0.25F;

    private ManaFormService() {}

    public static boolean toggle(ServerPlayer player, PlayerMagicState state) {
        if (!state.hasAuthority(AuthorityContent.MANA) || !state.hasUnlocked(MagicContent.MANA_FORM.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        if (state.inManaForm()) {
            end(player, state, "message.magical.mana_form_ended");
            return true;
        }
        if (state.isSkillOnCooldown(MagicContent.MANA_FORM.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        if (state.mana() < Math.round(state.maxMana() * MIN_ENTRY_FRACTION)) {
            player.displayClientMessage(Component.translatable("message.magical.mana_form_too_shallow"), true);
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.MANA_FORM.resolve(state.tuningFor(MagicContent.MANA_FORM.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        state.setManaFormTicks(1);
        state.setSkillCooldown(MagicContent.MANA_FORM.id(), stats.cooldownTicks());
        state.sync(player);
        applyBodilessEffects(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.9F);
        player.displayClientMessage(Component.translatable("message.magical.mana_form_entered"), true);
        return true;
    }

    /** Runs once per server tick for a wielder who has put the body down. */
    public static void tick(ServerPlayer player, PlayerMagicState state) {
        if (!state.inManaForm()) {
            return;
        }
        if (state.mana() < DRAIN_PER_TICK) {
            state.setMana(0);
            end(player, state, "message.magical.mana_form_spent");
            return;
        }
        state.setMana(state.mana() - DRAIN_PER_TICK);
        state.setManaFormTicks(state.manaFormTicks() + 1);
        applyBodilessEffects(player);
        if (state.manaFormTicks() % 10 == 0) {
            state.sync(player);
        }
    }

    /**
     * Pays a blow out of the pool rather than the body. True when the damage was absorbed and the
     * event should be cancelled.
     *
     * <p>A blow bigger than what is left does not carry over into the flesh: it empties the pool and
     * throws the wielder out of the form. Being caught mid-collapse is the risk, not being killed
     * through it.
     */
    public static boolean absorb(ServerPlayer player, PlayerMagicState state, float damage) {
        if (!state.inManaForm()) {
            return false;
        }
        int bill = Math.max(1, Math.round(damage * MANA_PER_DAMAGE));
        if (bill >= state.mana()) {
            state.setMana(0);
            end(player, state, "message.magical.mana_form_shattered");
            return true;
        }
        state.setMana(state.mana() - bill);
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.7F, 1.6F);
        return true;
    }

    private static void end(ServerPlayer player, PlayerMagicState state, String message) {
        state.setManaFormTicks(0);
        state.sync(player);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
        player.displayClientMessage(Component.translatable(message), true);
    }

    /**
     * Unseen, and quick, because a field has no weight to drag. Invulnerability is deliberately
     * <em>not</em> set: {@code Entity.setInvulnerable} short-circuits {@code hurt} before the damage
     * event fires, and the whole design is that the blow lands somewhere - on the pool.
     */
    private static void applyBodilessEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, true, false, false));
    }
}
