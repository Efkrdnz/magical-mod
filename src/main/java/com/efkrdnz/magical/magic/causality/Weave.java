package com.efkrdnz.magical.magic.causality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * The board: every pin the wielder has put up and every piece of string between them.
 *
 * <p>This is the whole of what a wielder of Causality owns, and the only part that survives a
 * logout. It is a <b>directed graph</b>, and that is the point - every other Authority in the mod
 * is something else. Space writes a table of laws over a region, Chaos keeps a heap of numbers that
 * topple, Mana reads a list front to back. A graph is the one shape where the wielder decides the
 * order of evaluation rather than the mod, which is what lets two people holding this Authority
 * build machines with nothing in common.
 *
 * <p>Three rules do all the balancing, and all three live here rather than in the fight:
 *
 * <ul>
 *   <li><b>A budget, not a count.</b> {@link #CAPACITY} points of {@link CausalNode#weight()},
 *       spent however the wielder likes. Six cheap chains or two expensive ones, never both.
 *   <li><b>Forward only.</b> {@link NodeKind#mayFeed} plus the reachability test in
 *       {@link #connect} means a chain always terminates, so the engine never needs a visited set.
 *   <li><b>Depth.</b> {@link #MAX_DEPTH} pins between a cause and a consequence. Long arguments
 *       with reality are what paradox is for.
 * </ul>
 *
 * <p>Pure but for NBT, which it needs in order to live on {@code PlayerMagicState}. Nothing here
 * knows what a level is; {@code WeaveTest} pins every rule on exact values.
 */
public final class Weave {

    /** Points of weight the board may hold at once. */
    public static final int CAPACITY = 24;

    /** Pins, whatever they weigh. A ceiling on the drawing rather than on the cost. */
    public static final int MAX_NODES = 24;

    /** Pieces of string, likewise. */
    public static final int MAX_WIRES = 32;

    /** Wires out of one pin. A cause may branch, but not into a fan nobody can read. */
    public static final int MAX_FAN_OUT = 4;

    /** Pins a signal may pass through between a cause and a consequence, the two ends included. */
    public static final int MAX_DEPTH = 8;

    /**
     * How much faster paradox cools while the board is switched off.
     *
     * <p>Suspending is not free - every chain stops - so the wielder is trading the machine
     * they built for the gauge that is stopping it working properly, which is a decision
     * rather than a wait.
     */
    public static final int SUSPENDED_COOLING = 3;

    /** The board the pins are placed on, in board units. The screen scales this to fit. */
    public static final int BOARD_W = 320;
    public static final int BOARD_H = 180;

    /** A wire, by the ids of the pins it joins. */
    public record Wire(int from, int to) {}

    private final Map<Integer, CausalNode> nodes = new LinkedHashMap<>();
    private final List<Wire> wires = new ArrayList<>();
    private int nextId = 1;
    private boolean suspended;

    // ---- reading ------------------------------------------------------------------------------

    public List<CausalNode> nodes() {
        return List.copyOf(nodes.values());
    }

    public List<Wire> wires() {
        return List.copyOf(wires);
    }

    public CausalNode node(int id) {
        return nodes.get(id);
    }

    public boolean empty() {
        return nodes.isEmpty();
    }

    /** True while the wielder has the whole board switched off. Saved: it is a standing choice. */
    public boolean suspended() {
        return suspended;
    }

    public void setSuspended(boolean off) {
        suspended = off;
    }

    public int size() {
        return nodes.size();
    }

    /** Every pin that starts a chain, in the order they were put up. */
    public List<CausalNode> causes() {
        List<CausalNode> found = new ArrayList<>();
        for (CausalNode node : nodes.values()) {
            if (node.kind() == NodeKind.CAUSE) {
                found.add(node);
            }
        }
        return found;
    }

    /** Whatever this pin feeds, in the order the wires were drawn - which is the order they fire in. */
    public List<CausalNode> downstream(int id) {
        List<CausalNode> found = new ArrayList<>();
        for (Wire wire : wires) {
            if (wire.from() != id) {
                continue;
            }
            CausalNode node = nodes.get(wire.to());
            if (node != null) {
                found.add(node);
            }
        }
        return found;
    }

    public int fanOut(int id) {
        int count = 0;
        for (Wire wire : wires) {
            if (wire.from() == id) {
                count++;
            }
        }
        return count;
    }

    /** True when this pin has nothing feeding it. A condition or an effect so left is a dead branch. */
    public boolean orphan(int id) {
        for (Wire wire : wires) {
            if (wire.to() == id) {
                return false;
            }
        }
        return true;
    }

    public int weight() {
        int total = 0;
        for (CausalNode node : nodes.values()) {
            total += node.weight();
        }
        return total;
    }

    public int capacity() {
        return CAPACITY;
    }

    public int spare() {
        return CAPACITY - weight();
    }

    /** True when any pin on the board wants a mark, so the wielder can be told the Weave is idle. */
    public boolean wantsAnchor() {
        for (CausalNode node : nodes.values()) {
            if (node.needsAnchor()) {
                return true;
            }
        }
        return false;
    }

    // ---- writing ------------------------------------------------------------------------------

    /**
     * Puts a pin up, and answers with it - or null when the board is full or could not pay for it.
     *
     * <p>The budget is checked here rather than at save time on purpose: a board that can be drawn
     * and not kept is a board that lies to the person drawing it.
     */
    public CausalNode add(CausalNode node) {
        if (node == null || nodes.size() >= MAX_NODES || weight() + node.weight() > CAPACITY) {
            return null;
        }
        CausalNode placed = new CausalNode(nextId++, node.kind(), node.ordinal(), node.param(),
                node.scope(), node.modifiers(), clampX(node.x()), clampY(node.y()));
        nodes.put(placed.id(), placed);
        return placed;
    }

    /**
     * Replaces a pin in place, keeping its id and therefore every wire on it.
     *
     * <p>False when the change would not fit the budget, in which case nothing moves - which is how
     * the screen can offer a modifier and refuse it in the same gesture rather than half-applying.
     */
    public boolean replace(CausalNode node) {
        CausalNode existing = node == null ? null : nodes.get(node.id());
        if (existing == null || existing.kind() != node.kind()) {
            return false;
        }
        if (weight() - existing.weight() + node.weight() > CAPACITY) {
            return false;
        }
        nodes.put(node.id(), new CausalNode(node.id(), node.kind(), node.ordinal(), node.param(),
                node.scope(), node.modifiers(), clampX(node.x()), clampY(node.y())));
        return true;
    }

    /** Takes a pin down, and every piece of string that was on it. */
    public boolean remove(int id) {
        if (nodes.remove(id) == null) {
            return false;
        }
        wires.removeIf(wire -> wire.from() == id || wire.to() == id);
        return true;
    }

    /**
     * Joins two pins, and answers with why not when it will not.
     *
     * <p>Every refusal is a sentence the board can show, which is what makes the grammar teachable:
     * the wielder learns that string runs one way by being told so at the moment they try to run it
     * the other, rather than by reading a page about it.
     */
    public WireRefusal connect(int from, int to) {
        CausalNode source = nodes.get(from);
        CausalNode target = nodes.get(to);
        if (source == null || target == null || from == to) {
            return WireRefusal.NOTHING_THERE;
        }
        if (!source.kind().mayFeed(target.kind())) {
            return WireRefusal.WRONG_WAY;
        }
        if (wires.size() >= MAX_WIRES || fanOut(from) >= MAX_FAN_OUT) {
            return WireRefusal.TOO_MANY;
        }
        for (Wire wire : wires) {
            if (wire.from() == from && wire.to() == to) {
                return WireRefusal.ALREADY;
            }
        }
        if (reaches(to, from)) {
            return WireRefusal.LOOP;
        }
        wires.add(new Wire(from, to));
        return WireRefusal.NONE;
    }

    public boolean disconnect(int from, int to) {
        return wires.removeIf(wire -> wire.from() == from && wire.to() == to);
    }

    /** Every wire touching this pin, taken off. Used when the wielder cuts a pin loose in place. */
    public boolean unwire(int id) {
        return wires.removeIf(wire -> wire.from() == id || wire.to() == id);
    }

    /** Whether a signal leaving {@code from} could ever arrive at {@code to}. The loop test. */
    public boolean reaches(int from, int to) {
        if (from == to) {
            return true;
        }
        List<Integer> stack = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();
        stack.add(from);
        while (!stack.isEmpty()) {
            int at = stack.remove(stack.size() - 1);
            if (seen.contains(at)) {
                continue;
            }
            seen.add(at);
            for (Wire wire : wires) {
                if (wire.from() != at) {
                    continue;
                }
                if (wire.to() == to) {
                    return true;
                }
                stack.add(wire.to());
            }
        }
        return false;
    }

    public void clear() {
        nodes.clear();
        wires.clear();
        nextId = 1;
        suspended = false;
    }

    public void copyFrom(Weave other) {
        nodes.clear();
        wires.clear();
        nodes.putAll(other.nodes);
        wires.addAll(other.wires);
        nextId = other.nextId;
        suspended = other.suspended;
    }

    public static int clampX(int x) {
        return Math.max(0, Math.min(BOARD_W, x));
    }

    public static int clampY(int y) {
        return Math.max(0, Math.min(BOARD_H, y));
    }

    // ---- the save -----------------------------------------------------------------------------

    /**
     * Names rather than ordinals, so reordering a vocabulary does not silently turn every Erase on
     * every board into a Store. A word this build has never heard of drops its pin and its wires.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag nodeList = new ListTag();
        for (CausalNode node : nodes.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", node.id());
            entry.putString("kind", node.kind().name());
            entry.putString("word", wordName(node));
            entry.putInt("param", node.param());
            entry.putString("scope", node.scope().name());
            entry.putInt("x", node.x());
            entry.putInt("y", node.y());
            ListTag mods = new ListTag();
            for (Modifier modifier : node.modifiers()) {
                mods.add(StringTag.valueOf(modifier.name()));
            }
            entry.put("mods", mods);
            nodeList.add(entry);
        }
        tag.put("nodes", nodeList);
        ListTag wireList = new ListTag();
        for (Wire wire : wires) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("from", wire.from());
            entry.putInt("to", wire.to());
            wireList.add(entry);
        }
        tag.put("wires", wireList);
        tag.putInt("next", nextId);
        tag.putBoolean("suspended", suspended);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        ListTag nodeList = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodeList.size(); i++) {
            CausalNode node = readNode(nodeList.getCompound(i));
            if (node != null && nodes.size() < MAX_NODES && !nodes.containsKey(node.id())) {
                nodes.put(node.id(), node);
                nextId = Math.max(nextId, node.id() + 1);
            }
        }
        ListTag wireList = tag.getList("wires", Tag.TAG_COMPOUND);
        for (int i = 0; i < wireList.size(); i++) {
            CompoundTag entry = wireList.getCompound(i);
            // Through connect, so a save edited by hand or written by an older build cannot put a
            // loop or a wrong-way wire on a board the engine then has to survive.
            connect(entry.getInt("from"), entry.getInt("to"));
        }
        nextId = Math.max(nextId, tag.getInt("next"));
        suspended = tag.getBoolean("suspended");
    }

    private static String wordName(CausalNode node) {
        return switch (node.kind()) {
            case CAUSE -> node.cause().name();
            case CONDITION -> node.condition().name();
            case EFFECT -> node.effect().name();
        };
    }

    private static CausalNode readNode(CompoundTag entry) {
        NodeKind kind = lookup(NodeKind.values(), entry.getString("kind"));
        int id = entry.getInt("id");
        if (kind == null || id <= 0) {
            return null;
        }
        int ordinal = switch (kind) {
            case CAUSE -> ordinalOf(Cause.values(), entry.getString("word"));
            case CONDITION -> ordinalOf(Condition.values(), entry.getString("word"));
            case EFFECT -> ordinalOf(Effect.values(), entry.getString("word"));
        };
        if (ordinal < 0) {
            return null;
        }
        Scope scope = lookup(Scope.values(), entry.getString("scope"));
        List<Modifier> mods = new ArrayList<>();
        ListTag modList = entry.getList("mods", Tag.TAG_STRING);
        for (int i = 0; i < modList.size() && mods.size() < Modifier.MAX_PER_NODE; i++) {
            Modifier modifier = lookup(Modifier.values(), modList.getString(i));
            if (modifier != null && !mods.contains(modifier)) {
                mods.add(modifier);
            }
        }
        CausalNode node = new CausalNode(id, kind, ordinal, entry.getInt("param"),
                scope == null ? Scope.OTHER : scope, mods, clampX(entry.getInt("x")), clampY(entry.getInt("y")));
        // Back through withParam so a number outside what this build allows is pulled into range
        // rather than handed to the engine.
        return node.withParam(node.param());
    }

    private static <T extends Enum<T>> T lookup(T[] values, String name) {
        for (T value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
    }

    private static <T extends Enum<T>> int ordinalOf(T[] values, String name) {
        T found = lookup(values, name);
        return found == null ? -1 : found.ordinal();
    }

    /** Why a piece of string was refused. {@link #NONE} is the one that means it was not. */
    public enum WireRefusal {
        NONE,
        /** One of the two pins is not on the board, or they are the same pin. */
        NOTHING_THERE,
        /** String runs cause to condition to effect and never back. */
        WRONG_WAY,
        /** The board is full of string, or this pin already branches as far as it may. */
        TOO_MANY,
        /** That string is already there. */
        ALREADY,
        /** The far end already reaches the near one, and a cause cannot be its own consequence. */
        LOOP;

        public boolean ok() {
            return this == NONE;
        }

        public String translationKey() {
            return "message.magical.weave_wire_" + name().toLowerCase(Locale.ROOT);
        }
    }
}
