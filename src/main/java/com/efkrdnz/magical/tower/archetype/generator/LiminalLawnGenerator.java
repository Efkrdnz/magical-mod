package com.efkrdnz.magical.tower.archetype.generator;

import com.efkrdnz.magical.tower.archetype.ArchetypeGenerator;
import com.efkrdnz.magical.tower.archetype.PlacementSink;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Generates a liminal-horror dungeon floor: a flat mown-grass plain with monolithic structures
 * arranged to create agoraphobia and claustrophobia simultaneously.
 *
 * <p>The paradox is structural: an endless open field where you cannot see the horizon because
 * something enormous is always in the way. Monoliths are arranged in near-canyons with 3-6 block
 * gaps between 80-120 block walls, producing the horror of maintained space — the grass is cut,
 * nothing is here, but something is keeping it that way.
 *
 * <p>This generator is structured for extension. The three named sites (The Mown Circle, The Sunk
 * House, The Standing Door) and the castle are placed by separate extension methods (marked with
 * comments) to be filled in as those locations are designed.
 *
 * <p>Generated area covers roughly 320x320 blocks of lawn with monoliths, staying well inside the
 * 1024x1024 plot bounds.
 *
 * @see com.efkrdnz.magical.tower.archetype.ArchetypeGenerator
 */
public final class LiminalLawnGenerator implements ArchetypeGenerator {
    public static final LiminalLawnGenerator INSTANCE = new LiminalLawnGenerator();

    /** Size of the main generated lawn area (centred on origin). Actual total is roughly 320x320. */
    /** How far structures spread from the plot origin. The ground itself is infinite. */
    private static final int LAWN_RADIUS = 400;

    /** Heights of the four main monolith zones, in blocks. Deliberately varied. */
    /** Colossal on purpose: these read as landmarks from the far side of the field. */
    private static final int[] MONOLITH_HEIGHTS = {168, 214, 190, 246};

    /** Wall thickness for hollow monolith shells. */
    /** One block. Doubling thickness doubles cost for no visual gain at this scale. */
    private static final int WALL_THICKNESS = 1;

    /** Minimum gap between monolith structures, in blocks. Claustrophobia achieved at 3-6 range. */
    private static final int MIN_MONOLITH_GAP = 4;

    /** Widest gap that still reads as a slot rather than a street. */
    private static final int MAX_MONOLITH_GAP = 6;

    /** Half the long axis of a monolith base, used to place a partner slab a fixed slot away. */
    private static final int MONOLITH_HALF_LONG = 50;

    private static final int LARGE_MONOLITH_COUNT = 5;
    private static final int NARROW_WALL_COUNT = 11;
    private static final int EXPOSED_CORRIDOR_COUNT = 3;

    /** How often a large monolith gets a partner slab a few blocks away, forming a canyon. */
    private static final int CANYON_CHANCE_PERCENT = 65;

    /** Minimum centre separation so structures crowd without merging into one solid mass. */
    private static final int LARGE_CLEARANCE = 130;
    private static final int NARROW_CLEARANCE = 60;

    /** Rejection-sampling attempts before giving up on a spot. Keeps generation bounded. */
    private static final int PLACEMENT_ATTEMPTS = 40;

    /** Primary block for monolith walls — cold, blank, institutional. */
    private static final BlockState MONOLITH_BLOCK = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();

    /** Accent block for monolith edges and impossible geometry. */
    private static final BlockState ACCENT_BLOCK = Blocks.QUARTZ_BLOCK.defaultBlockState();

    /** Ground cover — perfectly flat, no undulation, no flowers, no trees. */
    private static final BlockState GRASS_BLOCK = Blocks.GRASS_BLOCK.defaultBlockState();

    /** Replacement for grass replaced by structures. */
    private static final BlockState DIRT_BLOCK = Blocks.DIRT.defaultBlockState();

    private LiminalLawnGenerator() {}

