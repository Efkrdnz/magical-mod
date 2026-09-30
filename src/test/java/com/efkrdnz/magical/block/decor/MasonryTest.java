package com.efkrdnz.magical.block.decor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The masonry is the vanilla block each cut stands in for, worn the way vanilla wears it, and every
 * one of its 120 blocks has its files. {@code scripts/decor-blocks.py} writes the files, and none of
 * a miss here fails a build.
 */
class MasonryTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Block analogue(Masonry.Cut cut) {
        return cut == Masonry.Cut.COBBLESTONE ? Blocks.COBBLESTONE : Blocks.STONE_BRICKS;
    }

    @Test
    void thereAreAHundredAndTwentyAndNoTwoShareAName() {
        List<Masonry> all = Masonry.all();
        assertEquals(5 * 6 * 4, all.size());
        Set<String> paths = new HashSet<>();
        all.forEach(masonry -> assertTrue(paths.add(masonry.path()), masonry.path() + " twice"));
        for (DecorKind kind : DecorKind.values()) {
            for (DyeColor colour : DecorKind.COLOURS) {
                assertTrue(!paths.contains(kind.path(colour)), kind.path(colour) + " is both a dyed and a masonry block");
            }
        }
    }

    @Test
    void theNamesReadConditionThenStone() {
        assertEquals("mossy_slate_bricks", new Masonry(Masonry.Cut.BRICKS, Masonry.Shade.SLATE, Masonry.Condition.MOSSY).path());
        assertEquals("cracked_polished_onyx", new Masonry(Masonry.Cut.POLISHED, Masonry.Shade.ONYX, Masonry.Condition.CRACKED).path());
        assertEquals("chalk_cobblestone", new Masonry(Masonry.Cut.COBBLESTONE, Masonry.Shade.CHALK, Masonry.Condition.PLAIN).path());
    }

    @Test
    void everyCutBreaksLikeItsVanillaStoneAndWearsLikeIt() {
        for (Masonry masonry : Masonry.all()) {
            Block vanilla = analogue(masonry.cut());
            assertEquals(vanilla.defaultDestroyTime(), masonry.hardness(), masonry.path() + " hardness");
            switch (masonry.condition()) {
                // Vanilla's cracked and mossy stone bricks are stone bricks in every number but the look.
                case PLAIN, CRACKED, MOSSY -> assertEquals(vanilla.getExplosionResistance(), masonry.resistance(), masonry.path());
                case MUDDY -> {
                    assertEquals(Blocks.MUD_BRICKS.getExplosionResistance(), masonry.resistance(), masonry.path() + " takes mud's resistance");
                    assertEquals(SoundType.MUD_BRICKS, masonry.sound(), masonry.path() + " sounds like mud bricks");
                }
            }
        }
        assertEquals(Blocks.CRACKED_STONE_BRICKS.defaultDestroyTime(), Blocks.STONE_BRICKS.defaultDestroyTime(),
                "the premise: vanilla's cracked bricks break like whole ones");
        assertEquals(Blocks.MOSSY_STONE_BRICKS.getExplosionResistance(), Blocks.STONE_BRICKS.getExplosionResistance(),
                "the premise: vanilla's mossy bricks survive like clean ones");
    }

    @Test
    void everyBlockHasItsTextureModelsLootNameAndPickaxe() throws IOException {
        String lang = text("/assets/magical/lang/en_us.json");
        Set<String> pickaxe = new HashSet<>();
        json("/data/minecraft/tags/block/mineable/pickaxe.json").getAsJsonArray("values")
                .forEach(value -> pickaxe.add(value.getAsString()));
        for (Masonry masonry : Masonry.all()) {
            String path = masonry.path();
            BufferedImage sprite = image("masonry", path);
            assertEquals(16, sprite.getWidth(), path);
            assertEquals("magical:block/masonry/" + path,
                    json("/assets/magical/models/block/" + path + ".json").getAsJsonObject("textures").get("all").getAsString(), path);
            assertNotNull(MasonryTest.class.getResource("/assets/magical/blockstates/" + path + ".json"), path + " blockstate");
            assertNotNull(MasonryTest.class.getResource("/assets/magical/items/" + path + ".json"), path + " item");
            assertEquals("magical:" + path, json("/data/magical/loot_table/blocks/" + path + ".json")
                    .getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                    .getAsJsonObject().get("name").getAsString(), path + " drops itself");
            assertTrue(lang.contains("\"block.magical." + path + "\""), path + " has a name");
            assertTrue(pickaxe.contains("magical:" + path), path + " is a pickaxe block");
        }
        assertTrue(lang.contains("\"itemGroup.magical.masonry\""), "the tab has a name");
    }

    @Test
    void noTwoSpritesInEitherFamilyAreTheSame() throws IOException {
        Map<Integer, String> seen = new HashMap<>();
        for (Masonry masonry : Masonry.all()) {
            record(seen, image("masonry", masonry.path()), masonry.path());
        }
        for (DecorKind kind : DecorKind.values()) {
            for (DyeColor colour : DecorKind.COLOURS) {
                record(seen, image("decor", kind.path(colour)), kind.path(colour));
            }
        }
    }

    private static void record(Map<Integer, String> seen, BufferedImage sprite, String path) {
        String before = seen.put(Arrays.hashCode(sprite.getRGB(0, 0, 16, 16, null, 0, 16)), path);
        assertNull(before, path + " is drawn the same as " + before);
    }

    private static BufferedImage image(String folder, String path) throws IOException {
        try (InputStream in = MasonryTest.class.getResourceAsStream("/assets/magical/textures/block/" + folder + "/" + path + ".png")) {
            assertNotNull(in, path + " has no texture");
            return ImageIO.read(in);
        }
    }

    private static JsonObject json(String resource) throws IOException {
        try (InputStream in = MasonryTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String text(String resource) throws IOException {
        try (InputStream in = MasonryTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
