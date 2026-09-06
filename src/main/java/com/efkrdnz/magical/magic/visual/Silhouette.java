package com.efkrdnz.magical.magic.visual;

/**
 * One drawable shape of a skill. The PRIMARY silhouette's (family, form, kind) is the roster-wide
 * visual uniqueness key. Painters interpret family + form into geometry and kind into shader data.
 *
 * @param sizeA main extent (radius / half-width / length) in blocks
 * @param sizeB secondary extent (height / thickness / half-height) in blocks
 * @param count shader count field (strands, spikes, cells, ...)
 * @param paramB shader paramB field (taper, softness, gloss, ...)
 * @param offsetY vertical offset from the effect origin in blocks
 */
public record Silhouette(
        Family family,
        Form form,
        int kind,
        float sizeA,
        float sizeB,
        int count,
        int paramB,
        ColorRole role,
        float opacity,
        String customPainter,
        float offsetY,
        int modeMask) {

    public enum Family {
        GLYPH, ORB, FILAMENT, FIELD, MARK, RIFT, LENS, BODY, SWARM, CUSTOM
    }

    public enum Form {
        BILLBOARD, STACK, TUBE, HELIX, LINK, RING, COLUMN, TRAIL, CROSSED_BLADES, PLATE_FAN, SPIKE_CLUSTER,
        SLAB, PRISM, CAGE, DISC, DOME, WALL, CYLINDER, SPHERE, PANE, FAN, BOULDER, RIG, CHAIN, POOL, CLOUD,
        FIGURE, GROUND_SEAM, GROUND_STAR, VERTICAL_PANE, HORIZONTAL_SLIT_EYE, BASIN, CONE_SPOT, LANE, NONE
    }

    public Silhouette(Family family, Form form, int kind, float sizeA, float sizeB, int count, int paramB, ColorRole role, float opacity, String customPainter) {
        this(family, form, kind, sizeA, sizeB, count, paramB, role, opacity, customPainter, 0.0F, -1);
    }

    public SilhouetteKey key() {
        return new SilhouetteKey(family, form, kind);
    }

    public Silhouette withOpacity(float value) {
        return new Silhouette(family, form, kind, sizeA, sizeB, count, paramB, role, value, customPainter, offsetY, modeMask);
    }

    public Silhouette withRole(ColorRole value) {
        return new Silhouette(family, form, kind, sizeA, sizeB, count, paramB, value, opacity, customPainter, offsetY, modeMask);
    }

    public Silhouette withOffset(float value) {
        return new Silhouette(family, form, kind, sizeA, sizeB, count, paramB, role, opacity, customPainter, value, modeMask);
    }

    public Silhouette withSize(float a, float b) {
        return new Silhouette(family, form, kind, a, b, count, paramB, role, opacity, customPainter, offsetY, modeMask);
    }

    /** Draw only for entities whose draw mode (mode >> 1) is one of the given values. */
    public Silhouette forModes(int... drawModes) {
        int mask = 0;
        for (int m : drawModes) {
            mask |= 1 << m;
        }
        return new Silhouette(family, form, kind, sizeA, sizeB, count, paramB, role, opacity, customPainter, offsetY, mask);
    }

    public boolean drawnIn(int drawMode) {
        return (modeMask & (1 << drawMode)) != 0;
    }

    public static Silhouette glyph(float radius) {
        return new Silhouette(Family.GLYPH, Form.DISC, 0, radius, 0.0F, 1, 0, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette orb(Form form, FxKinds.Orb kind, float radius) {
        return new Silhouette(Family.ORB, form, kind.id(), radius, radius, 6, 10, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette orb(Form form, FxKinds.Orb kind, float radius, int count, int paramB) {
        return new Silhouette(Family.ORB, form, kind.id(), radius, radius, count, paramB, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette filament(Form form, FxKinds.Filament kind, int strands, float halfWidth) {
        return new Silhouette(Family.FILAMENT, form, kind.id(), halfWidth, 1.0F, strands, 8, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette filament(Form form, FxKinds.Filament kind, int strands, float halfWidth, float length, int taper) {
        return new Silhouette(Family.FILAMENT, form, kind.id(), halfWidth, length, strands, taper, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette body(Form form, FxKinds.Body kind, int plates, float size) {
        return new Silhouette(Family.BODY, form, kind.id(), size, size, plates, 12, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette body(Form form, FxKinds.Body kind, int plates, float size, float height) {
        return new Silhouette(Family.BODY, form, kind.id(), size, height, plates, 12, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette field(Form form, FxKinds.Field kind, float extent, float height) {
        return new Silhouette(Family.FIELD, form, kind.id(), extent, height, 8, 4, ColorRole.BASE, 0.35F, "");
    }

    public static Silhouette field(Form form, FxKinds.Field kind, float extent, float height, int tiling, int weight) {
        return new Silhouette(Family.FIELD, form, kind.id(), extent, height, tiling, weight, ColorRole.BASE, 0.35F, "");
    }

    public static Silhouette mark(FxKinds.Mark kind, float radius) {
        return new Silhouette(Family.MARK, Form.DISC, kind.id(), radius, radius, 8, 6, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette mark(FxKinds.Mark kind, float radius, int count) {
        return new Silhouette(Family.MARK, Form.DISC, kind.id(), radius, radius, count, 6, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette rift(Form form, FxKinds.Rift kind, float halfHeight, float halfWidth, FxKinds.RiftInterior interior) {
        return new Silhouette(Family.RIFT, form, kind.id(), halfWidth, halfHeight, 8, interior.id(), ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette lens(FxKinds.Lens kind, float radius, int warp) {
        return new Silhouette(Family.LENS, Form.BILLBOARD, kind.id(), radius, radius, 4, warp, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette swarm(Form form, FxKinds.Smoke particle, int count, float radius) {
        return new Silhouette(Family.SWARM, form, particle.id(), radius, radius, count, 16, ColorRole.BASE, 1.0F, "");
    }

    public static Silhouette swarm(Form form, FxKinds.Smoke particle, int count, float radius, float height) {
        return new Silhouette(Family.SWARM, form, particle.id(), radius, height, count, 16, ColorRole.BASE, 1.0F, "");
    }

    /** Bespoke painter; the painter id doubles as the kind so every custom look has its own silhouette key. */
    public static Silhouette custom(String painterId, float extent) {
        return new Silhouette(Family.CUSTOM, Form.NONE, painterId.hashCode() & 0x7FFFFFFF, extent, extent, 1, 0, ColorRole.BASE, 1.0F, painterId);
    }

    public record SilhouetteKey(Family family, Form form, int kind) {
        @Override
        public String toString() {
            return family + "/" + form + "/" + kind;
        }
    }
}
