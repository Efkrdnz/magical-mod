package com.efkrdnz.magical.forge;

/**
 * Every element a forged weapon can carry.
 *
 * <p>The first ten are drawn directly. The rest are compounds: two element runes fused in the
 * grammar into one element, so that everything below the grammar still deals with a single kind.
 * Adding one here is deliberately noisy - the exhaustive switches in the rider, the impact style
 * and the accent colour all stop compiling until the new element is given behaviour and a look.
 *
 * <p>Order matters, but only relatively: {@link #isCompound()} compares against BLACK_FLAME, so a
 * drawable element has to be declared before it. Nothing persists these by ordinal - a forged
 * weapon stores its element as a ResourceLocation - so inserting one is safe.
 */
public enum ForgeElementKind {
    /**
     * DARK is unrelated to MagicSchool.DARK and MagicAttribute.DARK despite the shared word: this
     * one is a thing you hammer into a blade. It is drawn rather than fused, and it is the second
     * half of BLACK_FLAME now that black flames are fire + dark rather than fire + void.
     */
    FIRE, FROST, STORM, VOID, RADIANT, VENOM, TERRA, GALE, DARK,

    /**
     * Drawn, not fused, and unrelated to MagicSchool.BLOOD in the same way DARK is unrelated to
     * MagicSchool.DARK. It is the only element that pays the wielder back: a share of what it deals
     * returns as health, and it bites deeper the worse the target already is.
     */
    BLOOD,

    /** fire + dark: a burn that resistance and water cannot put out, and that stops healing. */
    BLACK_FLAME,

    /** fire + gale: detonates where it lands instead of applying anything. */
    EXPLOSION,

    /** frost + gale: freezes and drags the target in. */
    RIME_GALE,

    /** fire + storm: chains, and every arc sets what it touches alight. */
    PLASMA,

    /** fire + terra: leaves burning ground behind. */
    MAGMA,

    /** frost + storm: chains, and every jump deepens the cold. */
    HAILSTORM,

    /** void + radiant: rot and blindness, and the undead still burn. */
    ECLIPSE,

    /** void + venom: poison and rot, and healing that no longer works. */
    BLIGHT,

    /** venom + terra: poisoned ground that keeps poisoning. */
    VERDIGRIS,

    /** blood + dark: hits harder the more Corruption the wielder carries, and adds to it. */
    CORRUPTION,

    /** blood + radiant: the wielder spends their own health and gets barrier back for it. */
    MARTYR,

    /** blood + frost: the wound freezes shut - no draw for the wielder, and nothing flows. */
    CLOT;

    /** Whether this element was fused rather than drawn. */
    public boolean isCompound() {
        return ordinal() >= BLACK_FLAME.ordinal();
    }
}
