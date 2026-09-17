package com.efkrdnz.magical.magic.mana;

import com.efkrdnz.magical.entity.domain.ManaWeaveEntity;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Putting the body down, and being the field instead.
 *
 * <p>Gated on the anchor the way every part of this Authority is: it works inside the wielder's own
 * Weave and nowhere else. Two things keep it from being plain invulnerability. The shell is a wall
 * rather than a door - stray outside the radius and the body comes back, wherever that leaves you.
 * And <b>every rule the wielder wrote applies to them in this state</b>, because a wielder who
 * legislated against everyone is part of everyone; that is not enforced here but by
 * {@link WeaveLaw}, which never asked who was casting in the first place.
 */
public final class ManaFormService {

    /** Long enough to matter, short enough that it is a decision rather than a way of living. */
    public static final int FORM_TICKS = 20 * 12;

    private ManaFormService() {}

    public static boolean toggle(ServerPlayer player, PlayerMagicState state) {
        if (!state.hasAuthority(AuthorityContent.MANA) || !state.hasUnlocked(MagicContent.MANA_FORM.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        if (state.inManaForm()) {
            end(player, state);
            return true;
        }
        ManaWeaveEntity weave = ManaAuthorityService.activeWeave(player, state);
        if (weave == null || !weave.covers(player)) {
            player.displayClientMessage(Component.translatable("message.magical.mana_form_needs_weave"), true);
            return false;
        }
        if (state.isSkillOnCooldown(MagicContent.MANA_FORM.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.MANA_FORM.resolve(state.tuningFor(MagicContent.MANA_FORM.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        state.setManaFormTicks(FORM_TICKS);
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
        ManaWeaveEntity weave = ManaAuthorityService.activeWeave(player, state);
        if (weave == null || !weave.covers(player)) {
            // The shell is the cage as much as the throne. Outside it there is no field to be.
            end(player, state);
            player.displayClientMessage(Component.translatable("message.magical.mana_form_expelled"), true);
            return;
        }
        state.setManaFormTicks(state.manaFormTicks() - 1);
        if (state.manaFormTicks() <= 0) {
            end(player, state);
            return;
        }
        applyBodilessEffects(player);
        if (state.manaFormTicks() % 20 == 0) {
            state.sync(player);
        }
    }

    private static void end(ServerPlayer player, PlayerMagicState state) {
        state.setManaFormTicks(0);
        state.sync(player);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.setInvulnerable(false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    /**
     * Unseen, and unfindable by anything that hunts a body. The damage half is refused in
     * {@code MagicGameplayEvents.onIncomingDamage} rather than here, because a cancelled event is
     * the only way to stop damage that never routes through a service.
     */
    private static void applyBodilessEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false, false));
        player.setInvulnerable(true);
    }
}
