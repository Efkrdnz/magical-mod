package com.efkrdnz.magical.magic.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A complete magic circle as data: one emblem, an outer N-gon frame, and a stack of glyph layers.
 * Built with one fluent line per skill; the painter interprets it every frame.
 */
public record CircleScript(
        int outerSides,
        EmblemId emblem,
        SpinSignature spin,
        boolean inkOutwardIn,
        int stackCount,
        float stackSpacing,
        List<CircleLayer> layers) {

    public static Builder of(SchoolMaterial material) {
        return new Builder(material);
    }

    /** The importance-0 subset: what survives at LOD 0 and what impact stamps draw. */
    public CircleScript identityOnly() {
        List<CircleLayer> kept = new ArrayList<>();
        for (CircleLayer layer : layers) {
            if (layer.importance() == 0) {
                kept.add(layer);
            }
        }
        return new CircleScript(outerSides, emblem, spin, inkOutwardIn, 1, 0.0F, List.copyOf(kept));
    }

    public CircleScript scaled(float factor) {
        List<CircleLayer> scaled = new ArrayList<>(layers.size());
        for (CircleLayer layer : layers) {
            scaled.add(layer.scaled(factor));
        }
        return new CircleScript(outerSides, emblem, spin, inkOutwardIn, stackCount, stackSpacing * factor, List.copyOf(scaled));
    }

    /** Sneak variation: mirrored spin signs, same look. */
    public CircleScript mirroredSpin() {
        List<CircleLayer> flipped = new ArrayList<>(layers.size());
        for (CircleLayer layer : layers) {
            flipped.add(layer.withSpin(-layer.spinDegPerTick()));
        }
        return new CircleScript(outerSides, emblem, spin, inkOutwardIn, stackCount, stackSpacing, List.copyOf(flipped));
    }

    /** The first authored band; the builder's thin SOLID_RING frame accent (count 1) is skipped. */
    public Optional<CircleLayer> primaryBand() {
        for (CircleLayer layer : layers) {
            if (layer.kind().isPrimaryBandCandidate() && !(layer.kind() == GlyphKind.SOLID_RING && layer.count() == 1)) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }

    public Optional<CircleLayer> stampLayer() {
        for (CircleLayer layer : layers) {
            if (layer.kind() == GlyphKind.STAMP_BAND) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }

    public Optional<CircleLayer> coreLayer() {
        for (CircleLayer layer : layers) {
            if (layer.kind() == GlyphKind.CORE) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
    }

    public int hotLayerCount() {
        int n = 0;
        for (CircleLayer layer : layers) {
            if (layer.role() == ColorRole.HOT) {
                n++;
            }
        }
        return n;
    }

    public int emblemLayerCount() {
        int n = 0;
        for (CircleLayer layer : layers) {
            if (layer.kind() == GlyphKind.EMBLEM) {
                n++;
            }
        }
        return n;
    }

    /** The validated uniqueness tuple (emblem, sides, primary band kind+count, stamp, core). */
    public String signature() {
        CircleLayer band = primaryBand().orElse(null);
        CircleLayer stamp = stampLayer().orElse(null);
        CircleLayer core = coreLayer().orElse(null);
        return emblem + "|" + outerSides + "|" + (band == null ? "-" : band.kind() + ":" + band.count())
                + "|" + (stamp == null ? "-" : String.valueOf(stamp.stampOrEmblem()))
                + "|" + (core == null ? "-" : String.valueOf(core.paramB()));
    }

    public static final class Builder {
        private final SchoolMaterial material;
        private final List<CircleLayer> layers = new ArrayList<>();
        private EmblemId emblem = EmblemId.BLANK;
        private int outerSides;
        private int frameThickness = 2;
        private boolean nestedFrame;
        private float frameRotationDeg;
        private SpinSignature spin;
        private boolean inkOutwardIn;
        private int stackCount = 1;
        private float stackSpacing;
        private int paletteVariant;
        private int bandCursor;
        private boolean hotAssigned;

        private Builder(SchoolMaterial material) {
            this.material = material;
            this.outerSides = material.defaultFrameSides();
            this.spin = material.defaultSpin();
        }

        public Builder emblem(EmblemId value) {
            this.emblem = value;
            return this;
        }

        public Builder frame(int sides) {
            this.outerSides = Math.max(3, sides);
            return this;
        }

        public Builder frame(int sides, FrameStyle style) {
            frame(sides);
            this.nestedFrame = style == FrameStyle.NESTED;
            this.frameThickness = style == FrameStyle.THIN ? 0 : style == FrameStyle.THICK ? 4 : 2;
            return this;
        }

        public Builder frameRotated(float degrees) {
            this.frameRotationDeg = degrees;
            return this;
        }

        public Builder band(GlyphKind kind, int count) {
            return band(kind, count, hotAssigned ? ColorRole.BASE : ColorRole.HOT);
        }

        public Builder band(GlyphKind kind, int count, ColorRole role) {
            if (!kind.isBand()) {
                throw new IllegalArgumentException(kind + " is not a band kind");
            }
            if (role == ColorRole.HOT) {
                hotAssigned = true;
            }
            layers.add(bandLayer(kind, count, 2, role, 0));
            return this;
        }

        public Builder stamps(StampId stamp, int count) {
            layers.add(bandLayer(GlyphKind.STAMP_BAND, count, stamp.atlasCell(), ColorRole.BRIGHT, stamp.atlasCell()));
            return this;
        }

        public Builder arcSweep(float radiusFraction, ColorRole role) {
            layers.add(new CircleLayer(GlyphKind.ARC_SWEEP, radiusFraction - 0.05F, radiusFraction + 0.05F, 1, 3, 0.0F, role, 1.2F, 1, 0.0F, 1.0F, 0, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            return this;
        }

        public Builder orbit(int count, float radiusFraction, int sealSides) {
            layers.add(new CircleLayer(GlyphKind.ORBIT_SEAL, 0.0F, 0.09F, Math.max(3, sealSides), 1, 1.4F, ColorRole.BRIGHT, 1.0F, 3, 0.0F, 6.0F, 0, count, radiusFraction, 0.5F, 0.0F, 360.0F, 0));
            return this;
        }

        public Builder spokes(int count, float innerFraction, boolean needle) {
            int paramB = Math.round(Math.max(0.0F, Math.min(innerFraction, 0.9F)) * 16.0F) | (needle ? 16 : 0);
            layers.add(new CircleLayer(GlyphKind.SPOKES, 0.0F, 0.92F, count, paramB, 0.0F, ColorRole.DIM, 0.9F, 2, 0.0F, 6.0F, 0, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            return this;
        }

        public Builder star(int points, int skip) {
            layers.add(new CircleLayer(GlyphKind.STAR, 0.0F, 0.86F, points, Math.max(1, skip), 0.0F, ColorRole.BASE, 1.0F, 2, 0.0F, 8.0F, 0, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            return this;
        }

        public Builder pentagram() {
            layers.add(new CircleLayer(GlyphKind.PENTAGRAM, 0.0F, 0.86F, 5, 2, 0.0F, ColorRole.BASE, 1.0F, 2, 0.0F, 8.0F, 0, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            return this;
        }

        public Builder lattice(int divisions) {
            layers.add(new CircleLayer(GlyphKind.LATTICE, 0.0F, 0.9F, divisions, 1, 0.0F, ColorRole.DIM, 0.8F, 3, 0.0F, 6.0F, 0, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            return this;
        }

        public Builder core(CoreKind kind) {
            return core(kind, hotAssigned ? ColorRole.BRIGHT : ColorRole.HOT);
        }

        public Builder core(CoreKind kind, ColorRole role) {
            if (role == ColorRole.HOT) {
                hotAssigned = true;
            }
            layers.add(new CircleLayer(GlyphKind.CORE, 0.0F, 0.34F, 1, kind.id(), 0.0F, kind.dark() ? ColorRole.INK : role, 1.0F, 2, 0.0F, 6.0F, 0, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            return this;
        }

        public Builder layer(CircleLayer custom) {
            layers.add(custom);
            return this;
        }

        public Builder mirror(int axes) {
            if (!layers.isEmpty()) {
                CircleLayer last = layers.remove(layers.size() - 1);
                layers.add(new CircleLayer(last.kind(), last.r0(), last.r1(), last.count(), last.paramB(), last.spinDegPerTick(), last.role(), last.intensity(), last.importance(), last.inkDelay(), last.inkTicks(), last.stampOrEmblem(), last.orbitCount(), last.orbitRadius(), last.orbitSpeed(), 0.0F, 360.0F / Math.max(1, axes) * 0.6F, axes));
            }
            return this;
        }

        public Builder spin(SpinSignature value) {
            this.spin = value;
            return this;
        }

        public Builder inkOutwardIn(boolean value) {
            this.inkOutwardIn = value;
            return this;
        }

        public Builder stack(int count, float spacing) {
            this.stackCount = Math.max(1, count);
            this.stackSpacing = spacing;
            return this;
        }

        public Builder palette(int variant) {
            this.paletteVariant = variant;
            return this;
        }

        public int paletteVariant() {
            return paletteVariant;
        }

        private CircleLayer bandLayer(GlyphKind kind, int count, int paramB, ColorRole role, int stamp) {
            float outer = 0.84F - bandCursor * 0.13F;
            float inner = outer - 0.09F;
            bandCursor++;
            int importance = bandCursor == 1 ? 0 : Math.min(3, bandCursor);
            return new CircleLayer(kind, Math.max(inner, 0.3F), Math.max(outer, 0.38F), Math.max(1, count), paramB, 0.0F, role, 1.0F, importance, 0.0F, 6.0F, stamp, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0);
        }

        public CircleScript build() {
            List<CircleLayer> out = new ArrayList<>();
            // outer frame (always identity)
            out.add(new CircleLayer(GlyphKind.FRAME, 0.0F, 1.0F, outerSides, frameThickness, 0.0F, ColorRole.BASE, 1.0F, 0, 0.0F, 6.0F, 0, 0, 0.0F, 0.0F, frameRotationDeg, 360.0F, 0));
            out.add(new CircleLayer(GlyphKind.SOLID_RING, 0.9F, 0.96F, 1, 1, 0.0F, ColorRole.DIM, 0.8F, 1, 1.0F, 6.0F, 0, 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            if (nestedFrame) {
                out.add(new CircleLayer(GlyphKind.FRAME, 0.0F, 0.88F, outerSides, Math.max(0, frameThickness - 1), 0.0F, ColorRole.DIM, 0.9F, 2, 2.0F, 6.0F, 0, 0, 0.0F, 0.0F, frameRotationDeg + 180.0F / outerSides, 360.0F, 0));
            }
            out.addAll(layers);
            // the emblem is always last (drawn on top) and always identity
            out.add(new CircleLayer(GlyphKind.EMBLEM, 0.0F, 0.3F, emblem.atlasCell() & 63, emblem.atlasCell() >> 6, 0.0F, ColorRole.BRIGHT, 1.2F, 0, 4.0F, 8.0F, emblem.atlasCell(), 0, 0.0F, 0.0F, 0.0F, 360.0F, 0));
            // assign spin signs and staggered ink delays
            List<CircleLayer> finished = new ArrayList<>(out.size());
            float base = spin.degPerTick();
            int i = 0;
            for (CircleLayer layer : out) {
                float s = layer.spinDegPerTick();
                if (s == 0.0F && layer.kind() != GlyphKind.EMBLEM && layer.kind() != GlyphKind.CORE && spin != SpinSignature.SWEEP) {
                    s = base * (spin.alternate() && (i % 2 == 1) ? -1.0F : 1.0F) * (layer.kind().isBand() ? 1.0F : 0.6F);
                }
                float delay = layer.inkDelay() > 0.0F ? layer.inkDelay() : (inkOutwardIn ? i : (out.size() - 1 - i)) * 1.2F;
                finished.add(layer.withSpin(s).withInk(delay, layer.inkTicks()));
                i++;
            }
            return new CircleScript(outerSides, emblem, spin, inkOutwardIn, stackCount, stackSpacing, List.copyOf(finished));
        }
    }

    public enum FrameStyle {
        SINGLE,
        NESTED,
        THIN,
        THICK
    }
}
