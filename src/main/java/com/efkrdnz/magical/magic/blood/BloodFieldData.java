package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;

/**
 * A formed blood field, as it travels from the server to the clients watching it.
 *
 * <p>What crosses is the spine the server already clipped and resampled, not the shape the player
 * drew. That is the point: the client cannot reach a different answer about how far the blood
 * reaches, because it is never asked the question. It also means no new packet type and no new
 * entity - this rides the synced tag the shared effect entity already carries.
 *
 * <p>Coordinates are canvas-local sixteenths of a block, the same quantization the stored shape
 * uses. Rotation is deliberately <em>not</em> baked in: the painter applies it every frame, so a
 * field set to keep rotating follows the caster's view smoothly rather than in tick steps.
 */
public record BloodFieldData(
        int[] spine,
        int[] ends,
        float heightOffset,
        float wallHeight,
        float thickness,
        float pitch,
        float baseYaw,
        float basePitch,
        float formTicks,
        int flags,
        int ownerId) {

    private static final String KEY_SPINE = "s";
    private static final String KEY_ENDS = "e";
    private static final String KEY_HEIGHT = "h";
    private static final String KEY_WALL = "wh";
    private static final String KEY_THICK = "th";
    private static final String KEY_PITCH = "pt";
    private static final String KEY_YAW = "y";
    private static final String KEY_TILT = "pi";
    private static final String KEY_FLAGS = "f";
    private static final String KEY_FORM = "ft";
    private static final String KEY_OWNER = "o";

    /**
     * Builds a field from clipped polylines of canvas coordinates.
     *
     * <p>Polyline breaks are kept rather than concatenated. A shape that left the canvas and came
     * back is two separate paths, and joining them would extrude a wall across a gap nobody drew -
     * which the server would then dutifully damage things inside of.
     */
    public static BloodFieldData of(List<double[]> polylines, float heightOffset, float wallHeight,
            float thickness, float pitch, float baseYaw, float basePitch, float formTicks,
            int flags, int ownerId) {
        int total = 0;
        for (double[] line : polylines) {
            total += line.length / 2;
        }
        total = Math.min(total, BloodShapeRules.MAX_SPINE_POINTS);

        int[] packed = new int[total];
        int[] rawEnds = new int[polylines.size()];
        int cursor = 0;
        int written = 0;
        for (double[] line : polylines) {
            for (int i = 0; i + 1 < line.length && cursor < total; i += 2) {
                packed[cursor++] = BloodShapeRules.pack(
                        BloodShapeRules.toUnits(line[i]), BloodShapeRules.toUnits(line[i + 1]));
            }
            rawEnds[written++] = cursor;
        }
        return new BloodFieldData(packed, dropEmptyTail(rawEnds), heightOffset, wallHeight,
                thickness, pitch, baseYaw, basePitch, formTicks, flags, ownerId);
    }

    /** Drops the trailing polylines the point cap left with nothing in them. */
    private static int[] dropEmptyTail(int[] ends) {
        int kept = 0;
        for (int i = 0; i < ends.length; i++) {
            int start = i == 0 ? 0 : ends[i - 1];
            if (ends[i] > start) {
                kept = i + 1;
            }
        }
        int[] trimmed = new int[kept];
        System.arraycopy(ends, 0, trimmed, 0, kept);
        return trimmed;
    }

    public boolean isEmpty() {
        return spine.length < 2 || ends.length == 0;
    }

    public int polylineCount() {
        return ends.length;
    }

    public int start(int polyline) {
        return polyline <= 0 ? 0 : ends[polyline - 1];
    }

    public int end(int polyline) {
        return ends[polyline];
    }

    /** One polyline unpacked into {@code u, v} blocks, ready for expansion or projection. */
    public double[] polyline(int index) {
        int from = start(index);
        int to = end(index);
        double[] out = new double[Math.max(0, to - from) * 2];
        for (int i = from, o = 0; i < to; i++) {
            out[o++] = u(i);
            out[o++] = v(i);
        }
        return out;
    }

    public double u(int index) {
        return BloodShapeRules.fromUnits(BloodShapeRules.unpackX(spine[index]));
    }

    public double v(int index) {
        return BloodShapeRules.fromUnits(BloodShapeRules.unpackY(spine[index]));
    }

    /** Furthest the field reaches from the caster in the canvas plane. */
    public float reach() {
        double max = 0.0D;
        for (int i = 0; i < spine.length; i++) {
            double u = u(i);
            double v = v(i);
            max = Math.max(max, Math.sqrt(u * u + v * v));
        }
        return (float) max;
    }

    /**
     * How far the field has formed, 0 at the middle to 1 at the outermost point.
     *
     * <p>The server damages what this front has swept past and the client delays each cube by the
     * same fraction, which is the only reason the damage lands where the blood visibly is. Both read
     * {@link #formTicks} out of this record rather than each keeping their own copy of the number.
     */
    public float formedFraction(float age) {
        return Math.max(0.0F, Math.min(1.0F, age / Math.max(1.0F, formTicks)));
    }

    public boolean keepRotating() {
        return BloodShapeRules.has(flags, BloodShapeRules.FLAG_KEEP_ROTATING);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.put(KEY_SPINE, new IntArrayTag(spine.clone()));
        // Ints rather than bytes: the spine cap is 384, which does not fit one, and a field has at
        // most a handful of polylines so the honest encoding costs sixteen bytes.
        tag.put(KEY_ENDS, new IntArrayTag(ends.clone()));
        tag.putFloat(KEY_HEIGHT, heightOffset);
        tag.putFloat(KEY_WALL, wallHeight);
        tag.putFloat(KEY_THICK, thickness);
        tag.putFloat(KEY_PITCH, pitch);
        tag.putFloat(KEY_YAW, baseYaw);
        tag.putFloat(KEY_TILT, basePitch);
        tag.putFloat(KEY_FORM, formTicks);
        tag.putByte(KEY_FLAGS, (byte) flags);
        tag.putInt(KEY_OWNER, ownerId);
        return tag;
    }

    /**
     * @return the field, or null when the tag is absent, from another build, or nonsense
     */
    public static BloodFieldData decode(CompoundTag tag) {
        if (tag == null || !tag.contains(KEY_SPINE, Tag.TAG_INT_ARRAY)) {
            return null;
        }
        int[] spine = tag.getIntArray(KEY_SPINE);
        if (spine.length < 2 || spine.length > BloodShapeRules.MAX_SPINE_POINTS) {
            return null;
        }
        int[] ends = sanitizeEnds(tag.getIntArray(KEY_ENDS), spine.length);
        return new BloodFieldData(spine, ends,
                tag.getFloat(KEY_HEIGHT), tag.getFloat(KEY_WALL), tag.getFloat(KEY_THICK),
                Math.max(0.01F, tag.getFloat(KEY_PITCH)), tag.getFloat(KEY_YAW),
                tag.getFloat(KEY_TILT), Math.max(1.0F, tag.getFloat(KEY_FORM)),
                BloodShapeRules.clampFlags(tag.getByte(KEY_FLAGS)), tag.getInt(KEY_OWNER));
    }

    /**
     * Forces the polyline table to ascend and to finish on the last point.
     *
     * <p>An end that ran backwards would walk a polyline through the array in reverse, and one that
     * stopped short would silently drop the tail of the shape. Neither is worth a crash, and both
     * are worth refusing to believe.
     */
    private static int[] sanitizeEnds(int[] raw, int points) {
        if (raw == null || raw.length == 0) {
            return new int[] {points};
        }
        int[] ends = new int[raw.length];
        int previous = 0;
        for (int i = 0; i < raw.length; i++) {
            ends[i] = Math.max(previous, Math.min(points, raw[i]));
            previous = ends[i];
        }
        ends[ends.length - 1] = points;
        return ends;
    }
}
