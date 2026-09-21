package com.efkrdnz.magical.magic.sword;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;

/**
 * The wielder half of the Sword Summoner: the bearings they authored and the Edge on each.
 *
 * <p>This is the only part of the class they actually own and the only part that is saved. The
 * blades in the air, the frame, the spent metal lying in the world and every recovery clock are
 * held by the service and dropped on logout, the way {@code PileService} drops its Piles. What
 * survives is the shape.
 *
 * <p>Three rules do all the balancing and all three are arithmetic, so all three are pinned on
 * exact values by {@code SwordArrayTest} and {@code SettleTest} with no world under them:
 *
 * <ul>
 *   <li><b>Separation.</b> Two blades may not share a bearing: {@link Station#separationFrom} is at
 *       least {@link #SEPARATION_MIN} between any two stations, at every reach, unless the rung
 *       grants coincidence. Two yaw steps is 30 degrees and {@code 360 / 30 = 12}, so
 *       {@link #MAX_STATIONS} and {@link #SEPARATION_MIN} <em>are the same fact</em>: twelve
 *       stations is exactly one full ring and is the densest legal packing there is.
 *   <li><b>Conservation.</b> {@code bound() + spent + loose == whole}, always, and loose is never
 *       negative. Nothing here creates Edge and nothing destroys it - it is bound into a station,
 *       spent and lying in the world, or loose in the wielder, and those three are all the places
 *       there are.
 *   <li><b>The bill.</b> {@code sum of reach * edge <= draw}, enforced by {@link #plant} - which
 *       answers {@link PlantResult#TOO_DEAR} by name - and again by {@link #load}, so a
 *       hand-edited save cannot hold a shape a hand could not have written.
 * </ul>
 *
 * <p>Pure but for {@link IntArrayTag}, which it needs in order to live on {@code PlayerMagicState},
 * exactly as {@code Fracture} and {@code Grimoire} already do. Nothing here knows what a level is.
 */
public final class SwordArray {

    /**
     * One full ring at the minimum separation, and the hard ceiling whatever the rung says.
     *
     * <p>{@code 360 / (SEPARATION_MIN * 15) == 12}. These two constants are one fact stated twice
     * and {@code SwordArrayTest.twelveIsExactlyAFullRing} says so out loud, because someone
     * raising one without the other would produce a cap that is either unreachable or a lie.
     */
    public static final int MAX_STATIONS = 12;

    /** Yaw-or-pitch steps two bearings must differ by. Two steps of yaw is 30 degrees. */
    public static final int SEPARATION_MIN = 2;

    /**
     * Rides at index 0 of the saved int array.
     *
     * <p>Nothing in the saved half is an enum, so nothing in it can be renumbered by a future
     * edit - which is a stronger guarantee than {@code Weave}'s name-writing rule and is why the
     * packing arithmetic gets a test of its own instead. The version is here anyway, because the
     * packing itself is the thing that could change.
     */
    public static final int SAVE_VERSION = 1;

    private static final int[] NO_SLOTS = new int[0];

    /** Insertion order, and insertion order is load-bearing: it is the tie-break in a shed. */
    private final List<Station> stations = new ArrayList<>();

    private SwordRules rules = SwordRules.SUMMONER;

    /** The base rung is the floor, so an Array read off a wielder who has no class is still legal. */
    public SwordArray() {
    }

    // ---- reading ------------------------------------------------------------------------------

    public SwordRules rules() {
        return rules;
    }

    /** 8 / 16 / 26 / 36, by the rung. The one number conservation is stated against. */
    public int whole() {
        return rules.whole();
    }

    public int size() {
        return stations.size();
    }

    public boolean isEmpty() {
        return stations.isEmpty();
    }

    /** Null out of range. Callers walk {@code 0 .. size()}; there is no clamping an index here. */
    public Station station(int index) {
        return index >= 0 && index < stations.size() ? stations.get(index) : null;
    }

    public List<Station> stations() {
        return List.copyOf(stations);
    }

