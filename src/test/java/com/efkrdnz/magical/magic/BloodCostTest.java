package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Crimson Vessel's arithmetic, and the rules that keep blood magic from being a suicide button.
 *
 * <p>Charging a real cost needs a live ServerPlayer, so what is pinned here is everything
 * underneath it: how much the Vessel covers, what it hands back as a shortfall, that it survives a
 * save, and that potency rises the way the whole school is priced around.
 */
class BloodCostTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theVesselPaysFirstAndHandsBackOnlyWhatItCouldNotCover() {
        PlayerMagicState state = new PlayerMagicState();
        state.addBloodVessel(30);

        assertEquals(0, state.drawFromVessel(20), "a cost the Vessel covers leaves no shortfall");
        assertEquals(10, state.bloodVessel());

        assertEquals(15, state.drawFromVessel(25), "and the rest is what the caster has to bleed for");
        assertEquals(0, state.bloodVessel(), "the Vessel gives all it has before the body pays");
    }

    @Test
    void anEmptyVesselHandsBackTheWholeCostRatherThanSwallowingIt() {
        // The failure this guards: a Vessel that reports a cost as covered when it paid nothing
        // would make every blood skill free the moment the meter ran dry.
        PlayerMagicState state = new PlayerMagicState();

        assertEquals(40, state.drawFromVessel(40));
        assertEquals(0, state.bloodVessel());
    }

    @Test
    void theVesselNeitherOverfillsNorGoesNegative() {
        PlayerMagicState state = new PlayerMagicState();
        state.addBloodVessel(PlayerMagicState.MAX_BLOOD_VESSEL * 3);
        assertEquals(PlayerMagicState.MAX_BLOOD_VESSEL, state.bloodVessel(), "harvest cannot overfill");

        state.addBloodVessel(-PlayerMagicState.MAX_BLOOD_VESSEL * 3);
        assertEquals(0, state.bloodVessel(), "and spending cannot go below empty");
    }

    @Test
    void aFullVesselReachesTheClientRatherThanStayingOnTheServer() {
        // Same shape as the race pool bonus bug: save() is the wire format and ClientMagicState
        // rebuilds through copy(), so a field missing from either draws an empty bar over a full one.
        PlayerMagicState state = new PlayerMagicState();
        state.addBloodVessel(64);
        state.openWound(80);

        assertEquals(64, PlayerMagicState.load(state.save()).bloodVessel(), "lost over the wire");
        assertEquals(64, state.copy().bloodVessel(), "lost in copy()");
        assertEquals(80, PlayerMagicState.load(state.save()).copy().openWoundTicks(),
                "the client applies load() then copy(), so the wound must survive both");
    }

    @Test
    void aSaveWrittenBeforeBloodMagicExistedLoadsAsAnEmptyVessel() {
        PlayerMagicState state = new PlayerMagicState();
        state.addBloodVessel(50);
        CompoundTag legacy = state.save();
        legacy.remove("bloodVessel");
        legacy.remove("openWoundTicks");

        PlayerMagicState loaded = PlayerMagicState.load(legacy);
        assertEquals(0, loaded.bloodVessel(), "an old save reads as no blood, not as a crash");
        assertEquals(0, loaded.openWoundTicks());
    }

    @Test
    void aSavedVesselAboveTheCeilingIsClampedRatherThanTrusted() {
        // A save from a build with a bigger ceiling, or an edited one, must not hand out a Vessel
        // the rest of the code believes is impossible.
        PlayerMagicState state = new PlayerMagicState();
        CompoundTag tag = state.save();
        tag.putInt("bloodVessel", PlayerMagicState.MAX_BLOOD_VESSEL + 500);

        assertEquals(PlayerMagicState.MAX_BLOOD_VESSEL, PlayerMagicState.load(tag).bloodVessel());
    }

    @Test
    void theWoundClosesOnItsOwnAndStaysClosed() {
        PlayerMagicState state = new PlayerMagicState();
        state.openWound(3);

        assertTrue(state.tickOpenWound());
        assertTrue(state.tickOpenWound());
        assertEquals(1, state.openWoundTicks());
        // The third tick spends the last one, so the wound is shut and says so.
        assertFalse(state.tickOpenWound());
        assertEquals(0, state.openWoundTicks());
        assertFalse(state.tickOpenWound(), "and never goes negative");
        assertEquals(0, state.openWoundTicks());
    }

    @Test
    void aFreshWoundNeverShortensOneAlreadyOpen() {
        // Two payments in quick succession must not let the second, smaller one heal the first.
        PlayerMagicState state = new PlayerMagicState();
        state.openWound(100);
        state.openWound(10);

        assertEquals(100, state.openWoundTicks());
    }

    @Test
    void bloodMagicIsWeakestWhenYouAreSafestAndStrongestAtTheEdge() {
        // The whole reason the school is worth its price. If this ever inverts, every blood skill
        // becomes a reason to stay at full health, which is the opposite of the fantasy.
        assertEquals(1.0F, potencyAt(1.0F), 0.0001F);
        assertEquals(BloodService.MAX_POTENCY, potencyAt(0.0F), 0.0001F);
        assertTrue(potencyAt(0.25F) > potencyAt(0.75F), "potency must rise as health falls");
    }

    @Test
    void aHeartBuysTheSameAmountOfBloodMagicAsItBuysOfMana() {
        // Red Payment already taught players what a heart is worth. Two exchange rates would make
        // that lesson a lie, so this pins them together rather than leaving it to a comment.
        assertEquals(8, BloodService.COST_PER_HEALTH);
    }

    /** Mirrors BloodService.potency without needing a LivingEntity to hold the health. */
    private static float potencyAt(float healthFraction) {
        return 1.0F + (1.0F - healthFraction) * (BloodService.MAX_POTENCY - 1.0F);
    }
}
