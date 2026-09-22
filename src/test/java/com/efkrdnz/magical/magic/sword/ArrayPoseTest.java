package com.efkrdnz.magical.magic.sword;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The pose arithmetic, which the server behaviour and the client painter both run.
 *
 * <p>Twelve blade positions cost zero bytes on the wire because both sides work them out from the
 * same frame and the same shape - which means a sign error in {@link ArrayPose} is a <em>silent
 * mirror</em>: every blade appears on the wrong side, for everybody at once, consistently, with a
 * green build and nothing in the log. So the handedness is pinned here against Minecraft's own
 * rather than reasoned about: <b>yaw 0 faces +Z, yaw increases clockwise seen from above, +Y is
 * up</b>, and {@link Station#pitch()} is an elevation, so +4 carries +Y.
 */
class ArrayPoseTest {

    private static final double EXACT = 1.0E-9D;

    /** Root two over two, which is every 45-degree answer in here. */
    private static final double HALF_ROOT_TWO = Math.sqrt(2.0D) / 2.0D;

    private static Frame held(float yaw, float pitch) {
        return new Frame(0.0D, 0.0D, 0.0D, yaw, pitch, 1.0F);
    }

    private static void near(double[] expected, double[] actual, String why) {
        assertArrayEquals(expected, actual, 1.0E-9D, why);
    }

    // ---- the handedness -----------------------------------------------------------------------

    @Test
    void theLatticeIsHandedTheWayMinecraftIs() {
        near(new double[] {0.0D, 0.0D, 1.0D}, new Station(0, 0, 1, 1).unitBearing(), "yaw 0 is +Z");
        near(new double[] {-1.0D, 0.0D, 0.0D}, new Station(6, 0, 1, 1).unitBearing(),
                "yaw step 6 is 90 degrees, and 90 degrees clockwise from +Z is -X");
        near(new double[] {0.0D, 0.0D, -1.0D}, new Station(12, 0, 1, 1).unitBearing(), "the antipode is -Z");
        near(new double[] {1.0D, 0.0D, 0.0D}, new Station(18, 0, 1, 1).unitBearing(), "and 270 is +X");
    }

    @Test
    void pitchIsAnElevationAndPlusFourIsSeventyTwoDegreesUp() {
        double up = Math.sin(Math.toRadians(72.0D));
        double out = Math.cos(Math.toRadians(72.0D));
        near(new double[] {0.0D, up, out}, new Station(0, 4, 1, 1).unitBearing(), "+4 carries +Y");
        near(new double[] {0.0D, -up, out}, new Station(0, -4, 1, 1).unitBearing(), "-4 carries -Y");
        near(new double[] {0.0D, 0.0D, 1.0D}, new Station(0, 0, 1, 1).unitBearing(), "and 0 is flat");
        assertEquals(0.5877852522924731D, new Station(0, 2, 1, 1).unitBearing()[1], EXACT, "sin 36");
    }

    @Test
    void everyBearingIsUnitLength() {
        for (int yaw = 0; yaw < Station.YAW_STEPS; yaw++) {
            for (int pitch = Station.PITCH_MIN; pitch <= Station.PITCH_MAX; pitch++) {
                double[] b = new Station(yaw, pitch, 1, 1).unitBearing();
                assertEquals(1.0D, Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]), EXACT,
                        "yaw " + yaw + " pitch " + pitch);
            }
        }
    }

    // ---- the frame ----------------------------------------------------------------------------

    @Test
    void aStationIsWhereBothSidesSayItIs() {
        Station station = new Station(5, -3, 4, 7);
        Frame frame = new Frame(12.5D, 64.0D, -30.25D, 143.0F, -22.0F, 1.375F);

        // The literal claim: two callers, identical arguments, bit-identical doubles. There is no
        // packet between them and there is not going to be one.
        assertArrayEquals(ArrayPose.worldOffset(station, frame), ArrayPose.worldOffset(station, frame), 0.0D);
        assertArrayEquals(ArrayPose.worldBearing(station, frame), ArrayPose.worldBearing(station, frame), 0.0D);
        assertEquals(ArrayPose.bladeYaw(station, frame), ArrayPose.bladeYaw(station, frame));
        assertEquals(ArrayPose.bladePitch(station, frame), ArrayPose.bladePitch(station, frame));

        double[] bearing = ArrayPose.worldBearing(station, frame);
        assertEquals(1.0D, Math.sqrt(bearing[0] * bearing[0] + bearing[1] * bearing[1] + bearing[2] * bearing[2]),
                EXACT, "a rotation does not change a length");
        double[] offset = ArrayPose.worldOffset(station, frame);
        assertEquals(4 * 1.375D, Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]),
                EXACT, "and the offset is reach times scale along it");
    }

    @Test
    void theFrameRotationComposesWithTheStationsOwnBearing() {
        Station ahead = new Station(0, 0, 1, 1);
        near(new double[] {0.0D, 0.0D, 1.0D}, ArrayPose.worldBearing(ahead, held(0.0F, 0.0F)), "yaw 0");
        near(new double[] {-1.0D, 0.0D, 0.0D}, ArrayPose.worldBearing(ahead, held(90.0F, 0.0F)), "yaw 90");
        near(new double[] {0.0D, 0.0D, -1.0D}, ArrayPose.worldBearing(ahead, held(180.0F, 0.0F)), "yaw 180");
        near(new double[] {1.0D, 0.0D, 0.0D}, ArrayPose.worldBearing(ahead, held(270.0F, 0.0F)), "yaw 270");

        // A frame at 90 with a station six steps round is 180 in total, and no other reading of
        // the two conventions gives that.
        near(new double[] {0.0D, 0.0D, -1.0D},
                ArrayPose.worldBearing(new Station(6, 0, 1, 1), held(90.0F, 0.0F)),
                "the frame's yaw and the station's yaw add");
    }

    @Test
    void theFrameTipsWithItsOwnPitchAndMinecraftsPitchIsPositiveDown() {
        Station ahead = new Station(0, 0, 1, 1);
        near(new double[] {0.0D, -HALF_ROOT_TWO, HALF_ROOT_TWO}, ArrayPose.worldBearing(ahead, held(0.0F, 45.0F)),
                "pitch +45 is looking down");
        near(new double[] {0.0D, HALF_ROOT_TWO, HALF_ROOT_TWO}, ArrayPose.worldBearing(ahead, held(0.0F, -45.0F)),
                "and -45 is looking up");

        // A station 36 degrees up on a frame 45 degrees down is 9 degrees down in total.
        double[] out = ArrayPose.worldBearing(new Station(0, 2, 1, 1), held(0.0F, 45.0F));
        near(new double[] {0.0D, Math.sin(Math.toRadians(-9.0D)), Math.cos(Math.toRadians(-9.0D))}, out,
                "the frame's pitch and the station's elevation subtract, because they are opposite signs");
    }

    @Test
    void aBladeWearsTheYawAndPitchOfItsOwnBearing() {
        assertEquals(0.0F, ArrayPose.bladeYaw(new Station(0, 0, 1, 1), held(0.0F, 0.0F)), 1.0E-4F);
        assertEquals(90.0F, ArrayPose.bladeYaw(new Station(6, 0, 1, 1), held(0.0F, 0.0F)), 1.0E-4F);
        assertEquals(90.0F, ArrayPose.bladeYaw(new Station(0, 0, 1, 1), held(90.0F, 0.0F)), 1.0E-4F,
                "a station dead ahead wears the frame's own yaw");
        assertEquals(-90.0F, ArrayPose.bladeYaw(new Station(18, 0, 1, 1), held(0.0F, 0.0F)), 1.0E-4F,
                "+X is -90, which is Minecraft's east");

        assertEquals(0.0F, ArrayPose.bladePitch(new Station(0, 0, 1, 1), held(0.0F, 0.0F)), 1.0E-4F);
        assertEquals(-72.0F, ArrayPose.bladePitch(new Station(0, 4, 1, 1), held(0.0F, 0.0F)), 1.0E-4F,
                "an elevation of +4 is a Minecraft pitch of -72: the sign flips exactly here and nowhere else");
        assertEquals(72.0F, ArrayPose.bladePitch(new Station(0, -4, 1, 1), held(0.0F, 0.0F)), 1.0E-4F);
        assertEquals(45.0F, ArrayPose.bladePitch(new Station(0, 0, 1, 1), held(0.0F, 45.0F)), 1.0E-4F);
    }

    @Test
    void reachAndScaleAreTheOnlyThingsThatMoveTheOffset() {
        Station station = new Station(0, 0, 4, 3);
        near(new double[] {0.0D, 0.0D, 4.0D}, ArrayPose.worldOffset(station, held(0.0F, 0.0F)), "reach 4");
        near(new double[] {0.0D, 0.0D, 8.0D},
                ArrayPose.worldOffset(station, held(0.0F, 0.0F).withScale(2.0F)), "and twice the scale");
        near(new double[] {0.0D, 0.0D, 4.0D},
                ArrayPose.worldOffset(station, held(0.0F, 0.0F).withOrigin(100.0D, -7.5D, 62.0D)),
                "the offset is from the origin, so moving the origin does not move it at all");
    }

    @Test
    void scaleZeroPutsEveryStationOnTheOrigin() {
        Frame collapsed = new Frame(11.0D, 70.0D, -4.0D, 37.0F, -12.0F, 0.0F);
        for (int yaw = 0; yaw < Station.YAW_STEPS; yaw++) {
            for (int pitch = Station.PITCH_MIN; pitch <= Station.PITCH_MAX; pitch++) {
                near(new double[] {0.0D, 0.0D, 0.0D},
                        ArrayPose.worldOffset(new Station(yaw, pitch, Station.REACH_MAX, 4), collapsed),
                        "the whole Array converges on one point, which is the whole of One Blade");
            }
        }
    }

    // ---- the inverse --------------------------------------------------------------------------

    /**
     * The one that matters: every place on the lattice, projected into the world and read back.
     *
     * <p>{@link Station#nearestTo} was written three times independently before it was written
     * once - privately in the rite, publicly on Call the Blade, and nearly a fourth time in the
     * Bearing overlay - and a sign in any copy is the same silent mirror this whole file exists to
     * refuse, except that the rite <em>saves</em> what it quantises. So the round trip is swept
     * rather than sampled: 24 x 9 bearings at six reaches, through seventy-seven frames.
     *
     * <p>The frame yaws include negative ones and ones past 360 on purpose. An entity's yaw is not
     * normalised in Minecraft and never has been, so a modulus that only holds on 0..360 is a
     * mirror lying in wait for the wielder to turn around twice.
     */
    @Test
    void everyStationRoundTripsThroughTheInverseAtEveryFacing() {
        float[] yaws = {-720.0F, -540.0F, -180.0F, -91.5F, -37.5F, 0.0F, 45.0F, 143.0F, 270.0F,
                360.0F, 725.5F};
        float[] pitches = {-90.0F, -45.0F, -22.0F, 0.0F, 17.0F, 45.0F, 90.0F};
        for (float frameYaw : yaws) {
            for (float framePitch : pitches) {
                Frame frame = held(frameYaw, framePitch);
                for (int yaw = 0; yaw < Station.YAW_STEPS; yaw++) {
                    for (int pitch = Station.PITCH_MIN; pitch <= Station.PITCH_MAX; pitch++) {
                        // A different reach on every bearing, so the distance is swept by the
                        // same loop rather than pinned at one value the rounding might like.
                        int reach = Station.REACH_MIN + Math.floorMod(yaw + pitch, Station.REACH_MAX);
                        Station station = new Station(yaw, pitch, reach, 5);
                        double[] offset = ArrayPose.worldOffset(station, frame);
                        assertEquals(station,
                                Station.nearestTo(offset[0], offset[1], offset[2], frame, 5),
                                "yaw " + yaw + " pitch " + pitch + " reach " + reach
                                        + " on a frame at " + frameYaw + "/" + framePitch);
                    }
                }
            }
        }
    }

    @Test
    void theInverseReadsTheNearestStepAndNotTheOneItIsPast() {
        // A yaw step is 15 degrees, so 7 is still the step you are on and 8 is the next one.
        assertEquals(0, atBearing(7.0D, 0.0D, 3.0D).yaw(), "seven degrees is still yaw 0");
        assertEquals(1, atBearing(8.0D, 0.0D, 3.0D).yaw(), "and eight is yaw 1");
        assertEquals(23, atBearing(-8.0D, 0.0D, 3.0D).yaw(), "the ring wraps rather than clamping");

        // A pitch step is 18, so the half is 9.
        assertEquals(0, atBearing(0.0D, 8.0D, 3.0D).pitch(), "eight degrees up is still the plane");
        assertEquals(1, atBearing(0.0D, 10.0D, 3.0D).pitch(), "and ten is one step up");
        assertEquals(-1, atBearing(0.0D, -10.0D, 3.0D).pitch(), "an elevation is signed");

        assertEquals(3, atBearing(0.0D, 0.0D, 3.4D).reach(), "the reach rounds");
        assertEquals(4, atBearing(0.0D, 0.0D, 3.6D).reach());
    }

    @Test
    void theInverseAlwaysAnswersOnTheLattice() {
        // Everything out of range is clamped rather than refused, because a wielder pointing at
        // the sky or at their own feet has still named a bearing - the pole is the nearest place.
        Frame frame = held(37.0F, -12.0F);
        Station straightUp = Station.nearestTo(0.0D, 40.0D, 0.0D, frame, 99);
        assertEquals(Station.PITCH_MAX, straightUp.pitch(), "the pole is the nearest place to the sky");
        assertEquals(Station.REACH_MAX, straightUp.reach(), "and forty blocks is the furthest reach");
        assertEquals(Station.EDGE_MAX, straightUp.edge(), "nonsense metal is clamped, not kept");
        assertTrue(straightUp.onLattice(), "so the answer is always a place");

        Station underfoot = Station.nearestTo(0.0D, -0.01D, 0.0D, frame, -4);
        assertEquals(Station.PITCH_MIN, underfoot.pitch());
        assertEquals(Station.REACH_MIN, underfoot.reach(), "and a hand's breadth is the nearest one");
        assertEquals(Station.EDGE_MIN, underfoot.edge());
        assertTrue(underfoot.onLattice());
    }

    @Test
    void anOffsetWithNoDirectionInItIsNotAStation() {
        assertNull(Station.nearestTo(0.0D, 0.0D, 0.0D, held(0.0F, 0.0F), 2),
                "a wielder aiming at their own eyes has not named a bearing");
        assertNull(Station.nearestTo(1.0E-9D, -1.0E-9D, 0.0D, held(90.0F, 30.0F), 2),
                "and neither has one aiming a millionth of a block out");
    }

    /** A station read back out of a bearing given in degrees off the frame's own facing. */
    private static Station atBearing(double degreesRound, double degreesUp, double distance) {
        double bearing = Math.toRadians(degreesRound);
        double elevation = Math.toRadians(degreesUp);
        double flat = Math.cos(elevation) * distance;
        // The frame is the identity here, so a frame-local bearing is a world one: this is
        // unitBearing's own arithmetic at an angle that is not on the lattice.
        return Station.nearestTo(-Math.sin(bearing) * flat, Math.sin(elevation) * distance,
                Math.cos(bearing) * flat, held(0.0F, 0.0F), 1);
    }

    @Test
    void boundScaleIsOneInsideEightBlocks() {
        assertEquals(8.0D, ArrayPose.BIND_REST, EXACT);
        assertEquals(1.0F, ArrayPose.boundScale(0.0D), 1.0E-6F);
        assertEquals(1.0F, ArrayPose.boundScale(4.0D), 1.0E-6F, "a bind is a leash, not a penalty");
        assertEquals(1.0F, ArrayPose.boundScale(8.0D), 1.0E-6F, "and eight blocks is still free");
        assertEquals(1.5F, ArrayPose.boundScale(12.0D), 1.0E-6F);
        assertEquals(2.0F, ArrayPose.boundScale(16.0D), 1.0E-6F);
        assertEquals(5.0F, ArrayPose.boundScale(40.0D), 1.0E-6F, "past the 40-block release, for completeness");
        assertEquals(1.0F, ArrayPose.boundScale(-3.0D), 1.0E-6F, "and a nonsense distance never shrinks a bill");
    }
}
