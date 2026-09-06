package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.block.AstralGateBlock;
import com.efkrdnz.magical.block.AstralGateBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public final class AstralGateRenderer implements BlockEntityRenderer<AstralGateBlockEntity> {
    private static final int SEGMENTS = 36;
    private static final int RINGS = 10;
    private static final float PORTAL_RADIUS = 1.5F;
    private static final float FUNNEL_DEPTH = 0.55F;

    public AstralGateRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(AstralGateBlockEntity blockEntity) {
        // The 3x3 portal disc extends well past the host block.
        return new AABB(blockEntity.getBlockPos()).inflate(2.0D);
    }

    @Override
    public void render(AstralGateBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockPos pos = blockEntity.getBlockPos();
        float phase = (Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13, 256)) * 0.35F;
        float time = (blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() % 240000L : 0L) + partialTick + phase;

        BlockState state = blockEntity.getBlockState();
        Direction facing = state.hasProperty(AstralGateBlock.FACING) ? state.getValue(AstralGateBlock.FACING) : Direction.NORTH;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        renderGate(poseStack, bufferSource, time);
        poseStack.popPose();
    }

    /**
     * Draws the vortex portal disc in the XY plane (normal +Z) around the current pose
     * origin. Shared by the block entity renderer and the item special renderer.
     */
    public static void renderGate(PoseStack poseStack, MultiBufferSource bufferSource, float time) {
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(MagicalRenderTypes.astralGate());
        for (int ring = 0; ring < RINGS; ring++) {
            for (int segment = 0; segment < SEGMENTS; segment++) {
                vertex(consumer, matrix, ring, segment, time);
                vertex(consumer, matrix, ring + 1, segment, time);
                vertex(consumer, matrix, ring + 1, segment + 1, time);
                vertex(consumer, matrix, ring, segment + 1, time);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, int ring, int segment, float time) {
        float ringFraction = ring / (float) RINGS;
        float angle = Mth.TWO_PI * segment / SEGMENTS;
        // Differential spiral twist: inner rings are wound further ahead and spin faster,
        // so the surface pattern visibly corkscrews into the center.
        float twist = (1.0F - ringFraction) * (2.6F + 0.35F * Mth.sin(time * 0.05F)) + time * 0.055F;
        float spiralAngle = angle + twist;
        // Irregular rim: flux harmonics grow toward the edge so the portal mouth ripples.
        float flux = 1.0F + ringFraction * (0.045F * Mth.sin(time * 0.09F + angle * 3.0F)
                + 0.035F * Mth.sin(time * 0.13F - angle * 5.0F + 1.3F));
        float radius = PORTAL_RADIUS * ringFraction * flux;
        float x = Mth.cos(spiralAngle) * radius;
        float y = Mth.sin(spiralAngle) * radius;
        // Suction funnel: the center recedes away from the facing direction.
        float z = -FUNNEL_DEPTH * (1.0F - ringFraction) * (1.0F - ringFraction);
        // Mirrored angular UV keeps the noise seamless where the disc wraps.
        float u = 1.0F - Math.abs(1.0F - 2.0F * segment / (float) SEGMENTS);
        float v = ringFraction;
        int alpha = Mth.clamp(Math.round(255.0F * (1.0F - (float) Math.pow(Mth.clamp((ringFraction - 0.86F) / 0.14F, 0.0F, 1.0F), 1.5D))), 0, 255);
        consumer.addVertex(matrix, x, y, z).setUv(u, v).setColor(255, 255, 255, alpha);
    }
}
