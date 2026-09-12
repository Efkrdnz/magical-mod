package com.efkrdnz.magical.boss.unwaking;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Immutable, shared collision/presentation frame. No world objects or client authority. */
public record UnwakingHazard(long id, Kind kind, long start, Vec3 origin, Vec3 axis, double length,
        int variant, List<Vec3> openings, UUID recipient) {
    public static final double HAND_INNER = 7, HAND_OUTER = 48, HAND_RADIUS = 3.5;
    public static final double CAGE_HALF = 14, CORRIDOR_START = 7, CORRIDOR_HALF = 4;
    public static final double FOLD_RADIUS = 6, FOLD_HALF = 36, SCAR_RADIUS = 3.5;
    public static final double MEMORY_RADIUS = 6, SLAB_HALF = 6, REND_HALF = 12, STAR_RADIUS = 9, REFUGE_RADIUS = 7;
    /** Move out of the way, or answer the prompt. There is no third option: blocking does nothing. */
    public enum Defense { MOVE, PARRY }
    public enum Kind {
        HAND(Defense.PARRY, 36, 44, 144), CONSTELLATION(Defense.MOVE, 40, 60, 116),
        RETURNING(Defense.PARRY, 32, 40, 136), FOLD(Defense.MOVE, 40, 60, 126),
        SHELL(Defense.MOVE, 32, 40, 80), PRESSURE(Defense.PARRY, 28, 40, 48),
        FRACTURE(Defense.MOVE, 32, 40, 48), ATTENTION(Defense.PARRY, 40, 36, 44),
        GESTURE(Defense.PARRY, 28, 32, 100), MEMORY(Defense.MOVE, 24, 72, 80),
        REFUSAL(Defense.MOVE, 0, 50, 100), SKY_REND(Defense.PARRY, 48, 52, 124),
        INVERSION(Defense.MOVE, 44, 64, 110), STARFALL(Defense.MOVE, 36, 48, 136),
        PROCESSION_CUT(Defense.PARRY,24,32,44), PALM(Defense.PARRY,24,24,36), ECHO(Defense.MOVE,24,24,36),
        THRUST(Defense.PARRY,24,24,36), ARRIVAL(Defense.MOVE,0,16,18),
        EARTH_FRONT(Defense.MOVE,48,40,100), WORLD_CUT(Defense.MOVE,48,40,84),
        HORIZON_HAND(Defense.MOVE,48,40,108), LAW_FRONT(Defense.MOVE,48,40,84),
        FALLEN_STAR(Defense.MOVE,48,40,76), STAR_FRONT(Defense.MOVE,48,24,76), DECREE(Defense.PARRY,48,32,44),
        RETURNING_VERDICT(Defense.PARRY,32,20,100),
        CLOCK_STRIKE(Defense.PARRY,48,20,28), VORTEX_BEAM(Defense.PARRY,48,24,40),
        FIRMAMENT_GUILLOTINE(Defense.PARRY,56,32,84), SIXFOLD_BURIAL(Defense.PARRY,48,40,84),
        NULL_HORIZON(Defense.PARRY,56,40,100),
        /**
         * A piece of the cut sky, torn loose and falling.
         *
         * <p>Appended last deliberately. {@link UnwakingHazard#write} serialises the kind as an
         * ordinal and both classifiers below are ordinal comparisons, so the end of the list is the
         * only place a constant can be added without silently reclassifying its neighbours - and
         * last is also what makes it {@code openCombat()} and {@code major()} without a special
         * case, which is what a falling piece of firmament should be.
         *
         * <p>Twenty-eight ticks of telegraph then thirty-six of approach, covering the hundred and
         * fifteen blocks from the wound to just past the player: the same speed as the converging
         * suns, which is the one attack in this passage that already reads as an object.
         */
        FIRMAMENT_FRAGMENT(Defense.PARRY,48,28,76),
        /**
         * Something in the tunnel, coming at you. The one body in the fight you can afford to clip.
         *
         * <p>Every other attack the boss has is a removal: {@link #damageFor} multiplies by twenty,
         * so a hit is nine hundred and sixty raw on a twenty-health player and full armour only
         * takes it to three hundred. That is the fight, deliberately. The tunnel is the exception -
         * it throws fifteen volleys in eleven seconds and asks you to thread them, which is only
         * worth attempting if a clip costs health rather than the run. See {@link #grazing}.
         *
         * <p>Appended last, like every kind before it: the enum is serialised by ordinal and both
         * classifiers below are ordinal comparisons, so the end of the list is the only safe place.
         */
        TUNNEL_SHARD(Defense.PARRY,6,22,56);
        public final Defense defense;
        public final float damage;
        public final int impact, end;
        Kind(Defense defense, float damage, int impact, int end) {
            this.defense = defense; this.damage = damage; this.impact = impact; this.end = end;
        }
        public boolean openCombat() { return ordinal() >= PROCESSION_CUT.ordinal(); }
        public boolean major() { return openCombat() && ordinal() >= EARTH_FRONT.ordinal() && this != DECREE && !grazing(); }
        /**
         * Whether clipping this costs health rather than the run.
         *
         * <p>Grazing bodies sit outside both the damage multiplier and the {@code baseDamageFor}
         * floors, which exist to make ordinary contact lethal. They are also not {@code major()}:
         * a chip-damage obstacle has no business winning the HUD banner off a guillotine.
         */
        public boolean grazing() { return this == TUNNEL_SHARD; }
        public int powerTier() { return 4; }
        public String nameKey() { return "attack.magical.unwaking." + name().toLowerCase(java.util.Locale.ROOT); }
        /** A fifth of maximum health, before armour. Four or five clips is still a death. */
        public static final float GRAZE_FRACTION = 0.2F;
        public float damageFor(float maximumHealth) {
            // Deliberately outside the multiplier. That knob exists to make contact lethal, and the
            // whole point of a grazing body is that it is not - scaling it would undo the exception.
            if (grazing()) return Math.max(1, maximumHealth * GRAZE_FRACTION);
            return baseDamageFor(maximumHealth) * com.efkrdnz.magical.boss.unwaking.UnwakingCombatRules.damageMultiplier();
        }
        public float baseDamageFor(float maximumHealth) {
            if (UnwakingAssaultGeometry.projectile(this)) return Math.max(32,maximumHealth*.25F);
            if (openCombat()) return this == ARRIVAL ? 0 : Math.max(damage,maximumHealth*(major()?0.35F:0.20F));
            float fraction = this == SKY_REND || this == INVERSION ? 0.35F : this == PRESSURE ? 0.18F : 0.25F;
            return this == REFUSAL ? 0 : Math.max(damage, maximumHealth * fraction);
        }
        public com.efkrdnz.magical.magic.MagicSkillDefinition counterSkill() {
            return switch (this) {
                case RETURNING, FOLD, SKY_REND, WORLD_CUT, LAW_FRONT, THRUST -> com.efkrdnz.magical.magic.MagicContent.CREASE_FOLD;
                case PRESSURE -> com.efkrdnz.magical.magic.MagicContent.PRISM_CASCADE;
                default -> com.efkrdnz.magical.magic.MagicContent.DIVINE_DIVIDER;
            };
        }
    }

    public UnwakingHazard { openings = List.copyOf(openings); }
    public int age(long clock) { return (int) (clock - start); }
    public UnwakingHazard frame(Vec3 origin, Vec3 axis, double length) { return new UnwakingHazard(id, kind, start, origin, axis, length, variant, openings, recipient); }
    public boolean visible(int age) { return age >= 0 && age < kind.end; }
    public boolean recovering(int age) {
        if (kind.openCombat()) return age > lastContactTick();
        return age >= switch (kind) { case HAND -> 104; case CONSTELLATION, FOLD -> 76; case RETURNING, SKY_REND -> 96; case INVERSION -> 76; case STARFALL -> 64; case GESTURE -> variant == 1 ? 40 : 64; case REFUSAL -> 50; default -> kind.end; };
    }
    public int lastContactTick() {
        return switch(kind) {
            case RETURNING_VERDICT -> 96; case CLOCK_STRIKE -> 20;
            case VORTEX_BEAM -> 31;
            case FIRMAMENT_GUILLOTINE -> 72; case SIXFOLD_BURIAL -> 76; case NULL_HORIZON -> 88;
            case FIRMAMENT_FRAGMENT -> 64;
            case TUNNEL_SHARD -> 48;
            case WORLD_CUT -> 76; case HORIZON_HAND -> 100; case LAW_FRONT -> 64;
            case FALLEN_STAR -> 60; case EARTH_FRONT -> 88; case STAR_FRONT -> 64;
            default -> kind.impact;
        };
    }
    public int nextImpact(int age) {
        if (kind == Kind.RETURNING && age > 52) return 84;
        if (kind == Kind.GESTURE && variant != 1 && age > 32) return 56;
        if (kind == Kind.SKY_REND && age > 52) return 88;
        return kind.impact;
    }

    /** Clock plane normal is +Y, or tilted 35 degrees about Z. Axis is its locked first hand. */
    public Vec3 planeNormal() { return variant == 1 ? new Vec3(-Math.sin(Math.toRadians(35)), Math.cos(Math.toRadians(35)), 0) : new Vec3(0, 1, 0); }
    public Vec3 hand(double age) { return rotate(axis, planeNormal(), Math.toRadians(Mth.clamp(age - 44, 0, 60) * 3)); }
    public Vec3 right() { return unit(axis.cross(Math.abs(axis.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0))); }
    public Vec3 up() { return right().cross(axis).normalize(); }
    public static Vec3 unit(Vec3 vector) { return vector.lengthSqr() < 1e-10 ? new Vec3(0, 0, 1) : vector.normalize(); }
    public static Vec3 rotate(Vec3 v, Vec3 normal, double radians) {
        double c = Math.cos(radians), s = Math.sin(radians);
        return v.scale(c).add(normal.cross(v).scale(s)).add(normal.scale(normal.dot(v) * (1 - c)));
    }
    public static Vec3 towardCenter(Vec3 position) {
        Vec3 delta = UnwakingEncounterService.CENTER.subtract(position);
        Vec3 best = new Vec3(1, 0, 0);
        for (Vec3 candidate : CARDINALS) if (candidate.dot(delta) > best.dot(delta)) best = candidate;
        return best;
    }
    public static final List<Vec3> CARDINALS = List.of(new Vec3(1,0,0), new Vec3(-1,0,0), new Vec3(0,0,1), new Vec3(0,0,-1), new Vec3(0,1,0), new Vec3(0,-1,0));

    /** -1 = no active contact; returning edges/double gestures have separate contact IDs. */
    public int contact(int age) {
        return switch (kind) {
            case RETURNING_VERDICT -> age>=20&&age<=52||age>=64&&age<=96?0:-1;
            case HAND -> age >= 44 && age <= 104 ? 0 : -1;
            case RETURNING -> age >= 40 && age <= 52 ? 0 : age >= 84 && age <= 96 ? 1 : -1;
            case SHELL -> age >= 40 && age <= 64 ? 0 : -1;
            case GESTURE -> age == 32 ? 0 : variant != 1 && age == 56 ? 1 : -1;
            case SKY_REND -> age == 52 ? 0 : age == 88 ? 1 : -1;
            case REFUSAL, ARRIVAL -> -1;
            case WORLD_CUT, HORIZON_HAND, LAW_FRONT, FALLEN_STAR, EARTH_FRONT, STAR_FRONT, VORTEX_BEAM,
                    FIRMAMENT_GUILLOTINE, SIXFOLD_BURIAL, NULL_HORIZON, FIRMAMENT_FRAGMENT -> age >= kind.impact && age <= lastContactTick() ? 0 : -1;
            default -> age == kind.impact ? 0 : -1;
        };
    }

    public boolean intersects(int age, AABB previous, AABB current) {
        if (contact(age) < 0) return false;
        if (UnwakingSkyGeometry.attack(kind)) return UnwakingSkyGeometry.intersects(this,age,previous,current);
        if (UnwakingAssaultGeometry.projectile(kind) || kind==Kind.CLOCK_STRIKE) return UnwakingAssaultGeometry.intersects(this,age,previous,current);
        if (kind == Kind.PRESSURE || kind == Kind.ATTENTION) return true;
        if (kind == Kind.GESTURE) {
            Vec3 direction = age == 56 ? right() : axis;
            return new UnwakingGesture(origin, direction).intersects(previous.getCenter(), current.getCenter(), current.getXsize(), current.getYsize());
        }
        // Sample both motion paths at <=0.15 blocks, with a matching conservative margin.
        // Moving hand tips/shells are included in the subdivision bound; instant strikes
        // sweep only player motion. This cannot tunnel through a narrow active surface.
        double speed = previous.getCenter().distanceTo(current.getCenter());
        double hazardSpeed = switch (kind) { case HAND -> 2.52; case SHELL -> 2; case RETURNING -> length / 12; case WORLD_CUT -> 96D/36; case HORIZON_HAND -> 13.5; case FALLEN_STAR -> 2; case EARTH_FRONT, STAR_FRONT -> 2.2; case LAW_FRONT -> 0.84; default -> 0; };
        int steps = Math.max(1, (int) Math.ceil((speed + hazardSpeed) / 0.15));
        for (int i = 0; i <= steps; i++) {
            double fraction = (double) i / steps;
            AABB box = previous.move(current.getCenter().subtract(previous.getCenter()).scale(fraction)).inflate(0.075);
            double time = hazardSpeed == 0 ? age : Math.max(kind.impact, age - 1 + fraction);
            if (kind == Kind.RETURNING && age >= 84) time = Math.max(84, age - 1 + fraction);
            if (contains(time, box)) return true;
        }
        return false;
    }

    public boolean safeOpening(AABB box) {
        for (Vec3 opening : openings) {
            boolean inside = true;
            for (Vec3 corner : corners(box)) {
                Vec3 delta = corner.subtract(origin);
                if (delta.lengthSqr() > 0.01 && unit(delta).dot(opening) < Math.cos(Math.toRadians(20))) { inside = false; break; }
            }
            if (inside) return true;
        }
        return false;
    }

    private boolean contains(double age, AABB box) {
        if (kind.openCombat()) return UnwakingOpenGeometry.contains(this,age,box);
        return switch (kind) {
            case HAND -> capsule(box, origin.add(hand(age).scale(HAND_INNER)), origin.add(hand(age).scale(HAND_OUTER)), HAND_RADIUS);
            case RETURNING -> {
                boolean back = age >= 84;
                double progress = Mth.clamp((age - (back ? 84 : 40)) / 12, 0, 1);
                double tip = back ? length * (1 - progress) : length * progress;
                double tail = Mth.clamp(tip + (back ? 8 : -8), 0, length);
                yield capsule(box, origin.add(axis.scale(tail)), origin.add(axis.scale(tip)), SCAR_RADIUS);
            }
            case CONSTELLATION -> cubeDanger(box);
            case FOLD -> cylinder(box, FOLD_RADIUS, FOLD_HALF);
            case FRACTURE -> Math.abs(box.getCenter().subtract(origin).dot(axis)) <= SLAB_HALF + projectionRadius(box, axis);
            case SKY_REND -> {
                Vec3 normal = age >= 88 ? right() : axis;
                yield Math.abs(box.getCenter().subtract(origin).dot(normal)) <= REND_HALF + projectionRadius(box, normal);
            }
            case INVERSION -> !insideRefuge(box);
            case STARFALL -> cylinder(box, STAR_RADIUS, 64);
            case MEMORY -> distanceSqr(box, origin) <= MEMORY_RADIUS * MEMORY_RADIUS;
            case SHELL -> {
                if (variant > 0 && age >= 64 && distanceSqr(box, origin) <= 100) yield true;
                if (safeOpening(box)) yield false;
                double radius = 52 - Mth.clamp(age - 40, 0, 24) * 2;
                double farthest = 0;
                for (Vec3 corner : corners(box)) farthest = Math.max(farthest, corner.distanceToSqr(origin));
                yield distanceSqr(box, origin) <= Math.pow(radius + 1.75, 2) && farthest >= Math.pow(Math.max(4, radius - 1.75), 2);
            }
            default -> false;
        };
    }

    public boolean insideRefuge(AABB box) {
        for (Vec3 refuge : openings) {
            boolean safe = true;
            for (Vec3 corner : corners(box)) if (corner.distanceToSqr(refuge) > REFUGE_RADIUS * REFUGE_RADIUS) { safe = false; break; }
            if (safe) return true;
        }
        return false;
    }

    private boolean cubeDanger(AABB box) {
        AABB cube = new AABB(origin, origin).inflate(CAGE_HALF);
        if (!cube.intersects(box)) return false;
        // Clip to the dangerous cube before subtraction: an outside shoulder must not
        // make a safely escaped player collide with an imaginary closed face.
        AABB overlap = cube.intersect(box);
        for (Vec3 corner : corners(overlap)) {
            Vec3 local = corner.subtract(origin);
            if (local.dot(axis) < CORRIDOR_START || Math.abs(local.dot(right())) > CORRIDOR_HALF || Math.abs(local.dot(up())) > CORRIDOR_HALF) return true;
        }
        return false;
    }
    private boolean cylinder(AABB box, double radius, double halfLength) {
        Vec3 delta = box.getCenter().subtract(origin);
        if (Math.abs(delta.dot(axis)) > halfLength + projectionRadius(box, axis)) return false;
        return capsule(box, origin.add(axis.scale(-halfLength)), origin.add(axis.scale(halfLength)), radius);
    }
    public Vec3 source(int age, Vec3 player) {
        if (UnwakingSkyGeometry.attack(kind)) return UnwakingSkyGeometry.source(this,age,player);
        if (UnwakingAssaultGeometry.projectile(kind) || kind==Kind.CLOCK_STRIKE) return UnwakingAssaultGeometry.incomingSource(this,age);
        return switch (kind) {
            case HAND -> origin.add(hand(age - 2).scale(Mth.clamp(player.distanceTo(origin), HAND_INNER, HAND_OUTER)));
            case RETURNING -> age >= 84 ? origin.add(axis.scale(length)) : origin;
            case ATTENTION -> player.add(axis.scale(12));
            default -> origin;
        };
    }
    public static double projectionRadius(AABB b, Vec3 axis) { return (Math.abs(axis.x) * b.getXsize() + Math.abs(axis.y) * b.getYsize() + Math.abs(axis.z) * b.getZsize()) / 2; }
    public static double distanceSqr(AABB box, Vec3 point) {
        double x = point.x - Mth.clamp(point.x, box.minX, box.maxX), y = point.y - Mth.clamp(point.y, box.minY, box.maxY), z = point.z - Mth.clamp(point.z, box.minZ, box.maxZ);
        return x*x + y*y + z*z;
    }
    public static boolean capsule(AABB box, Vec3 a, Vec3 b, double radius) {
        // Squared distance to an AABB is convex along a segment. Minimize that function,
        // including endpoints, instead of using a sphere around the whole player.
        Vec3 delta = b.subtract(a);
        double lo = 0, hi = 1;
        for (int i = 0; i < 32; i++) {
            double m1 = (2 * lo + hi) / 3, m2 = (lo + 2 * hi) / 3;
            if (distanceSqr(box, a.add(delta.scale(m1))) <= distanceSqr(box, a.add(delta.scale(m2)))) hi = m2; else lo = m1;
        }
        return Math.min(Math.min(distanceSqr(box, a), distanceSqr(box, b)), distanceSqr(box, a.add(delta.scale((lo + hi) / 2)))) <= radius * radius;
    }
    public static Vec3[] corners(AABB box) {
        Vec3[] result = new Vec3[8];
        for (int i = 0; i < 8; i++) result[i] = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
        return result;
    }
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeLong(id); buf.writeEnum(kind); buf.writeLong(start); writeVec(buf, origin); writeVec(buf, axis); buf.writeDouble(length); buf.writeVarInt(variant);
        buf.writeVarInt(openings.size()); openings.forEach(v -> writeVec(buf, v)); buf.writeBoolean(recipient != null); if (recipient != null) buf.writeUUID(recipient);
    }
    public static UnwakingHazard read(RegistryFriendlyByteBuf buf) {
        long id = buf.readLong(); Kind kind = buf.readEnum(Kind.class); long start = buf.readLong(); Vec3 origin = readVec(buf), axis = readVec(buf); double length = buf.readDouble(); int variant = buf.readVarInt();
        int count = buf.readVarInt(); if (count < 0 || count > 4) throw new IllegalArgumentException("Invalid opening count");
        java.util.ArrayList<Vec3> openings = new java.util.ArrayList<>(); for (int i = 0; i < count; i++) openings.add(readVec(buf));
        return new UnwakingHazard(id, kind, start, origin, axis, length, variant, openings, buf.readBoolean() ? buf.readUUID() : null);
    }
    private static void writeVec(RegistryFriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
}
