package com.efkrdnz.magical.client.renderer.space;

/**
 * What a subspace vertex is, packed into the five-bit {@code kind} field of the magic vertex.
 *
 * <p>The old dome had no such field: it drew six different things on one render type with one
 * shader that could not tell them apart, so every one of them had to be a solid coloured quad and
 * the whole wall read as a single milky material. Naming them lets the fragment shader give each
 * its own optics - and, more to the point here, lets one chamfer serve every stroke in the domain
 * while the pane stays a pane.
 */
public final class SubspaceKind {

    /** The wall itself: a flat pane from inside, an inverted-Fresnel silhouette from outside. */
    public static final int MEMBRANE = 0;
    /** The ring where the wall meets the floor the caster is standing on. */
    public static final int FOOT = 1;
    /** The course the ribs spring from, which is also the gravity readout. */
    public static final int SPRING = 2;
    /** One tally mark crossing that course, one per slot, lit when its law is written. */
    public static final int NOTCH = 3;
    /** One of the twelve ribs: a wound member climbing from the course to the oculus. */
    public static final int RIB = 4;
    /** The swelling on a lit rib that carries its law {@code SpaceRuleChange} glyph. */
    public static final int BOSS = 5;
    /** The rim of the opening overhead, where the wall stops and the sky is the caster own. */
    public static final int OCULUS = 6;
    /** The ring that runs out from where a body crossed the wall, and the only thing that moves. */
    public static final int RIPPLE = 7;

    /** One past the last kind, so a test can sweep them and the packer can range-check. */
    public static final int COUNT = 8;

    private SubspaceKind() {}
}
