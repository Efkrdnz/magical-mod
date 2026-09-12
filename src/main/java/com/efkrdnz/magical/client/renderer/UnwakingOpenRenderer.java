package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.boss.unwaking.*;
import com.efkrdnz.magical.client.ChronosClientEnvironment;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.*;

/**
 * The large analytic volumes: world cuts, law fronts, earth fronts, the falling bodies' lit cores.
 *
 * <p>Flat untextured quads are honest for a five-hundred-block plane, which is why these stayed
 * here. The thrown attacks moved to {@link UnwakingFxRenderer} and real shaders, because flat quads
 * are not honest for a projectile.
 */
public final class UnwakingOpenRenderer {
    private UnwakingOpenRenderer() {}
    /** Stable camera-relative background; attack readability does not depend on a noise shader. */
    public static void sky(PoseStack pose, MultiBufferSource.BufferSource buffers, float radius, float inversion) {
        VertexConsumer out=buffers.getBuffer(MagicalRenderTypes.unwakingSurface());
        int color=ARGB.lerp(Math.clamp(inversion,0,1),0xFF030407,0xFFF2F2F4);
        Vec3 x=new Vec3(radius,0,0),y=new Vec3(0,radius,0),z=new Vec3(0,0,radius);
        for(int sign:new int[]{-1,1}) {
            face(pose,out,x.scale(sign),y,z,color);
            face(pose,out,y.scale(sign),x,z,color);
            face(pose,out,z.scale(sign),x,y,color);
        }
        buffers.endBatch(MagicalRenderTypes.unwakingSurface());
    }
    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, UnwakingHazard h, double age, Vec3 eye, boolean reduced) {
        if(!h.visible((int)age)) return;
        VertexConsumer out=buffers.getBuffer(MagicalRenderTypes.unwakingSurface());
        float fade=(float)Math.min(1,(h.kind().end-age)/8);
        boolean warning=age<h.kind().impact;
        float inverted=Math.max(0,ChronosClientEnvironment.inversionMix());
        int ink=ARGB.lerp(inverted,0xFFE9E7E2,0xFF161821);
        int tint=alpha(ink,(warning?0.18F:0.60F)*fade*(reduced?0.65F:1));
        Vec3 c=h.origin(),a=h.axis(),r=h.right(),u=h.up();
        switch(h.kind()) {
            case SIXFOLD_BURIAL -> {
                Vec3 center=UnwakingSkyGeometry.center(h,age);
                if(warning) {
                    ring(pose,out,center,r,u,UnwakingSkyGeometry.SUN_RADIUS,.3,alpha(ink,.8F));
                    beam(pose,out,c,h.openings().getFirst(),.1,alpha(ink,.35F));
                } else {
                    float opacity=eye.distanceTo(center)<UnwakingSkyGeometry.SUN_RADIUS+1?.1F:.85F;
                    sphere(pose,out,center,UnwakingSkyGeometry.SUN_RADIUS,alpha(ink,opacity*fade),reduced?12:20);
                }
            }
            case NULL_HORIZON -> {
                Vec3 center=UnwakingSkyGeometry.center(h,age);
                double inner=UnwakingSkyGeometry.RING_RADIUS-UnwakingSkyGeometry.RING_WIDTH;
                double outer=UnwakingSkyGeometry.RING_RADIUS+UnwakingSkyGeometry.RING_WIDTH;
                Vec3 depth=a.scale(UnwakingSkyGeometry.RING_DEPTH);
                for(int i=0;i<96;i++) {
                    double t=i*Math.PI/48,n=(i+1)*Math.PI/48;
                    Vec3 left=r.scale(Math.cos(t)).add(u.scale(Math.sin(t))),right=r.scale(Math.cos(n)).add(u.scale(Math.sin(n)));
                    Vec3 li=center.add(left.scale(inner)),lo=center.add(left.scale(outer));
                    Vec3 ri=center.add(right.scale(inner)),ro=center.add(right.scale(outer));
                    for(int sign:new int[]{-1,1}) quad(pose,out,li.add(depth.scale(sign)),lo.add(depth.scale(sign)),ro.add(depth.scale(sign)),ri.add(depth.scale(sign)),tint);
                    quad(pose,out,li.subtract(depth),li.add(depth),ri.add(depth),ri.subtract(depth),tint);
                    quad(pose,out,lo.subtract(depth),lo.add(depth),ro.add(depth),ro.subtract(depth),tint);
                }
                ring(pose,out,center.subtract(depth),r,u,inner-.3,.25,0xFF69B8A1);
            }
            case VORTEX_BEAM -> {
                Vec3 end=h.openings().getFirst(), target=h.openings().get(1);
                double radius=UnwakingAssaultGeometry.BEAM_RADIUS;
                if(warning) {
                    beam(pose,out,c,end,.10,alpha(ink,.35F));
                    ring(pose,out,target,r,u,radius,.12,alpha(ink,.85F));
                } else {
                    // A real twelve-sided tube with the same radius and endpoints as server
                    // collision. This one was always the best-looking attack the boss had; it does
                    // not want a filament shader over it, which would only be two crossed quads.
                    boolean inside=capsule(new net.minecraft.world.phys.AABB(eye,eye),c,end,radius+.5);
                    float opacity=inside?.08F:.85F;
                    for(int i=0;i<12;i++) {
                        double angle=i*Math.PI/6, next=(i+1)*Math.PI/6;
                        Vec3 left=r.scale(Math.cos(angle)*radius).add(u.scale(Math.sin(angle)*radius));
                        Vec3 right=r.scale(Math.cos(next)*radius).add(u.scale(Math.sin(next)*radius));
                        quad(pose,out,c.add(left),end.add(left),end.add(right),c.add(right),alpha(ink,opacity*fade*(reduced?.7F:1)));
                    }
                }
            }
            case WORLD_CUT -> {
                Vec3 center=UnwakingOpenGeometry.planeCenter(h,age);
                Vec3 gap=h.openings().isEmpty()?c:h.openings().getFirst();
                Vec3 projected=gap.add(a.scale(center.subtract(gap).dot(a)));
                double radius=UnwakingOpenGeometry.openingRadius(h)-.2;
                perforatedPlane(pose,out,center,r,u,projected,256,radius,tint);
                ring(pose,out,projected,r,u,radius,0.3,0xFF69B8A1);
            }
            case TUNNEL_SHARD -> {
                Vec3 center=UnwakingSkyGeometry.center(h,age);
                double radius=UnwakingSkyGeometry.TUNNEL_RADIUS;
                // Coloured, unlike everything else the boss throws. The tunnel is the one place the
                // fight leaves its own palette, and an obstacle that came down it bone-white would
                // read as belonging to the boss rather than to the shaft it arrived in.
                int hue=UnwakingSceneryRenderer.tunnelHue(h.variant(),age);
                if(warning) ring(pose,out,center,r,u,radius,.25,alpha(hue,.75F));
                else sphere(pose,out,center,radius,alpha(hue,.9F*fade),reduced?8:14);
            }
            case FIRMAMENT_FRAGMENT -> {
                Vec3 center=UnwakingSkyGeometry.center(h,age);
                double radius=UnwakingSkyGeometry.fragmentRadius(h);
                if(warning) {
                    // Warned at its true size, the way the converging suns are - a dimmer copy of
                    // the same silhouette is exactly what made the old volleys unreadable. Until it
                    // falls the body sits in the wound, which is where the ring draws.
                    ring(pose,out,center,r,u,radius,.3,alpha(ink,.8F));
                    beam(pose,out,c,h.openings().getFirst(),.1,alpha(ink,.35F));
                } else {
                    float opacity=eye.distanceTo(center)<radius+1?.1F:.85F;
                    sphere(pose,out,center,radius,alpha(ink,opacity*fade),reduced?12:20);
                }
            }
            case RETURNING_VERDICT -> {
                double radius=UnwakingAssaultGeometry.VERDICT_RADIUS;
                if(age<20) {
                    ring(pose,out,c,r,u,radius,.3,alpha(ink,.8F));
                    beam(pose,out,c,h.openings().getFirst(),.10,alpha(ink,.35F));
                } else {
                    Vec3 tip=UnwakingAssaultGeometry.position(h,age);
                    float opacity=eye.distanceTo(tip)<radius+1?.1F:.9F;
                    sphere(pose,out,tip,radius,alpha(ink,opacity*fade),reduced?12:18);
                    // The hold is the tell that it is coming back. Outside the body so it reads.
                    if(age>=52&&age<64) ring(pose,out,tip,r,u,radius+2.5,.18,0xFF69B8A1);
                }
            }
            case LAW_FRONT -> {
                Vec3 center=c.add(a.scale(UnwakingOpenGeometry.lawOffset(age)));
                // Follow the viewer tangentially: this is the same infinite mathematical plane.
                Vec3 offset=eye.subtract(center); center=center.add(offset.subtract(a.scale(offset.dot(a))));
                face(pose,out,center,r.scale(256),u.scale(256),alpha(ink,warning?0.08F:0.2F));
                ring(pose,out,c.add(a.scale(24)),r,u,3,0.3,0xFF69B8A1);
            }
            case HORIZON_HAND -> {
                Vec3 hand=UnwakingOpenGeometry.hand(h,age);
                Vec3 start=c.add(hand.scale(8)),end=c.add(hand.scale(256));
                beam(pose,out,start,end,3,tint);
                if(!reduced) ring(pose,out,c,h.axis(),h.planeNormal().cross(h.axis()),256,0.3,alpha(ink,0.12F));
            }
            case FALLEN_STAR -> {
                sphere(pose,out,UnwakingOpenGeometry.starCenter(h,age),40,alpha(ink,0.9F*fade),reduced?12:24);
                ring(pose,out,c,new Vec3(1,0,0),new Vec3(0,0,1),40,0.35,alpha(ink,0.5F));
            }
            case EARTH_FRONT -> {
                double radius=UnwakingOpenGeometry.frontRadius(age,true);
                for(int i=0;i<128;i++) {
                    Vec3 d=radial(i*Math.PI/64),next=radial((i+1)*Math.PI/64);
                    if(gap(h,d)&&gap(h,next)) continue;
                    Vec3 left=c.add(d.scale(radius)),right=c.add(next.scale(radius));
                    quad(pose,out,left.add(0,-5,0),left.add(0,5,0),right.add(0,5,0),right.add(0,-5,0),tint);
                }
            }
            case STAR_FRONT -> shell(pose,out,h,UnwakingOpenGeometry.frontRadius(age,false),tint,reduced?16:32);
            case PROCESSION_CUT, THRUST -> {
                double length=h.kind()==Kind.THRUST?48:72;
                Vec3 middle=c.add(a.scale(length/2));
                double height=h.variant()%2==0?3:12,width=h.variant()%2==0?2:3;
                // A single solid blade previews all three damaging dimensions.
                Vec3 longAxis=a.scale(length/2), widthAxis=r.scale(width), heightAxis=u.scale(height);
                for(int sign:new int[]{-1,1}) {
                    face(pose,out,middle.add(widthAxis.scale(sign)),longAxis,heightAxis,tint);
                    face(pose,out,middle.add(heightAxis.scale(sign)),longAxis,widthAxis,tint);
                }
            }
            case PALM -> ring(pose,out,c.add(a.scale(6)),r,u,4,warning?0.18:0.8,tint);
            case ECHO -> sphere(pose,out,c,6,tint,16);
            case DECREE -> ring(pose,out,eye.add(a.scale(12)),r,u,0.6+Math.max(0,32-age)/16,0.15,alpha(ink,0.9F));
            case ARRIVAL -> {
                face(pose,out,c.add(0,0.9,0),new Vec3(0.3,0,0),new Vec3(0,0.9,0),alpha(ink,0.65F));
                ring(pose,out,c,new Vec3(1,0,0),new Vec3(0,0,1),1.2,0.1,alpha(ink,0.7F));
            }
            default -> {}
        }
    }

    public static void awakening(PoseStack pose,MultiBufferSource.BufferSource buffers,Vec3 eye,long age) {
        if(age<30||age>160) return;
        float level=UnwakingPresentation.smooth((age-30)/50F)*UnwakingPresentation.smooth((160-age)/20F);
        VertexConsumer out=buffers.getBuffer(MagicalRenderTypes.chronoRift());
        Vec3 c=eye.add(0,55,-100),r=new Vec3(75,0,0),u=new Vec3(0,95,0);
        textured(pose,out,c.subtract(r).subtract(u),0,0,alpha(0xFFFFFFFF,level));
        textured(pose,out,c.subtract(r).add(u),0,1,alpha(0xFFFFFFFF,level));
        textured(pose,out,c.add(r).add(u),1,1,alpha(0xFFFFFFFF,level));
        textured(pose,out,c.add(r).subtract(u),1,0,alpha(0xFFFFFFFF,level));
        buffers.endBatch(MagicalRenderTypes.chronoRift());
    }

    private static void perforatedPlane(PoseStack pose,VertexConsumer out,Vec3 center,Vec3 r,Vec3 u,Vec3 hole,double half,double radius,int color) {
        Vec3 offset=hole.subtract(center); double x=offset.dot(r),y=offset.dot(u);
        for(int i=0;i<96;i++) {
            double t=i*Math.PI/48,n=(i+1)*Math.PI/48;
            Vec3 inner=hole.add(r.scale(Math.cos(t)*radius)).add(u.scale(Math.sin(t)*radius));
            Vec3 next=hole.add(r.scale(Math.cos(n)*radius)).add(u.scale(Math.sin(n)*radius));
            quad(pose,out,inner,edge(center,r,u,x,y,t,half),edge(center,r,u,x,y,n,half),next,color);
        }
    }
    private static Vec3 edge(Vec3 center,Vec3 r,Vec3 u,double x,double y,double t,double half) {
        double dx=Math.cos(t),dy=Math.sin(t),tx=Math.abs(dx)<1e-8?Double.POSITIVE_INFINITY:((dx>0?half:-half)-x)/dx;
        double ty=Math.abs(dy)<1e-8?Double.POSITIVE_INFINITY:((dy>0?half:-half)-y)/dy,d=Math.min(tx,ty);
        return center.add(r.scale(x+dx*d)).add(u.scale(y+dy*d));
    }
    private static Vec3 radial(double t) { return new Vec3(Math.cos(t),0,Math.sin(t)); }
    private static boolean gap(UnwakingHazard h,Vec3 d) { return h.openings().stream().anyMatch(v->v.dot(d)>Math.cos(Math.toRadians(25))); }
    private static void shell(PoseStack pose,VertexConsumer out,UnwakingHazard h,double radius,int color,int count) {
        for(int lat=0;lat<count/2;lat++) for(int lon=0;lon<count;lon++) {
            Vec3 a=direction(lat,lon,count),b=direction(lat+1,lon,count),c=direction(lat+1,lon+1,count),d=direction(lat,lon+1,count);
            if(gap(h,a)&&gap(h,b)&&gap(h,c)&&gap(h,d)) continue;
            quad(pose,out,h.origin().add(a.scale(radius)),h.origin().add(b.scale(radius)),h.origin().add(c.scale(radius)),h.origin().add(d.scale(radius)),color);
        }
    }
    private static Vec3 direction(int lat,int lon,int count) {
        double y=-Math.PI/2+lat*Math.PI/(count/2),t=lon*Math.PI*2/count;
        return new Vec3(Math.cos(y)*Math.cos(t),Math.sin(y),Math.cos(y)*Math.sin(t));
    }
    private static void sphere(PoseStack pose,VertexConsumer out,Vec3 center,double radius,int color,int count) {
        for(int lat=0;lat<count/2;lat++) for(int lon=0;lon<count;lon++) {
            Vec3 a=direction(lat,lon,count),b=direction(lat+1,lon,count),c=direction(lat+1,lon+1,count),d=direction(lat,lon+1,count);
            float shade=0.6F+0.4F*(float)(a.y+1)/2;
            int shaded=ARGB.color(ARGB.alpha(color),Math.round(ARGB.red(color)*shade),Math.round(ARGB.green(color)*shade),Math.round(ARGB.blue(color)*shade));
            quad(pose,out,center.add(a.scale(radius)),center.add(b.scale(radius)),center.add(c.scale(radius)),center.add(d.scale(radius)),shaded);
        }
    }
    private static void ring(PoseStack pose,VertexConsumer out,Vec3 c,Vec3 r,Vec3 u,double radius,double width,int color) {
        for(int i=0;i<96;i++) {
            double t=i*Math.PI/48,n=(i+1)*Math.PI/48;
            Vec3 a=r.scale(Math.cos(t)).add(u.scale(Math.sin(t))),b=r.scale(Math.cos(n)).add(u.scale(Math.sin(n)));
            quad(pose,out,c.add(a.scale(radius-width)),c.add(a.scale(radius+width)),c.add(b.scale(radius+width)),c.add(b.scale(radius-width)),color);
        }
    }
    private static void beam(PoseStack pose,VertexConsumer out,Vec3 from,Vec3 to,double radius,int color) {
        Vec3 a=unit(to.subtract(from)),r=unit(a.cross(Math.abs(a.y)>0.95?new Vec3(1,0,0):new Vec3(0,1,0))).scale(radius),c=from.add(to).scale(0.5),half=to.subtract(from).scale(0.5);
        face(pose,out,c,r,half,color); face(pose,out,c,a.cross(r),half,color);
    }
    private static void face(PoseStack pose,VertexConsumer out,Vec3 c,Vec3 r,Vec3 u,int color) { quad(pose,out,c.subtract(r).subtract(u),c.subtract(r).add(u),c.add(r).add(u),c.add(r).subtract(u),color); }
    private static void quad(PoseStack pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color) { vertex(pose,out,a,color);vertex(pose,out,b,color);vertex(pose,out,c,color);vertex(pose,out,d,color); }
    private static void vertex(PoseStack pose,VertexConsumer out,Vec3 p,int color) { out.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setColor(color); }
    private static void textured(PoseStack pose,VertexConsumer out,Vec3 p,float u,float v,int color) { out.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(color); }
    private static int alpha(int color,float alpha) { return ARGB.color(Math.clamp(Math.round(alpha*255),0,255),color); }
}
