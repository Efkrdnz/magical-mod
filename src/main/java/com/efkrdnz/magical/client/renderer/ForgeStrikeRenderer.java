package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.client.renderer.forge.ForgeElementAccent;
import com.efkrdnz.magical.client.renderer.forge.ForgeForms;
import com.efkrdnz.magical.client.renderer.forge.ForgePalette;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon;
import com.efkrdnz.magical.entity.ForgeStrikeEntity;
import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.FormFamily;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a forged strike. All this class does is pull the synced facts off the entity, aim the pose
 * down the strike's direction, and hand the frame to the geometry for that form family; the shapes
 * themselves live in {@code client.renderer.forge}.
 */
public final class ForgeStrikeRenderer extends EntityRenderer<ForgeStrikeEntity, ForgeStrikeRenderer.State> {

    private static final float ECHO_ALPHA = 0.45f;
    private static final float FADE_START = 0.55f;

    public ForgeStrikeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ForgeStrikeEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.family = entity.family();
        state.accent = ForgeElementAccent.of(entity.element());
        state.primary = entity.primaryColor();
        state.secondary = entity.secondaryColor();
        state.edge = entity.edgeColor();
        state.heavy = entity.heavy();
        state.halfWidth = entity.halfWidth();
        state.arc = entity.arc();
        state.reach = entity.reach();
        state.life = Math.max(1, entity.life());
        Vec3 dir = entity.direction();
        state.dirX = (float) dir.x;
        state.dirY = (float) dir.y;
        state.dirZ = (float) dir.z;
        state.progress = Mth.clamp(state.ageInTicks / state.life, 0.0f, 1.0f);
        state.alpha = (1.0f - ForgeRibbon.smoothstep(FADE_START, 1.0f, state.progress))
                * (entity.echo() ? ECHO_ALPHA : 1.0f) * state.accent.bodyAlpha();
        anchor(entity, state, partialTick);
    }

    /** An anchored family rides the wielder, drawn at the offset to their interpolated position. */
    private static void anchor(ForgeStrikeEntity entity, State state, float partialTick) {
        state.anchorX = 0.0f;
        state.anchorY = 0.0f;
        state.anchorZ = 0.0f;
        Entity owner = state.family.anchored() ? entity.level().getEntity(entity.ownerId()) : null;
        if (owner == null) {
            return;
        }
        Vec3 offset = owner.getPosition(partialTick).subtract(entity.getPosition(partialTick));
        state.anchorX = (float) offset.x;
        state.anchorY = (float) offset.y;
        state.anchorZ = (float) offset.z;
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (state.alpha > 0.0f) {
            draw(state, poseStack, buffer);
        }
        super.render(state, poseStack, buffer, packedLight);
    }

    private void draw(State state, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.translate(state.anchorX, state.anchorY, state.anchorZ);
        orient(state, poseStack);
        // The form table owns which render types this family needs and in which order it may fetch
        // them; nothing here holds a consumer of its own.
        ForgeForms.render(poseStack, buffer, state, new ForgePalette(state.primary, state.secondary, state.edge));
        poseStack.popPose();
    }

    /** Turns local +Z into the strike's aim, so every form is written facing forward. */
    private static void orient(State state, PoseStack poseStack) {
        Vec3 dir = new Vec3(state.dirX, state.dirY, state.dirZ);
        if (dir.lengthSqr() <= 1.0E-6) {
            return;
        }
        dir = dir.normalize();
        poseStack.mulPose(Axis.YP.rotation((float) Mth.atan2(dir.x, dir.z)));
        poseStack.mulPose(Axis.XP.rotation((float) -Math.asin(Mth.clamp(dir.y, -1.0, 1.0))));
    }

    /** Everything the geometry classes are allowed to know about a strike. */
    public static final class State extends EntityRenderState {
        public FormFamily family = FormFamily.SLASH;
        public ForgeElementAccent.Accent accent = ForgeElementAccent.of(ForgeElementKind.FIRE);
        public int primary = 0xD8E4FF, secondary = 0xD8E4FF, edge = 0xFFFFFF;
        public boolean heavy;
        public float halfWidth = 1.6f, arc = 150.0f, reach = 3.5f, life = 4.0f;
        // partialTick is inherited from EntityRenderState and already set by
        // EntityRenderer.extractRenderState; redeclaring it here would shadow the real one.
        public float progress, alpha;
        public float dirX, dirY, dirZ = 1.0f;
        /** Offset from this entity's render position to the wielder's, zero for a free form. */
        public float anchorX, anchorY, anchorZ;
    }
}
