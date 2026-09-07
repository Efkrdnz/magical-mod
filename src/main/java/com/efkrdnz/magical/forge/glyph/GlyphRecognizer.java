package com.efkrdnz.magical.forge.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Matches a drawn glyph against a fixed template set using the $P point-cloud recognizer. */
public final class GlyphRecognizer {

    /** Bounding size below which a drawing is treated as a stray tap rather than a glyph. */
    public static final float TOO_SMALL_SIZE = 0.03f;

    private final List<GlyphTemplate> templates;

    public GlyphRecognizer(List<GlyphTemplate> templates) {
        if (templates.isEmpty()) {
            throw new IllegalArgumentException("a recognizer needs at least one template");
        }
        this.templates = List.copyOf(templates);
    }

    public List<GlyphTemplate> templates() {
        return templates;
    }

    /** Scores every template against {@code strokes} and classifies the best two. */
    public RecognitionResult recognize(List<List<GlyphPoint>> strokes) {
        if (GlyphNormalizer.boundingSize(strokes) < TOO_SMALL_SIZE) {
            return RecognitionResult.tooSmall();
        }
        List<GlyphPoint> cloud = GlyphNormalizer.normalize(strokes);
        List<Scored> scored = new ArrayList<>(templates.size());
        for (GlyphTemplate template : templates) {
            scored.add(new Scored(template, distanceToScore(PointCloudMatcher.distance(cloud, template.cloud()))));
        }
        scored.sort(Comparator.comparingDouble((Scored s) -> s.score).reversed());

        Scored best = scored.get(0);
        Optional<Scored> second = scored.size() > 1 ? Optional.of(scored.get(1)) : Optional.empty();
        float secondScore = second.map(s -> s.score).orElse(0f);
        return new RecognitionResult(
                Optional.of(best.template),
                best.score,
                second.map(s -> s.template),
                secondScore,
                classify(best, secondScore));
    }

    private static RecognitionResult.Status classify(Scored best, float secondScore) {
        if (best.score < best.template.acceptScore()) {
            return RecognitionResult.Status.REJECTED;
        }
        if (best.score - secondScore < best.template.category().ambiguityMargin()) {
            return RecognitionResult.Status.AMBIGUOUS;
        }
        return RecognitionResult.Status.ACCEPTED;
    }

    /** Converts a $P cloud distance into a 0..1 score. */
    public static float distanceToScore(float distance) {
        float normalized = distance / (GlyphNormalizer.N * 0.5f);
        return 1f - Math.max(0f, Math.min(1f, normalized / 0.5f));
    }

    private record Scored(GlyphTemplate template, float score) {
    }
}