    /** Stations with metal on them. An emptied bearing still counts against the station cap. */
    public int manned() {
        int count = 0;
        for (Station station : stations) {
            if (station.manned()) {
                count++;
            }
        }
        return count;
    }

    /** The build constraint: how much shape the wielder is carrying, before any scale. */
    public int bill() {
        int total = 0;
        for (Station station : stations) {
            total += station.weight();
        }
        return total;
    }

    /** Metal in the air. */
    public int bound() {
        int total = 0;
        for (Station station : stations) {
            total += station.edge();
        }
        return total;
    }

    /**
     * What is left in the wielder, given what is lying out in the world.
     *
     * <p>{@code spent} is not saved and not held here - it belongs to the service, with the blades
     * it describes - so it is a parameter rather than a field. That is also what keeps this class
     * checkable with no world at all.
     */
    public int loose(int spent) {
        return whole() - bound() - Math.max(0, spent);
    }

    /** The bill the frame actually presents at this scale. Scale is 1 for every bind but BOUND. */
    public int billAt(double scale) {
        return (int) Math.ceil(bill() * Math.max(0.0D, scale));
    }

    /**
     * {@code max(0, ceil(bill * scale) - draw)}, and the only number in the structure the wielder
     * does not own: from Sword Saint, a bound opponent running away inflates it.
     */
    public int strainAt(double scale) {
        return Math.max(0, billAt(scale) - rules.draw());
    }

    // ---- writing ------------------------------------------------------------------------------

    /**
     * Raises or lowers the rung, and puts the shape back through the new rung's rules.
     *
     * <p>A rung only ever goes up in play, but a reset and a fresh login both come through here,
     * and a shape that the new rules cannot hold has to fall off rather than sit there illegal.
     * It is the same private path {@link #load} uses, for the same reason.
     */
    public void setRules(SwordRules next) {
        if (next == null) {
            return;
        }
        rules = next;
        List<Station> held = new ArrayList<>(stations);
        stations.clear();
        for (Station station : held) {
            write(station, 0, true);
        }
    }

    /**
     * Call the Blade. Writes a bearing, or puts more metal on one already written.
     *
     * <p>The amount is <em>clamped</em> to what the rung's {@code maxEdge} and the wielder's loose
     * Edge allow, and refused only when that lands at zero: a cap is a cap and there is no
     * PlantResult for hitting one. The bill is the other way about - it is refused by name,
     * because {@link PlantResult#TOO_DEAR} is a thing the wielder fixes by aiming nearer.
     */
    public PlantResult plant(Station station, int spent) {
        return write(station, spent, false);
    }

    /**
     * The Bearing's unwrite: the station goes, and its Edge is loose again.
     *
     * <p>This is the only thing in the kit that takes a bearing off the Array. Ward and the shed
     * empty a station; only a pull removes it, which is why the Bearing can dismantle a build and
     * never make one.
     */
    public boolean pull(int index) {
        if (index < 0 || index >= stations.size()) {
            return false;
        }
        stations.remove(index);
        return true;
    }

    /**
     * Takes metal off a station without unwriting it, and answers how much it actually got.
     *
     * <p>Ward's interception and a blade leaving on a Loose both come through here. The bearing
     * stays authored: the shape survives, the metal does not.
     */
    public int spend(int index, int amount) {
        if (index < 0 || index >= stations.size() || amount <= 0) {
            return 0;
        }
        Station held = stations.get(index);
        int taken = Math.min(amount, held.edge());
        if (taken <= 0) {
            return 0;
        }
        stations.set(index, held.withEdge(held.edge() - taken));
        return taken;
    }

    public void clear() {
        stations.clear();
    }

    public void copyFrom(SwordArray other) {
        if (other == null || other == this) {
            return;
        }
        rules = other.rules;
        stations.clear();
        stations.addAll(other.stations);
    }

    // ---- the enforcement pass -----------------------------------------------------------------

