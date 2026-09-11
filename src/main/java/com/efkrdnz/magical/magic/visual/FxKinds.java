package com.efkrdnz.magical.magic.visual;

/** Shader kind ids for every library shader other than glyph_ink (see {@link GlyphKind}). */
public final class FxKinds {
    private FxKinds() {}

    public enum Orb {
        PLASMA, HOLLOW_SHELL, SPARK_BURST, IRIS, CRESCENT, THIN_HALO, WORLEY_CELLS, EMBER_CLUSTER,
        HEX_LENS, BLOOM_FLASH, VOID_CORE, LIQUID_DROP, CHARGE_SPHERE, LENS_STREAKS, SHARD_DIAMOND, EYE_SLIT;

        public int id() { return ordinal(); }

        public boolean dark() { return this == VOID_CORE || this == IRIS || this == EYE_SLIT; }
    }

    public enum Filament {
        PLASMA_TUBE, HELIX, LIGHTNING, CHAIN, RIBBON, BLADE_RIM, THREAD_KNOTS, DASH_TRAIN,
        ROPE, VEIN, RUNE_THREAD, LIQUID_ROPE, FLAME_TONGUE, CRYSTAL_SHARD, WATER, INK_TENDRIL;

        public int id() { return ordinal(); }

        public boolean dark() { return this == VEIN || this == LIQUID_ROPE || this == CRYSTAL_SHARD || this == WATER || this == INK_TENDRIL; }
    }

    public enum Field {
        HEX_LATTICE, MAGMA_CRACKS, FROST_CELLS, RIPPLE_WATER, FILIGREE, VOID_INK, PRISM_GRID, SCALE_PLATES,
        ORGANIC_CELLS, STATIC_SCANLINES, RUNE_PAPER, HOLY_GLASS, EMBER_CRUST, MIRROR_SHEEN, SPORE_MOSS, HONEYCOMB_SHELL;

        public int id() { return ordinal(); }
    }

    public enum Mark {
        SHOCK_RING, CRACK_WEB, SCORCH_DECAL, FROST_BLOOM, OVERGROWTH, LATTICE_GRID, RIPPLES, RAY_BURST,
        VORTEX_SPIRAL, MAW, CLOCK_SPOKES, INK_STAIN, SIGIL_SLAM_FLASH, EMBER_FIELD, HEX_CELLS, SPIRAL_DRAIN;

        public int id() { return ordinal(); }

        /** Kinds that must be able to darken (drawn through the translucent twin). */
        public boolean dark() {
            return this == CRACK_WEB || this == SCORCH_DECAL || this == FROST_BLOOM || this == OVERGROWTH
                    || this == VORTEX_SPIRAL || this == MAW || this == INK_STAIN || this == EMBER_FIELD || this == SPIRAL_DRAIN;
        }
    }

    public enum Rift {
        VERTICAL_WOUND, HORIZONTAL_SLIT_EYE, BLADE, IRIS_MOUTH, SPIRAL_TEAR, SHATTER, SEAM_HAIRLINE, ZIPPER,
        RADIAL_STAR_CRACK, RING_WOUND, GLITCH_CUT;

        public int id() { return ordinal(); }
    }

    public enum RiftInterior {
        VOID_BLACK, LENSED_STARS, WHITE_LIGHT, TINTED_REALM;

        public int id() { return ordinal(); }
    }

    public enum Lens {
        GRAVITY_LENS, HEAT_HAZE, REFRACTION_BUBBLE, TIME_RIPPLE, PRISM_SPLIT, SHOCK_LENS, MIRAGE_SHEET;

        public int id() { return ordinal(); }
    }

    public enum Body {
        CRYSTAL, ICE, OBSIDIAN, MAGMA_ROCK, BONE_IVORY, GLASS, METAL_BANDS, WOOD_VINE, GOLD, STONE, AMBER, PEARL,
        /**
         * Wet, dark and viscous. Added for the blood voxel field, and the one material whose
         * dissolve actually reaches zero - see the BLOOD branch in rendertype_shard_body.fsh.
         */
        BLOOD;

        public int id() { return ordinal(); }
    }

    public enum Smoke {
        SMOKE_PUFF, MIST_WISP, EMBER_CLUSTER, ASH_FLAKE, INK_BLOOM, SPORE_DOTS, PETAL, FEATHER, HEX_FRAGMENT,
        FROST_CRYSTAL, GLASS_SPLINTER, SPARK_STREAK, RUNE_MOTE, DROPLET, LENS_SPARKLE, DUST;

        public int id() { return ordinal(); }

        public boolean dark() { return this == SMOKE_PUFF || this == ASH_FLAKE || this == INK_BLOOM; }

        /** Quads for these kinds are stretched along their velocity by the emitter. */
        public boolean stretched() { return this == SPARK_STREAK || this == DROPLET; }
    }

    public enum Overlay {
        VIGNETTE, FLASH, SHOCK_RING, TUNNEL, FROST_EDGES, HEAT_SHIMMER, INK_BLEED, BLOOM_RAYS, PRISM_RING,
        HEX_PULSE, CRACKED_GLASS, IRIS_CLOSE, HEARTBEAT, STATIC_GLITCH, WATER_DROPLETS, EMBER_DRIFT;

        public int id() { return ordinal(); }

        public static Overlay byId(int id) {
            Overlay[] values = values();
            return values[Math.floorMod(id, values.length)];
        }
    }
}
