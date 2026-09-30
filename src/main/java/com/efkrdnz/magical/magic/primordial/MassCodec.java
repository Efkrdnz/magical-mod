package com.efkrdnz.magical.magic.primordial;

/**
 * How a rock in flight - a falling star, a torn-up plate - carries its blocks to the client: block
 * state ids and packed offsets in the effect's synced data, drawn by the {@code primordial_mass}
 * painter. Pure. An offset is -8..7 on each axis, four bits apiece.
 */
public final class MassCodec {
    public static final int MAX_BLOCKS = 48;
    public static final String STATES = "States";
    public static final String OFFSETS = "Offs";
    public static final String SCALE = "Sc";
    public static final String SPIN = "Spin";
    public static final String GLOW = "Glow";

    private MassCodec() {}

    public static int pack(int dx, int dy, int dz) {
        return ((dx + 8) & 15) | (((dy + 8) & 15) << 4) | (((dz + 8) & 15) << 8);
    }

    public static int[] unpack(int packed) {
        return new int[] {(packed & 15) - 8, ((packed >> 4) & 15) - 8, ((packed >> 8) & 15) - 8};
    }
}
