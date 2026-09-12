package com.efkrdnz.magical.forge.glyph;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.weapon.MagicalWeapons;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Which shapes a given weapon knows how to be inscribed with.
 *
 * <p>A sigil is not an object anyone carries. It is knowledge the smith draws on, and a weapon
 * either has somewhere for that shape to go or it does not - so this is a rule about the weapon in
 * the slot, checked when the chain is validated and mirrored in the codex list the player reads
 * while drawing.
 *
 * <p>Deliberately a side table rather than a column on {@link GlyphTemplate}. The recognizer's job
 * is to say which shape was drawn, and it should keep saying that whatever the weapon is: telling a
 * player "that is not a rune" when the truth is "that is not a rune <em>this</em> can take" is the
 * worse of the two errors by a distance.
 *
 * <p>Three kinds of access, and anything unlisted is universal - the thirty-odd glyphs that were
 * here before weapons existed need no entries and get none.
 */
public final class ForgeVocabulary {

    /** Where a glyph may be drawn. */
    public sealed interface Access {

        /** Any forgeable weapon, vanilla ones included. The default for anything unlisted. */
        record Universal() implements Access {}

        /** Any weapon of one of these shapes. A dagger's temper is every dagger's temper. */
        record Archetype(Set<WeaponClass> classes) implements Access {
            public Archetype {
                classes = Set.copyOf(classes);
            }
        }

        /** These catalogue weapons and nothing else. */
        record Signature(Set<ResourceLocation> weapons) implements Access {
            public Signature {
                weapons = Set.copyOf(weapons);
            }
        }
    }

    private static final Access UNIVERSAL = new Access.Universal();

    /**
     * The archetype pools. One entry per glyph that belongs to a shape of weapon rather than to
     * weapons in general.
     *
     * <p>The new tempers and forms are all here rather than on individual weapons, because a temper
     * is how a <em>kind</em> of weapon is worked. RUSH is what it means to temper a dagger; it is
     * not a secret one dagger keeps. What one weapon keeps is its signature, and that is declared
     * on the weapon itself, in {@code MagicalWeapons}.
     */
    private static final Map<String, Set<WeaponClass>> ARCHETYPE_POOL = Map.of(
            "rush", Set.of(WeaponClass.DAGGER),
            "lunge", Set.of(WeaponClass.DAGGER, WeaponClass.SPEAR),
            "heft", Set.of(WeaponClass.GREATSWORD, WeaponClass.AXE),
            "plunge", Set.of(WeaponClass.GREATSWORD),
            "coil", Set.of(WeaponClass.SPEAR),
            "reap", Set.of(WeaponClass.SCYTHE),
            "split", Set.of(WeaponClass.CLAWS),
            "hook", Set.of(WeaponClass.CLAWS),
            "tithe", Set.of(WeaponClass.GREATSWORD, WeaponClass.AXE, WeaponClass.SCYTHE),
            "chorus", Set.of(WeaponClass.SWORD, WeaponClass.SPEAR));

    private ForgeVocabulary() {}

    /**
     * How {@code glyphId} is gated.
     *
     * <p>Signature beats archetype: a weapon naming a glyph in its own column claims it outright,
     * so a shape can be one weapon's secret even when it would otherwise read as a shared temper.
     */
    public static Access of(String glyphId) {
        Set<ResourceLocation> owners = MagicalWeapons.owners(glyphId);
        if (!owners.isEmpty()) {
            return new Access.Signature(owners);
        }
        Set<WeaponClass> classes = ARCHETYPE_POOL.get(glyphId);
        return classes == null ? UNIVERSAL : new Access.Archetype(classes);
    }

    /**
     * Whether {@code weapon} may be inscribed with {@code glyphId}.
     *
     * <p>An empty stack allows everything. The forge refuses an empty slot long before the grammar
     * runs ({@code ForgeError.NO_WEAPON}), so narrowing here would only replace a clear error with
     * a confusing one.
     */
    public static boolean allows(String glyphId, ItemStack weapon) {
        if (weapon == null || weapon.isEmpty()) {
            return true;
        }
        Optional<WeaponDefinition> row = ForgeMaterials.catalogue(weapon);
        if (row.isPresent()) {
            return allows(glyphId, row.get());
        }
        // A vanilla weapon. It has a shape, so an archetype pool can still admit it, but it has no
        // row in the catalogue and so can never be the weapon a signature names.
        return switch (of(glyphId)) {
            case Access.Universal ignored -> true;
            case Access.Archetype archetype -> ForgeMaterials.weaponClass(weapon)
                    .map(archetype.classes()::contains)
                    .orElse(false);
            case Access.Signature ignored -> false;
        };
    }

    /**
     * The same question asked of a catalogue row rather than of a stack.
     *
     * <p>Minecraft-free, which is the point: it lets a test walk the whole catalogue against the
     * whole Art table and prove that no combination the game names is one no weapon can draw.
     */
    public static boolean allows(String glyphId, WeaponDefinition weapon) {
        return switch (of(glyphId)) {
            case Access.Universal ignored -> true;
            case Access.Archetype archetype -> archetype.classes().contains(weapon.archetype());
            case Access.Signature signature -> signature.weapons().contains(weapon.id());
        };
    }

    /**
     * The shapes a glyph belongs to before any weapon claims it, empty for one that belongs to none.
     *
     * <p>The one question {@link #of} cannot answer, because {@code of} has already applied the
     * signature override. A test needs to ask it: a weapon claiming a glyph that belonged to a
     * <em>different</em> shape does not gain a secret, it deletes the glyph from the shape that had
     * it, and the Arts keyed on that combination stop being forgeable by anyone.
     */
    public static Set<WeaponClass> pool(String glyphId) {
        return ARCHETYPE_POOL.getOrDefault(glyphId, Set.of());
    }

    /**
     * Every glyph this shape of weapon may draw that weapons in general may not, in a stable order.
     *
     * <p>Glyphs another weapon has since claimed as a signature are gone from here, because they
     * are gone from the shape: that is what claiming one means.
     */
    public static List<String> archetypeGlyphs(WeaponClass archetype) {
        return ARCHETYPE_POOL.keySet().stream()
                .filter(glyphId -> of(glyphId) instanceof Access.Archetype pool
                        && pool.classes().contains(archetype))
                .sorted()
                .toList();
    }

    /** The grammar's view of one weapon: which ids it will accept, and nothing else. */
    public static Predicate<String> forWeapon(ItemStack weapon) {
        return glyphId -> allows(glyphId, weapon);
    }

    /**
     * The lang key naming what a refused glyph actually wants, for the error the forge shows.
     *
     * <p>A refusal that only says "not on this weapon" leaves the player guessing which weapon it
     * <em>is</em> on, which is the whole question they are asking. Where several would do, the
     * first in a stable order is named rather than all of them, because the error is one line.
     */
    public static Optional<String> requirementKey(String glyphId) {
        return switch (of(glyphId)) {
            case Access.Universal ignored -> Optional.empty();
            case Access.Archetype archetype -> archetype.classes().stream()
                    .min(Comparator.comparingInt(Enum::ordinal))
                    .map(WeaponClass::nameKey);
            case Access.Signature signature -> signature.weapons().stream()
                    .min(Comparator.comparing(ResourceLocation::toString))
                    .flatMap(id -> MagicalWeapons.get(id).map(WeaponDefinition::nameKey));
        };
    }
}
