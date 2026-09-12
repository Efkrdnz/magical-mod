package com.efkrdnz.magical.forge.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.art.ForgeArt;
import com.efkrdnz.magical.forge.chain.ForgeChainGrammar;
import com.efkrdnz.magical.forge.chain.ForgeError;
import com.efkrdnz.magical.forge.chain.ForgeValidation;
import com.efkrdnz.magical.forge.chain.RecognizedGlyph;
import com.efkrdnz.magical.forge.weapon.MagicalWeapons;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;
import com.efkrdnz.magical.registry.MagicalItems;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Exclusivity is only real if it is checked in both directions.
 *
 * <p>A rule that only rejects is a rule that can quietly reject everything - a typo in a pool key
 * makes a glyph unreachable on every weapon in the game, and nothing about that looks like a bug
 * from inside the forge. So each case below asserts the accept as well as the refusal.
 */
class ForgeVocabularyTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack stackOf(WeaponDefinition weapon) {
        return new ItemStack(MagicalItems.weapon(weapon.id()).get());
    }

    @Test
    void aUniversalGlyphIsDrawableOnEverythingIncludingVanilla() {
        assertInstanceOf(ForgeVocabulary.Access.Universal.class, ForgeVocabulary.of("slash"));
        assertTrue(ForgeVocabulary.allows("slash", new ItemStack(Items.IRON_SWORD)));
        assertTrue(ForgeVocabulary.allows("slash", ItemStack.EMPTY));
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            assertTrue(ForgeVocabulary.allows("slash", stackOf(weapon)),
                    weapon.id() + " cannot draw a universal glyph");
        }
    }

    @Test
    void anArchetypeGlyphIsDrawableOnThatShapeAndRefusedOnEveryOther() {
        ForgeVocabulary.Access access = ForgeVocabulary.of("plunge");
        ForgeVocabulary.Access.Archetype archetype =
                assertInstanceOf(ForgeVocabulary.Access.Archetype.class, access);
        assertTrue(archetype.classes().contains(WeaponClass.GREATSWORD));

        List<String> wrong = new ArrayList<>();
        boolean acceptedSomewhere = false;
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            boolean allowed = ForgeVocabulary.allows("plunge", stackOf(weapon));
            if (weapon.archetype() == WeaponClass.GREATSWORD) {
                assertTrue(allowed, weapon.id() + " is a greatsword and cannot draw plunge");
                acceptedSomewhere = true;
            } else if (allowed) {
                wrong.add(weapon.id().toString());
            }
        }
        assertTrue(acceptedSomewhere, "no greatsword exists, so this proves nothing");
        assertTrue(wrong.isEmpty(), "plunge is drawable on weapons that are not greatswords: " + wrong);
        assertFalse(ForgeVocabulary.allows("plunge", new ItemStack(Items.IRON_SWORD)),
                "a vanilla sword can draw a greatsword-only form");
    }

    @Test
    void aSignatureGlyphIsDrawableOnlyOnTheWeaponsThatClaimIt() {
        for (String glyph : MagicalWeapons.allSignatureGlyphs()) {
            boolean acceptedSomewhere = false;
            for (WeaponDefinition weapon : MagicalWeapons.all()) {
                boolean owns = weapon.signatureGlyphs().contains(glyph);
                boolean allowed = ForgeVocabulary.allows(glyph, stackOf(weapon));
                assertEquals(owns, allowed,
                        weapon.id() + (owns ? " cannot draw its own signature " : " can draw someone else's ") + glyph);
                acceptedSomewhere |= allowed;
            }
            assertTrue(acceptedSomewhere, glyph + " is claimed by nothing that can actually draw it");
            assertFalse(ForgeVocabulary.allows(glyph, new ItemStack(Items.IRON_SWORD)),
                    "a vanilla sword can draw the signature glyph " + glyph);
        }
    }

    /** A signature on the weapon overrides the shared pool, which is the whole point of having one. */
    @Test
    void aSignatureBeatsAnArchetypePool() {
        assertInstanceOf(ForgeVocabulary.Access.Signature.class, ForgeVocabulary.of("chorus"),
                "chorus is claimed by two weapons, so it should no longer read as a sword-wide rune");
        assertTrue(ForgeVocabulary.allows("chorus", stackOf(MagicalWeapons.CHORUS_EDGE)));
        assertTrue(ForgeVocabulary.allows("chorus", stackOf(MagicalWeapons.COILSPINE)));
        assertFalse(ForgeVocabulary.allows("chorus", stackOf(MagicalWeapons.TIDEWRACK)),
                "another sword can still draw a rune two weapons have claimed outright");
    }

    /**
     * A weapon may only claim a glyph its own shape could already have drawn.
     *
     * <p>Claiming one from a different shape reads like an exclusive and behaves like a deletion:
     * the pool it belonged to loses it, every weapon of that shape loses it, and the claim buys
     * nothing, because a weapon that could never draw the glyph still cannot. This is exactly how
     * THOUSAND_CUTS was lost - a pair of claws claimed {@code rush}, which is a dagger's temper.
     */
    @Test
    void aWeaponOnlyClaimsSigilsItsOwnShapeCouldHaveDrawn() {
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            for (String glyph : weapon.signatureGlyphs()) {
                Set<WeaponClass> pool = ForgeVocabulary.pool(glyph);
                assertTrue(pool.isEmpty() || pool.contains(weapon.archetype()),
                        weapon.path() + " claims " + glyph + ", which belongs to " + pool
                                + " and not to a " + weapon.archetype()
                                + ": claiming it deletes the glyph from the shape that owned it");
            }
        }
    }

    /**
     * Every Art in the table is forgeable by somebody.
     *
     * <p>{@code ForgeSpecials} already refuses to load unless every Art has behaviour bound to it,
     * which catches an Art nobody wrote. This catches the opposite and quieter failure: an Art with
     * behaviour, a name, a lang key and a description, keyed on a combination the vocabulary makes
     * impossible - so it is complete, tested, shipped, and can never once fire in a game.
     */
    @Test
    void everyArtCanBeForgedOnSomeWeaponInTheCatalogue() {
        for (ForgeArt art : ForgeArt.values()) {
            boolean forgeable = MagicalWeapons.all().stream().anyMatch(weapon -> canCarry(art, weapon));
            assertTrue(forgeable, art + " keys on " + art.key()
                    + ", which no weapon in the catalogue is allowed to draw");
        }
    }

    private static boolean canCarry(ForgeArt art, WeaponDefinition weapon) {
        if (art.archetype() != null
                && !art.archetype().equalsIgnoreCase(weapon.archetype().name())) {
            return false;
        }
        return drawable(art.element(), weapon)
                && drawable(art.form(), weapon)
                && drawable(art.temper(), weapon);
    }

    /** A column the Art leaves open asks nothing of the weapon. */
    private static boolean drawable(String glyph, WeaponDefinition weapon) {
        return glyph == null || ForgeVocabulary.allows(glyph, weapon);
    }

    /**
     * The rule has to reach the grammar, not just the table: the grammar is what the server re-runs
     * as the authority, and a rule the authority never asks about is decoration.
     */
    @Test
    void theGrammarRefusesAGlyphTheWeaponCannotTake() {
        List<RecognizedGlyph> chain = List.of(
                new RecognizedGlyph("crude", GlyphCategory.GRADE, 100),
                new RecognizedGlyph("fire", GlyphCategory.ELEMENT, 100),
                new RecognizedGlyph("plunge", GlyphCategory.FORM, 100));

        ForgeValidation onDagger = ForgeChainGrammar.validate(chain,
                ForgeVocabulary.forWeapon(stackOf(MagicalWeapons.HOLLOW_FANG)));
        ForgeValidation.Invalid refused = assertInstanceOf(ForgeValidation.Invalid.class, onDagger,
                "a dagger was allowed to take a greatsword's form");
        assertEquals(ForgeError.GLYPH_NOT_IN_VOCABULARY, refused.error());
        assertEquals(2, refused.argument(), "the error should point at the offending glyph");

        assertInstanceOf(ForgeValidation.Valid.class,
                ForgeChainGrammar.validate(chain, ForgeVocabulary.forWeapon(stackOf(MagicalWeapons.MOUNTAINBREAKER))),
                "the greatsword that owns plunge was refused it");
    }

    /**
     * What the codex lists and what the grammar accepts are the same set, for every weapon.
     *
     * <p>A list showing a glyph the server then refuses is worse than no filter at all: it invites
     * the player to spend a draw on something that was never going to work.
     */
    @Test
    void theCodexFilterAndTheGrammarAgreeForEveryWeapon() {
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            ItemStack stack = stackOf(weapon);
            for (GlyphTemplate template : ForgeGlyphLibrary.all()) {
                boolean listed = ForgeVocabulary.allows(template.id(), stack);
                boolean accepted = ForgeVocabulary.forWeapon(stack).test(template.id());
                assertEquals(listed, accepted,
                        weapon.id() + " lists and accepts " + template.id() + " differently");
            }
        }
    }

    /** Every gated glyph can say what it wants; a universal one has nothing to say. */
    @Test
    void everyGatedGlyphNamesWhatItNeeds() {
        assertTrue(ForgeVocabulary.requirementKey("slash").isEmpty());
        for (GlyphTemplate template : ForgeGlyphLibrary.all()) {
            if (ForgeVocabulary.of(template.id()) instanceof ForgeVocabulary.Access.Universal) {
                continue;
            }
            assertTrue(ForgeVocabulary.requirementKey(template.id()).isPresent(),
                    template.id() + " is gated but cannot say what it is gated on");
        }
    }
}
