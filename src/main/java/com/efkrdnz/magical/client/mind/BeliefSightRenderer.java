package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.renderer.causality.AnchorMarkRenderer;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.HashMap;
import java.util.Map;

/** The wielder's Belief Sight: a ring over every viewer of their scenes, through walls. */
public final class BeliefSightRenderer {
    private static final float RADIUS = 0.28F;
    private static final double ABOVE_HEAD = 0.55;
    private static final int PIPS = 8;
    private static final int WEIGHT = 3;

    private BeliefSightRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        Map<Integer, Float> strongest = new HashMap<>();
        for (ClientMind.View view : ClientMind.scenes()) {
            if (!view.mine()) {
                continue;
            }
            var rows = ClientMind.rows(view.id());
            for (var row : rows) {
                float belief = BeliefSight.strongest(rows, row.viewer());
                strongest.merge(row.viewer(), belief, Math::max);
            }
        }
        if (strongest.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.glyphInkThrough());
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        strongest.forEach((id, belief) -> {
            BeliefSight.Mark mark = BeliefSight.mark(belief);
            if (mark == null || !(minecraft.level.getEntity(id) instanceof LivingEntity body) || !body.isAlive()) {
                return;
            }
            Vec3 at = body.getPosition(partial).add(0.0, body.getBbHeight() + ABOVE_HEAD, 0.0);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(event.getCamera().rotation());
            AnchorMarkRenderer.band(consumer, pose.last().pose(), mark.kind(), PIPS, RADIUS, WEIGHT, mark.rgb(), mark.opacity());
            pose.popPose();
        });
        buffers.endBatch(MagicalFxRenderTypes.glyphInkThrough());
    }
}
