package com.efkrdnz.magical.client.mind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Imagined blocks, drawn by this client at the strength its own mind holds them. */
public final class IllusionRenderer {
    public static final int LILAC = 0xBDA4FF;
    private static final float EDGE_ALPHA = 0.9F;
    private static final float MIN_ALPHA = 0.02F;

    private IllusionRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.level == null || ClientMind.scenes().isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        for (ClientMind.View view : ClientMind.scenes()) {
            for (ClientMind.Cell cell : view.cells()) {
                float alpha = view.mine() ? ClientMind.OWNER_ALPHA : ClientMind.visibility(view.id(), cell.element());
                drawBlock(minecraft, pose, buffers, cam, cell.state(), cell.pos(), alpha);
            }
        }
        buffers.endBatch(RenderType.translucent());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (ClientMind.View view : ClientMind.scenes()) {
            if (view.mine()) {
                for (ClientMind.Cell cell : view.cells()) {
                    drawEdge(pose, lines, cam, new AABB(cell.pos()), LILAC, EDGE_ALPHA);
                }
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    /** One block at {@code alpha}; the caller ends the translucent batch. Shared with the Daydream draft. */
    public static void drawBlock(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam,
                                 BlockState state, BlockPos pos, float alpha) {
        if (alpha < MIN_ALPHA || minecraft.level == null) {
            return;
        }
        pose.pushPose();
        pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        int light = LevelRenderer.getLightColor(minecraft.level, pos);
        MultiBufferSource faded = type -> new Faded(buffers.getBuffer(RenderType.translucent()), alpha);
        minecraft.getBlockRenderer().renderSingleBlock(state, pose, faded, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    /** A line box in world space, {@code rgb} at {@code alpha}; the caller ends the lines batch. */
    public static void drawEdge(PoseStack pose, VertexConsumer lines, Vec3 cam, AABB box, int rgb, float alpha) {
        ShapeRenderer.renderLineBox(pose, lines, box.move(-cam.x, -cam.y, -cam.z),
                ((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F, alpha);
    }

    /** Passes every vertex through with its alpha scaled; the bulk paths default through setColor. */
    private record Faded(VertexConsumer inner, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
            return this;
        }
    }
}
