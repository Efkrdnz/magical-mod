package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * A catalogue is an instance so a test can hold a small one. Two verses cannot share an id, an
 * unknown id answers null rather than throwing (the validator turns that into words), and every
 * prototype the table holds has the {@code magical:body/} namespace so it can never be mistaken for
 * a verse.
 */
class VerseCatalogueTest {

    private static Verse needle(String path) {
        return Verse.of(path, VerseType.PROJECTILE, 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1,
                Verse.Declared.of(0, 1, 0), (recital, recursion, iteration) -> VerseAction.NONE);
    }

    @Test
    void anIdIsRegisteredOnce() {
        VerseCatalogue catalogue = new VerseCatalogue();
        catalogue.register(needle("needle"));
        assertThrows(IllegalStateException.class, () -> catalogue.register(needle("needle")));
        assertEquals(1, catalogue.size());
    }

    @Test
    void anUnknownIdIsNull() {
        VerseCatalogue catalogue = new VerseCatalogue();
        assertNull(catalogue.get(VerseIds.of("nothing")));
        assertTrue(catalogue.ofType(VerseType.CONTROL).isEmpty());
    }

    @Test
    void recursiveIsACopyNotAMutation() {
        Verse plain = needle("needle");
        Verse recursive = plain.asRecursive();
        assertTrue(recursive.recursive());
        assertFalse(plain.recursive());
        assertEquals(plain.id(), recursive.id());
    }

    @Test
    void prototypesLiveUnderBody() {
        for (VersePrototype prototype : VersePrototypes.all()) {
            ResourceLocation id = prototype.id();
            assertEquals("magical", id.getNamespace());
            assertTrue(id.getPath().startsWith("body/"), id + " is not a body");
            assertEquals(prototype, VersePrototypes.byId(id));
        }
        assertEquals(17, VersePrototypes.all().size());
    }
}
