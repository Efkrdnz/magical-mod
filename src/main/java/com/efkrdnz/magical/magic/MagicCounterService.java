package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.network.FirstPersonEffectPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class MagicCounterService {
    public static final int QTE_WINDOW_TICKS = 18;

    /**
     * Stands in for the counter skill on a prompt that needs none.
     *
     * <p>Not a registered skill, and deliberately so: it names the absence of one. Both the client
     * HUD and {@link #respond} branch on it before they try to resolve a definition.
     */
    public static final ResourceLocation FORCED_COUNTER =
            ResourceLocation.fromNamespaceAndPath("magical", "brace");

    private static final Map<UUID, CounterPrompt> ACTIVE_PROMPTS = new HashMap<>();
    private static final Map<UUID, Set<Integer>> RESOLVED_THREATS = new HashMap<>();
    private static final Map<UUID, Long> AEGIS_COUNTER_COOLDOWNS = new HashMap<>();

    private MagicCounterService() {}

    public static boolean offerCounter(ServerPlayer defender, CounterableSkillThreat threat, Vec3 clashPosition) {
        return offerCounter(defender, threat, clashPosition, QTE_WINDOW_TICKS);
    }

    public static boolean offerCounter(ServerPlayer defender, CounterableSkillThreat threat, Vec3 clashPosition, int windowTicks) {
        if (!(defender.level() instanceof ServerLevel level) || !threat.canBeCounteredBy(defender)) {
            return false;
        }
        int threatId = threat.counterThreatId();
        UUID defenderId = defender.getUUID();
        CounterPrompt active = ACTIVE_PROMPTS.get(defenderId);
        long now = level.getGameTime();
        if (active != null && active.threatId == threatId) {
            if (now <= active.deadlineTick) {
                return true;
            }
            clearPrompt(defender, threatId);
            return false;
        }
        if (resolvedThreats(defenderId).contains(threatId)) {
            return false;
        }

        PlayerMagicState state = defender.getData(MagicalAttachments.MAGIC_STATE);
        if (isValidGluttonyCounter(state)) {
            int safeWindowTicks = Math.max(threat.minimumCounterWindowTicks(), windowTicks);
            long deadline = now + safeWindowTicks;
            ACTIVE_PROMPTS.put(defenderId, new CounterPrompt(threatId, threat.counterSkillId(), MagicPassiveContent.SIN_GLUTTONY.id(), deadline, clashPosition));
            MagicalNetwork.sendCounterPrompt(defender, threatId, threat.counterSkillId(), MagicPassiveContent.SIN_GLUTTONY.id(), deadline, threat.counterDisplayWindowTicks(safeWindowTicks), threat.counterNameKey());
            return true;
        }

        Optional<MagicSkillDefinition> counter = bestCounterSkill(defender, state, threat);
        if (counter.isEmpty()) {
            return false;
        }

        MagicSkillDefinition counterSkill = counter.get();
        int safeWindowTicks = Math.max(threat.minimumCounterWindowTicks(), windowTicks);
        long deadline = now + safeWindowTicks;
        ACTIVE_PROMPTS.put(defenderId, new CounterPrompt(threatId, threat.counterSkillId(), counterSkill.id(), deadline, clashPosition));
        MagicalNetwork.sendCounterPrompt(defender, threatId, threat.counterSkillId(), counterSkill.id(), deadline, threat.counterDisplayWindowTicks(safeWindowTicks), threat.counterNameKey());
        return true;
    }

    /** Whether a Gluttony-only window would prompt this player at all, before anything is spawned. */
    public static boolean canDevour(ServerPlayer defender) {
        return isValidGluttonyCounter(defender.getData(MagicalAttachments.MAGIC_STATE));
    }

    /**
     * Prompts only a player whose Gluttony is enabled and off cooldown, and never falls back.
     *
     * <p>For the attacks that are meant to be dodged. {@link #offerCounter} would happily find some
     * skill with a favourable attribute, and {@link #offerForcedCounter} would accept the key from
     * anyone at all, and either of those turns a movement check into a reaction check. Gluttony is
     * the one answer allowed here, it costs a sixteen-second cooldown, and when it is not available
     * there is no prompt - which is the point: the attack has to be stepped out of.
     *
     * <p>Returns false rather than offering anything else, so a caller cannot accidentally chain it
     * into a softer prompt the way the ordinary window does.
     */
    public static boolean offerGluttonyCounter(ServerPlayer defender, CounterableSkillThreat threat,
            Vec3 clashPosition, int windowTicks) {
        if (!(defender.level() instanceof ServerLevel level) || !threat.canBeCounteredBy(defender)) {
            return false;
        }
        int threatId = threat.counterThreatId();
        UUID defenderId = defender.getUUID();
        long now = level.getGameTime();
        CounterPrompt active = ACTIVE_PROMPTS.get(defenderId);
        if (active != null && active.threatId == threatId) {
            if (now <= active.deadlineTick) {
                return true;
            }
            clearPrompt(defender, threatId);
            return false;
        }
        if (resolvedThreats(defenderId).contains(threatId)) {
            return false;
        }
        PlayerMagicState state = defender.getData(MagicalAttachments.MAGIC_STATE);
        if (!isValidGluttonyCounter(state)) {
            return false;
        }
        int safeWindowTicks = Math.max(threat.minimumCounterWindowTicks(), windowTicks);
        long deadline = now + safeWindowTicks;
        ACTIVE_PROMPTS.put(defenderId, new CounterPrompt(threatId, threat.counterSkillId(),
                MagicPassiveContent.SIN_GLUTTONY.id(), deadline, clashPosition));
        MagicalNetwork.sendCounterPrompt(defender, threatId, threat.counterSkillId(),
                MagicPassiveContent.SIN_GLUTTONY.id(), deadline,
                threat.counterDisplayWindowTicks(safeWindowTicks), threat.counterNameKey());
        return true;
    }

    /**
     * Prompts unconditionally: no attribute match, no tier floor, no counter skill required.
     *
     * <p>{@link #offerCounter} only prompts when the defender happens to own a skill whose
     * attribute beats the incoming one, and a tier-4 incoming additionally demands a tier-4 answer
     * off cooldown. That is the right shape for relief from another player's spell - you get out of
     * it with something you built for. It is the wrong shape for a boss telegraph, which most
     * players would simply never be shown.
     *
     * <p>So this one asks for the key alone. The existing rules are untouched; five shipped threats
     * depend on them.
     */
    public static boolean offerForcedCounter(ServerPlayer defender, CounterableSkillThreat threat,
            Vec3 clashPosition, int windowTicks) {
        if (!(defender.level() instanceof ServerLevel level) || !threat.canBeCounteredBy(defender)) {
            return false;
        }
        int threatId = threat.counterThreatId();
        UUID defenderId = defender.getUUID();
        long now = level.getGameTime();
        CounterPrompt active = ACTIVE_PROMPTS.get(defenderId);
        if (active != null && active.threatId == threatId) {
            if (now <= active.deadlineTick) {
                return true;
            }
            clearPrompt(defender, threatId);
            return false;
        }
        if (resolvedThreats(defenderId).contains(threatId)) {
            return false;
        }
        int safeWindowTicks = Math.max(threat.minimumCounterWindowTicks(), windowTicks);
        long deadline = now + safeWindowTicks;
        ACTIVE_PROMPTS.put(defenderId,
                new CounterPrompt(threatId, threat.counterSkillId(), FORCED_COUNTER, deadline, clashPosition));
        MagicalNetwork.sendCounterPrompt(defender, threatId, threat.counterSkillId(), FORCED_COUNTER,
                deadline, threat.counterDisplayWindowTicks(safeWindowTicks), threat.counterNameKey());
        return true;
    }

    public static boolean hasActivePrompt(ServerPlayer defender, CounterableSkillThreat threat) {
        if (!(defender.level() instanceof ServerLevel level)) {
            return false;
        }
        CounterPrompt prompt = ACTIVE_PROMPTS.get(defender.getUUID());
        return prompt != null && prompt.threatId == threat.counterThreatId() && level.getGameTime() <= prompt.deadlineTick;
    }

    public static boolean hasActivePrompt(ServerPlayer defender) {
        CounterPrompt prompt = ACTIVE_PROMPTS.get(defender.getUUID());
        return prompt != null && defender.level().getGameTime() <= prompt.deadlineTick;
    }

    /** Retire a discarded short-lived threat without clearing a newer/unrelated prompt. */
    public static void releaseThreat(ServerPlayer defender, CounterableSkillThreat threat) {
        CounterPrompt prompt = ACTIVE_PROMPTS.get(defender.getUUID());
        if (prompt != null && prompt.threatId == threat.counterThreatId()) {
            ACTIVE_PROMPTS.remove(defender.getUUID());
            MagicalNetwork.sendCounterClear(defender, threat.counterThreatId());
        }
        Set<Integer> resolved = RESOLVED_THREATS.get(defender.getUUID());
        if (resolved != null) resolved.remove(threat.counterThreatId());
    }

    public static void expirePrompt(ServerPlayer defender, CounterableSkillThreat threat) {
        clearPrompt(defender, threat.counterThreatId());
    }

    public static void respond(ServerPlayer defender, int threatId) {
        if (!(defender.level() instanceof ServerLevel level)) {
            return;
        }
        UUID defenderId = defender.getUUID();
        CounterPrompt prompt = ACTIVE_PROMPTS.get(defenderId);
        if (prompt == null || prompt.threatId != threatId || level.getGameTime() > prompt.deadlineTick) {
            clearPrompt(defender, threatId);
            return;
        }

        Entity entity = level.getEntity(threatId);
        if (!(entity instanceof CounterableSkillThreat threat) || !threat.canBeCounteredBy(defender)) {
            clearPrompt(defender, threatId);
            return;
        }

        if (!threat.validateCounterResponse(defender)) {
            clearPrompt(defender, threatId);
            return;
        }
        if (FORCED_COUNTER.equals(prompt.counterSkillId)) {
            // Nothing to validate and nothing to spend: the press was the whole answer.
            ACTIVE_PROMPTS.remove(defenderId);
            resolvedThreats(defenderId).add(threatId);
            threat.onForcedCounter(level, defender, prompt.clashPosition);
            celebrateParry(defender, FORCED_PARRY_COLOR, threat);
            notifyParried(threat, FORCED_PARRY_COLOR);
            return;
        }

        PlayerMagicState state = defender.getData(MagicalAttachments.MAGIC_STATE);
        if (MagicPassiveContent.SIN_GLUTTONY.id().equals(prompt.counterSkillId)) {
            if (!isValidGluttonyCounter(state)) {
                clearPrompt(defender, threatId);
                return;
            }
            MagicSkillDefinition incoming = MagicContent.get(threat.counterSkillId());
            int tier = Math.max(1, threat.counterTier() + 1);
            int manaGain = incoming == null ? 12 : Math.max(8, incoming.baseManaCost() / 2 + tier * 5);
            state.addMana(manaGain);
            state.addManaCharge(Math.min(5, Math.max(1, tier)), 20 * (12 + tier * 8));
            state.setGluttonyCooldown(20 * 16);
            state.sync(defender);
            ACTIVE_PROMPTS.remove(defenderId);
            resolvedThreats(defenderId).add(threatId);
            threat.onGluttonyCountered(level, defender, prompt.clashPosition);
            celebrateParry(defender, MagicPassiveContent.SIN_GLUTTONY.color(), threat);
            notifyParried(threat, incoming == null ? FORCED_PARRY_COLOR : incoming.color());
            defender.displayClientMessage(Component.translatable("message.magical.gluttony_devoured", Component.translatable(incoming == null ? "passive.magical.sin_gluttony" : incoming.nameKey())), true);
            return;
        }

        MagicSkillDefinition counterSkill = MagicContent.get(prompt.counterSkillId);
        if (counterSkill == null || !isValidCounter(defender, state, counterSkill, threat)) {
            clearPrompt(defender, threatId);
            return;
        }

        MagicSkillResolvedStats stats = counterSkill.resolve(state.tuningFor(counterSkill.id()));
        if (!state.spendMana(stats.manaCost())) {
            clearPrompt(defender, threatId);
            return;
        }

        if (isAegisProtectionCounter(counterSkill)) {
            AEGIS_COUNTER_COOLDOWNS.put(defenderId, level.getGameTime() + 24L);
        } else {
            state.setSkillCooldown(counterSkill.id(), stats.cooldownTicks());
        }
        state.sync(defender);
        ACTIVE_PROMPTS.remove(defenderId);
        resolvedThreats(defenderId).add(threatId);
        threat.onCountered(level, defender, counterSkill, prompt.clashPosition);
        celebrateParry(defender, counterSkill.color(), threat);
        notifyParried(threat, MagicContent.get(threat.counterSkillId()) == null
                ? FORCED_PARRY_COLOR
                : MagicContent.get(threat.counterSkillId()).color());
        defender.displayClientMessage(Component.translatable("message.magical.counter_success", Component.translatable(counterSkill.nameKey())), true);
    }

    public static void tickPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        UUID playerId = player.getUUID();
        CounterPrompt prompt = ACTIVE_PROMPTS.get(playerId);
        if (prompt != null && level.getGameTime() > prompt.deadlineTick) {
            clearPrompt(player, prompt.threatId);
        }
        RESOLVED_THREATS.computeIfPresent(playerId, (id, threats) -> threats.isEmpty() ? null : threats);
    }

    /** Gold, for a parry that had no counter skill behind it - the key press was the whole answer. */
    private static final int FORCED_PARRY_COLOR = 0xFFD166;

    /** Long enough to register, short enough not to be in the way of the next attack. */
    private static final int PARRY_OVERLAY_TICKS = 10;
    private static final float PARRY_OVERLAY_ALPHA = 0.5F;
    private static final int PARRY_SHAKE_TICKS = 6;
    private static final float PARRY_SHAKE = 0.4F;

    /** The hold. Three ticks of locked camera is what makes a parry land rather than merely happen. */
    private static final int PARRY_FREEZE_TICKS = 3;
    private static final float PARRY_FOV_KICK = -4.0F;

    /**
     * What the player who parried feels.
     *
     * <p>The clash entity is seen by everyone nearby; this is only for the one who earned it, and it
     * is most of why a parry reads as a parry. A ring pulse in the colour of whatever held, a short
     * shake, and a camera hold - the same trick every fighting game uses to say "that connected".
     */
    private static void celebrateParry(ServerPlayer defender, int counterColor, CounterableSkillThreat threat) {
        MagicalNetwork.playFirstPersonEffect(defender,
                FirstPersonEffectPayload.impact(counterColor, PARRY_OVERLAY_TICKS, PARRY_OVERLAY_ALPHA,
                                PARRY_SHAKE_TICKS, PARRY_SHAKE, PARRY_FREEZE_TICKS, PARRY_FOV_KICK)
                        .withOverlay(FxKinds.Overlay.SHOCK_RING.id(), 40, FirstPersonEffectPayload.OMNI));
    }

    /**
     * And what the caster feels, when the caster is a player.
     *
     * <p>Being parried should be information, not silence. Deliberately weaker than the defender's
     * and in the attack's own colour, so the two sides of the same clash do not feel identical.
     */
    private static void notifyParried(CounterableSkillThreat threat, int incomingColor) {
        if (threat.counterOwner() instanceof ServerPlayer caster) {
            MagicalNetwork.playFirstPersonEffect(caster,
                    FirstPersonEffectPayload.impact(incomingColor, 8, 0.28F, 4, 0.2F, 0, 0.0F)
                            .withOverlay(FxKinds.Overlay.CRACKED_GLASS.id(), 24, FirstPersonEffectPayload.OMNI));
        }
    }

    public static void spawnClash(ServerLevel level, Vec3 position, int incomingColor, int counterColor) {
        level.addFreshEntity(SkillClashEffectEntity.create(level, position, incomingColor, counterColor, 24));
        // Four layers in the order the ear wants them: the strike, the boom under it, the attack
        // breaking, and a tail to ring out on. The old stack led with the boom and closed on a
        // beacon powering down, which said "something switched off" rather than "you stopped it".
        level.playSound(null, position.x, position.y, position.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.75F, 1.45F);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 0.7F);
        level.playSound(null, position.x, position.y, position.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.55F);
    }

    private static Optional<MagicSkillDefinition> bestCounterSkill(ServerPlayer defender, PlayerMagicState state, CounterableSkillThreat threat) {
        return state.unlockedSkills().stream()
                .map(MagicContent::get)
                .filter(skill -> skill != null && isValidCounter(defender, state, skill, threat))
                .max(Comparator
                        .comparingInt(MagicSkillDefinition::tier)
                        .thenComparingInt(skill -> -state.skillCooldown(skill.id()))
                        .thenComparingInt(skill -> -MagicContent.skillIndex(skill.id())));
    }

    private static boolean isValidCounter(ServerPlayer defender, PlayerMagicState state, MagicSkillDefinition skill, CounterableSkillThreat threat) {
        MagicSkillDefinition incoming = MagicContent.get(threat.counterSkillId());
        boolean judgement = MagicContent.GABRIEL_JUDGEMENT.id().equals(threat.counterSkillId());
        boolean aegisProtection = isAegisProtectionCounter(skill);
        if (aegisProtection) {
            if (!canAegisCounterIncoming(incoming) || aegisCounterCooling(defender)) {
                return false;
            }
        } else {
            if (judgement) {
                return false;
            }
            if (!skill.attribute().counters(threat.counterAttribute())) {
                return false;
            }
            if (!matchesForbiddenDepth(skill, threat.counterTier())) {
                return false;
            }
        }
        if (threat.counterTier() >= TierFive.APEX_TIER && !isTierFive(skill)) {
            return false;
        }
        if (!aegisProtection && state.isSkillOnCooldown(skill.id())) {
            return false;
        }
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        return state.mana() >= stats.manaCost();
    }

    /**
     * Forbidden magic has to be answered from at least its own depth: a counter of tier X answers
     * tier -X, and everything shallower than that too. In display tiers, tier 5 answers -4 and it
     * answers -1 as well - the strongest holy magic in the game stopping a novice blood spell is
     * the reading that makes sense, and matching exactly left tier -1 with precisely one legal
     * answer in the whole registry.
     *
     * <p>Nothing answers -5. That costs nothing to enforce: it would want a tier 5 counter, the
     * ladder stops at {@link TierFive#APEX_TIER}, and so Authority is uncounterable without a
     * single line saying so. Which is also why this is a floor and not "deeper always wins" - the
     * ceiling is the load-bearing half.
     *
     * <p>Positive threats are unaffected, and so are boss strikes: {@code counterTier()} is
     * overridden to report the attack's own power tier, so an attack that merely borrows a
     * forbidden spell's identity is not treated as forbidden magic.
     */
    // Package-private rather than private: this is the whole rule for answering forbidden magic,
    // and every path that would exercise it from outside needs a live ServerPlayer.
    static boolean matchesForbiddenDepth(MagicSkillDefinition counter, int incomingTier) {
        return incomingTier >= 0 || counter.tier() >= -incomingTier;
    }

    private static boolean isAegisProtectionCounter(MagicSkillDefinition skill) {
        return MagicContent.GABRIEL_ULTIMATE_PROTECTION.id().equals(skill.id());
    }

    /**
     * The one layer Sovereign Aegis refuses. Aegis answers by authority rather than by opposition -
     * it skips both the attribute check and {@link #matchesForbiddenDepth} - so it carries its own
     * exclusions instead: the authority skills by id, and this tier by depth.
     *
     * <p>Deliberately {@code != } rather than {@code >=}: three tier -5 skills (singularity,
     * dimensional_guillotine, soul_valley) sit outside {@code AUTHORITY_SKILLS}, so a floor here
     * would quietly stop Aegis answering them. That is a balance decision, not a cleanup.
     */
    private static final int AEGIS_REFUSED_TIER = -4;

    private static boolean canAegisCounterIncoming(MagicSkillDefinition incoming) {
        return incoming == null || (!MagicContent.isAuthoritySkill(incoming.id())
                && incoming.tier() != AEGIS_REFUSED_TIER);
    }

    private static boolean aegisCounterCooling(ServerPlayer defender) {
        Long until = AEGIS_COUNTER_COOLDOWNS.get(defender.getUUID());
        if (until == null) {
            return false;
        }
        if (!(defender.level() instanceof ServerLevel level) || level.getGameTime() < until) {
            return true;
        }
        AEGIS_COUNTER_COOLDOWNS.remove(defender.getUUID());
        return false;
    }

    /** The apex test, kept in one place so the counter rule and the ward rule cannot diverge. */
    private static boolean isTierFive(MagicSkillDefinition skill) {
        return TierFive.is(skill);
    }

    private static boolean isValidGluttonyCounter(PlayerMagicState state) {
        return state.isSinEnabled(MagicPassiveContent.SIN_GLUTTONY.id()) && state.gluttonyCooldownTicks() <= 0;
    }

    private static void clearPrompt(UUID defenderId, int threatId) {
        CounterPrompt prompt = ACTIVE_PROMPTS.get(defenderId);
        if (prompt != null && prompt.threatId == threatId) ACTIVE_PROMPTS.remove(defenderId);
        resolvedThreats(defenderId).add(threatId);
    }

    private static void clearPrompt(ServerPlayer defender, int threatId) {
        clearPrompt(defender.getUUID(), threatId);
        MagicalNetwork.sendCounterClear(defender, threatId);
    }

    private static Set<Integer> resolvedThreats(UUID defenderId) {
        return RESOLVED_THREATS.computeIfAbsent(defenderId, id -> new HashSet<>());
    }

    private record CounterPrompt(int threatId, ResourceLocation incomingSkillId, ResourceLocation counterSkillId, long deadlineTick, Vec3 clashPosition) {}
}
