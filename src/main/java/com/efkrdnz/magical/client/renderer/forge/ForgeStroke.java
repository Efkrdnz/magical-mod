package com.efkrdnz.magical.client.renderer.forge;

import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.mojang.blaze3d.vertex.VertexConsumer;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;

/**
 * What a strike is painted with: the smear consumer every blade is drawn on, the glint consumer
 * the hot lip of a lit element is drawn on a second time (absent for an element that gives off no
 * light), the atlas row the element wears, the strike's own place on the tile, and how bright its
 * glint burns - the weapon's grade, which used to brighten the whole blade.
 */
public record ForgeStroke(VertexConsumer smear, @Nullable VertexConsumer glint, ForgeSmear.Row row,
        float offset, float glintStrength) {

    /** The same stroke with no glint: for a lagged copy, which is where the blade was, not its edge. */
    public ForgeStroke dull() {
        return glint == null ? this : new ForgeStroke(smear, null, row, offset, glintStrength);
    }

    /** The stroke for a trail copy {@code lag} behind the head: the head keeps its glint, a copy does not. */
    public ForgeStroke at(float lag) {
        return lag > 0.0f ? dull() : this;
    }

    /** Where light that is not the blade itself goes: the glint pass if the element has one. */
    public VertexConsumer light() {
        return glint != null ? glint : smear;
    }

    /** The u of a point {@code t} along this sweep: head-anchored for an arc, whole tiles for a ring. */
    public float u(Sweep sweep, float t) {
        float span = sweep.toDegrees() - sweep.fromDegrees();
        if (ForgeSmear.closed(span)) {
            return ForgeSmear.ring(t, Mth.TWO_PI * sweep.radius());
        }
        return ForgeSmear.head(t, sweep.radius() * Math.abs(span) * Mth.DEG_TO_RAD, offset);
    }

    /** How opaque this sweep is at {@code t}: a ring is the same all the way round. */
    public float ramp(Sweep sweep, float t) {
        return ForgeSmear.closed(sweep.toDegrees() - sweep.fromDegrees()) ? 1.0f : ForgeSmear.headRamp(t);
    }
}
