package com.efkrdnz.magical.magic.incantation;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * One card. Type, mana, uses and the recursive flag are what the machine reads; the prototype and
 * body count are what an Impose reads; {@link Declared} is what a tooltip shows, and
 * {@code VerseContentTest} holds it to what the action actually does.
 */
public record Verse(ResourceLocation id, VerseType type, int mana, int maxUses, boolean recursive,
                    VersePrototype prototype, int bodies, Declared declared, VerseAction action) {

    public static final int UNLIMITED = -1;

    /** The numbers a verse claims: cards drawn after it, beat added, rest added. */
    public record Declared(int draw, int beat, int rest) {
        public static final int ALL = -1;
        public static final Declared NONE = new Declared(0, 0, 0);

        public static Declared of(int draw, int beat, int rest) {
            return new Declared(draw, beat, rest);
        }
    }

    public Verse {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(declared, "declared");
        Objects.requireNonNull(action, "action");
        if (bodies < 1) {
            throw new IllegalArgumentException(id + " fires " + bodies + " bodies");
        }
    }

    public static Verse of(String path, VerseType type, int mana, int maxUses, VersePrototype prototype,
                           int bodies, Declared declared, VerseAction action) {
        return new Verse(VerseIds.of(path), type, mana, maxUses, false, prototype, bodies, declared, action);
    }

    /** The same verse flagged {@code recursive}, which the recursion limit counts. (The record's own {@code recursive()} is the flag.) */
    public Verse asRecursive() {
        return new Verse(id, type, mana, maxUses, true, prototype, bodies, declared, action);
    }

    public boolean hasPrototype() {
        return prototype != null;
    }

    public boolean unlimited() {
        return maxUses < 0;
    }

    public String path() {
        return id.getPath();
    }
}
