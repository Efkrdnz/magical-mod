package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlausibilityTest {
    private static final float EPSILON = 1.0E-5F;

    /** A flat world: a floor of {@code floor} at y 0 from -12 to 12, air above, optional daylight. */
    private static final class FlatWorld implements MindWorld {
        private final Map<String, String> blocks = new HashMap<>();
        private final boolean daylight;

        FlatWorld(String floor, boolean daylight) {
            this.daylight = daylight;
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    blocks.put(x + "," + 0 + "," + z, floor);
                }
            }
        }

        void set(int x, int y, int z, String id) {
            blocks.put(x + "," + y + "," + z, id);
        }

        @Override public String blockId(int x, int y, int z) {
            return blocks.getOrDefault(x + "," + y + "," + z, "minecraft:air");
        }

        @Override public boolean solid(int x, int y, int z) {
            String id = blockId(x, y, z);
            return !id.equals("minecraft:air") && !id.equals("minecraft:water");
        }

        @Override public boolean water(int x, int y, int z) {
            return blockId(x, y, z).equals("minecraft:water");
        }

        @Override public boolean openSkyDaylight(int x, int y, int z) {
            return daylight;
        }
    }

    private static Lexicon studied(String key, int gazes) {
        Lexicon lexicon = new Lexicon();
        lexicon.learn(key, gazes);
        return lexicon;
    }

    @Test
    void aGrassLidOverAPitIsHeldByItsRimAndMatchesTheField() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        world.set(0, 0, 0, "minecraft:air");
        world.set(1, 0, 0, "minecraft:air");
        List<Plausibility.Placed> lid = List.of(
                new Plausibility.Placed(0, 0, 0, "minecraft:grass_block"),
                new Plausibility.Placed(1, 0, 0, "minecraft:grass_block"));
        Plausibility.Reading reading = Plausibility.cluster(world, lid,
                studied("block:minecraft:grass_block", 5), 2);
        assertEquals(0.90F, reading.p(), EPSILON, "0.5 + context 0.30 + fidelity 0.10");
        assertTrue(reading.terms().stream().noneMatch(t -> t.key().equals("unsupported")));
    }

    @Test
    void aFloatingSlabPaysForHangingInTheAir() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        Plausibility.Reading reading = Plausibility.cluster(world,
                List.of(new Plausibility.Placed(0, 5, 0, "minecraft:grass_block")),
                studied("block:minecraft:grass_block", 5), 1);
        assertEquals(0.50F, reading.p(), EPSILON, "0.5 - 0.40 + 0.30 + 0.10");
    }

    @Test
    void theLazyLieIsADiamondWallInAField() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        Plausibility.Reading reading = Plausibility.cluster(world,
                List.of(new Plausibility.Placed(0, 1, 0, "minecraft:diamond_block"),
                        new Plausibility.Placed(0, 2, 0, "minecraft:diamond_block")),
                studied("block:minecraft:diamond_block", 1), 2);
        assertEquals(0.30F, reading.p(), EPSILON, "0.5 - alien 0.20");
    }

    @Test
    void aVillagerThatFleesIsLikeItsKindAndOneThatChasesIsNot() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        Lexicon lexicon = studied("creature:minecraft:villager", 5);
        Plausibility.Placed at = new Plausibility.Placed(0, 1, 0, "minecraft:villager");
        assertEquals(0.70F, Plausibility.figment(world, at, new Script(Stance.WANDER, Reaction.FLEE), lexicon, 1).p(), EPSILON);
        assertEquals(0.40F, Plausibility.figment(world, at, new Script(Stance.WANDER, Reaction.CHASE), lexicon, 1).p(), EPSILON);
    }

    @Test
    void anUndeadFigmentInDaylightThatDoesNotBurnIsDoubted() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", true);
        Plausibility.Reading reading = Plausibility.figment(world,
                new Plausibility.Placed(0, 1, 0, "minecraft:zombie"),
                new Script(Stance.IDLE, Reaction.CHASE), studied("creature:minecraft:zombie", 1), 1);
        assertEquals(0.30F, reading.p(), EPSILON, "0.5 - habitat 0.30 + like its kind 0.10");
    }

    @Test
    void aFishOnDryLandIsOutOfItsHabitat() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", false);
        world.set(3, 1, 3, "minecraft:water");
        Lexicon lexicon = studied("creature:minecraft:cod", 1);
        Script idle = Script.DEFAULT;
        assertEquals(0.30F, Plausibility.figment(world, new Plausibility.Placed(0, 1, 0, "minecraft:cod"), idle, lexicon, 1).p(), EPSILON,
                "0.5 - habitat 0.30 + like its kind 0.10: ignoring is a fish's nature");
        assertEquals(0.60F, Plausibility.figment(world, new Plausibility.Placed(3, 1, 3, "minecraft:cod"), idle, lexicon, 1).p(), EPSILON);
    }

    @Test
    void sizeCostsATenthPerDoublingAboveThirtyTwo() {
        assertEquals(0.0F, Plausibility.sizeTerm(32), EPSILON);
        assertEquals(-0.10F, Plausibility.sizeTerm(64), EPSILON);
        assertEquals(-0.20F, Plausibility.sizeTerm(128), EPSILON);
    }

    @Test
    void theReadingIsClampedAndReportsOnlyWhatMovedIt() {
        FlatWorld world = new FlatWorld("minecraft:grass_block", true);
        Plausibility.Reading reading = Plausibility.figment(world,
                new Plausibility.Placed(0, 1, 0, "minecraft:zombie"),
                new Script(Stance.IDLE, Reaction.FLEE), studied("creature:minecraft:zombie", 1), 128);
        assertEquals(Plausibility.FLOOR, reading.p(), EPSILON, "0.5 - 0.30 - 0.20 - 0.20 is below the floor");
        assertEquals(List.of("habitat", "unlike_kind", "size"), reading.terms().stream().map(Plausibility.Term::key).toList());
    }
}
