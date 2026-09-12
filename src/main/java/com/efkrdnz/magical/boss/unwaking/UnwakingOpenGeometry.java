package com.efkrdnz.magical.boss.unwaking;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.*;

/** Scalable analytic hazards; none depends on the shrine or the dimension spawn. */
public final class UnwakingOpenGeometry {
    public static final double CUT_SPAN = 512, HAND_REACH = 256, STAR_RADIUS = 40, OPENING_RADIUS = 7;
    public static double openingRadius(UnwakingHazard h) { return h.variant() >= 10 ? 12 : OPENING_RADIUS; }
    private UnwakingOpenGeometry() {}
    public static Vec3 planeCenter(UnwakingHazard h, double age) {
        return h.origin().add(h.axis().scale(-48 + Mth.clamp(age-40,0,36) * (96D/36)));
    }
    public static Vec3 starCenter(UnwakingHazard h, double age) { return h.origin().add(0,Math.max(0,120-age*2),0); }
    public static Vec3 hand(UnwakingHazard h, double age) {
        return rotate(h.axis(),h.planeNormal(),Mth.clamp(age-40,0,60)*Math.PI/60);
    }
    public static double frontRadius(double age, boolean earth) { return 8 + Mth.clamp(age-(earth?40:24),0,earth?48:40)*(earth?56D/48:2.2); }
    public static double lawOffset(double age) { return Mth.clamp(age-40,0,24)*20/24; }
    public static boolean opening(UnwakingHazard h, AABB box) {
        for (Vec3 point : h.openings()) {
            boolean inside = true;
            for (Vec3 corner : corners(box)) {
                Vec3 d = corner.subtract(point); d = d.subtract(h.axis().scale(d.dot(h.axis())));
                if (d.lengthSqr() > openingRadius(h)*openingRadius(h)) { inside=false; break; }
            }
            if (inside) return true;
        }
        return false;
    }
    public static boolean contains(UnwakingHazard h, double age, AABB box) {
        Vec3 c=h.origin(), a=h.axis(), d=box.getCenter().subtract(c);
        return switch(h.kind()) {
            case VORTEX_BEAM -> capsule(box,c,h.openings().getFirst(),UnwakingAssaultGeometry.BEAM_RADIUS);
            case PROCESSION_CUT, THRUST -> {
                double forward=d.dot(a), half=h.kind()==Kind.THRUST?24:36;
                double width=h.variant()%2==0?2:3, height=h.variant()%2==0?3:12;
                yield forward>=-projectionRadius(box,a) && forward<=half*2+projectionRadius(box,a)
                        && Math.abs(d.dot(h.right()))<=width+projectionRadius(box,h.right())
                        && Math.abs(d.dot(h.up()))<=height+projectionRadius(box,h.up());
            }
            case PALM -> distanceSqr(box,c.add(a.scale(6)))<=16;
            case ECHO -> distanceSqr(box,c)<=36;
            case WORLD_CUT -> {
                Vec3 delta=box.getCenter().subtract(planeCenter(h,age));
                yield Math.abs(delta.dot(a))<=2+projectionRadius(box,a)
                        && Math.abs(delta.dot(h.right()))<=256+projectionRadius(box,h.right())
                        && Math.abs(delta.dot(h.up()))<=256+projectionRadius(box,h.up()) && !opening(h,box);
            }
            case HORIZON_HAND -> capsule(box,c.add(hand(h,age).scale(8)),c.add(hand(h,age).scale(HAND_REACH)),3);
            case LAW_FRONT -> d.dot(a)-projectionRadius(box,a)<=lawOffset(age);
            case FALLEN_STAR -> distanceSqr(box,starCenter(h,age))<=STAR_RADIUS*STAR_RADIUS;
            case EARTH_FRONT, STAR_FRONT -> {
                boolean earth=h.kind()==Kind.EARTH_FRONT;
                double radius=frontRadius(age,earth);
                Vec3 flat=earth?new Vec3(d.x,0,d.z):d;
                boolean gap = h.openings().stream().anyMatch(v -> java.util.Arrays.stream(corners(box)).allMatch(corner -> {
                    Vec3 relative = corner.subtract(c);
                    if (earth) relative = new Vec3(relative.x,0,relative.z);
                    return relative.lengthSqr() > 1 && unit(relative).dot(v) > Math.cos(Math.toRadians(25));
                }));
                double margin=Math.max(box.getXsize(),box.getYsize())/2+2;
                yield !gap && Math.abs(flat.length()-radius)<=margin && (!earth || Math.abs(d.y)<=5+box.getYsize()/2);
            }
            case DECREE -> true;
            default -> false;
        };
    }
}
