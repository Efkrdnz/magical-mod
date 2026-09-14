package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.efkrdnz.magical.magic.eldritch.EldritchPrices;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** What each call to the deep costs in Notice before the points move it. */
class EldritchPricesTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("magical", path);
    }

    @Test
    void everyCallHasItsPriceAndTheCallIsTheCheapestBecauseItIsPaidPerPulse() {
        assertEquals(12, EldritchPrices.base(id("grasp_of_the_deep")));
        assertEquals(8, EldritchPrices.base(id("unblinking_eye")));
        assertEquals(15, EldritchPrices.base(id("hungering_maw")));
        assertEquals(6, EldritchPrices.base(id("tendril_lash")));
        assertEquals(10, EldritchPrices.base(id("skin_of_the_deep")));
        assertEquals(5, EldritchPrices.base(id("call_of_the_deep")));
    }

    @Test
    void aSkillOutsideTheKitIsAnErrorRatherThanFree() {
        assertThrows(IllegalArgumentException.class, () -> EldritchPrices.base(id("vein_walk")));
        assertThrows(IllegalArgumentException.class, () -> EldritchPrices.base(null));
    }
}
