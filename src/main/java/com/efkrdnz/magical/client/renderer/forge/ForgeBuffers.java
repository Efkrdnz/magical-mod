package com.efkrdnz.magical.client.renderer.forge;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * The one rule the forge renderers have to obey about vertex buffers.
 *
 * <p>Neither {@code MagicalRenderTypes.forgeEdge()} nor {@code forgeImpact()} is one of the render
 * types {@code RenderBuffers} hands a fixed buffer, so both are built on the immediate source's
 * single {@code sharedBuffer}. {@code MultiBufferSource.BufferSource.getBuffer} therefore ends the
 * previous shared batch before it returns the next consumer, and ending a batch calls
 * {@code build()} on the old {@code BufferBuilder}, clearing its {@code building} flag. A consumer
 * fetched before that call is dead: writing to it throws
 * {@code IllegalStateException: Not building!}.</p>
 *
 * <p>So a forge visual that spans two render types finishes one, flushes it through here, and only
 * then asks for the other. Never two live consumers at once.</p>
 */
public final class ForgeBuffers {

    private ForgeBuffers() {}

    /**
     * Closes {@code type}'s batch so the next fetch starts clean. A source that cannot close one
     * (an outline pass, say) delegates to a {@code BufferSource} of its own, and that delegate ends
     * the shared batch on the next fetch anyway — which is why the ordering, not this call, is what
     * actually keeps the geometry alive.
     */
    public static void flush(MultiBufferSource buffer, RenderType type) {
        if (buffer instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(type);
        }
    }
}
