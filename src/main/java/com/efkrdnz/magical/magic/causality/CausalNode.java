package com.efkrdnz.magical.magic.causality;

import java.util.List;

/**
 * One pin on the board: a cause, a question or a consequence, with wherever it was pinned.
 *
 * <p>Immutable, and every edit on the board is a replacement rather than a mutation - the same
 * discipline the rest of the mod keeps, and the reason the screen can hold a copy of the Weave and
 * compare it with the saved one to know whether there is anything to save.
 *
 * <p>The {@code ordinal} is an index into whichever of {@link Cause}, {@link Condition} or
 * {@link Effect} the {@link #kind} names. It is stored rather than the enum so one record covers
 * all three and one NBT shape covers the whole board; {@link #cause()}, {@link #condition()} and
 * {@link #effect()} hand back the real thing, and null for the two it is not.
 *
 * <p>{@link #scope} and {@link #modifiers} mean nothing on a cause or a condition and are carried
 * anyway, at their defaults, so that changing a node from one kind to another cannot lose them.
 * {@link #weight()} ignores them for those kinds, which is what the budget is charged on.
 */
public record CausalNode(
        int id,
        NodeKind kind,
        int ordinal,
        int param,
        Scope scope,
        List<Modifier> modifiers,
        int x,
        int y) {

    public CausalNode {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        if (scope == null) {
            scope = Scope.OTHER;
        }
    }

    public static CausalNode of(int id, Cause cause, int x, int y) {
        return new CausalNode(id, NodeKind.CAUSE, cause.ordinal(), cause.defaultParam(), Scope.OTHER, List.of(), x, y);
    }

    public static CausalNode of(int id, Condition condition, int x, int y) {
        return new CausalNode(id, NodeKind.CONDITION, condition.ordinal(), condition.defaultParam(),
                Scope.OTHER, List.of(), x, y);
    }

    public static CausalNode of(int id, Effect effect, int x, int y) {
        return new CausalNode(id, NodeKind.EFFECT, effect.ordinal(), effect.defaultParam(), Scope.OTHER, List.of(), x, y);
    }

    public Cause cause() {
        return kind == NodeKind.CAUSE ? value(Cause.values()) : null;
    }

    public Condition condition() {
        return kind == NodeKind.CONDITION ? value(Condition.values()) : null;
    }

    public Effect effect() {
        return kind == NodeKind.EFFECT ? value(Effect.values()) : null;
    }

    /** Never out of range: an ordinal that has drifted past the end of its enum reads as the first. */
    private <T> T value(T[] values) {
        return values[ordinal < 0 || ordinal >= values.length ? 0 : ordinal];
    }

    /** What the board charges to hold this pin: the word itself, where it lands, and its tools. */
    public int weight() {
        return switch (kind) {
            case CAUSE -> cause().weight();
            case CONDITION -> condition().weight();
            case EFFECT -> {
                int total = effect().weight() + scope.weight();
                for (Modifier modifier : modifiers) {
                    total += modifier.weight();
                }
                yield total;
            }
        };
    }

    /** True when this pin reaches outside the wielder, and so wants a live mark to be worth anything. */
    public boolean needsAnchor() {
        return switch (kind) {
            case CAUSE -> cause().needsAnchor();
            case CONDITION -> condition().needsAnchor();
            case EFFECT -> scope.needsAnchor();
        };
    }

    public boolean takesParam() {
        return switch (kind) {
            case CAUSE -> cause().takesParam();
            case CONDITION -> condition().takesParam();
            case EFFECT -> effect().takesParam();
        };
    }

    public int minParam() {
        return switch (kind) {
            case CAUSE -> cause().minParam();
            case CONDITION -> condition().minParam();
            case EFFECT -> effect().minParam();
        };
    }

    public int maxParam() {
        return switch (kind) {
            case CAUSE -> cause().maxParam();
            case CONDITION -> condition().maxParam();
            case EFFECT -> effect().maxParam();
        };
    }

    /** The name the screen looks up, whichever vocabulary this pin is drawn from. */
    public String translationKey() {
        return switch (kind) {
            case CAUSE -> cause().translationKey();
            case CONDITION -> condition().translationKey();
            case EFFECT -> effect().translationKey();
        };
    }

    public String descriptionKey() {
        return switch (kind) {
            case CAUSE -> cause().descriptionKey();
            case CONDITION -> condition().descriptionKey();
            case EFFECT -> effect().descriptionKey();
        };
    }

    public CausalNode movedTo(int nx, int ny) {
        return new CausalNode(id, kind, ordinal, param, scope, modifiers, nx, ny);
    }

    /** A new pin with the number clamped into whatever this kind allows, so no caller has to. */
    public CausalNode withParam(int value) {
        int clamped = takesParam() ? Math.max(minParam(), Math.min(maxParam(), value)) : param;
        return new CausalNode(id, kind, ordinal, clamped, scope, modifiers, x, y);
    }

    public CausalNode withScope(Scope value) {
        return new CausalNode(id, kind, ordinal, param, value, modifiers, x, y);
    }

    /** Adds a tool, or takes it off again if it was already worn; refuses a third. */
    public CausalNode toggled(Modifier modifier) {
        if (kind != NodeKind.EFFECT || modifier == null) {
            return this;
        }
        List<Modifier> next = new java.util.ArrayList<>(modifiers);
        if (!next.remove(modifier)) {
            if (next.size() >= Modifier.MAX_PER_NODE) {
                return this;
            }
            next.add(modifier);
            next.sort(java.util.Comparator.comparingInt(Enum::ordinal));
        }
        return new CausalNode(id, kind, ordinal, param, scope, next, x, y);
    }

    public boolean wears(Modifier modifier) {
        return modifiers.contains(modifier);
    }

    /** The magnitude scale of everything worn, multiplied together. */
    public float modifierScale() {
        float scale = 1.0F;
        for (Modifier modifier : modifiers) {
            scale *= modifier.scale();
        }
        return scale;
    }
}
