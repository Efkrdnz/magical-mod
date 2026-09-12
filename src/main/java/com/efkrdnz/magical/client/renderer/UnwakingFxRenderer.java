package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultGeometry;
import com.efkrdnz.magical.boss.unwaking.UnwakingHazard;
import com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind;
import com.efkrdnz.magical.boss.unwaking.UnwakingSkyGeometry;
import com.efkrdnz.magical.client.ChronosClientEnvironment;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.TrailBuffer;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The thrown attacks, drawn on the mod's own FX shaders instead of flat untextured quads.
 *
 * <p>Everything the boss draws used to go through one render type - POSITION_COLOR, the vanilla
 * shader, no texture, no depth write - and through {@code UnwakingOpenRenderer.beam}, which is not
 * a tube but two flat rectangles crossed in an X. A volley of those reads as a muddy band rather
 * than as objects. The mod already ships real beam, orb and mark materials with packed per-vertex
 * parameters; this class is the boss finally using them.
 *
 * <p>Additive for the two falling bodies: the lit sphere stays, because it is the only surface in
 * the whole fight with luminance across it and that is exactly why the converging suns read as
 * solid, and the charge shell, the leading bloom and the trail go over it. Replacing for the beams
 * and the blade, whose flat geometry had nothing worth keeping.
 */
public final class UnwakingFxRenderer {
    private static final int INK = 0xFFE9E7E2, DARK_INK = 0xFF161821, SAFE = 0x69B8A1;

    private UnwakingFxRenderer() {}

    /**
     * Kinds whose whole body lives here now, so {@link UnwakingOpenRenderer} no longer draws them.
     *
     * <p>The vortex beam is deliberately not one of them. It already had a genuine twelve-sided
     * tube rather than crossed quads, and swapping that for a filament - two crossed quads with a
     * good shader on them - lost more silhouette than the material gained.
     */
    public static boolean replaces(Kind kind) {
        return kind==Kind.CLOCK_STRIKE || kind==Kind.FIRMAMENT_GUILLOTINE;
    }

    public static void render(PoseStack pose, MultiBufferSource.BufferSource buffers, UnwakingHazard h,
            double age, Vec3 camera, Quaternionf facing, float partialTick, boolean reduced) {
        if(!h.visible((int)age)) return;
        int ink=ARGB.lerp(Math.max(0,ChronosClientEnvironment.inversionMix()),INK,DARK_INK)&0xFFFFFF;
        float fade=(float)Math.min(1,(h.kind().end-age)/8);
        boolean warning=age<h.kind().impact;
        FxContext ctx=new FxContext(pose,buffers,partialTick,facing,camera)
                .timing((float)age,h.kind().end,MagicVertex.seedOf(h.id()));
        ctx.origin=h.origin();
        switch(h.kind()) {
            case FIRMAMENT_FRAGMENT -> falling(ctx,h,age,UnwakingSkyGeometry.fragmentRadius(h),ink,fade,warning,reduced);
            case RETURNING_VERDICT -> falling(ctx,h,age,UnwakingAssaultGeometry.VERDICT_RADIUS,ink,fade,age<20,reduced);
            case CLOCK_STRIKE -> clock(ctx,h,age,camera,ink);
            case FIRMAMENT_GUILLOTINE -> guillotine(ctx,h,age,ink,fade,warning);
            case NULL_HORIZON -> { if(warning) horizonWarning(ctx,h,age,fade); }
            default -> { return; }
        }
        flush(buffers);
    }

    /**
     * A body falling from the wound: a charge shell over the lit core, a bloom on the leading face,
     * and its last half-second of travel as a tapering ribbon. The trail is what makes a moving
     * thing read as moving, and it costs nothing to keep because the path is analytic - there is no
     * per-hazard history to store, only the same function evaluated at earlier ages.
     */
    private static void falling(FxContext ctx,UnwakingHazard h,double age,double radius,int ink,
            float fade,boolean warning,boolean reduced) {
        Vec3 center=path(h,age);
        if(warning) {
            // Telegraphed at the body's true size. A dimmer copy of the same silhouette - which is
            // what every other attack here did - tells you nothing about how much room you need.
            mark(ctx,center,FxKinds.Mark.SHOCK_RING,(float)radius,SAFE,.55F*fade,3,0);
            return;
        }
        float opacity=(ctx.cameraPos.distanceTo(center)<radius+1?.12F:.95F)*fade*(reduced?.8F:1);
        Vec3 lead=UnwakingHazard.unit(center.subtract(path(h,age-1)));
        trail(ctx,h,age,radius,ink,opacity*.5F,reduced?8:16);
        orb(ctx,center,FxKinds.Orb.CHARGE_SPHERE,(float)radius,ink,opacity,4,2);
        orb(ctx,center.add(lead.scale(radius*.7)),FxKinds.Orb.BLOOM_FLASH,(float)(radius*.45),ink,opacity*.75F,2,0);
    }

    /**
     * Hitscan: a shrinking set of spokes while it aims, then a revealed bolt down the line it takes.
     * {@link FilamentPainter#beam} exists for exactly this - its reveal parameter wipes the bolt
     * outward from the muzzle instead of popping the whole length in on a single frame.
     */
    private static void clock(FxContext ctx,UnwakingHazard h,double age,Vec3 camera,int ink) {
        mark(ctx,h.origin(),FxKinds.Mark.CLOCK_SPOKES,(float)Math.max(.4,2.6*(1-Math.clamp(age/20,0,1))),
                SAFE,.95F,6,0);
        if(age<20||age>=26) return;
        Vec3 delta=camera.subtract(h.origin());
        ctx.pose.pushPose();
        ctx.pose.translate(h.origin().x,h.origin().y,h.origin().z);
        FilamentPainter.orientAlong(ctx.pose,delta);
        FilamentPainter.beam(ctx,FxKinds.Filament.LIGHTNING,.34F,(float)delta.length(),ink,
                (float)(1-(age-20)/6),(float)Math.min(1,(age-20)/2.5),3,1);
        ctx.pose.popPose();
    }

