package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.block.SacrificialCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class SacrificialCoreRenderer implements BlockEntityRenderer<SacrificialCoreBlockEntity> {
    private static final int LON_SEGMENTS = 16;
    private static final int LAT_SEGMENTS = 12;
    private static final int SPIKES = 14;
    private static final int SHELLS = 3;
    private static final float GOLDEN_ANGLE = 2.39996F;

    public SacrificialCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(SacrificialCoreBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockPos pos = blockEntity.getBlockPos();
        float phase = (Math.floorMod(pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 13, 256)) * 0.35F;
        float time = (blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() % 240000L : 0L) + partialTick + phase;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        renderCore(poseStack, bufferSource, time);
        poseStack.popPose();
    }

    /**
     * Draws the full sacrificial core (heart, emission shells, erupting spikes) around the
     * current pose origin. Shared by the block entity renderer and the item special renderer.
     */
    public static void renderCore(PoseStack poseStack, MultiBufferSource bufferSource, float time) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.75F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.027F) * 7.0F));
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = bufferSource.getBuffer(MagicalRenderTypes.sacrificialCore());

        float heartbeat = heartbeat(time);
        // Dense black heart, squeezed by the heartbeat.
        drawSphere(consumer, matrix, time, 0.185F + heartbeat * 0.022F, 1.0F, 255, 255, 255, 250);

        // Shockwave shells that continuously swell outward from the heart and dissolve.
        for (int i = 0; i < SHELLS; i++) {
            float cycle = Mth.frac(time * 0.014F + i / (float) SHELLS);
            float radius = 0.2F + cycle * 0.46F;
            int alpha = Math.round((1.0F - cycle) * (1.0F - cycle) * 108.0F);
            if (alpha > 2) {
                drawSphere(consumer, matrix, time * 0.6F + i * 37.0F, radius, 0.25F, 195, 58, 50, alpha);
            }
        }

        // Steady outer energy field carrying the streaming shader rays - the block's aura.
        drawSphere(consumer, matrix, time * 0.45F + 120.0F, 0.58F, 0.35F, 175, 42, 40, 32);

        drawSpikes(consumer, matrix, time, heartbeat);
        poseStack.popPose();
    }

    private static float heartbeat(float time) {
        float beatPhase = time * 0.085F;
        float thump = Mth.sin(beatPhase);
        float echo = Mth.sin(beatPhase - 0.5F);
        return (float) (Math.pow(Math.max(thump, 0.0F), 6.0) + 0.55D * Math.pow(Math.max(echo, 0.0F), 6.0));
    }

    private static void drawSpikes(VertexConsumer consumer, Matrix4f matrix, float time, float heartbeat) {
        for (int i = 0; i < SPIKES; i++) {
            float y = 1.0F - 2.0F * (i + 0.5F) / SPIKES;
            float ring = Mth.sqrt(Math.max(0.0F, 1.0F - y * y));
            float angle = i * GOLDEN_ANGLE + time * 0.008F;
            Vec3 direction = new Vec3(ring * Mth.cos(angle), y, ring * Mth.sin(angle));

            float length = (0.2F + 0.2F * (0.5F + 0.5F * Mth.sin(time * 0.11F + i * 2.39F))) * (0.75F + heartbeat * 0.55F);
            float width = 0.05F;

            Vec3 reference = Math.abs(direction.y) > 0.98D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 side1 = direction.cross(reference).normalize().scale(width);
            Vec3 side2 = direction.cross(side1).normalize().scale(width);
            Vec3 base = direction.scale(0.15D);
            Vec3 tip = direction.scale(0.15D + length);

            float u0 = i / (float) SPIKES;
            float u1 = u0 + 0.4F / SPIKES;
            spikeQuad(consumer, matrix, base, tip, side1, u0, u1);
            spikeQuad(consumer, matrix, base, tip, side2, u0, u1);
        }
    }

    private static void spikeQuad(VertexConsumer consumer, Matrix4f matrix, Vec3 base, Vec3 tip, Vec3 side, float u0, float u1) {
        Vec3 tipSide = side.scale(0.08D);
        vertex(consumer, matrix, base.add(side), u0, 0.0F, 255, 200, 180, 215);
        vertex(consumer, matrix, base.subtract(side), u1, 0.0F, 255, 200, 180, 215);
        vertex(consumer, matrix, tip.subtract(tipSide), u1, 1.0F, 255, 90, 60, 0);
        vertex(consumer, matrix, tip.add(tipSide), u0, 1.0F, 255, 90, 60, 0);
    }

    private static void drawSphere(VertexConsumer consumer, Matrix4f matrix, float time, float baseRadius, float wobbleScale, int red, int green, int blue, int alpha) {
        for (int lat = 0; lat < LAT_SEGMENTS; lat++) {
            for (int lon = 0; lon < LON_SEGMENTS; lon++) {
                sphereVertex(consumer, matrix, lat, lon, time, baseRadius, wobbleScale, red, green, blue, alpha);
                sphereVertex(consumer, matrix, lat + 1, lon, time, baseRadius, wobbleScale, red, green, blue, alpha);
                sphereVertex(consumer, matrix, lat + 1, lon + 1, time, baseRadius, wobbleScale, red, green, blue, alpha);
                sphereVertex(consumer, matrix, lat, lon + 1, time, baseRadius, wobbleScale, red, green, blue, alpha);
            }
        }
    }

    private static void sphereVertex(VertexConsumer consumer, Matrix4f matrix, int lat, int lon, float time, float baseRadius, float wobbleScale, int red, int green, int blue, int alpha) {
        float theta = Mth.PI * lat / LAT_SEGMENTS;
        float phi = Mth.TWO_PI * lon / LON_SEGMENTS;
        float sinTheta = Mth.sin(theta);
        // Longitude harmonics are scaled by sin(theta) to keep the poles watertight; the
        // high-frequency term gives the heart its jagged, straining silhouette.
        float radius = baseRadius + wobbleScale * (0.01F * Mth.sin(time * 0.045F + theta * 4.0F)
                + sinTheta * (0.028F * Mth.sin(time * 0.13F + theta * 3.0F + phi * 2.0F)
                        + 0.02F * Mth.sin(time * 0.09F - theta * 2.0F + phi * 3.0F + 1.7F)
                        + 0.018F * Mth.sin(time * 0.21F + theta * 5.0F + phi * 7.0F)));
        float x = radius * sinTheta * Mth.cos(phi);
        float y = radius * Mth.cos(theta);
        float z = radius * sinTheta * Mth.sin(phi);
        float u = 1.0F - Math.abs(1.0F - 2.0F * lon / (float) LON_SEGMENTS);
        float v = lat / (float) LAT_SEGMENTS;
        vertex(consumer, matrix, new Vec3(x, y, z), u, v, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 position, float u, float v, int red, int green, int blue, int alpha) {
        consumer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z).setUv(u, v).setColor(red, green, blue, alpha);
    }
}
