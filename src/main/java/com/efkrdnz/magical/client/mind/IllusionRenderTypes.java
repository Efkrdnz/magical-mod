package com.efkrdnz.magical.client.mind;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/** Render types for imagined blocks. */
final class IllusionRenderTypes {
    /** Room for a scene or two: 32 bytes a vertex, 128 a quad. */
    private static final int BUFFER_BYTES = 262144;

    private static RenderType block;

    private IllusionRenderTypes() {}

    /**
     * Vanilla's translucent block type with the depth write taken off. {@code RenderType.translucent()}
     * writes depth, so even a 2%-alpha illusion would hide every mob and all water behind it, and on
     * Fabulous its target is re-copied after entities and the illusion would vanish. Same shader,
     * block atlas, lightmap and blending as {@code translucent}; {@code COLOR_WRITE} instead of
     * {@code COLOR_DEPTH_WRITE}; and the item-entity target as {@code translucentMovingBlock} does.
     */
    static RenderType block() {
        if (block == null) {
            block = RenderType.create("magical_illusion_block", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, BUFFER_BYTES,
                    false, true,
                    RenderType.CompositeState.builder()
                            .setLightmapState(RenderStateShard.LIGHTMAP)
                            .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                            .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                            .createCompositeState(true));
        }
        return block;
    }
}
