package com.efkrdnz.magical.magic.sword.stance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every formation, at every count, measured rather than reasoned about.
 *
 * <p>This school has already shipped one green suite that certified something invisible - four
 * tests agreed a subspace wall was fine by measuring the mean alpha over its projected disc, which
 * is a property of a viewer standing outside a dome that discards itself when its owner leaves it.
 * So none of the rules below are about the arithmetic being self-consistent. They are about
 * <b>the view the player actually has</b>: nothing inside the camera, nothing over the crosshair,
 * nothing inside anything else, and six stances that genuinely look like six stances.
 *
 * <p>The sweep is every stance x every count 1..12 x a spread of phases including several that
 * are not multiples of anything, because three of the six carry a per-blade wave and a wave
 * sampled only at zero is a wave that was never tested.
 */
class FormationTest {

    /** Every count a rung can produce, and every count below it as swords go away. */
    private static final int MAX_COUNT = 12;

    /** Deliberately not round: a phase of 0 hides every per-blade wave in the package. */
    private static final double[] PHASES = {0.0D, 1.0D, 7.3D, 19.0D, 41.7D, 100.0D, 617.4D, 4321.9D};

    @Test
    void everyBladePointsSomewhere() {
        forEachSlot((stance, count, phase, i, slot) -> {
            double length = Math.sqrt(slot.dx() * slot.dx() + slot.dy() * slot.dy()
                    + slot.dz() * slot.dz());
            assertEquals(1.0D, length, 1.0E-9D, where(stance, count, phase, i)
                    + " has a direction of length " + length + "; SwordBladeRenderer builds a"
                    + " rotation from it, so anything but a unit vector is a blade drawn at the"
                    + " wrong angle or at no angle at all");
        });
    }

    @Test
    void noTwoBladesOccupyOneAnother() {
        for (SwordStance stance : SwordStance.values()) {
            for (int count = 2; count <= MAX_COUNT; count++) {
                for (double phase : PHASES) {
                    List<Slot> slots = formation(stance, count, phase);
                    for (int a = 0; a < slots.size(); a++) {
                        for (int b = a + 1; b < slots.size(); b++) {
                            double gap = slots.get(a).gapTo(slots.get(b));
                            assertTrue(gap >= Formation.MIN_SEPARATION,
                                    stance + " at count " + count + " phase " + phase
                                            + " puts blades " + a + " and " + b + " only " + gap
                                            + " apart, under MIN_SEPARATION "
                                            + Formation.MIN_SEPARATION + " - two swords that close"
                                            + " read on screen as one object with a rendering bug");
                        }
                    }
                }
            }
        }
    }

    @Test
    void nothingIsInsideTheWielder() {
        forEachSlot((stance, count, phase, i, slot) -> assertTrue(
                slot.distance() >= Formation.BODY_CLEARANCE,
                where(stance, count, phase, i) + " sits " + slot.distance()
                        + " from the frame origin, inside BODY_CLEARANCE "
                        + Formation.BODY_CLEARANCE + " - that is in the wielder's chest"));
    }

    @Test
    void nothingIsInsideTheCamera() {
        forEachSlot((stance, count, phase, i, slot) -> {
            double gap = distanceFromEye(stance, slot);
            assertTrue(gap >= Formation.EYE_CLEARANCE, where(stance, count, phase, i)
                    + " sits " + gap + " from the eye, inside EYE_CLEARANCE "
                    + Formation.EYE_CLEARANCE + " - in first person that is a blade filling the"
                    + " whole frame and clipping through the near plane");
        });
    }

