package com.efkrdnz.magical.magic.mind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    public record Placed(int x, int y, int z, String id) {}

    public record Term(String key, float value) {}

    public record Reading(float p, List<Term> terms) {
        public Reading {
            terms = List.copyOf(terms);
        }
    }

    private Plausibility() {}

    public static Reading cluster(MindWorld world, List<Placed> blocks, Lexicon lexicon, int sceneSize) {
        List<Term> terms = new ArrayList<>();
        int count = blocks.size();
        if (count == 0) {
            return new Reading(START, terms);
        }
        add(terms, "unsupported", UNSUPPORTED * unsupported(world, blocks) / count);

        Placed first = blocks.get(0);
        Set<String> nearby = materialsNear(world, first.x(), first.y(), first.z());
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

    private static Set<String> materialsNear(MindWorld world, int x, int y, int z) {
        Set<String> materials = new HashSet<>();
        for (int dx = -CONTEXT_RADIUS; dx <= CONTEXT_RADIUS; dx++) {
            for (int dy = -CONTEXT_RADIUS; dy <= CONTEXT_RADIUS; dy++) {
                for (int dz = -CONTEXT_RADIUS; dz <= CONTEXT_RADIUS; dz++) {
                    String id = world.blockId(x + dx, y + dy, z + dz);
                    if (!AIR.equals(id)) {
                        materials.add(id);
                    }
                }
            }
        }
        return materials;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
