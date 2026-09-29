package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/** Draws a figment as its imagined kind - for the owner always, for anyone else once they half-believe it, for everyone once it is real. */
public final class FigmentRenderer extends EntityRenderer<FigmentEntity, FigmentRenderer.State> {
    public static final class State extends EntityRenderState {
        FigmentEntity figment;
        float partial;
    }

    public FigmentRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FigmentEntity figment, State state, float partial) {
        super.extractRenderState(figment, state, partial);
        state.figment = figment;
        state.partial = partial;
    }

    @Override
    public void render(State state, PoseStack pose, MultiBufferSource buffers, int light) {
        FigmentEntity figment = state.figment;
        if (figment == null) {
            return;
        }
        boolean real = figment.isManifested();
        if (!real && !ClientMind.sees(figment.sceneId(), figment.element())) {
            return;
        }
        AABB local = figment.getBoundingBox().move(figment.position().reverse());
        if (!real && ClientMind.mine(figment.sceneId())) {
            // The wielder is never fooled: their figments carry the same lilac edge as their blocks.
            IllusionRenderer.drawLocalEdge(pose, buffers, local);
        }
        float rim = real ? figment.hardening(state.partial) : 0.0F;
        if (rim > 0.0F) {
            IllusionRenderer.drawLocalEdge(pose, buffers, local, IllusionRenderer.EDGE_ALPHA * rim);
        }
        LivingEntity dummy = FigmentDummies.posed(figment);
        if (dummy != null) {
            Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, 0.0, 0.0, 0.0, state.partial, pose, buffers, light);
        }
    }
}
