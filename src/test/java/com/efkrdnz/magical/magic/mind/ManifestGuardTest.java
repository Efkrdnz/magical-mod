package com.efkrdnz.magical.magic.mind;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the guard reads as imagined matter still standing, and what the tag keeps out of the world. */
class ManifestGuardTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aWaterloggedSlabIsStillStanding() {
        // A bucket on a real imagined slab must not turn its cell into one the next slab merges into.
        assertTrue(ManifestGuard.standing(Blocks.OAK_SLAB.defaultBlockState()
                .setValue(SlabBlock.WATERLOGGED, true)));
        assertTrue(ManifestGuard.standing(Blocks.CANDLE.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, true)));
        assertTrue(ManifestGuard.standing(Blocks.COBBLESTONE.defaultBlockState()));
    }

    @Test
    void whatABreakLeavesIsNotStanding() {
        assertFalse(ManifestGuard.standing(Blocks.AIR.defaultBlockState()));
        assertFalse(ManifestGuard.standing(Blocks.WATER.defaultBlockState()));
        assertFalse(ManifestGuard.standing(Blocks.LAVA.defaultBlockState()));
    }

    @Test
    void theTagKeepsOutWhatBuildsGolemsWithersAndPortals() throws IOException {
        String tag;
        try (InputStream in = ManifestGuardTest.class.getResourceAsStream("/data/magical/tags/block/never_manifests.json")) {
            assertNotNull(in, "the never_manifests tag is missing");
            tag = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String id : new String[] {
                "minecraft:carved_pumpkin", "minecraft:jack_o_lantern", "minecraft:iron_block", "minecraft:snow_block",
                "minecraft:soul_sand", "minecraft:soul_soil", "minecraft:obsidian", "minecraft:crying_obsidian"}) {
            assertTrue(tag.contains("\"" + id + "\""), id + " could be made real and finish a golem, a wither or a portal");
        }
        assertTrue(tag.contains("\"replace\": false"), "the tag clobbers others");
    }
}
