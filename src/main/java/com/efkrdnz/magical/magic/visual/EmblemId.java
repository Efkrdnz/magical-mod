package com.efkrdnz.magical.magic.visual;

/**
 * The skill's identity mark, drawn at every LOD. Each value is one 64x64 SDF cell in the sigil
 * atlas starting at cell 32 (cells 0..31 belong to {@link StampId}). The atlas is generated at
 * client start from the vector strokes in {@code FxTextures}.
 */
public enum EmblemId {
    BLANK,
    EYE,
    FLAME,
    DROP,
    SNOWFLAKE,
    HOURGLASS,
    KEY,
    SKULL,
    SUN,
    MOON,
    FEATHER,
    ANCHOR,
    SPIRAL,
    TREE,
    HAND,
    CROWN,
    WING,
    THORN_CROWN,
    INFINITY,
    GEAR,
    ARROW,
    LOCK,
    CHAIN,
    HEART,
    BELL,
    MASK,
    LEAF,
    BONE,
    STAR6,
    WAVE,
    MOUNTAIN,
    COMET,
    SEED,
    LANTERN,
    NEEDLE,
    MIRROR,
    TOOTH,
    SHELL,
    KNOT,
    COMPASS,
    VOID_RING,
    // roster-specific cells
    CHIMNEY,
    CRACKED_STONE,
    CRUCIBLE,
    HORSESHOE,
    CHIASMA,
    PINCER,
    CALIPER,
    ARMATURE,
    HINGE,
    SCATTER,
    LEVIATHAN,
    BEACON,
    SCRIPTURE,
    GULLET,
    MOTH,
    CUIRASS,
    HAMMER,
    RAM,
    VICE,
    WAR_HORN,
    KEYSTONE,
    QUIVER,
    WOLF,
    RETICLE,
    OPEN_HAND,
    HARE,
    LEECH,
    CLOSED_EYE,
    PLAGUE,
    ALEMBIC,
    // fusion cells
    GEYSER,
    WELL,
    ORRERY,
    WHEEL,
    ECLIPSE,
    JAWS,
    // forbidden cells. The atlas is 16x16 and emblems start at cell 32, so there is room for well
    // over a hundred more - new schools add marks here rather than reusing another school's.
    EFFIGY_DOLL,
    CAST_SHADOW,
    TALLY,
    CUT_THREAD,
    // and the Light mark that answers them
    CHALICE,
    SPLATTER;

    public static final int FIRST_CELL = 32;

    public int atlasCell() {
        return FIRST_CELL + ordinal();
    }
}
