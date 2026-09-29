package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.network.BeliefSyncPayload;

import java.util.List;

/** What the ring over a viewer's head says, from a belief: shape, hue and strength. */
public final class BeliefSight {
    public static final int DOUBT = 0xBDA4FF;
    public static final int CONVINCED = 0xEFC86A;
    public static final float BASE_OPACITY = 0.35F;
    public static final float OPACITY_PER_BELIEF = 0.6F;
    public static final float CONVINCED_OPACITY = 0.95F;

    public record Mark(GlyphKind kind, int rgb, float opacity) {}

    private BeliefSight() {}

    /** The ring for a belief, or null for none: no belief, or a shattered one. */
    public static Mark mark(float belief) {
        if (belief <= 0.0F) {
            return null;
        }
        if (belief >= Belief.CONVINCED) {
            return new Mark(GlyphKind.SOLID_RING, CONVINCED, CONVINCED_OPACITY);
        }
        return new Mark(GlyphKind.DASHED_RING, DOUBT, BASE_OPACITY + OPACITY_PER_BELIEF * belief);
    }

    /** A viewer's strongest belief across a scene: -1 if every row is shattered, 0 with no rows. */
    public static float strongest(List<BeliefSyncPayload.Entry> rows, int viewer) {
        float best = 0.0F;
        boolean any = false;
        boolean allShattered = true;
        for (BeliefSyncPayload.Entry entry : rows) {
            if (entry.viewer() != viewer) {
                continue;
            }
            any = true;
            if (!entry.shattered()) {
                allShattered = false;
                best = Math.max(best, entry.value());
            }
        }
        return any && allShattered ? -1.0F : best;
    }
}
