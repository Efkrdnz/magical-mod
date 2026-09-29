package com.efkrdnz.magical.magic.mind;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@link Manifestation#refusesByNature} turns away without looking at the world: anything that
 * could leave its cell, grow, or spawn something real on its own clock. The {@code never_manifests}
 * tag is not bound without a data pack, so the tag's own entries are held by {@code ManifestGameTests}.
 */
class ManifestationRefusesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static boolean refused(String id) {
        return Manifestation.refusesByNature(id, Manifestation.stateOf(id));
    }

    @Test
    void whatFallsGrowsOrKeepsABlockEntityIsNeverMadeReal() {
        for (String id : new String[] {
                "minecraft:sand", "minecraft:gravel", "minecraft:anvil", "minecraft:pointed_dripstone",
                "minecraft:oak_sapling", "minecraft:grass_block", "minecraft:wheat", "minecraft:cactus",
                "minecraft:chest", "minecraft:ice", "minecraft:frosted_ice",
                "minecraft:water", "minecraft:lava", "minecraft:fire",
                "minecraft:no_such_block"}) {
            assertTrue(refused(id), id + " would be made real");
        }
    }

    @Test
    void plainMatterStillManifests() {
        for (String id : new String[] {
                "minecraft:cobblestone", "minecraft:stone", "minecraft:bricks", "minecraft:oak_fence",
                "minecraft:oak_trapdoor", "minecraft:oak_planks"}) {
            assertFalse(refused(id), id + " is refused");
        }
    }
}
