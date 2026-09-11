package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.magic.blood.BloodFieldData;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * Bakes synced field data into the arrays the painter reads, and remembers the result.
 *
 * <p><b>Why the cache is keyed on identity.</b> The entity hands back the same {@link CompoundTag}
 * instance until a sync packet replaces it, so identity is both cheap and exactly right: a new tag
 * means new data, and nothing else does. Expanding a thousand voxels sixty times a second would
 * otherwise be the most expensive thing in the frame, and all of it repeated work.
 *
 * <p>The map is bounded and self-evicting rather than hooked to a logout event. Eight entries of a
 * few thousand floats is small enough that a stale one costs nothing worth writing a lifecycle for,
 * and a bound that holds without anyone remembering to call it cannot be forgotten.
 */
public final class VoxelFields {

    private static final int MAX_CACHED = 8;
    private static final Map<CompoundTag, VoxelField> CACHE = new IdentityHashMap<>();

    private VoxelFields() {
    }

    /** The field for this synced tag, baking it the first time it is seen. Null when unreadable. */
    public static VoxelField of(CompoundTag data, VoxelStyle style, int budgetClass) {
        if (data == null) {
            return null;
        }
        VoxelField cached = CACHE.get(data);
        if (cached != null) {
            return cached;
        }
        BloodFieldData source = BloodFieldData.decode(data);
        if (source == null || source.isEmpty()) {
            return null;
        }
        VoxelField field = bake(source, style, budgetClass);
        if (field == null) {
            return null;
        }
        if (CACHE.size() >= MAX_CACHED) {
            // Oldest-insertion eviction would need a second structure to track. With eight entries
            // and one field per cast, clearing outright costs one re-bake and no bookkeeping.
            CACHE.clear();
        }
        CACHE.put(data, field);
        return field;
    }

    /**
     * Expands the spine into voxel targets.
     *
     * <p>The pitch arrives already chosen by the server, so the client never decides how dense a
     * field is - only how much of it to draw.
     */
    public static VoxelField bake(BloodFieldData source, VoxelStyle style, int budgetClass) {
        int cap = style.capFor(budgetClass);
        float[] targets = new float[cap * 3];
        float[] scratch = new float[cap * 3];
        double pitch = source.pitch();
        double wall = Math.max(pitch, source.wallHeight());
        double thickness = Math.max(0.0D, source.thickness());

        int written = 0;
        for (int line = 0; line < source.polylineCount() && written < cap; line++) {
            double[] polyline = source.polyline(line);
            if (polyline.length < 4) {
                continue;
            }
            int added = BloodShapeGeometry.expand(polyline, wall, thickness, pitch,
                    source.ownerId() * 31 + line, cap - written, scratch);
            System.arraycopy(scratch, 0, targets, written * 3, added * 3);
            written += added;
        }
        if (written == 0) {
            return null;
        }

        float[] rank = new float[written];
        float[] lodKey = new float[written];
        float maxReach = 0.0F;
        for (int i = 0; i < written; i++) {
            float u = targets[i * 3];
            float v = targets[i * 3 + 2];
            rank[i] = (float) Math.sqrt(u * u + v * v);
            maxReach = Math.max(maxReach, rank[i]);
            lodKey[i] = BloodShapeGeometry.hash01(i * 7919 + 13);
        }
        float divisor = maxReach > 1.0E-4F ? maxReach : 1.0F;
        for (int i = 0; i < written; i++) {
            rank[i] /= divisor;
        }
        return new VoxelField(targets, rank, lodKey, written, maxReach, source);
    }
}
