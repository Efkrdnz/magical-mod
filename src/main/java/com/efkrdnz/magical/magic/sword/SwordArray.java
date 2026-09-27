package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;

/**
 * The wielder half of the Sword Summoner: which posture they hold, and whether the steel is out.
 *
 * <p>Two facts, and they are the only part of the class the wielder owns and the only part that is
 * saved. The swords in the air, which of them are away, every return clock and the frame are held
 * by {@code SwordService} and dropped on logout, the way {@code PileService} drops its Piles.
 *
 * <p>This used to be a list of up to twelve authored bearings with a conserved measure of Edge
 * partitioned across them, three arithmetic invariants and a settle pass. All of it is gone, and
 * the reason is in {@code docs/superpowers/specs/2026-09-22-sword-stance-design.md}: the shape was
 * <em>authored</em> one press at a time from a crosshair that could not reach half of it, so every
 * good thing the kit did was a projection of something the wielder had never seen. The formations
 * are designed now. What is left to own is which one.
 *
 * <p><b>And the rack, counted.</b> The swords are the weapons in the wielder's rack - an empty
 * socket is nothing, not plain steel - so the Array carries {@link #racked()}, which sockets hold a
 * weapon the wielder may fly. It is a copy: {@code SwordService.refreshRack} reads it off the rack
 * whenever the rack, the rung or Weapon God may have moved it, and it rides the save because the
 * client counts the formation off the same tag the server writes.
 *
 * <p>Pure but for {@link IntArrayTag}, which it needs in order to live on {@code PlayerMagicState},
 * exactly as {@code Fracture} and {@code Grimoire} already do. Nothing here knows what a level is.
 */
public final class SwordArray {

    /**
     * Rides at index 0 of the saved int array.
     *
     * <p><b>Two, and the bump is the whole migration.</b> A version 1 tag was a packed list of
     * lattice stations and there is no honest way to read one as a stance - the shape it describes
     * cannot be expressed any more. {@link #load}'s existing rule handles it without a line of
     * migration code: a version this build does not know is dropped whole rather than guessed at,
     * so every v1 save comes back as the default stance with the steel sheathed, which is one
     * keypress from right.
     */
    public static final int SAVE_VERSION = 2;

    /**
     * Index 1 is the stance ordinal, index 2 the drawn flag: the shortest tag {@link #load} reads.
     * The rack rides a fourth int, and a tag written before it had one loads as nothing racked.
     */
    private static final int LENGTH = 3;

    private static final int RACK_INDEX = 3;

    /** Sockets on the rack, one a sword the apex fields. {@code SwordRack.SIZE} is this number. */
    public static final int RACK_SOCKETS = 12;

    /** Every socket at once. Nothing above it is a socket, so nothing above it is ever kept. */
    public static final int FULL_RACK = (1 << RACK_SOCKETS) - 1;

    private SwordStance stance = SwordStance.first();

    private boolean drawn;

    private SwordRules rules = SwordRules.SUMMONER;

    /** Bit <i>s</i> set means rack socket <i>s</i> holds a weapon this wielder may fly. */
    private int racked;

    /** The base rung is the floor, so an Array read off a wielder with no class is still legal. */
    public SwordArray() {
    }

    // ---- reading ------------------------------------------------------------------------------

    public SwordRules rules() {
        return rules;
    }

    public SwordStance stance() {
        return stance;
    }

    /** Whether the steel is out. Off means gone - no entity, no swords, no upkeep. */
    public boolean drawn() {
        return drawn;
    }

    /** Bit <i>s</i> set means rack socket <i>s</i> holds a weapon this wielder may fly. */
    public int racked() {
        return racked;
    }

    /**
     * The one number the whole class is counted in: the swords in the rack, under what the rung
     * opens, capped by the shape.
     *
     * <p>The rung opens 4 / 7 / 10 / 12 sockets, the rack fills as many of them as the wielder
     * has weapons for, and the stance takes as much of that as its silhouette can carry - see
     * {@code SwordStance}. All three are here rather than at the call sites because every one of
     * them (the live mask, Sword Heart's pool, a volley, the picker's diagram) has to agree, and a
     * second place that forgot the rack would stand swords round a wielder who has none.
     */
    public int swords() {
        return fielded(rules, stance, racked);
    }

