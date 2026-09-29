package com.efkrdnz.magical.client.mind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The Flaw of your own dream, where you can find it again: a gold glaze and a heavy gold edge the
 * whole time you are in the dream, Daydreaming or not. It used to be a one-pixel line drawn only
 * while Daydreaming, under the white cursor box that sits on the very block just marked.
 */
public final class FlawRenderer {
    private static final int GOLD = 0xFFD36B;
    private static final float GLAZE_ALPHA = 0.28F;
    /** Vanilla lines are a pixel wide whatever is asked; three shells a hair apart read as one heavy edge. */
    private static final double[] SHELLS = {0.03, 0.045, 0.06};

    private FlawRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (!ClientDream.ownDream() || minecraft.level == null) {
            return;
        }
        AABB box = null;
        if (ClientDream.flawBlock() != null) {
            box = new AABB(ClientDream.flawBlock());
        } else if (ClientDream.flawEntity() >= 0) {
            Entity figment = minecraft.level.getEntity(ClientDream.flawEntity());
            box = figment == null ? null : figment.getBoundingBox();
        }
        if (box == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float r = ((GOLD >> 16) & 0xFF) / 255.0F;
        float g = ((GOLD >> 8) & 0xFF) / 255.0F;
        float b = (GOLD & 0xFF) / 255.0F;
        AABB glaze = box.inflate(0.02).move(-cam.x, -cam.y, -cam.z);
        ShapeRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                glaze.minX, glaze.minY, glaze.minZ, glaze.maxX, glaze.maxY, glaze.maxZ, r, g, b, GLAZE_ALPHA);
        buffers.endBatch(RenderType.debugFilledBox());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (double shell : SHELLS) {
            IllusionRenderer.drawEdge(pose, lines, cam, box.inflate(shell), GOLD, 1.0F);
        }
        buffers.endBatch(RenderType.lines());
    }
}