    @Override
    public void generate(
            ServerLevel level, BlockPos origin, int floor, RandomSource random, PlacementSink sink) {
        // Generate the ground plane first so monoliths sit on it

        // Place the main monolith field to create the claustrophobic paradox
        generateMonolithField(origin, random, sink);

        // EXTENSION POINT: The three named sites will go here
        // - generateMownCircle(origin, random, sink);
        // - generateSunkHouse(origin, random, sink);
        // - generateStandingDoor(origin, random, sink);

        // EXTENSION POINT: The castle facade will be placed by a separate method
        // - generateCastleOnHorizon(origin, random, sink);
    }

    @Override
    public AABB footprint(BlockPos origin) {
        // Conservative footprint covering terrain + monoliths + future sites and castle.
        // Plotgrid bounds are ±512 XZ from origin.
        return new AABB(
                origin.getX() - LAWN_RADIUS,
                origin.getY() - 1,
                origin.getZ() - LAWN_RADIUS,
                origin.getX() + LAWN_RADIUS,
                origin.getY() + 260,
                origin.getZ() + LAWN_RADIUS);
    }

    @Override
    public BlockPos entryPoint(BlockPos origin, int floor) {
        // Place the player one block above the grass surface
        return origin.above();
    }

    /**
     * Generates the base terrain: a perfectly flat, perfectly mown grass plain. No undulation, no
     * flowers, no trees — just uniformity. The horror is in the maintenance.
     */

    /**
     * Generates the main monolith field. Monoliths are arranged in a 2x2 grid of zones, each with
     * its own height and internal structure. Spacing creates canyons of 3-6 blocks, producing the
     * claustrophobic paradox.
     *
     * <p>Each monolith is a hollow shell (walls only) to drastically reduce block count while
     * maintaining visual presence.
     */
    /**
     * Scatters the structures that make an open field feel enclosed.
     *
     * <p>Placement is deliberately irregular. A grid reads as designed and orderly, which is the
     * opposite of the effect: what unsettles is repetition without arrangement. Positions come from
     * the seeded random via rejection sampling, so a plot regenerates identically but no two plots
     * share a layout.
     *
     * <p>The claustrophobia does not come from enclosure — the sky is always open. It comes from
     * <em>slots</em>: pairs of eighty-block slabs set a few blocks apart, which box the view in
     * whichever direction the player happens to be facing.
     */
    private void generateMonolithField(BlockPos origin, RandomSource random, PlacementSink sink) {
        List<BlockPos> claimed = new ArrayList<>();

        for (int i = 0; i < LARGE_MONOLITH_COUNT; i++) {
            BlockPos spot = findSpot(origin, claimed, LARGE_CLEARANCE, random);
            if (spot == null) {
                continue;
            }
            generateMonolith(spot, MONOLITH_HEIGHTS[random.nextInt(MONOLITH_HEIGHTS.length)], random, sink);
            claimed.add(spot);

            // The slot. This is the floor's whole point, so most monoliths get one.
            if (random.nextInt(100) < CANYON_CHANCE_PERCENT) {
                int gap = MIN_MONOLITH_GAP + random.nextInt(MAX_MONOLITH_GAP - MIN_MONOLITH_GAP + 1);
                int reach = MONOLITH_HALF_LONG * 2 + gap;
                boolean alongX = random.nextBoolean();
                int delta = random.nextBoolean() ? reach : -reach;
                BlockPos partner = alongX ? spot.offset(delta, 0, 0) : spot.offset(0, 0, delta);
                if (withinLawn(origin, partner)) {
                    // Near-symmetry that is off: the partner is a different height on purpose.
                    generateMonolith(partner, MONOLITH_HEIGHTS[random.nextInt(MONOLITH_HEIGHTS.length)], random, sink);
                    claimed.add(partner);
                }
            }
        }

        // Narrow walls do most of the view-blocking, being cheap and tall.
        for (int i = 0; i < NARROW_WALL_COUNT; i++) {
            BlockPos spot = findSpot(origin, claimed, NARROW_CLEARANCE, random);
            if (spot == null) {
                continue;
            }
            generateNarrowWall(spot, 10 + random.nextInt(8), 150 + random.nextInt(90), random, sink);
            claimed.add(spot);
        }

        // Interior architecture standing in the open with no building around it.
        for (int i = 0; i < EXPOSED_CORRIDOR_COUNT; i++) {
            BlockPos spot = findSpot(origin, claimed, NARROW_CLEARANCE, random);
            if (spot == null) {
                continue;
            }
            generateExposedCorridor(spot, random, sink);
            claimed.add(spot);
        }
    }

