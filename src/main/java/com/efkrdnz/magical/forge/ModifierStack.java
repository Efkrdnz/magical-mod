package com.efkrdnz.magical.forge;

import java.util.Collection;

/**
 * How many copies of each modifier rune a strike carries, packed into one long: four bits per
 * {@link ForgeModifierKind}, indexed by ordinal.
 *
 * <p>This replaces the plain {@code int} bit set the strike math used to pass around. A bit set can
 * only say "present" or "absent", and the forge now lets a rune be drawn more than once - two PIERCE
 * runes should pierce harder than one. Four bits per kind is more than the
 * {@link ForgeModifierKind#maxStacks() cap} needs and keeps the whole set inside a single long, so a
 * stack stays a value that can be compared, stored in a record, and sent over the wire as one field.
 *
 * <p>{@link #has(ForgeModifierKind)} keeps the meaning the old {@code hasFlag} had, so every site
 * that only asks "is this rune on the weapon?" reads the same as it always did.
 */
public record ModifierStack(long packed) {

    /** No modifiers at all. */
    public static final ModifierStack EMPTY = new ModifierStack(0L);

    static final int BITS_PER_KIND = 4;
    private static final long KIND_MASK = 0xFL;

    static {
        // Java masks shift counts: `1L << 64` is `1L << 0`, not zero. A seventeenth modifier kind
        // would therefore wrap silently onto ECHO's bits and corrupt every stack that carried it,
        // with no error anywhere. Refuse to load instead.
        int needed = ForgeModifierKind.values().length * BITS_PER_KIND;
        if (needed > Long.SIZE) {
            throw new IllegalStateException("ForgeModifierKind has outgrown ModifierStack: "
                    + ForgeModifierKind.values().length + " kinds need " + needed
                    + " bits, and a long has " + Long.SIZE);
        }
    }

    /**
     * Counts {@code kinds}, repeats included, clamping each kind to its own cap. Feeding this the
     * one-of-each collection the old bit set was built from produces a stack that answers
     * {@link #has} identically, which is what keeps already-forged weapons behaving the same.
     */
    public static ModifierStack of(Collection<ForgeModifierKind> kinds) {
        ModifierStack stack = EMPTY;
        for (ForgeModifierKind kind : kinds) {
            stack = stack.plus(kind);
        }
        return stack;
    }

    /** Whether the weapon carries this rune at all. The old {@code hasFlag} question. */
    public boolean has(ForgeModifierKind kind) {
        return stacks(kind) > 0;
    }

    /** How many copies of this rune the strike carries, 0..{@link ForgeModifierKind#maxStacks()}. */
    public int stacks(ForgeModifierKind kind) {
        return (int) ((packed >>> shift(kind)) & KIND_MASK);
    }

    /** This stack with one more copy of {@code kind}, or unchanged if it is already at its cap. */
    public ModifierStack plus(ForgeModifierKind kind) {
        int current = stacks(kind);
        return current >= kind.maxStacks() ? this : withStacks(kind, current + 1);
    }

    /** This stack with every copy of {@code kind} removed. */
    public ModifierStack without(ForgeModifierKind kind) {
        return withStacks(kind, 0);
    }

    public boolean isEmpty() {
        return packed == 0L;
    }

    private ModifierStack withStacks(ForgeModifierKind kind, int count) {
        int shift = shift(kind);
        long cleared = packed & ~(KIND_MASK << shift);
        return new ModifierStack(cleared | ((long) count & KIND_MASK) << shift);
    }

    private static int shift(ForgeModifierKind kind) {
        return kind.ordinal() * BITS_PER_KIND;
    }
}
