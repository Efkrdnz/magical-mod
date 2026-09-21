package com.efkrdnz.magical.magic.causality;

/**
 * Consequence the wielder is holding rather than letting happen.
 *
 * <p>The one resource in the Authority that is not mana, and the reason it is an Authority rather
 * than a spell list. {@link Effect#STORE} takes a share of a consequence out of the world and puts
 * it here; {@link Effect#SPEND}, {@link Effect#MEND} and {@link Effect#WARD} take it back out
 * again, as harm, as health or as barrier. <b>Nothing creates ledger out of nothing.</b> Every
 * point in here was a point that did not land on somebody, which is what makes storing a defence
 * and releasing an attack the same act seen twice, and what makes the whole thing legible: the
 * number on the board is a debt the world is owed.
 *
 * <p>It leaks. A consequence held is a consequence being argued with, and the argument is not free:
 * one point per {@link #DECAY_INTERVAL} ticks drains away on its own. That is what stops a wielder
 * banking a hundred points over an afternoon and opening a fight with them.
 *
 * <p>Pure, and a plain float behind an interface that cannot go out of bounds. Held on
 * {@code PlayerMagicState} and saved with it, because it is a thing the wielder owns rather than a
 * thing standing in the world - the same split the Fracture and the Pile make.
 */
public final class Ledger {

    /** The ceiling. Storing past it is refused, and the overflow lands on the world as normal. */
    public static final int MAX = 100;

    /** One point drains every this many ticks. Twenty seconds to lose ten. */
    public static final int DECAY_INTERVAL = 40;

    private float held;

    public float held() {
        return held;
    }

    public int rounded() {
        return Math.round(held);
    }

    public boolean empty() {
        return held <= 0.0F;
    }

    /** Room left under the ceiling, so a store knows how much of a consequence it may actually take. */
    public float room() {
        return Math.max(0.0F, MAX - held);
    }

    /**
     * Puts consequence in, and answers with how much it actually took.
     *
     * <p>The answer is the point of the method: what it took is what the world does <em>not</em>
     * get, and the caller subtracts exactly that from the consequence it was holding. A ledger at
     * the ceiling takes nothing and the hit lands whole.
     */
    public float store(float amount) {
        if (amount <= 0.0F) {
            return 0.0F;
        }
        float taken = Math.min(amount, room());
        held += taken;
        return taken;
    }

    /** Takes consequence back out, and answers with how much there was to take. */
    public float draw(float amount) {
        if (amount <= 0.0F || held <= 0.0F) {
            return 0.0F;
        }
        float given = Math.min(amount, held);
        held -= given;
        return given;
    }

    /** Everything at once, and the ledger is empty after it. The collapse and Recompense both use this. */
    public float drain() {
        float all = held;
        held = 0.0F;
        return all;
    }

    /** The slow leak. Called once per {@link #DECAY_INTERVAL} ticks by the service. */
    public void decay() {
        held = Math.max(0.0F, held - 1.0F);
    }

    public void set(float value) {
        held = Math.max(0.0F, Math.min(MAX, value));
    }

    public void clear() {
        held = 0.0F;
    }

    public void copyFrom(Ledger other) {
        held = other.held;
    }
}
