package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.*;

/** The sky finale uses identical world paths for collision, warnings and rendering. */
public final class UnwakingSkyGeometry {
    public static final double BLADE_HALF = 192, BLADE_RADIUS = 4.5, SUN_RADIUS = 12;
    public static final double RING_RADIUS = 48, RING_WIDTH = 5, RING_DEPTH = 4;
    public static final double FRAGMENT_RADIUS = 8, SHARD_RADIUS = 5;
    /** The tunnel obstacles. Small, because there are a great many of them and a gap to thread. */
    public static final double TUNNEL_RADIUS = 3;
    private UnwakingSkyGeometry() {}

    public static boolean attack(Kind kind) {
        return kind==Kind.FIRMAMENT_GUILLOTINE || kind==Kind.SIXFOLD_BURIAL || kind==Kind.NULL_HORIZON
                || kind==Kind.FIRMAMENT_FRAGMENT || kind==Kind.TUNNEL_SHARD;
    }
    /**
     * Fragments come in two sizes and a hazard has no size field, so the variant carries it - the
     * same trick {@link UnwakingOpenGeometry#openingRadius} already uses, and cheaper than widening
     * the record for one attack. The opening list is not available for this: it is capped at four
     * entries on read and already holds the fall's end point.
     */
    public static double fragmentRadius(UnwakingHazard h) { return h.variant()>=10?SHARD_RADIUS:FRAGMENT_RADIUS; }
    public static Vec3 center(UnwakingHazard h,double age) {
        return h.origin().lerp(h.openings().getFirst(),Mth.clamp((age-h.kind().impact)/(h.lastContactTick()-h.kind().impact),0,1));
    }
    public static boolean intersects(UnwakingHazard h,int age,AABB previous,AABB current) {
        if(h.contact(age)<0) return false;
        Vec3 motion=current.getCenter().subtract(previous.getCenter());
        // Both move linearly. Subtracting the hazard motion makes its volume stationary.
        Vec3 from=center(h,Math.max(h.kind().impact,age-1)), to=center(h,age);
        Vec3 relative=motion.subtract(to.subtract(from));
        int steps=Math.max(1,(int)Math.ceil(relative.length()/.15));
        for(int i=0;i<=steps;i++) {
            AABB box=previous.move(relative.scale((double)i/steps)).inflate(.075);
            if(contains(h,from,box)) return true;
        }
        return false;
    }
    private static boolean contains(UnwakingHazard h,Vec3 center,AABB box) {
        if(h.kind()==Kind.FIRMAMENT_GUILLOTINE)
            return capsule(box,center.subtract(h.right().scale(BLADE_HALF)),center.add(h.right().scale(BLADE_HALF)),BLADE_RADIUS);
        if(h.kind()==Kind.SIXFOLD_BURIAL) return distanceSqr(box,center)<=SUN_RADIUS*SUN_RADIUS;
        if(h.kind()==Kind.TUNNEL_SHARD) return distanceSqr(box,center)<=TUNNEL_RADIUS*TUNNEL_RADIUS;
        if(h.kind()==Kind.FIRMAMENT_FRAGMENT) {
            double radius=fragmentRadius(h);
            return distanceSqr(box,center)<=radius*radius;
        }
        Vec3 d=box.getCenter().subtract(center), flat=d.subtract(h.axis().scale(d.dot(h.axis())));
        double radius=flat.length(), margin=projectionRadius(box,unit(flat));
        return Math.abs(d.dot(h.axis()))<=RING_DEPTH+projectionRadius(box,h.axis())
                && radius+margin>=RING_RADIUS-RING_WIDTH && radius-margin<=RING_RADIUS+RING_WIDTH;
    }
    public static Vec3 source(UnwakingHazard h,int age,Vec3 eye) {
        Vec3 center=center(h,Math.max(h.kind().impact,age-1));
        if(h.kind()==Kind.FIRMAMENT_GUILLOTINE)
            center=center.add(h.right().scale(Mth.clamp(eye.subtract(center).dot(h.right()),-BLADE_HALF,BLADE_HALF)));
        if(h.kind()==Kind.NULL_HORIZON) {
            Vec3 delta=eye.subtract(center); delta=delta.subtract(h.axis().scale(delta.dot(h.axis())));
            center=center.add(unit(delta).scale(RING_RADIUS));
        }
        return center.subtract(h.axis().scale(8));
    }
}
