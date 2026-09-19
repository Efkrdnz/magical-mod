package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The one pure edge of the service: what a typed verse name becomes. */
class IncantationServiceTest {

    @Test
    void aBarePathIsAMagicalVerse() {
        assertEquals(List.of(VerseIds.of("needle"), VerseIds.of("weight")), IncantationService.parseIds(List.of("needle", "weight")));
    }

    @Test
    void aQualifiedIdIsKept() {
        assertEquals(List.of(ResourceLocation.fromNamespaceAndPath("other", "thing")), IncantationService.parseIds(List.of("other:thing")));
    }

    @Test
    void oneBadNameRefusesTheWholeList() {
        assertNull(IncantationService.parseIds(List.of("needle", "Not A Path")));
        assertNull(IncantationService.parseIds(List.of("")));
        assertNull(IncantationService.parseIds(List.of("bad::id")));
        assertNull(IncantationService.parseIds(null));
    }

    @Test
    void thePayloadCarriesNoMoreThanTheCapsAllow() {
        assertEquals(ReciteCaps.MAX_VERSES, SetIncantationPayloadCaps.MAX_IDS);
        assertEquals(64, SetIncantationPayloadCaps.MAX_ID_LENGTH);
    }
}
