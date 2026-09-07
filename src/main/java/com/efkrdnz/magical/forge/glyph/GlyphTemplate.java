package com.efkrdnz.magical.forge.glyph;

import java.util.ArrayList;
import java.util.List;

/**
 * A reference glyph: the strokes it is drawn with plus the normalized cloud they resample to.
 *
 * @param id          bare path id, e.g. {@code "fire"}
 * @param category    which slot of the chain this glyph fills
 * @param strokes     the reference drawing, in normalized canvas coordinates
 * @param cloud       {@code strokes} put through {@link GlyphNormalizer#normalize}
 * @param acceptScore lowest score at which a drawing may be recognized as this template
 */
public record GlyphTemplate(
        String id,
        GlyphCategory category,
        List<List<GlyphPoint>> strokes,
        List<GlyphPoint> cloud,
        float acceptScore) {

    public GlyphTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("template id must not be blank");
        }
        strokes = deepCopy(strokes);
        cloud = List.copyOf(cloud);
    }

    /** Builds a template from its strokes, deriving the cloud and stamping stroke ids. */
    public static GlyphTemplate of(
            String id, GlyphCategory category, float acceptScore, List<List<GlyphPoint>> strokes) {
        List<List<GlyphPoint>> stamped = withStrokeIds(strokes);
        return new GlyphTemplate(id, category, stamped, GlyphNormalizer.normalize(stamped), acceptScore);
    }

    private static List<List<GlyphPoint>> withStrokeIds(List<List<GlyphPoint>> strokes) {
        List<List<GlyphPoint>> out = new ArrayList<>(strokes.size());
        for (int strokeId = 0; strokeId < strokes.size(); strokeId++) {
            List<GlyphPoint> source = strokes.get(strokeId);
            List<GlyphPoint> stamped = new ArrayList<>(source.size());
            for (GlyphPoint p : source) {
                stamped.add(new GlyphPoint(p.x(), p.y(), strokeId));
            }
            out.add(List.copyOf(stamped));
        }
        return List.copyOf(out);
    }

    private static List<List<GlyphPoint>> deepCopy(List<List<GlyphPoint>> strokes) {
        List<List<GlyphPoint>> out = new ArrayList<>(strokes.size());
        for (List<GlyphPoint> stroke : strokes) {
            out.add(List.copyOf(stroke));
        }
        return List.copyOf(out);
    }
}
