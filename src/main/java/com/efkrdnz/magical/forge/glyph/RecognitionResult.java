package com.efkrdnz.magical.forge.glyph;

import java.util.Optional;

/**
 * Outcome of matching one drawn glyph against a template set.
 *
 * @param best        highest scoring template, empty when the drawing was too small
 * @param bestScore   score of {@code best}, 0 when there is none
 * @param second      runner-up template, empty when the set holds fewer than two templates
 * @param secondScore score of {@code second}, 0 when there is none
 * @param status      how the caller should treat the match
 */
public record RecognitionResult(
        Optional<GlyphTemplate> best,
        float bestScore,
        Optional<GlyphTemplate> second,
        float secondScore,
        Status status) {

    /** How a recognition attempt turned out. */
    public enum Status {
        ACCEPTED,
        AMBIGUOUS,
        REJECTED,
        TOO_SMALL
    }

    /** A result nothing can be read from: the drawing was empty or below the minimum size. */
    public static RecognitionResult tooSmall() {
        return new RecognitionResult(Optional.empty(), 0f, Optional.empty(), 0f, Status.TOO_SMALL);
    }

    public boolean accepted() {
        return status == Status.ACCEPTED;
    }
}
