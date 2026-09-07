package com.efkrdnz.magical.magic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.efkrdnz.magical.entity.ForgeEffectEntity;
import com.efkrdnz.magical.entity.ForgeStrikeEntity;
import com.efkrdnz.magical.entity.SovereignAegisEntity;
import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeBrandService;
import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeForms;
import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ForgeTempers;
import com.efkrdnz.magical.forge.ForgeIds;
import com.efkrdnz.magical.forge.StrikeLoadout;
import com.efkrdnz.magical.forge.ForgeWeaponFlags;
import com.efkrdnz.magical.forge.chain.ForgeProgram;
import com.efkrdnz.magical.forge.chain.Payload;
import com.efkrdnz.magical.forge.chain.ForgeStep;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.FormDefinition;
import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.TemperDefinition;
import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.strike.ComboState;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.StrikeSpec;
import com.efkrdnz.magical.forge.strike.TemperStats;
import com.efkrdnz.magical.network.ForgeComboSyncPayload;
import com.efkrdnz.magical.network.ForgeStrikePayload;
import com.efkrdnz.magical.network.MagicalNetwork;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Owns the per-player combo for forged weapons: which step of the chain the next press lands on,
 * how long its window and recovery still have to run, the charge telegraph, the guard window, and
 * the queue of delayed echo repeats.
 *
 * <p>An unforged weapon never reaches this class - it stays pure vanilla melee. A forged one keeps
 * vanilla too: a press that has an entity under the crosshair goes through vanilla's own attack
 * path, and a press that whiffs is snapped onto a nearby enemy with {@code Player#attack}, so
 * durability, enchantments, crits and {@code AttackEntityEvent} all fire exactly once.</p>
 */
public final class ForgeComboService {

    private static final double AIM_ASSIST_CONE_DOT = 0.5; // a 60-degree half-cone
    private static final int TELEGRAPH_LIFE = 40;
    private static final float GUARD_CHARGE_REDUCTION = 0.50f;
    private static final float GUARD_HEAVY_CHARGE_REDUCTION = 0.60f;
    /**
     * Ticks a charge guard may run for. A hold stops buying anything at
     * {@link ForgeStrikeMath#MAX_CHARGE_TICKS}, so a guard that outlives that is protecting a charge
     * nobody is still paying for; the telegraph's own 40-tick life is a visual, not a budget.
     */
    private static final int GUARD_CHARGE_TICKS = ForgeStrikeMath.MAX_CHARGE_TICKS;
    /**
     * How long after arming a charge guard the next one may be armed, when the first was never spent.
     *
     * <p>Twice the guard's own life, so an un-spent guard covers at most half the time. A charge
     * released into an actual heavy clears this outright (see {@link #onChargeRelease}), which is
     * the whole legitimate path: charge, release, strike, charge again with the guard back up. What
     * it stops is the client that sends CHARGE_BEGIN forever and never releases, which used to hold
     * a permanent 50% (60% on HEAVY) incoming-damage reduction while moving and attacking freely.</p>
     */
    private static final int GUARD_REARM_TICKS = 2 * GUARD_CHARGE_TICKS;
    /**
     * Minimum ticks between two accepted CHARGE_BEGINs. A legitimate second begin cannot arrive
     * sooner: it needs a release, a fresh press, and another hold to the weapon's threshold, and the
     * shortest threshold in the table is HEAVY's six. Without it, each packet discarded and respawned
     * the following telegraph entity - a spawn plus a removal to every nearby player, at packet rate.
     */
    private static final int CHARGE_BEGIN_COOLDOWN_TICKS = 4;
    private static final float GUARD_HIT_REDUCTION = 0.30f;
    private static final int GUARD_HIT_TICKS = 6;
    private static final int OFFLINE_SWEEP_TICKS = 100;
    private static final double SLAM_FORWARD_FRACTION = 0.6;
    private static final double WAVE_LEAD = 1.0;
    private static final int UNKNOWN_COLOR = 0xD8E4FF;
    private static final int NO_TARGET = -1;

    /** Ticks a forked press adds to its recovery for each form past the first. */
    private static final int FORK_RECOVERY_PER_EXTRA = 4;

    private record PendingEcho(UUID owner, long fireTick, StrikeSpec spec, ForgedWeapon weapon, Vec3 origin,
            Vec3 direction, ResourceLocation elementId, ResourceLocation formId) {}

    private record Guard(long untilTick, float reduction) {}

    private static final Map<UUID, ComboState> STATES = new HashMap<>();
    private static final List<PendingEcho> ECHOES = new ArrayList<>();
    private static final Map<UUID, Guard> CHARGE_GUARDS = new HashMap<>();
    private static final Map<UUID, Guard> HIT_GUARDS = new HashMap<>();
    /** Tick each player's last CHARGE_BEGIN was accepted on, for the rate limit. */
    private static final Map<UUID, Long> LAST_CHARGE_BEGIN = new HashMap<>();
    /** Tick each player's standing charge guard was armed on; removed once the charge is spent. */
    private static final Map<UUID, Long> CHARGE_GUARD_ARMED_AT = new HashMap<>();

    private ForgeComboService() {}

    // --- lifecycle ------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        ForgeBrandService.tick(server);
        fireDueEchoes(server, now);
        if (server.getTickCount() % OFFLINE_SWEEP_TICKS == 0) {
            dropOfflinePlayers(server);
        }
    }

    /** Drops everything a player was carrying: on logout, on respawn, and on a dimension change. */
    public static void reset(ServerPlayer player) {
        UUID id = player.getUUID();
        discardTelegraph(player);
        STATES.remove(id);
        CHARGE_GUARDS.remove(id);
        HIT_GUARDS.remove(id);
        LAST_CHARGE_BEGIN.remove(id);
        CHARGE_GUARD_ARMED_AT.remove(id);
        ECHOES.removeIf(echo -> echo.owner().equals(id));
    }

    private static void fireDueEchoes(MinecraftServer server, long now) {
        Iterator<PendingEcho> pending = ECHOES.iterator();
        while (pending.hasNext()) {
            PendingEcho echo = pending.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(echo.owner());
            if (owner == null) {
                pending.remove();
                continue;
            }
            if (now < echo.fireTick()) {
                continue;
            }
            pending.remove();
            spawnEcho(owner, echo);
        }
    }

    private static void spawnEcho(ServerPlayer owner, PendingEcho echo) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<ElementDefinition> element = ForgeElements.get(echo.elementId());
        Optional<FormDefinition> form = ForgeForms.get(echo.formId());
        if (element.isEmpty() || form.isEmpty()) {
            return;
        }
        ForgeStrikeEntity.spawn(level, owner, echo.spec().asEcho(), echo.weapon(), element.get(), form.get(),
                echo.origin(), echo.direction(), true);
    }

    private static void dropOfflinePlayers(MinecraftServer server) {
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            online.add(player.getUUID());
        }
        STATES.keySet().removeIf(id -> !online.contains(id));
        CHARGE_GUARDS.keySet().removeIf(id -> !online.contains(id));
        HIT_GUARDS.keySet().removeIf(id -> !online.contains(id));
        LAST_CHARGE_BEGIN.keySet().removeIf(id -> !online.contains(id));
        CHARGE_GUARD_ARMED_AT.keySet().removeIf(id -> !online.contains(id));
        ECHOES.removeIf(echo -> !online.contains(echo.owner()));
    }

    // --- state read by the rest of the combat core ------------------------------------------

    /**
     * Recorded from {@code AttackEntityEvent}: the one target vanilla hit on this press. The client
     * sends its attack before its strike packet, so on the very first swing there is no state yet -
     * seed an idle one from the weapon in hand rather than losing the target and charging it both
     * the vanilla hit and an uncorrected strike.
     */
    public static void notePrimaryHit(ServerPlayer player, LivingEntity target) {
        ComboState state = STATES.get(player.getUUID());
        if (state == null) {
            state = ForgedWeapons.get(player.getMainHandItem())
                    .map(weapon -> ComboState.idle(weapon.hashCode(), Math.max(1, weapon.compiled().length())))
                    .orElse(null);
        }
        if (state != null) {
            STATES.put(player.getUUID(), state.withPrimaryTarget(target.getId()));
        }
    }

    public static int primaryTargetId(ServerPlayer player) {
        ComboState state = STATES.get(player.getUUID());
        return state == null ? NO_TARGET : state.primaryTargetId();
    }

    /** The fraction of incoming damage a GUARD weapon is currently soaking, 0 when none is up. */
    public static float guardReduction(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        return Math.max(activeReduction(CHARGE_GUARDS.get(id), now), activeReduction(HIT_GUARDS.get(id), now));
    }

    /** A GUARD strike that landed refreshes the short post-press window. */
    public static void noteGuardHit(ServerPlayer player, long now) {
        putGuard(HIT_GUARDS, player.getUUID(), now + GUARD_HIT_TICKS, GUARD_HIT_REDUCTION, now);
    }

    private static float activeReduction(Guard guard, long now) {
        return guard != null && now < guard.untilTick() ? guard.reduction() : 0.0f;
    }

    private static void putGuard(Map<UUID, Guard> guards, UUID id, long untilTick, float reduction, long now) {
        Guard current = guards.get(id);
        if (current != null && now < current.untilTick()) {
            guards.put(id, new Guard(Math.max(untilTick, current.untilTick()),
                    Math.max(reduction, current.reduction())));
            return;
        }
        guards.put(id, new Guard(untilTick, reduction));
    }

    // --- input ------------------------------------------------------------------------------

    public static void onStrike(ServerPlayer player, ForgeStrikePayload payload) {
        switch (payload.kind()) {
            case ForgeStrikePayload.PRESS -> fire(player, payload.whiff(), false, 0.0f);
            case ForgeStrikePayload.CHARGE_BEGIN -> onChargeBegin(player);
            case ForgeStrikePayload.CHARGE_RELEASE -> onChargeRelease(player, payload);
            case ForgeStrikePayload.CHARGE_CANCEL -> onChargeCancel(player);
            default -> {
                // an unknown kind from a mismatched client: ignore it rather than guessing
            }
        }
    }

    /**
     * The single path a strike takes, whether it came from a tap or a released charge. There is no
     * attack-strength gate: the combo's own recovery and rate limit are what pace it.
     */
    private static void fire(ServerPlayer player, boolean whiff, boolean heavy, float chargeFraction) {
        ItemStack stack = player.getMainHandItem();
        Optional<ForgedWeapon> forged = ForgedWeapons.getOrMigrate(stack);
        if (forged.isEmpty() || !ForgeMaterials.isForgeable(stack) || !(player.level() instanceof ServerLevel level)) {
            return; // unforged: pure vanilla, no strike at all
        }
        if (SovereignAegisEntity.isInsideOffenseBlockingSanctuary(player)) {
            return;
        }
        ForgedWeapon weapon = forged.get();
        // Data from another build - an unknown element, an unknown form, or no forms at all - falls
        // back to plain vanilla rather than substituting a default the player never forged.
        Optional<ElementDefinition> element = ForgeElements.get(weapon.element());
        ForgeProgram program = weapon.compiled();
        if (element.isEmpty() || program.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        ComboState state = currentState(player, weapon, now);
        if (state.rateLimited(now) || state.recovering(now)) {
            // The vanilla hit this press came with is not ours to correct. Drop the target so a
            // later whiffing press cannot snapshot it and discount a strike that followed no swing.
            STATES.put(player.getUUID(), state.withPrimaryTarget(NO_TARGET));
            return;
        }
        int index = Math.min(state.index(), program.length() - 1);
        ForgeStep step = program.stepAt(index);
        List<FormDefinition> forms = new ArrayList<>(step.width());
        for (String formId : step.forms()) {
            ForgeForms.get(ForgeIds.id(formId)).ifPresent(forms::add);
        }
        if (forms.isEmpty()) {
            return;
        }
        float forkScale = ForgeStrikeMath.forkScale(forms.size());
        List<StrikeSpec> specs = new ArrayList<>(forms.size());
        for (FormDefinition member : forms) {
            specs.add(resolve(player, weapon, element.get(), member, step, stack, state, index, heavy,
                    chargeFraction).withDamageScale(forkScale));
        }
        // Stored before the vanilla hit so notePrimaryHit has a state to write the target into.
        STATES.put(player.getUUID(), state);
        if (whiff) {
            snapVanillaHit(player, specs.get(0).reach());
        }
        launch(level, player, weapon, element.get(), forms, specs, step.payload(),
                STATES.getOrDefault(player.getUUID(), state), now);
    }

    /**
     * How long a press costs when it fires several forms at once: the slowest member, plus a little
     * for each extra one.
     *
     * <p>Charging only the slowest would make a fork strictly free, and summing them would make it
     * unusable. What a fork really costs is the glyph budget it spends and the deck positions it
     * burns - this is the smaller, per-press part of the price.
     */
    private static int groupRecovery(List<StrikeSpec> specs) {
        int slowest = 0;
        for (StrikeSpec spec : specs) {
            slowest = Math.max(slowest, spec.recoveryTicks());
        }
        return slowest + FORK_RECOVERY_PER_EXTRA * (specs.size() - 1);
    }

    private static void launch(ServerLevel level, ServerPlayer player, ForgedWeapon weapon, ElementDefinition element,
            List<FormDefinition> forms, List<StrikeSpec> specs, Optional<Payload> payload, ComboState state,
            long now) {
        Vec3 look = lookOf(player);
        StrikeSpec spec = specs.get(0);
        int primary = primaryTargetId(player);
        for (int i = 0; i < specs.size(); i++) {
            StrikeSpec member = specs.get(i);
            Vec3 origin = originFor(player, member.family(), look, member.reach());
            // Only the lead strike claims the single vanilla hit this press came with.
            ForgeStrikeEntity.spawn(level, player, member, weapon, element, forms.get(i), origin, look, false,
                    i == 0 ? primary : StrikeLoadout.NO_PRIMARY_TARGET, payload);
        }
        Vec3 origin = originFor(player, spec.family(), look, spec.reach());
        ForgeSounds.play(level, player, spec.family());
        int recovery = groupRecovery(specs);
        long windowEnd = ForgeStrikeMath.windowEnd(now, recovery, temperOf(weapon), spec.mods());
        ComboState advanced = state.afterStrike(now, recovery, windowEnd);
        STATES.put(player.getUUID(), advanced);
        if (spec.has(ForgeModifierKind.GUARD)) {
            putGuard(HIT_GUARDS, player.getUUID(), now + GUARD_HIT_TICKS, GUARD_HIT_REDUCTION, now);
        }
        scheduleEcho(player, spec, weapon, element, forms.get(0), origin, look, now);
        syncCombo(player, advanced, element, now);
    }

    private static void onChargeBegin(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        Optional<ForgedWeapon> forged = ForgedWeapons.getOrMigrate(stack);
        if (forged.isEmpty() || !ForgeMaterials.isForgeable(stack) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (SovereignAegisEntity.isInsideOffenseBlockingSanctuary(player)) {
            return;
        }
        ForgedWeapon weapon = forged.get();
        long now = level.getGameTime();
        if (chargeBeginRateLimited(player.getUUID(), now)) {
            return;
        }
        LAST_CHARGE_BEGIN.put(player.getUUID(), now);
        discardTelegraph(player);
        int color = ForgeElements.get(weapon.element()).map(ElementDefinition::primaryColor).orElse(UNKNOWN_COLOR);
        ForgeEffectEntity telegraph = ForgeEffectEntity.following(level, player, ForgeEffectStyle.CHARGE_TELEGRAPH,
                color, TELEGRAPH_LIFE);
        STATES.put(player.getUUID(),
                currentState(player, weapon, now).withPress(now).withTelegraph(telegraph.getId()));
        armChargeGuard(player, weapon, now);
    }

    /**
     * Arms the charge guard, if the weapon carries GUARD and the last one was actually spent or
     * released early.
     *
     * <p>A guard is spent by a release that reached the threshold and fired the heavy, or freed by a
     * cancel that arrived before the guard's own expiry - both clear {@link #CHARGE_GUARD_ARMED_AT}
     * (see {@link #onChargeCancel}). One left to lapse, or dropped by a release too short to fire or
     * a cancel that waited for the guard's natural expiry, keeps its stamp and blocks the next
     * arming for {@link #GUARD_REARM_TICKS}, so a client that begins charges it never spends - or
     * only ever cancels after banking the full window - can hold the reduction at most half the time
     * instead of permanently.</p>
     */
    private static void armChargeGuard(ServerPlayer player, ForgedWeapon weapon, long now) {
        if (!ForgeWeaponFlags.of(weapon).has(ForgeModifierKind.GUARD)) {
            return;
        }
        Long armedAt = CHARGE_GUARD_ARMED_AT.get(player.getUUID());
        if (armedAt != null && now >= armedAt && now - armedAt < GUARD_REARM_TICKS) {
            return;
        }
        // A HEAVY temper is a shield as much as a hammer: guarding through its long wind-up
        // soaks more than the same rune would on a keen or swift weapon.
        float reduction = hasHeavyTemper(weapon) ? GUARD_HEAVY_CHARGE_REDUCTION : GUARD_CHARGE_REDUCTION;
        CHARGE_GUARD_ARMED_AT.put(player.getUUID(), now);
        putGuard(CHARGE_GUARDS, player.getUUID(), now + GUARD_CHARGE_TICKS, reduction, now);
    }

    private static boolean chargeBeginRateLimited(UUID id, long now) {
        Long last = LAST_CHARGE_BEGIN.get(id);
        return last != null && now >= last && now - last < CHARGE_BEGIN_COOLDOWN_TICKS;
    }

    /**
     * A release only becomes a heavy if the hold really reached the weapon's charge threshold. The
     * client-reported hold is clamped against the server's own view of when the charge started, so
     * a lying client cannot buy a full charge for free.
     */
    private static void onChargeRelease(ServerPlayer player, ForgeStrikePayload payload) {
        Optional<ForgedWeapon> forged = ForgedWeapons.getOrMigrate(player.getMainHandItem());
        if (forged.isEmpty() || !(player.level() instanceof ServerLevel level)) {
            onChargeCancel(player);
            return;
        }
        long now = level.getGameTime();
        ComboState state = STATES.get(player.getUUID());
        long pressTick = state == null || state.pressTick() < 0 ? now : state.pressTick();
        int held = Math.min(Math.max(0, payload.chargeTicks()), (int) (now - pressTick) + 2);
        held = Math.min(held, ForgeStrikeMath.MAX_CHARGE_TICKS);
        int threshold = ForgeStrikeMath.chargeThreshold(temperOf(forged.get()));
        discardTelegraph(player);
        CHARGE_GUARDS.remove(player.getUUID());
        if (held < threshold) {
            // Never reached a charge: the press it started already did its work, and the guard this
            // charge was given goes unspent - so its re-arm stamp stands rather than being cleared.
            return;
        }
        CHARGE_GUARD_ARMED_AT.remove(player.getUUID());
        fire(player, false, true, ForgeStrikeMath.chargeFraction(held, threshold));
    }

    private static void onChargeCancel(ServerPlayer player) {
        discardTelegraph(player);
        UUID id = player.getUUID();
        Guard guard = CHARGE_GUARDS.remove(id);
        // The line above already kills the guard this arm gave, so a cancel never banks anything
        // the way an un-spent charge could; it only ever cost the player a telegraph and the 4-tick
        // CHARGE_BEGIN rate limit for nothing. Clearing the re-arm stamp too means a screen-open, a
        // mining start or a weapon swap mid-charge (see ForgeComboInput) no longer costs the *next*
        // charge its guard as well - the 3-second blackout an earlier pass used to leave behind -
        // but only when the cancel is genuinely early: the guard this arm gave has not yet run its
        // own course (checked against that guard's own tracked expiry, so this needs no second
        // rate limit). A client that instead lets the guard reach its natural expiry and only then
        // cancels has already banked the same full mitigation window a spent charge would have, so
        // the stamp stands and GUARD_REARM_TICKS still applies - closing the residual gap the
        // previous pass flagged: cancelling right at (or after) the guard's own expiry no longer
        // buys an immediate re-arm. A cancel with no guard on record (an ungeared weapon, or one a
        // release already consumed) has nothing to prove it was early, so the stamp is left as-is
        // rather than assumed clear.
        if (guard != null && ForgeStrikeMath.guardStillRunning(guard.untilTick(), player.level().getGameTime())) {
            CHARGE_GUARD_ARMED_AT.remove(id);
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private static ComboState currentState(ServerPlayer player, ForgedWeapon weapon, long now) {
        int hash = weapon.hashCode();
        int chainLength = Math.max(1, weapon.compiled().length());
        ComboState state = STATES.get(player.getUUID());
        if (state == null) {
            return ComboState.idle(hash, chainLength);
        }
        if (!state.matches(hash) || state.windowExpired(now)) {
            // See ComboState.idleCarryingPrimaryTarget: a lapsed window or a weapon swap must not
            // drop the target notePrimaryHit already wrote onto the state being replaced here.
            return state.idleCarryingPrimaryTarget(hash, chainLength);
        }
        return state;
    }

    /**
     * The element is part of the resolve because one Art changes the strike rather than what the
     * strike does to a body: GALE's Gale Step is worth "+1 reach" on a light flurry, and reach is
     * settled here, before the strike is ever in the air.
     */
    private static StrikeSpec resolve(ServerPlayer player, ForgedWeapon weapon, ElementDefinition element,
            FormDefinition form, ForgeStep step, ItemStack stack, ComboState state, int index, boolean heavy,
            float chargeFraction) {
        WeaponClass weaponClass = ForgeMaterials.weaponClass(stack).orElse(WeaponClass.SWORD);
        return ForgeStrikeMath.resolve(form.stats(), temperOf(weapon), weaponClass, step.mods(),
                weapon.grade(), weapon.quality(), (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE),
                heavy, chargeFraction, state.finisherAt(index), index, element.kind());
    }

    private static void scheduleEcho(ServerPlayer player, StrikeSpec spec, ForgedWeapon weapon,
            ElementDefinition element, FormDefinition form, Vec3 origin, Vec3 direction, long now) {
        if (!spec.has(ForgeModifierKind.ECHO) || !ForgeStrikeMath.echoAllowed(spec.family(), spec.heavy())) {
            return;
        }
        ECHOES.add(new PendingEcho(player.getUUID(), now + ForgeStrikeMath.ECHO_DELAY_TICKS, spec, weapon, origin,
                direction, element.id(), form.id()));
    }

    private static void syncCombo(ServerPlayer player, ComboState state, ElementDefinition element, long now) {
        int windowLeft = (int) Math.max(0L, state.windowEndTick() - now);
        int readyIn = (int) Math.max(0L, state.readyTick() - now);
        MagicalNetwork.sendForgeCombo(player, new ForgeComboSyncPayload(state.index(), state.chainLength(),
                windowLeft, readyIn, element.primaryColor()));
    }

    private static void discardTelegraph(ServerPlayer player) {
        ComboState state = STATES.get(player.getUUID());
        if (state == null || state.telegraphEntityId() < 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity telegraph = level.getEntity(state.telegraphEntityId());
        if (telegraph != null) {
            telegraph.discard();
        }
        STATES.put(player.getUUID(), state.withoutTelegraph());
    }

    private static void snapVanillaHit(ServerPlayer player, double reach) {
        LivingEntity snap = nearestInCone(player, reach, AIM_ASSIST_CONE_DOT);
        if (snap == null) {
            return;
        }
        player.attack(snap); // full vanilla melee: durability, enchants, crits, AttackEntityEvent
        player.resetAttackStrengthTicker();
    }

    private static boolean hasHeavyTemper(ForgedWeapon weapon) {
        return weapon.temper().filter(ForgeTempers.HEAVY.id()::equals).isPresent();
    }

    private static TemperStats temperOf(ForgedWeapon weapon) {
        return weapon.temper().flatMap(ForgeTempers::get).map(TemperDefinition::stats).orElse(TemperStats.NONE);
    }

    private static Vec3 lookOf(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        return look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
    }

    /** Where the strike is born: on the eye for anchored forms, ahead for a wave, underfoot for a slam. */
    private static Vec3 originFor(ServerPlayer player, FormFamily family, Vec3 look, float reach) {
        return switch (family) {
            case SLAM -> player.position().add(look.scale(reach * SLAM_FORWARD_FRACTION));
            case WAVE -> player.getEyePosition().add(look.scale(WAVE_LEAD));
            default -> player.getEyePosition();
        };
    }

    /** Ported from the melee system this replaces: the nearest attackable body in view. */
    private static LivingEntity nearestInCone(ServerPlayer player, double range, double coneDot) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = lookOf(player);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB area = player.getBoundingBox().inflate(range);
        for (Entity entity : player.serverLevel().getEntities(player, area,
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != player
                        && candidate.isAttackable())) {
            Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || to.lengthSqr() < 1.0E-6 || to.normalize().dot(look) < coneDot) {
                continue;
            }
            if (distance < bestDistance && player.hasLineOfSight(entity)) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }
}
