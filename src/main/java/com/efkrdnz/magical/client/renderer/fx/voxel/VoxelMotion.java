package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import net.minecraft.util.Mth;

/**
 * Where one voxel is at one instant. Pure, allocation-free, and the reason the field cannot stutter.
 *
 * <p>Every term is a continuous function of the age it is handed, and the age the renderer supplies
 * already carries the partial tick. There is no stored previous position, so there is nothing to
 * interpolate and nothing to fall out of step: asking for the position at 12.5 ticks gives the
 * position at 12.5 ticks. Smoothness is a property of the arithmetic rather than something the frame
 * loop has to maintain.
 *
 * <p>Continuity is deliberate at every seam - the idle wobble fades in from zero amplitude rather
 * than switching on, and the growth and dissolve ramps meet their neighbours at the same value. A
 * discontinuity here is a visible pop in a thousand places at once.
 */
public final class VoxelMotion {

    /** Where the idle wobble starts fading in, as a fraction of the flight. */
    private static final float IDLE_FROM = 0.6F;

    /** Ticks a cube takes to grow to full size once it starts moving. */
    private static final float GROW_TICKS = 2.0F;

    /** Width of the soft edge on the distance cut, in LOD-key units. */
    private static final float LOD_EDGE = 0.06F;

    private VoxelMotion() {
    }

    /**
     * When this voxel starts moving.
     *
     * <p>Proportional to how far out the voxel belongs, which is the whole of "forms from the middle
     * outwards": near voxels leave first and far ones trail, so the shape grows outward from the
     * caster instead of appearing all at once.
     *
     * <p>{@code formTicks} comes from the synced field rather than from the style, because the
     * server damages along the same front and the two have to agree on one number.
     */
    public static float delayFor(VoxelStyle.Timeline timing, float formTicks, float rank01,
            int index, int seed) {
        return formTicks * rank01
                + BloodShapeGeometry.hash01(index * 3 + seed * 7) * timing.jitter();
    }

    /** Flight progress in 0..1: zero before the delay, one once the voxel has landed. */
    public static float progress(VoxelStyle.Timeline timing, float delay, float age) {
        if (timing.launch() <= 0.0F) {
            return age >= delay ? 1.0F : 0.0F;
        }
        return Mth.clamp((age - delay) / timing.launch(), 0.0F, 1.0F);
    }

    /**
     * The voxel's centre at this instant, written into {@code out} as x, y, z.
     *
     * <p>The path is a quadratic bezier through a lifted control point, because blood thrown across
     * a room arcs; a straight interpolation reads as sliding. The launch point is spread over a
     * small sphere at the caster so the field leaves them rather than appearing around them.
     *
     * @param progress flight progress from {@link #progress}
     */
    public static void place(VoxelStyle style, int index, int seed, float age, float progress,
            float targetX, float targetY, float targetZ,
            float anchorX, float anchorY, float anchorZ, float[] out) {

        float h1 = BloodShapeGeometry.hash01(index * 3 + seed * 7);
        float h2 = BloodShapeGeometry.hash01(index * 5 + seed * 11 + 1);
        float h3 = BloodShapeGeometry.hash01(index * 7 + seed * 13 + 2);

        float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);

        // A uniform point on a sphere: cos-distributed height, uniform angle. Sampling the angles
        // independently would cluster at the poles and send the blood out in two jets.
        float angle = h1 * Mth.TWO_PI;
        float cosZ = h2 * 2.0F - 1.0F;
        float sinZ = Mth.sqrt(Math.max(0.0F, 1.0F - cosZ * cosZ));
        float burst = style.burstRadius() * (0.3F + 0.7F * h3);
        float fromX = anchorX + Mth.cos(angle) * sinZ * burst;
        float fromY = anchorY + cosZ * burst;
        float fromZ = anchorZ + Mth.sin(angle) * sinZ * burst;

        float midX = (fromX + targetX) * 0.5F;
        float midY = (fromY + targetY) * 0.5F + style.archHeight() * (0.5F + h3);
        float midZ = (fromZ + targetZ) * 0.5F;