    /** A blade rim rather than a white rectangle: the bright leading edge is the whole attack. */
    private static void guillotine(FxContext ctx,UnwakingHazard h,double age,int ink,float fade,boolean warning) {
        Vec3 center=UnwakingSkyGeometry.center(h,age), span=h.right().scale(UnwakingSkyGeometry.BLADE_HALF);
        if(warning) {
            mark(ctx,h.openings().getFirst(),FxKinds.Mark.RAY_BURST,
                    (float)UnwakingSkyGeometry.BLADE_HALF*.35F,SAFE,.5F,4,0);
            link(ctx,h.origin(),h.openings().getFirst(),.16F,ink,.6F,FxKinds.Filament.PLASMA_TUBE,0);
            return;
        }
        link(ctx,center.subtract(span),center.add(span),(float)UnwakingSkyGeometry.BLADE_RADIUS,ink,.9F*fade,
                FxKinds.Filament.BLADE_RIM,5);
    }

    /**
     * The ring's warning gets its own shape instead of the annulus at eighteen percent alpha, which
     * across forty-eight blocks of radius was a faint smear you could not place in depth.
     */
    private static void horizonWarning(FxContext ctx,UnwakingHazard h,double age,float fade) {
        Vec3 center=UnwakingSkyGeometry.center(h,age);
        mark(ctx,center,FxKinds.Mark.SHOCK_RING,(float)UnwakingSkyGeometry.RING_RADIUS,SAFE,.5F*fade,2,0);
        mark(ctx,center,FxKinds.Mark.RAY_BURST,
                (float)((UnwakingSkyGeometry.RING_RADIUS-UnwakingSkyGeometry.RING_WIDTH)*.8),SAFE,.35F*fade,3,0);
    }

    /** The first age at which a body is off its starting point; before this its path is a point. */
    private static int pathStart(UnwakingHazard h) {
        return h.kind()==Kind.RETURNING_VERDICT?20:h.kind().impact;
    }

    /** Where the body is at a given age, from whichever shared geometry owns its path. */
    private static Vec3 path(UnwakingHazard h,double age) {
        double clamped=Math.max(pathStart(h),age);
        return h.kind()==Kind.RETURNING_VERDICT
                ? UnwakingAssaultGeometry.position(h,clamped)
                : UnwakingSkyGeometry.center(h,clamped);
    }

    private static void trail(FxContext ctx,UnwakingHazard h,double age,double radius,int ink,float opacity,int wanted) {
        // Only as far back as the body has actually travelled. Reaching past its start samples the
        // same point repeatedly, which costs a quad per repeat and draws nothing.
        int segments=Math.min(wanted,(int)(age-pathStart(h)));
        if(segments<2) return;
        TrailBuffer ribbon=new TrailBuffer(segments+1);
        for(int i=segments;i>=0;i--) {
            Vec3 p=path(h,age-i);
            ribbon.push(p.x,p.y,p.z,(int)(age-i));
        }
        ribbon.emit(ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam()),ctx.pose.last().pose(),Vec3.ZERO,
                ctx.cameraPos,(float)(radius*.55),ink,opacity,
                MagicVertex.pack(FxKinds.Filament.RIBBON.id(),4,0,ctx.phase,ctx.seed,0),segments);
    }

    private static void orb(FxContext ctx,Vec3 at,FxKinds.Orb kind,float radius,int rgb,float opacity,int count,int paramB) {
        ctx.pose.pushPose();
        ctx.pose.translate(at.x,at.y,at.z);
        OrbPainter.billboard(ctx,kind,radius,rgb,opacity,ctx.phase,count,paramB);
        ctx.pose.popPose();
    }

    private static void mark(FxContext ctx,Vec3 at,FxKinds.Mark kind,float radius,int rgb,float opacity,int count,int paramB) {
        ctx.pose.pushPose();
        ctx.pose.translate(at.x,at.y,at.z);
        // Marks are a flat square in the local XY plane; these are read in mid-air rather than off
        // the ground, so they face the camera instead of lying down.
        if(ctx.cameraOrientation!=null) ctx.pose.mulPose(ctx.cameraOrientation);
        MarkPainter.mark(ctx,kind,radius,rgb,opacity,ctx.phase,count,paramB);
        ctx.pose.popPose();
    }

    private static void link(FxContext ctx,Vec3 from,Vec3 to,float halfWidth,int rgb,float opacity,
            FxKinds.Filament kind,int count) {
        FilamentPainter.link(ctx,ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam()),from,to,halfWidth,
                rgb,opacity,MagicVertex.pack(kind.id(),count,0,ctx.phase,ctx.seed,0));
    }

    /**
     * Flushed per hazard rather than once at the end of the loop: these buffers are shared with the
     * flat surfaces drawn beside them, and a batch left open is emitted whenever the frame happens
     * to end - which for translucent bodies at wildly different depths is the wrong order.
     */
    private static void flush(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch(MagicalFxRenderTypes.filamentBeam());
        buffers.endBatch(MagicalFxRenderTypes.plasmaOrb());
        buffers.endBatch(MagicalFxRenderTypes.groundMarkAdd());
    }
}
