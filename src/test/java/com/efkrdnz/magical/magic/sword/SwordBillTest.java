package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The bill the HUD prints, and every place between the wielder and that numeral it can be lost.
 *
 * <p>Written against a photograph. A Sword God planted twelve bearings - eight at reach 4 with two
 * Edge on each and four at reach 3 with one - which {@code Station.weight} makes
 * {@code 8*(4*2) + 4*(3*1) = 76} of a draw of 84, and {@code magical array show} listed all twelve
 * on the server. The sigil read <b>24</b> of 84.
 *
 * <p>24 is not a fraction of 76 and it is not a scale: it is the exact bill of the first three
 * reach-4 stations, {@code 8 + 8 + 8}, which is {@link SwordRules#SUMMONER}'s whole draw, at which
 * point the fourth is {@link PlantResult#TOO_DEAR} and so is everything behind it. That number can
 * only be produced one way - by {@link SwordArray#load} re-running the plant rules over a save
 * while the Array is still standing on the base rung - and {@code SwordArray.save} deliberately
 * writes no rules at all, so <b>every load path has to put the rung on before it puts the bearings
 * on</b>. {@code SwordService.refreshRung} does that on login and on an evolve; it cannot undo it,
 * because by then the bearings the load threw away are gone.
 *
 * <p>So the pins here are in three rings. The arithmetic of the shape, which is pure. The core
 * round trip with the rung put on first, which is lossless and says so. And
 * {@link #aSwordGodsWholeStateKeepsAllTwelveAcrossASaveAndLoad}, which is the one that goes
 * through {@code PlayerMagicState} - the path the wire and the disk both take, and the one that
 * loses nine of the twelve.
 */
class SwordBillTest {

    /** Exactly the twelve bearings that were planted, in exactly the order they were planted in. */
    private static final List<Station> PHOTOGRAPHED = photographed();

    /** {@code 8*(4*2) + 4*(3*1)}, worked out by hand and never read back out of the Array. */
    private static final int BILL = 8 * (4 * 2) + 4 * (3 * 1);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Station> photographed() {
        List<Station> stations = new ArrayList<>();
        // Eight round the frame plane three yaw steps apart - 45 degrees, well clear of the two
        // steps SwordArray.SEPARATION_MIN wants - and the order matters: the reach-4 ring was
        // planted first, and it is that order that makes the base rung keep three and not four.
        for (int yaw = 0; yaw < Station.YAW_STEPS; yaw += 3) {
            stations.add(new Station(yaw, 0, 4, 2));
        }
        // Four more a full pitch step above them, so the separation is carried by the elevation.
        for (int yaw = 1; yaw < Station.YAW_STEPS; yaw += 6) {
            stations.add(new Station(yaw, 2, 3, 1));
        }
        return List.copyOf(stations);
    }

    private static SwordArray planted(SwordRules rules) {
        SwordArray array = new SwordArray();
        array.setRules(rules);
        for (Station station : PHOTOGRAPHED) {
            assertEquals(PlantResult.PLANTED, array.plant(station, 0),
                    "the photographed shape is legal at the top rung, bearing by bearing: " + station);
        }
        return array;
    }

    /** A wielder on the top rung with the photographed shape on them, and nothing else. */
    private static PlayerMagicState swordGod() {
        PlayerMagicState state = new PlayerMagicState();
        state.classProgressFor(MagicalClasses.SWORD_GOD).unlock();
        // What SwordService.refreshRung does, minus the sync a test has no player to send.
        state.swordArray().setRules(SwordService.rulesFor(state));
        assertEquals(SwordRules.GOD, state.swordArray().rules(), "the rung comes off the class progress");
        for (Station station : PHOTOGRAPHED) {
            assertEquals(PlantResult.PLANTED, state.swordArray().plant(station, 0), station.toString());
        }
        return state;
    }

    // ---- the shape ----------------------------------------------------------------------------

    @Test
    void theTwelveStationShapeBillsSeventySix() {
        SwordArray array = planted(SwordRules.GOD);
        assertEquals(12, array.size(), "twelve bearings is one full ring and the hard ceiling");
        assertEquals(SwordArray.MAX_STATIONS, array.size());
        assertEquals(76, BILL, "the arithmetic in the field name");
        assertEquals(BILL, array.bill(), "sum of reach times Edge, and nothing else is in the bill");
        assertEquals(8 * 2 + 4 * 1, array.bound(), "twenty Edge in the air of a whole of thirty-six");
        assertTrue(array.bill() <= SwordRules.GOD.draw(),
                "76 of 84: the photographed shape is legal, so nothing here should ever shed");
        assertEquals(0, array.strainAt(1.0D), "and a legal shape at scale 1 is no strain at all");
    }

    @Test
    void theBillAtScaleOneIsTheBillItself() {
        SwordArray array = planted(SwordRules.GOD);
        // The floor the HUD numerator stands on: a held frame is scale 1, so a reading that is not
        // 76 on this shape is not a scale factor being applied - it is stations that are missing.
        assertEquals(BILL, array.billAt(1.0D));
        assertEquals(0, array.billAt(0.0D), "a fusing frame drives the scale to zero and the bill with it");
        assertEquals(BILL / 2, array.billAt(0.5D));
        assertEquals(114, array.billAt(1.5D), "and a bound frame stretching after a fleeing body bills more");
        assertEquals(1, array.billAt(0.001D), "ceil, so a scale the frame is still carrying bills at least one");
    }

    // ---- the core round trip ------------------------------------------------------------------

    @Test
    void theCoreRoundTripKeepsAllTwelveWhenTheRungIsPutOnFirst() {
        SwordArray array = planted(SwordRules.GOD);
        IntArrayTag tag = array.save();
        assertEquals(13, tag.getAsIntArray().length, "the version and one packed int a station");
        assertEquals(SwordArray.SAVE_VERSION, tag.getAsIntArray()[0]);

        SwordArray back = new SwordArray();
        back.setRules(SwordRules.GOD);
        back.load(tag);
        assertEquals(12, back.size());
        assertEquals(PHOTOGRAPHED, back.stations(), "station for station, in the order they were written");
        assertEquals(BILL, back.bill());
        assertEquals(array.bound(), back.bound());
    }

    @Test
    void aLoadOntoAnArrayStillOnTheBaseRungKeepsThreeStationsAndBillsTwentyFour() {
        // This is SwordArray doing exactly what it was designed to do, which is why it is pinned
        // here rather than changed: load() puts every bearing it reads through the same rules a
        // hand placement goes through, so an Array nobody has told its rung filters a Sword God
        // down to a Summoner. It is pinned because the number it produces is the fingerprint -
        // anyone who ever sees 24 on a 76 shape is looking at a load that ran too early.
        SwordArray base = new SwordArray();
        assertEquals(SwordRules.SUMMONER, base.rules(), "a fresh Array stands up on the base rung");

        base.load(planted(SwordRules.GOD).save());
        assertEquals(3, base.size(), "three of twelve survive the base rung");
        assertEquals(3 * (4 * 2), base.bill());
        assertEquals(24, base.bill(), "the numerator that was photographed");
        assertEquals(SwordRules.SUMMONER.draw(), base.bill(),
                "and it is the base rung's whole draw, which is what makes it a fingerprint and not a coincidence");
    }

    // ---- the path the wire and the disk both take -----------------------------------------------

    @Test
    void aSwordGodsWholeStateKeepsAllTwelveAcrossASaveAndLoad() {
        PlayerMagicState state = swordGod();
        assertEquals(BILL, state.swordArray().bill(), "the shape that goes in");

        CompoundTag tag = state.save();
        assertEquals(13, tag.getIntArray("swordArray").length, "and all twelve are on the wire");

        SwordArray loaded = PlayerMagicState.load(tag).swordArray();
        assertEquals(12, loaded.size(),
                "PlayerMagicState.load reads swordArray before it reads classes, so SwordService.rulesFor"
                        + " cannot answer GOD yet and SwordArray.load filters the shape down to the base"
                        + " rung. Move the swordArray line below the classes loop and precede it with"
                        + " state.swordArray.setRules(SwordService.rulesFor(state)).");
        assertEquals(PHOTOGRAPHED, loaded.stations(), "station for station");
        assertEquals(BILL, loaded.bill(), "and the numerator the sigil prints");
    }
}
