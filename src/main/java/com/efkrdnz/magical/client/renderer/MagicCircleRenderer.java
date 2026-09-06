package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.MagicCircleEffectEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public final class MagicCircleRenderer extends EntityRenderer<MagicCircleEffectEntity, MagicCircleRenderer.State> {
    public MagicCircleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MagicCircleEffectEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.color = entity.color();
        state.life = entity.life();
        state.style = entity.style();
        state.yaw = entity.yaw();
        state.pitch = entity.pitch();
        state.roll = entity.roll();
        state.detail = entity.detail();
        state.skillIndex = entity.skillIndex();
        state.role = entity.circleRole();
        state.distanceSqr = entity.distanceToSqr(entityRenderDispatcher.camera.getPosition());
    }

    @Override
    public boolean shouldRender(MagicCircleEffectEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        double extent = Math.max(1.0D, entity.radius() + 0.75D);
        AABB bounds = new AABB(entity.getX() - extent, entity.getY() - 0.7D, entity.getZ() - extent, entity.getX() + extent, entity.getY() + 0.7D, entity.getZ() + extent);
        return entity.shouldRender(cameraX, cameraY, cameraZ) && frustum.isVisible(bounds);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (state.skillIndex >= 0) {
            renderScripted(state, poseStack, buffer);
            super.render(state, poseStack, buffer, packedLight);
            return;
        }
        MagicCircleSpec spec = spec(state.style).simplified(state.detail);
        float progress = Mth.clamp(state.ageInTicks / Math.max(1.0F, state.life), 0.0F, 1.0F);
        float in = Mth.clamp(progress / 0.18F, 0.0F, 1.0F);
        float out = 1.0F - Mth.clamp((progress - 0.72F) / 0.28F, 0.0F, 1.0F);
        float fade = in * out;
        float pulse = 1.02F + Mth.sin(state.ageInTicks * 0.22F) * 0.045F;
        int red = red(state.color);
        int green = green(state.color);
        int blue = blue(state.color);
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll + state.ageInTicks * 1.3F));
        switch (state.style) {
            case MagicCircleEffectEntity.STYLE_MANA_FLIGHT -> drawFlightCircle(consumer, poseStack, state.radius * pulse, fade, state.ageInTicks);
            case MagicCircleEffectEntity.STYLE_MIST_STEP -> drawMistStepCircle(consumer, poseStack, spec, state.detail, state.radius * pulse, red, green, blue, fade, state.ageInTicks);
            case MagicCircleEffectEntity.STYLE_ANCHOR_SIGIL -> drawAnchorSigilCircle(consumer, poseStack, spec, state.detail, state.radius * pulse, red, green, blue, fade, state.ageInTicks);
            case MagicCircleEffectEntity.STYLE_CINDER_MARK -> drawCinderMarkCircle(consumer, poseStack, spec, state.detail, state.radius * pulse, red, green, blue, fade, state.ageInTicks);
            case MagicCircleEffectEntity.STYLE_PRISM_GUARD -> drawPrismGuardCircle(consumer, poseStack, spec, state.detail, state.radius * pulse, red, green, blue, fade, state.ageInTicks);
            case MagicCircleEffectEntity.STYLE_GLACIER_WAVE -> drawGlacierCircle(consumer, poseStack, spec, state.detail, state.radius * pulse, fade, state.ageInTicks);
            case MagicCircleEffectEntity.STYLE_BLACK_FLAMES -> drawBlackFlamesCircle(consumer, poseStack, spec, state.radius * pulse, fade, state.ageInTicks);
            case MagicCircleEffectEntity.STYLE_SOUL_VALLEY -> drawSoulValleyCircle(consumer, poseStack, spec, state.radius * pulse, fade, state.ageInTicks);
            default -> drawDivineRestorationCircle(consumer, poseStack, spec, state.detail, state.radius * pulse, red, green, blue, fade, state.ageInTicks);
        }
        poseStack.popPose();

        super.render(state, poseStack, buffer, packedLight);
    }

    /** Profile-driven circles: the CircleScript interpreter draws the skill's own sigil. */
    private static void renderScripted(State state, PoseStack poseStack, MultiBufferSource buffer) {
        com.efkrdnz.magical.magic.visual.VisualProfile profile = com.efkrdnz.magical.magic.visual.VisualProfiles.byIndex(state.skillIndex);
        boolean looping = state.role == MagicCircleEffectEntity.ROLE_TARGET || state.role == MagicCircleEffectEntity.ROLE_HOLD || state.role == MagicCircleEffectEntity.ROLE_LINGER;
        float life = looping ? 0.0F : Math.max(1.0F, state.life);
        float opacity = 1.0F;
        if (looping) {
            float remaining = state.life - state.ageInTicks;
            opacity = Mth.clamp(remaining / 12.0F, 0.0F, 1.0F) * Mth.clamp(state.ageInTicks / 6.0F, 0.0F, 1.0F);
        }
        int detail = com.efkrdnz.magical.client.renderer.fx.FxBudget.detailForDistance(state.detail, state.distanceSqr);
        com.efkrdnz.magical.magic.visual.CircleScript script = state.role == MagicCircleEffectEntity.ROLE_IMPACT ? profile.deliveryCircle() : profile.castCircle();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        com.efkrdnz.magical.client.renderer.fx.paint.GlyphCirclePainter.paint(script, profile.palette(), state.radius, state.ageInTicks, life, detail, poseStack, buffer, state.skillIndex & 63, false, opacity, 0.0F);
        poseStack.popPose();
    }

    private static MagicCircleSpec spec(int style) {
        return switch (style) {
            case MagicCircleEffectEntity.STYLE_MIST_STEP -> MagicCircleSpec.mistStep();
            case MagicCircleEffectEntity.STYLE_ANCHOR_SIGIL -> MagicCircleSpec.anchorSigil();
            case MagicCircleEffectEntity.STYLE_CINDER_MARK -> MagicCircleSpec.cinderMark();
            case MagicCircleEffectEntity.STYLE_PRISM_GUARD -> MagicCircleSpec.prismGuard();
            case MagicCircleEffectEntity.STYLE_GLACIER_WAVE -> MagicCircleSpec.glacierWave();
            case MagicCircleEffectEntity.STYLE_BLACK_FLAMES -> MagicCircleSpec.blackFlames();
            case MagicCircleEffectEntity.STYLE_SOUL_VALLEY -> MagicCircleSpec.soulValley();
            default -> MagicCircleSpec.divineRestoration();
        };
    }

    public static void renderManaFlightCircle(PoseStack poseStack, MultiBufferSource buffer, float radius, float fade, float age) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float pulse = 0.98F + Mth.sin(age * 0.22F) * 0.025F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.3F));
        drawFlightCircle(consumer, poseStack, radius * pulse, fade, age);
        poseStack.popPose();
    }

    public static void renderSoulValleyCircle(PoseStack poseStack, MultiBufferSource buffer, float radius, float fade, float age) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        float pulse = 0.98F + Mth.sin(age * 0.18F) * 0.025F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(age * 1.1F));
        drawSoulValleyCircle(consumer, poseStack, MagicCircleSpec.soulValley(), radius * pulse, fade, age);
        poseStack.popPose();
    }

    private static void drawDivineRestorationCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, int detail, float radius, int red, int green, int blue, float fade, float age) {
        drawCircle(consumer, poseStack, spec, detail, radius, red, green, blue, fade, age);
        int gold = 0xFFE65A;
        int pearl = 0xFFF8D6;
        int white = 0xFFFFFF;
        if (detail >= 1) {
            sunburst(consumer, poseStack, detail >= 3 ? 28 : 14, radius * 0.72F, radius * 1.14F, radius * 0.012F, gold, alpha(105, fade), age * 0.01F);
        }
        if (detail >= 2) {
            ring(consumer, poseStack, radius * 1.16F, radius * 0.008F, 144, red(pearl), green(pearl), blue(pearl), alpha(110, fade));
            petalRosette(consumer, poseStack, 14, radius * 0.44F, radius * 0.18F, radius * 0.006F, white, alpha(135, fade), age * 0.006F);
        }
        if (detail >= 3) {
            for (int i = 0; i < 7; i++) {
                float angle = Mth.TWO_PI * i / 7.0F - age * 0.003F;
                miniSeal(consumer, poseStack, Mth.cos(angle) * radius * 0.88F, Mth.sin(angle) * radius * 0.88F, 5 + i % 3, radius * 0.105F, i * 17.0F + age * 0.12F, red, green, blue, 255, 248, 214, fade);
            }
        }
    }

    private static void drawMistStepCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, int detail, float radius, int red, int green, int blue, float fade, float age) {
        drawCircle(consumer, poseStack, spec, detail, radius, red, green, blue, fade * 0.8F, age);
        int mist = 0xBDEBFF;
        int deep = 0x6A7DFF;
        if (detail >= 1) {
            arcRing(consumer, poseStack, radius * 1.13F, radius * 0.016F, 150, -18.0F + age * 0.5F, 124.0F, mist, alpha(168, fade));
            arcRing(consumer, poseStack, radius * 1.13F, radius * 0.016F, 150, 162.0F + age * 0.5F, 124.0F, mist, alpha(168, fade));
        }
        if (detail >= 2) {
            arcRing(consumer, poseStack, radius * 0.7F, radius * 0.012F, 120, 42.0F - age * 0.8F, 88.0F, deep, alpha(128, fade));
            arcRing(consumer, poseStack, radius * 0.7F, radius * 0.012F, 120, 222.0F - age * 0.8F, 88.0F, deep, alpha(128, fade));
            footprintPairs(consumer, poseStack, 4, radius * 0.52F, radius * 0.028F, deep, alpha(124, fade), age);
        }
        if (detail >= 3) {
            spiralRunes(consumer, poseStack, 2, 42, radius * 0.16F, radius * 0.92F, radius * 0.0048F, mist, alpha(108, fade), age);
        }
    }

    private static void drawAnchorSigilCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, int detail, float radius, int red, int green, int blue, float fade, float age) {
        drawCircle(consumer, poseStack, spec, detail, radius, red, green, blue, fade * 0.82F, age);
        int space = 0x7FEFFF;
        int anchor = 0xFFFFFF;
        if (detail >= 1) {
            compassNeedles(consumer, poseStack, radius * 0.2F, radius * 1.1F, radius * 0.01F, anchor, alpha(150, fade), age);
        }
        if (detail >= 2) {
            squareFrame(consumer, poseStack, radius * 0.72F, radius * 0.009F, age * 0.2F, space, alpha(146, fade));
            squareFrame(consumer, poseStack, radius * 0.44F, radius * 0.007F, 45.0F - age * 0.3F, anchor, alpha(118, fade));
        }
        if (detail >= 3) {
            orthogonalGrid(consumer, poseStack, radius * 0.86F, radius * 0.16F, radius * 0.0048F, space, alpha(85, fade));
            cornerLocks(consumer, poseStack, radius * 0.98F, radius * 0.12F, radius * 0.008F, space, alpha(132, fade), age);
        }
    }

    private static void drawCinderMarkCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, int detail, float radius, int red, int green, int blue, float fade, float age) {
        drawCircle(consumer, poseStack, spec, detail, radius, red, green, blue, fade * 0.72F, age);
        int ember = 0xFF6A1A;
        int hot = 0xFFE27A;
        int coal = 0x641208;
        ring(consumer, poseStack, radius * 0.32F, radius * 0.018F, 54, red(hot), green(hot), blue(hot), alpha(185, fade));
        if (detail >= 1) {
            flamePetals(consumer, poseStack, detail >= 3 ? 13 : 7, radius * 0.18F, radius * 1.08F, radius * 0.013F, ember, hot, alpha(168, fade), age);
        }
        if (detail >= 2) {
            triangularTeeth(consumer, poseStack, 18, radius * 0.94F, radius * 0.13F, radius * 0.007F, coal, alpha(168, fade), age * 0.008F);
        }
        if (detail >= 3) {
            lavaCracks(consumer, poseStack, 9, radius * 0.2F, radius * 0.78F, radius * 0.008F, hot, alpha(122, fade), age);
        }
    }

    private static void drawPrismGuardCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, int detail, float radius, int red, int green, int blue, float fade, float age) {
        drawCircle(consumer, poseStack, spec, detail, radius, red, green, blue, fade * 0.74F, age);
        int cyan = 0x7FEFFF;
        int violet = 0xC7A8FF;
        int white = 0xFFFFFF;
        if (detail >= 1) {
            arcRing(consumer, poseStack, radius * 1.12F, radius * 0.013F, 144, age * 0.35F, 52.0F, cyan, alpha(120, fade));
            arcRing(consumer, poseStack, radius * 1.12F, radius * 0.013F, 144, 120.0F + age * 0.35F, 52.0F, violet, alpha(120, fade));
            arcRing(consumer, poseStack, radius * 1.12F, radius * 0.013F, 144, 240.0F + age * 0.35F, 52.0F, white, alpha(120, fade));
        }
        if (detail >= 2) {
            shieldKites(consumer, poseStack, 6, radius * 0.72F, radius * 0.18F, radius * 0.0075F, white, alpha(128, fade), age);
        }
        if (detail >= 3) {
            crystalFacets(consumer, poseStack, 6, radius * 0.28F, radius * 0.96F, radius * 0.007F, cyan, violet, alpha(138, fade), age);
        }
    }

    private static void drawFlightCircle(VertexConsumer consumer, PoseStack poseStack, float radius, float fade, float age) {
        int green = 0x72F26A;
        int yellow = 0xFFE65A;
        int blue = 0x7FEFFF;
        drawCircle(consumer, poseStack, MagicCircleSpec.mistStep(), 3, radius, red(blue), green(blue), blue(blue), fade * 0.64F, age);
        wingArcs(consumer, poseStack, 3, radius * 0.78F, radius * 0.12F, radius * 0.013F, green, yellow, blue, alpha(170, fade), age);
        featherLines(consumer, poseStack, 18, radius * 0.42F, radius * 1.03F, radius * 0.006F, green, alpha(118, fade), age);
        waveRing(consumer, poseStack, 128, radius * 0.62F, radius * 0.055F, 3, radius * 0.006F, red(yellow), green(yellow), blue(yellow), alpha(120, fade), age * 0.05F);
        polygon(consumer, poseStack, 3, radius * 0.36F, radius * 0.014F, -90.0F - age * 0.8F, red(yellow), green(yellow), blue(yellow), alpha(180, fade));
    }

    private static void drawGlacierCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, int detail, float radius, float fade, float age) {
        int deepIce = 0x45C7FF;
        int paleIce = 0xBDF8FF;
        int white = 0xF4FFFF;
        drawCircle(consumer, poseStack, spec, detail, radius, red(paleIce), green(paleIce), blue(paleIce), fade * 0.76F, age);
        if (detail >= 1) {
            snowflake(consumer, poseStack, 7, radius * 0.12F, radius * 0.86F, radius * 0.009F, red(white), green(white), blue(white), alpha(170, fade));
        }
        if (detail >= 2) {
            fracturedShardRing(consumer, poseStack, 16, radius * 0.47F, radius * 1.08F, radius * 0.009F, paleIce, deepIce, alpha(165, fade), age);
            ring(consumer, poseStack, radius * 1.18F, radius * 0.006F, 128, red(white), green(white), blue(white), alpha(105, fade));
        }
        if (detail >= 3) {
            snowflake(consumer, poseStack, 13, radius * 0.38F, radius * 0.72F, radius * 0.0046F, red(deepIce), green(deepIce), blue(deepIce), alpha(118, fade));
            jaggedFaultLines(consumer, poseStack, 9, radius * 0.18F, radius * 0.98F, radius * 0.006F, white, alpha(132, fade), age);
        }
    }

    private static void drawBlackFlamesCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, float radius, float fade, float age) {
        int abyss = 0x09020D;
        int ember = 0x9E160A;
        int violet = 0x3C0458;
        int blood = 0xD52A18;
        drawCircle(consumer, poseStack, spec, 3, radius, red(violet), green(violet), blue(violet), fade * 0.72F, age);
        thornCrown(consumer, poseStack, 21, radius * 0.74F, radius * 1.14F, radius * 0.012F, abyss, blood, alpha(205, fade), age);
        flamePetals(consumer, poseStack, 11, radius * 0.16F, radius * 0.86F, radius * 0.011F, violet, ember, alpha(148, fade), -age);
        invertedRunes(consumer, poseStack, 18, radius * 0.96F, radius * 0.06F, radius * 0.005F, blood, alpha(138, fade), age);
        pentagram(consumer, poseStack, radius * 0.72F, radius * 0.014F, -90.0F - age * 0.16F, red(abyss), green(abyss), blue(abyss), alpha(235, fade));
        ring(consumer, poseStack, radius * 0.21F, radius * 0.021F, 66, red(abyss), green(abyss), blue(abyss), alpha(245, fade));
    }

    private static void drawSoulValleyCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, float radius, float fade, float age) {
        int soul = 0xD8F0FF;
        int veil = 0x7B7CFF;
        int vow = 0xF7FEFF;
        int pale = 0x9DE5FF;
        drawCircle(consumer, poseStack, spec, 3, radius, red(soul), green(soul), blue(soul), fade * 0.72F, age);
        infinityChain(consumer, poseStack, 18, radius * 0.72F, radius * 0.09F, radius * 0.0048F, red(vow), green(vow), blue(vow), alpha(150, fade));
        soulLobes(consumer, poseStack, radius * 0.3F, radius * 0.42F, radius * 0.009F, pale, veil, alpha(148, fade), age);
        vowKnots(consumer, poseStack, 11, radius * 0.96F, radius * 0.075F, radius * 0.0055F, soul, vow, alpha(134, fade), age);
        teardropRunes(consumer, poseStack, 22, radius * 0.52F, radius * 0.055F, radius * 0.0048F, pale, alpha(118, fade), age);
        polygon(consumer, poseStack, 3, radius * 0.28F, radius * 0.014F, -90.0F + age * 0.9F, red(vow), green(vow), blue(vow), alpha(198, fade));
    }

    private static void sunburst(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int color, int alpha, float phase) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + phase;
            float inset = innerRadius + ((i & 1) == 0 ? 0.0F : (outerRadius - innerRadius) * 0.28F);
            line(consumer, poseStack,
                    Mth.cos(angle) * inset, Mth.sin(angle) * inset,
                    Mth.cos(angle) * outerRadius, Mth.sin(angle) * outerRadius,
                    thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void petalRosette(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float petalLength, float thickness, int color, int alpha, float phase) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + phase;
            float tangent = angle + Mth.HALF_PI;
            float centerX = Mth.cos(angle) * radius;
            float centerY = Mth.sin(angle) * radius;
            float tipX = Mth.cos(angle) * (radius + petalLength);
            float tipY = Mth.sin(angle) * (radius + petalLength);
            float leftX = centerX + Mth.cos(tangent) * petalLength * 0.34F;
            float leftY = centerY + Mth.sin(tangent) * petalLength * 0.34F;
            float rightX = centerX - Mth.cos(tangent) * petalLength * 0.34F;
            float rightY = centerY - Mth.sin(tangent) * petalLength * 0.34F;
            line(consumer, poseStack, leftX, leftY, tipX, tipY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, tipX, tipY, rightX, rightY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, rightX, rightY, leftX, leftY, thickness * 0.7F, red(color), green(color), blue(color), alpha);
        }
    }

    private static void arcRing(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, int segments, float startDegrees, float sweepDegrees, int color, int alpha) {
        int steps = Math.max(4, Math.round(segments * Math.abs(sweepDegrees) / 360.0F));
        float start = startDegrees * Mth.DEG_TO_RAD;
        float sweep = sweepDegrees * Mth.DEG_TO_RAD;
        float previousX = Mth.cos(start) * radius;
        float previousY = Mth.sin(start) * radius;
        for (int i = 1; i <= steps; i++) {
            float angle = start + sweep * i / steps;
            float x = Mth.cos(angle) * radius;
            float y = Mth.sin(angle) * radius;
            line(consumer, poseStack, previousX, previousY, x, y, thickness, red(color), green(color), blue(color), alpha);
            previousX = x;
            previousY = y;
        }
    }

    private static void spiralRunes(VertexConsumer consumer, PoseStack poseStack, int arms, int steps, float innerRadius, float outerRadius, float thickness, int color, int alpha, float age) {
        for (int arm = 0; arm < arms; arm++) {
            float offset = Mth.TWO_PI * arm / arms + age * 0.016F;
            float previousX = Mth.cos(offset) * innerRadius;
            float previousY = Mth.sin(offset) * innerRadius;
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float angle = offset + t * Mth.TWO_PI * 0.82F;
                float r = Mth.lerp(t, innerRadius, outerRadius);
                float x = Mth.cos(angle) * r;
                float y = Mth.sin(angle) * r;
                line(consumer, poseStack, previousX, previousY, x, y, thickness, red(color), green(color), blue(color), alpha);
                previousX = x;
                previousY = y;
            }
        }
    }

    private static void footprintPairs(VertexConsumer consumer, PoseStack poseStack, int pairs, float radius, float size, int color, int alpha, float age) {
        for (int i = 0; i < pairs; i++) {
            float angle = Mth.TWO_PI * i / pairs + age * 0.01F;
            float tangent = angle + Mth.HALF_PI;
            for (int side = -1; side <= 1; side += 2) {
                float cx = Mth.cos(angle) * radius + Mth.cos(tangent) * size * side;
                float cy = Mth.sin(angle) * radius + Mth.sin(tangent) * size * side;
                ringAt(consumer, poseStack, cx, cy, size * 0.62F, size * 0.12F, 18, red(color), green(color), blue(color), alpha(75, alpha / 255.0F));
                line(consumer, poseStack, cx - Mth.cos(angle) * size * 0.8F, cy - Mth.sin(angle) * size * 0.8F, cx + Mth.cos(angle) * size * 0.8F, cy + Mth.sin(angle) * size * 0.8F, size * 0.16F, red(color), green(color), blue(color), alpha);
            }
        }
    }

    private static void orthogonalGrid(VertexConsumer consumer, PoseStack poseStack, float halfSize, float spacing, float thickness, int color, int alpha) {
        for (float offset = -halfSize; offset <= halfSize + 0.001F; offset += spacing) {
            line(consumer, poseStack, -halfSize, offset, halfSize, offset, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, offset, -halfSize, offset, halfSize, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void compassNeedles(VertexConsumer consumer, PoseStack poseStack, float innerRadius, float outerRadius, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < 4; i++) {
            float angle = Mth.HALF_PI * i + age * 0.004F;
            line(consumer, poseStack, Mth.cos(angle) * innerRadius, Mth.sin(angle) * innerRadius, Mth.cos(angle) * outerRadius, Mth.sin(angle) * outerRadius, thickness, red(color), green(color), blue(color), alpha);
            float tangent = angle + Mth.HALF_PI;
            float tipX = Mth.cos(angle) * outerRadius;
            float tipY = Mth.sin(angle) * outerRadius;
            line(consumer, poseStack, tipX, tipY, tipX - Mth.cos(angle) * outerRadius * 0.11F + Mth.cos(tangent) * outerRadius * 0.055F, tipY - Mth.sin(angle) * outerRadius * 0.11F + Mth.sin(tangent) * outerRadius * 0.055F, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, tipX, tipY, tipX - Mth.cos(angle) * outerRadius * 0.11F - Mth.cos(tangent) * outerRadius * 0.055F, tipY - Mth.sin(angle) * outerRadius * 0.11F - Mth.sin(tangent) * outerRadius * 0.055F, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void squareFrame(VertexConsumer consumer, PoseStack poseStack, float halfSize, float thickness, float degreesOffset, int color, int alpha) {
        polygon(consumer, poseStack, 4, halfSize * Mth.SQRT_OF_TWO, thickness, 45.0F + degreesOffset, red(color), green(color), blue(color), alpha);
    }

    private static void cornerLocks(VertexConsumer consumer, PoseStack poseStack, float radius, float length, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < 4; i++) {
            float angle = Mth.HALF_PI * i + Mth.PI / 4.0F + age * 0.003F;
            float tangent = angle + Mth.HALF_PI;
            float cx = Mth.cos(angle) * radius;
            float cy = Mth.sin(angle) * radius;
            line(consumer, poseStack, cx, cy, cx - Mth.cos(angle) * length, cy - Mth.sin(angle) * length, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, cx, cy, cx + Mth.cos(tangent) * length * 0.45F, cy + Mth.sin(tangent) * length * 0.45F, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, cx, cy, cx - Mth.cos(tangent) * length * 0.45F, cy - Mth.sin(tangent) * length * 0.45F, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void flamePetals(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int colorA, int colorB, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + age * 0.012F;
            float bend = Mth.sin(age * 0.08F + i) * 0.12F;
            float tangent = angle + Mth.HALF_PI;
            float baseX = Mth.cos(angle) * innerRadius;
            float baseY = Mth.sin(angle) * innerRadius;
            float midX = Mth.cos(angle + bend) * ((innerRadius + outerRadius) * 0.52F) + Mth.cos(tangent) * outerRadius * 0.06F;
            float midY = Mth.sin(angle + bend) * ((innerRadius + outerRadius) * 0.52F) + Mth.sin(tangent) * outerRadius * 0.06F;
            float tipX = Mth.cos(angle - bend * 0.6F) * outerRadius;
            float tipY = Mth.sin(angle - bend * 0.6F) * outerRadius;
            line(consumer, poseStack, baseX, baseY, midX, midY, thickness, red(colorA), green(colorA), blue(colorA), alpha);
            line(consumer, poseStack, midX, midY, tipX, tipY, thickness * 0.75F, red(colorB), green(colorB), blue(colorB), alpha);
        }
    }

    private static void triangularTeeth(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float height, float thickness, int color, int alpha, float phase) {
        for (int i = 0; i < count; i++) {
            float a = Mth.TWO_PI * i / count + phase;
            float b = Mth.TWO_PI * (i + 0.5F) / count + phase;
            float c = Mth.TWO_PI * (i + 1.0F) / count + phase;
            float outer = radius + height * (i % 2 == 0 ? 1.0F : 0.55F);
            line(consumer, poseStack, Mth.cos(a) * radius, Mth.sin(a) * radius, Mth.cos(b) * outer, Mth.sin(b) * outer, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, Mth.cos(b) * outer, Mth.sin(b) * outer, Mth.cos(c) * radius, Mth.sin(c) * radius, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void lavaCracks(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + age * 0.006F;
            float r = innerRadius;
            float x = Mth.cos(angle) * r;
            float y = Mth.sin(angle) * r;
            for (int step = 1; step <= 4; step++) {
                float nextAngle = angle + Mth.sin(i * 1.7F + step + age * 0.04F) * 0.16F;
                float nextR = Mth.lerp(step / 4.0F, innerRadius, outerRadius);
                float nx = Mth.cos(nextAngle) * nextR;
                float ny = Mth.sin(nextAngle) * nextR;
                line(consumer, poseStack, x, y, nx, ny, thickness * (1.1F - step * 0.14F), red(color), green(color), blue(color), alpha);
                x = nx;
                y = ny;
            }
        }
    }

    private static void crystalFacets(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int colorA, int colorB, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float a = Mth.TWO_PI * i / count + age * 0.005F;
            float b = Mth.TWO_PI * (i + 1) / count + age * 0.005F;
            float mid = (a + b) * 0.5F;
            line(consumer, poseStack, Mth.cos(a) * innerRadius, Mth.sin(a) * innerRadius, Mth.cos(mid) * outerRadius, Mth.sin(mid) * outerRadius, thickness, red(colorA), green(colorA), blue(colorA), alpha);
            line(consumer, poseStack, Mth.cos(mid) * outerRadius, Mth.sin(mid) * outerRadius, Mth.cos(b) * innerRadius, Mth.sin(b) * innerRadius, thickness, red(colorB), green(colorB), blue(colorB), alpha);
            line(consumer, poseStack, Mth.cos(a) * innerRadius, Mth.sin(a) * innerRadius, Mth.cos(b) * innerRadius, Mth.sin(b) * innerRadius, thickness * 0.7F, red(colorA), green(colorA), blue(colorA), alpha);
        }
    }

    private static void shieldKites(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float size, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count - age * 0.004F;
            float tangent = angle + Mth.HALF_PI;
            float cx = Mth.cos(angle) * radius;
            float cy = Mth.sin(angle) * radius;
            float topX = cx + Mth.cos(angle) * size;
            float topY = cy + Mth.sin(angle) * size;
            float bottomX = cx - Mth.cos(angle) * size * 0.8F;
            float bottomY = cy - Mth.sin(angle) * size * 0.8F;
            float leftX = cx - Mth.cos(tangent) * size * 0.45F;
            float leftY = cy - Mth.sin(tangent) * size * 0.45F;
            float rightX = cx + Mth.cos(tangent) * size * 0.45F;
            float rightY = cy + Mth.sin(tangent) * size * 0.45F;
            line(consumer, poseStack, topX, topY, rightX, rightY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, rightX, rightY, bottomX, bottomY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, bottomX, bottomY, leftX, leftY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, leftX, leftY, topX, topY, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void wingArcs(VertexConsumer consumer, PoseStack poseStack, int wings, float radius, float spread, float thickness, int green, int yellow, int blue, int alpha, float age) {
        int[] colors = {green, yellow, blue};
        for (int i = 0; i < wings; i++) {
            float base = 120.0F * i + age * 0.55F;
            arcRing(consumer, poseStack, radius - spread * 0.5F, thickness, 96, base - 56.0F, 112.0F, colors[i % colors.length], alpha);
            arcRing(consumer, poseStack, radius + spread * 0.5F, thickness * 0.72F, 96, base - 42.0F, 84.0F, colors[(i + 1) % colors.length], alpha(125, alpha / 255.0F));
        }
    }

    private static void featherLines(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + age * 0.012F;
            float tangent = angle + Mth.HALF_PI;
            float outer = outerRadius * (0.82F + (i % 3) * 0.08F);
            float x1 = Mth.cos(angle) * innerRadius;
            float y1 = Mth.sin(angle) * innerRadius;
            float x2 = Mth.cos(angle) * outer + Mth.cos(tangent) * outerRadius * 0.08F;
            float y2 = Mth.sin(angle) * outer + Mth.sin(tangent) * outerRadius * 0.08F;
            line(consumer, poseStack, x1, y1, x2, y2, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void fracturedShardRing(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int colorA, int colorB, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float a = Mth.TWO_PI * i / count + age * 0.003F;
            float width = Mth.TWO_PI / count * (0.36F + (i % 4) * 0.08F);
            float r1 = innerRadius * (0.9F + (i % 3) * 0.06F);
            float r2 = outerRadius * (0.82F + (i % 5) * 0.045F);
            float left = a - width;
            float right = a + width * 0.72F;
            float tipX = Mth.cos(a) * r2;
            float tipY = Mth.sin(a) * r2;
            float leftX = Mth.cos(left) * r1;
            float leftY = Mth.sin(left) * r1;
            float rightX = Mth.cos(right) * r1;
            float rightY = Mth.sin(right) * r1;
            int color = i % 2 == 0 ? colorA : colorB;
            line(consumer, poseStack, leftX, leftY, tipX, tipY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, tipX, tipY, rightX, rightY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, rightX, rightY, leftX, leftY, thickness * 0.5F, red(color), green(color), blue(color), alpha(90, alpha / 255.0F));
        }
    }

    private static void jaggedFaultLines(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + age * 0.004F;
            float x = Mth.cos(angle) * innerRadius;
            float y = Mth.sin(angle) * innerRadius;
            for (int step = 1; step <= 5; step++) {
                float r = Mth.lerp(step / 5.0F, innerRadius, outerRadius);
                float offset = ((step & 1) == 0 ? 1.0F : -1.0F) * 0.08F;
                float nx = Mth.cos(angle + offset) * r;
                float ny = Mth.sin(angle + offset) * r;
                line(consumer, poseStack, x, y, nx, ny, thickness, red(color), green(color), blue(color), alpha);
                x = nx;
                y = ny;
            }
        }
    }

    private static void thornCrown(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int colorA, int colorB, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float a = Mth.TWO_PI * i / count - age * 0.006F;
            float b = a + Mth.TWO_PI / count * 0.42F;
            float c = a - Mth.TWO_PI / count * 0.42F;
            int color = i % 2 == 0 ? colorA : colorB;
            line(consumer, poseStack, Mth.cos(b) * innerRadius, Mth.sin(b) * innerRadius, Mth.cos(a) * outerRadius, Mth.sin(a) * outerRadius, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, Mth.cos(a) * outerRadius, Mth.sin(a) * outerRadius, Mth.cos(c) * innerRadius, Mth.sin(c) * innerRadius, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void invertedRunes(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float height, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + age * 0.005F;
            float tangent = angle + Mth.HALF_PI;
            float cx = Mth.cos(angle) * radius;
            float cy = Mth.sin(angle) * radius;
            line(consumer, poseStack, cx, cy, cx - Mth.cos(angle) * height, cy - Mth.sin(angle) * height, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, cx, cy, cx + Mth.cos(tangent) * height * 0.48F, cy + Mth.sin(tangent) * height * 0.48F, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void soulLobes(VertexConsumer consumer, PoseStack poseStack, float offset, float radius, float thickness, int colorA, int colorB, int alpha, float age) {
        arcRing(consumer, poseStack, radius, thickness, 92, -35.0F + age * 0.2F, 250.0F, colorA, alpha);
        arcRing(consumer, poseStack, radius, thickness, 92, 145.0F - age * 0.2F, 250.0F, colorB, alpha);
        ringAt(consumer, poseStack, -offset, 0.0F, radius * 0.55F, thickness * 0.6F, 54, red(colorA), green(colorA), blue(colorA), alpha(95, alpha / 255.0F));
        ringAt(consumer, poseStack, offset, 0.0F, radius * 0.55F, thickness * 0.6F, 54, red(colorB), green(colorB), blue(colorB), alpha(95, alpha / 255.0F));
    }

    private static void vowKnots(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float glyphScale, float thickness, int colorA, int colorB, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + age * 0.003F;
            float cx = Mth.cos(angle) * radius;
            float cy = Mth.sin(angle) * radius;
            int color = i % 2 == 0 ? colorA : colorB;
            miniSeal(consumer, poseStack, cx, cy, 4 + i % 4, glyphScale, i * 21.0F - age * 0.12F, red(color), green(color), blue(color), red(colorB), green(colorB), blue(colorB), alpha / 255.0F);
        }
    }

    private static void teardropRunes(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float size, float thickness, int color, int alpha, float age) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count - age * 0.004F;
            float tangent = angle + Mth.HALF_PI;
            float cx = Mth.cos(angle) * radius;
            float cy = Mth.sin(angle) * radius;
            float tipX = cx + Mth.cos(angle) * size;
            float tipY = cy + Mth.sin(angle) * size;
            float leftX = cx - Mth.cos(angle) * size * 0.5F + Mth.cos(tangent) * size * 0.42F;
            float leftY = cy - Mth.sin(angle) * size * 0.5F + Mth.sin(tangent) * size * 0.42F;
            float rightX = cx - Mth.cos(angle) * size * 0.5F - Mth.cos(tangent) * size * 0.42F;
            float rightY = cy - Mth.sin(angle) * size * 0.5F - Mth.sin(tangent) * size * 0.42F;
            line(consumer, poseStack, tipX, tipY, leftX, leftY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, leftX, leftY, rightX, rightY, thickness, red(color), green(color), blue(color), alpha);
            line(consumer, poseStack, rightX, rightY, tipX, tipY, thickness, red(color), green(color), blue(color), alpha);
        }
    }

    private static void drawCircle(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, int detail, float radius, int red, int green, int blue, float fade, float age) {
        int brightRed = Math.min(255, red + 36);
        int brightGreen = Math.min(255, green + 28);
        int brightBlue = Math.min(255, blue + 68);
        int hotRed = Math.min(255, red + 78);
        int hotGreen = Math.min(255, green + 66);
        int hotBlue = Math.min(255, blue + 98);
        int dimRed = Math.max(0, red - 28);
        int dimGreen = Math.max(0, green - 40);
        int dimBlue = Math.max(0, blue - 28);

        if (spec.circularLayers()) {
            layeredRings(consumer, poseStack, spec, radius, red, green, blue, brightRed, brightGreen, brightBlue, hotRed, hotGreen, hotBlue, fade, age);
        }

        polygon(consumer, poseStack, spec.outerSides(), radius * 1.0F, radius * 0.023F, -90.0F + age * 0.08F, red, green, blue, alpha(234, fade));
        if (detail >= 3) {
            polygon(consumer, poseStack, Math.max(3, spec.outerSides() + 2), radius * 0.82F, radius * 0.011F, -30.0F - age * 0.18F, brightRed, brightGreen, brightBlue, alpha(142, fade));
            polygon(consumer, poseStack, spec.middleSides(), radius * 0.56F, radius * 0.009F, -54.0F - age * 0.42F, dimRed, green, blue, alpha(132, fade));
        }
        if (detail >= 1) {
            polygon(consumer, poseStack, spec.middleSides(), radius * 0.34F, radius * 0.014F, -90.0F + age * 0.75F, brightRed, brightGreen, brightBlue, alpha(198, fade));
        }

        if (spec.starPoints() > 0) {
            starPolygon(consumer, poseStack, spec.starPoints(), radius * 0.68F, radius * 0.009F, -90.0F + age * 0.13F, brightRed, brightGreen, brightBlue, alpha(154, fade));
        }
        if (spec.secondaryStarPoints() > 0) {
            starPolygon(consumer, poseStack, spec.secondaryStarPoints(), radius * 0.48F, radius * 0.0065F, -90.0F - age * 0.28F, hotRed, hotGreen, hotBlue, alpha(108, fade));
        }
        if (spec.pentagram()) {
            pentagram(consumer, poseStack, radius * 0.62F, radius * 0.011F, -90.0F, brightRed, brightGreen, brightBlue, alpha(192, fade));
        }
        if (spec.spokeCount() > 0) {
            spokes(consumer, poseStack, spec.spokeCount() * 2, radius * 0.28F, radius * 0.98F, radius * 0.0075F, red, green, blue, alpha(122, fade));
            if (detail >= 2) {
                brokenSpokes(consumer, poseStack, spec.spokeCount(), radius * 0.18F, radius * 0.92F, radius * 0.008F, dimRed, dimGreen, dimBlue, alpha(84, fade), age);
            }
        }
        if (spec.zigzagRing()) {
            zigzagRing(consumer, poseStack, spec.ringSegments(), radius * 0.74F, radius * 0.055F, radius * 0.006F, brightRed, green, blue, alpha(128, fade));
        }
        if (spec.infinityChain()) {
            infinityChain(consumer, poseStack, spec.glyphCount(), radius * 0.89F, radius * 0.066F, radius * 0.0048F, brightRed, brightGreen, brightBlue, alpha(118, fade));
        }
        if (spec.waveRing()) {
            waveRing(consumer, poseStack, spec.ringSegments(), radius * 0.67F, radius * 0.028F, 5 + spec.middleSides() % 5, radius * 0.0048F, hotRed, hotGreen, hotBlue, alpha(106, fade), age * 0.025F);
        }
        if (spec.braidRing()) {
            braidedRing(consumer, poseStack, spec.ringSegments(), radius * 0.93F, radius * 0.022F, radius * 0.0048F, red, green, blue, brightRed, brightGreen, brightBlue, alpha(118, fade), age);
        }
        if (spec.diamondMarks()) {
            diamondMarks(consumer, poseStack, spec.glyphCount() + spec.outerSides(), radius * 0.61F, radius * 0.036F, radius * 0.0048F, hotRed, hotGreen, hotBlue, alpha(118, fade), age);
        }
        if (spec.orbitingSealCount() > 0) {
            orbitingSeals(consumer, poseStack, spec.orbitingSealCount(), Math.max(3, spec.middleSides()), radius * 0.84F, radius * 0.095F, red, green, blue, brightRed, brightGreen, brightBlue, fade, age);
        }

        for (int band = 0; band < Math.max(1, spec.glyphBands()); band++) {
            float bandRadius = radius * (0.42F + band * 0.19F);
            int count = spec.glyphCount() * (band + 2);
            float phase = band * 17.0F + age * (0.08F + band * 0.015F);
            repeatingGlyphMarks(consumer, poseStack, count, bandRadius, radius * (0.038F + band * 0.006F), radius * 0.0046F, band % 2 == 0 ? red : brightRed, band % 2 == 0 ? green : brightGreen, band % 2 == 0 ? blue : brightBlue, alpha(124 - band * 10, fade), phase);
        }
        if (detail >= 2) {
            repeatingGlyphMarks(consumer, poseStack, spec.glyphCount() * 2, radius * 1.01F, radius * 0.065F, radius * 0.006F, brightRed, brightGreen, brightBlue, alpha(132, fade), age * 0.04F);
        }
        ring(consumer, poseStack, radius * 0.16F, radius * 0.016F, spec.ringSegments() / 2, brightRed, brightGreen, brightBlue, alpha(195, fade));
    }

    private static void layeredRings(VertexConsumer consumer, PoseStack poseStack, MagicCircleSpec spec, float radius, int red, int green, int blue, int brightRed, int brightGreen, int brightBlue, int hotRed, int hotGreen, int hotBlue, float fade, float age) {
        int layers = Math.max(1, spec.layerCount());
        for (int i = 0; i < layers; i++) {
            float t = layers == 1 ? 0.0F : i / (float) (layers - 1);
            float ringRadius = radius * Mth.lerp(t, 1.08F, 0.22F);
            float thickness = radius * Mth.lerp(t, 0.017F, 0.005F);
            int segmentCount = Math.max(24, spec.ringSegments() - i * 8);
            int a = alpha(Math.max(68, 172 - i * 12), fade);
            if (i % 3 == 0) {
                ring(consumer, poseStack, ringRadius, thickness, segmentCount, brightRed, brightGreen, brightBlue, a);
            } else if (i % 3 == 1) {
                ring(consumer, poseStack, ringRadius, thickness, segmentCount, red, green, blue, a);
            } else {
                ring(consumer, poseStack, ringRadius, thickness, segmentCount, hotRed, hotGreen, hotBlue, alpha(Math.max(55, 130 - i * 10), fade));
            }
            if ((i & 1) == 1) {
                polygon(consumer, poseStack, Math.max(3, spec.outerSides() + i), ringRadius * 0.985F, thickness * 0.55F, -90.0F + age * (0.03F + i * 0.015F), red, green, blue, alpha(72, fade));
            }
        }
    }

    private static void ring(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, int segments, int red, int green, int blue, int alpha) {
        float inner = Math.max(0.01F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < segments; i++) {
            float angleA = Mth.TWO_PI * i / segments;
            float angleB = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    Mth.cos(angleA) * inner, Mth.sin(angleA) * inner,
                    Mth.cos(angleA) * outer, Mth.sin(angleA) * outer,
                    Mth.cos(angleB) * outer, Mth.sin(angleB) * outer,
                    Mth.cos(angleB) * inner, Mth.sin(angleB) * inner,
                    red, green, blue, alpha);
        }
    }

    private static void polygon(VertexConsumer consumer, PoseStack poseStack, int sides, float radius, float thickness, float degreesOffset, int red, int green, int blue, int alpha) {
        int safeSides = Math.max(3, sides);
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < safeSides; i++) {
            float angleA = offset + Mth.TWO_PI * i / safeSides;
            float angleB = offset + Mth.TWO_PI * (i + 1) / safeSides;
            line(consumer, poseStack,
                    Mth.cos(angleA) * radius, Mth.sin(angleA) * radius,
                    Mth.cos(angleB) * radius, Mth.sin(angleB) * radius,
                    thickness, red, green, blue, alpha);
        }
    }

    private static void pentagram(VertexConsumer consumer, PoseStack poseStack, float radius, float thickness, float degreesOffset, int red, int green, int blue, int alpha) {
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < 5; i++) {
            int next = (i + 2) % 5;
            float angleA = offset + Mth.TWO_PI * i / 5.0F;
            float angleB = offset + Mth.TWO_PI * next / 5.0F;
            line(consumer, poseStack,
                    Mth.cos(angleA) * radius, Mth.sin(angleA) * radius,
                    Mth.cos(angleB) * radius, Mth.sin(angleB) * radius,
                    thickness, red, green, blue, alpha);
        }
    }

    private static void starPolygon(VertexConsumer consumer, PoseStack poseStack, int points, float radius, float thickness, float degreesOffset, int red, int green, int blue, int alpha) {
        int safePoints = Math.max(5, points);
        int skip = safePoints % 2 == 0 ? safePoints / 2 - 1 : safePoints / 2;
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < safePoints; i++) {
            int next = (i + skip) % safePoints;
            float angleA = offset + Mth.TWO_PI * i / safePoints;
            float angleB = offset + Mth.TWO_PI * next / safePoints;
            line(consumer, poseStack,
                    Mth.cos(angleA) * radius, Mth.sin(angleA) * radius,
                    Mth.cos(angleB) * radius, Mth.sin(angleB) * radius,
                    thickness, red, green, blue, alpha);
        }
    }

    private static void spokes(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            line(consumer, poseStack,
                    Mth.cos(angle) * innerRadius, Mth.sin(angle) * innerRadius,
                    Mth.cos(angle) * outerRadius, Mth.sin(angle) * outerRadius,
                    thickness, red, green, blue, alpha);
        }
    }

    private static void brokenSpokes(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int red, int green, int blue, int alpha, float age) {
        int safeCount = Math.max(3, count);
        for (int i = 0; i < safeCount; i++) {
            float angle = Mth.TWO_PI * (i + 0.5F) / safeCount + age * 0.006F;
            for (int segment = 0; segment < 3; segment++) {
                float a = segment / 3.0F;
                float b = a + 0.16F;
                float r1 = Mth.lerp(a, innerRadius, outerRadius);
                float r2 = Mth.lerp(b, innerRadius, outerRadius);
                line(consumer, poseStack,
                        Mth.cos(angle) * r1, Mth.sin(angle) * r1,
                        Mth.cos(angle) * r2, Mth.sin(angle) * r2,
                        thickness, red, green, blue, alpha);
            }
        }
    }

    private static void snowflake(VertexConsumer consumer, PoseStack poseStack, int count, float innerRadius, float outerRadius, float thickness, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            float tipX = Mth.cos(angle) * outerRadius;
            float tipY = Mth.sin(angle) * outerRadius;
            float baseX = Mth.cos(angle) * innerRadius;
            float baseY = Mth.sin(angle) * innerRadius;
            line(consumer, poseStack, baseX, baseY, tipX, tipY, thickness, red, green, blue, alpha);
            float branchRadius = outerRadius * 0.64F;
            float branchLength = outerRadius * 0.12F;
            float centerX = Mth.cos(angle) * branchRadius;
            float centerY = Mth.sin(angle) * branchRadius;
            float branchA = angle + 0.72F;
            float branchB = angle - 0.72F;
            line(consumer, poseStack, centerX, centerY, centerX + Mth.cos(branchA) * branchLength, centerY + Mth.sin(branchA) * branchLength, thickness * 0.75F, red, green, blue, alpha);
            line(consumer, poseStack, centerX, centerY, centerX + Mth.cos(branchB) * branchLength, centerY + Mth.sin(branchB) * branchLength, thickness * 0.75F, red, green, blue, alpha);
        }
    }

    private static void zigzagRing(VertexConsumer consumer, PoseStack poseStack, int segments, float radius, float amplitude, float thickness, int red, int green, int blue, int alpha) {
        int steps = Math.max(12, segments);
        float previousX = radius + amplitude;
        float previousY = 0.0F;
        for (int i = 1; i <= steps; i++) {
            float angle = Mth.TWO_PI * i / steps;
            float currentRadius = radius + ((i & 1) == 0 ? amplitude : -amplitude);
            float x = Mth.cos(angle) * currentRadius;
            float y = Mth.sin(angle) * currentRadius;
            line(consumer, poseStack, previousX, previousY, x, y, thickness, red, green, blue, alpha);
            previousX = x;
            previousY = y;
        }
    }

    private static void waveRing(VertexConsumer consumer, PoseStack poseStack, int segments, float radius, float amplitude, int waveCount, float thickness, int red, int green, int blue, int alpha, float phase) {
        int steps = Math.max(32, segments);
        float previousRadius = radius + Mth.sin(phase) * amplitude;
        float previousX = previousRadius;
        float previousY = 0.0F;
        for (int i = 1; i <= steps; i++) {
            float angle = Mth.TWO_PI * i / steps;
            float wave = Mth.sin(angle * waveCount + phase) * amplitude;
            float currentRadius = radius + wave;
            float x = Mth.cos(angle) * currentRadius;
            float y = Mth.sin(angle) * currentRadius;
            line(consumer, poseStack, previousX, previousY, x, y, thickness, red, green, blue, alpha);
            previousX = x;
            previousY = y;
        }
    }

    private static void braidedRing(VertexConsumer consumer, PoseStack poseStack, int segments, float radius, float amplitude, float thickness, int redA, int greenA, int blueA, int redB, int greenB, int blueB, int alpha, float age) {
        waveRing(consumer, poseStack, segments, radius, amplitude, 8, thickness, redA, greenA, blueA, alpha, age * 0.04F);
        waveRing(consumer, poseStack, segments, radius, amplitude, 8, thickness, redB, greenB, blueB, alpha, age * 0.04F + Mth.PI);
    }

    private static void diamondMarks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float size, float thickness, int red, int green, int blue, int alpha, float age) {
        int safeCount = Math.max(3, count);
        for (int i = 0; i < safeCount; i++) {
            float angle = Mth.TWO_PI * i / safeCount + age * 0.003F;
            float tangent = angle + Mth.HALF_PI;
            float centerX = Mth.cos(angle) * radius;
            float centerY = Mth.sin(angle) * radius;
            float radialX = Mth.cos(angle) * size;
            float radialY = Mth.sin(angle) * size;
            float tangentX = Mth.cos(tangent) * size * 0.56F;
            float tangentY = Mth.sin(tangent) * size * 0.56F;
            float topX = centerX + radialX;
            float topY = centerY + radialY;
            float rightX = centerX + tangentX;
            float rightY = centerY + tangentY;
            float bottomX = centerX - radialX;
            float bottomY = centerY - radialY;
            float leftX = centerX - tangentX;
            float leftY = centerY - tangentY;
            line(consumer, poseStack, topX, topY, rightX, rightY, thickness, red, green, blue, alpha);
            line(consumer, poseStack, rightX, rightY, bottomX, bottomY, thickness, red, green, blue, alpha);
            line(consumer, poseStack, bottomX, bottomY, leftX, leftY, thickness, red, green, blue, alpha);
            line(consumer, poseStack, leftX, leftY, topX, topY, thickness, red, green, blue, alpha);
        }
    }

    private static void orbitingSeals(VertexConsumer consumer, PoseStack poseStack, int count, int sides, float orbitRadius, float sealRadius, int red, int green, int blue, int brightRed, int brightGreen, int brightBlue, float fade, float age) {
        int safeCount = Math.max(1, count);
        for (int i = 0; i < safeCount; i++) {
            float angle = Mth.TWO_PI * i / safeCount + age * 0.004F;
            float centerX = Mth.cos(angle) * orbitRadius;
            float centerY = Mth.sin(angle) * orbitRadius;
            float localSpin = -90.0F + i * 19.0F - age * 0.18F;
            miniSeal(consumer, poseStack, centerX, centerY, sides + i % 3, sealRadius, localSpin, red, green, blue, brightRed, brightGreen, brightBlue, fade);
        }
    }

    private static void miniSeal(VertexConsumer consumer, PoseStack poseStack, float centerX, float centerY, int sides, float radius, float degreesOffset, int red, int green, int blue, int brightRed, int brightGreen, int brightBlue, float fade) {
        ringAt(consumer, poseStack, centerX, centerY, radius, radius * 0.085F, 32, brightRed, brightGreen, brightBlue, alpha(94, fade));
        polygonAt(consumer, poseStack, centerX, centerY, sides, radius * 0.78F, radius * 0.055F, degreesOffset, red, green, blue, alpha(124, fade));
        starAt(consumer, poseStack, centerX, centerY, Math.max(5, sides), radius * 0.52F, radius * 0.042F, degreesOffset * 0.7F, brightRed, brightGreen, brightBlue, alpha(92, fade));
        line(consumer, poseStack, centerX - radius * 0.36F, centerY, centerX + radius * 0.36F, centerY, radius * 0.036F, red, green, blue, alpha(72, fade));
        line(consumer, poseStack, centerX, centerY - radius * 0.36F, centerX, centerY + radius * 0.36F, radius * 0.036F, red, green, blue, alpha(72, fade));
    }

    private static void ringAt(VertexConsumer consumer, PoseStack poseStack, float centerX, float centerY, float radius, float thickness, int segments, int red, int green, int blue, int alpha) {
        float inner = Math.max(0.01F, radius - thickness);
        float outer = radius + thickness;
        for (int i = 0; i < segments; i++) {
            float angleA = Mth.TWO_PI * i / segments;
            float angleB = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    centerX + Mth.cos(angleA) * inner, centerY + Mth.sin(angleA) * inner,
                    centerX + Mth.cos(angleA) * outer, centerY + Mth.sin(angleA) * outer,
                    centerX + Mth.cos(angleB) * outer, centerY + Mth.sin(angleB) * outer,
                    centerX + Mth.cos(angleB) * inner, centerY + Mth.sin(angleB) * inner,
                    red, green, blue, alpha);
        }
    }

    private static void polygonAt(VertexConsumer consumer, PoseStack poseStack, float centerX, float centerY, int sides, float radius, float thickness, float degreesOffset, int red, int green, int blue, int alpha) {
        int safeSides = Math.max(3, sides);
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < safeSides; i++) {
            float angleA = offset + Mth.TWO_PI * i / safeSides;
            float angleB = offset + Mth.TWO_PI * (i + 1) / safeSides;
            line(consumer, poseStack,
                    centerX + Mth.cos(angleA) * radius, centerY + Mth.sin(angleA) * radius,
                    centerX + Mth.cos(angleB) * radius, centerY + Mth.sin(angleB) * radius,
                    thickness, red, green, blue, alpha);
        }
    }

    private static void starAt(VertexConsumer consumer, PoseStack poseStack, float centerX, float centerY, int points, float radius, float thickness, float degreesOffset, int red, int green, int blue, int alpha) {
        int safePoints = Math.max(5, points);
        int skip = safePoints % 2 == 0 ? safePoints / 2 - 1 : safePoints / 2;
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < safePoints; i++) {
            int next = (i + skip) % safePoints;
            float angleA = offset + Mth.TWO_PI * i / safePoints;
            float angleB = offset + Mth.TWO_PI * next / safePoints;
            line(consumer, poseStack,
                    centerX + Mth.cos(angleA) * radius, centerY + Mth.sin(angleA) * radius,
                    centerX + Mth.cos(angleB) * radius, centerY + Mth.sin(angleB) * radius,
                    thickness, red, green, blue, alpha);
        }
    }

    private static void infinityChain(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float glyphScale, float thickness, int red, int green, int blue, int alpha) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count;
            float centerX = Mth.cos(angle) * radius;
            float centerY = Mth.sin(angle) * radius;
            float tangent = angle + Mth.HALF_PI;
            float prevX = centerX;
            float prevY = centerY;
            for (int step = 1; step <= 18; step++) {
                float t = Mth.TWO_PI * step / 18.0F;
                float localX = Mth.sin(t) * glyphScale;
                float localY = Mth.sin(t) * Mth.cos(t) * glyphScale * 0.62F;
                float x = centerX + Mth.cos(tangent) * localX - Mth.sin(tangent) * localY;
                float y = centerY + Mth.sin(tangent) * localX + Mth.cos(tangent) * localY;
                line(consumer, poseStack, prevX, prevY, x, y, thickness, red, green, blue, alpha);
                prevX = x;
                prevY = y;
            }
        }
    }

    private static void repeatingGlyphMarks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float height, float thickness, int red, int green, int blue, int alpha) {
        repeatingGlyphMarks(consumer, poseStack, count, radius, height, thickness, red, green, blue, alpha, 0.0F);
    }

    private static void repeatingGlyphMarks(VertexConsumer consumer, PoseStack poseStack, int count, float radius, float height, float thickness, int red, int green, int blue, int alpha, float phaseDegrees) {
        for (int i = 0; i < count; i++) {
            float angle = Mth.TWO_PI * i / count + phaseDegrees * Mth.DEG_TO_RAD;
            float tangent = angle + Mth.HALF_PI;
            float centerX = Mth.cos(angle) * radius;
            float centerY = Mth.sin(angle) * radius;
            float halfHeight = height * (0.35F + (i % 3) * 0.16F);
            float x1 = centerX - Mth.cos(tangent) * halfHeight;
            float y1 = centerY - Mth.sin(tangent) * halfHeight;
            float x2 = centerX + Mth.cos(tangent) * halfHeight;
            float y2 = centerY + Mth.sin(tangent) * halfHeight;
            line(consumer, poseStack, x1, y1, x2, y2, thickness, red, green, blue, alpha);
            if ((i & 3) == 0) {
                float notch = halfHeight * 0.45F;
                line(consumer, poseStack, centerX, centerY, centerX + Mth.cos(angle) * notch, centerY + Mth.sin(angle) * notch, thickness * 0.8F, red, green, blue, alpha);
            }
        }
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float x2, float y2, float thickness, int red, int green, int blue, int alpha) {
        if (alpha <= 0) {
            return;
        }
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001F) {
            return;
        }
        float half = thickness * 0.5F;
        float px = -dy / length * half;
        float py = dx / length * half;
        quad(consumer, poseStack, x1 - px, y1 - py, x1 + px, y1 + py, x2 + px, y2 + py, x2 - px, y2 - py, red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), x1, y1, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x4, y4, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x3, y3, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x2, y2, 0.0F).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), x1, y1, 0.0F).setColor(red, green, blue, alpha);
    }

    private static int alpha(int base, float fade) {
        return Mth.clamp(Math.round(base * fade), 0, 255);
    }

    private static int red(int color) {
        return color >> 16 & 255;
    }

    private static int green(int color) {
        return color >> 8 & 255;
    }

    private static int blue(int color) {
        return color & 255;
    }

    public static final class State extends EntityRenderState {
        private float radius = 2.8F;
        private int color = 0xFFE65A;
        private int life = 60;
        private int style = MagicCircleEffectEntity.STYLE_DIVINE_RESTORATION;
        private float yaw;
        private float pitch = 90.0F;
        private float roll;
        private int detail = MagicCircleEffectEntity.FULL_DETAIL;
        private int skillIndex = -1;
        private byte role;
        private double distanceSqr;
    }
}
