package com.efkrdnz.magical.magic.mind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How easy a lie is to believe where it stands, as a number and the terms that made it. Pure: the
 * world comes in through {@link MindWorld}, so the Playbill forecasts with exactly the arithmetic
 * the server will use.
 */
public final class Plausibility {
    public static final float START = 0.5F;
    public static final float FLOOR = 0.05F;
    public static final float CEILING = 1.0F;
    public static final int CONTEXT_RADIUS = 8;
    public static final float UNSUPPORTED = -0.40F;
    public static final float CONTEXT = 0.30F;
    public static final float ALIEN = -0.20F;
    public static final float FIDELITY = 0.10F;
    public static final float HABITAT = -0.30F;
    public static final float UNLIKE_KIND = -0.20F;
    public static final float LIKE_KIND = 0.10F;
    public static final float SIZE_PER_DOUBLING = -0.10F;
    public static final int SIZE_FREE = 32;
    private static final String AIR = "minecraft:air";
    /**
     * The most cells one shared scan lays out as a grid. A live scene's clusters sit within its reach
     * of +-24, so its grid is at most 65 cubed (274,625); anything wider is read centre by centre.
     */
    private static final int MAX_SHARED_CELLS = 1 << 19;

    public record Placed(int x, int y, int z, String id) {}

    public record Term(String key, float value) {}

    public record Reading(float p, List<Term> terms) {
        public Reading {
            terms = List.copyOf(terms);
        }
    }

    /**
     * The real materials within {@link #CONTEXT_RADIUS} of each of a set of centres, read for a whole
     * scene at once. Every real cell any centre looks at is read from the world exactly once and
     * shared, where reading each cluster alone read its 17-cubed neighbourhood again per cluster: a
     * hundred one-block clusters of fake ore in a real wall was half a million block lookups a re-read.
     */
    public static final class Surroundings {
        private final Map<Long, Set<String>> byCentre;

        private Surroundings(Map<Long, Set<String>> byCentre) {
            this.byCentre = byCentre;
        }

        /** The materials around one of the centres these were read for. */
        public Set<String> near(int x, int y, int z) {
            Set<String> found = byCentre.get(pack(x, y, z));
            if (found == null) {
                throw new IllegalArgumentException("no surroundings were read around " + x + "," + y + "," + z);
            }
            return found;
        }
    }

    private Plausibility() {}

