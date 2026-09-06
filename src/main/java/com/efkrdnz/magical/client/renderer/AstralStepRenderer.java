package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.registry.MagicalBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

public final class AstralStepRenderer {
    private static final int RANGE = 14;
    private static final double FADE_START = 13.5D;
    private static final double FADE_FULL = 2.0D;
    private static final int SCAN_INTERVAL_TICKS = 5;
    private static final List<BlockPos> CACHED_POSITIONS = new ArrayList<>();
    private static long lastScanGameTime = Long.MIN_VALUE;

    private AstralStepRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        Vec3 playerPosition = minecraft.player.getPosition(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        rescanIfStale(minecraft, playerPosition);
        if (CACHED_POSITIONS.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = event.getPoseStack();
        List<VisibleStep> visibleSteps = new ArrayList<>();

        for (BlockPos pos : CACHED_POSITIONS) {
            BlockState state = minecraft.level.getBlockState(pos);
            if (!state.is(MagicalBlocks.ASTRAL_STEP_SLAB.get())) {
                continue;
            }
            double distance = playerPosition.distanceTo(Vec3.atCenterOf(pos));
            float alpha = alphaForDistance(distance);
            if (alpha <= 0.01F) {
                continue;
            }
            visibleSteps.add(new VisibleStep(pos, state, alpha));
        }

        if (visibleSteps.isEmpty()) {
            return;
        }

        VertexConsumer astral = buffer.getBuffer(MagicalRenderTypes.astralStep());
        for (VisibleStep step : visibleSteps) {
            renderAstralSurface(poseStack, astral, minecraft, camera, step.pos(), step.state(), step.alpha());
        }
        buffer.endBatch(MagicalRenderTypes.astralStep());
    }

    private static void rescanIfStale(Minecraft minecraft, Vec3 playerPosition) {
        long gameTime = minecraft.level.getGameTime();
        if (lastScanGameTime != Long.MIN_VALUE && gameTime - lastScanGameTime < SCAN_INTERVAL_TICKS && gameTime >= lastScanGameTime) {
            return;
        }
        lastScanGameTime = gameTime;
        CACHED_POSITIONS.clear();
        BlockPos origin = BlockPos.containing(playerPosition);
        for (int x = -RANGE; x <= RANGE; x++) {
            for (int y = -RANGE; y <= RANGE; y++) {
                for (int z = -RANGE; z <= RANGE; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (minecraft.level.getBlockState(pos).is(MagicalBlocks.ASTRAL_STEP_SLAB.get())) {
                        CACHED_POSITIONS.add(pos.immutable());
                    }
                }
            }
        }
    }

    private static float alphaForDistance(double distance) {
        if (distance >= FADE_START) {
            return 0.0F;
        }
        if (distance <= FADE_FULL) {
            return 0.88F;
        }
        float t = (float) ((FADE_START - distance) / (FADE_START - FADE_FULL));
        t = Mth.clamp(t, 0.0F, 1.0F);
        return (t * t * (3.0F - 2.0F * t)) * 0.88F;
    }

    private static void renderAstralSurface(PoseStack poseStack, VertexConsumer astral, Minecraft minecraft, Vec3 camera, BlockPos pos, BlockState state, float alpha) {
        SlabType type = state.getValue(SlabBlock.TYPE);
        float y0 = type == SlabType.TOP ? 0.5F : 0.0F;
        float y1 = type == SlabType.BOTTOM ? 0.5F : 1.0F;
        double dx = pos.getX() - camera.x;
        double dy = pos.getY() - camera.y;
        double dz = pos.getZ() - camera.z;

        poseStack.pushPose();
        poseStack.translate(dx, dy, dz);
        Matrix4f matrix = poseStack.last().pose();
        int topAlpha = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        int sideAlpha = Mth.clamp(Math.round(alpha * 230.0F), 0, 230);
        int bottomAlpha = Mth.clamp(Math.round(alpha * 205.0F), 0, 205);

        float scale = 0.31F;
        float ux0 = pos.getX() * scale;
        float ux1 = ux0 + scale;
        float uz0 = pos.getZ() * scale;
        float uz1 = uz0 + scale;
        float vy0 = (pos.getY() + y0) * scale;
        float vy1 = (pos.getY() + y1) * scale;

        if (!(y1 == 1.0F && coversFace(minecraft, pos.above(), SlabType.BOTTOM))) {
            float y = y1 + 0.006F;
            quad(astral, matrix,
                    0.0F, y, 0.0F, ux0, uz0,
                    0.0F, y, 1.0F, ux0, uz1,
                    1.0F, y, 1.0F, ux1, uz1,
                    1.0F, y, 0.0F, ux1, uz0, topAlpha);
        }
        if (!(y0 == 0.0F && coversFace(minecraft, pos.below(), SlabType.TOP))) {
            float y = y0 + 0.004F;
            quad(astral, matrix,
                    0.0F, y, 0.0F, ux0, uz0,
                    0.0F, y, 1.0F, ux0, uz1,
                    1.0F, y, 1.0F, ux1, uz1,
                    1.0F, y, 0.0F, ux1, uz0, bottomAlpha);
        }
        if (!coversSide(minecraft, pos.north(), type)) {
            float z = 0.004F;
            quad(astral, matrix,
                    0.0F, y0, z, ux0, vy0,
                    0.0F, y1, z, ux0, vy1,
                    1.0F, y1, z, ux1, vy1,
                    1.0F, y0, z, ux1, vy0, sideAlpha);
        }
        if (!coversSide(minecraft, pos.south(), type)) {
            float z = 0.996F;
            quad(astral, matrix,
                    0.0F, y0, z, ux0, vy0,
                    0.0F, y1, z, ux0, vy1,
                    1.0F, y1, z, ux1, vy1,
                    1.0F, y0, z, ux1, vy0, sideAlpha);
        }
        if (!coversSide(minecraft, pos.west(), type)) {
            float x = 0.004F;
            quad(astral, matrix,
                    x, y0, 0.0F, uz0, vy0,
                    x, y1, 0.0F, uz0, vy1,
                    x, y1, 1.0F, uz1, vy1,
                    x, y0, 1.0F, uz1, vy0, sideAlpha);
        }
        if (!coversSide(minecraft, pos.east(), type)) {
            float x = 0.996F;
            quad(astral, matrix,
                    x, y0, 0.0F, uz0, vy0,
                    x, y1, 0.0F, uz0, vy1,
                    x, y1, 1.0F, uz1, vy1,
                    x, y0, 1.0F, uz1, vy0, sideAlpha);
        }
        poseStack.popPose();
    }

    private static boolean coversFace(Minecraft minecraft, BlockPos neighborPos, SlabType coveringHalf) {
        BlockState neighbor = minecraft.level.getBlockState(neighborPos);
        if (!neighbor.is(MagicalBlocks.ASTRAL_STEP_SLAB.get())) {
            return false;
        }
        SlabType neighborType = neighbor.getValue(SlabBlock.TYPE);
        return neighborType == SlabType.DOUBLE || neighborType == coveringHalf;
    }

    private static boolean coversSide(Minecraft minecraft, BlockPos neighborPos, SlabType ourType) {
        BlockState neighbor = minecraft.level.getBlockState(neighborPos);
        if (!neighbor.is(MagicalBlocks.ASTRAL_STEP_SLAB.get())) {
            return false;
        }
        SlabType neighborType = neighbor.getValue(SlabBlock.TYPE);
        return neighborType == SlabType.DOUBLE || neighborType == ourType;
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float x4, float y4, float z4, float u4, float v4, int a) {
        vertex(consumer, matrix, x1, y1, z1, u1, v1, a);
        vertex(consumer, matrix, x2, y2, z2, u2, v2, a);
        vertex(consumer, matrix, x3, y3, z3, u3, v3, a);
        vertex(consumer, matrix, x4, y4, z4, u4, v4, a);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v, int a) {
        consumer.addVertex(matrix, x, y, z).setUv(u, v).setColor(255, 255, 255, a);
    }

    private record VisibleStep(BlockPos pos, BlockState state, float alpha) {}
}
