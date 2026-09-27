package com.efkrdnz.magical.magic.visual;

/**
 * The matter half of a spell: which real particles - vanilla's, or the mod's four tinted sprites -
 * a skill throws where it is cast, where it flies and where it lands.
 *
 * <p>The FX library draws light, and it draws it well: a cast circle, a beam, a field, a ground
 * mark. It was also drawing everything else, and every one of those draws is an additive quad,
 * so a spell's embers, splinters, spray and smoke all glowed and stacked and clipped toward white
 * together with its light. An accent hands the matter to the particle engine instead - a flame is
 * vanilla's flame, a splash is vanilla's splash, a shard is a lit sprite that falls and lands -
 * and the shader keeps only what is actually light.
 *
 * <p>A profile takes its school's accent unless it names one ({@code VisualProfile.Builder#accent}).
 * {@link #NONE} is the old look, untouched: the Authorities, whose visuals are their own design,
 * the Sword school, which already throws its steel's impacts as vanilla particles, and the skills
 * that are mostly shader by nature - a beam has no matter to hand over.
 */
public enum Accent {
    /** Drawn exactly as before the matter layer existed. */
    NONE,
    /** Flame, smoke and the odd lava pop. */
    EMBER,
    /** Splash, bubbles and falling drips. */
    SPLASH,
    /** Snowflakes, ice shards, snowball grit and a breath of cold mist. */
    FROST,
    /** End-rod sparks and gold motes. */
    RADIANT,
    /** Reverse-portal motes and violet smoke. */
    UMBRA,
    /** Pale motes, electric crackle and glass: the Spatial school's folds. */
    RIFT,
    /** Soul flame and soul wisps. */
    SOUL,
    /** Lifting runes, enchanting glyphs and motes: the Arcane school's matter. */
    RUNE,
    /** Dark smoke and ink. */
    GLOOM,
    /** Blood: red dust and a dark red breath. */
    GORE,
    /** Grit, dust plumes and crumbs of the ground: a spell that is the ground moving. */
    EARTH,
    /** Glowing and dark ink and sculk pops: the Eldritch school. */
    DEEP,
    /** Pink motes and witch sparks. */
    CHAOS;

    /** The accent a school's profiles start from. */
    public static Accent of(SchoolMaterial material) {
        return switch (material) {
            case ARCANE -> RUNE;
            case FIRE -> EMBER;
            case WATER -> SPLASH;
            case LIGHT -> RADIANT;
            case VOID -> UMBRA;
            case SPATIAL -> RIFT;
            case SOUL -> SOUL;
            case BLOOD -> GORE;
            case DARK -> GLOOM;
            case CHAOS -> CHAOS;
            case PRIMORDIAL -> EARTH;
            case ELDRITCH -> DEEP;
            case SWORD -> NONE;
        };
    }

    /** True when the profile's cues are drawn with the matter layer and the lighter shader pass. */
    public boolean active() {
        return this != NONE;
    }
}
