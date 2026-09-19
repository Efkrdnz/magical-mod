package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.entity.verse.VerseBodySpawner;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Authority of Mana's runtime: a press on Incantation k recites slot k of the wielder's
 * Grimoire. The core plans the shot ({@link Reciter}); this bills it, spawns it, sets the cooldown
 * and tells the wielder what comes next.
 *
 * <p>Sessions (the deck, the hand, the discard) live here, keyed by UUID, and are never saved:
 * rebuilt from the Grimoire on first use, dropped on logout, respawn and a change of dimension,
 * as {@code PileService} drops its Piles. A session that outlived its dimension is rebuilt too;
 * a cleared Authority needs no hook, because its emptied incantation refuses the next press.
 */
public final class IncantationService {

    public static final int SLOTS = Grimoire.SLOTS;

    private record Held(ResourceKey<Level> dimension, ReciteSession[] sessions) {
    }

    private static final Map<UUID, Held> SESSIONS = new HashMap<>();

    private IncantationService() {
    }

    // ------------------------------------------------------------------ the press

    /** The self-managed cast of Incantation {@code slot + 1}: everything after the registry's unlock and authority checks. */
    public static void recite(CastContext ctx, int slot) {
        ServerPlayer player = ctx.player();
        if (player == null || slot < 0 || slot >= SLOTS) {
            return;
        }
        PlayerMagicState state = ctx.state();
        ResourceLocation skillId = ctx.definition().id();
        Incantation incantation = state.grimoire().incantation(slot);
        if (incantation.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_empty", slot + 1), true);
            return;
        }
        if (state.isSkillOnCooldown(skillId)) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }
        MagicSkillResolvedStats stats = MagicSinService.adjustStatsBeforeCast(player, state, ctx.stats());
        ReciteSession session = session(player, state, slot);
        // The Reciter walks the deck in a loop bounded by ReciteCaps.MAX_STEPS and nests only as
        // deep as ReciteCaps.MAX_DEPTH, so the stack it needs is fixed whatever the incantation;
        // the step cap is what stops a Refrain of Ten reading a Recall All forever, not the stack.
        RecitePlan plan = Reciter.recite(session, incantation.breath(), state.mana(), stats.costScale(),
                new LevelReciteWorld(player, state, slot));
        // One call bills and refunds alike: a negative manaSpent (Wellspring) hands mana back and
        // is clamped to the pool by spendManaForSkill's own negative branch. The Reciter never
        // plans past state.mana(), so the bill cannot bounce; if a sin rule still refuses, the
        // shot is not spawned but the deck has moved - that is the price of trying to cast broke.
        boolean paid = MagicSinService.spendManaForSkill(player, state, plan.manaSpent());
        if (!paid) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            state.setSkillCooldown(skillId, plan.cooldownTicks());
            state.sync(player);
            return;
        }
        ServerLevel level = player.serverLevel();
        Vec3 look = ctx.aimDirection();
        Vec3 origin = player.getEyePosition().add(look.scale(0.4D)).add(0.0D, -0.1D, 0.0D);
        int spawned = VerseBodySpawner.spawn(level, player, plan.root(), origin, look, skillId).size();
        state.setSkillCooldown(skillId, plan.cooldownTicks());
        if (spawned > 0) {
            MagicSinService.afterSuccessfulCast(player, state, ctx.definition());
        }
        state.sync(player);
        readout(player, session, incantation, plan);
    }

    private static void readout(ServerPlayer player, ReciteSession session, Incantation incantation, RecitePlan plan) {
        ResourceLocation next = session.nextUnread();
        Component nextName = next == null
                ? Component.translatable("message.magical.incantation_rest")
                : Component.translatable("verse.magical." + next.getPath());
        player.displayClientMessage(Component.translatable("message.magical.incantation_next",
                nextName, session.unreadCount(), incantation.size()), true);
        if (plan.frayed()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_frayed"), false);
        }
    }

    // ------------------------------------------------------------------ writing

    /** Writes an incantation into a slot after the validator has passed it, and starts that slot's session over. */
    public static boolean setIncantation(ServerPlayer player, int slot, int breath, List<ResourceLocation> ids) {
        if (slot < 0 || slot >= SLOTS || ids == null) {
            return false;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        List<IncantationValidator.Finding> findings = IncantationValidator.problems(ids, breath, state.grimoire().known(), VerseContent.CATALOGUE);
        if (!findings.isEmpty()) {
            IncantationValidator.Finding first = findings.get(0);
            String where = first.id() == null ? String.valueOf(first.index()) : first.id().toString();
            player.displayClientMessage(Component.translatable("message.magical.incantation_rejected",
                    first.problem().name().toLowerCase(java.util.Locale.ROOT) + " at " + where), false);
            return false;
        }
        if (!state.grimoire().incantation(slot).write(ids, breath, VerseContent.CATALOGUE)) {
            return false;
        }
        resetSession(player.getUUID(), slot);
        state.sync(player);
        player.displayClientMessage(Component.translatable("message.magical.incantation_written", slot + 1, ids.size(), breath), false);
        return true;
    }

    /**
     * The plan a press would produce, without pressing: the uses are spent on a copy, the session is
     * left alone, and the world is the assumed one (nobody near, full health, a fixed random, no
     * price paid), so a preview moves nothing - not the Grimoire's toggle, not a heart.
     */
    public static RecitePlan preview(PlayerMagicState state, int slot) {
        Incantation copy = new Incantation();
        copy.copyFrom(state.grimoire().incantation(slot));
        ReciteSession session = ReciteSession.of(copy, VerseContent.CATALOGUE);
        return Reciter.recite(session, copy.breath(), state.mana(), 1.0D, new PreviewReciteWorld(state.grimoire(), slot, VerseContent.CATALOGUE));
    }

    /** {@code needle} and {@code magical:needle} both name the needle; anything unparsable makes the whole list null. */
    public static List<ResourceLocation> parseIds(List<String> raw) {
        if (raw == null) {
            return null;
        }
        List<ResourceLocation> ids = new ArrayList<>(raw.size());
        for (String s : raw) {
            if (s == null || s.isBlank()) {
                return null;
            }
            ResourceLocation id = s.contains(":") ? ResourceLocation.tryParse(s) : (ResourceLocation.isValidPath(s) ? VerseIds.of(s) : null);
            if (id == null) {
                return null;
            }
            ids.add(id);
        }
        return ids;
    }

    // ------------------------------------------------------------------ sessions

    private static ReciteSession session(ServerPlayer player, PlayerMagicState state, int slot) {
        ResourceKey<Level> dimension = player.level().dimension();
        Held held = SESSIONS.get(player.getUUID());
        if (held == null || !held.dimension().equals(dimension)) {
            held = new Held(dimension, new ReciteSession[SLOTS]);
            SESSIONS.put(player.getUUID(), held);
        }
        ReciteSession session = held.sessions()[slot];
        if (session == null) {
            session = ReciteSession.of(state.grimoire().incantation(slot), VerseContent.CATALOGUE);
            held.sessions()[slot] = session;
        }
        return session;
    }

    public static void resetSession(UUID wielder, int slot) {
        Held held = SESSIONS.get(wielder);
        if (held != null && slot >= 0 && slot < SLOTS) {
            held.sessions()[slot] = null;
        }
    }

    /** Logout, respawn, a change of dimension: the deck goes, the Grimoire stays. A cleared Authority needs no call - its emptied incantation refuses the next press. */
    public static void forget(UUID wielder) {
        SESSIONS.remove(wielder);
    }
}
