package com.efkrdnz.magical.forge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Optional;

import com.efkrdnz.magical.forge.art.ForgeArt;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Somebody has to load {@link ForgeSpecials}.
 *
 * <p>Everything it owns is built in its class initialiser, and two other test classes say in their
 * own comments that it "refuses to load unless every Art has behaviour bound" - while neither of
 * them ever mentions it in code. That gap shipped a crash: the widened Art table let an Art carry no
 * element, the index builder handed that null straight to {@code ForgeIds.id}, and the resulting
 * {@code ExceptionInInitializerError} surfaced in the creative inventory, hovering an item, several
 * layers away from anything that looked responsible.
 *
 * <p>An initialiser that throws is the worst kind of failure to find late, because the class never
 * recovers - every later touch of it in the same JVM throws again, with a stack trace pointing at
 * whoever touched it rather than at what is wrong. So the first assertion here is simply that the
 * class can be loaded, and the rest walk the table it builds.
 */
class ForgeSpecialsLoadTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Reading {@code size()} is what forces the initialiser to run; the count is the second check. */
    @Test
    void theTableLoadsAndBindsOneBehaviourPerArt() {
        assertEquals(ForgeArt.values().length, ForgeSpecials.size(),
                "an Art in the table has no behaviour, or a behaviour is bound to something else");
    }

    /** The pair lookup, which is all most callers hold, still reaches every Art a pair can name. */
    @Test
    void everyArtAPairCanIdentifyIsFoundByThatPair() {
        int found = 0;
        for (ForgeArt art : ForgeArt.values()) {
            if (!art.keyedOnPairAlone()) {
                continue;
            }
            assertTrue(ForgeSpecials.has(ForgeIds.id(art.element()), ForgeIds.id(art.form())),
                    art + " is keyed on an element and a form but cannot be found by them");
            found++;
        }
        assertTrue(found > 0, "no Art is reachable by a pair, so this proves nothing");
    }

    /**
     * And every Art at all is found by the exact columns it declares.
     *
     * <p>This is the end-to-end one: it goes through {@code bestMatch}, so it also proves the Art the
     * strike pipeline picks for a combination is the Art that named it, rather than some other row
     * that happens to match the same shape.
     */
    @Test
    void everyArtIsFoundByItsOwnColumns() {
        for (ForgeArt art : ForgeArt.values()) {
            ElementDefinition element = art.element() == null
                    ? null
                    : ForgeElements.get(ForgeIds.id(art.element()))
                            .orElseThrow(() -> new AssertionError(art + " names an element that does not exist"));
            FormDefinition form = ForgeForms.get(ForgeIds.id(art.form()))
                    .orElseThrow(() -> new AssertionError(art + " names a form that does not exist"));
            WeaponClass archetype = art.archetype() == null
                    ? null
                    : WeaponClass.valueOf(art.archetype().toUpperCase(Locale.ROOT));
            ResourceLocation temper = art.temper() == null ? null : ForgeIds.id(art.temper());

            assertEquals(Optional.of(art),
                    ForgeArt.bestMatch(art.element(), art.form(), art.archetype(), art.temper()),
                    art + " is not what its own combination resolves to");
            assertTrue(ForgeSpecials.lookup(element, form, archetype, temper).isPresent(),
                    art + " resolves but has no behaviour behind it");
        }
    }

    /**
     * The pair index holds exactly the pairs the table says carry an Art - no more, no fewer.
     *
     * <p>Swept over every element against every form, because the index is built by a loop over the
     * table and the two ways that loop can be wrong are opposite: dropping a pair that should be
     * there, and admitting an Art that no pair should reach. Asking each pair the same question
     * twice, once of the index and once of the table, catches both without restating either.
     *
     * <p>Without this, "skip the Arts with no element" and "crash on the Arts with no element" pass
     * every other test in this file, and only one of them is a decision.
     */
    @Test
    void thePairIndexHoldsExactlyThePairsTheTableSaysItShould() {
        for (ElementDefinition element : ForgeElements.all()) {
            for (FormDefinition form : ForgeForms.all()) {
                Optional<ForgeArt> art = ForgeSpecials.art(element.id(), form.id());
                assertEquals(art.isPresent(), ForgeSpecials.has(element.id(), form.id()),
                        "the pair " + element.id() + "/" + form.id()
                                + " is in the behaviour index but not in the table, or the other way round");
                art.ifPresent(found -> assertNotNull(found.element(),
                        "the pair " + element.id() + "/" + form.id() + " resolves to " + found
                                + ", which is keyed on the shape of the weapon and should need more than a pair"));
            }
        }
    }
}
