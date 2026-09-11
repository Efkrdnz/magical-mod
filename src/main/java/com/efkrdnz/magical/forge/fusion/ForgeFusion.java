package com.efkrdnz.magical.forge.fusion;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The element pairs that fuse into something new, and what each one demands of the smith.
 *
 * <p>A fusion is resolved in the grammar, before a recipe exists, and what comes out is a single
 * compound element. Nothing below the grammar knows fusion happened: the component still stores one
 * element id, the rider still switches on one kind. That is the point - the alternative, two live
 * elements at runtime, would force every one of those switches to decide which of two riders wins,
 * which colour to draw, and which Art to fire.
 *
 * <p>Deliberately free of Minecraft types so the table can be pinned by a plain unit test, in the
 * same shape {@code ForgeArt} uses.
 */
public enum ForgeFusion {

    /**
     * The one the smith has to earn. Fire and dark together are the sorcery a Divinesmith learns as
     * Black Flames, and forging it demands they actually know it.
     *
     * <p>Dark rather than void: black flames are dark magic, and the skills that share the name now
     * sit in the dark school on the -2 layer. Fire + void is no longer a recipe at all.
     */
    BLACK_FLAME("fire", "dark", "black_flame", "divinesmith", List.of("black_flames")),

    /**
     * Air fed into flame. Ungated on purpose: it is the fusion that teaches a player fusion exists,
     * and it does something no rider does - it detonates.
     */
    EXPLOSION("fire", "gale", "explosion", null, List.of()),

    /** A wind that freezes and drags. */
    RIME_GALE("frost", "gale", "rime_gale", null, List.of()),

    /** Fire carried along a chain of lightning. */
    PLASMA("fire", "storm", "plasma", null, List.of()),

    /** Fire poured into the ground, left burning where it lands. */
    MAGMA("fire", "terra", "magma", null, List.of()),

    /** A storm of ice that spreads its cold as it jumps. */
    HAILSTORM("frost", "storm", "hailstorm", null, List.of()),

    /** Light and void together: blinding rot that still smites the undead. */
    ECLIPSE("void", "radiant", "eclipse", null, List.of()),

    /** Rot and poison, and nothing that heals through either. */
    BLIGHT("void", "venom", "blight", null, List.of()),

    /** Poison worked into the ground, so the ground itself is poison. */
    VERDIGRIS("venom", "terra", "verdigris", null, List.of());

    private final String first;
    private final String second;
    private final String resultPath;
    private final String requiredClass;
    private final List<String> requiredSkills;

    ForgeFusion(String first, String second, String resultPath, String requiredClass,
            List<String> requiredSkills) {
        this.first = first;
        this.second = second;
        this.resultPath = resultPath;
        this.requiredClass = requiredClass;
        this.requiredSkills = List.copyOf(requiredSkills);
    }

    /**
     * The fusion two element glyphs make, in either order.
     *
     * <p>Order-insensitive because the element is the material the blade is made of, not a step in
     * the program. Only the run of forms and modifiers is read in order.
     */
    public static Optional<ForgeFusion> of(String elementA, String elementB) {
        for (ForgeFusion fusion : values()) {
            boolean straight = fusion.first.equals(elementA) && fusion.second.equals(elementB);
            boolean reversed = fusion.first.equals(elementB) && fusion.second.equals(elementA);
            if (straight || reversed) {
                return Optional.of(fusion);
            }
        }
        return Optional.empty();
    }

    /** The fusion that produced this element path, if any. */
    public static Optional<ForgeFusion> byResult(String path) {
        for (ForgeFusion fusion : values()) {
            if (fusion.resultPath.equals(path)) {
                return Optional.of(fusion);
            }
        }
        return Optional.empty();
    }

    /** The two element glyph paths that make this fusion, in table order. */
    public List<String> components() {
        return List.of(first, second);
    }

    /** Path of the compound element this fuses into. */
    public String resultPath() {
        return resultPath;
    }

    /** Path of the class that may forge this, or empty when any smith may. */
    public Optional<String> requiredClass() {
        return Optional.ofNullable(requiredClass);
    }

    /** Paths of the skills that must already be unlocked. Empty for an ungated fusion. */
    public List<String> requiredSkills() {
        return requiredSkills;
    }

    public boolean isGated() {
        return requiredClass != null || !requiredSkills.isEmpty();
    }

    /** Translation key naming what this fusion asks for, shown when the forge refuses it. */
    public String requirementKey() {
        return "forge.magical.fusion." + name().toLowerCase(Locale.ROOT) + ".requirement";
    }

    /** Translation key of the fusion display name. */
    public String nameKey() {
        return "forge.magical.fusion." + name().toLowerCase(Locale.ROOT);
    }
}
