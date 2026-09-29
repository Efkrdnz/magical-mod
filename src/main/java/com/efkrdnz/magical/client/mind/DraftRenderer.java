package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.mind.DraftRay;
import com.efkrdnz.magical.magic.mind.Figment;
import com.efkrdnz.magical.magic.mind.ImaginedBlock;
import com.efkrdnz.magical.magic.mind.Impression;
import com.efkrdnz.magical.magic.mind.Offset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/** The draft in lilac, and where the next stroke will land in white. */
public final class DraftRenderer {
    private static final float DRAFT_ALPHA = 0.6F;
    private static final float EDGE_ALPHA = 0.5F;
    private static final int CURSOR = 0xFFFFFF;
    private static final float CURSOR_ALPHA = 0.9F;
    private static final float FIGMENT_ALPHA = 0.9F;
    private static final int FLAW = 0xFFD36B;

    private DraftRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (!DaydreamMode.active() || minecraft.level == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        for (ImaginedBlock block : DaydreamMode.draft().blocks()) {
            IllusionRenderer.drawBlock(minecraft, pose, buffers, cam, state(block.blockId()), DaydreamMode.world(block.at()), DRAFT_ALPHA);
        }
        buffers.endBatch(IllusionRenderTypes.block());

        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (ImaginedBlock block : DaydreamMode.draft().blocks()) {
            IllusionRenderer.drawEdge(pose, lines, cam, new AABB(DaydreamMode.world(block.at())), IllusionRenderer.LILAC, EDGE_ALPHA);
        }
        for (Figment figment : DaydreamMode.draft().figments()) {
            IllusionRenderer.drawEdge(pose, lines, cam, DaydreamMode.figmentBox(figment), IllusionRenderer.LILAC, FIGMENT_ALPHA);
        }
        DraftRay.Hit hit = DaydreamMode.cursor(minecraft);
        if (hit != null) {
            BlockPos from = DaydreamMode.corner() != null ? DaydreamMode.corner() : hit.place();
            Impression chosen = Impression.parse(DaydreamMode.impression());
            boolean creature = chosen != null && chosen.kind() == Impression.Kind.CREATURE;
            List<Offset> cells = creature ? List.of(DaydreamMode.offset(hit.place()))
                    : DaydreamMode.brush().cells(DaydreamMode.offset(from), DaydreamMode.offset(hit.place()));
            for (Offset cell : cells) {
                IllusionRenderer.drawEdge(pose, lines, cam, new AABB(DaydreamMode.world(cell)), CURSOR, CURSOR_ALPHA);
            }
        }
        if (ClientDream.ownDream()) {
            if (ClientDream.flawBlock() != null) {
                IllusionRenderer.drawEdge(pose, lines, cam, new AABB(ClientDream.flawBlock()).inflate(0.02), FLAW, 1.0F);
            }
            Entity figment = ClientDream.flawEntity() >= 0 ? minecraft.level.getEntity(ClientDream.flawEntity()) : null;
            if (figment != null) {
                IllusionRenderer.drawEdge(pose, lines, cam, figment.getBoundingBox().inflate(0.05), FLAW, 1.0F);
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static BlockState state(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? Blocks.AIR.defaultBlockState() : BuiltInRegistries.BLOCK.getValue(key).defaultBlockState();
    }
}
