package com.efkrdnz.magical.magic.passive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.SacrificeCatalogue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The parts of a pact's effects that need no live player: who claims what, and the numbers the pure
 * helpers produce. Anything that deals damage is a game test instead, because damage needs a world.
 */
class SacrificeEffectsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState withPrices(int count) {
        PlayerMagicState state = new PlayerMagicState();
        for (ResourceLocation id : SacrificeCatalogue.PRICES.subList(0, count)) {
            state.grantRitualPassive(id, 400);
        }
        return state;
    }

    @Test
    void oneHandlerClaimsAllThirtyThree() {
        Set<ResourceLocation> handled = new SacrificePassives().handled();
        assertEquals(33, handled.size(), "thirty-two entries and the broker");
        assertTrue(handled.contains(MagicPassiveContent.HELLBROKER.id()));
        for (ResourceLocation id : SacrificeCatalogue.all()) {
            assertTrue(handled.contains(id), id + " has no handler");
        }
    }

    @Test
    void noOtherHandlerClaimsAnyOfThem() {
        // The coverage test asserts every passive has a handler; this asserts none has two, which
        // would mean an effect applied twice with nothing on screen to say so.
        Set<ResourceLocation> ours = new SacrificePassives().handled();
        for (ClassPassiveHandler handler : ClassPassiveEffects.handlers()) {
            if (handler instanceof SacrificePassives) {
                continue;
            }
            Set<ResourceLocation> overlap = new HashSet<>(handler.handled());
            overlap.retainAll(ours);
            assertTrue(overlap.isEmpty(), handler.getClass().getSimpleName() + " also claims " + overlap);
        }
    }

    @Test
    void spellFizzleIsFifteenPercentAloneAndNeverPastAThird() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.SPELL_FIZZLE.id(), 400);

        assertEquals(0.15F, SacrificeCurses.fizzleChance(state, false), 0.0001F);

        for (ResourceLocation id : List.of(MagicPassiveContent.GLASS_BONES.id(),
                MagicPassiveContent.SLOW_BLOOD.id(), MagicPassiveContent.THIN_SKIN.id())) {
            state.grantRitualPassive(id, 400);
        }

        assertEquals(0.3375F, SacrificeCurses.fizzleChance(state, true), 0.0001F,
                "four prices under a Hellbroker, and not a point past the ceiling");
    }

    @Test
    void bloodborneFuryPaysPerPriceAndOnlyForPrices() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.BLOODBORNE_FURY.id(), 400);
        state.grantRitualPassive(MagicPassiveContent.CRIMSON_EDGE.id(), 400);

        assertEquals(1.0F, SacrificeBoons.furyMultiplier(state), 0.0001F, "a boon is not a price");

        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 400);
        state.grantRitualPassive(MagicPassiveContent.SLOW_BLOOD.id(), 400);

        assertEquals(1.16F, SacrificeBoons.furyMultiplier(state), 0.0001F, "eight percent each");
    }

    @Test
    void glassBonesStaysAFifthWorseUntilASecondPriceJoinsIt() {
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 400);

        assertEquals(12.0F, SacrificeCurses.incoming(state, 10.0F), 0.0001F, "a fifth harder, alone");

        state.grantRitualPassive(MagicPassiveContent.SLOW_BLOOD.id(), 400);
        assertEquals(12.0F, SacrificeCurses.incoming(state, 10.0F), 0.0001F,
                "and unchanged without a Hellbroker to broker it");
    }

    @Test
    void anAmplifiedPriceScalesItsPenaltyRatherThanItself() {
        // Glass Bones is a fifth worse. Two prices under a Hellbroker make it three tenths worse,
        // not two fifths - scaling the whole multiplier would double a hit that was only ever meant
        // to be a fifth harder.
        PlayerMagicState state = new PlayerMagicState();
        state.unlockPassive(MagicPassiveContent.HELLBROKER.id());
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 400);
        state.grantRitualPassive(MagicPassiveContent.SLOW_BLOOD.id(), 400);

        assertEquals(13.0F, SacrificeCurses.incoming(state, 10.0F), 0.0001F);
    }

    @Test
    void nothingFiresWhileNoRitualIsRunning() {
        PlayerMagicState idle = new PlayerMagicState();
        assertEquals(0.0F, SacrificeCurses.fizzleChance(idle, true), 0.0001F);
        assertEquals(1.0F, SacrificeBoons.furyMultiplier(idle), 0.0001F);
        assertEquals(10.0F, SacrificeCurses.incoming(idle, 10.0F), 0.0001F);
        assertEquals(10.0F, SacrificeBoons.incoming(idle, 10.0F), 0.0001F);
        assertEquals(0, SacrificeBoons.bonusBarrier(idle));
    }

    @Test
    void theBoonsAndThePricesPullAgainstEachOtherOnTheSameNumber() {
        // Clotted Hide softens a blow and Glass Bones sharpens it. Taking both is a legal pact and
        // has to come out somewhere sensible rather than applying only one of them.
        PlayerMagicState state = new PlayerMagicState();
        state.grantRitualPassive(MagicPassiveContent.CLOTTED_HIDE.id(), 400);
        state.grantRitualPassive(MagicPassiveContent.GLASS_BONES.id(), 400);

        float landed = SacrificeCurses.incoming(state, SacrificeBoons.incoming(state, 10.0F));
        assertEquals(10.2F, landed, 0.0001F, "0.85 then 1.2");
    }

    @Test
    void aPriceCountsWhileItRunsAndStopsCountingWhenItEnds() {
        PlayerMagicState state = withPrices(3);
        assertEquals(3, state.activeRitualPriceCount());

        state.clearRitualPassives();
        assertEquals(0, state.activeRitualPriceCount());
    }
}
