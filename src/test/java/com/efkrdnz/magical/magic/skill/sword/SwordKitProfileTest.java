package com.efkrdnz.magical.magic.skill.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.SwordMath;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What the Sword school is allowed to put in the world besides its swords: nothing.
 *
 * <p>The school was drawn with a glow round every blade, a glint on it, a thread from the chest to
 * each one, a red wash as the count ran down, a cast circle at the hand and on the ground, a muzzle
 * flash, a tinted screen, a clock-faced ring under Below and a crossed-plate greatsword - and the
 * whole of the complaint about it was that none of that was a sword. So the rule is now one line:
 * the steel is Duskfall, drawn by the school's own renderers and by two painters that draw nothing
 * but Duskfall, and a hit is a small wave of vanilla particles. Everything a {@link VisualProfile}
 * could still hand the shared FX library is pinned shut here, all six skills at once, because
 * every one of the old effects was a number in a profile that no test had an opinion about.
 *
 * <p>The circle itself stays in each profile: it is where the HUD and the codex take the skill's
 * emblem from. What goes is its <em>anchor</em>, which is the only thing that ever put it in the
 * world.
 */
class SwordKitProfileTest {

    /** The two painters that draw Duskfall and nothing else. Every other silhouette is held out. */
    private static final Set<String> STEEL_PAINTERS = Set.of("one_blade", "below");

    private static List<VisualProfile> kit;
    private static VisualProfile oneBlade;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        oneBlade = new OneBladeSkill().profile().build();
        kit = List.of(
                new CallTheBladeSkill().profile().build(),
                new SwordStanceSkill().profile().build(),
                new LooseSkill().profile().build(),
                new BelowSkill().profile().build(),
                oneBlade,
                new TheKeelSkill().profile().build());
    }

    // ---- nothing but steel -------------------------------------------------------------------------

    @Test
    void noSwordSkillHangsACircleInTheWorld() {
        // SpellFx.windup places a cast circle at the profile's anchor and sends nothing at all for
        // NONE, and SpellFx.release collapses nothing where no circle was placed. Loose and Below
        // are plain presses and reached both on every cast; the other four return out of
        // castViaRegistry first, and are held to the same line so the day one of them stops doing
        // that it does not start drawing a circle.
        for (VisualProfile profile : kit) {
            assertEquals(CircleAnchor.NONE, profile.anchor(),
                    profile.skillId() + " still hangs a cast circle in the world");
        }
    }

    @Test
    void noSwordSkillWashesTheCastersScreen() {
        // The presets are what tint, shake and kick the caster's own view at the press and at the
        // release. A screen washed in the school's colour is an effect like any other.
        for (VisualProfile profile : kit) {
            assertEquals(ProfileCues.FirstPersonPreset.NONE, profile.firstPerson().cast(),
                    profile.skillId() + " washes the caster's screen when it is pressed");
            assertEquals(ProfileCues.FirstPersonPreset.NONE, profile.release().casterPreset(),
                    profile.skillId() + " washes the caster's screen when it lets go");
        }
    }

    @Test
    void everySilhouetteIsHeldOutExceptTheSteel() {
        // Both sword renderers extend ProfileRendererShell and call super.render, which walks the
        // profile's silhouettes and paints every one whose mode mask admits the entity's draw
        // mode - so anything left in a mask here is drawn on the formation, on every blade in
        // the air, or on the effect entity a skill spawns.
        for (VisualProfile profile : kit) {
            for (Silhouette silhouette : profile.silhouettes()) {
                boolean steel = silhouette.family() == Silhouette.Family.CUSTOM
                        && STEEL_PAINTERS.contains(silhouette.customPainter());
                if (steel) {
                    continue;
                }
                for (int mode = 0; mode < 8; mode++) {
                    assertFalse(silhouette.drawnIn(mode),
                            profile.skillId() + " paints a " + silhouette.family() + " "
                                    + silhouette.form() + " in draw mode " + mode + ", which is not a sword");
                }
            }
        }
    }

    @Test
    void everySwordSkillStillCarriesItsEmblem() {
        // The floor. Anchoring nothing must not be mistaken for having no circle: the HUD's cast
        // card and the codex draw the emblem off the circle script, and a blank one is a key with
        // no picture on it.
        for (VisualProfile profile : kit) {
            assertNotEquals(EmblemId.BLANK, profile.castCircle().emblem(),
                    profile.skillId() + " has lost the emblem its HUD card is drawn from");
        }
    }

    // ---- the greatsword ----------------------------------------------------------------------------

    @Test
    void theGreatswordIsDuskfallDrawnByItsOwnPainter() {
        Silhouette primary = oneBlade.primary();
        assertEquals(Silhouette.Family.CUSTOM, primary.family(),
                "the greatsword is a family silhouette again, which is an FX shape and not the steel");
        assertEquals("one_blade", primary.customPainter(), "the greatsword no longer names its steel painter");
        for (int mode = 0; mode < 2; mode++) {
            assertTrue(primary.drawnIn(mode), "the greatsword is not drawn in draw mode " + mode);
        }
    }

    @Test
    void theGreatswordIsNeverDrawnLongerThanTheBladeItIs() {
        // The painter cannot follow the fusion's real length - the profile is built once and the
        // blade grows with the swords that made it - so it draws the one length that is true of
        // every fusion: the shortest the skill can make. A short drawing of a long blade is a
        // reading the wielder can correct; a long drawing of a short one is a promise of reach.
        double shortest = SwordMath.oneBladeReach(1);
        float drawn = oneBlade.primary().sizeA();
        assertTrue(drawn <= shortest + 1.0E-5D,
                "the greatsword is drawn " + drawn + " blocks long and the shortest one the skill can make is " + shortest);
        assertTrue(drawn >= 1.5F, "the greatsword is drawn " + drawn + " blocks long, which is a knife");
    }
}
