package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.entity.MagicBarrageFieldEntity;
import com.efkrdnz.magical.magic.MagicBarrageService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MagicBarrageFieldRenderer extends EntityRenderer<MagicBarrageFieldEntity, MagicBarrageFieldRenderer.State> {
    public MagicBarrageFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MagicBarrageFieldEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.color = entity.color();
        state.age = entity.tickCount + partialTick;
        state.shatterTicks = entity.shatterTicks();
        state.lastShotIndex = entity.lastShotIndex();
        state.lastShotTick = entity.lastShotTick();
    }

    @Override
    public boolean shouldRender(MagicBarrageFieldEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        double extent = Math.max(8.0D, entity.radius() * 1.55D + 4.0D);
        AABB bounds = new AABB(entity.getX() - extent, entity.getY() - extent, entity.getZ() - extent, entity.getX() + extent, entity.getY() + extent, entity.getZ() + extent);
        return entity.shouldRender(cameraX, cameraY, cameraZ) && frustum.isVisible(bounds);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        int red = red(state.color);
        int green = green(state.color);
        int blue = blue(state.color);
        poseStack.pushPose();
        float shatter = shatterProgress(state.shatterTicks, state.age);
        float fade = state.shatterTicks >= 0 ? 1.0F - shatter : 1.0F;
        float appear = appearProgress(state.age);
        renderCircleArray(consumer, poseStack, state.radius, state.age, red, green, blue, fade, shatter, appear, state.lastShotIndex, state.lastShotTick);
        if (state.shatterTicks < 0) {
            if (appear > 0.72F) {
                renderShootingStars(consumer, poseStack, state.radius, state.age, red, green, blue, Mth.clamp((appear - 0.72F) / 0.28F, 0.0F, 1.0F));
            }
        } else {
            renderShatterFragments(consumer, poseStack, state.radius, state.age, red, green, blue, fade, shatter);
        }
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    public static void renderPreview(PoseStack poseStack, MultiBufferSource buffer, float radius, float age, float fade) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        poseStack.pushPose();
        renderSphereShell(consumer, poseStack, radius, age, 142, 123, 255, alpha(58, fade));
        renderPreviewRings(consumer, poseStack, radius, age, fade);
        poseStack.popPose();
    }

    private static void renderSphereShell(VertexConsumer consumer, PoseStack poseStack, float radius, float age, int red, int green, int blue, int alpha) {
        for (int layer = -5; layer < 5; layer++) {
            float y1 = radius * layer / 5.0F;
            float y2 = radius * (layer + 0.46F) / 5.0F;
            float r1 = Mth.sqrt(Math.max(0.0F, radius * radius - y1 * y1));
            float r2 = Mth.sqrt(Math.max(0.0F, radius * radius - y2 * y2));
            surfaceBand(consumer, poseStack, r1, r2, y1, y2, red, green, blue, Math.max(4, alpha / 3 - Math.abs(layer) * 2));
        }
        for (int layer = -4; layer <= 4; layer++) {
            float y = radius * layer / 5.0F;
            float ringRadius = Mth.sqrt(Math.max(0.0F, radius * radius - y * y));
            ring3d(consumer, poseStack, new Vec3(0.0D, y, 0.0D), new Basis(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D)), ringRadius, radius * 0.006F, 96, red, green, blue, Math.max(10, alpha - Math.abs(layer) * 3));
        }
        for (int meridian = 0; meridian < 6; meridian++) {
            double angle = Math.PI * meridian / 6.0D + age * 0.004D;
            Vec3 right = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            ring3d(consumer, poseStack, Vec3.ZERO, new Basis(right, up), radius, radius * 0.005F, 96, Math.min(255, red + 40), Math.min(255, green + 40), 255, Math.max(12, alpha - 8));
        }
    }

    private static void surfaceBand(VertexConsumer consumer, PoseStack poseStack, float r1, float r2, float y1, float y2, int red, int green, int blue, int alpha) {
        int segments = 72;
        for (int i = 0; i < segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 1) / segments;
            quad(consumer, poseStack,
                    new Vec3(Mth.cos(a) * r1, y1, Mth.sin(a) * r1),
                    new Vec3(Mth.cos(b) * r1, y1, Mth.sin(b) * r1),
                    new Vec3(Mth.cos(b) * r2, y2, Mth.sin(b) * r2),
                    new Vec3(Mth.cos(a) * r2, y2, Mth.sin(a) * r2),
                    red, green, blue, alpha);
        }
    }

    private static void renderPreviewRings(VertexConsumer consumer, PoseStack poseStack, float radius, float age, float fade) {
        int alpha = alpha(115, fade);
        ring3d(consumer, poseStack, Vec3.ZERO, new Basis(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D)), radius, radius * 0.01F, 128, 190, 235, 255, alpha);
        ring3d(consumer, poseStack, Vec3.ZERO, new Basis(new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D)), radius * (0.98F + Mth.sin(age * 0.08F) * 0.01F), radius * 0.007F, 128, 142, 123, 255, alpha(95, fade));
        ring3d(consumer, poseStack, Vec3.ZERO, new Basis(new Vec3(0.0D, 0.0D, 1.0D), new Vec3(0.0D, 1.0D, 0.0D)), radius * (0.98F + Mth.cos(age * 0.07F) * 0.01F), radius * 0.007F, 128, 215, 245, 255, alpha(95, fade));
    }

    private static void renderCircleArray(VertexConsumer consumer, PoseStack poseStack, float radius, float age, int red, int green, int blue, float fade, float shatter, float appear, int lastShotIndex, int lastShotTick) {
        int count = MagicBarrageService.circleCountForRadius(radius);
        for (int i = 0; i < count; i++) {
            float localAppear = localAppearProgress(age, i, count);
            if (localAppear <= 0.01F && shatter <= 0.0F) {
                continue;
            }
            Vec3 normalOut = MagicBarrageFieldEntity.surfacePoint(i, count, 1.0F).normalize();
            float centerExpand = Mth.lerp(localAppear, 0.18F, 1.0F);
            Vec3 center = normalOut.scale(radius * (0.93F + pseudo(i, 0.13F) * 0.06F) * centerExpand);
            if (shatter > 0.0F) {
                Vec3 tangent = new Vec3(-normalOut.z, pseudo(i, 15.0F) - 0.5F, normalOut.x);
                if (tangent.lengthSqr() < 1.0E-6D) {
                    tangent = new Vec3(1.0D, 0.0D, 0.0D);
                }
                center = center.add(normalOut.scale(radius * shatter * (0.025F + pseudo(i, 14.0F) * 0.085F)))
                        .add(tangent.normalize().scale(radius * shatter * (pseudo(i, 16.0F) - 0.5F) * 0.11F));
            }
            Basis basis = inwardBasis(normalOut);
            float overshoot = localAppear < 1.0F ? 1.0F + Mth.sin(localAppear * Mth.PI) * (0.26F + pseudo(i, 18.0F) * 0.18F) : 1.0F;
            float pulse = shotPulse(age, i, lastShotIndex, lastShotTick, count);
            float localRadius = radius * (0.066F + pseudo(i, 1.9F) * 0.064F) * (1.0F + shatter * 0.16F) * localAppear * overshoot * (1.0F + pulse * 0.38F);
            float spinBurst = (1.0F - localAppear) * (90.0F + pseudo(i, 19.0F) * 220.0F);
            float spin = (age * (0.45F + pseudo(i, 4.0F) * 0.75F) + spinBurst) * (i % 2 == 0 ? 1.0F : -1.0F);
            float drift = 0.5F + 0.5F * Mth.sin(age * 0.018F + pseudo(i, 12.3F) * Mth.TWO_PI);
            int blueRed = Mth.clamp(red + Math.round((pseudo(i, 6.0F) - 0.45F) * 80.0F), 95, 235);
            int blueGreen = Mth.clamp(green + Math.round((pseudo(i, 7.0F) - 0.05F) * 95.0F), 110, 255);
            int blueBlue = Mth.clamp(blue + Math.round(pseudo(i, 8.0F) * 45.0F), 185, 255);
            int purpleRed = Mth.clamp(red + Math.round((pseudo(i, 6.6F) + 0.1F) * 95.0F), 130, 255);
            int purpleGreen = Mth.clamp(green + Math.round((pseudo(i, 7.6F) - 0.55F) * 75.0F), 75, 220);
            int purpleBlue = Mth.clamp(blue + Math.round((pseudo(i, 8.6F) - 0.05F) * 60.0F), 175, 255);
            int localRed = blend(purpleRed, blueRed, drift);
            int localGreen = blend(purpleGreen, blueGreen, drift);
            int localBlue = blend(purpleBlue, blueBlue, drift);
            int alpha = alpha(158 + Math.round(pseudo(i, 9.0F) * 58.0F + drift * 24.0F + pulse * 65.0F), fade * fade * smooth(localAppear));
            if (alpha <= 4) {
                continue;
            }
            Basis rotatedBasis = basis.rotate(spin);
            circleGlyph(consumer, poseStack, center, rotatedBasis, localRadius, 42 + (i % 4) * 8, localRed, localGreen, localBlue, alpha, i, age);
            float flash = shotFlash(age, i, lastShotIndex, lastShotTick);
            if (flash > 0.0F) {
                circleFlash(consumer, poseStack, center, rotatedBasis, localRadius, flash, fade * smooth(localAppear));
            }
        }
    }

    private static void renderShatterFragments(VertexConsumer consumer, PoseStack poseStack, float radius, float age, int red, int green, int blue, float fade, float shatter) {
        int count = MagicBarrageService.circleCountForRadius(radius);
        int alpha = alpha(185, fade);
        if (alpha <= 4) {
            return;
        }
        int shardCount = Math.min(count, 72);
        for (int i = 0; i < shardCount; i++) {
            if (pseudo(i, 21.0F) < shatter * 0.18F) {
                continue;
            }
            Vec3 normalOut = MagicBarrageFieldEntity.surfacePoint(i, count, 1.0F).normalize();
            Vec3 base = normalOut.scale(radius * (0.94F + pseudo(i, 22.0F) * 0.055F));
            Vec3 tangent = new Vec3(-normalOut.z, pseudo(i, 23.0F) - 0.5F, normalOut.x);
            if (tangent.lengthSqr() < 1.0E-6D) {
                tangent = new Vec3(1.0D, 0.0D, 0.0D);
            }
            tangent = tangent.normalize();
            Vec3 side = normalOut.cross(tangent);
            if (side.lengthSqr() < 1.0E-6D) {
                side = new Vec3(0.0D, 1.0D, 0.0D);
            }
            side = side.normalize();
            Vec3 origin = base.add(normalOut.scale(radius * shatter * (0.08F + pseudo(i, 24.0F) * 0.18F)))
                    .add(side.scale(radius * shatter * (pseudo(i, 25.0F) - 0.5F) * 0.22F));
            float length = radius * (0.035F + pseudo(i, 26.0F) * 0.075F) * (1.0F + shatter * 1.25F);
            Vec3 direction = tangent.scale((pseudo(i, 27.0F) < 0.5F ? -1.0D : 1.0D) * length)
                    .add(side.scale((pseudo(i, 28.0F) - 0.5F) * length * 0.7F));
            int shardRed = Mth.clamp(red + 92, 0, 255);
            int shardGreen = Mth.clamp(green + 86, 0, 255);
            int shardBlue = 255;
            line(consumer, poseStack, origin.subtract(direction.scale(0.35D)), origin.add(direction.scale(0.65D)), radius * 0.0025F, shardRed, shardGreen, shardBlue, alpha);
            if ((i & 3) == 0) {
                Vec3 crack = side.scale(length * 0.42F);
                line(consumer, poseStack, origin, origin.add(crack), radius * 0.0018F, red, green, blue, alpha(95, fade));
            }
        }
    }

    private static void renderShootingStars(VertexConsumer consumer, PoseStack poseStack, float radius, float age, int red, int green, int blue, float fade) {
        int count = MagicBarrageService.circleCountForRadius(radius);
        int cometCount = Mth.clamp(count / 18, 3, 6);
        for (int comet = 0; comet < cometCount; comet++) {
            float travel = age * (0.42F + comet * 0.065F) + comet * 7.0F;
            int routeStep = Mth.floor(travel);
            float local = travel - routeStep;
            int fromIndex = routeIndex(comet, routeStep, count);
            int toIndex = zigzagRouteIndex(fromIndex, comet, routeStep, count, radius);
            Vec3 from = circleCenter(fromIndex, count, radius);
            Vec3 to = circleCenter(toIndex, count, radius);
            Vec3 head = zigzagPoint(from, to, comet, routeStep, local);
            Vec3 tail = zigzagPoint(from, to, comet, routeStep, Math.max(0.0F, local - 0.28F));
            int starRed = Math.min(255, red + 96);
            int starGreen = Math.min(255, green + 88);
            int starBlue = 255;
            line(consumer, poseStack, tail, head, radius * 0.0065F, starRed, starGreen, starBlue, alpha(205, fade));
            line(consumer, poseStack, zigzagPoint(from, to, comet, routeStep, Math.max(0.0F, local - 0.52F)), tail, radius * 0.0035F, red, green, blue, alpha(82, fade));

            for (int trail = 1; trail <= 3; trail++) {
                int oldFromIndex = routeIndex(comet, routeStep - trail, count);
                int oldToIndex = zigzagRouteIndex(oldFromIndex, comet, routeStep - trail, count, radius);
                Vec3 oldFrom = circleCenter(oldFromIndex, count, radius);
                Vec3 oldTo = circleCenter(oldToIndex, count, radius);
                zigzagLine(consumer, poseStack, oldFrom, oldTo, comet, routeStep - trail, 4, radius * 0.0022F, red, green, blue, alpha(48 - trail * 9, fade));
            }
        }
    }

    private static int zigzagRouteIndex(int fromIndex, int comet, int step, int count, float radius) {
        Vec3 from = circleCenter(fromIndex, count, radius).normalize();
        int bestIndex = Math.floorMod(fromIndex + 1, count);
        double bestScore = Double.MAX_VALUE;
        int direction = (comet & 1) == 0 ? 1 : -1;
        double fromAngle = Math.atan2(from.z, from.x);
        double targetAhead = 0.42D + pseudo(comet, step * 0.19F) * 0.22D;
        double verticalSwing = 0.18D + pseudo(comet + step * 7, 4.8F) * 0.12D;
        double desiredY = Mth.clamp((float) (from.y + (((comet + step) & 1) == 0 ? verticalSwing : -verticalSwing)), -0.84F, 0.84F);
        for (int candidate = 0; candidate < count; candidate++) {
            if (candidate == fromIndex) {
                continue;
            }
            Vec3 normal = circleCenter(candidate, count, radius).normalize();
            double distance = normal.distanceToSqr(from);
            if (distance > 0.82D) {
                continue;
            }
            double candidateAngle = Math.atan2(normal.z, normal.x);
            double ahead = angularAhead(fromAngle, candidateAngle, direction);
            double horizontalScore = Math.abs(ahead - targetAhead);
            double verticalScore = Math.abs(normal.y - desiredY);
            double jitter = pseudo(candidate + comet * 31 + step * 17, 3.41F) * 0.045D;
            double score = horizontalScore * 1.65D + verticalScore * 1.35D + distance * 0.22D + jitter;
            if (score < bestScore) {
                bestScore = score;
                bestIndex = candidate;
            }
        }
        return bestIndex;
    }

    private static double angularAhead(double fromAngle, double candidateAngle, int direction) {
        double delta = (candidateAngle - fromAngle) * direction;
        while (delta < 0.0D) {
            delta += Math.PI * 2.0D;
        }
        while (delta >= Math.PI * 2.0D) {
            delta -= Math.PI * 2.0D;
        }
        return delta;
    }

    private static int routeIndex(int comet, int step, int count) {
        int stride = 7 + comet * 4;
        int wobble = Mth.floor(pseudo(comet + step * 13, 2.7F) * count);
        return Math.floorMod(comet * 11 + step * stride + wobble, count);
    }

    private static Vec3 circleCenter(int index, int count, float radius) {
        Vec3 normalOut = MagicBarrageFieldEntity.surfacePoint(index, count, 1.0F).normalize();
        return normalOut.scale(radius * (0.94F + pseudo(index, 0.13F) * 0.055F));
    }

    private static Vec3 arcPoint(Vec3 from, Vec3 to, float progress) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        Vec3 direction = from.normalize().lerp(to.normalize(), t);
        if (direction.lengthSqr() < 1.0E-6D) {
            return from.lerp(to, t);
        }
        double length = from.length() + (to.length() - from.length()) * t;
        return direction.normalize().scale(length);
    }

    private static Vec3 zigzagPoint(Vec3 from, Vec3 to, int comet, int step, float progress) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        Vec3 bend = arcPoint(from, to, 0.5F);
        Vec3 upward = new Vec3(0.0D, ((comet + step) & 1) == 0 ? 1.0D : -1.0D, 0.0D);
        Vec3 tangent = to.normalize().subtract(from.normalize());
        Vec3 side = tangent.cross(upward);
        if (side.lengthSqr() < 1.0E-6D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        double bendLength = bend.length();
        Vec3 kink = bend.add(upward.scale(bendLength * 0.075D)).add(side.normalize().scale(bendLength * 0.045D));
        kink = kink.normalize().scale(bendLength);
        return t < 0.5F ? arcPoint(from, kink, t * 2.0F) : arcPoint(kink, to, (t - 0.5F) * 2.0F);
    }

    private static void zigzagLine(VertexConsumer consumer, PoseStack poseStack, Vec3 from, Vec3 to, int comet, int step, int segments, float thickness, int red, int green, int blue, int alpha) {
        Vec3 previous = from;
        for (int i = 1; i <= segments; i++) {
            Vec3 next = zigzagPoint(from, to, comet, step, i / (float) segments);
            line(consumer, poseStack, previous, next, thickness, red, green, blue, alpha);
            previous = next;
        }
    }

    private static void circleGlyph(VertexConsumer consumer, PoseStack poseStack, Vec3 center, Basis basis, float radius, int segments, int red, int green, int blue, int alpha, int index, float age) {
        ring3d(consumer, poseStack, center, basis, radius * 1.1F, radius * 0.06F, segments, Math.min(255, red + 58), Math.min(255, green + 50), 255, Math.max(24, alpha - 82));
        ring3d(consumer, poseStack, center, basis, radius, radius * 0.04F, segments, red, green, blue, alpha);
        ring3d(consumer, poseStack, center, basis, radius * 0.79F, radius * 0.022F, segments, Math.min(255, red + 24), Math.min(255, green + 22), 255, Math.max(24, alpha - 46));
        ring3d(consumer, poseStack, center, basis, radius * 0.48F, radius * 0.018F, segments / 2, Math.min(255, red + 70), Math.min(255, green + 62), 255, Math.max(22, alpha - 58));
        int sides = 3 + index % 6;
        polygon3d(consumer, poseStack, center, basis, sides, radius * 0.82F, radius * 0.027F, -90.0F, red, green, blue, Math.max(20, alpha - 24));
        polygon3d(consumer, poseStack, center, basis.rotate(22.5F + index * 3.0F), 5 + index % 4, radius * 0.58F, radius * 0.016F, -54.0F, Math.min(255, red + 42), Math.min(255, green + 30), 255, Math.max(20, alpha - 68));
        if ((index & 1) == 0) {
            star3d(consumer, poseStack, center, basis.rotate(age * 0.35F), 5, radius * 0.68F, radius * 0.026F, Math.min(255, red + 74), Math.min(255, green + 74), 255, Math.max(18, alpha - 70));
        }
        for (int spoke = 0; spoke < 6 + index % 7; spoke++) {
            float angle = Mth.TWO_PI * spoke / (6 + index % 7);
            Vec3 a = point(center, basis, Mth.cos(angle) * radius * 0.22F, Mth.sin(angle) * radius * 0.22F);
            Vec3 b = point(center, basis, Mth.cos(angle) * radius * 0.95F, Mth.sin(angle) * radius * 0.95F);
            line(consumer, poseStack, a, b, radius * 0.016F, red, green, blue, Math.max(18, alpha - 68));
        }
        for (int tick = 0; tick < 12 + index % 9; tick++) {
            float angle = Mth.TWO_PI * tick / (12 + index % 9);
            float inner = radius * (tick % 3 == 0 ? 0.88F : 0.93F);
            float outer = radius * 1.12F;
            Vec3 a = point(center, basis, Mth.cos(angle) * inner, Mth.sin(angle) * inner);
            Vec3 b = point(center, basis, Mth.cos(angle) * outer, Mth.sin(angle) * outer);
            line(consumer, poseStack, a, b, radius * 0.018F, Math.min(255, red + 52), Math.min(255, green + 40), 255, Math.max(16, alpha - 72));
        }
        if (index % 3 == 0) {
            Vec3 inward = center.normalize().scale(-1.0D);
            Vec3 muzzle = center.add(inward.scale(radius * 0.16F));
            Vec3 focus = center.add(inward.scale(radius * (0.95F + Mth.sin(age * 0.12F + index) * 0.12F)));
            line(consumer, poseStack, muzzle, focus, radius * 0.04F, 245, 250, 255, Math.max(18, alpha - 54));
        }
    }

    private static void circleFlash(VertexConsumer consumer, PoseStack poseStack, Vec3 center, Basis basis, float radius, float flash, float fade) {
        int hot = alpha(235, flash * fade);
        int soft = alpha(145, flash * fade);
        if (hot <= 4) {
            return;
        }
        ring3d(consumer, poseStack, center, basis, radius * (1.28F + flash * 0.22F), radius * 0.09F, 72, 255, 255, 255, hot);
        ring3d(consumer, poseStack, center, basis, radius * 0.54F, radius * 0.05F, 48, 235, 248, 255, soft);
        for (int ray = 0; ray < 8; ray++) {
            float angle = Mth.TWO_PI * ray / 8.0F;
            Vec3 inner = point(center, basis, Mth.cos(angle) * radius * 0.2F, Mth.sin(angle) * radius * 0.2F);
            Vec3 outer = point(center, basis, Mth.cos(angle) * radius * (1.42F + flash * 0.38F), Mth.sin(angle) * radius * (1.42F + flash * 0.38F));
            line(consumer, poseStack, inner, outer, radius * 0.018F * flash, 255, 255, 255, soft);
        }
    }

    private static Basis inwardBasis(Vec3 outward) {
        Vec3 normal = outward.scale(-1.0D);
        Vec3 reference = Math.abs(normal.y) > 0.82D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = reference.cross(normal).normalize();
        Vec3 up = normal.cross(right).normalize();
        return new Basis(right, up);
    }

    private static void ring3d(VertexConsumer consumer, PoseStack poseStack, Vec3 center, Basis basis, float radius, float thickness, int segments, int red, int green, int blue, int alpha) {
        for (int i = 0; i < segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 1) / segments;
            Vec3 p1 = point(center, basis, Mth.cos(a) * (radius - thickness), Mth.sin(a) * (radius - thickness));
            Vec3 p2 = point(center, basis, Mth.cos(a) * (radius + thickness), Mth.sin(a) * (radius + thickness));
            Vec3 p3 = point(center, basis, Mth.cos(b) * (radius + thickness), Mth.sin(b) * (radius + thickness));
            Vec3 p4 = point(center, basis, Mth.cos(b) * (radius - thickness), Mth.sin(b) * (radius - thickness));
            quad(consumer, poseStack, p1, p2, p3, p4, red, green, blue, alpha);
        }
    }

    private static void polygon3d(VertexConsumer consumer, PoseStack poseStack, Vec3 center, Basis basis, int sides, float radius, float thickness, float degreesOffset, int red, int green, int blue, int alpha) {
        int safeSides = Math.max(3, sides);
        float offset = degreesOffset * Mth.DEG_TO_RAD;
        for (int i = 0; i < safeSides; i++) {
            float a = offset + Mth.TWO_PI * i / safeSides;
            float b = offset + Mth.TWO_PI * (i + 1) / safeSides;
            line(consumer, poseStack, point(center, basis, Mth.cos(a) * radius, Mth.sin(a) * radius), point(center, basis, Mth.cos(b) * radius, Mth.sin(b) * radius), thickness, red, green, blue, alpha);
        }
    }

    private static void star3d(VertexConsumer consumer, PoseStack poseStack, Vec3 center, Basis basis, int points, float radius, float thickness, int red, int green, int blue, int alpha) {
        int safePoints = Math.max(5, points);
        for (int i = 0; i < safePoints; i++) {
            int next = (i + 2) % safePoints;
            float a = -Mth.HALF_PI + Mth.TWO_PI * i / safePoints;
            float b = -Mth.HALF_PI + Mth.TWO_PI * next / safePoints;
            line(consumer, poseStack, point(center, basis, Mth.cos(a) * radius, Mth.sin(a) * radius), point(center, basis, Mth.cos(b) * radius, Mth.sin(b) * radius), thickness, red, green, blue, alpha);
        }
    }

    private static void spark(VertexConsumer consumer, PoseStack poseStack, Vec3 center, float size, int red, int green, int blue, int alpha) {
        line(consumer, poseStack, center.add(-size, 0.0D, 0.0D), center.add(size, 0.0D, 0.0D), size * 0.28F, red, green, blue, alpha);
        line(consumer, poseStack, center.add(0.0D, -size, 0.0D), center.add(0.0D, size, 0.0D), size * 0.22F, red, green, blue, Math.max(0, alpha - 26));
        line(consumer, poseStack, center.add(0.0D, 0.0D, -size), center.add(0.0D, 0.0D, size), size * 0.22F, red, green, blue, Math.max(0, alpha - 42));
    }

    private static Vec3 point(Vec3 center, Basis basis, float x, float y) {
        return center.add(basis.right.scale(x)).add(basis.up.scale(y));
    }

    private static void line(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, float thickness, int red, int green, int blue, int alpha) {
        Vec3 direction = b.subtract(a);
        Vec3 normal = new Vec3(-direction.z, 0.0D, direction.x);
        if (normal.lengthSqr() < 1.0E-6D) {
            normal = new Vec3(1.0D, 0.0D, 0.0D);
        }
        normal = normal.normalize().scale(thickness * 0.5F);
        quad(consumer, poseStack, a.subtract(normal), a.add(normal), b.add(normal), b.subtract(normal), red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack poseStack, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int red, int green, int blue, int alpha) {
        consumer.addVertex(poseStack.last(), (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) d.x, (float) d.y, (float) d.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) d.x, (float) d.y, (float) d.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha);
        consumer.addVertex(poseStack.last(), (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha);
    }

    private static float pseudo(int index, float salt) {
        float value = Mth.sin(index * 12.9898F + salt * 78.233F) * 43758.547F;
        return value - Mth.floor(value);
    }

    private static int blend(int from, int to, float progress) {
        return Mth.clamp(Math.round(Mth.lerp(progress, from, to)), 0, 255);
    }

    private static float appearProgress(float age) {
        return smooth(Mth.clamp(age / 34.0F, 0.0F, 1.0F));
    }

    private static float localAppearProgress(float age, int index, int count) {
        float band = index / (float) Math.max(1, count - 1);
        float waveDelay = band * 15.0F;
        float randomDelay = pseudo(index, 17.0F) * 13.0F;
        float duration = 12.0F + pseudo(index, 18.0F) * 15.0F;
        return smooth(Mth.clamp((age - waveDelay - randomDelay) / duration, 0.0F, 1.0F));
    }

    private static float shotPulse(float age, int index, int lastShotIndex, int lastShotTick, int count) {
        if (lastShotIndex < 0 || lastShotTick < 0) {
            return 0.0F;
        }
        float elapsed = age - lastShotTick;
        if (elapsed < 0.0F || elapsed > 12.0F) {
            return 0.0F;
        }
        int distance = Math.min(Math.floorMod(index - lastShotIndex, count), Math.floorMod(lastShotIndex - index, count));
        if (distance > 2) {
            return 0.0F;
        }
        float delay = distance * 1.25F;
        float t = Mth.clamp((elapsed - delay) / 8.0F, 0.0F, 1.0F);
        if (t <= 0.0F || t >= 1.0F) {
            return 0.0F;
        }
        float strength = distance == 0 ? 1.0F : 0.42F / distance;
        return Mth.sin(t * Mth.PI) * (1.0F - t * 0.18F) * strength;
    }

    private static float shotFlash(float age, int index, int lastShotIndex, int lastShotTick) {
        if (index != lastShotIndex || lastShotTick < 0) {
            return 0.0F;
        }
        float elapsed = age - lastShotTick;
        if (elapsed < 0.0F || elapsed > 7.0F) {
            return 0.0F;
        }
        float t = elapsed / 7.0F;
        return Mth.sin(t * Mth.PI) * (1.0F - t * 0.35F);
    }

    private static float smooth(float progress) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private static float shatterProgress(int shatterTicks, float age) {
        if (shatterTicks < 0) {
            return 0.0F;
        }
        float partial = age - Mth.floor(age);
        return Mth.clamp((shatterTicks + partial) / (float) MagicBarrageFieldEntity.SHATTER_DURATION, 0.0F, 1.0F);
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
        private float radius = MagicBarrageService.MIN_RADIUS;
        private int color = 0x8E7BFF;
        private float age;
        private int shatterTicks = -1;
        private int lastShotIndex = -1;
        private int lastShotTick = -1000;
    }

    private record Basis(Vec3 right, Vec3 up) {
        private Basis rotate(float degrees) {
            float radians = degrees * Mth.DEG_TO_RAD;
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            Vec3 rotatedRight = right.scale(cos).add(up.scale(sin));
            Vec3 rotatedUp = up.scale(cos).subtract(right.scale(sin));
            return new Basis(rotatedRight, rotatedUp);
        }
    }
}
