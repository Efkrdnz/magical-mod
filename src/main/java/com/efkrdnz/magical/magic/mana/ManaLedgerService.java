package com.efkrdnz.magical.magic.mana;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Keeping the book open, and writing in it.
 *
 * <p>Two abilities that are nothing apart, which is the shape the Authority of Space has - but this
 * is not that shape wearing a different coat. Space claims a volume and legislates inside it; there
 * is no volume here at all. The anchor is a <em>state of attention</em>: while the Ledger is open
 * every spell cast near the wielder is entered in it, and a writ may only ever name a spell the
 * book already holds. So the gate on the grammar is not "are you standing in the right place" but
 * "have you ever actually seen this done".
 */
public final class ManaLedgerService {

    private ManaLedgerService() {}

    /** Opens the book, or shuts it. What it recorded stays recorded either way. */
    public static boolean openOrClose(ServerPlayer player, PlayerMagicState state) {
        if (!state.hasAuthority(AuthorityContent.MANA) || !state.hasUnlocked(MagicContent.OPEN_LEDGER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        ManaLedger book = state.manaLedger();
        if (book.isOpen()) {
            book.close();
            state.sync(player);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.7F);
            player.displayClientMessage(Component.translatable("message.magical.ledger_closed", book.witnessed().size()), true);
            return true;
        }
        if (state.isSkillOnCooldown(MagicContent.OPEN_LEDGER.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = MagicContent.OPEN_LEDGER.resolve(state.tuningFor(MagicContent.OPEN_LEDGER.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        book.open();
        state.setSkillCooldown(MagicContent.OPEN_LEDGER.id(), stats.cooldownTicks());
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.4F);
        player.displayClientMessage(Component.translatable("message.magical.ledger_opened"), true);
        return true;
    }

    /**
     * Enters a cast in every open book near enough to have seen it.
     *
     * <p>Hung on {@code MagicSinService.afterSuccessfulCast}, which is the one line every successful
     * cast in the mod passes through. A refused cast is not witnessed, because nothing happened to
     * witness. Authority skills are never entered: they cannot be legislated, so recording them
     * would only fill the book with spells no writ may name.
     */
    public static void witnessCast(ServerPlayer caster, MagicSkillDefinition definition) {
        if (definition == null || MagicContent.isAuthoritySkill(definition.id())) {
            return;
        }
        double rangeSqr = ManaLedger.WITNESS_RANGE * ManaLedger.WITNESS_RANGE;
        for (ServerPlayer watcher : caster.serverLevel().players()) {
            if (watcher.distanceToSqr(caster) > rangeSqr) {
                continue;
            }
            PlayerMagicState state = watcher.getData(MagicalAttachments.MAGIC_STATE);
            if (state == null || !state.manaLedger().isOpen()) {
                continue;
            }
            if (state.manaLedger().witness(definition.id())) {
                state.sync(watcher);
                watcher.displayClientMessage(Component.translatable("message.magical.ledger_entered",
                        Component.translatable("skill.magical." + definition.id().getPath())), true);
                watcher.level().playSound(null, watcher.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS, 0.45F, 1.7F);
            }
        }
    }

    /**
     * Writes one writ, replaces the one on that line, or strikes it out.
     *
     * @param target a skill id, or {@code #SCHOOL} to bind every spell of that school at once. A
     *               school writ is the escalation the book is worth keeping for, and it is gated the
     *               same way: the wielder must have witnessed at least one spell of that school.
     */
    public static boolean inscribe(ServerPlayer player, String target, int aspectOrdinal, int operationOrdinal,
                                   int subjectOrdinal) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAuthority(AuthorityContent.MANA) || !state.hasUnlocked(MagicContent.WRIT.id())) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        WritAspect aspect = value(WritAspect.values(), aspectOrdinal);
        WritOperation operation = value(WritOperation.values(), operationOrdinal);
        WritSubject subject = value(WritSubject.values(), subjectOrdinal);
        if (aspect == null || operation == null || subject == null || target == null || target.isEmpty()) {
            return false;
        }
        Writ writ = resolve(state.manaLedger(), target, aspect, operation, subject);
        if (writ == null) {
            player.displayClientMessage(Component.translatable("message.magical.ledger_unwitnessed"), true);
            return false;
        }
        if (state.isSkillOnCooldown(MagicContent.WRIT.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        ManaLedger.Outcome outcome = state.manaLedger().write(writ);
        switch (outcome) {
            case FULL -> {
                player.displayClientMessage(Component.translatable("message.magical.ledger_full", ManaLedger.MAX_WRITS), true);
                return false;
            }
            case LOCKED -> {
                player.displayClientMessage(Component.translatable("message.magical.writ_locked"), true);
                return false;
            }
            case NOTHING_TO_STRIKE -> {
                player.displayClientMessage(Component.translatable("message.magical.writ_nothing_to_strike"), true);
                return false;
            }
            default -> { }
        }
        MagicSkillResolvedStats stats = MagicContent.WRIT.resolve(state.tuningFor(MagicContent.WRIT.id()));
        if (!MagicSinService.spendManaForSkill(player, state, stats.manaCost())) {
            // The writ was already entered, so take it back out rather than granting a free law.
            state.manaLedger().write(new Writ(writ.skill(), writ.school(), aspect, WritOperation.RESTORE, subject));
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        state.setSkillCooldown(MagicContent.WRIT.id(), stats.cooldownTicks());
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.9F, outcome == ManaLedger.Outcome.STRUCK ? 0.55F : 0.75F + operation.ordinal() * 0.1F);
        player.displayClientMessage(Component.translatable(outcome == ManaLedger.Outcome.STRUCK
                ? "message.magical.writ_struck" : "message.magical.writ_declared"), true);
        return true;
    }

    /** Null when the wielder is reaching for a spell or a school their book has never seen. */
    private static Writ resolve(ManaLedger book, String target, WritAspect aspect, WritOperation operation,
                                WritSubject subject) {
        if (target.startsWith("#")) {
            MagicSchool school = school(target.substring(1));
            if (school == null || !hasWitnessedSchool(book, school)) {
                return null;
            }
            return new Writ(null, school, aspect, operation, subject);
        }
        ResourceLocation skill = ResourceLocation.tryParse(target);
        if (skill == null || !book.hasWitnessed(skill)) {
            return null;
        }
        return new Writ(skill, null, aspect, operation, subject);
    }

    /** Every school the book has seen at least one spell of, in the order they were first seen. */
    public static java.util.List<MagicSchool> witnessedSchools(ManaLedger book) {
        java.util.List<MagicSchool> schools = new java.util.ArrayList<>();
        for (ResourceLocation id : book.witnessed()) {
            MagicSkillDefinition definition = MagicContent.get(id);
            if (definition != null && !schools.contains(definition.school())) {
                schools.add(definition.school());
            }
        }
        return schools;
    }

    private static boolean hasWitnessedSchool(ManaLedger book, MagicSchool school) {
        return witnessedSchools(book).contains(school);
    }

    private static MagicSchool school(String name) {
        for (MagicSchool school : MagicSchool.values()) {
            if (school.name().equalsIgnoreCase(name)) {
                return school;
            }
        }
        return null;
    }

    private static <T> T value(T[] values, int ordinal) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }
}
