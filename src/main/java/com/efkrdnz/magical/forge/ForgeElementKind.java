package com.efkrdnz.magical.forge;

/**
 * Every element a forged weapon can carry.
 *
 * <p>The first eight are drawn directly. The rest are compounds: two element runes fused in the
 * grammar into one element, so that everything below the grammar still deals with a single kind.
 * Adding one here is deliberately noisy - the exhaustive switches in the rider, the impact style
 * and the accent colour all stop compiling until the new element is given behaviour and a look.
 */
public enum ForgeElementKind {
    FIRE, FROST, STORM, VOID, RADIANT, VENOM, TERRA, GALE,

    /** fire + void: a burn that resistance and water cannot put out, and that stops healing. */
    BLACK_FLAME,

    /** fire + gale: detonates where it lands instead of applying anything. */
    EXPLOSION,

    /** frost + gale: freezes and drags the target in. */
    RIME_GALE;

    /** Whether this element was fused rather than drawn. */
    public boolean isCompound() {
        return ordinal() >= BLACK_FLAME.ordinal();
    }
}
