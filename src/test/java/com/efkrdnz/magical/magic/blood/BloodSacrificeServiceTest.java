package com.efkrdnz.magical.magic.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Everything the server checks before it charges a hundred blood.
 *
 * <p>The seal packet carries two lists chosen on the client, so these are the rules that stand
 * between a forged packet and a free pact. Sealing itself needs a live player and is a game test;
 * what is pinned here is the judgement that runs first.
 */
class BloodSacrificeServiceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState fullVessel() {
        PlayerMagicState state = new PlayerMagicState();
        state.addBloodVessel(BloodSacrificeService.RITUAL_COST);
        return state;
    }

    @Test
    void theRitualWantsTheWholeVesselAndNotAPointLess() {
        assertEquals(PlayerMagicState.MAX_BLOOD_VESSEL, BloodSacrificeService.RITUAL_COST,
                "a ritual that wants less than a full Vessel is not a ritual");
    }

    @Test
    void aFairPactIsAccepted() {
        assertNull(BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.SANGUINE_MIGHT.id()),
                List.of(MagicPassiveContent.GLASS_BONES.id())));
    }

    @Test
    void aPactMustBePaidFor() {
        assertEquals(BloodSacrificeService.Refusal.PRICES_TOO_CHEAP, BloodSacrificeService.validate(
                fullVessel(), List.of(MagicPassiveContent.SANGUINE_MIGHT.id()), List.of()));
    }

    @Test
    void aPactMayNotSpendMoreThanItHas() {
        assertEquals(BloodSacrificeService.Refusal.OVER_BUDGET, BloodSacrificeService.validate(
                fullVessel(), SacrificeCatalogue.BOONS, SacrificeCatalogue.PRICES));
    }

    @Test
    void anEmptyVesselIsNoPactAtAll() {
        assertEquals(BloodSacrificeService.Refusal.VESSEL_NOT_FULL, BloodSacrificeService.validate(
                new PlayerMagicState(), List.of(MagicPassiveContent.CRIMSON_EDGE.id()),
                List.of(MagicPassiveContent.THIN_SKIN.id())));
    }

    @Test
    void oneShortOfFullIsStillShort() {
        PlayerMagicState state = new PlayerMagicState();
        state.addBloodVessel(BloodSacrificeService.RITUAL_COST - 1);

        assertEquals(BloodSacrificeService.Refusal.VESSEL_NOT_FULL, BloodSacrificeService.validate(state,
                List.of(MagicPassiveContent.CRIMSON_EDGE.id()), List.of(MagicPassiveContent.THIN_SKIN.id())));
    }

    @Test
    void theSameBoonTwiceIsOneBoonAndFiveForgedPoints() {
        assertEquals(BloodSacrificeService.Refusal.DUPLICATE, BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.CRIMSON_EDGE.id(), MagicPassiveContent.CRIMSON_EDGE.id()),
                List.of(MagicPassiveContent.THIN_SKIN.id(), MagicPassiveContent.OPEN_WOUND.id())));
    }

    @Test
    void anIdThatIsOnNeitherListIsRefused() {
        assertEquals(BloodSacrificeService.Refusal.UNKNOWN_ENTRY, BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.MANA_SKIN.id()), List.of(MagicPassiveContent.THIN_SKIN.id())));
    }

    @Test
    void aPriceSmuggledIntoTheBoonListIsRefusedRatherThanCounted() {
        // Otherwise a forged packet could pay for a pact with the same entry twice over.
        assertEquals(BloodSacrificeService.Refusal.UNKNOWN_ENTRY, BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.GLASS_BONES.id()), List.of(MagicPassiveContent.GLASS_BONES.id())));
    }

    @Test
    void anEmptyPactIsNotAPact() {
        assertEquals(BloodSacrificeService.Refusal.NOTHING_CHOSEN,
                BloodSacrificeService.validate(fullVessel(), List.of(), List.of()));
    }

    @Test
    void overshootingOnPricesIsAllowed() {
        // Costs are lumpy. A requirement of three with a four-point price left has to round up, and
        // rounding up is also a build: more prices is more for a Hellbroker to amplify.
        assertNull(BloodSacrificeService.validate(fullVessel(),
                List.of(MagicPassiveContent.SECOND_HEART.id()),
                List.of(MagicPassiveContent.BLOOD_DEBT.id())));
    }

    @Test
    void aHellbrokerBuysOnePointOnCredit() {
        PlayerMagicState broker = fullVessel();
        broker.unlockPassive(MagicPassiveContent.HELLBROKER.id());

        // Two points of boon against one point of price: refused without the broker, fine with it.
        List<ResourceLocation> boons = List.of(MagicPassiveContent.SANGUINE_MIGHT.id());
        List<ResourceLocation> prices = List.of(MagicPassiveContent.THIN_SKIN.id());

        assertEquals(BloodSacrificeService.Refusal.PRICES_TOO_CHEAP,
                BloodSacrificeService.validate(fullVessel(), boons, prices));
        assertNull(BloodSacrificeService.validate(broker, boons, prices));
    }

    @Test
    void theUnknownRollsTheWholeTableAndNothingElse() {
        Set<ResourceLocation> rolled = new HashSet<>();
        for (long seed = 0; seed < 4000; seed++) {
            rolled.add(BloodSacrificeService.rollUnknown(seed));
        }
        assertEquals(Set.of(MagicPassiveContent.SLOW_BLOOD.id(), MagicPassiveContent.MANA_DROUGHT.id(),
                MagicPassiveContent.GLASS_BONES.id(), MagicPassiveContent.SPELL_FIZZLE.id(),
                MagicPassiveContent.BLOOD_DEBT.id()), rolled);
    }

    @Test
    void theUnknownNeverRollsItself() {
        for (long seed = 0; seed < 4000; seed++) {
            assertNotEquals(MagicPassiveContent.THE_UNKNOWN.id(), BloodSacrificeService.rollUnknown(seed));
        }
    }

    @Test
    void theUnknownIsFairThreeTimesInFourAndRuinousOneTimeInTen() {
        // The gamble the two points buy. Rough counts rather than exact ones, because the roll is a
        // weight table and not a shuffle, but far enough apart that a reordered table fails this.
        int fair = 0;
        int ruinous = 0;
        for (long seed = 0; seed < 10000; seed++) {
            ResourceLocation rolled = BloodSacrificeService.rollUnknown(seed);
            if (SacrificeCatalogue.cost(rolled) == SacrificeCatalogue.cost(MagicPassiveContent.THE_UNKNOWN.id())) {
                fair++;
            }
            if (MagicPassiveContent.BLOOD_DEBT.id().equals(rolled)) {
                ruinous++;
            }
        }
        assertTrue(fair > 6500 && fair < 8500, "about three in four should be worth what was paid, got " + fair);
        assertTrue(ruinous > 500 && ruinous < 1500, "about one in ten should be the debt, got " + ruinous);
    }
}
