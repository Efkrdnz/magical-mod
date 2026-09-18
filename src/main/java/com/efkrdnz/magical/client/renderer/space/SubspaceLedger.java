package com.efkrdnz.magical.client.renderer.space;

import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleNotation;
import com.efkrdnz.magical.magic.SpaceRuleOperation;

/**
 * The twelve synced law ordinals, reduced to what the wall has to draw.
 *
 * <p>A domain's whole state is twelve small integers, and the point of the boundary is to wear
 * them: an empty domain is a pane of cold glass and a fully legislated one carries a dense band
 * of marks along its horizon. The reduction is here rather than in the renderer so it can be
 * pinned by a test - which slot a category owns, how many laws are written, whether the boundary
 * is sealed - without a Minecraft class anywhere in the way.
 *
 * <p>The band sits on one arc centred due north, and no mark covers north itself: the graduated
 * meridian stands there, and a reader who can find north can count marks off from it.
 */
public final class SubspaceLedger {

    /** A slot with no law on it. The wire uses this for a cleared category as well as an unset one. */
    public static final int NO_RULE = -1;

    /** Gravity as the horizon draws it: level, inverted, or dissolved. */
    public static final int GRAVITY_NORMAL = 0;
    public static final int GRAVITY_FLIP = 1;
    public static final int GRAVITY_ZERO = 2;

    /** Degrees between one slot's centre and the next. */
    public static final double SLOT_PITCH_DEGREES = SubspaceVault.RIB_PITCH_DEGREES;
    private static final SpaceRuleCategory[] CATEGORIES = SpaceRuleCategory.values();
    private static final SpaceRuleOperation[] OPERATIONS = SpaceRuleOperation.values();

    private final SpaceRuleOperation[] laws;
    private final int lawCount;

    private SubspaceLedger(SpaceRuleOperation[] laws, int lawCount) {
        this.laws = laws;
        this.lawCount = lawCount;
    }

    /**
     * Reads the wire.
     *
     * <p>Everything here is defensive because the array arrives from synced entity data: an
     * ordinal outside the enum, an operation filed under somebody else's category, or a short
     * array all mean "no law" rather than an exception on the render thread. A clearing operation
     * also means no law - the server writes {@code NO_RULE} for one, so a mark for it could only
     * ever arrive from a forged packet.
     */
    public static SubspaceLedger of(int[] operationOrdinals) {
        SpaceRuleOperation[] laws = new SpaceRuleOperation[CATEGORIES.length];
        int count = 0;
        if (operationOrdinals != null) {
            for (int slot = 0; slot < Math.min(CATEGORIES.length, operationOrdinals.length); slot++) {
                int ordinal = operationOrdinals[slot];
                if (ordinal < 0 || ordinal >= OPERATIONS.length) {
                    continue;
                }
                SpaceRuleOperation operation = OPERATIONS[ordinal];
                if (operation.clear() || operation.category() != CATEGORIES[slot]) {
                    continue;
                }
                laws[slot] = operation;
                count++;
            }
        }
        return new SubspaceLedger(laws, count);
    }

    /** How many laws are written. The one reading of a domain that needs no vocabulary. */
    public int lawCount() {
        return lawCount;
    }

    /** Whether this slot carries a law, and so whether its mark is drawn at all. */
    public boolean lit(int slot) {
        return slot >= 0 && slot < laws.length && laws[slot] != null;
    }

    /** The {@code SpaceRuleChange} id that gives a mark its form, or -1 for an empty slot. */
    public int change(int slot) {
        return lit(slot) ? SpaceRuleNotation.change(laws[slot]).id() : -1;
    }

    /** The operation on a slot, or null. */
    public SpaceRuleOperation law(int slot) {
        return lit(slot) ? laws[slot] : null;
    }

    /**
     * Gravity, read a second time as the horizon's own state.
     *
     * <p>It is the one law with two readings on purpose: the horizon is a level line, and a level
     * line is exactly the thing that says which way is down. Inverting or dissolving gravity
     * inverts or dissolves it, so the single most consequential law in the domain is legible
     * before you have learned what any mark means.
     */
    public int gravityState() {
        SpaceRuleOperation gravity = law(SpaceRuleCategory.GRAVITY.ordinal());
        if (gravity == SpaceRuleOperation.REVERSE_GRAVITY) {
            return GRAVITY_FLIP;
        }
        if (gravity == SpaceRuleOperation.REMOVE_GRAVITY) {
            return GRAVITY_ZERO;
        }
        return GRAVITY_NORMAL;
    }

    /** Whether the boundary is sealed - the one law that thickens the wall it is written on. */
    public boolean sealed() {
        return law(SpaceRuleCategory.BOUNDARY.ordinal()) == SpaceRuleOperation.SEAL_BOUNDARY;
    }

    /**
     * The bearing a slot owns, for good.
     *
     * <p>A category owns a compass direction rather than a place on a band: gravity stands due
     * north, velocity thirty degrees east of it, collision thirty degrees west. Twelve of them wrap
     * the whole horizon, which is the reading the wall this replaces failed hardest - it put all
     * twelve on one arc round north, so from three quarters of all bearings a fully legislated
     * domain and an empty one were the same picture.
     */
    public static double slotBearingDegrees(int slot) {
        return slot * SLOT_PITCH_DEGREES;
    }
}
