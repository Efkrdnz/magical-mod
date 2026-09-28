package com.efkrdnz.magical.magic.mind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * One authored scene: imagined blocks and figments at offsets from an anchor, in the facing the
 * wielder had when they wrote it. Blocks that touch face to face form a cluster, and a cluster is
 * one element to every viewer - believed, sensed and shattered as a whole.
 */
public final class Reverie {
    public static final int REACH = 24;

    public enum Refusal { NONE, FULL, UNKNOWN, OCCUPIED, TOO_FAR }

    private String name = "";
    private int facing;
    private final LinkedHashMap<Offset, ImaginedBlock> blocks = new LinkedHashMap<>();
    private final List<Figment> figments = new ArrayList<>();

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public int facing() {
        return facing;
    }

    public void setFacing(int facing) {
        this.facing = Math.floorMod(facing, 4);
    }

    public Refusal addBlock(Offset at, String blockId, Lexicon lexicon) {
        Refusal refusal = refuse(at, "block:" + blockId, lexicon);
        if (refusal != Refusal.NONE) {
            return refusal;
        }
        blocks.put(at, new ImaginedBlock(at, blockId, Set.of()));
        normaliseSenses();
        return Refusal.NONE;
    }

    public Refusal addFigment(Offset at, String creatureId, Lexicon lexicon) {
        Refusal refusal = refuse(at, "creature:" + creatureId, lexicon);
        if (refusal != Refusal.NONE) {
            return refusal;
        }
        figments.add(new Figment(at, creatureId, Script.DEFAULT, Set.of()));
        return Refusal.NONE;
    }

    private Refusal refuse(Offset at, String key, Lexicon lexicon) {
        if (!at.within(REACH)) {
            return Refusal.TOO_FAR;
        }
        if (!lexicon.knows(key)) {
            return Refusal.UNKNOWN;
        }
        if (occupied(at)) {
            return Refusal.OCCUPIED;
        }
        return size() >= lexicon.budget() ? Refusal.FULL : Refusal.NONE;
    }

    public boolean occupied(Offset at) {
        return blocks.containsKey(at) || figments.stream().anyMatch(f -> f.at().equals(at));
    }

    public boolean remove(Offset at) {
        return blocks.remove(at) != null || figments.removeIf(f -> f.at().equals(at));
    }

    public Collection<ImaginedBlock> blocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public List<Figment> figments() {
        return Collections.unmodifiableList(figments);
    }

    /** Face-connected clusters, each in the order its blocks were laid, clusters by their first block. */
    public List<List<ImaginedBlock>> clusters() {
        List<List<ImaginedBlock>> clusters = new ArrayList<>();
        Set<Offset> seen = new HashSet<>();
        for (Offset start : blocks.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            Set<Offset> members = new HashSet<>();
            ArrayDeque<Offset> open = new ArrayDeque<>();
            open.add(start);
            members.add(start);
            while (!open.isEmpty()) {
                for (Offset next : open.poll().neighbours()) {
                    if (blocks.containsKey(next) && members.add(next)) {
                        seen.add(next);
                        open.add(next);
                    }
                }
            }
            List<ImaginedBlock> cluster = new ArrayList<>();
            for (ImaginedBlock block : blocks.values()) {
                if (members.contains(block.at())) {
                    cluster.add(block);
                }
            }
            clusters.add(List.copyOf(cluster));
        }
        return clusters;
    }

    public void setClusterSenses(Offset member, Set<Sense> senses) {
        for (List<ImaginedBlock> cluster : clusters()) {
            if (cluster.stream().anyMatch(b -> b.at().equals(member))) {
                for (ImaginedBlock block : cluster) {
                    blocks.put(block.at(), block.withSenses(senses));
                }
                return;
            }
        }
    }

    public void setFigmentSenses(int index, Set<Sense> senses) {
        if (index >= 0 && index < figments.size()) {
            figments.set(index, figments.get(index).withSenses(senses));
        }
    }

    public void setScript(int index, Script script) {
        if (index >= 0 && index < figments.size()) {
            figments.set(index, figments.get(index).withScript(script));
        }
    }

    public int size() {
        return blocks.size() + figments.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    /** Sense layers as billed: one per sense per cluster, one per sense per figment. */
    public int senseLayers() {
        int layers = 0;
        for (List<ImaginedBlock> cluster : clusters()) {
            layers += cluster.get(0).senses().size();
        }
        for (Figment figment : figments) {
            layers += figment.senses().size();
        }
        return layers;
    }

    /** Every cluster carries one sense set: the union of what its members had. */
    void normaliseSenses() {
        for (List<ImaginedBlock> cluster : clusters()) {
            Set<Sense> union = EnumSet.noneOf(Sense.class);
            for (ImaginedBlock block : cluster) {
                union.addAll(block.senses());
            }
            for (ImaginedBlock block : cluster) {
                if (!block.senses().equals(union)) {
                    blocks.put(block.at(), block.withSenses(union));
                }
            }
        }
    }

    void put(ImaginedBlock block) {
        if (block.at().within(REACH) && !occupied(block.at())) {
            blocks.put(block.at(), block);
        }
    }

    void put(Figment figment) {
        if (figment.at().within(REACH) && !occupied(figment.at())) {
            figments.add(figment);
        }
    }

    public Reverie copy() {
        Reverie copy = new Reverie();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(Reverie other) {
        if (other == this) {
            return;
        }
        name = other.name;
        facing = other.facing;
        blocks.clear();
        blocks.putAll(other.blocks);
        figments.clear();
        figments.addAll(other.figments);
    }

    public void clear() {
        name = "";
        facing = 0;
        blocks.clear();
        figments.clear();
    }
}
