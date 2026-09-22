package com.efkrdnz.magical.magic.skill.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.sword.SwordBladeRenderer.Geometry;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What one blade landing is allowed to put on the screen, measured against the fact that up to
 * {@link SwordBladeEntity#MAX_IN_FLIGHT} of them land at once.
 *
 * <p>Every other skill in the mod takes its impact grammar from
 * {@code ProfileCues.ImpactSpec.forTier}, and that table is priced for a skill that lands
 * <em>one</em> hit. Loose is the one skill in the game that lands a dozen inside the same handful
 * of ticks, and nothing anywhere in the build had an opinion about that. This file is the
 * opinion, and every rule in it is written in the form "twelve of these must cost no more than
 * one of those" rather than as a taste bound on a single number, because the count is the defect
 * and a per-hit bound would be re-broken by the next skill that fires in a fan.
 *
 * <p>Both bounds, always, the way {@code SwordKitProfileTest} and {@code BelowSilhouetteTest}
 * hold their own numbers. A ceiling alone invites the panic fix, and the panic fix in an additive
 * pipeline is deletion: the flash is on {@code MagicalFxRenderTypes.plasmaOrb()}, which blends
 * {@code ONE, ONE}, so the only two obvious moves are "blinding" and "gone" and the second one is
 * green on any test that only says "not too bright".
 *
 * <p>Pure: a {@link VisualProfile} is plain data by design - that is what makes it safe on a
 * dedicated server - so everything a painter will be handed can be read straight off it.
 * {@code SwordBladeRenderer$Geometry} is a nested class for exactly this, so measuring the flash
 * against the blade never drags {@code EntityRenderer} into a unit test, and the two constants
 * taken from {@link SwordBladeEntity} are compile-time ints that the compiler inlines.
 */
class LooseFlashTest {

    /** Floats through a 1.6 multiply and a pi: a hair of slack. */
    private static final double EPSILON = 1.0E-5D;

    /**
     * What {@code TransientVisuals} multiplies {@code flashSize} by before handing it to the
     * painter: {@code OrbPainter.billboard(ctx, impact.flashKind(), impact.flashSize() * 1.6F *
     * payload.scale(), ...)}. {@code FxMesh.square()} spans -1..1 and the shader discards outside
     * {@code r > 1.0}, so that product is the drawn disc's radius in blocks and not its diameter.
     */
    private static final float FLASH_DRAW = 1.6F;

    /**
     * The scale the five-argument {@code SpellFx.impact} passes. {@code SpellFx} floors any scale
     * at 0.3 and {@code SkillTargets.hurt} can raise it to 2.0, so this is the middle of the range
     * rather than the worst case - the worst case is a caller's decision and this file is about
     * the profile.
     */
    private static final float NOMINAL_SCALE = 1.0F;

    /**
     * How long {@code TransientVisuals} draws the flash for: {@code if (ctx.age < 10.0F)}, and the
     * same ten ticks are {@code CREATURE_IMPACT_TICKS}.
     */
    private static final int FLASH_WINDOW_TICKS = 10;

    /** A player: the smallest body a volley can land in, and the one it is balanced against. */
    private static final double VICTIM_WIDTH = 0.6D;

    private static final double VICTIM_HEIGHT = 1.8D;

    private static VisualProfile profile;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        profile = new LooseSkill().profile().build();
    }

    private static ProfileCues.ImpactSpec impact() {
        return profile.impact();
    }

    /** What the tier table would have handed this skill, for the "twelve against one" rules. */
    private static ProfileCues.ImpactSpec ordinaryHit() {
        return ProfileCues.ImpactSpec.forTier(profile.tier(), impact().markKind(),
                impact().matterKind(), impact().victimOverlay());
    }

    /** The area of sky one flash covers, in square blocks, at the nominal scale. */
    private static double flashArea(float flashSize) {
        double radius = flashSize * FLASH_DRAW * NOMINAL_SCALE;
        return Math.PI * radius * radius;
    }

    // ---- the flash -----------------------------------------------------------------------------

    @Test
    void oneFlashIsTheMarkOfOneBladeAndNotOfTheVolley() {
        // The ceiling is the blade, not the tier, because the tier is the thing that was wrong.
        // A flash is a mark of the sword that made it, and a mark that covers more sky than the
        // sword does has stopped being about the sword. Geometry.LENGTH is 1.17 blocks of drawn
        // Duskfall and 2 * HALF_BREADTH is 0.47 across the flat, so one blade's whole drawn
        // footprint is 0.55 square blocks - and forTier(4) wanted a disc of 3.2 blocks of radius,
        // 32 square blocks, fifty-eight times the sword it is a picture of.
        double footprint = Geometry.LENGTH * 2.0D * Geometry.HALF_BREADTH;
        double area = flashArea(impact().flashSize());
        assertTrue(area <= footprint + EPSILON,
                "one Loose blade flashes over " + area + " square blocks where the blade that made it"
                        + " only covers " + footprint + " - the flash is a picture of the volley, not of a hit");
        // The floor, and it is the half that matters most here. There is no dimmer BLOOM_FLASH:
        // the shader branch reaches core = pow(1 - rr, 3.0) * 2.0, mixes 85% toward white, adds
        // core * 0.4 and then additiveOut multiplies by glow again, and TransientVisuals hands
        // the painter a hardcoded opacity of 1.0. So the only lever the profile has on this
        // effect is its size, and the only way to answer "too bright" from here is to make it
        // small - which means the next person to answer it can make it invisible without a
        // single test going red. A flash narrower than the flat of the blade is not a mark of
        // that blade landing.
        double across = 2.0D * impact().flashSize() * FLASH_DRAW * NOMINAL_SCALE;
        double flat = 2.0D * Geometry.HALF_BREADTH;
        assertTrue(across >= flat - EPSILON,
                "one Loose blade flashes " + across + " blocks across where the blade is " + flat
                        + " across the flat - the hit is drawn smaller than the thing that made it");
    }

    @Test
    void aWholeVolleyOfFlashesCostsNoMoreLightThanOneOrdinaryHit() {
        // The rule the whole file exists for. plasmaOrb() is ADDITIVE_TRANSPARENCY - ONE, ONE -
        // so twelve flashes are twelve times the light with no saturation anywhere in the blend
        // to save it, and they arrive inside a handful of ticks because the blades leave together.
        // A press that lands twelve hits may spend what a press that lands one hit spends, and no
        // more: that is what makes a volley read as twelve hits rather than as one white field.
        double volley = SwordBladeEntity.MAX_IN_FLIGHT * flashArea(impact().flashSize());
        double one = flashArea(ordinaryHit().flashSize());
        assertTrue(volley <= one + EPSILON,
                "a full volley flashes over " + volley + " square blocks where one ordinary hit of this"
                        + " tier spends " + one + " - twelve impacts are being billed twelve times");
        // The floor. Twelve blades going into a man must at least light the man up: the volley's
        // flashes together may not come to less than the frontal area of the smallest body they
        // can land in. Without this, dividing by the count once more is always green.
        double body = VICTIM_WIDTH * VICTIM_HEIGHT;
        assertTrue(volley >= body - EPSILON,
                "a full volley flashes over " + volley + " square blocks and a player is " + body
                        + " square blocks of target - twelve swords went in and nothing showed");
    }

    // ---- the matter ----------------------------------------------------------------------------

    @Test
    void aVolleySpendsOneOrdinaryHitWorthOfSparksSharedOutTwelveWays() {
        // SpellParticles.burst is called once per impact with Math.round(matterCount * scale), and
        // the particle system is shared: a press that spends twelve times any other skill's budget
        // does not only look wrong, it drops FxBudget.pressure() and demotes every other effect in
        // the frame - including the twelve swords the frame is actually of.
        int volley = SwordBladeEntity.MAX_IN_FLIGHT * impact().matterCount();
        int one = ordinaryHit().matterCount();
        assertTrue(volley <= one,
                "a full volley throws " + volley + " matter particles where one ordinary hit of this tier"
                        + " throws " + one + " - the count was never divided by the number of blades");
        assertTrue(volley >= one,
                "a full volley throws only " + volley + " matter particles where one ordinary hit throws "
                        + one + " - the shower has been divided away rather than shared out");
    }

    // ---- the channels that are not light --------------------------------------------------------

    @Test
    void nothingOneBladeDoesToItsVictimIsWorthDoingTwelveTimes() {
        // FirstPersonEffects.apply takes the max of shake, freeze and fov, so those three cannot
        // multiply - but it APPENDS the overlay to a list it only trims at eight, so twelve
        // impacts really do put eight overlays on the victim at once. VICTIM_HEAVY is 0.4 alpha
        // for twelve ticks with two ticks of freeze; eight of those overlapping is a white-out
        // with the camera locked. VICTIM_ZONE is the row this enum already keeps for a body being
        // hit over and over by one source, which is exactly what a volley is.
        ProfileCues.FirstPersonPreset victim = impact().victimPreset();
        assertEquals(0, victim.freezeTicks(),
                "a Loose blade freezes its victim's camera for " + victim.freezeTicks()
                        + " ticks, and up to " + SwordBladeEntity.MAX_IN_FLIGHT + " of them land at once");
        assertEquals(0, victim.shakeTicks(),
                "a Loose blade shakes its victim's camera, twelve times over");
        assertTrue(victim.alpha() < ProfileCues.FirstPersonPreset.VICTIM_HIT.alpha(),
                "a Loose blade washes its victim's screen at " + victim.alpha()
                        + " alpha, which is a single-hit price on a twelve-hit skill");
        // The floor: being hit by a volley must still be something that happens to you.
        assertTrue(victim.overlayTicks() > 0 && victim.alpha() > 0.0F,
                "a Loose blade tells its victim nothing at all");
    }

    @Test
    void aWallTakesOneScarPerBladeAndNotOneCastCircle() {
        // The mark and the stamp are only drawn when payload.victimId() < 0, so this is the
        // wall case: a volley into stone. stampDeliveryCircle runs GlyphCirclePainter over a whole
        // delivery circle at every one of them - twelve cast circles for one cast - and the cast
        // circle has already been drawn once, at the hand, where it means something.
        assertFalse(impact().stampDeliveryCircle(),
                "every blade of a Loose volley stamps its own delivery circle on the wall it hits");
        // Both bounds on how long the scar lives, and both are about the blade rather than taste.
        // A scar shorter than the flash is a gap in the middle of one hit; a scar that outlives
        // the blade standing in the wall is a claim about a hit that is no longer there.
        assertTrue(impact().markTicks() >= FLASH_WINDOW_TICKS,
                "a Loose scar lasts " + impact().markTicks() + " ticks and the flash over it lasts "
                        + FLASH_WINDOW_TICKS + " - the hit goes dark in its own middle");
        assertTrue(impact().markTicks() <= SwordBladeEntity.LYING_TICKS,
                "a Loose scar lasts " + impact().markTicks() + " ticks and the blade that cut it only"
                        + " stands for " + SwordBladeEntity.LYING_TICKS);
    }

    // ---- what the shared painters may add to a blade ---------------------------------------------

    @Test
    void theBladeIsDrawnByItsOwnRendererAndThisProfileAddsNothingToIt() {
        // SwordBladeEntity carries LOOSE's id on every blade Loose fires, so every one of them
        // wears this profile; SwordBladeRenderer extends ProfileRendererShell and calls
        // super.render, which walks profile.silhouettes() and paints each one whose mode mask
        // admits the blade's draw mode. Nothing is registered under the painter id "loose", so
        // that walk went straight into CustomPainters' miss branch and drew Orb.PLASMA at
        // max(0.4, sizeA * 0.5) - a one-block additive disc that clips to white at every size and
        // every distance - centred on each blade. Call the Blade and the Bearing were emptied
        // when that was found and this one was missed, which is why a volley capture still had
        // white discs in it while the Array's captures had none.
        for (Silhouette silhouette : profile.silhouettes()) {
            for (int mode = 0; mode < 8; mode++) {
                assertFalse(silhouette.drawnIn(mode),
                        "Loose's " + silhouette.family() + " silhouette is painted in draw mode " + mode
                                + ", on every blade the skill puts in the air");
            }
        }
        // The floor: emptied, not deleted. VisualProfile has no way to carry no silhouette at all
        // - build() inserts a PLASMA orb when the list is empty, which is the same white disc by
        // another route - and CustomPainterCoverageTest reads this declaration to check it against
        // its own list of ids that are drawn somewhere else.
        Silhouette primary = profile.primary();
        assertEquals(Silhouette.Family.CUSTOM, primary.family(),
                "Loose's silhouette is no longer the CUSTOM placeholder, so build() may have filled"
                        + " the list with the default orb instead");
        assertEquals("loose", primary.customPainter(),
                "Loose no longer declares the painter id its coverage allow-list is written against");
        assertTrue(Geometry.LENGTH > 0.5F,
                "the renderer that is supposed to be drawing the blade instead draws "
                        + Geometry.LENGTH + " blocks of sword");
    }
}
