package com.efkrdnz.magical.client.renderer.sword;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The Sword school draws its swords and nothing else, read straight off the source.
 *
 * <p>A profile can be pinned as data, and {@code SwordKitProfileTest} does; but the glow round each
 * blade, the glint on it, the threads to the chest and the red wash never lived in a profile. They
 * were calls in the two renderers - a beam, an orb, a tinted overlay - and a call is only visible
 * to a test that reads the file. So this one does, with the comments stripped so the history of
 * why each of them went cannot trip it: the renderers and the two steel painters may render
 * Duskfall through the item renderer, and may not reach the FX painters, the FX kinds or the
 * overlay texture at all. The server half is held to the same line: no sword hit spawns the old
 * clash flare or the profile impact grammar - a hit is {@code SwordImpacts}, which is vanilla
 * particles and nothing else.
 */
class SwordSteelOnlyTest {

    private static final String SOURCE_PACKAGE = "src/main/java/com/efkrdnz/magical";

    private static final int MAX_DEPTH_TO_PROJECT_ROOT = 6;

    /** Everything that ever drew something round a sword that was not the sword. */
    private static final List<String> NOT_STEEL = List.of(
            "FilamentPainter.beam", "OrbPainter", "BodyPainter", "MarkPainter", "FxKinds",
            "OverlayTexture.pack", "RED_OVERLAY_V", "WHITE_OVERLAY_V", "SpellParticles");

    /** The drawing half: both renderers and the painters that draw the greatsword and the rise. */
    private static final List<String> DRAWING = List.of(
            "client/renderer/sword/SwordBladeRenderer.java",
            "client/renderer/sword/SwordArrayRenderer.java",
            "client/renderer/fx/paint/custom/SwordPainters.java");

    /** Every server file a sword hits something in. */
    private static final List<String> HITS = List.of(
            "entity/sword/SwordBladeEntity.java",
            "magic/sword/stance/StanceWatchService.java",
            "magic/skill/sword/BelowSkill.java",
            "magic/skill/sword/OneBladeSkill.java",
            "magic/skill/sword/LooseSkill.java");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void theRenderersDrawSteelAndNothingRoundIt() throws IOException {
        for (String file : DRAWING) {
            String code = code(file);
            for (String forbidden : NOT_STEEL) {
                assertFalse(code.contains(forbidden), file + " still reaches " + forbidden
                        + ", which draws something round the sword that is not the sword");
            }
        }
    }

    @Test
    void theSteelIsDuskfallThroughTheItemRenderer() throws IOException {
        // The floor: stripping the effects must not strip the sword. The blade renderer is the one
        // place the model is drawn, and both the Array and the two painters draw through it.
        assertTrue(code("client/renderer/sword/SwordBladeRenderer.java").contains("renderStatic("),
                "the blade renderer no longer draws the Duskfall model at all");
        assertTrue(code("client/renderer/sword/SwordArrayRenderer.java").contains("SwordBladeRenderer.blade("),
                "the formation no longer draws its swords through the blade renderer");
        assertTrue(code("client/renderer/fx/paint/custom/SwordPainters.java").contains("SwordBladeRenderer."),
                "the greatsword and the rise are no longer drawn through the blade renderer");
    }

    @Test
    void aSwordHitIsAWaveAndNotAFlare() throws IOException {
        for (String file : HITS) {
            String code = code(file);
            assertFalse(code.contains("SkillClashEffectEntity"), file + " still throws the clash flare at a hit");
            assertFalse(code.contains("SpellFx.impact("), file + " still fires the profile impact grammar at a hit");
            assertFalse(code.contains("SpellFx.burst("), file + " still fires a profile particle burst at a hit");
        }
        for (String file : List.of("entity/sword/SwordBladeEntity.java", "magic/sword/stance/StanceWatchService.java",
                "magic/skill/sword/BelowSkill.java", "magic/skill/sword/OneBladeSkill.java")) {
            assertTrue(code(file).contains("SwordImpacts."), file + " lands a hit and throws no wave for it");
        }
    }

    private static String code(String relative) throws IOException {
        String source = Files.readString(sourceRoot().resolve(relative), StandardCharsets.UTF_8);
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll("")).replaceAll("");
    }

    private static Path sourceRoot() {
        Path at = Path.of("").toAbsolutePath();
        for (int up = 0; at != null && up < MAX_DEPTH_TO_PROJECT_ROOT; up++, at = at.getParent()) {
            Path source = at.resolve(SOURCE_PACKAGE);
            if (Files.isDirectory(source)) {
                return source;
            }
        }
        throw new AssertionError("could not find " + SOURCE_PACKAGE + " above " + Path.of("").toAbsolutePath());
    }
}
