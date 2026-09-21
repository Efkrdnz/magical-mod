package com.efkrdnz.magical.classes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Where every class node sits in the radial tree, in unzoomed graph space centred on the origin.
 *
 * <p>Each base class owns an angular sector; its tree fans outward inside that sector, one ring per
 * tier. Pure maths with no client imports, so the screen and the tests share one source of truth.</p>
 */
public final class ClassTreeLayout {
    /**
     * Ring radius by tier: base, discipline, mastery, apex. These are sized so the arc available to
     * a ring inside one sector comfortably exceeds {@link #NODE_WIDTH} times the nodes on it; the
     * first version used radii a third of this and every sector collided with its neighbours.
     */
    private static final float[] RING = {112.0F, 330.0F, 440.0F, 545.0F};
    /**
     * Angular span a ring may use inside its sector, in degrees, by tier. Two constraints set
     * these: nodes on the same ring must be at least {@link #NODE_WIDTH} of arc apart, and the
     * leftover gap to the neighbouring sector must be at least that too, or trees collide at their
     * edges. At tier 1 that means 44 degrees of a 72 degree sector, leaving 28 for the gap.
     */
    private static final float[] SPAN = {0.0F, 44.0F, 26.0F, 15.0F};
    /** Nominal node footprint in graph units, used here and asserted by the layout test. */
    public static final float NODE_WIDTH = 76.0F;
    public static final float NODE_HEIGHT = 22.0F;

    /**
     * The Sword Summoner line is a hidden chain rather than a starting tree, so it is hand-placed:
     * it owns no sector and {@link MagicalClasses#startingRoots()} - which is what sets the sector
     * count - deliberately does not contain it, so the five existing trees do not move by a pixel.
     *
     * <p>It takes the empty wedge between the Blacksmith sector at -90 and its neighbour at -18,
     * whose tier-1 spans reach only -68 and -40, and it stays <b>inside the outer ring</b>. That is
     * the load-bearing half. {@link #extent()} is {@code max(|x|, |y|)} over every node and it
     * feeds {@code ClassTreeScreen.fitZoom()}, which draws a node's <em>name</em> only at
     * {@code zoom >= 0.40F} while the default fit is already about 0.43 - so the graph is one
     * careless placement away from stripping the label off every class node in the game, for every
     * player, with a completely green build. These four have {@code |x| <= 320.4} and
     * {@code |y| <= 440.9} against an existing envelope of 535.9 by 540.3, so {@code extent()} does
     * not move at all. {@code ClassTreeExtentTest} is the only thing that would ever say so.</p>
     */
    private static final float SWORD_BEARING = -54.0F;
    /** Radius by rung: Summoner, Rider, Saint, God. The apex shares RING[3] and nothing else does. */
    private static final float[] SWORD_RING = {210.0F, 380.0F, 470.0F, 545.0F};

    private static final Map<ResourceLocation, Node> NODES = new LinkedHashMap<>();

    /** A laid-out node: graph-space position plus the tier that produced it. */
    public record Node(ResourceLocation id, float x, float y, int tier) {}

    private ClassTreeLayout() {}

    static {
        List<MagicalClassDefinition> bases = MagicalClasses.startingRoots();
        int baseCount = Math.max(1, bases.size());
        float sector = 360.0F / baseCount;
        for (int b = 0; b < bases.size(); b++) {
            MagicalClassDefinition base = bases.get(b);
            // -90 puts the first base straight up, which reads better than starting at 3 o'clock.
            float centreAngle = -90.0F + sector * b;
            place(base.id(), RING[0], centreAngle, 0);
            for (int tier = 1; tier <= 3; tier++) {
                spread(tierOf(base.id(), tier), centreAngle, SPAN[tier], RING[tier], tier);
            }
        }
        // The Spell Creator line is a utility branch rather than a starting tree, so it sits in the
        // hollow at the centre, well inside the base ring where nothing else is placed.
        place(MagicalClasses.SPELL_CREATOR, 26.0F, -90.0F, 0);
        place(MagicalClasses.MAGIC_ORIGINATOR, 26.0F, 90.0F, 1);
        // Hand-placed because ClassTreeTest asserts all().size() == nodes().size(): you cannot
        // hide a class by refusing to lay it out. The screen skips drawing it instead.
        place(MagicalClasses.SWORD_SUMMONER, SWORD_RING[0], SWORD_BEARING, 0);
        place(MagicalClasses.SWORD_RIDER, SWORD_RING[1], SWORD_BEARING, 1);
        place(MagicalClasses.SWORD_SAINT, SWORD_RING[2], SWORD_BEARING, 2);
        place(MagicalClasses.SWORD_GOD, SWORD_RING[3], SWORD_BEARING, 3);
    }

    private static List<MagicalClassDefinition> tierOf(ResourceLocation baseId, int tier) {
        List<MagicalClassDefinition> nodes = new ArrayList<>();
        for (MagicalClassDefinition definition : MagicalClasses.treeOf(baseId)) {
            if (definition.tier() == tier) {
                nodes.add(definition);
            }
        }
        return nodes;
    }

    private static void spread(List<MagicalClassDefinition> ring, float centreAngle, float span, float radius, int tier) {
        int count = ring.size();
        if (count == 0) {
            return;
        }
        for (int i = 0; i < count; i++) {
            // Single node sits dead centre; otherwise spread evenly across the sector span.
            float t = count == 1 ? 0.5F : i / (float) (count - 1);
            float angle = centreAngle - span * 0.5F + span * t;
            place(ring.get(i).id(), radius, angle, tier);
        }
    }

    private static void place(ResourceLocation id, float radius, float angleDegrees, int tier) {
        double radians = Math.toRadians(angleDegrees);
        NODES.put(id, new Node(id, (float) (Math.cos(radians) * radius), (float) (Math.sin(radians) * radius), tier));
    }

    public static Node get(ResourceLocation id) {
        return NODES.get(id);
    }

    public static List<Node> nodes() {
        return List.copyOf(NODES.values());
    }

    /** Half-extent of the whole graph, used to clamp panning. */
    public static float extent() {
        float max = 1.0F;
        for (Node node : NODES.values()) {
            max = Math.max(max, Math.max(Math.abs(node.x()), Math.abs(node.y())));
        }
        return max;
    }
}
