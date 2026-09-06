package com.efkrdnz.magical.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The evolution trees must diverge and converge in the shape the design promises. */
class ClassTreeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyBaseHasFourDisciplinesThreeMasteriesAndAtMostThreeApexes() {
        for (MagicalClassDefinition base : MagicalClasses.startingRoots()) {
            List<MagicalClassDefinition> tree = MagicalClasses.treeOf(base.id());
            assertEquals(4, count(tree, 1), base.id() + " disciplines");
            assertEquals(3, count(tree, 2), base.id() + " masteries");
            int apexes = count(tree, 3);
            assertTrue(apexes >= 2 && apexes <= 3, base.id() + " must end in 2 or 3 apexes, found " + apexes);
        }
    }

    @Test
    void everyNonBaseNodeConvergesFromRealParentsInTheSameTree() {
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            if (definition.isBase()) {
                continue;
            }
            assertTrue(!definition.parents().isEmpty(), definition.id() + " has no parents");
            for (ResourceLocation parentId : definition.parents()) {
                MagicalClassDefinition parent = MagicalClasses.get(parentId);
                assertNotNull(parent, definition.id() + " names a missing parent " + parentId);
                assertEquals(definition.tier() - 1, parent.tier(), definition.id() + " parent " + parentId + " is the wrong tier");
                assertEquals(MagicalClasses.baseOf(definition.id()), MagicalClasses.baseOf(parentId),
                        definition.id() + " and its parent belong to different trees");
            }
        }
    }

    @Test
    void everyNodeReachesItsBaseWithoutCycles() {
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            Set<ResourceLocation> seen = new HashSet<>();
            MagicalClassDefinition cursor = definition;
            while (cursor != null && !cursor.isBase()) {
                assertTrue(seen.add(cursor.id()), "cycle through " + cursor.id());
                cursor = MagicalClasses.get(cursor.parentId());
            }
            assertNotNull(cursor, definition.id() + " never reaches a base");
            assertEquals(cursor.id(), MagicalClasses.baseOf(definition.id()));
        }
    }

    @Test
    void costsAscendWithTierAndBasesAreFree() {
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            if (definition.isBase()) {
                assertEquals(0, definition.xpCost(), definition.id() + " base must be free");
            } else {
                assertTrue(definition.xpCost() > 0, definition.id() + " must cost something");
            }
            for (ResourceLocation parentId : definition.parents()) {
                MagicalClassDefinition parent = MagicalClasses.get(parentId);
                assertTrue(definition.xpCost() > parent.xpCost(), definition.id() + " must cost more than " + parentId);
            }
        }
    }

    @Test
    void everyApexIsReachableFromMoreThanOneBranch() {
        for (MagicalClassDefinition base : MagicalClasses.startingRoots()) {
            for (MagicalClassDefinition definition : MagicalClasses.treeOf(base.id())) {
                if (definition.tier() == 3) {
                    assertTrue(definition.parents().size() >= 2,
                            definition.id() + " is an apex and should converge from at least two masteries");
                }
            }
        }
    }

    /**
     * The first version of this only checked for identical coordinates, which passed while the
     * rendered graph was a pile of overlapping labels. Separation is the property that matters.
     */
    @Test
    void noTwoNodesOverlapOnScreen() {
        List<ClassTreeLayout.Node> nodes = ClassTreeLayout.nodes();
        assertEquals(MagicalClasses.all().size(), nodes.size(), "every class must be laid out");
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                ClassTreeLayout.Node a = nodes.get(i);
                ClassTreeLayout.Node b = nodes.get(j);
                float dx = Math.abs(a.x() - b.x());
                float dy = Math.abs(a.y() - b.y());
                // Boxes overlap only when they are too close on BOTH axes at once.
                boolean overlaps = dx < ClassTreeLayout.NODE_WIDTH && dy < ClassTreeLayout.NODE_HEIGHT;
                assertTrue(!overlaps, a.id() + " overlaps " + b.id() + " (dx=" + dx + ", dy=" + dy + ")");
            }
        }
        assertTrue(ClassTreeLayout.extent() > 400.0F, "graph should be big enough to need panning");
    }

    @Test
    void rewardedPassivesAndSkillsAllResolve() {
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            for (ResourceLocation skillId : definition.rewardSkills()) {
                assertNotNull(com.efkrdnz.magical.magic.MagicContent.get(skillId),
                        definition.id() + " rewards a missing skill " + skillId);
            }
            for (ResourceLocation passiveId : definition.rewardPassives()) {
                assertNotNull(com.efkrdnz.magical.magic.MagicPassiveContent.get(passiveId),
                        definition.id() + " rewards a missing passive " + passiveId);
            }
        }
    }

    /**
     * The whole point of the reward pass: walking a branch has to change how you play. A node that
     * grants nothing is a dead click, and a passive granted by two different nodes makes neither of
     * them mean anything.
     */
    @Test
    void everyNonBaseNodeGrantsSomethingAndNoClassPassiveIsGrantedTwice() {
        Set<ResourceLocation> seen = new HashSet<>();
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            // The Spell Creator line is a utility branch, not one of the five evolution trees: its
            // reward is the spell-creation screen itself rather than a skill or a passive.
            if (definition.isBase() || !MagicalClasses.isStartingRoot(MagicalClasses.baseOf(definition.id()))) {
                continue;
            }
            assertTrue(!definition.rewardSkills().isEmpty() || !definition.rewardPassives().isEmpty(),
                    definition.id() + " grants nothing at all");
            for (ResourceLocation passiveId : definition.rewardPassives()) {
                if (!com.efkrdnz.magical.magic.MagicPassiveContent.isClassPassive(passiveId)) {
                    continue; // the seven sins ride along on the apexes and are checked separately
                }
                assertTrue(seen.add(passiveId),
                        passiveId + " is granted by more than one node, so neither node is distinctive");
            }
        }
        assertEquals(com.efkrdnz.magical.magic.MagicPassiveContent.classPassives().size(), seen.size(),
                "every registered class passive should be granted by exactly one node");
    }

    /** Each of the seven sins has exactly one source in the trees; before this they doubled up. */
    @Test
    void eachSinPassiveHasExactlyOneSource() {
        Set<ResourceLocation> seen = new HashSet<>();
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            for (ResourceLocation passiveId : definition.rewardPassives()) {
                if (com.efkrdnz.magical.magic.MagicPassiveContent.isSinPassive(passiveId)) {
                    assertTrue(seen.add(passiveId), passiveId + " is granted by more than one node");
                }
            }
        }
        assertEquals(7, seen.size(), "all seven sins must remain obtainable");
    }

    private static int count(List<MagicalClassDefinition> tree, int tier) {
        return (int) tree.stream().filter(definition -> definition.tier() == tier).count();
    }
}
