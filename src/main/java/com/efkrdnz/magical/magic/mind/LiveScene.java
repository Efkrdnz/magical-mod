package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * One reverie set down in the world. Its elements are its clusters (in {@link Reverie#clusters()}
 * order) followed by its figments, and an element's index is its address in the belief ledger and
 * on the wire. Nothing here is saved.
 */
public final class LiveScene {
    public static final int LIFE_TICKS = 1200;
    public static final int REREAD_TICKS = 100;
    private static final double FIGMENT_HALF_WIDTH = 0.3;
    private static final double FIGMENT_HEIGHT = 1.95;

    public enum Kind { CLUSTER, FIGMENT }

    public record Element(int index, Kind kind, List<BlockPos> cells, List<String> blockIds, Set<String> impressions,
                          Set<Sense> senses, BlockPos figmentAt, Figment figment, AABB box) {}

    private final int id;
    private final UUID owner;
    private final ResourceKey<Level> dimension;
    private final BlockPos anchor;
    private final int turns;
    private final long bornAt;
    private final Lexicon lexicon;
    private final List<Element> elements = new ArrayList<>();
    private final Map<Long, Integer> cellIndex = new HashMap<>();
    private final Belief belief = new Belief();
    private final float[] plausibility;
    private final AABB bounds;
    final Set<Long> inside = new HashSet<>();
    final Set<Long> seenProjectiles = new HashSet<>();
    final Set<Integer> knownViewers = new LinkedHashSet<>();
    final Map<Integer, Integer> figmentEntities = new HashMap<>();
    /** Players this scene has been sent to; see {@link MindSync}. */
    final Set<UUID> audience = new HashSet<>();
    /** Elements that are real right now; see {@code Manifestation}. */
    final Set<Integer> manifested = new HashSet<>();
    /** Real figments that were killed: gone from the scene for good. */
    final Set<Integer> slain = new HashSet<>();
    /** The terrain edit holding each real cluster's blocks, by element. */
    final Map<Integer, UUID> edits = new HashMap<>();
    /** Each element's consensus at the last step, see {@link Consensus}. */
    float[] consensus;

    LiveScene(int id, UUID owner, ResourceKey<Level> dimension, Reverie reverie, BlockPos anchor, int turns,
              Lexicon lexicon, long bornAt) {
        this.id = id;
        this.owner = owner;
        this.dimension = dimension;
        this.anchor = anchor;
        this.turns = turns;
        this.bornAt = bornAt;
        this.lexicon = new Lexicon();
        this.lexicon.copyFrom(lexicon);
        for (List<ImaginedBlock> cluster : reverie.clusters()) {
            List<BlockPos> cells = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            Set<String> impressions = new LinkedHashSet<>();
            AABB box = null;
            for (ImaginedBlock block : cluster) {
                BlockPos cell = world(block.at());
                cells.add(cell);
                ids.add(block.blockId());
                impressions.add(Impression.block(block.blockId()).key());
                box = box == null ? new AABB(cell) : box.minmax(new AABB(cell));
                cellIndex.put(cell.asLong(), elements.size());
            }
            elements.add(new Element(elements.size(), Kind.CLUSTER, List.copyOf(cells), List.copyOf(ids),
                    Set.copyOf(impressions), cluster.get(0).senses(), null, null, box));
        }
        for (Figment figment : reverie.figments()) {
            BlockPos at = world(figment.at());
            AABB box = new AABB(at.getX() + 0.5 - FIGMENT_HALF_WIDTH, at.getY(), at.getZ() + 0.5 - FIGMENT_HALF_WIDTH,
                    at.getX() + 0.5 + FIGMENT_HALF_WIDTH, at.getY() + FIGMENT_HEIGHT, at.getZ() + 0.5 + FIGMENT_HALF_WIDTH);
            elements.add(new Element(elements.size(), Kind.FIGMENT, List.of(), List.of(),
                    Set.of(Impression.creature(figment.creatureId()).key()), figment.senses(), at, figment, box));
        }
        AABB all = elements.get(0).box();
        for (Element element : elements) {
            all = all.minmax(element.box());
        }
        this.bounds = all;
        this.plausibility = new float[elements.size()];
        this.consensus = new float[elements.size()];
    }

    private BlockPos world(Offset offset) {
        Offset turned = offset.rotate(turns);
        return anchor.offset(turned.dx(), turned.dy(), turned.dz());
    }

    /**
     * Reads every element's plausibility where it stands. The real materials around the clusters are
     * read once for the whole scene and shared ({@link Plausibility.Surroundings}), so a scene of many
     * small clusters costs one neighbourhood rather than one per cluster.
     */
    public void reread(MindWorld world) {
        int size = elements.stream().mapToInt(e -> e.kind() == Kind.CLUSTER ? e.cells().size() : 1).sum();
        List<List<Plausibility.Placed>> clusters = new ArrayList<>();
        List<Plausibility.Placed> firsts = new ArrayList<>();
        for (Element element : elements) {
            if (element.kind() != Kind.CLUSTER) {
                continue;
            }
            List<Plausibility.Placed> placed = new ArrayList<>();
            for (int i = 0; i < element.cells().size(); i++) {
                BlockPos cell = element.cells().get(i);
                placed.add(new Plausibility.Placed(cell.getX(), cell.getY(), cell.getZ(), element.blockIds().get(i)));
            }
            clusters.add(placed);
            firsts.add(placed.get(0));
        }
        Plausibility.Surroundings around = Plausibility.surroundings(world, firsts);
        for (Element element : elements) {
            Plausibility.Reading reading;
            if (element.kind() == Kind.CLUSTER) {
                // Clusters come first among the elements, so a cluster's index is its place in the list.
                reading = Plausibility.cluster(world, clusters.get(element.index()), lexicon, size, around);
            } else {
                BlockPos at = element.figmentAt();
                reading = Plausibility.figment(world, new Plausibility.Placed(at.getX(), at.getY(), at.getZ(),
                        element.figment().creatureId()), element.figment().script(), lexicon, size);
            }
            plausibility[element.index()] = reading.p();
        }
    }

    public static long key(int a, int b) {
        return ((long) a << 32) | (b & 0xFFFFFFFFL);
    }

    public int elementAt(BlockPos pos) {
        return cellIndex.getOrDefault(pos.asLong(), -1);
    }

    public int elementAt(long packedPos) {
        return cellIndex.getOrDefault(packedPos, -1);
    }

    /** The entity id of a figment element's creature, or -1 when it has none. */
    public int figmentEntity(int element) {
        return figmentEntities.getOrDefault(element, -1);
    }

    /** Whether a scene is finished: its clock has run out and nothing in it is real. */
    public static boolean over(long age, boolean anythingReal) {
        return age >= LIFE_TICKS && !anythingReal;
    }

    public boolean over(long now) {
        return over(now - bornAt, anythingReal());
    }

    public boolean manifested(int element) {
        return manifested.contains(element);
    }

    public boolean slain(int element) {
        return slain.contains(element);
    }

    public boolean anythingReal() {
        return !manifested.isEmpty();
    }

    /** The real elements in index order, for the wire. */
    public List<Integer> manifestedList() {
        List<Integer> list = new ArrayList<>(manifested);
        Collections.sort(list);
        return list;
    }

    public float consensus(int element) {
        return consensus[element];
    }

    /** What an element weighs: half a point a block, a quarter of its kind's health. */
    public float weight(int element) {
        Element e = elements.get(element);
        return e.kind() == Kind.CLUSTER ? Consensus.clusterWeight(e.cells().size())
                : Consensus.figmentWeight(KindStats.maxHealth(e.figment().creatureId()));
    }

    /**
     * The element nearest along a ray: a cluster by any of its cells, a figment by the box it has now
     * ({@code figmentBox}, by element index). Slain elements are not there to aim at.
     */
    public SceneAim.Hit aim(Vec3 from, Vec3 to, IntFunction<AABB> figmentBox) {
        List<AABB> boxes = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        for (Element element : elements) {
            if (slain(element.index())) {
                continue;
            }
            if (element.kind() == Kind.CLUSTER) {
                for (BlockPos cell : element.cells()) {
                    boxes.add(new AABB(cell));
                    owners.add(element.index());
                }
            } else {
                boxes.add(figmentBox.apply(element.index()));
                owners.add(element.index());
            }
        }
        SceneAim.Hit hit = SceneAim.nearest(from, to, boxes);
        return hit == null ? null : new SceneAim.Hit(owners.get(hit.index()), hit.distance());
    }

    public int id() { return id; }
    public UUID owner() { return owner; }
    public ResourceKey<Level> dimension() { return dimension; }
    public BlockPos anchor() { return anchor; }
    public int turns() { return turns; }
    public long bornAt() { return bornAt; }
    public Lexicon lexicon() { return lexicon; }
    public List<Element> elements() { return java.util.Collections.unmodifiableList(elements); }
    public Belief belief() { return belief; }
    public float plausibility(int element) { return plausibility[element]; }
    public AABB bounds() { return bounds; }
}
