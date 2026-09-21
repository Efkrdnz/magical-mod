package com.efkrdnz.magical.magic.causality;

import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The half of the Authority of Causality that knows what a level is, and decides nothing.
 *
 * <p>{@link Weaver} says what should happen; this goes and does it. The split is the same one
 * {@code Pile} and {@code PileService} make, and it is worth as much here: the grammar, the prices,
 * the order of two branches and the whole conservation rule are unit tests rather than a thing
 * somebody watched happen once in a dev client.
 *
 * <p>Three pieces of bookkeeping live here and none of them is saved. The <b>gate clocks</b> that
 * {@link Condition#ONCE_PER} reads, the <b>queue</b> of consequences an {@link Modifier#AFTER} has
 * put off, and the <b>severances</b> that {@link Effect#SEVER} has laid on other bodies. All three
 * are per-session by design: a wielder who logs out drops their timers, their pending consequences
 * and their hold on somebody else, and keeps the board, the ledger and the paradox, which are the
 * things they authored and the things they owe.
 */
public final class CausalityService {

    /** How deep one consequence may set off another before reality objects. */
    public static final int MAX_CASCADE = 3;

    /** What objecting costs, once per dispatch that went too deep. */
    public static final float CASCADE_PARADOX = 6.0F;

    /** How often the heartbeat is beaten out, and the unit every TOLL pin is measured in. */
    public static final int TOLL_INTERVAL = Cause.MIN_INTERVAL;

    /** How often the tripwire is swept, and how long a severance lasts before it lapses. */
    public static final int NEAR_INTERVAL = 10;
    public static final int SEVER_TICKS = 300;

    /** What a Bind does: rooted, and told about it. */
    public static final int BIND_AMPLIFIER = 6;

    /** How far behind the target a Step puts the wielder. */
    public static final double STEP_BEHIND = 1.6D;

    /** A consequence the board put off until later, and who is owed it. */
    private record Pending(UUID owner, CausalAction action, int otherId, long dueAt) {}

    /** A body whose cause and effect have been pulled apart, and how many more it may lose. */
    private record Severance(int left, long until) {}

    /** Per-session bookkeeping for one wielder. Dropped on logout and on a change of dimension. */
    private static final class Session {
        final Map<Integer, Long> gates = new HashMap<>();
        float lastLedger;
        int depth;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final List<Pending> QUEUE = new ArrayList<>();
    private static final Map<Integer, Severance> SEVERED = new HashMap<>();

    private CausalityService() {}

    // ---- the front door -----------------------------------------------------------------------

    /**
     * Runs the board for one event and answers with what the consequence has become.
     *
     * <p>The caller writes that number back wherever it came from - into the damage container, into
     * the heal amount - and everything else the board decided has already happened by the time this
     * returns, or been queued if something asked for it later.
     */
    public static float dispatch(ServerPlayer player, CausalEvent event) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!holds(state) || state.weave().empty() || state.weave().suspended()) {
            return event.magnitude();
        }
        long now = player.level().getGameTime();
        if (state.paradox().shut(now)) {
            return event.magnitude();
        }
        Session session = session(player);
        if (session.depth >= MAX_CASCADE) {
            // A consequence setting off a consequence setting off a consequence. Allowed, bounded,
            // and charged for: this is the instability from extreme manipulation the design doc
            // asks for, and the only paradox in the Authority that is not about a single effect.
            charge(player, state, CASCADE_PARADOX, now);
            return event.magnitude();
        }
        CausalEvent marked = flagMarked(player, state, event);
        LevelCausalWorld world = new LevelCausalWorld(player, state, session.gates);
        Resolution resolution = Weaver.resolve(state.weave(), marked, world);
        if (resolution.idle()) {
            return resolution.magnitude();
        }
        session.depth++;
        try {
            settle(player, state, marked, resolution, now, session);
        } finally {
            session.depth--;
        }
        return resolution.magnitude();
    }

    /**
     * Pays for a resolution and carries it out, in that order.
     *
     * <p>Mana and the ledger are billed for <em>every</em> action up front, including the ones a
     * delay has put off. A consequence leaves the ledger the moment the rule fires; only the landing
     * waits. Otherwise a delayed Spend would draw against a ledger that had moved on, and the
     * reading the board showed when the wielder built the chain would have been a guess.
     */
    private static void settle(ServerPlayer player, PlayerMagicState state, CausalEvent event,
            Resolution resolution, long now, Session session) {
        if (resolution.mana() > 0.0F) {
            state.spendMana(Math.round(resolution.mana()));
        }
        for (Integer gate : resolution.gatesPassed()) {
            session.gates.put(gate, now);
        }
        for (CausalAction action : resolution.actions()) {
            bill(state, action);
            if (action.delay() > 0) {
                QUEUE.add(new Pending(player.getUUID(), action, event.otherId(), now + action.delay()));
            } else {
                perform(player, state, action, event.otherId());
            }
        }
        charge(player, state, resolution.paradox(), now);
        noteLedger(session, state);
        if (resolution.starved()) {
            player.displayClientMessage(Component.translatable("message.magical.weave_starved"), true);
        }
        state.sync(player);
    }

    /** The ledger side of an action, applied at once so a delay cannot spend the same points twice. */
    private static void bill(PlayerMagicState state, CausalAction action) {
        switch (action.effect()) {
            case STORE -> state.ledger().store(action.magnitude());
            case SPEND, MEND, WARD -> state.ledger().draw(action.magnitude());
            default -> {
                // Everything else moves a consequence about the world rather than in or out of the
                // ledger, so there is nothing to bill and the landing is the whole of it.
            }
        }
    }

    /** Adds paradox, and owes the wielder a collapse if that was the point it tipped over at. */
    private static void charge(ServerPlayer player, PlayerMagicState state, float amount, long now) {
        if (amount <= 0.0F) {
            return;
        }
        Paradox.Rung before = state.paradox().rung();
        if (state.paradox().add(amount, now)) {
            collapse(player, state, now);
            return;
        }
        Paradox.Rung after = state.paradox().rung();
        if (after != before) {
            player.displayClientMessage(Component.translatable("message.magical.paradox_rung",
                    Component.translatable(after.translationKey())), true);
            player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE,
                    SoundSource.PLAYERS, 0.5F, after == Paradox.Rung.FRAYING ? 0.6F : 1.1F);
        }
    }

    /**
     * The board comes apart, and the ledger comes due.
     *
     * <p>Consequence held is consequence owed. With nothing left holding it, it lands on the body
     * that was holding it - the conservation rule keeping its promise at the exact moment it would
     * have been most convenient to break.
     */
    private static void collapse(ServerPlayer player, PlayerMagicState state, long now) {
        float owed = state.ledger().drain();
        state.paradox().collapse(now);
        if (owed > 0.0F) {
            MagicDamageService.hurt(player, player.damageSources().magic(), owed);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, 1.0F, 0.5F);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
                    40, 0.6D, 0.8D, 0.6D, 0.04D);
        }
        player.displayClientMessage(Component.translatable("message.magical.paradox_collapse",
                Math.round(owed), Paradox.COLLAPSE_TICKS / 20), true);
    }

    // ---- doing it -----------------------------------------------------------------------------

    /**
     * One consequence, landed.
     *
     * <p>Nothing here decides anything: the magnitude, the scope, the sign and the delay were all
     * settled by the engine. What is left is the translation into verbs the game has - and a
     * {@link Modifier#SHARED} dividing what it carries among however many bodies turned out to be
     * standing there, which is the one number that cannot be known until this moment.
     */
    private static void perform(ServerPlayer player, PlayerMagicState state, CausalAction action, int otherId) {
        List<LivingEntity> targets = targets(player, state, action, otherId);
        if (targets.isEmpty()) {
            return;
        }
        float each = action.shared() ? action.magnitude() / targets.size() : action.magnitude();
        for (LivingEntity target : targets) {
            land(player, state, action, target, each);
        }
        spark(player, action, targets.get(0));
    }

    private static void land(ServerPlayer player, PlayerMagicState state, CausalAction action,
            LivingEntity target, float amount) {
        switch (action.effect()) {
            case SPEND, PASS, RETURN, ECHO -> {
                if (action.turned()) {
                    target.heal(amount);
                } else {
                    MagicDamageService.hurt(target, player.damageSources().indirectMagic(player, player), amount);
                }
            }
            case MEND -> {
                if (action.turned()) {
                    MagicDamageService.hurt(target, player.damageSources().indirectMagic(player, player), amount);
                } else {
                    target.heal(amount);
                }
            }
            case WARD -> ward(target, amount);
            case BRAND -> brand(player, state, target);
            case STEP -> step(player, target);
            case HAUL -> haul(player, target);
            case BIND -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    action.param(), BIND_AMPLIFIER, false, true));
            case KINDLE -> target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), action.param()));
            case SEVER -> sever(player, target, action.param());
            case SIGH -> state.addMana(action.param());
            case STORE, ERASE -> {
                // Both were finished in the engine: the points are already off the consequence and
                // either banked or gone. There is nothing to put anywhere.
            }
        }
    }

    /** Barrier for a wielder of magic, plain absorption for anything that has no barrier to give. */
    private static void ward(LivingEntity target, float amount) {
        if (target instanceof ServerPlayer other) {
            PlayerMagicState theirs = other.getData(MagicalAttachments.MAGIC_STATE);
            theirs.addBarrier(Math.round(amount));
            theirs.sync(other);
            return;
        }
        target.setAbsorptionAmount(target.getAbsorptionAmount() + amount);
    }

    private static void brand(ServerPlayer player, PlayerMagicState state, LivingEntity target) {
        if (target == player) {
            return;
        }
        state.anchor().place(target.getId(), player.level().dimension().location().toString(),
                player.level().getGameTime());
        player.level().playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.PLAYERS, 0.6F, 1.6F);
    }

    /** Behind whatever it is looking at, facing its back. The one consequence that moves the wielder. */
    private static void step(ServerPlayer player, LivingEntity target) {
        if (target == player) {
            return;
        }
        Vec3 behind = target.position().subtract(target.getLookAngle().normalize().scale(STEP_BEHIND));
        player.teleportTo(behind.x, target.getY(), behind.z);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    private static void haul(ServerPlayer player, LivingEntity target) {
        if (target == player) {
            return;
        }
        Vec3 pull = player.position().subtract(target.position());
        if (pull.lengthSqr() < 1.0E-4D) {
            return;
        }
        target.setDeltaMovement(pull.normalize().scale(Math.min(1.8D, pull.length() * 0.35D)).add(0.0D, 0.18D, 0.0D));
        target.hurtMarked = true;
    }

    /**
     * Whose consequences stop landing, and for how many.
     *
     * <p>The Authority pointed at somebody else Authority, or at their sword, or at the arrow they
     * already loosed. It is the dearest thing on the board in weight and in paradox both, and it is
     * counted in consequences rather than in seconds so that a wielder cannot buy three seconds of
     * safety from a thing that only swings once.
     */
    private static void sever(ServerPlayer player, LivingEntity target, int count) {
        if (target == player) {
            return;
        }
        SEVERED.put(target.getId(), new Severance(count, player.level().getGameTime() + SEVER_TICKS));
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getEyeY(), target.getZ(),
                    12, 0.3D, 0.4D, 0.3D, 0.2D);
        }
    }

    /**
     * Whether this body has had its cause and effect pulled apart, spending one of the severances.
     *
     * <p>Asked by the damage hook, once per consequence that body tries to author. Returning true
     * means the hit does not happen at all, and the counter goes down whether or not that mattered.
     */
    public static boolean consumeSeverance(Entity author, long now) {
        if (author == null) {
            return false;
        }
        Severance severance = SEVERED.get(author.getId());
        if (severance == null) {
            return false;
        }
        if (now >= severance.until() || severance.left() <= 1) {
            SEVERED.remove(author.getId());
        } else {
            SEVERED.put(author.getId(), new Severance(severance.left() - 1, severance.until()));
        }
        return now < severance.until();
    }

    // ---- aiming -------------------------------------------------------------------------------

    /** Every body this consequence lands on, in a stable order. Empty when there is nobody to hit. */
    private static List<LivingEntity> targets(ServerPlayer player, PlayerMagicState state,
            CausalAction action, int otherId) {
        // Return is not aimed: it goes back to whoever authored the consequence, whatever the pin
        // says its scope is, because that is the whole of what the word means.
        if (action.effect() == Effect.RETURN) {
            return one(other(player, otherId));
        }
        if (action.effect() == Effect.SIGH) {
            return List.of(player);
        }
        return switch (action.scope()) {
            case SELF -> List.of(player);
            case OTHER -> one(other(player, otherId));
            case MARKED -> one(LevelCausalWorld.marked(player, state));
            case NEAREST -> one(nearest(player));
            case FIELD -> LevelCausalWorld.nearby(player, Scope.FIELD_RADIUS);
        };
    }

    private static List<LivingEntity> one(LivingEntity living) {
        return living == null ? List.of() : List.of(living);
    }

    private static LivingEntity other(ServerPlayer player, int otherId) {
        Entity entity = otherId < 0 ? null : player.level().getEntity(otherId);
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    private static LivingEntity nearest(ServerPlayer player) {
        LivingEntity best = null;
        double closest = Double.MAX_VALUE;
        for (LivingEntity living : LevelCausalWorld.nearby(player, Scope.NEAREST_RADIUS)) {
            double distance = player.distanceToSqr(living);
            if (distance < closest) {
                closest = distance;
                best = living;
            }
        }
        return best;
    }

    /** One thread of light from the wielder to wherever the consequence went. The only tell. */
    private static void spark(ServerPlayer player, CausalAction action, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = player.getEyePosition();
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 along = to.subtract(from);
        int steps = Math.max(2, Math.min(24, (int) along.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(along.scale(i / (double) steps));
            level.sendParticles(action.turned() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WAX_OFF,
                    at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    // ---- the clock ----------------------------------------------------------------------------

    /**
     * Everything the Authority does when nobody is pressing anything.
     *
     * <p>Which is most of it. The queue of delayed consequences comes due, the heartbeat is beaten,
     * the tripwire is swept, the ledger leaks, the paradox cools, and a ledger that went up last
     * tick sets off whatever was waiting on it.
     *
     * <p>{@link Cause#BRIM} is deliberately a tick behind the store that fed it rather than firing
     * inside it. A board that banks a hit and answers on the same tick would be re-entering the
     * engine from inside itself for the most ordinary thing anyone will build, and the cascade guard
     * exists for genuine recursion, not for the common case.
     */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        drainQueue(server, now);
        SEVERED.entrySet().removeIf(entry -> now >= entry.getValue().until());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            if (!holds(state)) {
                continue;
            }
            Session session = session(player);
            if (now % Ledger.DECAY_INTERVAL == 0) {
                state.ledger().decay();
            }
            int cooling = state.weave().suspended() ? Weave.SUSPENDED_COOLING : 1;
            if (now % Math.max(1, Paradox.COOL_INTERVAL / cooling) == 0) {
                state.paradox().cool(now);
            }
            if (state.weave().empty() || state.weave().suspended() || state.paradox().shut(now)) {
                noteLedger(session, state);
                continue;
            }
            float ledger = state.ledger().held();
            if (ledger > session.lastLedger) {
                session.lastLedger = ledger;
                dispatch(player, CausalEvent.of(Cause.BRIM));
            } else {
                session.lastLedger = ledger;
            }
            if (now % TOLL_INTERVAL == 0) {
                dispatch(player, CausalEvent.of(Cause.TOLL));
            }
            if (now % NEAR_INTERVAL == 0) {
                sweep(player, state);
            }
        }
    }

    /** The tripwire. Fires once per sweep for the nearest body, carrying how far away it is. */
    private static void sweep(ServerPlayer player, PlayerMagicState state) {
        boolean watching = false;
        for (CausalNode node : state.weave().causes()) {
            if (node.cause() == Cause.NEAR) {
                watching = true;
                break;
            }
        }
        if (!watching) {
            return;
        }
        LivingEntity closest = nearest(player);
        if (closest == null) {
            return;
        }
        float distance = (float) Math.sqrt(player.distanceToSqr(closest));
        if (distance > Cause.MAX_RANGE) {
            return;
        }
        dispatch(player, new CausalEvent(Cause.NEAR, distance, closest.getId(), 0));
    }

    private static void drainQueue(MinecraftServer server, long now) {
        if (QUEUE.isEmpty()) {
            return;
        }
        Iterator<Pending> pending = QUEUE.iterator();
        while (pending.hasNext()) {
            Pending due = pending.next();
            if (now < due.dueAt()) {
                continue;
            }
            pending.remove();
            ServerPlayer owner = server.getPlayerList().getPlayer(due.owner());
            if (owner == null) {
                continue;
            }
            PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
            perform(owner, state, due.action(), due.otherId());
            state.sync(owner);
        }
    }

    // ---- bookkeeping --------------------------------------------------------------------------

    public static boolean holds(PlayerMagicState state) {
        return state != null && state.hasAuthority(com.efkrdnz.magical.magic.AuthorityContent.CAUSALITY);
    }

    private static Session session(ServerPlayer player) {
        return SESSIONS.computeIfAbsent(player.getUUID(), ignored -> new Session());
    }

    /**
     * Only ever lowers the watermark.
     *
     * <p>So a Store that runs inside a dispatch still reads as a rise on the next tick and sets off
     * whatever is waiting on {@link Cause#BRIM}, while a Spend that emptied the ledger does not
     * leave a high watermark standing that would swallow the next rise.
     */
    private static void noteLedger(Session session, PlayerMagicState state) {
        session.lastLedger = Math.min(session.lastLedger, state.ledger().held());
    }

    /** Tells the event whether the other party is the one wearing the mark, once, for every pin. */
    private static CausalEvent flagMarked(ServerPlayer player, PlayerMagicState state, CausalEvent event) {
        if (!event.hasOther()) {
            return event;
        }
        LivingEntity marked = LevelCausalWorld.marked(player, state);
        return marked != null && marked.getId() == event.otherId() ? event.withFlag(CausalEvent.FROM_MARKED) : event;
    }

    /** Dropped on logout and on a change of dimension: timers, pending consequences, held bodies. */
    public static void forget(UUID wielder) {
        SESSIONS.remove(wielder);
        QUEUE.removeIf(pending -> pending.owner().equals(wielder));
    }

    public static void clear() {
        SESSIONS.clear();
        QUEUE.clear();
        SEVERED.clear();
    }

    // ---- the five things a wielder can press ----------------------------------------------------

    /**
     * The mark, onto whatever the crosshair is on.
     *
     * <p>One at a time, and it is the only door out of the wielder own causality. Everything
     * anchored on the board is dead weight until this lands, which the board says in as many words
     * rather than leaving them to wonder why a chain they were proud of never fires.
     */
    public static boolean anchor(ServerPlayer player, PlayerMagicState state) {
        LivingEntity target = aimed(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.magical.weave_no_target"), true);
            return false;
        }
        state.anchor().place(target.getId(), player.level().dimension().location().toString(),
                player.level().getGameTime());
        state.sync(player);
        player.level().playSound(null, target.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL,
                SoundSource.PLAYERS, 0.8F, 1.4F);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getEyeY(), target.getZ(),
                    16, 0.25D, 0.35D, 0.25D, 0.02D);
        }
        player.displayClientMessage(Component.translatable("message.magical.weave_anchored",
                target.getDisplayName(), Anchor.TICKS / 20), true);
        return true;
    }

    /** The one cause the wielder fires by hand, so a board can have a trigger of its own. */
    public static boolean decree(ServerPlayer player, PlayerMagicState state) {
        if (state.weave().empty()) {
            player.displayClientMessage(Component.translatable("message.magical.weave_empty"), true);
            return false;
        }
        if (state.weave().suspended()) {
            player.displayClientMessage(Component.translatable("message.magical.weave_suspended"), true);
            return false;
        }
        long now = player.level().getGameTime();
        if (state.paradox().shut(now)) {
            player.displayClientMessage(Component.translatable("message.magical.paradox_shut"), true);
            return false;
        }
        dispatch(player, CausalEvent.of(Cause.DECREE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7F, 1.2F);
        return true;
    }

    /**
     * The whole ledger, at once, on whatever is aimed at.
     *
     * <p>It charges no paradox at all, and that is the point: every point of it was taken out of the
     * world fairly and is only being put back. A wielder who banks well is rewarded with an honest
     * hammer; a wielder who erases their way through a fight has a gauge climbing instead.
     */
    public static boolean recompense(ServerPlayer player, PlayerMagicState state) {
        LivingEntity target = aimed(player);
        if (target == null) {
            target = LevelCausalWorld.marked(player, state);
        }
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.magical.weave_no_target"), true);
            return false;
        }
        float owed = state.ledger().drain();
        if (owed <= 0.0F) {
            player.displayClientMessage(Component.translatable("message.magical.weave_ledger_empty"), true);
            return false;
        }
        MagicDamageService.hurt(target, player.damageSources().indirectMagic(player, player), owed);
        state.sync(player);
        player.level().playSound(null, target.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.7F);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getEyeY(), target.getZ(),
                    (int) Math.min(60.0F, owed * 2.0F), 0.4D, 0.5D, 0.4D, 0.25D);
        }
        player.displayClientMessage(Component.translatable("message.magical.weave_recompense", Math.round(owed)), true);
        return true;
    }

    /**
     * The brake: every chain off, or every chain on again.
     *
     * <p>A board is not always an asset. One that returns consequence to its author is a liability
     * in a friend area of effect, and one that stores half of everything is a liability when the
     * wielder wants a heal to land whole. Paradox also cools {@link Weave#SUSPENDED_COOLING} times
     * as fast while it is off, which turns the gauge from a thing that is waited out into a thing
     * that is <em>decided</em> about.
     */
    public static boolean suspend(ServerPlayer player, PlayerMagicState state) {
        boolean off = !state.weave().suspended();
        state.weave().setSuspended(off);
        state.sync(player);
        player.level().playSound(null, player.blockPosition(),
                off ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 1.5F);
        player.displayClientMessage(Component.translatable(
                off ? "message.magical.weave_suspended" : "message.magical.weave_resumed"), true);
        return true;
    }

    /**
     * A board off the wire, checked and taken on.
     *
     * <p>Loaded into a scratch Weave first, which is what does most of the work: an unknown word
     * drops its pin, every number is clamped, and every wire goes back through {@code connect} so
     * a forged packet cannot hand the engine a loop or a wire running backwards. What is left to
     * check here is the budget, because {@code load} deliberately does not - an older save written
     * when the ceiling was higher should open rather than silently empty, but a packet claiming
     * the same thing is somebody trying it on.
     */
    public static boolean applyWeave(ServerPlayer player, net.minecraft.nbt.CompoundTag data) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!holds(state)) {
            player.displayClientMessage(Component.translatable("message.magical.authority_required"), true);
            return false;
        }
        Weave scratch = new Weave();
        scratch.load(data);
        if (scratch.weight() > Weave.CAPACITY || scratch.size() > Weave.MAX_NODES) {
            player.displayClientMessage(Component.translatable("message.magical.weave_over_budget",
                    scratch.weight(), Weave.CAPACITY), true);
            return false;
        }
        state.weave().copyFrom(scratch);
        session(player).gates.clear();
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.PLAYERS, 0.7F, 1.2F);
        player.displayClientMessage(Component.translatable("message.magical.weave_saved",
                scratch.weight(), Weave.CAPACITY), true);
        return true;
    }

    /** Whatever living thing the wielder is looking at, within an anchor reach. */
    private static LivingEntity aimed(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().normalize().scale(Anchor.REACH));
        LivingEntity best = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(eye, end).inflate(1.0D),
                candidate -> candidate.isAlive() && candidate != player)) {
            var clip = living.getBoundingBox().inflate(0.35D).clip(eye, end);
            if (clip.isEmpty()) {
                continue;
            }
            double distance = eye.distanceToSqr(clip.get());
            if (distance < nearest) {
                nearest = distance;
                best = living;
            }
        }
        return best;
    }
}
