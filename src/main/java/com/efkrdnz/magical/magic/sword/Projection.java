package com.efkrdnz.magical.magic.sword;

import java.util.ArrayList;
import java.util.List;

/**
 * Four ways of looking at a shape that never changes, and this is why the kit is six verbs rather
 * than five skills.
 *
 * <p>Strip the word <em>sword</em> and count what falls out of the object rather than being priced
 * by it: take the forward half, take the lower half, ask which bearing covers an incoming line,
 * reflect the whole thing. None of these writes anything, none of them takes a cap, and the
 * projection <em>is</em> the cap - a wielder who planted a forward cone Looses all of it, a wielder
 * who planted a full ring Looses exactly half, always, and nobody had to pick a number.
 *
 * <p>Manned stations only, throughout. An emptied bearing is a place the wielder still owns and
 * has no metal at; it fires nothing, guards nothing and casts no reflection.
 */
public final class Projection {

    /** Degrees either side of a manned bearing that it is judged to be guarding. */
    public static final double WARD_CONE = 30.0D;

    private static final double COVER_COS = Math.cos(Math.toRadians(WARD_CONE));

    /**
     * The hair of slack on {@code dot > 0}.
     *
     * <p>{@code Math.cos(Math.PI / 2)} is {@code 6.1e-17} in doubles and not zero, so a station
     * exactly abeam would otherwise be counted as facing forward on a rounding artefact - and it
     * is not an exotic case, it is what a twelve-station ring puts two blades on every time.
     */
    private static final double FACING = 1.0E-6D;

    private static final int[] NOTHING = new int[0];

    private Projection() {
    }

    /**
     * Loose: every manned station facing the way the wielder is looking, nearest the look first.
     *
     * <p>Descending by the dot product, ties to the lower slot. No cap, deliberately - see the
     * class note.
     */
    public static int[] forward(SwordArray array, Frame frame, double[] look) {
        double[] aim = normalised(look);
        if (aim == null) {
            return NOTHING;
        }
        List<Integer> slots = new ArrayList<>();
        List<Double> dots = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Station station = array.station(i);
            if (!station.manned()) {
                continue;
            }
            double[] bearing = ArrayPose.worldBearing(station, frame);
            double dot = bearing[0] * aim[0] + bearing[1] * aim[1] + bearing[2] * aim[2];
            if (dot <= FACING) {
                continue;
            }
            // Insertion sort, because twelve is the ceiling and a stable comparator over boxed
            // indices would be more machinery than the whole method is worth.
            int at = 0;
            while (at < dots.size() && dots.get(at) >= dot) {
                at++;
            }
            slots.add(at, i);
            dots.add(at, dot);
        }
        return toSlots(slots);
    }

    /**
     * Below: every manned station written under the frame plane, deepest first.
     *
     * <p>No frame, because the reflection Below applies is a property of the authored pitch and
     * not of where the frame happens to be pointing - the eruption's strength is exactly how much
     * of the shape the wielder aimed at the floor, which is precisely the part of it that is not
     * covering their flanks.
     */
    public static int[] below(SwordArray array) {
        List<Integer> slots = new ArrayList<>();
        for (int pitch = Station.PITCH_MIN; pitch < 0; pitch++) {
            for (int i = 0; i < array.size(); i++) {
                Station station = array.station(i);
                if (station.manned() && station.pitch() == pitch) {
                    slots.add(i);
                }
            }
        }
        return toSlots(slots);
    }

    /**
     * Ward: the manned station guarding the line a blow is coming in on, or -1.
     *
     * <p>{@code incoming} is the direction <b>from the frame origin toward where the blow came
     * from</b>, so a station at yaw 0 guards what arrives out of +Z. That sign is the whole
     * behaviour of the passive and reversing it would turn the guard into an exact anti-guard
     * with no symptom but losing fights, so it is written here and pinned in {@code ProjectionTest}.
     *
     * <p>When several cover it, the closest one answers: a shape is the choice, and the blade
     * pointing most nearly at the blow is the one that meets it.
     */
    public static int covers(SwordArray array, Frame frame, double[] incoming) {
        double[] from = normalised(incoming);
        if (from == null) {
            return -1;
        }
        int best = -1;
        double closest = -2.0D;
        for (int i = 0; i < array.size(); i++) {
            Station station = array.station(i);
            if (!station.manned()) {
                continue;
            }
            double[] bearing = ArrayPose.worldBearing(station, frame);
            double dot = bearing[0] * from[0] + bearing[1] * from[1] + bearing[2] * from[2];
            if (dot < COVER_COS) {
                continue;
            }
            // Strictly greater, so a tie goes to the lower slot the way a shed's does.
            if (dot > closest) {
                closest = dot;
                best = i;
            }
        }
        return best;
    }

    /**
     * Mirror of the Array: a twin at every manned bearing's antipode.
     *
     * <p>{@code yaw + 12}, pitch negated, reach unchanged, Edge halved with a floor of one - and
     * <b>it costs no Edge and no bill, because it is a reflection and not metal</b>. A twin that
     * lands within {@link SwordArray#SEPARATION_MIN} of a bearing the wielder actually wrote is
     * dropped, so the apex cannot quietly double a station the wielder already paid for.
     *
     * <p>Read by {@code below()} and {@code covers()} and <b>never by {@code forward()} or the
     * fusion</b>: a reflection defends and comes up from underneath; it does not fly and it
     * carries nothing into a greatsword. That rule is the only thing stopping the apex being a
     * free doubling of the kit's damage.
     */
    public static List<Station> mirror(SwordArray array) {
        List<Station> twins = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            Station station = array.station(i);
            if (!station.manned()) {
                continue;
            }
            Station twin = new Station(
                    (station.yaw() + Station.YAW_STEPS / 2) % Station.YAW_STEPS,
                    -station.pitch(),
                    station.reach(),
                    Math.max(1, station.edge() / 2));
            if (!shadowed(array, twin)) {
                twins.add(twin);
            }
        }
        return List.copyOf(twins);
    }

    private static boolean shadowed(SwordArray array, Station twin) {
        for (int i = 0; i < array.size(); i++) {
            if (twin.separationFrom(array.station(i)) < SwordArray.SEPARATION_MIN) {
                return true;
            }
        }
        return false;
    }

    /** Null for anything that is not a direction, so a caller cannot divide by a zero vector. */
    private static double[] normalised(double[] vector) {
        if (vector == null || vector.length < 3) {
            return null;
        }
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (!(length > 1.0E-9D)) {
            return null;
        }
        return new double[] {vector[0] / length, vector[1] / length, vector[2] / length};
    }

    private static int[] toSlots(List<Integer> slots) {
        if (slots.isEmpty()) {
            return NOTHING;
        }
        int[] out = new int[slots.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = slots.get(i);
        }
        return out;
    }
}
