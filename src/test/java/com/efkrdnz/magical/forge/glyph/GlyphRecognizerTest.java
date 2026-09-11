package com.efkrdnz.magical.forge.glyph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class GlyphRecognizerTest {

    /** Highest score any template may reach against a template that is not itself. */
    private static final float MAX_CROSS_SCORE = 0.80f;

    private static final float MIN_SELF_SCORE = 0.95f;

    static Stream<String> templateIds() {
        return ForgeGlyphLibrary.all().stream().map(GlyphTemplate::id);
    }

    private static GlyphTemplate template(String id) {
        return ForgeGlyphLibrary.byId(id)
                .orElseThrow(() -> new AssertionError("unknown template id: " + id));
    }

    private static List<List<GlyphPoint>> transform(
            List<List<GlyphPoint>> strokes, float scale, float dx, float dy) {
        List<List<GlyphPoint>> out = new ArrayList<>(strokes.size());
        for (List<GlyphPoint> stroke : strokes) {
            List<GlyphPoint> moved = new ArrayList<>(stroke.size());
            for (GlyphPoint p : stroke) {
                moved.add(new GlyphPoint(p.x() * scale + dx, p.y() * scale + dy, p.strokeId()));
            }
            out.add(moved);
        }
        return out;
    }

    private static List<List<GlyphPoint>> jitter(List<List<GlyphPoint>> strokes, float amount, long seed) {
        Random random = new Random(seed);
        List<List<GlyphPoint>> out = new ArrayList<>(strokes.size());
        for (List<GlyphPoint> stroke : strokes) {
            List<GlyphPoint> noisy = new ArrayList<>(stroke.size());
            for (GlyphPoint p : stroke) {
                noisy.add(new GlyphPoint(
                        p.x() + (float) (random.nextDouble() * 2 - 1) * amount,
                        p.y() + (float) (random.nextDouble() * 2 - 1) * amount,
                        p.strokeId()));
            }
            out.add(noisy);
        }
        return out;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templateIds")
    void eachTemplateRecognizesItself(String id) {
        GlyphTemplate expected = template(id);

        RecognitionResult result = ForgeGlyphLibrary.recognizer().recognize(expected.strokes());

        assertEquals(RecognitionResult.Status.ACCEPTED, result.status(),
                id + " scored " + result.bestScore() + " with runner-up "
                        + result.second().map(GlyphTemplate::id).orElse("<none>")
                        + " at " + result.secondScore());
        assertEquals(id, result.best().orElseThrow().id());
        assertTrue(result.bestScore() >= MIN_SELF_SCORE,
                id + " self score " + result.bestScore() + " < " + MIN_SELF_SCORE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templateIds")
    void recognitionIsInvariantToTranslationAndScale(String id) {
        List<List<GlyphPoint>> moved = transform(template(id).strokes(), 0.5f, 0.3f, -0.2f);

        RecognitionResult result = ForgeGlyphLibrary.recognizer().recognize(moved);

        assertEquals(id, result.best().orElseThrow().id());
        assertTrue(result.accepted(), id + " was " + result.status());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("templateIds")
    void recognitionSurvivesThreePercentJitter(String id) {
        List<List<GlyphPoint>> noisy = jitter(template(id).strokes(), 0.03f, 1234L);

        RecognitionResult result = ForgeGlyphLibrary.recognizer().recognize(noisy);

        assertEquals(id, result.best().orElseThrow().id(),
                "jittered " + id + " was recognized as something else");
        assertTrue(result.accepted(),
                "jittered " + id + " was " + result.status() + " at " + result.bestScore()
                        + " with runner-up " + result.second().map(GlyphTemplate::id).orElse("<none>")
                        + " at " + result.secondScore());
        assertTrue(result.bestScore() >= MAX_CROSS_SCORE,
                id + " jittered score " + result.bestScore() + " < " + MAX_CROSS_SCORE);
    }

    @Test
    void templatesAreMutuallyDistinct() {
        List<GlyphTemplate> templates = ForgeGlyphLibrary.all();
        List<String> failures = new ArrayList<>();
        for (GlyphTemplate drawn : templates) {
            for (GlyphTemplate other : templates) {
                if (drawn.id().equals(other.id())) {
                    continue;
                }
                float score = GlyphRecognizer.distanceToScore(
                        PointCloudMatcher.distance(drawn.cloud(), other.cloud()));
                if (score > MAX_CROSS_SCORE) {
                    failures.add(String.format("%s vs %s = %.4f", drawn.id(), other.id(), score));
                }
            }
        }
        assertTrue(failures.isEmpty(), "cross-scores above " + MAX_CROSS_SCORE + ": " + failures);
    }

    @Test
    void everyTemplateHasAUniqueId() {
        List<GlyphTemplate> templates = ForgeGlyphLibrary.all();

        assertEquals(40, templates.size());
        assertEquals(40, templates.stream().map(GlyphTemplate::id).distinct().count());
    }

    @ParameterizedTest(name = "{0} is never {1}")
    @CsvSource({
        "pierce, reach",
        "reach, pierce",
        "slam, high",
        "high, slam",
        "haste, echo",
        "echo, haste",
        "thrust, rising",
        "rising, thrust",
        "venom, wave",
        "wave, venom"
    })
    void orientationSensitivePairsAreNeverConfused(String drawnId, String forbiddenId) {
        RecognitionResult result = ForgeGlyphLibrary.recognizer().recognize(template(drawnId).strokes());

        assertNotEquals(forbiddenId, result.best().orElseThrow().id());
        assertEquals(drawnId, result.best().orElseThrow().id());
    }

    @Test
    void aGlyphSmallerThanTheMinimumSizeIsTooSmall() {
        List<List<GlyphPoint>> tiny = List.of(List.of(
                new GlyphPoint(0.50f, 0.50f, 0),
                new GlyphPoint(0.52f, 0.50f, 0)));

        RecognitionResult result = ForgeGlyphLibrary.recognizer().recognize(tiny);

        assertEquals(RecognitionResult.Status.TOO_SMALL, result.status());
        assertTrue(result.best().isEmpty());
        assertEquals(0f, result.bestScore(), 0f);
        assertEquals(0f, result.secondScore(), 0f);
    }

    /**
     * The accept-score gate is the only thing between a scribble and a forged weapon. Everything
     * downstream works from the id the recognizer names, so whatever it names becomes a rune, and
     * nothing else in this suite ever produces a REJECTED status: deleting
     * {@code best.score < best.template.acceptScore()} from {@link GlyphRecognizer} left all 298
     * other tests green while a drawing like the one below forged a weapon.
     */
    @Test
    void aDrawingThatMatchesNoTemplateWellEnoughIsRejected() {
        List<List<GlyphPoint>> scribble = scribble();
        assertTrue(GlyphNormalizer.boundingSize(scribble) >= GlyphRecognizer.TOO_SMALL_SIZE,
                "the scribble has to clear TOO_SMALL_SIZE, or the size gate is what refused it");

        RecognitionResult result = ForgeGlyphLibrary.recognizer().recognize(scribble);

        GlyphTemplate best = result.best().orElseThrow();
        assertTrue(result.bestScore() < best.acceptScore(),
                "the scribble now scores " + result.bestScore() + " against " + best.id()
                        + ", which accepts from " + best.acceptScore()
                        + " - this test can no longer say anything, pick a worse drawing");
        assertEquals(RecognitionResult.Status.REJECTED, result.status(),
                "a drawing short of its best template's accept score has to be REJECTED, not "
                        + result.status() + " as " + best.id());
    }

    /**
     * The same gate with the margin made obvious, so the pin does not rest on how close the real
     * library's floor happens to sit: a template nothing short of a near-perfect copy satisfies
     * refuses a good drawing. Without the accept-score check this comes back ACCEPTED.
     */
    @Test
    void aScoreBelowTheTemplatesOwnAcceptScoreIsRejectedHoweverCloseTheMatch() {
        GlyphTemplate slash = template("slash");
        GlyphRecognizer perfectionist = new GlyphRecognizer(
                List.of(GlyphTemplate.of(slash.id(), slash.category(), 0.999f, slash.strokes())));

        RecognitionResult result = perfectionist.recognize(jitter(slash.strokes(), 0.03f, 1234L));

        assertTrue(result.bestScore() > MAX_CROSS_SCORE,
                "the drawing has to be a good match first, or this proves nothing: "
                        + result.bestScore());
        assertEquals(RecognitionResult.Status.REJECTED, result.status(),
                "scored " + result.bestScore() + " against an accept score of 0.999");
    }

    /**
     * A blob of unconnected dabs with one stray far off. Resampling never interpolates across the
     * gap between strokes, so the cloud stays two clumps with nothing between them - which is not
     * how any of the thirty-five templates is shaped.
     */
    private static List<List<GlyphPoint>> scribble() {
        List<List<GlyphPoint>> strokes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            strokes.add(dab(0.18f + (i % 3) * 0.004f, 0.18f + (i / 3) * 0.004f, i));
        }
        strokes.add(dab(0.96f, 0.96f, 4));
        return List.copyOf(strokes);
    }

    private static List<GlyphPoint> dab(float x, float y, int strokeId) {
        return List.of(new GlyphPoint(x, y, strokeId), new GlyphPoint(x + 0.003f, y + 0.003f, strokeId));
    }

    @Test
    void anEmptyGlyphIsTooSmall() {
        RecognitionResult result = ForgeGlyphLibrary.recognizer().recognize(List.of());

        assertEquals(RecognitionResult.Status.TOO_SMALL, result.status());
        assertTrue(result.best().isEmpty());
    }

    @Test
    void twoIdenticalTemplatesOfTheSameCategoryAreAmbiguous() {
        GlyphTemplate slash = template("slash");
        GlyphRecognizer recognizer = new GlyphRecognizer(List.of(
                GlyphTemplate.of("slash_a", GlyphCategory.FORM, 0.55f, slash.strokes()),
                GlyphTemplate.of("slash_b", GlyphCategory.FORM, 0.55f, slash.strokes())));

        RecognitionResult result = recognizer.recognize(slash.strokes());

        assertEquals(RecognitionResult.Status.AMBIGUOUS, result.status());
        assertTrue(result.second().isPresent());
    }

    @Test
    void aSingleTemplateRecognizerHasNoRunnerUp() {
        GlyphTemplate slash = template("slash");
        GlyphRecognizer recognizer = new GlyphRecognizer(List.of(slash));

        RecognitionResult result = recognizer.recognize(slash.strokes());

        assertEquals(RecognitionResult.Status.ACCEPTED, result.status());
        assertTrue(result.second().isEmpty());
        assertEquals(0f, result.secondScore(), 0f);
    }

    @Test
    void gradeTemplatesUseTheGradeAcceptScore() {
        assertEquals(0.50f, template("crude").acceptScore(), 1e-6f);
        assertEquals(0.80f, template("divine").acceptScore(), 1e-6f);
        assertEquals(GlyphCategory.FORM.defaultAcceptScore(), template("slash").acceptScore(), 1e-6f);
    }

    @Test
    void distanceToScoreFollowsTheDollarPSpec() {
        assertEquals(1.0f, GlyphRecognizer.distanceToScore(0f), 1e-6f);
        assertEquals(0.5f, GlyphRecognizer.distanceToScore(4f), 1e-6f);
        assertEquals(0.0f, GlyphRecognizer.distanceToScore(8f), 1e-6f);
        assertEquals(0.0f, GlyphRecognizer.distanceToScore(20f), 1e-6f);
    }

    @Test
    void categoryMarginsMatchTheSpec() {
        assertEquals(0.08f, GlyphCategory.GRADE.ambiguityMargin(), 1e-6f);
        for (GlyphCategory category : GlyphCategory.values()) {
            assertEquals(0.55f, category.defaultAcceptScore(), 1e-6f);
            if (category != GlyphCategory.GRADE) {
                assertEquals(0.06f, category.ambiguityMargin(), 1e-6f);
            }
        }
    }
}
