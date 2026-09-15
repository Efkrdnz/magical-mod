package com.efkrdnz.magical.magic.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Sixteen boons, sixteen prices, every one of them registered, priced and sorted exactly once.
 *
 * <p>The cheapest thing to get wrong here is an entry that exists in the catalogue and nowhere
 * else, which would let the pick screen offer a pact the server cannot grant.
 */
class SacrificeCatalogueTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theCatalogueIsSixteenAndSixteen() {
        assertEquals(16, SacrificeCatalogue.BOONS.size());
        assertEquals(16, SacrificeCatalogue.PRICES.size());
    }

    @Test
    void everyEntryIsARegisteredPassiveThatIsNotACurse() {
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            assertNotNull(MagicPassiveContent.get(id), id + " must be registered");
            // Prices are curses in fiction only. A curse gets a Dispel button, and a price the
            // player can dispel is not a price.
            assertFalse(MagicPassiveContent.get(id).curse(), id + " must not carry the curse flag");
        }
    }

    @Test
    void everyEntryCostsBetweenOneAndFourPoints() {
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            int cost = SacrificeCatalogue.cost(id);
            assertTrue(cost >= 1 && cost <= 4, id + " costs " + cost + ", outside 1..4");
        }
    }

    @Test
    void nothingIsBothABoonAndAPrice() {
        Set<ResourceLocation> seen = new HashSet<>();
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            assertTrue(seen.add(id), id + " appears twice");
            assertEquals(SacrificeCatalogue.isBoon(id), !SacrificeCatalogue.isPrice(id),
                    id + " must be exactly one of the two");
        }
    }

    @Test
    void everyRequirementIsPayableWithWhatIsOnTheList() {
        // A budget of nine with nothing under two points on the price list would leave some spends
        // unpayable. One-pointers make every requirement from one to nine reachable exactly.
        assertTrue(SacrificeCatalogue.PRICES.stream().anyMatch(id -> SacrificeCatalogue.cost(id) == 1),
                "at least one price must cost a single point");
    }

    @Test
    void bothListsRunCheapestFirst() {
        // The screen lists them in this order, so the top of each column is where a small pact is
        // built and a player is never scrolling to find the affordable half.
        assertSortedByCost(SacrificeCatalogue.BOONS);
        assertSortedByCost(SacrificeCatalogue.PRICES);
    }

    @Test
    void theStateAgreesWithTheCatalogueAboutWhichIsWhich() {
        for (ResourceLocation id : SacrificeCatalogue.BOONS) {
            assertTrue(MagicPassiveContent.isRitualBoon(id), id.toString());
            assertFalse(MagicPassiveContent.isRitualPrice(id), id.toString());
        }
        for (ResourceLocation id : SacrificeCatalogue.PRICES) {
            assertTrue(MagicPassiveContent.isRitualPrice(id), id.toString());
        }
    }

    @Test
    void theRegistryAndTheCatalogueHoldExactlyTheSameEntries() {
        // Registering a passive and forgetting to price it would put a row on the screen worth
        // nothing, which is a free boon.
        assertEquals(MagicPassiveContent.ritualBoons(), Set.copyOf(SacrificeCatalogue.BOONS));
        assertEquals(MagicPassiveContent.ritualPrices(), Set.copyOf(SacrificeCatalogue.PRICES));
    }

    @Test
    void anIdOnNeitherListIsWorthNothing() {
        assertEquals(0, SacrificeCatalogue.cost(MagicPassiveContent.MANA_SKIN.id()),
                "an unknown id must never be spendable");
    }

    @Test
    void summingIgnoresAnythingFromTheOtherList() {
        // The seal packet carries two lists and is not to be trusted. A price smuggled into the
        // boon list must be worth nothing rather than quietly funding the pact.
        int mixed = SacrificeCatalogue.sum(
                List.of(MagicPassiveContent.SANGUINE_MIGHT.id(), MagicPassiveContent.GLASS_BONES.id()), true);
        assertEquals(SacrificeCatalogue.cost(MagicPassiveContent.SANGUINE_MIGHT.id()), mixed);
    }

    private static void assertSortedByCost(List<ResourceLocation> entries) {
        for (int i = 1; i < entries.size(); i++) {
            assertTrue(SacrificeCatalogue.cost(entries.get(i - 1)) <= SacrificeCatalogue.cost(entries.get(i)),
                    entries.get(i) + " is cheaper than the entry above it");
        }
    }
}
