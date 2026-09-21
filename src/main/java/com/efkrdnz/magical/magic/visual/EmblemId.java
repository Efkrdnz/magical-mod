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
    SPLATTER,
    // ELDRITCH. A curling tendril with suckers, an eye that has no lids, a ring of fangs, a whip
    // mid-crack, three rows of scales, and a call rising out of the deep.
    TENDRIL,
    LIDLESS_EYE,
    FANGED_MAW,
    LASH,
    SCALES,
    DEEP_CALL;

    /**
     * Where the emblems begin in the shared SDF atlas, immediately after the stamps.
     *
     * <p>It is 33 rather than 32 because {@link StampId} grew {@code EDGE} for the Sword
     * school and a stamp's cell is its own ordinal, so at 32 that new stamp and
     * {@link #BLANK} sampled the same cell. A cell collision draws the wrong mark and
     * fails nothing, so this constant has to move every time a stamp is added and
     * {@code FxAtlasCellsTest} is the only thing that will ever say so. The atlas is 16x16
     * procedural cells and the last emblem lands at 121, so there is room for another 134.</p>
     */
    public static final int FIRST_CELL = 33;

    public int atlasCell() {
        return FIRST_CELL + ordinal();
    }
}