    /**
     * Rejection-samples a position at least {@code clearance} from everything already placed.
     *
     * @return a spot, or null if the field is too crowded to find one within the attempt budget
     */
    private BlockPos findSpot(BlockPos origin, List<BlockPos> claimed, int clearance, RandomSource random) {
        int span = LAWN_RADIUS * 2 - LARGE_CLEARANCE;
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            int x = random.nextInt(span) - span / 2;
            int z = random.nextInt(span) - span / 2;
            BlockPos candidate = origin.offset(x, 0, z);
            if (isClear(candidate, claimed, clearance)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isClear(BlockPos candidate, List<BlockPos> claimed, int clearance) {
        int clearanceSqr = clearance * clearance;
        for (BlockPos taken : claimed) {
            int dx = candidate.getX() - taken.getX();
            int dz = candidate.getZ() - taken.getZ();
            if (dx * dx + dz * dz < clearanceSqr) {
                return false;
            }
        }
        return true;
    }

    /** Keeps a partner slab from spilling past the lawn edge and outside the recorded footprint. */
    private boolean withinLawn(BlockPos origin, BlockPos candidate) {
        return Math.abs(candidate.getX() - origin.getX()) <= LAWN_RADIUS - MONOLITH_HALF_LONG
                && Math.abs(candidate.getZ() - origin.getZ()) <= LAWN_RADIUS - MONOLITH_HALF_LONG;
    }

    /**
     * Generates one large monolith: a hollow rectangular block with walls but no interior fill.
     * Walls are 2 blocks thick. Dimensions are chosen to be slightly different from "normal" to
     * create the subtle wrongness that makes liminal spaces unsettling.
     *
     * <p>The footprint is irregular: 47x43 blocks for the base (non-square, subtly off), and the
     * height is absolute to make scale paradoxical.
     */
    private void generateMonolith(
            BlockPos center, int height, RandomSource random, PlacementSink sink) {
        // Deliberately non-square base: 47x43 instead of 48x48
        final int halfWidth = 50;
        final int halfDepth = 43;


        // Hollow shell walls (WALL_THICKNESS thick)
        for (int y = 0; y < height; y++) {
            for (int x = -halfWidth; x <= halfWidth; x++) {
                for (int z = -halfDepth; z <= halfDepth; z++) {
                    // Outer perimeter and inner hollow
                    boolean isWall =
                            (Math.abs(x) >= halfWidth - WALL_THICKNESS + 1 || Math.abs(z) >= halfDepth - WALL_THICKNESS + 1)
                                    && (Math.abs(x) <= halfWidth || Math.abs(z) <= halfDepth);
                    if (isWall) {
                        // Alternate accent blocks every 8 blocks for subtle striping
                        BlockState block = (y % 8 == 0) ? ACCENT_BLOCK : MONOLITH_BLOCK;
                        sink.set(center.offset(x, y, z), block);
                    }
                }
            }
        }

        // Impossible geometry: add a doorway 16 blocks tall at one face
        int doorY = (height / 2) - 8;
        for (int y = doorY; y < doorY + 16; y++) {
            if (y >= 0 && y < height) {
                sink.set(center.offset(halfWidth, y, 0), Blocks.AIR.defaultBlockState());
                sink.set(center.offset(halfWidth - 1, y, 0), Blocks.AIR.defaultBlockState());
            }
        }
    }

    /**
     * Generates a narrow wall to create canyon effects. These walls are 5-8 blocks wide but tall
     * (75-90 blocks), creating the visual of being boxed in while surrounded by open space.
     */
    private void generateNarrowWall(
            BlockPos center, int width, int height, RandomSource random, PlacementSink sink) {
        final int halfWidth = width / 2;

        // Base
        for (int x = -halfWidth; x <= halfWidth; x++) {
            sink.set(center.offset(x, -1, 0), DIRT_BLOCK);
        }

        // Wall, hollow inside
        for (int y = 0; y < height; y++) {
            for (int x = -halfWidth; x <= halfWidth; x++) {
                // Only outer faces, plus a thin interior wall for structure
                if (Math.abs(x) == halfWidth || (Math.abs(x) == halfWidth - 1 && y % 3 == 0)) {
                    BlockState block = (y % 7 == 0) ? ACCENT_BLOCK : MONOLITH_BLOCK;
                    sink.set(center.offset(x, y, 0), block);
                }
            }
        }
    }

    /**
     * Generates an exposed interior structure: a corridor or stairwell standing alone in the field
     * with no building around it. This is the liminal paradox — functional interior architecture
     * existing where there is no interior.
     *
     * <p>This is a small 4-block-wide corridor with a vaulted roof and interior detail,
     * illustrating the design principle of "exposed interior architecture".
     */
    private void generateExposedCorridor(BlockPos origin, RandomSource random, PlacementSink sink) {
        BlockPos base = origin.offset(80, 0, -40);
        final int corridorWidth = 4;
        final int corridorLength = 20;
        final int corridorHeight = 6;

        // Corridor floor
        for (int x = 0; x < corridorLength; x++) {
            for (int z = -corridorWidth; z <= corridorWidth; z++) {
                sink.set(base.offset(x, 0, z), MONOLITH_BLOCK);
                sink.set(base.offset(x, -1, z), DIRT_BLOCK);
            }
        }

        // Walls (WALL_THICKNESS deep)
        for (int x = 0; x < corridorLength; x++) {
            for (int y = 1; y <= corridorHeight; y++) {
                for (int d = 0; d < WALL_THICKNESS; d++) {
                    sink.set(base.offset(x, y, -corridorWidth - d), MONOLITH_BLOCK);
                    sink.set(base.offset(x, y, corridorWidth + d), MONOLITH_BLOCK);
                }
            }
        }

        // Vaulted ceiling (arch, not flat)
        for (int x = 0; x < corridorLength; x++) {
            for (int z = -corridorWidth; z <= corridorWidth; z++) {
                int vaultY = corridorHeight + 1 - (Math.abs(z) / 2);
                if (vaultY >= corridorHeight) {
                    sink.set(base.offset(x, vaultY, z), ACCENT_BLOCK);
                }
            }
        }
    }

    // EXTENSION POINTS
    // ================
    // The three named sites will be generated by separate methods here, one per site:
    //
    // private void generateMownCircle(BlockPos origin, RandomSource random, PlacementSink sink) {
    //     // The Mown Circle: a perfect circle where grass is UN-cut (waist-high field inside)
    //     // Location: somewhere north of the origin
    // }
    //
    // private void generateSunkHouse(BlockPos origin, RandomSource random, PlacementSink sink) {
    //     // The Sunk House: an ordinary house buried to its eaves
    //     // Entry is downward through an upstairs window
    //     // Interior is far larger than outside
    //     // Location: somewhere west of the origin
    // }
    //
    // private void generateStandingDoor(BlockPos origin, RandomSource random, PlacementSink sink) {
    //     // The Standing Door: a doorframe alone in the field, no walls
    //     // Walking through teleports elsewhere on the plain depending on entry direction
    //     // Location: somewhere east of the origin
    // }

    // EXTENSION POINT
    // ===============
    // The castle will be generated by a separate method here:
    //
    // private void generateCastleOnHorizon(BlockPos origin, RandomSource random, PlacementSink sink)
    // {
    //     // The castle: monumental, always visible at the horizon
    //     // Appears closer during phase 2 of the boss fight
    //     // Needs to be hollow and carefully budgeted for block count
    //     // Location: far south of the origin, at maximum draw distance
    // }
}
