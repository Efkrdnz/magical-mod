package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReverieTest {
    private static final String GRASS = "minecraft:grass_block";
    private static final String VILLAGER = "minecraft:villager";

    private static Lexicon knowing(String... keys) {
        Lexicon lexicon = new Lexicon();
        for (String key : keys) {
            lexicon.gaze(key);
        }
        return lexicon;
    }

    @Test
    void aQuarterTurnIsClockwiseFromAbove() {
        assertEquals(new Offset(0, 2, 1), new Offset(1, 2, 0).rotate(1), "east turns to south");
        assertEquals(new Offset(-1, 0, 0), new Offset(1, 0, 0).rotate(2));
        assertEquals(new Offset(1, 0, 0), new Offset(1, 0, 0).rotate(-4));
        assertEquals(new Offset(0, 0, -1), new Offset(1, 0, 0).rotate(3), "east turns to north");
    }

    @Test
    void reachIsAnExactBoxEvenForAnOffsetThatOverflowsAbs() {
        assertFalse(new Offset(Integer.MIN_VALUE, 0, 0).within(24));
        assertFalse(new Offset(0, Integer.MIN_VALUE, 0).within(24));
        assertFalse(new Offset(0, 0, Integer.MIN_VALUE).within(24));
        assertTrue(new Offset(24, -24, 24).within(24));
        assertFalse(new Offset(25, 0, 0).within(24));
        assertFalse(new Offset(0, 0, -25).within(24));

        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS);
        assertEquals(Reverie.Refusal.TOO_FAR, reverie.addBlock(new Offset(Integer.MIN_VALUE, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.NONE, reverie.addBlock(new Offset(24, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.TOO_FAR, reverie.addBlock(new Offset(25, 0, 0), GRASS, lexicon));
    }

    @Test
    void aBlockThatBridgesTwoClustersMergesTheirSenses() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS);
        reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon);
        reverie.addBlock(new Offset(2, 0, 0), GRASS, lexicon);
        reverie.setClusterSenses(new Offset(2, 0, 0), EnumSet.of(Sense.SHADOW));
        assertEquals(2, reverie.clusters().size());

        reverie.addBlock(new Offset(1, 0, 0), GRASS, lexicon);
        assertEquals(1, reverie.clusters().size());
        for (ImaginedBlock block : reverie.clusters().get(0)) {
            assertEquals(Set.of(Sense.SHADOW), block.senses());
        }
        assertEquals(1, reverie.senseLayers());
    }

    @Test
    void aForgedTagCannotLeaveAClusterWithMixedSensesOrElementsOutOfReach() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS);
        reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon);
        reverie.addBlock(new Offset(1, 0, 0), GRASS, lexicon);
        net.minecraft.nbt.CompoundTag tag = ReverieNbt.save(reverie);
        net.minecraft.nbt.ListTag blocks = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
        blocks.getCompound(0).putInt("senses", Sense.mask(EnumSet.of(Sense.SOUND)));
        blocks.getCompound(1).putInt("senses", Sense.mask(EnumSet.of(Sense.SCENT)));
        net.minecraft.nbt.CompoundTag far = new net.minecraft.nbt.CompoundTag();
        far.putInt("x", Integer.MIN_VALUE);
        far.putString("id", GRASS);
        blocks.add(far);

        Reverie back = ReverieNbt.load(tag);
        assertEquals(2, back.size(), "the element at Integer.MIN_VALUE is dropped");
        for (ImaginedBlock block : back.blocks()) {
            assertEquals(Set.of(Sense.SOUND, Sense.SCENT), block.senses());
        }
        assertEquals(2, back.senseLayers());
    }

    @Test
    void copyingAReverieOntoItselfChangesNothing() {
        Reverie reverie = new Reverie();
        reverie.addBlock(new Offset(0, 0, 0), GRASS, knowing("block:" + GRASS));
        reverie.setName("Same");
        reverie.copyFrom(reverie);
        assertEquals(1, reverie.size());
        assertEquals("Same", reverie.name());
    }

    @Test
    void aReverieRefusesWhatTheWielderHasNeverSeenWhatIsTakenAndWhatIsTooFar() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS, "creature:" + VILLAGER);
        assertEquals(Reverie.Refusal.NONE, reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.OCCUPIED, reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.OCCUPIED, reverie.addFigment(new Offset(0, 0, 0), VILLAGER, lexicon));
        assertEquals(Reverie.Refusal.UNKNOWN, reverie.addBlock(new Offset(1, 0, 0), "minecraft:diamond_block", lexicon));
        assertEquals(Reverie.Refusal.TOO_FAR, reverie.addBlock(new Offset(25, 0, 0), GRASS, lexicon));
        assertEquals(Reverie.Refusal.NONE, reverie.addFigment(new Offset(3, 0, 0), VILLAGER, lexicon));
        assertEquals(2, reverie.size());
    }

    @Test
    void theBudgetCountsBlocksAndFigmentsAlike() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS);
        for (int i = 0; i < 18; i++) {
            assertEquals(Reverie.Refusal.NONE, reverie.addBlock(new Offset(i - 9, 0, 0), GRASS, lexicon));
        }
        assertEquals(Reverie.Refusal.FULL, reverie.addBlock(new Offset(10, 0, 0), GRASS, lexicon),
                "one impression buys 16 + 2 = 18 elements");
    }

    @Test
    void clustersAreFaceConnectedAndSensesCoverAWholeCluster() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS);
        reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon);
        reverie.addBlock(new Offset(1, 0, 0), GRASS, lexicon);
        reverie.addBlock(new Offset(2, 1, 0), GRASS, lexicon);
        List<List<ImaginedBlock>> clusters = reverie.clusters();
        assertEquals(2, clusters.size(), "a diagonal is not a face");
        assertEquals(2, clusters.get(0).size());

        reverie.setClusterSenses(new Offset(1, 0, 0), EnumSet.of(Sense.SHADOW));
        assertEquals(Set.of(Sense.SHADOW), reverie.clusters().get(0).get(0).senses());
        assertEquals(Set.of(), reverie.clusters().get(1).get(0).senses());

        reverie.addBlock(new Offset(0, 0, 1), GRASS, lexicon);
        assertEquals(Set.of(Sense.SHADOW), reverie.clusters().get(0).get(2).senses(),
                "a block laid against a cluster joins its senses");
        assertEquals(1, reverie.senseLayers());
    }

    @Test
    void aFigmentCarriesItsScriptAndItsSenses() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("creature:" + VILLAGER);
        reverie.addFigment(new Offset(0, 0, 0), VILLAGER, lexicon);
        assertEquals(Script.DEFAULT, reverie.figments().get(0).script());
        reverie.setScript(0, new Script(Stance.WANDER, Reaction.FLEE));
        reverie.setFigmentSenses(0, EnumSet.of(Sense.SOUND, Sense.SHADOW));
        assertEquals(Reaction.FLEE, reverie.figments().get(0).script().reaction());
        assertEquals(2, reverie.senseLayers());
        assertEquals(1.25F * 1.15F, Sense.multiplier(reverie.figments().get(0).senses()), 1.0E-6F);
    }

    @Test
    void itSurvivesNbtAndACopyIsIndependent() {
        Reverie reverie = new Reverie();
        Lexicon lexicon = knowing("block:" + GRASS, "creature:" + VILLAGER);
        reverie.setName("Pit");
        reverie.setFacing(3);
        reverie.addBlock(new Offset(0, 0, 0), GRASS, lexicon);
        reverie.setClusterSenses(new Offset(0, 0, 0), EnumSet.of(Sense.SCENT));
        reverie.addFigment(new Offset(2, 0, 2), VILLAGER, lexicon);
        reverie.setScript(0, new Script(Stance.GUARD, Reaction.STARE));

        Reverie back = ReverieNbt.load(ReverieNbt.save(reverie));
        assertEquals("Pit", back.name());
        assertEquals(3, back.facing());
        assertEquals(Set.of(Sense.SCENT), back.blocks().iterator().next().senses());
        assertEquals(new Script(Stance.GUARD, Reaction.STARE), back.figments().get(0).script());

        Reverie copy = reverie.copy();
        reverie.clear();
        assertEquals(2, copy.size());
        assertTrue(reverie.isEmpty());
    }
}
