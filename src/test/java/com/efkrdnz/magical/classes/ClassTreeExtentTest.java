package com.efkrdnz.magical.classes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link ClassTreeLayout#extent()} is a global maximum over every node, and it is load-bearing in a
 * way nothing about it looks: it feeds {@code ClassTreeScreen.fitZoom()} (ClassTreeScreen:67-71),
 * which is the zoom the screen opens at, and {@code ClassTreeScreen:227} draws a node's
 * <b>name</b> only at {@code zoom >= 0.40F}. The default fit today is about 0.43.
 *
 * <p>So the Paths of Power is one node placement away from opening with no label on any class in
 * the game - not the new one, <em>every</em> one - for every player, silently, with a completely
 * green build. A single node out at radius 660 would do it. Nothing else in the build models the
 * relationship between where a node is put and whether the screen can be read, so it is modelled
 * here, in the arithmetic the screen actually runs.</p>
 */
class ClassTreeExtentTest {

    // ClassTreeScreen's own numbers, duplicated because they are private to a client screen a unit
    // test should not load. If any of them move there, they move here and this test says so.
    private static final float MIN_ZOOM = 0.28F;
    private static final float MAX_ZOOM = 1.4F;
    /** Below this the screen stops drawing node names entirely. ClassTreeScreen:227. */
    private static final float LABEL_ZOOM = 0.40F;
    /** The screen takes the window up to these, so this is the most generous fit there is. */
    private static final int MAX_IMAGE_WIDTH = 900;
    private static final int MAX_IMAGE_HEIGHT = 600;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The hidden Sword Summoner chain is hand-placed in the wedge between two sectors and inside
     * the outer ring precisely so this number does not move. If it ever does, the reason is a node
     * placement and the consequence is the label threshold below.
     */
    @Test
    void theGraphIsExactlyAsBigAsItWasBeforeTheHiddenChain() {
        assertEquals(541.0F, ClassTreeLayout.extent(), 1.0F,
                "extent() sets the opening zoom; growing it strips the name off every class node");
    }

    @Test
    void everyHiddenNodeSitsStrictlyInsideTheExistingEnvelope() {
        float extent = ClassTreeLayout.extent();
        List<ResourceLocation> chain = List.of(
                MagicalClasses.SWORD_SUMMONER,
                MagicalClasses.SWORD_RIDER,
                MagicalClasses.SWORD_SAINT,
                MagicalClasses.SWORD_GOD);
        for (ResourceLocation id : chain) {
            ClassTreeLayout.Node node = ClassTreeLayout.get(id);
            assertNotNull(node, id + " must be laid out; all().size() == nodes().size()");
            assertTrue(Math.abs(node.x()) < extent, id + " sets the graph's x extent at " + node.x());
            assertTrue(Math.abs(node.y()) < extent, id + " sets the graph's y extent at " + node.y());
        }
    }

    /** The whole reason the number above matters, spelled out as the screen computes it. */
    @Test
    void theDefaultFitIsStillHighEnoughToDrawNodeNames() {
        float fit = fitZoom(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
        assertTrue(fit >= LABEL_ZOOM,
                "the class tree opens at zoom " + fit + ", under the " + LABEL_ZOOM
                        + " at which ClassTreeScreen draws a node's name, so every class node in "
                        + "the game is now an unlabelled box; pull the outermost node inward");
    }

    /** ClassTreeScreen.fitZoom, verbatim. */
    private static float fitZoom(int imageWidth, int imageHeight) {
        float span = (ClassTreeLayout.extent() + ClassTreeLayout.NODE_WIDTH) * 2.0F;
        float byWidth = (imageWidth - 24) / span;
        float byHeight = (imageHeight - 70) / span;
        return Math.min(Math.max(Math.min(byWidth, byHeight), MIN_ZOOM), MAX_ZOOM);
    }
}
