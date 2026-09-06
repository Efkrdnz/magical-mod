package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * filament_beam geometry. Local +Z is the travel/length axis for TUBE/HELIX/CROSSED_BLADES; COLUMN
 * runs up local +Y; RING lies in local XY; LINK spans from the origin to ctx.endPoint; TRAIL draws
 * the entity's TrailBuffer (handled by the renderer shell, which passes the buffer as endPoint-less
 * geometry through {@link #trail}).
 */
public final class FilamentPainter {
    private FilamentPainter() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette s) {
        FxKinds.Filament kind = FxKinds.Filament.values()[Math.floorMod(s.kind(), FxKinds.Filament.values().length)];
        boolean dark = kind.dark();
        int rgb = profile.color(s.role());
        float opacity = ctx.fade() * s.opacity();
        int packed = MagicVertex.pack(kind.id(), s.count(), s.paramB(), ctx.phase, ctx.seed, dark ? 1 : 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(dark ? MagicalFxRenderTypes.filamentDark() : MagicalFxRenderTypes.filamentBeam());
        PoseStack pose = ctx.pose;
        float halfWidth = s.sizeA();
        float length = s.sizeB() > 0.0F ? s.sizeB() : 1.0F;
        switch (s.form()) {
            case TUBE, CROSSED_BLADES -> {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(ctx.seed * 5.6F + (s.form() == Silhouette.Form.CROSSED_BLADES ? 45.0F : 0.0F)));
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.tube(), halfWidth, halfWidth, length, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(2);
            }
            case HELIX -> {
                // strands are drawn in-shader from one wide tube (count = strands)
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(ctx.age * 6.0F));
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.tube(), halfWidth, halfWidth, length, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(2);
            }
            case COLUMN -> {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(ctx.seed * 5.6F));
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.column(), halfWidth, length, halfWidth, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(2);
            }
            case RING -> {
                float[] mesh = FxMesh.ring(48, halfWidth / Math.max(length, 0.001F));
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(ctx.age * 1.5F));
                FxMesh.emit(consumer, pose.last().pose(), mesh, length, length, 1.0F, rgb, opacity, MagicVertex.withPhase(packed, 0.5F));
                pose.popPose();
                FxBudget.countQuads(48);
            }
            case LINK -> link(ctx, consumer, Vec3.ZERO, ctx.endPoint, halfWidth, rgb, opacity, packed);
            case FAN -> {
                // a hinged flat blade sweeping around local Y: a thin vertical ribbon along +Z
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(ctx.extra));
                pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.tube(), halfWidth, halfWidth, length, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(2);
            }
            case TRAIL -> {
                // trail geometry is owned by the entity renderer (TrailBuffer); nothing static to draw
            }
            default -> {
                pose.pushPose();
                FxMesh.emit(consumer, pose.last().pose(), FxMesh.tube(), halfWidth, halfWidth, length, rgb, opacity, packed);
                pose.popPose();
                FxBudget.countQuads(2);
            }
        }
    }

    /** Crossed quads between two local points using an orthonormal frame (the FusionRite link idiom). */
    public static void link(FxContext ctx, VertexConsumer consumer, Vec3 from, Vec3 to, float halfWidth, int rgb, float opacity, int packed) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-4D) {
            return;
        }
        Vec3 dir = d.scale(1.0D / len);
        Vec3 up = Math.abs(dir.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = dir.cross(up).normalize().scale(halfWidth);
        Vec3 side2 = dir.cross(side).normalize().scale(halfWidth);
        Matrix4f m = ctx.pose.last().pose();
        quad(consumer, m, from, to, side, rgb, opacity, packed);
        quad(consumer, m, from, to, side2, rgb, opacity, packed);
        FxBudget.countQuads(2);
    }

    private static void quad(VertexConsumer consumer, Matrix4f m, Vec3 a, Vec3 b, Vec3 side, int rgb, float opacity, int packed) {
        MagicVertex.emit(consumer, m, (float) (a.x - side.x), (float) (a.y - side.y), (float) (a.z - side.z), 0.0F, 0.0F, rgb, opacity, packed);
        MagicVertex.emit(consumer, m, (float) (a.x + side.x), (float) (a.y + side.y), (float) (a.z + side.z), 0.0F, 1.0F, rgb, opacity, packed);
        MagicVertex.emit(consumer, m, (float) (b.x + side.x), (float) (b.y + side.y), (float) (b.z + side.z), 1.0F, 1.0F, rgb, opacity, packed);
        MagicVertex.emit(consumer, m, (float) (b.x - side.x), (float) (b.y - side.y), (float) (b.z - side.z), 1.0F, 0.0F, rgb, opacity, packed);
    }

    /** A straight beam from the origin along +Z with an explicit reveal phase (hitscan flashes). */
    public static void beam(FxContext ctx, FxKinds.Filament kind, float halfWidth, float length, int rgb, float opacity, float reveal, int count, int taper) {
        int packed = MagicVertex.pack(kind.id(), count, taper, reveal, ctx.seed, kind.dark() ? 1 : 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(kind.dark() ? MagicalFxRenderTypes.filamentDark() : MagicalFxRenderTypes.filamentBeam());
        ctx.pose.pushPose();
        FxMesh.emit(consumer, ctx.pose.last().pose(), FxMesh.tube(), halfWidth, halfWidth, length, rgb, opacity, packed);
        ctx.pose.popPose();
        FxBudget.countQuads(2);
    }

    /** Rotate so local +Z points along a surface normal; straight up/down become flat discs. */
    public static void orientToNormal(PoseStack pose, Vec3 normal) {
        if (normal.lengthSqr() < 1.0E-4D) {
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            return;
        }
        Vec3 n = normal.normalize();
        if (Math.abs(n.y) > 0.999D) {
            pose.mulPose(Axis.XP.rotationDegrees(n.y > 0.0D ? -90.0F : 90.0F));
            return;
        }
        orientAlong(pose, n);
    }

    /** Orient the pose so local +Z points along dir (yaw then pitch). */
    public static void orientAlong(PoseStack pose, Vec3 dir) {
        if (dir.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 n = dir.normalize();
        float yaw = (float) Math.atan2(n.x, n.z);
        float pitch = (float) Math.asin(Mth.clamp(n.y, -1.0D, 1.0D));
        pose.mulPose(Axis.YP.rotation(yaw));
        pose.mulPose(Axis.XP.rotation(-pitch));
    }
}
