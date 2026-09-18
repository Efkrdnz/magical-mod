package com.efkrdnz.magical.client.renderer.space;

/**
 * What a subspace vertex is, packed into the five-bit {@code kind} field of the magic vertex.
 *
 * <p>The old dome had no such field: it drew six different things - shell bands, sphere rings,
 * orbit arcs, glyph ticks, glints - on one render type with one shader that could not tell them
 * apart, so every one of them had to be a solid coloured quad and the whole wall read as a
 * single milky material. Naming them lets the fragment shader give each its own optics: the
 * membrane is a Fresnel pane, the horizon and the meridian are hairlines with their own pixel
 * widths, and the crown is the one hard edge in the whole domain.
 */
public final class SubspaceKind {

    /** The wall itself: an inverted-Fresnel pane, nearly clear face-on and firm at the limb. */
    public static final int MEMBRANE = 0;
    /** The level line round the dome's waist, which doubles as the gravity readout. */
    public static final int HORIZON = 1;
    /** The graduated line standing due north, from the horizon to the crown. */
    public static final int MERIDIAN = 2;
    /** One graduation on the meridian: one tick per block of radius, east of the line only. */
    public static final int TICK = 3;
    /** One law written into the band: a mark whose form is its {@code SpaceRuleChange}. */
    public static final int MARK = 4;
    /** The zenith cap, thinned to a whisper - the only hard boundary the domain owns. */
    public static final int CROWN = 5;
    /** The ring that runs out from where a body crossed the wall, and the only thing that moves. */
    public static final int RIPPLE = 6;

    /** One past the last kind, so a test can sweep them and the packer can range-check. */
    public static final int COUNT = 7;

    private SubspaceKind() {}
}
