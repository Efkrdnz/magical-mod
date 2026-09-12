package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.*;

/** The renderer and server share these paths, so what you see is exactly what can touch you. */
public final class UnwakingAssaultGeometry {
    private UnwakingAssaultGeometry() {}
    /** One kind, since the folded lance was retired along with the five-abreast volleys. */
    public static boolean projectile(Kind kind) { return kind==Kind.RETURNING_VERDICT; }
    /**
     * Kinds thrown several at a time, so one contact settles the whole group.
     *
     * <p>This is what stops a four-body volley charging four impact cues and four separate damage
     * events for what the player experiences as one attack.
     */
    public static boolean volley(Kind kind) {
        return projectile(kind) || kind==Kind.VORTEX_BEAM || kind==Kind.SIXFOLD_BURIAL
                || kind==Kind.FIRMAMENT_FRAGMENT;
    }
    public static boolean moving(Kind kind) { return volley(kind) || UnwakingSkyGeometry.attack(kind); }
    public static final double BEAM_RADIUS = 2.75;
    /**
     * The verdict's body. It used to be a 1.25-block splinter fired five at a time, which is why it
     * read as a flicker rather than a thing; alone in the Procession it is sized to be seen, and
     * drawn at the same radius the server collides with.
     */
    public static final double VERDICT_RADIUS = 5;
    /** Nine waves rather than seven: same window, tighter spacing, never more than two in the air. */
    public static boolean showerWave(int age) { return age>=40&&age<=232&&(age-40)%SHOWER_PERIOD==0; }
    public static final int SHOWER_PERIOD = 24;
    public static Vec3 showerOffset(int wave,int lane,boolean closing) {
        if(lane==0) return Vec3.ZERO;
        double angle=(lane-1)*Math.PI/3+wave*Math.PI/6;
        double radius=closing&&wave%2==1?10:14;
        return new Vec3(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
    }
    /** Out for thirty-two ticks, a twelve-tick hold at the far end, then the same line back. */
    /** Spacing between neighbouring bodies in a rain volley. Ten of body, six of gap. */
    public static final double RAIN_SPREAD = 16;
    /**
     * Where one body of a rain volley aims, relative to the volley's predicted target point.
     *
     * <p>A line through the target rather than a ring around it. A ring leaves the middle open, and
     * standing still is not a dodge. The line is horizontal and square to the approach, because
     * that is the plane a player can actually move in.
     *
     * <p>Alternate volleys shift the whole line by half a spacing, which puts a body exactly where
     * the last volley left a gap. One safe spot is never the answer twice running - that is the
     * entire difficulty of the passage, and it is this half-step that creates it.
     */
    public static Vec3 rainOffset(int volley, int index, int count, Vec3 axis) {
        if(count<=1) return Vec3.ZERO;
        Vec3 right=unit(unit(axis).cross(new Vec3(0,1,0)));
        return right.scale((index-(count-1)/2.0)*RAIN_SPREAD+(volley&1)*RAIN_SPREAD/2);
    }
    /**
     * How many eyes hang on the dome during the Eyes passage.
     *
     * <p>Shared because the client must draw the eye the server is about to fire from. Nothing about
     * an eye travels on the wire - both sides derive the whole ring from the passage age and this
     * count, the same way the shower's lanes are derived rather than sent.
     */
    public static final int EYE_COUNT = 10;
    /** The unit bearing of one eye, measured from the player's own facing when the passage opened. */
    public static Vec3 eyeBearing(int index, Vec3 forward) {
        int i=Math.floorMod(index,EYE_COUNT);
        // Three pitches rather than one, so the ring is a dome with depth and not a flat necklace.
        double pitch=Math.toRadians(i%3==0?44:i%3==1?24:9);
        Vec3 flat=rotate(unit(new Vec3(forward.x,0,forward.z)),new Vec3(0,1,0),i*2*Math.PI/EYE_COUNT);
        return unit(flat.scale(Math.cos(pitch)).add(0,Math.sin(pitch),0));
    }
    /**
     * Which eye fires the {@code slot}-th beam of the {@code volley}-th beat.
     *
     * <p>Beams in one volley are spread across the ring rather than taken in sequence, so a volley
     * always arrives from genuinely opposed bearings - the point of the passage is that there is no
     * direction to face - and the ring is walked by a step coprime with its size so consecutive
     * volleys never reuse an eye.
     */
    public static int eyeFor(int volley, int slot, int count) {
        return Math.floorMod(volley*3 + slot*(EYE_COUNT/Math.max(1,count)), EYE_COUNT);
    }
    /** How far out an eye and its beam start: past the fog, so a beam is already at speed on arrival. */
    public static final double EYE_DISTANCE = 220;
    /**
     * The Eyes passage, as {tick, beams}: one eye, one from behind you, then pairs, then threes.
     *
     * <p>The schedule lives here rather than in the controller because the client needs it too. An
     * eye that fires without contracting first is a beam with no telegraph, and the only way the
     * renderer can know which of the ten to contract is to read the same table the server fires
     * from. Everything else about an eye - bearing, charge, which slot takes which eye - is derived
     * from this, so nothing about the passage travels on the wire.
     */
    public static final int[][] EYE_BEATS = {{48,1},{76,1},{104,2},{128,2},{152,2},{174,3},{196,3}};
    /** The converging suns close the passage, with every eye open behind them. */
    public static final int EYE_BURIAL = 210;
    /** Ticks of pupil contraction before the beam leaves. Shorter than the beam own telegraph. */
    public static final int EYE_CHARGE = 34;

    /** The Mirror passage: a volley from a different reflection every twenty-two ticks. */
    public static final int MIRROR_START = 48, MIRROR_PERIOD = 22, MIRROR_VOLLEYS = 9;
    /** How long a reflection stays lit before it throws. The fragment telegraph, plus a beat. */
    public static final int MIRROR_TELL = 32;

    /**
     * The Giant passage: a long rain of flicked fragments, then three gestures that never overlap.
     *
     * <p>Each gesture is the telegraph for the shape that follows it, which is the only reason to
     * draw a figure that size. {@link #GESTURE_WIND} is how long the arm takes to wind up, so the
     * renderer starts moving before the hazard exists and the player reads the body, not the HUD.
     */
    public static final int FLICK_START = 60, FLICK_PERIOD = 18, FLICK_VOLLEYS = 9;
    public static final int GIANT_SWEEP = 280, GIANT_PALM = 400, GIANT_FISTS = 490;
    public static final int[] GIANT_LATE_FLICKS = {370, 388};
    public static final int GESTURE_WIND = 40;
    /** How far off the horizon the figure stands, and how tall it is drawn against that distance. */
    public static final double GIANT_DISTANCE = 300, GIANT_SCALE = 210;

    /**
     * The lattice cells of the Mirror passage that can hold a lit reflection.
     *
     * <p>Only the ring at the player's own height is ever lit. The copies above and below are there
     * to make the hall read as infinite; a threat announced from a cell the player cannot see
     * without looking up is a threat with no telegraph.
     */
    public static final int MIRROR_CELLS = 8;
    public static final double MIRROR_SPAN = 32, MIRROR_RISE = 24;
    /** Which reflection lights up for a given volley. Stepping by three walks the whole ring. */
    public static int mirrorCell(int volley) { return Math.floorMod(volley*3,MIRROR_CELLS); }
    /** The world offset of one lit cell from the player, on the same lattice the renderer draws. */
    public static Vec3 mirrorOffset(int cell) {
        double angle=Math.floorMod(cell,MIRROR_CELLS)*2*Math.PI/MIRROR_CELLS;
        return new Vec3(Math.cos(angle)*MIRROR_SPAN,0,Math.sin(angle)*MIRROR_SPAN);
    }

    /**
     * The Clock passage, as the bearings the figure comes to rest on before each strike.
     *
     * <p>The strikes always came from three yaws off the passage forward axis, which is a direction
     * the player may have turned away from twenty seconds earlier - so the only way to find a strike
     * was to already know where it would be. The bearings are now spread around the whole circle and
     * a figure walks between them, settling {@link #CLOCK_SETTLE} ticks early on the one the next
     * strike will use. Watch where it stops, look there, counter.
     */
    public static final int[] CLOCK_STRIKES = {40,64,88,160,184,208};
    public static final double[] CLOCK_YAW = {-115,140,-25,75,-160,35};
    /** How long the figure holds still before striking. Long enough to turn and find it. */
    public static final int CLOCK_SETTLE = 10;
    /** The strike leaves from twelve blocks out, so the figure stands exactly where it comes from. */
    public static final double CLOCK_ORBIT = 12;

    /** Which strike is next at this tick, or the count if the passage is past its last one. */
    public static int clockStrikeIndex(int age) {
        int n=0;
        while(n<CLOCK_STRIKES.length && CLOCK_STRIKES[n]<age) n++;
        return n;
    }
    /** The pitch of one strike. Unchanged: the closing phase alternates, the opening one does not. */
    public static double clockPitch(int index, boolean closing) {
        int within=Math.floorMod(index,3);
        return closing ? (within%2==0?35:-35) : (within==2?35:0);
    }
    /**
     * Where the figure is at this tick: sweeping between bearings, or already settled on the next.
     *
     * <p>It always travels the same way round rather than by the shorter arc, so it reads as
     * circling the player rather than flicking between points.
     */
    public static Vec3 clockAim(int age, boolean closing, Vec3 forward) {
        int n=clockStrikeIndex(age);
        if(n>=CLOCK_STRIKES.length)
            return bearing(CLOCK_YAW[CLOCK_YAW.length-1],clockPitch(CLOCK_YAW.length-1,closing),forward);
        int arrive=CLOCK_STRIKES[n]-CLOCK_SETTLE;
        if(age>=arrive) return bearing(CLOCK_YAW[n],clockPitch(n,closing),forward);
        int previous=n==0?CLOCK_YAW.length-1:n-1;
        int leave=n==0?0:CLOCK_STRIKES[n-1];
        double t=Mth.clamp((age-leave)/(double)Math.max(1,arrive-leave),0,1);
        double eased=t*t*(3-2*t);
        double sweep=Math.floorMod(Math.round(CLOCK_YAW[n]-CLOCK_YAW[previous]),360L);
        double pitch=Mth.lerp(eased,clockPitch(previous,closing),clockPitch(n,closing));
        return bearing(CLOCK_YAW[previous]+sweep*eased,pitch,forward);
    }
    private static Vec3 bearing(double yawDegrees, double pitchDegrees, Vec3 forward) {
        double pitch=Math.toRadians(pitchDegrees);
        return unit(rotate(unit(new Vec3(forward.x,0,forward.z)),new Vec3(0,1,0),Math.toRadians(yawDegrees))
                .scale(Math.cos(pitch)).add(0,Math.sin(pitch),0));
    }

    /**
     * The Tunnel passage: a shaft of colour rushing at the player, with things in it.
     *
     * <p>Nothing actually moves the player - the tunnel comes to them, which is what sells the
     * fall - so all they have to do is step out of the way of what is in it. The obstacles arrive in
     * lanes with exactly one lane open, and the open lane walks across, so standing in the gap is
     * never the answer twice running.
     */
    public static final int TUNNEL_START = 40, TUNNEL_PERIOD = 14, TUNNEL_VOLLEYS = 15;
    /** How far down the shaft an obstacle starts, and how far apart the lanes are. */
    public static final double TUNNEL_REACH = 120, TUNNEL_SPREAD = 7;
    /**
     * One obstacle offset from the tunnel axis.
     *
     * <p>{@code count} obstacles occupy {@code count + 1} lanes, so one is always open. A little
     * vertical stagger keeps a volley from reading as a flat wall, without ever putting the gap
     * somewhere a player standing on the floor cannot reach.
     */
    public static Vec3 tunnelOffset(int volley, int index, int count, Vec3 axis) {
        Vec3 right=unit(unit(axis).cross(new Vec3(0,1,0)));
        int lane=index<tunnelGapLane(volley,count)?index:index+1;
        return right.scale((lane-count/2.0)*TUNNEL_SPREAD).add(0,Math.sin((volley+index)*1.1)*1.5,0);
    }
    /** Which lane a volley leaves open. It walks, so the safe spot is never the safe spot twice. */
    public static int tunnelGapLane(int volley, int count) { return Math.floorMod(volley*2+1,count+1); }
    /** Where that open lane is, so the renderer can light the way through. */
    public static Vec3 tunnelGap(int volley, int count, Vec3 axis) {
        Vec3 right=unit(unit(axis).cross(new Vec3(0,1,0)));
        return right.scale((tunnelGapLane(volley,count)-count/2.0)*TUNNEL_SPREAD);
    }
    /** How many obstacles a volley throws: alternating, so the gap changes width as well as place. */
    public static int tunnelCount(int volley) { return volley%2==0?3:2; }

    public static Vec3 position(UnwakingHazard h, double age) {
        Vec3 end=h.openings().get(0);
        if(age<=52) return h.origin().lerp(end,Mth.clamp((age-20)/32,0,1));
        if(age<64) return end;
        return end.lerp(h.origin(),Mth.clamp((age-64)/32,0,1));
    }
    public static boolean intersects(UnwakingHazard h, int age, AABB previous, AABB current) {
        if(h.contact(age)<0) return false;
        if(h.kind()==Kind.CLOCK_STRIKE) return true;
        Vec3 a=position(h,Math.max(20,age-1)), b=position(h,age);
        Vec3 motion=current.getCenter().subtract(previous.getCenter());
        // Relative motion gives continuous collision without sampling or distant-coordinate loss.
        return capsule(previous,a,b.subtract(motion),VERDICT_RADIUS);
    }
    public static Vec3 incomingSource(UnwakingHazard h, int age) {
        if(h.kind()==Kind.CLOCK_STRIKE) return h.origin();
        double earlier=Math.max(20,age-1);
        Vec3 point=position(h,age), direction=unit(position(h,earlier).subtract(position(h,age+0.1)));
        return point.add(direction.scale(8));
    }
    public static boolean aimed(Vec3 eye, Vec3 look, Vec3 target) {
        return aimed(eye,look,target,12);
    }
    public static boolean aimed(Vec3 eye,Vec3 look,Vec3 target,double degrees) {
        return look.lengthSqr()>0.9 && unit(target.subtract(eye)).dot(look.normalize())>=Math.cos(Math.toRadians(degrees));
    }
}
