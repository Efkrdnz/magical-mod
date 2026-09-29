package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/** Draws a figment as its imagined kind - for the owner always, for anyone else once they half-believe it. */
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
        if (figment == null || !ClientMind.sees(figment.sceneId(), figment.element())) {
            return;
        }
        LivingEntity dummy = FigmentDummies.posed(figment);
        if (dummy != null) {
            Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, 0.0, 0.0, 0.0, state.partial, pose, buffers, light);
        }
    }
}
