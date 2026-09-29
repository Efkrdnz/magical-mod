package com.efkrdnz.magical.magic.mind;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What an imagined block does to a body that believes it. A lie that hurts is believed more: a
 * believer who touches imagined lava is burned by it, magic damage times their belief, and comes away
 * surer. Fire and fluids are never placed as real blocks, so imagined lava only ever burns the mind.
 */
public final class PhantomHarm {
    /** How much surer a believer is after a lie has hurt them. */
    public static final float RAISE = 0.10F;
    /** Ticks between burns while a believer stays inside. */
    public static final int INTERVAL = 20;

    private static final Map<String, Float> HARM = Map.of(
            "minecraft:lava", 4.0F,
            "minecraft:fire", 1.0F,
            "minecraft:soul_fire", 2.0F,
            "minecraft:magma_block", 1.0F,
            "minecraft:cactus", 1.0F,
            "minecraft:sweet_berry_bush", 1.0F);
    private static final Set<String> UNMANIFESTABLE = Set.of(
            "minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:water");

    private PhantomHarm() {}

    public static float of(String blockId) {
        return HARM.getOrDefault(blockId, 0.0F);
    }

    /** A cluster hurts as much as the worst block in it. */
    public static float of(List<String> blockIds) {
        float worst = 0.0F;
        for (String id : blockIds) {
            worst = Math.max(worst, of(id));
        }
        return worst;
    }

    public static float amount(float base, float belief) {
        return base * belief;
    }

    /** Whether a block is never placed for real, however many minds agree on it. */
    public static boolean unmanifestable(String blockId) {
        return UNMANIFESTABLE.contains(blockId);
    }
}
