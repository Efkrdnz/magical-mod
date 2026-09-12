package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.boss.unwaking.UnwakingEncounterService;
import com.efkrdnz.magical.boss.unwaking.UnwakingGesture;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultGeometry;
import com.efkrdnz.magical.boss.unwaking.UnwakingHazard;
import com.efkrdnz.magical.boss.unwaking.UnwakingSkyGeometry;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Vanilla line rendering is deliberately sufficient to read every lethal volume - all of them. */
public final class UnwakingHazardRenderer {
    private static final int GOLD = 0xFFFFD166, VIOLET = 0xFFC997FF, SAFE = 0xFF8CFFE4, WHITE = 0xFFFFF4DB;
    private UnwakingHazardRenderer() {}
    public static void render(PoseStack pose, VertexConsumer out, UnwakingHazard h, double age, Vec3 player) {
        if (age < 0 || age >= h.kind().end) return;
        boolean broken = h.kind().defense == UnwakingHazard.Defense.MOVE;
        int color = broken ? VIOLET : GOLD;
        if (h.recovering((int) age)) color = SAFE;
        float inversion = Math.max(0,com.efkrdnz.magical.client.ChronosClientEnvironment.inversionMix());
        color = net.minecraft.util.ARGB.lerp(inversion,color,broken ? 0xFF512470 : 0xFF725013);
        Vec3 c = h.origin(), a = h.axis();
        switch (h.kind()) {
            case WORLD_CUT -> {
                Vec3 center = com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry.planeCenter(h, age);
                for (int sign : new int[] {-1,1}) {
                    Vec3 face = center.add(a.scale(sign * 2));
                    Vec3 r = h.right().scale(256), u = h.up().scale(256);
                    line(pose,out,face.add(r).add(u),face.add(r).subtract(u),color,false);
                    line(pose,out,face.subtract(r).add(u),face.subtract(r).subtract(u),color,false);
                    line(pose,out,face.add(r).add(u),face.subtract(r).add(u),color,false);
                    line(pose,out,face.add(r).subtract(u),face.subtract(r).subtract(u),color,false);
                }
                for (Vec3 gap : h.openings()) circle(pose,out,gap.add(a.scale(center.subtract(gap).dot(a))),a,7,SAFE,false);
            }
            case HORIZON_HAND -> {
                Vec3 d = com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry.hand(h,age);
                tube(pose,out,c.add(d.scale(8)),c.add(d.scale(256)),3,color,false);
            }
            case FALLEN_STAR -> sphere(pose,out,com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry.starCenter(h,age),40,color,false);
            case LAW_FRONT -> {
                Vec3 plane = c.add(a.scale(com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry.lawOffset(age)));
                line(pose,out,plane.add(h.right().scale(-256)),plane.add(h.right().scale(256)),color,false);
                line(pose,out,plane.add(h.up().scale(-256)),plane.add(h.up().scale(256)),color,false);
                arrow(pose,out,plane,plane.add(a.scale(20)),SAFE);
            }
            case EARTH_FRONT, STAR_FRONT -> {
                double radius = com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry.frontRadius(age,h.kind()==UnwakingHazard.Kind.EARTH_FRONT);
                circle(pose,out,c,new Vec3(0,1,0),radius,color,true);
                for (Vec3 gap : h.openings()) arrow(pose,out,c.add(gap.scale(8)),c.add(gap.scale(radius)),SAFE);
            }
            case PROCESSION_CUT, THRUST -> {
                Vec3 r=h.right().scale(h.variant()%2==0?2:3), u=h.up().scale(h.variant()%2==0?3:12);
                Vec3 end=c.add(a.scale(h.kind()==UnwakingHazard.Kind.THRUST?48:72));
                for (Vec3 v : java.util.List.of(r.add(u),r.subtract(u),r.scale(-1).add(u),r.scale(-1).subtract(u))) line(pose,out,c.add(v),end.add(v),color,false);
            }
            case PALM -> sphere(pose,out,c.add(a.scale(6)),4,color,false);
            case ECHO -> sphere(pose,out,c,6,color,false);
            case DECREE -> arrow(pose,out,player,player.add(a.scale(12)),color);
            case ARRIVAL -> circle(pose,out,c,new Vec3(0,1,0),1,color,false);

            // The thrown attacks. These had no cases at all, so `/unwaking visuals hitboxes true`
            // drew nothing for exactly the attacks whose drawn size is supposed to be their
            // collision size - which is the one claim this debug view exists to check.
            case FIRMAMENT_FRAGMENT, SIXFOLD_BURIAL -> {
                double radius=h.kind()==Kind.SIXFOLD_BURIAL?UnwakingSkyGeometry.SUN_RADIUS:UnwakingSkyGeometry.fragmentRadius(h);
                sphere(pose,out,UnwakingSkyGeometry.center(h,age),radius,color,false);
                arrow(pose,out,c,h.openings().getFirst(),SAFE);
            }
            case RETURNING_VERDICT -> {
                sphere(pose,out,UnwakingAssaultGeometry.position(h,Math.max(20,age)),UnwakingAssaultGeometry.VERDICT_RADIUS,color,false);
                arrow(pose,out,c,h.openings().getFirst(),SAFE);
            }
            case TUNNEL_SHARD -> sphere(pose,out,UnwakingSkyGeometry.center(h,age),UnwakingSkyGeometry.TUNNEL_RADIUS,color,false);
            case VORTEX_BEAM -> tube(pose,out,c,h.openings().getFirst(),UnwakingAssaultGeometry.BEAM_RADIUS,color,false);
            // Hitscan: there is no volume to outline, only the line it will take.
            case CLOCK_STRIKE -> arrow(pose,out,c,player,color);
            case FIRMAMENT_GUILLOTINE -> {
                Vec3 center=UnwakingSkyGeometry.center(h,age), span=h.right().scale(UnwakingSkyGeometry.BLADE_HALF);
                tube(pose,out,center.subtract(span),center.add(span),UnwakingSkyGeometry.BLADE_RADIUS,color,false);
            }
            case NULL_HORIZON -> {
                Vec3 center=UnwakingSkyGeometry.center(h,age);
                circle(pose,out,center,a,UnwakingSkyGeometry.RING_RADIUS-UnwakingSkyGeometry.RING_WIDTH,color,false);
                circle(pose,out,center,a,UnwakingSkyGeometry.RING_RADIUS+UnwakingSkyGeometry.RING_WIDTH,color,false);
                circle(pose,out,center,a,2,SAFE,false);
            }

            case HAND -> {
                Vec3 normal = h.planeNormal();
                // Two boundary arcs preview precisely the swept capsule sector, not a filled disk.
                for (int i = 0; i < 36; i++) {
                    Vec3 d1 = UnwakingHazard.rotate(a, normal, i * Math.PI / 36), d2 = UnwakingHazard.rotate(a, normal, (i + 1) * Math.PI / 36);
                    line(pose,out,c.add(d1.scale(HAND_OUTER)),c.add(d2.scale(HAND_OUTER)),color,false);
                    line(pose,out,c.add(d1.scale(HAND_INNER)),c.add(d2.scale(HAND_INNER)),SAFE,false);
                }
                tube(pose,out,c.add(h.hand(age).scale(HAND_INNER)),c.add(h.hand(age).scale(HAND_OUTER)),HAND_RADIUS+0.5,color,false);
                sphere(pose,out,c.add(h.hand(age).scale(HAND_INNER)),HAND_RADIUS+0.5,color,false);
                sphere(pose,out,c.add(h.hand(age).scale(HAND_OUTER)),HAND_RADIUS+0.5,color,false);
                circle(pose,out,c,normal,2,SAFE,false);
                arrow(pose,out,c.add(h.hand(age).scale(22)),c.add(h.hand(age).scale(22)).add(normal.scale(5)),SAFE);
            }
            case CONSTELLATION -> {
                // Remove the entire exit face from the outline; show the actual inset corridor.
                box(pose,out,c,CAGE_HALF+0.5,color,true,a);
                Vec3 exit = c.add(a.scale(CAGE_HALF)), r = h.right().scale(CORRIDOR_HALF), u = h.up().scale(CORRIDOR_HALF);
                for (int i = 0; i < 4; i++) {
                    Vec3 v = i == 0 ? r.add(u) : i == 1 ? r.subtract(u) : i == 2 ? r.scale(-1).subtract(u) : r.scale(-1).add(u);
                    Vec3 w = i == 0 ? r.subtract(u) : i == 1 ? r.scale(-1).subtract(u) : i == 2 ? r.scale(-1).add(u) : r.add(u);
                    line(pose,out,exit.add(v),exit.add(w),SAFE,false);
                    line(pose,out,c.add(a.scale(CORRIDOR_START)).add(v),exit.add(v),SAFE,false);
                }
                arrow(pose,out,c,exit.add(a.scale(3)),SAFE);
                // Interior hatch avoids presenting the cube center as a safe pocket.
                for (int i = -3; i <= 3; i += 2) line(pose,out,c.add(h.right().scale(i)).add(h.up().scale(-4.5)),c.add(h.right().scale(i)).add(h.up().scale(4.5)),color,true);
            }
            case RETURNING -> {
                Vec3 end = c.add(a.scale(h.length()));
                tube(pose,out,c,end,SCAR_RADIUS+0.5,VIOLET,true);
                arrow(pose,out,age < 60 ? c : end,age < 60 ? end : c,color);
                if (age >= 40 && age <= 52 || age >= 84 && age <= 96) {
                    boolean back = age >= 84; double t = Mth.clamp((age - (back ? 84 : 40)) / 12,0,1);
                    double tip = h.length() * (back ? 1 - t : t), tail = Mth.clamp(tip + (back ? 8 : -8),0,h.length());
                    tube(pose,out,c.add(a.scale(tail)),c.add(a.scale(tip)),SCAR_RADIUS+0.5,WHITE,false);
                    sphere(pose,out,c.add(a.scale(tip)),SCAR_RADIUS+0.5,WHITE,false);
                }
            }
            case FOLD -> {
                tube(pose,out,c.add(a.scale(-FOLD_HALF-0.5)),c.add(a.scale(FOLD_HALF+0.5)),FOLD_RADIUS+0.5,color,true);
                arrow(pose,out,c,c.add(h.up().scale(5)),SAFE);
                arrow(pose,out,c,c.add(h.right().scale(5)),SAFE);
            }
            case SHELL -> {
                double radius = 52 - Mth.clamp(age - 40,0,24) * 2;
                // Broken great-circle segments are omitted where a full player fits the cone.
                for (Vec3 normal : UnwakingHazard.CARDINALS.subList(0,5)) for (int i = 0; i < 96; i++) {
                    Vec3 basis = basis(normal), cross = normal.cross(basis);
                    Vec3 v = basis.scale(Math.cos(i*Math.PI/48)).add(cross.scale(Math.sin(i*Math.PI/48)));
                    Vec3 w = basis.scale(Math.cos((i+1)*Math.PI/48)).add(cross.scale(Math.sin((i+1)*Math.PI/48)));
                    Vec3 point = c.add(v.scale(radius));
                    if (!h.safeOpening(new AABB(point,point).inflate(0.9))) line(pose,out,point,c.add(w.scale(radius)),color,true);
                }
                for (Vec3 opening : h.openings()) {
                    double r = radius * Math.sin(Math.toRadians(20));
                    circle(pose,out,c.add(opening.scale(radius * Math.cos(Math.toRadians(20)))),opening,r,SAFE,false);
                    arrow(pose,out,c.add(opening.scale(7)),c.add(opening.scale(13)),SAFE);
                }
                if (h.variant() > 0) sphere(pose,out,c,10.5,color,true);
            }
            case PRESSURE -> {
                for (int i = 0; i < 3; i++) sphere(pose,out,c,Math.max(3,48 - (age / 40 * 48 + i * 12) % 48),WHITE,false);
            }
            case FRACTURE -> {
                Vec3 r = h.right(), u = h.up();
                for (int side : new int[] {-1,1}) {
                    Vec3 plane = c.add(a.scale(side * (SLAB_HALF+0.5)));
                    for (int offset = -48; offset <= 48; offset += 8) {
                        line(pose,out,plane.add(r.scale(offset)).add(u.scale(-48)),plane.add(r.scale(offset)).add(u.scale(48)),color,true);
                        line(pose,out,plane.add(u.scale(offset)).add(r.scale(-48)),plane.add(u.scale(offset)).add(r.scale(48)),color,true);
                    }
                    arrow(pose,out,c,c.add(a.scale(side*10)),SAFE);
                }
            }
            case ATTENTION -> {
                // Targeted pulse direction moves with its recipient; it is never presented as an escapable projectile.
                Vec3 source = player.add(a.scale(12));
                sphere(pose,out,source,0.7,WHITE,false);
                circle(pose,out,source,a,0.8 + Math.max(0,36-age)/12,color,false);
            }
            case GESTURE -> {
                gesture(pose,out,new UnwakingGesture(c,a),color);
                if (h.variant() != 1 && age >= 32) gesture(pose,out,new UnwakingGesture(c,h.right()),color);
            }
            case SKY_REND -> {
                Vec3 normal = age < 64 ? a : h.right();
                Vec3 across = UnwakingHazard.unit(normal.cross(new Vec3(0,1,0)));
                for (int side : new int[] {-1,1}) {
                    Vec3 plane = c.add(normal.scale(side*(REND_HALF+0.5)));
                    for (int i=-48;i<=48;i+=12) line(pose,out,plane.add(across.scale(i)).add(0,-56,0),plane.add(across.scale(i)).add(0,56,0),color,false);
                    arrow(pose,out,c,c.add(normal.scale(side*18)),SAFE);
                }
            }
            case INVERSION -> {
                for (Vec3 refuge : h.openings()) {
                    sphere(pose,out,refuge,REFUGE_RADIUS,SAFE,false);
                    arrow(pose,out,player,refuge,SAFE);
                }
            }
            case STARFALL -> tube(pose,out,c.add(0,-64,0),c.add(0,64,0),STAR_RADIUS+0.5,color,true);
            case MEMORY -> sphere(pose,out,c,MEMORY_RADIUS+0.5,color,true);
            case REFUSAL -> {
                circle(pose,out,c.add(a.scale(0.5)),a,1.5,color,false);
                if (h.variant() == 2) sphere(pose,out,c,1,SAFE,false);
            }
        }
    }
    public static void boundary(PoseStack pose, VertexConsumer out) { sphere(pose,out,UnwakingEncounterService.CENTER,42,SAFE,true); }
    public static void domain(PoseStack pose, VertexConsumer out, boolean eye, int fractures) {
        Vec3 center = UnwakingEncounterService.CENTER;
        if (eye) {
            Vec3 c = center.add(0,28,-65);
            for (int i=0;i<64;i++) {
                double t=i*Math.PI/32, next=(i+1)*Math.PI/32;
                line(pose,out,c.add(Math.cos(t)*22,Math.sin(t)*9,0),c.add(Math.cos(next)*22,Math.sin(next)*9,0),WHITE,false);
            }
            circle(pose,out,c,new Vec3(0,0,1),4,GOLD,false);
        }
        for (int i=0;i<fractures;i++) {
            double angle=i*Math.PI/3;
            Vec3 c=center.add(Math.cos(angle)*35,-59.5,Math.sin(angle)*35);
            Vec3 a=new Vec3(Math.cos(angle),0,Math.sin(angle));
            line(pose,out,c.add(a.scale(-5)),c.add(0,0,2),SAFE,false);
            line(pose,out,c.add(0,0,2),c.add(a.scale(5)),SAFE,false);
        }
    }
    public static void gesture(PoseStack pose, VertexConsumer out, UnwakingGesture g, int color) {
        Vec3[] corners = new Vec3[8];
        for(int i=0;i<8;i++) corners[i]=g.world((i&1)==0?-1.25:1.25,(i&2)==0?-2.5:2.5,(i&4)==0?-0.5:14.5);
        edges(pose,out,corners,color,false,null,Vec3.ZERO);
    }
    private static void box(PoseStack pose,VertexConsumer out,Vec3 c,double r,int color,boolean broken,Vec3 open) { edges(pose,out,UnwakingHazard.corners(new AABB(c,c).inflate(r)),color,broken,open,c); }
    private static void edges(PoseStack pose,VertexConsumer out,Vec3[] points,int color,boolean broken,Vec3 open,Vec3 c) {
        for(int i=0;i<8;i++) for(int b=1;b<=4;b<<=1) if((i&b)==0) {
            if(open != null && points[i].subtract(c).dot(open)>0 && points[i|b].subtract(c).dot(open)>0) continue;
            line(pose,out,points[i],points[i|b],color,broken);
        }
    }
    private static Vec3 basis(Vec3 normal) { return UnwakingHazard.unit(normal.cross(Math.abs(normal.y)>0.95?new Vec3(1,0,0):new Vec3(0,1,0))); }
    private static void tube(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,double radius,int color,boolean broken) {
        Vec3 normal=UnwakingHazard.unit(b.subtract(a)),r=basis(normal),u=normal.cross(r);
        circle(pose,out,a,normal,radius,color,broken); circle(pose,out,b,normal,radius,color,broken);
        for(int i=0;i<8;i++) { Vec3 offset=r.scale(Math.cos(i*Math.PI/4)*radius).add(u.scale(Math.sin(i*Math.PI/4)*radius)); line(pose,out,a.add(offset),b.add(offset),color,broken); }
    }
    private static void sphere(PoseStack pose,VertexConsumer out,Vec3 c,double r,int color,boolean broken) {
        circle(pose,out,c,new Vec3(1,0,0),r,color,broken); circle(pose,out,c,new Vec3(0,1,0),r,color,broken); circle(pose,out,c,new Vec3(0,0,1),r,color,broken);
    }
    public static void circle(PoseStack pose,VertexConsumer out,Vec3 c,Vec3 normal,double radius,int color,boolean broken) {
        Vec3 r=basis(normal),u=normal.cross(r);
        for(int i=0;i<64;i++) { double t=i*Math.PI/32,t2=(i+1)*Math.PI/32; line(pose,out,c.add(r.scale(Math.cos(t)*radius)).add(u.scale(Math.sin(t)*radius)),c.add(r.scale(Math.cos(t2)*radius)).add(u.scale(Math.sin(t2)*radius)),color,broken && i%2==0); }
    }
    private static void arrow(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,int color) {
        Vec3 back=UnwakingHazard.unit(a.subtract(b)),r=basis(back); line(pose,out,a,b,color,false);
        line(pose,out,b,b.add(back).add(r.scale(0.5)),color,false); line(pose,out,b,b.add(back).subtract(r.scale(0.5)),color,false);
    }
    private static void line(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,int color,boolean broken) {
        if (broken) { Vec3 delta=b.subtract(a); int count=Math.max(2,(int)Math.ceil(delta.length()/0.6)); for(int i=0;i<count;i+=2) segment(pose,out,a.add(delta.scale((double)i/count)),a.add(delta.scale((double)(i+1)/count)),color); }
        else segment(pose,out,a,b,color);
    }
    private static void segment(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,int color) {
        Vec3 normal=b.subtract(a).normalize(); Vector3f n=new Vector3f((float)normal.x,(float)normal.y,(float)normal.z);
        out.addVertex(pose.last(),(float)a.x,(float)a.y,(float)a.z).setColor(color).setNormal(pose.last(),n);
        out.addVertex(pose.last(),(float)b.x,(float)b.y,(float)b.z).setColor(color).setNormal(pose.last(),n);
    }
}
