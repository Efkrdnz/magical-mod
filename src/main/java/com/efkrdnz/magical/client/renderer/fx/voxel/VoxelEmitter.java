package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Emits cubes, three faces at a time, without allocating anything per voxel.
 *
 * <p>Two things here are load-bearing at a thousand cubes a frame, and both are invisible until they
 * are not:
 *
 * <ul>
 *   <li><b>No pose push per cube.</b> {@code PoseStack.pushPose} copies a 4x4 and a 3x3 into a new
 *       object - three allocations, which at a thousand cubes is three thousand a frame and two
 *       hundred thousand a second. The established painters do this happily, at forty elements.
 *   <li><b>No matrix per vertex.</b> {@code addVertex(Matrix4f, ...)} transforms through a freshly
 *       allocated vector on every call, another fourteen thousand objects a frame. When the pose is
 *       a pure translate - which it is for a custom silhouette, since no rotation is applied to one
 *       - the translation is three additions and the matrix is not needed at all.
 * </ul>
 *
 * <p>The matrix path is kept for the case where someone hands this a rotated pose, so the class is
 * correct rather than merely fast.
 */
public final class VoxelEmitter {

    /**
     * Per-face brightness, in the face order of {@link FxMesh#voxelUnit()}.
     *
     * <p>Without this a cube is a flat blob. The body shader builds its normal out of the texture
     * coordinate rather than a real one, so all six faces shade identically no matter which way they
     * point. Tinting per face on the way in costs nothing - the colour is already a per-call
     * argument - and it is the entire difference between reading as a cube and reading as a smudge.
     */
    private static final float[] FACE_LIGHT = {0.80F, 0.72F, 1.00F, 0.45F, 0.86F, 0.66F};

    private final VertexConsumer consumer;
    private final Matrix4f matrix;
    private final float offsetX;
    private final float offsetY;
    private final float offsetZ;
    private final float cameraX;
    private final float cameraY;
    private final float cameraZ;
    private int faces;

    /**
     * @param cameraX the camera in the same local space the cube centres are given in - not the
     *                camera's world position
     */
    public VoxelEmitter(VertexConsumer consumer, PoseStack.Pose pose,
            float cameraX, float cameraY, float cameraZ) {
        this.consumer = consumer;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;

        Matrix4f pending = pose.pose();
        if (isPureTranslation(pending)) {
            this.matrix = null;
            this.offsetX = pending.m30();
            this.offsetY = pending.m31();
            this.offsetZ = pending.m32();
        } else {
            this.matrix = pending;
            this.offsetX = 0.0F;
            this.offsetY = 0.0F;
            this.offsetZ = 0.0F;
        }
    }

    /**
     * Which faces of an axis-aligned cube can face the camera, as bits in the mesh's face order.
     *
     * <p>A box face is front-facing exactly when the camera is on its outward side, and for an
     * axis-aligned cube that reduces to comparing one coordinate against the half extent. At most
     * three bits are ever set, they always close the silhouette, and the tests for the two sides of
     * an axis are mutually exclusive - which is what stops two touching cubes from both drawing the
     * face they share.
     *
     * <p>Split out of {@link #cube} so it can be asserted without a vertex buffer: the failure mode
     * is a cube that vanishes from one angle, and that is not something a diff shows.
     */
    static int faceMask(float centreX, float centreY, float centreZ, float half,
            float camX, float camY, float camZ) {
        int mask = 0;
        if (camX - centreX > half) {
            mask |= 1;
        } else if (centreX - camX > half) {
            mask |= 1 << 1;
        }
        if (camY - centreY > half) {
            mask |= 1 << 2;
        } else if (centreY - camY > half) {
            mask |= 1 << 3;
        }
        if (camZ - centreZ > half) {
            mask |= 1 << 4;
        } else if (centreZ - camZ > half) {
            mask |= 1 << 5;
        }
        if (mask == 0) {
            // The camera is inside this six-centimetre cube. Three extra quads, once.
            mask = 0b111111;
        }
        return mask;
    }

    private static boolean isPureTranslation(Matrix4f m) {
        return m.m00() == 1.0F && m.m11() == 1.0F && m.m22() == 1.0F
                && m.m01() == 0.0F && m.m02() == 0.0F
                && m.m10() == 0.0F && m.m12() == 0.0F
                && m.m20() == 0.0F && m.m21() == 0.0F;
    }

    /**
     * Draws one cube, emitting only the faces that can be seen.
     *
     * <p>An axis-aligned box shows at most three faces to any camera, and which three is one
     * comparison per axis. The test is against the face plane rather than the centre: when the
     * camera sits inside the cube's slab on some axis, that axis's face points away, and this render
     * type does not cull back faces - it would blend through as a dark plane inside the cube.
     *
     * <p>Halving the faces has a second effect worth more than the bandwidth. Two cubes that touch
     * can no longer both draw the face they share, because the tests for those two faces are exactly
     * opposite. The seam that would z-fight cannot be drawn twice.
     */
    public void cube(float centreX, float centreY, float centreZ, float half,
            int rgb, int alpha, int packed) {
        if (half <= 0.0F || alpha <= 0) {
            return;
        }
        int mask = faceMask(centreX, centreY, centreZ, half, cameraX, cameraY, cameraZ);

        float[] mesh = FxMesh.voxelUnit();
        int uv2Low = packed & 0xFFFF;
        int uv2High = (packed >>> 16) & 0xFFFF;
        for (int face = 0; face < 6; face++) {
            if ((mask & (1 << face)) == 0) {
                continue;
            }
            int shaded = shade(rgb, FACE_LIGHT[face]);
            int red = (shaded >> 16) & 0xFF;
            int green = (shaded >> 8) & 0xFF;
            int blue = shaded & 0xFF;
            int o = face * FxMesh.FACE_STRIDE;
            for (int vertex = 0; vertex < 4; vertex++, o += 5) {
                float x = centreX + mesh[o] * half;
                float y = centreY + mesh[o + 1] * half;
                float z = centreZ + mesh[o + 2] * half;
                if (matrix == null) {
                    consumer.addVertex(offsetX + x, offsetY + y, offsetZ + z);
                } else {
                    consumer.addVertex(matrix, x, y, z);
                }
                consumer.setColor(red, green, blue, alpha)
                        .setUv(mesh[o + 3], mesh[o + 4])
                        .setUv2(uv2Low, uv2High);
            }
            faces++;
        }
    }

    /** Quads emitted so far. One per face, which is what the frame budget wants counted. */
    public int faces() {
        return faces;
    }

    private static int shade(int rgb, float scale) {
        int red = Mth.clamp(Math.round(((rgb >> 16) & 0xFF) * scale), 0, 255);
        int green = Mth.clamp(Math.round(((rgb >> 8) & 0xFF) * scale), 0, 255);
        int blue = Mth.clamp(Math.round((rgb & 0xFF) * scale), 0, 255);
        return (red << 16) | (green << 8) | blue;
    }
}
