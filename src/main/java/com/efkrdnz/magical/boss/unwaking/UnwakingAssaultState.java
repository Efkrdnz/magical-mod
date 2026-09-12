package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

/** Recipient-local presentation and movement state on the authoritative encounter clock. */
public record UnwakingAssaultState(int order, long start, Vec3 anchor, Vec3 forward,
        Vec3 lockAnchor, long lockUntil) {
    public static final int PASSAGE_TICKS = 300, SKY_TICKS = 600, ASSAULT_TICKS = 1200, REWARD_TICKS = 400;
    /**
     * The most passages any rotation has, which is the stride the order byte is packed on.
     *
     * <p>Rotations no longer have to be the same length. The byte is {@code rotation * this + start},
     * so a rotation with fewer passages simply leaves the high starts in its block unused - see
     * {@link #valid(int)}, which is what the wire checks.
     */
    public static final int MAX_ROTATION_SIZE = 4;
    public static final UnwakingAssaultState NONE = new UnwakingAssaultState(0, -1, Vec3.ZERO,
            new Vec3(0,0,1), Vec3.ZERO, 0);
    /**
     * One passage of an assault.
     *
     * <p>Length is a constructor field rather than a ternary on the constant, so a rotation's total
     * is data that {@link Rotation#ticks} can add up and a test can check against
     * {@link #ASSAULT_TICKS}. That equality is not decoration: {@link #segment} walks the passages
     * subtracting their lengths and returns the last index when it runs out, so a rotation that
     * sums to less than the assault leaves {@link #passageAge} running off the end of its passage.
     */
    public enum Passage {
        SKY(SKY_TICKS), VORTEX(PASSAGE_TICKS), CLOCK(PASSAGE_TICKS),
        EYES(PASSAGE_TICKS), MIRROR(PASSAGE_TICKS), GIANT(SKY_TICKS), TUNNEL(PASSAGE_TICKS);
        private final int ticks;
        Passage(int ticks) { this.ticks = ticks; }
        public int ticks() { return ticks; }
    }
    /**
     * Which three passages an assault runs, chosen by how angry the boss is.
     *
     * <p>CALM is what the boss had through phase two and is unchanged. ENRAGED is the phase-three
     * set, gated by {@link UnwakingPhase#enraged()}. Both must total {@link #ASSAULT_TICKS}; adding
     * a rotation is a line here plus a schedule and a renderer, with nothing on the wire to change.
     */
    public enum Rotation {
        CALM(Passage.SKY, Passage.VORTEX, Passage.CLOCK),
        ENRAGED(Passage.EYES, Passage.MIRROR, Passage.TUNNEL, Passage.GIANT);
        private final java.util.List<Passage> passages;
        Rotation(Passage... passages) { this.passages = java.util.List.of(passages); }
        public java.util.List<Passage> passages() { return passages; }
        public int size() { return passages.size(); }
        /** How long an assault on this rotation runs. Rotations are not all the same length. */
        public int ticks() { return passages.stream().mapToInt(Passage::ticks).sum(); }
    }
    /** Every order byte that names a real rotation and a real starting passage within it. */
    public static java.util.List<Integer> orders() {
        java.util.List<Integer> all=new java.util.ArrayList<>();
        for(Rotation r:Rotation.values())
            for(int start=0;start<r.size();start++) all.add(r.ordinal()*MAX_ROTATION_SIZE+start);
        return java.util.List.copyOf(all);
    }
    public static boolean valid(int order) {
        int r=Math.floorDiv(order,MAX_ROTATION_SIZE), start=Math.floorMod(order,MAX_ROTATION_SIZE);
        return r>=0 && r<Rotation.values().length && start<Rotation.values()[r].size();
    }
    public static Rotation rotation(int order) { return Rotation.values()[Math.floorDiv(order, MAX_ROTATION_SIZE)]; }
    /** The order byte that opens on a given passage, which is what the debug command names. */
    public static int orderStartingAt(Passage passage) {
        for(Rotation r:Rotation.values()) {
            int index=r.passages().indexOf(passage);
            if(index>=0) return r.ordinal()*MAX_ROTATION_SIZE+index;
        }
        throw new IllegalArgumentException("No rotation contains "+passage);
    }
    /** The passage a given order reaches after {@code index} whole segments. */
    public static Passage passage(int order, int index) {
        Rotation rotation=rotation(order);
        return rotation.passages().get(Math.floorMod(order%MAX_ROTATION_SIZE + index, rotation.size()));
    }
    /** How long this assault runs: its own rotation total, not one shared constant. */
    public int length() { return rotation(order).ticks(); }

    public boolean active(long now) { return start >= 0 && now >= start && now < start + length(); }
    public int age(long now) { return (int)Math.max(0, now - start); }
    public static int segment(int order,int age) {
        int size=rotation(order).size();
        for(int i=0;i<size-1;i++) {
            int duration=passage(order,i).ticks();
            if(age<duration) return i;
            age-=duration;
        }
        return size-1;
    }
    public int passageAge(long now) {
        int offset=0;
        for(int i=0;i<segment(order,age(now));i++) offset+=passage(order,i).ticks();
        return age(now)-offset;
    }
    public Passage passage(long now) { return passage(order,segment(order,age(now))); }
    /**
     * Whether the whole dimension is inverted right now - sky, monuments, attacks and HUD at once.
     *
     * <p>The Mirror is inverted end to end: a reflection that came back the same colour would not be
     * a reflection. The others invert only for their finale, which is the existing Sky behaviour.
     */
    public float palette(long now) {
        int age=passageAge(now);
        return switch(passage(now)) {
            case VORTEX, MIRROR -> 1;
            // The tunnel supplies its own colour, so the dimension-wide inversion stays out of it.
            case TUNNEL -> 0;
            case SKY -> age>=390&&age<490 ? 1 : 0;
            case GIANT -> age>=430 ? 1 : 0;
            case EYES -> age>=210 ? 1 : 0;
            default -> 0;
        };
    }
    public boolean locked(long now) { return active(now) && now < lockUntil; }
    public static boolean clockLocked(int age) { return age >= 40 && age < 120 || age >= 160 && age < 240; }
    public com.efkrdnz.magical.magic.ChronosSkyCutGeometry.Frame woundFrame() {
        return com.efkrdnz.magical.magic.ChronosSkyCutGeometry.frame(anchor, forward,
                96 / com.efkrdnz.magical.magic.ChronosSkyCutGeometry.DISTANCE);
    }
    public Vec3 wound() { return woundFrame().center(); }
    public com.efkrdnz.magical.magic.ChronosSkyCutGeometry.Frame woundFrame(long now) {
        var frame=woundFrame(); int age=passageAge(now);
        // Open horizontally for the guillotine; turn upright again for the closing ring.
        float blend=UnwakingPresentation.smooth((age-300)/20F)*(1-UnwakingPresentation.smooth((age-490)/24F));
        double angle=Math.toRadians(55)*blend;
        Vec3 r=frame.right().normalize(), u=frame.up().normalize();
        return new com.efkrdnz.magical.magic.ChronosSkyCutGeometry.Frame(frame.center(),
                r.scale(Math.cos(angle)).add(u.scale(Math.sin(angle))).scale(frame.right().length()),
                u.scale(Math.cos(angle)).subtract(r.scale(Math.sin(angle))).scale(frame.up().length()));
    }
    public Vec3 vortex() { return anchor.add(0,96,0); }
    /**
     * Where the world-sized figure is, for the half of it that has to exist in world space.
     *
     * <p>The figure itself is drawn camera-relative so it never parallaxes, but its attacks are real
     * hazards with real origins, and they have to leave from where the player is looking when they
     * watch it move. This is that point: far along the passage's forward axis and high, so a body
     * thrown from it arrives on a shallow descending line rather than dropping out of the zenith.
     */
    public Vec3 giant() { return anchor.add(giantBearing().scale(GIANT_REACH)); }
    /**
     * The direction the figure stands in, shared with the renderer that draws it.
     *
     * <p>The gestures are the only telegraph the Giant passage has, so the drawn arm and the thrown
     * body have to leave the same bearing. They did not at first - the figure was drawn on a fixed
     * bearing while its attacks left from here - which is a telegraph pointing at nothing.
     */
    public Vec3 giantBearing() { return forward.scale(240).add(0,150,0).normalize(); }
    public static final double GIANT_REACH = 283;
    /**
     * How bright the ambient monuments are. Passages that own the sky push them out of the way.
     *
     * <p>The Eyes and the Giant both draw at skybox distance, so a lit monument field in front of
     * them reads as scenery standing between the player and something that is supposed to be
     * enormous and far away - the same reason the Sky passage already dims for its wound.
     */
    public float scenery(long now) {
        return switch(passage(now)) {
            case SKY, EYES, GIANT, TUNNEL -> .15F*(1-Math.min(1,effect(now)*2));
            default -> .15F;
        };
    }
    public float effect(long now) {
        if (!active(now)) return 0;
        int age = passageAge(now);
        return UnwakingPresentation.smooth(Math.min(age / 40F, (passage(now).ticks() - age) / 20F));
    }
    public void write(RegistryFriendlyByteBuf b) {
        b.writeByte(order); b.writeLong(start); vec(b,anchor); vec(b,forward); vec(b,lockAnchor);
        b.writeLong(lockUntil);
    }
    public static UnwakingAssaultState read(RegistryFriendlyByteBuf b) {
        int order=b.readUnsignedByte();
        if(!valid(order)) throw new IllegalArgumentException("Invalid assault order");
        return new UnwakingAssaultState(order,b.readLong(),vec(b),vec(b),vec(b),b.readLong());
    }
    private static void vec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vec(RegistryFriendlyByteBuf b) {
        Vec3 v=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
        if(!Double.isFinite(v.x)||!Double.isFinite(v.y)||!Double.isFinite(v.z)) throw new IllegalArgumentException("Invalid assault vector");
        return v;
    }
}