    /** Reads the surroundings of every centre in one pass; see {@link Surroundings}. */
    public static Surroundings surroundings(MindWorld world, List<Placed> centres) {
        Map<Long, Set<String>> byCentre = new HashMap<>();
        if (centres.isEmpty()) {
            return new Surroundings(byCentre);
        }
        int r = CONTEXT_RADIUS;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Placed centre : centres) {
            minX = Math.min(minX, centre.x());
            minY = Math.min(minY, centre.y());
            minZ = Math.min(minZ, centre.z());
            maxX = Math.max(maxX, centre.x());
            maxY = Math.max(maxY, centre.y());
            maxZ = Math.max(maxZ, centre.z());
        }
        long sizeX = (long) maxX - minX + 1 + 2L * r;
        long sizeY = (long) maxY - minY + 1 + 2L * r;
        long sizeZ = (long) maxZ - minZ + 1 + 2L * r;
        if (sizeX * sizeY * sizeZ > MAX_SHARED_CELLS) {
            for (Placed centre : centres) {
                byCentre.computeIfAbsent(pack(centre.x(), centre.y(), centre.z()),
                        key -> surroundings(world, List.of(centre)).near(centre.x(), centre.y(), centre.z()));
            }
            return new Surroundings(byCentre);
        }
        // Each grid cell holds its material's place in the palette plus one; zero is not read yet.
        int[] grid = new int[(int) (sizeX * sizeY * sizeZ)];
        List<String> palette = new ArrayList<>();
        Map<String, Integer> places = new HashMap<>();
        int originX = minX - r, originY = minY - r, originZ = minZ - r;
        for (Placed centre : centres) {
            long key = pack(centre.x(), centre.y(), centre.z());
            if (byCentre.containsKey(key)) {
                continue;
            }
            BitSet seen = new BitSet();
            for (int x = centre.x() - r; x <= centre.x() + r; x++) {
                for (int y = centre.y() - r; y <= centre.y() + r; y++) {
                    int row = (int) (((x - originX) * sizeY + (y - originY)) * sizeZ);
                    for (int z = centre.z() - r; z <= centre.z() + r; z++) {
                        int cell = row + (z - originZ);
                        int entry = grid[cell];
                        if (entry == 0) {
                            String id = world.blockId(x, y, z);
                            Integer place = places.get(id);
                            if (place == null) {
                                place = palette.size();
                                palette.add(id);
                                places.put(id, place);
                            }
                            entry = place + 1;
                            grid[cell] = entry;
                        }
                        seen.set(entry - 1);
                    }
                }
            }
            Set<String> materials = new HashSet<>();
            for (int place = seen.nextSetBit(0); place >= 0; place = seen.nextSetBit(place + 1)) {
                String id = palette.get(place);
                if (!AIR.equals(id)) {
                    materials.add(id);
                }
            }
            byCentre.put(key, materials);
        }
        return new Surroundings(byCentre);
    }

    /** One cluster read alone, as the Playbill's forecast does. */
    public static Reading cluster(MindWorld world, List<Placed> blocks, Lexicon lexicon, int sceneSize) {
        return blocks.isEmpty() ? new Reading(START, List.of())
                : cluster(world, blocks, lexicon, sceneSize, surroundings(world, List.of(blocks.get(0))));
    }

    /** One cluster of a scene whose surroundings were read together; they must include its first block. */
    public static Reading cluster(MindWorld world, List<Placed> blocks, Lexicon lexicon, int sceneSize, Surroundings around) {
        List<Term> terms = new ArrayList<>();
        int count = blocks.size();
        if (count == 0) {
            return new Reading(START, terms);
        }
        add(terms, "unsupported", UNSUPPORTED * unsupported(world, blocks) / count);

        Placed first = blocks.get(0);
        Set<String> nearby = around.near(first.x(), first.y(), first.z());
        int matching = 0;
        float fidelity = 0.0F;
        for (Placed block : blocks) {
            if (nearby.contains(block.id())) {
                matching++;
            }
            fidelity += lexicon.fidelity("block:" + block.id());
        }
        add(terms, "context", CONTEXT * matching / count);
        add(terms, "alien", ALIEN * (count - matching) / count);
        add(terms, "fidelity", FIDELITY * (fidelity / count - 1.0F));
        add(terms, "size", sizeTerm(sceneSize));
        return read(terms);
    }

    public static Reading figment(MindWorld world, Placed at, Script script, Lexicon lexicon, int sceneSize) {
        List<Term> terms = new ArrayList<>();
        CreatureTraits traits = CreatureTraits.of(at.id());
        add(terms, "fidelity", FIDELITY * (lexicon.fidelity("creature:" + at.id()) - 1));
        boolean outOfWater = traits.aquatic() && !world.water(at.x(), at.y(), at.z());
        boolean unburnt = traits.undead() && world.openSkyDaylight(at.x(), at.y(), at.z());
        add(terms, "habitat", outOfWater || unburnt ? HABITAT : 0.0F);
        if (traits.natural().contains(script.reaction())) {
            add(terms, "like_kind", LIKE_KIND);
        } else if (traits.unnatural().contains(script.reaction())) {
            add(terms, "unlike_kind", UNLIKE_KIND);
        }
        add(terms, "size", sizeTerm(sceneSize));
        return read(terms);
    }

    public static float sizeTerm(int sceneSize) {
        if (sceneSize <= SIZE_FREE) {
            return 0.0F;
        }
        return SIZE_PER_DOUBLING * (float) (Math.log((double) sceneSize / SIZE_FREE) / Math.log(2.0));
    }

    private static Reading read(List<Term> terms) {
        float p = START;
        for (Term term : terms) {
            p += term.value();
        }
        return new Reading(Math.max(FLOOR, Math.min(CEILING, p)), terms);
    }

    private static void add(List<Term> terms, String key, float value) {
        if (Math.abs(value) > 1.0E-6F) {
            terms.add(new Term(key, value));
        }
    }

    /** Blocks not held up by the real world, directly or through their own cluster. */
    private static int unsupported(MindWorld world, List<Placed> blocks) {
        Set<Long> cells = new HashSet<>();
        for (Placed block : blocks) {
            cells.add(pack(block.x(), block.y(), block.z()));
        }
        Set<Long> held = new HashSet<>();
        ArrayDeque<Placed> open = new ArrayDeque<>();
        for (Placed block : blocks) {
            if (anchored(world, block)) {
                held.add(pack(block.x(), block.y(), block.z()));
                open.add(block);
            }
        }
        while (!open.isEmpty()) {
            Placed from = open.poll();
            for (int[] d : FACES) {
                long next = pack(from.x() + d[0], from.y() + d[1], from.z() + d[2]);
                if (cells.contains(next) && held.add(next)) {
                    open.add(new Placed(from.x() + d[0], from.y() + d[1], from.z() + d[2], from.id()));
                }
            }
        }
        return blocks.size() - held.size();
    }

    private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static boolean anchored(MindWorld world, Placed block) {
        for (int[] d : FACES) {
            if (world.solid(block.x() + d[0], block.y() + d[1], block.z() + d[2])) {
                return true;
            }
        }
        return false;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
