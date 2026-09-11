package com.efkrdnz.magical.magic.visual;

import com.efkrdnz.magical.magic.MagicSchool;

/**
 * School fixes the MATERIAL defaults a profile starts from: palette ramp, default frame sides,
 * band kind, stamp, core, whether the school darkens the ground, and a sound bed. Every field is
 * overridable per skill.
 */
public enum SchoolMaterial {
    ARCANE(MagicSchool.ARCANE, new int[] {0x72E4FF, 0x9AB8FF, 0x5FE0D0, 0xB4A0FF}, 6, GlyphKind.TICK_BAND, StampId.HEX, CoreKind.HEX_LENS, SpinSignature.COUNTER_SLOW, false),
    FIRE(MagicSchool.FIRE, new int[] {0xFF7A45, 0xFFB15A, 0xFF4E21, 0x9A8A80}, 3, GlyphKind.TOOTH_BAND, StampId.FLAME, CoreKind.EMBER_PIT, SpinSignature.SINGLE_FAST, false),
    WATER(MagicSchool.WATER, new int[] {0x5BC0FF, 0x8EEAFF, 0x31C7FF, 0x155F7A}, 8, GlyphKind.WAVE_BAND, StampId.SNOWFLAKE, CoreKind.RIPPLE, SpinSignature.SLOW, false),
    LIGHT(MagicSchool.LIGHT, new int[] {0xFFE17A, 0xFFF4B2, 0xFFD35F, 0xFFF1A8}, 12, GlyphKind.PETAL_BAND, StampId.KITE, CoreKind.IRIS, SpinSignature.COUNTER_SLOW, false),
    VOID(MagicSchool.VOID, new int[] {0xA57DFF, 0x9F7BFF, 0x6F45FF, 0x341052}, 5, GlyphKind.RUNE_BAND, StampId.THORN, CoreKind.VOID_PIT, SpinSignature.COUNTER_FAST, true),
    SPATIAL(MagicSchool.SPATIAL, new int[] {0x88DFFF, 0xB4F1FF, 0x82E8FF, 0x5DA8FF}, 4, GlyphKind.DASHED_RING, StampId.NEEDLE, CoreKind.CROSS, SpinSignature.COUNTER_SLOW, false),
    SOUL(MagicSchool.SOUL, new int[] {0xD8F0FF, 0xEAF6FF, 0xB8E0FF, 0x7AA0C0}, 7, GlyphKind.CHAIN_BAND, StampId.TEARDROP, CoreKind.RIPPLE, SpinSignature.SLOW, false),

    // The forbidden schools. Every one of these needs a row: of() falls back to ARCANE for anything
    // unlisted, which fails silently - the skill renders as blue arcane rather than crashing, and
    // nobody finds out until they see it in game.
    BLOOD(MagicSchool.BLOOD, new int[] {0xC4122B, 0xE8425E, 0x8A0B1E, 0x4A0710}, 9, GlyphKind.BRAID_BAND, StampId.DROP, CoreKind.DISC_GLOW, SpinSignature.SLOW, false),
    DARK(MagicSchool.DARK, new int[] {0x5B3A78, 0x7E52A6, 0x2E1B42, 0x120A1C}, 10, GlyphKind.SOLID_RING, StampId.BONE, CoreKind.VOID_PIT, SpinSignature.COUNTER_SLOW, true),
    CHAOS(MagicSchool.CHAOS, new int[] {0xFF4FD8, 0xFF9BEC, 0xC81FA6, 0x5E0A4C}, 11, GlyphKind.FACET_BAND, StampId.SPIRAL, CoreKind.SUNBURST, SpinSignature.ONE_WAY_FAST, false),
    PRIMORDIAL(MagicSchool.PRIMORDIAL, new int[] {0x7A6A4A, 0xB09A6E, 0x4E4230, 0x241D14}, 3, GlyphKind.STAMP_BAND, StampId.TRIANGLE, CoreKind.DISC_GLOW, SpinSignature.STATIC, false),
    ELDRITCH(MagicSchool.ELDRITCH, new int[] {0x2FBF9E, 0x5FEFD0, 0x1A7A66, 0x06322A}, 13, GlyphKind.FACET_BAND, StampId.EYE, CoreKind.IRIS, SpinSignature.COUNTER_FAST, false);

    private final MagicSchool school;
    private final int[] variants;
    private final int defaultFrameSides;
    private final GlyphKind defaultBand;
    private final StampId defaultStamp;
    private final CoreKind defaultCore;
    private final SpinSignature defaultSpin;
    private final boolean darkensGround;

    SchoolMaterial(MagicSchool school, int[] variants, int defaultFrameSides, GlyphKind defaultBand, StampId defaultStamp, CoreKind defaultCore, SpinSignature defaultSpin, boolean darkensGround) {
        this.school = school;
        this.variants = variants;
        this.defaultFrameSides = defaultFrameSides;
        this.defaultBand = defaultBand;
        this.defaultStamp = defaultStamp;
        this.defaultCore = defaultCore;
        this.defaultSpin = defaultSpin;
        this.darkensGround = darkensGround;
    }

    public static SchoolMaterial of(MagicSchool school) {
        for (SchoolMaterial material : values()) {
            if (material.school == school) {
                return material;
            }
        }
        return ARCANE;
    }

    public MagicSchool school() {
        return school;
    }

    public int variantColor(int variant) {
        return variants[Math.floorMod(variant, variants.length)];
    }

    public int defaultFrameSides() {
        return defaultFrameSides;
    }

    public GlyphKind defaultBand() {
        return defaultBand;
    }

    public StampId defaultStamp() {
        return defaultStamp;
    }

    public CoreKind defaultCore() {
        return defaultCore;
    }

    public SpinSignature defaultSpin() {
        return defaultSpin;
    }

    public boolean darkensGround() {
        return darkensGround;
    }
}