        float inv = 1.0F - eased;
        out[0] = inv * inv * fromX + 2.0F * inv * eased * midX + eased * eased * targetX;
        out[1] = inv * inv * fromY + 2.0F * inv * eased * midY + eased * eased * targetY;
        out[2] = inv * inv * fromZ + 2.0F * inv * eased * midZ + eased * eased * targetZ;

        if (progress > IDLE_FROM) {
            // Faded in from zero rather than switched on, so there is no kink at the threshold.
            float weight = style.idleAmp() * (progress - IDLE_FROM) / (1.0F - IDLE_FROM);
            float phase = age * style.idleHz();
            out[0] += Mth.sin(phase + h1 * Mth.TWO_PI) * weight;
            out[1] += Mth.sin(phase * 1.31F + h2 * Mth.TWO_PI) * weight;
            out[2] += Mth.sin(phase * 0.83F + h3 * Mth.TWO_PI) * weight;
        }
    }

    /**
     * How far gone this voxel is, 0 solid to 1 nothing. Drives both the shader's dissolve and the
     * cube's own size.
     *
     * <p>The tips drain first, mirroring the way they arrived last, so the field retreats toward the
     * caster instead of vanishing all over at once.
     */
    public static float erosion(VoxelStyle.Timeline timing, float rank01, float delay,
            float age, float life) {
        float arriving = timing.materialise() <= 0.0F ? 0.0F
                : 1.0F - Mth.clamp((age - delay) / timing.materialise(), 0.0F, 1.0F);
        if (life <= 0.0F || timing.dissolve() <= 0.0F) {
            return arriving;
        }
        // Scaled by rank rather than by its complement: the further out a voxel belongs, the
        // earlier it starts leaving, so the field retreats toward the caster. The other way round
        // drains the middle first and the shape collapses outward, which reads as it breaking.
        float startsAt = life - timing.dissolve() - timing.dissolveSpread() * rank01;
        float leaving = Mth.clamp((age - startsAt) / timing.dissolve(), 0.0F, 1.0F);
        return Math.max(arriving, leaving);
    }

    /**
     * Half the cube's edge at this instant.
     *
     * <p>Fading is done by shrinking rather than by alpha, on purpose. This render type writes depth
     * and flushes before every glow in the library, so a half-transparent cube punches an opaque
     * hole in whatever is behind it. A cube that shrinks to nothing needs no draw order at all.
     */
    public static float halfExtent(VoxelStyle style, float pitch, int index, int seed,
            float age, float delay, float erosion, float lodEdge) {
        float grow = Mth.clamp((age - delay) / GROW_TICKS, 0.0F, 1.0F);
        float variation = 0.8F + 0.4F * BloodShapeGeometry.hash01(index * 5 + seed * 11 + 1);
        return pitch * style.cube() * 0.5F * grow * (1.0F - erosion) * lodEdge * variation;
    }

    /**
     * The share of the field drawn at this distance, quantized so it changes rarely.
     *
     * <p>Deliberately not {@code FxBudget.lodForDistance}: that multiplies by frame pressure, whose
     * counter is reset partway through the frame by an unrelated caller, so feeding it into a
     * thousand-voxel count makes hundreds of cubes blink every frame. It also returns exactly zero
     * past forty-eight blocks while these entities render to two hundred and fifty-six, which would
     * drop the field off a cliff in the middle of its own view distance.
     */
    public static float lodKeep(double distance) {
        float raw = distance <= 16.0D ? 1.0F
                : distance >= 64.0D ? 0.0F
                : (float) (1.0D - (distance - 16.0D) / 48.0D);
        return Math.round(raw * 16.0F) / 16.0F;
    }

    /**
     * How much of a voxel survives the distance cut: 1 well inside, 0 outside, and a soft ramp
     * between so the boundary shrinks away rather than popping.
     */
    public static float lodEdge(float lodKey, float keep) {
        if (keep >= 1.0F) {
            return 1.0F;
        }
        return Mth.clamp((keep - lodKey) / LOD_EDGE, 0.0F, 1.0F);
    }
}