    /** What {@code stance} fields at {@code rules} with {@code racked} in the rack: the picker's count. */
    public static int fielded(SwordRules rules, SwordStance stance, int racked) {
        return stance.swords(Math.min(rules.swords(), Integer.bitCount(racked & FULL_RACK)));
    }

    /**
     * The rack socket sword {@code sword} flies from: the {@code sword}-th set bit of the rack, so
     * a gap on the ring is never a gap in the formation. -1 when there is no such sword.
     */
    public static int socketOf(int racked, int sword) {
        if (sword < 0) {
            return -1;
        }
        int rest = racked & FULL_RACK;
        for (int i = 0; i < sword && rest != 0; i++) {
            rest &= rest - 1;
        }
        return rest == 0 ? -1 : Integer.numberOfTrailingZeros(rest);
    }

    /**
     * Whether this is the untouched default, which is what lets the save tag be omitted entirely.
     *
     * <p>The rung is not part of the answer: it is re-derived from the class progress on every
     * load, so a Sword God who has never taken a stance, drawn nor racked is still worth no bytes.
     */
    public boolean isDefault() {
        return !drawn && stance == SwordStance.first() && racked == 0;
    }

    // ---- writing ------------------------------------------------------------------------------

    /**
     * Takes a stance, or refuses one this rung has not opened.
     *
     * <p>Refuses rather than clamping, unlike {@link #setRules}, and the difference is who asked:
     * a wielder choosing a locked stance has made a mistake the picker should name, while a rung
     * dropping out from under a stance already held is nobody's mistake and must not leave them
     * standing in nothing.
     *
     * @return whether the stance changed
     */
    public boolean setStance(SwordStance next) {
        if (next == null || next == stance || !rules.allows(next)) {
            return false;
        }
        stance = next;
        return true;
    }

    /** @return whether it changed, so a caller can tell a real toggle from a repeat press */
    public boolean setDrawn(boolean next) {
        if (drawn == next) {
            return false;
        }
        drawn = next;
        return true;
    }

    /**
     * Raises or lowers the rung, and puts the stance back through the new rung's rules.
     *
     * <p>A rung only ever goes up in play, but a reset and a fresh login both come through here,
     * and a stance the new rules have not opened has to fall back rather than sit there illegal.
     */
    public void setRules(SwordRules next) {
        if (next == null) {
            return;
        }
        rules = next;
        stance = rules.clamp(stance);
    }

    /**
     * Takes the rack's count, as {@code SwordService.refreshRack} read it.
     *
     * @return whether it changed, so the caller syncs only a rack that moved
     */
    public boolean setRacked(int mask) {
        int next = mask & FULL_RACK;
        if (next == racked) {
            return false;
        }
        racked = next;
        return true;
    }

    /**
     * Back to the default posture with the steel away and nothing counted. What
     * {@code /magical reset} leaves; the rack itself outlives a reset, and the next refresh counts
     * it again.
     */
    public void clear() {
        stance = SwordStance.first();
        drawn = false;
        racked = 0;
    }

    public void copyFrom(SwordArray other) {
        if (other == null || other == this) {
            return;
        }
        rules = other.rules;
        stance = other.stance;
        drawn = other.drawn;
        racked = other.racked;
    }

    // ---- persistence --------------------------------------------------------------------------

    public IntArrayTag save() {
        return new IntArrayTag(new int[] {SAVE_VERSION, stance.ordinal(), drawn ? 1 : 0, racked});
    }

    /**
     * Total: no input throws and everything is clamped.
     *
     * <p>{@code SwordStance.byOrdinal} answers the default for anything out of range, so a
     * hand-edited save naming stance 99 loads as Guard rather than throwing inside a player's
     * login. The rung is applied by {@link #setRules} and must be applied <em>first</em>, which
     * is why the order in {@code PlayerMagicState.load} matters and is commented there: read
     * before the class progress is on, a Sword God's Rain is clamped back to Guard and the
     * clamped value is what gets written to disk.
     */
    public void load(IntArrayTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        int[] data = tag.getAsIntArray();
        if (data.length < LENGTH || data[0] != SAVE_VERSION) {
            return;
        }
        stance = rules.clamp(SwordStance.byOrdinal(data[1]));
        drawn = data[2] != 0;
        racked = data.length > RACK_INDEX ? data[RACK_INDEX] & FULL_RACK : 0;
    }

    public static int tagType() {
        return Tag.TAG_INT_ARRAY;
    }
}
