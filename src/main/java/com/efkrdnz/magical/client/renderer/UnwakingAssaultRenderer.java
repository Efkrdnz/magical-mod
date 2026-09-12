package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultState;
import com.efkrdnz.magical.client.ClientUnwakingEncounter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/** Actual chronosfx shaders, anchored to the same world frames that emit the attacks. */
public final class UnwakingAssaultRenderer {
    private UnwakingAssaultRenderer() {}
    public static void render(PoseStack pose,MultiBufferSource.BufferSource buffers,UnwakingAssaultState state,long now,boolean reduced) {
        if(!state.active(now)) return;
        Vec3 right=new Vec3(-state.forward().z,0,state.forward().x), up=new Vec3(0,1,0);
        Vec3 center,r,u; RenderType type;
        int age=state.passageAge(now);
        switch(state.passage(now)) {
            case SKY -> {
                var frame=state.woundFrame(now);
                center=frame.center(); r=frame.right(); u=frame.up(); type=MagicalRenderTypes.chronoRift();
            }
            case VORTEX -> {
                center=state.vortex();
                double angle=age*.012;
                r=right.scale(Math.cos(angle)*192).add(state.forward().scale(Math.sin(angle)*192));
                u=state.forward().scale(Math.cos(angle)*192).subtract(right.scale(Math.sin(angle)*192)); type=MagicalRenderTypes.chronoVortex();
            }
            case CLOCK -> { center=state.anchor().add(0,-40,0); r=right.scale(192); u=state.forward().scale(192); type=MagicalRenderTypes.chronoClock(); }
            // The Eyes hang their backdrop overhead, so the dome of eyes sits on a surface rather
            // than on empty sky, and it turns the other way from the vortex.
            case EYES -> {
                double angle=-age*.006;
                center=state.anchor().add(0,150,0);
                r=right.scale(Math.cos(angle)*256).add(state.forward().scale(Math.sin(angle)*256));
                u=state.forward().scale(Math.cos(angle)*256).subtract(right.scale(Math.sin(angle)*256));
                type=MagicalRenderTypes.chronoRift();
            }
            // The Mirror puts its surface underfoot: the reflections are standing on something.
            case MIRROR -> { center=state.anchor().add(0,-2,0); r=right.scale(224); u=state.forward().scale(224); type=MagicalRenderTypes.chronoVortex(); }
            // The Giant gets a wall rather than a ceiling - a horizon for something that tall to be
            // standing in front of, out along the bearing the figure is drawn on.
            case GIANT -> {
                center=state.anchor().add(0,120,0).add(state.forward().scale(300));
                r=right.scale(420); u=up.scale(320); type=MagicalRenderTypes.chronoRift();
            }
            // The shaft itself is the scenery, so the tunnel gets no sheet across its far end - that
            // would close the one thing the passage is selling. It gets a surface behind the player
            // instead, at the mouth the tunnel appears to be arriving from.
            case TUNNEL -> {
                center=state.anchor().add(state.forward().scale(160)).add(0,20,0);
                r=right.scale(300); u=up.scale(300); type=MagicalRenderTypes.chronoVortex();
            }
            // A statement switch takes a missing constant silently, and a passage with no backdrop
            // is three hundred ticks of empty sky. This used to default to the clock disc, which is
            // worse than nothing: a new passage would quietly look like an old one.
            default -> throw new IllegalStateException("Assault passage has no backdrop: "+state.passage(now));
        }
        int ink=ARGB.lerp(ClientUnwakingEncounter.inversion(),0xFFE9E7E2,0xFF161821);
        int color=ARGB.color(Math.round(255*state.effect(now)*(reduced?.7F:1)),ink);
        VertexConsumer out=buffers.getBuffer(type);
        vertex(pose,out,center.subtract(r).subtract(u),0,0,color);
        vertex(pose,out,center.subtract(r).add(u),0,1,color);
        vertex(pose,out,center.add(r).add(u),1,1,color);
        vertex(pose,out,center.add(r).subtract(u),1,0,color);
        buffers.endBatch(type);
    }
    /**
     * The set pieces that need more than one textured quad, drawn on top of the backdrop above.
     *
     * <p>Kept separate because the backdrop is one quad anchored in world space and these are
     * camera-relative scenery and posed models. Called after the backdrop so it blends over it -
     * none of these write depth, so batch order is blend order.
     */
    public static void scenery(PoseStack pose,MultiBufferSource.BufferSource buffers,UnwakingAssaultState state,
            long now,float partial,Vec3 camera,org.joml.Quaternionf facing,boolean reduced) {
        int ink=ARGB.lerp(ClientUnwakingEncounter.inversion(),0xFFE9E7E2,0xFF161821);
        UnwakingSceneryRenderer.render(pose,buffers,state,now,partial,camera,facing,ink&0xFFFFFF,reduced,
                ClientUnwakingEncounter.phase()==com.efkrdnz.magical.boss.unwaking.UnwakingPhase.FINAL);
    }
    private static void vertex(PoseStack pose,VertexConsumer out,Vec3 p,float u,float v,int color) {
        out.addVertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).setUv(u,v).setColor(color);
    }
}
