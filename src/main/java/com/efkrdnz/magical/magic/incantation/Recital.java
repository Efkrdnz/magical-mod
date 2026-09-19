package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * One press of one incantation: {@code gun.lua}'s globals and helper functions as one object.
 *
 * <p>Verse actions are handed this and call the same helpers the Lua actions call:
 * {@link #drawActions}, {@link #addProjectile}, the three triggered adds, {@link #call},
 * {@link #checkRecursion}, {@link #setDrawDisabled}, the piles. The private half is
 * {@code draw_action}, {@code play_action}, {@code move_hand_to_discarded} and
 * {@code _handle_reload}. Everything past a cap frays rather than throws.
 */
public final class Recital {

    /** {@code create_shot}: a state and a draw budget, and the bodies added while it was current. */
    static final class Frame {
        final ShotState state = new ShotState();
        final List<ProjectilePlan> bodies = new ArrayList<>();
        final int draw;

        Frame(int draw) {
            this.draw = draw;
        }
    }

    private final ReciteSession session;
    private final ReciteWorld world;
    private final double costScale;
    private final int manaAtStart;
    private final List<ReciteEvent> events = new ArrayList<>();
    private final Deque<Frame> parents = new ArrayDeque<>();
    private final Deque<Verse> running = new ArrayDeque<>();
    private Frame frame;
    private int mana;
    private int rest;
    private boolean dontDraw;
    private boolean forceStopDraws;
    private boolean reloading;
    private boolean startReload;
    private boolean gotProjectiles;
    private int steps;
    private int bodies;
    private boolean frayed;

    Recital(ReciteSession session, ReciteWorld world, int mana, double costScale) {
        this.session = session;
        this.world = world;
        this.mana = mana;
        this.manaAtStart = mana;
        this.costScale = costScale;
    }

    // ---- what a verse may touch ------------------------------------------------------------

    /** {@code c}: the state of the shot being written. */
    public ShotState state() {
        return frame.state;
    }

    public List<VerseCard> deck() {
        return session.deck();
    }

    public List<VerseCard> hand() {
        return session.hand();
    }

    public List<VerseCard> discard() {
        return session.discard();
    }

    public ReciteWorld world() {
        return world;
    }

    public int mana() {
        return mana;
    }

    public void setMana(int mana) {
        this.mana = mana;
    }

    /** {@code current_reload_time}: the rest carried until the next rest happens. */
    public int rest() {
        return rest;
    }

    public void addRest(int ticks) {
        rest += ticks;
    }

    public void setRest(int ticks) {
        rest = ticks;
    }

    /** How many payloads deep the current shot is; 0 at the root. */
    public int depth() {
        return parents.size();
    }

    public boolean drawDisabled() {
        return dontDraw;
    }

    /** {@code dont_draw_actions}: a copy that must not draw sets this around the call. */
    public void setDrawDisabled(boolean disabled) {
        dontDraw = disabled;
    }

    public boolean frayed() {
        return frayed;
    }

    /** {@code draw_actions(n, true)}: what a modifier, multicast or control verse calls. */
    public void drawActions(int howMany) {
        drawActions(howMany, true);
    }

    /** {@code add_projectile}: one body stamped with the state as it stands now. */
    public void addProjectile(VersePrototype prototype) {
        if (!countBody()) {
            return;
        }
        frame.bodies.add(new ProjectilePlan(prototype, runningId(), frame.state.copy(), PayloadKind.NONE, 0, null));
    }

    public void addProjectileLatch(VersePrototype prototype, int draw) {
        addTriggered(prototype, PayloadKind.LATCH, 0, draw);
    }

    public void addProjectileFuse(VersePrototype prototype, int fuseTicks, int draw) {
        addTriggered(prototype, PayloadKind.FUSE, fuseTicks, draw);
    }

    public void addProjectileEpitaph(VersePrototype prototype, int draw) {
        addTriggered(prototype, PayloadKind.EPITAPH, 0, draw);
    }

    /** {@code data.action(rec, iter)}: run a verse's function without drawing it. Free. */
    public int call(Verse verse, int recursion, int iteration) {
        event(ReciteEvent.Kind.COPIED, verse.id());
        return run(verse, recursion, iteration);
    }

    /** {@code check_recursion}: -1 refuses; a non-recursive verse passes the level through. */
    public int checkRecursion(Verse verse, int level) {
        if (verse != null && verse.recursive()) {
            return level >= ReciteCaps.RECURSION_LIMIT ? -1 : level + 1;
        }
        return level;
    }

    public void discardTop(int count) {
        discardAt(0, count);
    }

    /** {@code table.remove(deck, index)} into {@code discarded}, {@code count} times. */
    public void discardAt(int index, int count) {
        for (int i = 0; i < count; i++) {
            List<VerseCard> deck = session.deck();
            if (index < 0 || index >= deck.size()) {
                return;
            }
            VerseCard card = deck.remove(index);
            session.discard().add(card);
            event(ReciteEvent.Kind.DISCARDED, card.id());
        }
    }

    /** RESET's body: everything to the read pile; the first time, rebuild the unread pile and forbid overrun. */
    public void refreshPage() {
        session.discard().addAll(session.hand());
        session.discard().addAll(session.deck());
        session.replaceHand();
        session.replaceDeck();
        if (!forceStopDraws) {
            forceStopDraws = true;
            session.moveDiscardToDeck();
            session.orderDeck();
        }
    }

    public void event(ReciteEvent.Kind kind, ResourceLocation verse) {
        events.add(new ReciteEvent(kind, verse, parents.size()));
    }

    // ---- the machine ------------------------------------------------------------------------

    /** {@code _start_shot} + {@code _draw_actions_for_shot(true)}. */
    RecitePlan recite(int breath) {
        Frame root = new Frame(breath);
        frame = root;
        if (session.firstShot()) {
            session.orderDeck();
            session.setRestCarry(0);
            session.firstShotDone();
        }
        rest = session.restCarry();
        drawActions(breath, false);
        moveHandToDiscard();
        boolean rests = false;
        int restOut = 0;
        // The Lua asks "not reloading" here too; that reload is done by the C++ side, which we lack.
        if (session.deck().isEmpty() || startReload) {
            session.moveDiscardToDeck();
            session.orderDeck();
            rests = true;
            restOut = rest;
            rest = 0;
            startReload = false;
            event(ReciteEvent.Kind.REST, null);
        }
        session.setRestCarry(rest);
        return new RecitePlan(new ShotPlan(root.bodies, root.state), root.state.beatTicks(), restOut, rests,
                manaAtStart - mana, mana, frayed, events);
    }

    /** {@code draw_actions}. */
    private void drawActions(int howMany, boolean instantReload) {
        if (dontDraw || frayed) {
            return;
        }
        frame.state.setDrawManyCount(howMany);
        for (int i = 0; i < howMany; i++) {
            boolean ok = drawAction(instantReload);
            if (!ok) {
                while (!session.deck().isEmpty() && !frayed) {
                    if (drawAction(instantReload)) {
                        break;
                    }
                }
            }
            if (reloading || frayed) {
                return;
            }
        }
    }

    /** {@code draw_action}: false means "skipped, try the next"; true means played or nothing to play. */
    private boolean drawAction(boolean instantReload) {
        if (frayed) {
            return true;
        }
        if (session.deck().isEmpty()) {
            if (instantReload && !forceStopDraws) {
                session.moveDiscardToDeck();
                session.orderDeck();
                startReload = true;
                event(ReciteEvent.Kind.OVERRUN, null);
            } else {
                reloading = true;
                return true;
            }
        }
        if (session.deck().isEmpty()) {
            return true;
        }
        VerseCard card = session.deck().remove(0);
        int price = price(card.verse());
        if (price > mana) {
            session.discard().add(card);
            event(ReciteEvent.Kind.FALTER_MANA, card.id());
            return false;
        }
        if (card.spent()) {
            session.discard().add(card);
            event(ReciteEvent.Kind.FALTER_SPENT, card.id());
            return false;
        }
        mana -= price;
        playCard(card);
        return true;
    }

    /** A refund is never scaled; a price is, and rounds to the nearest whole mana. */
    private int price(Verse verse) {
        return verse.mana() > 0 ? (int) Math.round(verse.mana() * costScale) : verse.mana();
    }

    /** {@code play_action}: into the hand, run at level 0, iteration 1; a body-type verse marks the press. */
    private void playCard(VerseCard card) {
        session.hand().add(card);
        event(ReciteEvent.Kind.PLAYED, card.id());
        run(card.verse(), 0, 1);
        if (card.type().spawnsBodies()) {
            gotProjectiles = true;
        }
    }

    private int run(Verse verse, int recursion, int iteration) {
        if (frayed) {
            return VerseAction.NONE;
        }
        if (++steps > ReciteCaps.MAX_STEPS) {
            fray();
            return VerseAction.NONE;
        }
        running.push(verse);
        try {
            return verse.action().run(this, recursion, iteration);
        } finally {
            running.pop();
        }
    }

    private ResourceLocation runningId() {
        Verse verse = running.peek();
        return verse == null ? null : verse.id();
    }

    /** {@code add_projectile_trigger_*}: a fresh shot drawn now, spawned later by the carrier. */
    private void addTriggered(VersePrototype prototype, PayloadKind kind, int fuseTicks, int draw) {
        if (!countBody()) {
            return;
        }
        ShotState stamped = frame.state.copy();
        ResourceLocation verse = runningId();
        if (parents.size() >= ReciteCaps.MAX_DEPTH) {
            fray();
            frame.bodies.add(new ProjectilePlan(prototype, verse, stamped, PayloadKind.NONE, 0, null));
            return;
        }
        Frame parent = frame;
        Frame child = new Frame(draw);
        parents.push(parent);
        frame = child;
        drawActions(draw, true);
        frame = parents.pop();
        parent.bodies.add(new ProjectilePlan(prototype, verse, stamped, kind, fuseTicks,
                new ShotPlan(child.bodies, child.state)));
    }

    private boolean countBody() {
        if (bodies >= ReciteCaps.MAX_BODIES) {
            fray();
            return false;
        }
        bodies++;
        return true;
    }

    private void fray() {
        if (!frayed) {
            frayed = true;
            event(ReciteEvent.Kind.FRAYED, null);
        }
    }

    /** {@code move_hand_to_discarded}: uses are spent by a press with a body, or by a control or utility verse; a spent card leaves the piles. */
    private void moveHandToDiscard() {
        for (VerseCard card : session.hand()) {
            if (gotProjectiles || card.type().spendsUseAlone()) {
                card.consumeUse();
            }
            if (!card.spent()) {
                session.discard().add(card);
            }
        }
        session.replaceHand();
    }
}
