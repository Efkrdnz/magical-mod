package com.efkrdnz.magical.visual;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters;
import com.efkrdnz.magical.client.renderer.fx.paint.custom.MagicalPainters;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.visual.MagicVisualContent;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every bespoke painter a profile asks for is either registered or named here as somebody else's job.
 *
 * <p>Written after the Sword Array shipped wearing a blown-white disc on the wielder's chest for the
 * whole of its development. {@code Silhouette.custom(id, extent)} is a promise that a painter answers
 * to {@code id}, and nothing in the build ever checked it: the ids are strings on one side of the mod
 * and strings on the other, the registry is filled at client setup, and a miss cost nothing that
 * raised. So three of the Sword profiles named painters that do not exist, one of them on an entity
 * that is on screen whenever the class is being played, and every screenshot taken of the school had
 * the miss in it. Nobody read a white orb as a missing painter; they read it as a spell with an orb.
 *
 * <p>This is the cheap half of that lesson. The declared side is read off the real profiles rather
 * than out of the source, so a silhouette built any way at all is counted; the registered side is the
 * real registry after {@link MagicalPainters#register()}. Both directions are checked, because the
 * mirror of a missing painter - a painter registered under a name no profile asks for - is now
 * completely silent in game and would otherwise only ever be found by noticing an effect is dull.
 */
class CustomPainterCoverageTest {

    /**
     * Ids a profile declares on purpose with no family painter behind them, each with the thing that
     * draws that object instead.
     *
     * <p>Every entry is a deliberate decision that the shared painter library has nothing to add to
     * this skill. Adding a line here is the expensive way out of a red build and it is meant to be:
     * the cheap way is to register a painter.
     */
    private static final Map<String, String> DRAWN_SOMEWHERE_ELSE = new LinkedHashMap<>();

    static {
        // The four kept skills. MagicVisualContentKept says it outright: their world visuals stay
        // on their bespoke renderers and the profile is there for the cast circle, the palette and
        // the first-person feedback. Nothing puts these definitions on a SpellEffectEntity, so the
        // silhouette is a placeholder for a body that was never moved onto the shared library.
        DRAWN_SOMEWHERE_ELSE.put("space_walker", "SpacePocketPortalRenderer");
        DRAWN_SOMEWHERE_ELSE.put("black_flames",
                "BlackFlameProjectileRenderer, BlackFlameArcRenderer, BlackFlameBrandRenderer, BlackFlameFieldRenderer");
        DRAWN_SOMEWHERE_ELSE.put("gabriel", "JudgementBeamRenderer and GabrielHolyFieldRenderer");
        DRAWN_SOMEWHERE_ELSE.put("abyssal_discharge", "AbyssalDischargeRenderer");
        // Blood Sacrifice has no effect entity at all: pressing it opens BloodSacrificeScreen, and
        // what the pact leaves behind is two lists of passives and a clock.
        DRAWN_SOMEWHERE_ELSE.put("blood_sacrifice", "nothing - the skill is a screen and a pact");
        // The Sword school, and the reason this file exists. SwordArrayEntity and SwordBladeEntity
        // wear a Sword profile and both renderers extend ProfileRendererShell, so the silhouette
        // walk really does reach these: the first two have been held out of it with .forModes()
        // since the white disc was found, and "loose" is still in it on every blade Loose fires -
        // harmless only because the miss now draws nothing. SwordArrayRenderer draws the formation
        // and SwordBladeRenderer draws Duskfall itself, its aura and its glint, so the shared
        // painters have nothing to add to a school whose whole subject is one piece of steel.
        DRAWN_SOMEWHERE_ELSE.put("call_the_blade", "SwordArrayRenderer and SwordBladeRenderer");
        DRAWN_SOMEWHERE_ELSE.put("the_bearing", "nothing carries this profile onto an entity");
        DRAWN_SOMEWHERE_ELSE.put("loose", "SwordBladeRenderer, on every blade Loose fires");
    }

    private static final Map<String, Set<ResourceLocation>> DECLARED = new LinkedHashMap<>();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MagicVisualContent.init();
        MagicalPainters.register();
        for (Map.Entry<ResourceLocation, VisualProfile> entry : VisualProfiles.all().entrySet()) {
            for (Silhouette silhouette : entry.getValue().silhouettes()) {
                if (silhouette.family() != Silhouette.Family.CUSTOM) {
                    continue;
                }
                DECLARED.computeIfAbsent(silhouette.customPainter(), key -> new LinkedHashSet<>())
                        .add(entry.getKey());
            }
        }
        assertFalse(DECLARED.isEmpty(),
                "no CUSTOM silhouette was found in the whole roster, so this file is checking nothing");
    }

    @Test
    void everyCustomSilhouetteNamesAPainterThatExists() {
        Set<String> missing = new TreeSet<>();
        for (Map.Entry<String, Set<ResourceLocation>> entry : DECLARED.entrySet()) {
            String id = entry.getKey();
            if (CustomPainters.has(id) || DRAWN_SOMEWHERE_ELSE.containsKey(id)) {
                continue;
            }
            missing.add(id + " (declared by " + entry.getValue() + ")");
        }
        assertTrue(missing.isEmpty(),
                "these Silhouette.custom ids have no painter and are not named as drawn elsewhere, so"
                        + " FxPainters walks straight into CustomPainters' miss branch for them: " + missing);
    }

    @Test
    void noPainterIsRegisteredUnderANameNothingAsksFor() {
        Set<String> orphans = new TreeSet<>(CustomPainters.ids());
        orphans.removeAll(DECLARED.keySet());
        assertTrue(orphans.isEmpty(),
                "these painters are registered under ids no profile declares - a typo on either side"
                        + " reads in game as an effect that is simply missing: " + orphans);
    }

    @Test
    void theAllowListSaysNothingThatIsNoLongerTrue() {
        for (Map.Entry<String, String> entry : DRAWN_SOMEWHERE_ELSE.entrySet()) {
            assertTrue(DECLARED.containsKey(entry.getKey()),
                    entry.getKey() + " is excused here as drawn by " + entry.getValue()
                            + ", but no profile declares it any more - drop the line");
            assertFalse(CustomPainters.has(entry.getKey()),
                    entry.getKey() + " has a real painter now, so \"" + entry.getValue()
                            + "\" is stale and this line hides the next miss");
        }
    }

    @Test
    void aMissingPainterDrawsNothingAtAll() {
        VisualProfile profile = VisualProfiles.of(MagicContent.STARTER_SKILL);
        Silhouette unknown = Silhouette.custom("no painter answers to this", 4.0F);
        // A null FxContext is the assertion. Every painter in the library dereferences it on the
        // first line it draws with, so surviving this call is the only evidence available in a
        // headless test that the miss branch emits no geometry at all. The second call is the
        // warn-once bookkeeping taking its other branch.
        assertDoesNotThrow(() -> CustomPainters.paint(null, profile, unknown),
                "a missing painter drew something - an unregistered id must cost nothing on screen");
        assertDoesNotThrow(() -> CustomPainters.paint(null, profile, unknown),
                "the second miss on the same id behaved differently from the first");
    }
}