    /**
     * The settle: what an over-stretched Array does about it.
     *
     * <p>Without overdraw, while there is any strain it takes the manned station with the largest
     * {@code reach * edge} - <b>ties broken by lowest slot index, never by map order</b> - and
     * sheds it: the blade cuts the line home and its Edge comes back. Repeat until the bill fits.
     * With overdraw nothing sheds and the strain stands, until {@code strain >= draw}, at which
     * point every station lets go in one tick and it is not the wielder's decision.
     *
     * <p><b>Why the strain is recomputed rather than decremented.</b> The caller hands us one int
     * because the adapter computed it from one scalar, so the inflated bill is {@code strain +
     * draw} and the scale it implies is that over the bill we started with. A shed changes the
     * bill and not the scale, so the strain after one is that same ratio applied to the new bill.
     * Subtracting the station's raw {@code reach * edge} would be the bill at scale 1, which is
     * exactly the number that is not true whenever there is any strain at all - and it would shed
     * more blades than the overrun called for, every time.
     */
    public Settlement settle(int strain, boolean overdraw) {
        if (strain <= 0) {
            return Settlement.quiet(0);
        }
        int draw = rules.draw();
        if (overdraw) {
            // Sword God. Under the draw the strain is simply carried - that is the rung. At it,
            // everything goes at once: twelve converging cuts, no cooldown, not your decision.
            return strain < draw ? Settlement.quiet(strain) : new Settlement(unmanEverything(), 0);
        }

        int billStart = bill();
        if (billStart <= 0) {
            return Settlement.quiet(0);
        }
        long inflatedStart = (long) strain + draw;

        List<Integer> shed = new ArrayList<>();
        int left = strain;
        while (left > 0) {
            int slot = heaviestMannedSlot();
            if (slot < 0) {
                break;
            }
            stations.set(slot, stations.get(slot).withEdge(0));
            shed.add(slot);
            long inflated = ceilDiv((long) bill() * inflatedStart, billStart);
            left = (int) Math.max(0L, inflated - draw);
        }
        return new Settlement(toSlots(shed), left);
    }

    /**
     * One Blade, and it is not a second mechanism.
     *
     * <p>The fusion drives {@code frame.scale} to zero, which drives the bill to zero, which is
     * reached by exactly one route in this class: every station letting go. So the collapse is
     * {@link #settle} run at the far end of its own range - the overdraw branch at the draw, which
     * is the branch that already sheds everything at once - rather than a separate unmanning with
     * its own arithmetic to get wrong. The overdraw flag is passed by hand and not read off the
     * rung, because a collapse is not an overrun: it is the wielder choosing to bring the whole
     * Array home, and Sword Saint may do it without owning Sword God's rule.
     */
    public Settlement fuse() {
        return settle(rules.draw(), true);
    }

    // ---- persistence --------------------------------------------------------------------------

    /** Index 0 is the version, then one packed station each. No ordinals, ever. */
    public IntArrayTag save() {
        int[] data = new int[stations.size() + 1];
        data[0] = SAVE_VERSION;
        for (int i = 0; i < stations.size(); i++) {
            data[i + 1] = stations.get(i).packed();
        }
        return new IntArrayTag(data);
    }

    /**
     * Total: no input throws, everything is clamped, and every station is put back through the
     * same rules a hand placement goes through - so a hand-edited save cannot hold a shape that
     * overruns the draw, shares a bearing or exceeds the rung's station count.
     *
     * <p>Unmanned bearings are admitted where {@link #plant} refuses them, because a wielder who
     * logs out with a station Ward emptied must find their shape where they left it.
     *
     * <p>A version this build does not know is dropped whole rather than guessed at. A mis-read
     * packing is twelve swords in the wrong places with no way to tell; an empty Array is one
     * Call the Blade away from being right again.
     */
    public void load(IntArrayTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        int[] data = tag.getAsIntArray();
        if (data.length < 1 || data[0] != SAVE_VERSION) {
            return;
        }
        for (int i = 1; i < data.length; i++) {
            write(Station.unpack(data[i]), 0, true);
        }
    }

