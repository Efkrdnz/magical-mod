package com.efkrdnz.magical.block.decor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/**
 * One masonry block: a cut of stone, in one of six greys from onyx to chalk, in one condition.
 *
 * <p>The dyed {@link DecorKind}s are colour; this family is the other thing a builder reaches for -
 * a value ramp, and the wear that makes a wall look like it has stood somewhere. Five cuts, six
 * greys and four conditions make 120 blocks. Like the dyed kinds, each is the vanilla block it
 * stands in for: cobblestone is cobblestone, every other cut is stone bricks, cracked and mossy
 * change nothing but the look (as vanilla's cracked and mossy stone bricks do), and muddy takes
 * mud bricks' blast resistance and sound. {@code MasonryTest} holds all of it to the vanilla
 * numbers; {@code scripts/decor-blocks.py} draws and writes the rest from the same three lists.
 */
public record Masonry(Cut cut, Shade shade, Condition condition) {

    public enum Cut {
        BRICKS("%s_bricks", 1.5F, 6.0F, SoundType.STONE),
        TILES("%s_tiles", 1.5F, 6.0F, SoundType.DEEPSLATE_TILES),
        COBBLESTONE("%s_cobblestone", 2.0F, 6.0F, SoundType.STONE),
        ASHLAR("%s_ashlar", 1.5F, 6.0F, SoundType.DEEPSLATE_BRICKS),
        POLISHED("polished_%s", 1.5F, 6.0F, SoundType.POLISHED_DEEPSLATE);

        private final String pattern;
        private final float hardness;
        private final float resistance;
        private final SoundType sound;

        Cut(String pattern, float hardness, float resistance, SoundType sound) {
            this.pattern = pattern;
            this.hardness = hardness;
            this.resistance = resistance;
            this.sound = sound;
        }
    }

    /** Darkest first, the order the creative tab lays each run out in. */
    public enum Shade {
        ONYX(MapColor.COLOR_BLACK),
        CHARCOAL(MapColor.DEEPSLATE),
        SLATE(MapColor.COLOR_GRAY),
        ASH(MapColor.STONE),
        DOVE(MapColor.COLOR_LIGHT_GRAY),
        CHALK(MapColor.QUARTZ);

        private final MapColor mapColor;

        Shade(MapColor mapColor) {
            this.mapColor = mapColor;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Condition {
        PLAIN(""),
        CRACKED("cracked_"),
        MOSSY("mossy_"),
        MUDDY("muddy_");

        private final String prefix;

        Condition(String prefix) {
            this.prefix = prefix;
        }
    }

    /** What mud bricks survive, and so what a mud-packed wall survives. */
    public static final float MUD_RESISTANCE = 3.0F;

    /** Every block of the family: cut by cut, condition by condition, each run dark to light. */
    public static List<Masonry> all() {
        List<Masonry> out = new ArrayList<>();
        for (Cut cut : Cut.values()) {
            for (Condition condition : Condition.values()) {
                for (Shade shade : Shade.values()) {
                    out.add(new Masonry(cut, shade, condition));
                }
            }
        }
        return out;
    }

    /** {@code mossy_slate_bricks}, {@code cracked_polished_onyx}: the condition, then the cut named by its grey. */
    public String path() {
        return condition.prefix + String.format(Locale.ROOT, cut.pattern, shade.id());
    }

    public float hardness() {
        return cut.hardness;
    }

    public float resistance() {
        return condition == Condition.MUDDY ? Math.min(cut.resistance, MUD_RESISTANCE) : cut.resistance;
    }

    public SoundType sound() {
        return condition == Condition.MUDDY ? SoundType.MUD_BRICKS : cut.sound;
    }

    /** Stone, every one of it: a pickaxe to break it and a pickaxe to get it back. */
    public BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of()
                .mapColor(shade.mapColor)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(hardness(), resistance())
                .sound(sound());
    }
}
