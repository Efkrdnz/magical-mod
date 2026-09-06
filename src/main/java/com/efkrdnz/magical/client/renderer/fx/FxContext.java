package com.efkrdnz.magical.client.renderer.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Everything a painter needs for one draw: pose (already translated to the effect origin and
 * oriented so local +Z is the travel axis when relevant), buffers, timing, seed, camera and LOD.
 */
public final class FxContext {
    public final PoseStack pose;
    public final MultiBufferSource buffers;
    public final float partialTick;
    public final Quaternionf cameraOrientation;
    public final Vec3 cameraPos;
    /** Age in ticks including the partial tick. */
    public float age;
    /** Total life in ticks (<= 0 means unbounded). */
    public float life;
    /** Lifecycle phase 0..1 for the shader; callers may override the default age/life ratio. */
    public float phase;
    public int seed;
    /** 0..3, from detailForTier and distance LOD. */
    public int detail = 3;
    /** Distance-based scale for particle/trail counts (1 = full). */
    public float lod = 1.0F;
    /** Optional second point for LINK forms, relative to the pose origin. */
    public Vec3 endPoint = Vec3.ZERO;
    /** Optional opacity multiplier (fade envelopes). */
    public float opacity = 1.0F;
    /** Optional extra scalar the behaviour syncs (charge fraction, integrity, sweep bearing). */
    public float extra;
    /** Optional synced payload for custom painters. */
    public net.minecraft.nbt.CompoundTag data;
    /** World position of the effect origin (for painters that place things in world space). */
    public Vec3 origin = Vec3.ZERO;
    /** Synced travel/facing direction (custom painters orient themselves). */
    public Vec3 direction = Vec3.ZERO;

    public FxContext(PoseStack pose, MultiBufferSource buffers, float partialTick, Quaternionf cameraOrientation, Vec3 cameraPos) {
        this.pose = pose;
        this.buffers = buffers;
        this.partialTick = partialTick;
        this.cameraOrientation = cameraOrientation;
        this.cameraPos = cameraPos;
    }

    public FxContext timing(float age, float life, int seed) {
        this.age = age;
        this.life = life;
        this.seed = seed & 63;
        this.phase = life > 0.0F ? Math.max(0.0F, Math.min(1.0F, age / life)) : 0.5F;
        return this;
    }

    public FxContext phase(float value) {
        this.phase = Math.max(0.0F, Math.min(1.0F, value));
        return this;
    }

    /** Standard fade envelope: in over the first 12%, out over the last 25% of life. */
    public float fade() {
        if (life <= 0.0F) {
            return opacity;
        }
        float p = age / life;
        float in = Math.min(1.0F, p / 0.12F);
        float out = p > 0.75F ? Math.max(0.0F, 1.0F - (p - 0.75F) / 0.25F) : 1.0F;
        return in * out * opacity;
    }
}