    public static int tagType() {
        return Tag.TAG_INT_ARRAY;
    }

    // ---- the rules, in one place ---------------------------------------------------------------

    /**
     * Every rule a bearing has to pass, whether it came off a crosshair or off the disk.
     *
     * <p>{@code allowUnmanned} is the one difference between the two callers and it is a single
     * bit for a single reason, stated in {@link #load}.
     */
    private PlantResult write(Station station, int spent, boolean allowUnmanned) {
        if (station == null || !station.onLattice()) {
            return PlantResult.OUT_OF_REACH;
        }

        int slot = bearingSlot(station);
        if (slot >= 0) {
            return topUp(slot, station.edge(), spent);
        }
        if (stations.size() >= stationCap()) {
            return PlantResult.FULL;
        }
        if (!rules.coincidence()) {
            for (Station other : stations) {
                if (station.separationFrom(other) < SEPARATION_MIN) {
                    return PlantResult.TOO_CLOSE;
                }
            }
        }

        int give = affordableEdge(station.edge(), rules.maxEdge(), spent);
        if (give < 1 && !allowUnmanned) {
            return PlantResult.NO_EDGE;
        }
        if (bill() + station.reach() * give > rules.draw()) {
            return PlantResult.TOO_DEAR;
        }
        stations.add(station.withEdge(give));
        return PlantResult.PLANTED;
    }

    /**
     * A bearing already written takes more metal rather than refusing, which is how a station that
     * Ward or a Loose emptied gets re-manned - and the reach stays as it was authored, because the
     * bearing is the build and a top-up is not a re-authoring.
     */
    private PlantResult topUp(int slot, int wanted, int spent) {
        Station held = stations.get(slot);
        int give = affordableEdge(wanted, rules.maxEdge() - held.edge(), spent);
        if (give < 1) {
            return PlantResult.NO_EDGE;
        }
        if (bill() + held.reach() * give > rules.draw()) {
            return PlantResult.TOO_DEAR;
        }
        stations.set(slot, held.withEdge(held.edge() + give));
        return PlantResult.TOPPED_UP;
    }

    private int affordableEdge(int wanted, int room, int spent) {
        return Math.max(0, Math.min(Math.min(wanted, room), loose(spent)));
    }

    /** The rung's cap, under the structure's own. Twelve is a full ring and there is no thirteenth. */
    private int stationCap() {
        return Math.min(MAX_STATIONS, Math.max(0, rules.maxStations()));
    }

    /** The slot holding this yaw and pitch, or -1. Reach does not make a second bearing. */
    private int bearingSlot(Station station) {
        for (int i = 0; i < stations.size(); i++) {
            Station held = stations.get(i);
            if (held.yaw() == station.yaw() && held.pitch() == station.pitch()) {
                return i;
            }
        }
        return -1;
    }

    /** Largest {@code reach * edge}, ties to the lowest slot - the ascending walk and a strict >. */
    private int heaviestMannedSlot() {
        int best = -1;
        int heaviest = 0;
        for (int i = 0; i < stations.size(); i++) {
            int weight = stations.get(i).weight();
            if (weight > heaviest) {
                heaviest = weight;
                best = i;
            }
        }
        return best;
    }

    private int[] unmanEverything() {
        List<Integer> shed = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            Station held = stations.get(i);
            if (!held.manned()) {
                continue;
            }
            stations.set(i, held.withEdge(0));
            shed.add(i);
        }
        return toSlots(shed);
    }

    private static int[] toSlots(List<Integer> slots) {
        if (slots.isEmpty()) {
            return NO_SLOTS;
        }
        int[] out = new int[slots.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = slots.get(i);
        }
        return out;
    }

    /** Both arguments are non-negative here, so the cheap form is the correct one. */
    private static long ceilDiv(long value, long divisor) {
        return (value + divisor - 1L) / divisor;
    }
}
