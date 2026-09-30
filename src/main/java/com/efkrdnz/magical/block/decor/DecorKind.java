package com.efkrdnz.magical.block.decor;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

/**
 * The eight decorative kinds, each made in all sixteen dye colours.
 *
 * <p>Every number here is the vanilla block the kind stands in for - bricks are stone bricks,
 * planks are planks, plating is an iron block - so a decor block mines, burns and survives a creeper
 * exactly as a player already expects it to. {@code DecorKindTest} holds each row to its vanilla
 * analogue, so a hardness typed here by hand cannot drift from the block it claims to be.
 *
 * <p>The textures, block models, loot tables, tool tags and names are generated from the same list
 * by {@code scripts/decor-blocks.py}; add a row here and run it.
 */
public enum DecorKind {
    RUNESTONE_BRICKS("runestone_bricks", 1.5F, 6.0F, SoundType.STONE, NoteBlockInstrument.BASEDRUM, Tool.PICKAXE, 0, 0),
    RUNESTONE_TILES("runestone_tiles", 1.5F, 6.0F, SoundType.POLISHED_DEEPSLATE, NoteBlockInstrument.BASEDRUM, Tool.PICKAXE, 0, 0),
    SIGIL_STONE("sigil_stone", 1.5F, 6.0F, SoundType.DEEPSLATE_TILES, NoteBlockInstrument.BASEDRUM, Tool.PICKAXE, 0, 0),
    GLIMMER_LAMP("glimmer_lamp", 0.3F, 0.3F, SoundType.GLASS, NoteBlockInstrument.PLING, Tool.NONE, 15, 0),
    CRYSTAL("crystal", 1.5F, 1.5F, SoundType.AMETHYST, NoteBlockInstrument.CHIME, Tool.PICKAXE, 5, 0),
    WOVEN_CLOTH("woven_cloth", 0.8F, 0.8F, SoundType.WOOL, NoteBlockInstrument.GUITAR, Tool.NONE, 0, 60),
    PAINTED_PLANKS("painted_planks", 2.0F, 3.0F, SoundType.WOOD, NoteBlockInstrument.BASS, Tool.AXE, 0, 20),
    ARCANE_PLATING("arcane_plating", 5.0F, 6.0F, SoundType.METAL, NoteBlockInstrument.IRON_XYLOPHONE, Tool.STONE_PICKAXE, 0, 0);

    /**
     * Every kind comes in these sixteen, in vanilla's creative order for dyed blocks: the neutrals,
     * then round the colour wheel. The generator script lists the same sixteen in the same order.
     */
    public static final java.util.List<DyeColor> COLOURS = java.util.List.of(
            DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK, DyeColor.BROWN, DyeColor.RED,
            DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN, DyeColor.CYAN, DyeColor.LIGHT_BLUE,
            DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK);

    /** Which tool breaks it fast, and whether anything less gets it back at all. */
    public enum Tool {
        /** Breaks by hand at full speed and always drops, like wool and glowstone. */
        NONE,
        /** Mined fastest with an axe; drops by hand, like planks. */
        AXE,
        /** Needs a pickaxe to drop, like stone bricks. */
        PICKAXE,
        /** Needs a stone pickaxe or better to drop, like an iron block. */
        STONE_PICKAXE;

        public boolean requiresTool() {
            return this == PICKAXE || this == STONE_PICKAXE;
        }
    }

    private final String suffix;
    private final float hardness;
    private final float resistance;
    private final SoundType sound;
    private final NoteBlockInstrument instrument;
    private final Tool tool;
    private final int light;
    /** How fast it burns away once alight, as {@code FireBlock.setFlammable} takes it; zero is fireproof. */
    private final int flammability;

    DecorKind(String suffix, float hardness, float resistance, SoundType sound, NoteBlockInstrument instrument,
            Tool tool, int light, int flammability) {
        this.suffix = suffix;
        this.hardness = hardness;
        this.resistance = resistance;
        this.sound = sound;
        this.instrument = instrument;
        this.tool = tool;
        this.light = light;
        this.flammability = flammability;
    }

    public String suffix() { return suffix; }
    public float hardness() { return hardness; }
    public float resistance() { return resistance; }
    public SoundType sound() { return sound; }
    public Tool tool() { return tool; }
    public int light() { return light; }
    public int flammability() { return flammability; }
    public boolean flammable() { return flammability > 0; }

    /** How eagerly fire catches from a neighbour: wool's 30 and planks' 5, the vanilla pairs. */
    public int encouragement() {
        return this == WOVEN_CLOTH ? 30 : this == PAINTED_PLANKS ? 5 : 0;
    }

    /** Glass rules: light passes, faces between two of it are culled, nothing spawns or suffocates in it. */
    public boolean translucent() {
        return this == CRYSTAL;
    }

    /** {@code red_runestone_bricks}: colour first, as vanilla names its dyed blocks. */
    public String path(DyeColor colour) {
        return colour.getName() + "_" + suffix;
    }

    public BlockBehaviour.Properties properties(DyeColor colour) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(colour.getMapColor())
                .instrument(instrument)
                .strength(hardness, resistance)
                .sound(sound);
        if (tool.requiresTool()) {
            properties.requiresCorrectToolForDrops();
        }
        if (light > 0) {
            int level = light;
            properties.lightLevel(state -> level);
        }
        if (flammable()) {
            properties.ignitedByLava();
        }
        if (this == GLIMMER_LAMP) {
            properties.isRedstoneConductor((state, level, pos) -> false);
        }
        if (translucent()) {
            properties.noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false);
        }
        return properties;
    }

    /** A crystal is stained glass underneath: face culling, skylight, and it tints a beacon beam. */
    public Block create(DyeColor colour, BlockBehaviour.Properties properties) {
        return translucent() ? new StainedGlassBlock(colour, properties) : new Block(properties);
    }
}