    /**
     * The rule the old kit broke worst: its cast circle was 73 degrees wide on a 70 degree screen.
     *
     * <p>Measured from the eye and with the look level, which is the resting case - a BODY stance
     * is <em>supposed</em> to come into view when the wielder tilts their head up at their own
     * Crown, and refusing that would be refusing the stance. What must never happen is a blade
     * parked over the crosshair while the wielder is looking straight ahead, and Vanguard is the
     * only stance with anything in front at all, so it is the one this is really about.
     */
    @Test
    void nothingStandsOnTheCrosshair() {
        double cos = Math.cos(Math.toRadians(Formation.CROSSHAIR_CONE));
        forEachSlot((stance, count, phase, i, slot) -> {
            double eye = eyeHeight(stance);
            double x = slot.x();
            double y = slot.y() - eye;
            double z = slot.z();
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length > Formation.CROSSHAIR_RANGE || length < 1.0E-9D) {
                return;
            }
            double alignment = z / length;
            assertTrue(alignment <= cos, where(stance, count, phase, i) + " is "
                    + Math.toDegrees(Math.acos(alignment)) + " degrees off the aim at " + length
                    + " blocks, inside the " + Formation.CROSSHAIR_CONE + " degree cone that stays"
                    + " clear out to " + Formation.CROSSHAIR_RANGE + " - a sword on the crosshair"
                    + " is the most obstructive thing a first-person formation can do");
        });
    }

    @Test
    void noFormationReachesFurtherThanTheRendererClaims() {
        forEachSlot((stance, count, phase, i, slot) -> assertTrue(
                slot.distance() <= Formation.MAX_EXTENT,
                where(stance, count, phase, i) + " reaches " + slot.distance()
                        + ", past MAX_EXTENT " + Formation.MAX_EXTENT
                        + " - the cull box is sized off that constant, so this blade vanishes"
                        + " whenever the wielder's own bounding box leaves the frustum"));
    }

    @Test
    void placeIsAFunctionOfItsArgumentsAndNothingElse() {
        for (SwordStance stance : SwordStance.values()) {
            for (int count = 1; count <= MAX_COUNT; count++) {
                for (double phase : PHASES) {
                    for (int i = 0; i < count; i++) {
                        assertEquals(Formation.place(stance, i, count, phase),
                                Formation.place(stance, i, count, phase),
                                where(stance, count, phase, i) + " answered differently twice."
                                        + " Both sides of the wire run this function and are told"
                                        + " only the stance, the mask and the frame, so anything"
                                        + " it reads that is not an argument is a desync");
                    }
                }
            }
        }
    }

    /** Out of range clamps rather than throwing, because a mask off the wire is not trusted. */
    @Test
    void anIndexOutOfRangeIsClampedAndNotThrown() {
        for (SwordStance stance : SwordStance.values()) {
            assertEquals(Formation.place(stance, 0, 4, 0.0D), Formation.place(stance, -3, 4, 0.0D),
                    stance + " did not clamp a negative index to the first blade");
            assertEquals(Formation.place(stance, 3, 4, 0.0D), Formation.place(stance, 99, 4, 0.0D),
                    stance + " did not clamp an index past the count to the last blade");
        }
    }

    /**
     * Six formations that look alike would be one formation in six coats, which is the exact
     * failure the whole redesign exists to prevent and the one thing no other test here would
     * catch - every rule above passes just as happily on six copies of the same ring.
     *
     * <p>Mean per-index displacement rather than a Hausdorff distance, because the question a
     * wielder actually asks is "if I switch stance, do my swords move?", and that is answered
     * blade by blade.
     */
    @Test
    void everyStanceLooksDifferentFromEveryOtherStance() {
        int count = 8;
        double floor = 0.9D;
        SwordStance[] all = SwordStance.values();
        for (int a = 0; a < all.length; a++) {
            for (int b = a + 1; b < all.length; b++) {
                double total = 0.0D;
                for (int i = 0; i < count; i++) {
                    total += Formation.place(all[a], i, count, 0.0D)
                            .gapTo(Formation.place(all[b], i, count, 0.0D));
                }
                double mean = total / count;
                assertTrue(mean >= floor, all[a] + " and " + all[b] + " move a blade only " + mean
                        + " on average when the wielder switches between them, under " + floor
                        + " - two stances that close are one stance with two names");
            }
        }
    }

    /** A stance that does not move is a stance that reads as a decal bolted to the player. */
    @Test
    void everyFormationIsAlive() {
        for (SwordStance stance : SwordStance.values()) {
            double moved = 0.0D;
            for (int i = 0; i < 6; i++) {
                moved = Math.max(moved, Formation.place(stance, i, 6, 0.0D)
                        .gapTo(Formation.place(stance, i, 6, 30.0D)));
            }
            assertTrue(moved > 0.02D, stance + " does not move at all over thirty ticks - every"
                    + " formation carries either a spin, a drift or a bob, and one that carries"
                    + " none is a decal");
        }
    }

    /** The one field that must not be read by a stance whose blades do not point at the look. */
    @Test
    void onlyALookFacingStanceCaresWhereTheWielderIsLooking() {
        for (SwordStance stance : SwordStance.values()) {
            Slot level = Formation.place(stance, 0, 6, 0.0D, 0.0D);
            Slot up = Formation.place(stance, 0, 6, 0.0D, 55.0D);
            boolean moved = Math.abs(level.dy() - up.dy()) > 1.0E-9D;
            assertEquals(stance.facing() == SwordStance.Facing.LOOK, moved,
                    stance + " facing " + stance.facing() + " answered "
                            + (moved ? "a different" : "the same")
                            + " direction when the wielder looked up, which is backwards:"
                            + " only Facing.LOOK reads the elevation");
        }
    }

    // ---- the sweep -------------------------------------------------------------------------

    private interface SlotCheck {
        void check(SwordStance stance, int count, double phase, int index, Slot slot);
    }

    private static void forEachSlot(SlotCheck check) {
        for (SwordStance stance : SwordStance.values()) {
            for (int count = 1; count <= MAX_COUNT; count++) {
                for (double phase : PHASES) {
                    for (int i = 0; i < count; i++) {
                        check.check(stance, count, phase, i,
                                Formation.place(stance, i, count, phase));
                    }
                }
            }
        }
    }

    private static List<Slot> formation(SwordStance stance, int count, double phase) {
        List<Slot> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots.add(Formation.place(stance, i, count, phase));
        }
        return slots;
    }

    /**
     * Zero for a LOOK-anchored frame, which is pinned to the eye, and 0.72 for a BODY one, which
     * is pinned to the chest. Getting this backwards is how a Vanguard ring comes to look centred
     * on the crosshair in a test and sit a head-height below it in the game.
     */
    private static double eyeHeight(SwordStance stance) {
        return stance.anchor() == SwordStance.Anchor.LOOK ? 0.0D : Formation.EYE_HEIGHT;
    }

    private static double distanceFromEye(SwordStance stance, Slot slot) {
        double y = slot.y() - eyeHeight(stance);
        return Math.sqrt(slot.x() * slot.x() + y * y + slot.z() * slot.z());
    }

    private static String where(SwordStance stance, int count, double phase, int index) {
        return stance + " blade " + index + " of " + count + " at phase " + phase;
    }
}
