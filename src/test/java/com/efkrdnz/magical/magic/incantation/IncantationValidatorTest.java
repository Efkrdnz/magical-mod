package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The validator is the one set of words the server and the future screen both speak: it is pure,
 * it reports every problem with the index it sits at, and it says nothing about a legal tape.
 */
class IncantationValidatorTest {

    private static VerseCatalogue catalogue() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(Verse.of("needle", VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (r, rec, it) -> VerseAction.NONE));
        catalogue.register(Verse.of("weight", VerseType.MODIFIER, 3, Verse.UNLIMITED, null, 1,
                Verse.Declared.of(1, 2, 0), (r, rec, it) -> VerseAction.NONE));
        return catalogue;
    }

    @Test
    void aLegalTapeHasNoProblems() {
        List<ResourceLocation> ids = List.of(VerseIds.of("weight"), VerseIds.of("needle"));
        assertTrue(IncantationValidator.problems(ids, 1, Set.copyOf(ids), catalogue()).isEmpty());
        assertTrue(IncantationValidator.problems(List.of(), 1, Set.of(), catalogue()).isEmpty());
    }

    @Test
    void everyProblemNamesItsIndex() {
        List<ResourceLocation> ids = List.of(VerseIds.of("needle"), VerseIds.of("nothing"), VerseIds.of("weight"));
        List<IncantationValidator.Finding> findings =
                IncantationValidator.problems(ids, 0, Set.of(VerseIds.of("needle")), catalogue());
        assertEquals(3, findings.size());
        assertEquals(new IncantationValidator.Finding(IncantationValidator.Problem.BAD_BREATH, -1, null), findings.get(0));
        assertEquals(new IncantationValidator.Finding(IncantationValidator.Problem.UNKNOWN_VERSE, 1, VerseIds.of("nothing")), findings.get(1));
        assertEquals(new IncantationValidator.Finding(IncantationValidator.Problem.NOT_KNOWN, 2, VerseIds.of("weight")), findings.get(2));
    }

    @Test
    void lengthIsCapped() {
        List<ResourceLocation> ids = Collections.nCopies(ReciteCaps.MAX_VERSES + 1, VerseIds.of("needle"));
        List<IncantationValidator.Finding> findings = IncantationValidator.problems(ids, 1, catalogue());
        assertEquals(1, findings.size());
        assertEquals(IncantationValidator.Problem.TOO_LONG, findings.get(0).problem());
    }
}
