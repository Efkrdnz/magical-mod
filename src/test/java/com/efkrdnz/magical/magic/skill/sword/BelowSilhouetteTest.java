package com.efkrdnz.magical.magic.skill.sword;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.renderer.sword.SwordBladeRenderer.Geometry;
import com.efkrdnz.magical.magic.sword.stance.Pattern;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What Below puts in the world: the swords coming up, measured against the cylinder that catches,
 * and a warning that is the ground itself.
 *
 * <p>It used to be a plate fan, a clock-faced ring and a pool of grit, all FX, and the ring was the
 * sixteen ticks of warning the dodge depends on. The school draws nothing but steel now, so the
 * rise is Duskfall - one blade per sword that went under, laid out in the stance's pattern - and
 * the warning is the ground trembling on the rim of the ring, in vanilla crumbs of whatever the
 * ring is standing on, sent by the server on a schedule this file pins.
 *
 * <p>Both bounds, always: the steel may never outgrow the cylinder it hits in, and it may never
 * shrink to a token either. {@code SwordBladeRenderer$Geometry} is a nested class of plain
 * arithmetic for exactly this, so the drawn blade's reach is measured without a renderer.
 */
class BelowSilhouetteTest {

    private static final float[] YAWS = {0.0F, 37.0F, 90.0F, 180.0F, -135.0F};

    private static VisualProfile profile;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        profile = new BelowSkill().profile().build();
    }

    // ---- what is drawn -----------------------------------------------------------------------------

    @Test
    void theOnlyDrawingIsTheSteelAndOnlyOnceItRises() {
        int warning = BelowSkill.drawModeAt(BelowSkill.COMMIT_TICK - 1);
        int rise = BelowSkill.drawModeAt(BelowSkill.COMMIT_TICK);
        boolean steel = false;
        for (Silhouette silhouette : profile.silhouettes()) {
            boolean ours = silhouette.family() == Silhouette.Family.CUSTOM && "below".equals(silhouette.customPainter());
            if (!ours) {
                for (int mode = 0; mode < 8; mode++) {
                    assertFalse(silhouette.drawnIn(mode),
                            "Below paints a " + silhouette.family() + " in draw mode " + mode + ", which is not a sword");
                }
                continue;
            }
            steel = true;
            assertTrue(silhouette.drawnIn(rise), "nothing comes up when the strike commits");
            assertFalse(silhouette.drawnIn(warning),
                    "the blades are standing in the world during the warning, so the dodge reads off"
                            + " the steel and not off the ground that is about to break");
        }
        assertTrue(steel, "Below draws no steel at all");
    }

    @Test
    void theRisenSteelStandsInsideTheCylinderThatCatches() {
        // SkillTargets.hostilesInCylinder measures ERUPT_RADIUS about the committed point and up
        // to ERUPT_HEIGHT. A blade drawn outside that promises a hit that never lands.
        double lateral = Geometry.lateralReach();
        for (Pattern pattern : Pattern.values()) {
            for (int count = 1; count <= 12; count++) {
                for (float yaw : YAWS) {
                    for (int i = 0; i < count; i++) {
                        double[] offset = BelowSkill.riseOffset(pattern, i, count, yaw);
                        double out = Math.hypot(offset[0], offset[1]) + lateral;
                        assertTrue(out <= BelowSkill.ERUPT_RADIUS + 1.0E-9D,
                                pattern + " blade " + i + " of " + count + " is drawn " + out
                                        + " blocks out, and the cylinder catches within " + BelowSkill.ERUPT_RADIUS);
                    }
                }
            }
        }
        double top = BelowSkill.RISE_LIFT + Geometry.axialReach();
        assertTrue(top <= BelowSkill.ERUPT_HEIGHT + 1.0E-9D,
                "a risen blade reaches " + top + " blocks up, and the cylinder catches to " + BelowSkill.ERUPT_HEIGHT);
    }

    @Test
    void theRisenSteelIsNotAToken() {
        assertTrue(BelowSkill.RISE_LIFT + Geometry.axialReach() >= BelowSkill.ERUPT_HEIGHT * 0.5D,
                "the blades do not come up half the height they kill to");
        for (Pattern pattern : Pattern.values()) {
            for (int count = 2; count <= 12; count++) {
                double widest = 0.0D;
                for (int i = 0; i < count; i++) {
                    double[] offset = BelowSkill.riseOffset(pattern, i, count, 0.0F);
                    widest = Math.max(widest, Math.hypot(offset[0], offset[1]));
                }
                assertTrue(widest >= BelowSkill.ERUPT_RADIUS * 0.5D,
                        pattern + " with " + count + " swords comes up in a knot " + widest
                                + " blocks across a ring of " + BelowSkill.ERUPT_RADIUS);
            }
        }
        double[] alone = BelowSkill.riseOffset(Pattern.RING, 0, 1, 45.0F);
        assertEquals(0.0D, Math.hypot(alone[0], alone[1]), 1.0E-9D, "a single sword does not come up on the mark");
    }

    @Test
    void theSteelComesUpOutOfTheGround() {
        double buried = Geometry.axialReach();
        assertTrue(BelowSkill.riseHeight(BelowSkill.COMMIT_TICK, buried) + buried <= 1.0E-9D,
                "the blades start the rise already out of the ground");
        assertEquals(BelowSkill.RISE_LIFT, BelowSkill.riseHeight(BelowSkill.HIT_TICK, buried), 1.0E-9D,
                "the blades are not up when the strike lands");
        double last = Double.NEGATIVE_INFINITY;
        for (float age = BelowSkill.COMMIT_TICK; age <= BelowSkill.HIT_TICK; age += 0.25F) {
            double height = BelowSkill.riseHeight(age, buried);
            assertTrue(height >= last - 1.0E-12D, "the blades sink again at age " + age);
            last = height;
        }
    }

    // ---- the warning -------------------------------------------------------------------------------

    @Test
    void theGroundTremblesThroughTheWholeWarningAndNotAfter() {
        // The warning is the dodge, so it may not have a gap in it: sixteen ticks to clear the
        // ring, and every one of them has ground moving on the rim within a pulse of it.
        assertTrue(BelowSkill.tremorAt(0), "the ground is still until after the press");
        for (int age = 0; age < BelowSkill.COMMIT_TICK; age++) {
            boolean recent = false;
            for (int back = 0; back < BelowSkill.TREMOR_INTERVAL && age - back >= 0; back++) {
                recent |= BelowSkill.tremorAt(age - back);
            }
            assertTrue(recent, "nothing trembles for a whole pulse around tick " + age + " of the warning");
        }
        for (int age = BelowSkill.COMMIT_TICK; age <= BelowSkill.HIT_TICK + 4; age++) {
            assertFalse(BelowSkill.tremorAt(age), "the ground is still trembling at tick " + age
                    + ", after the strike has committed and the steel is coming up");
        }
    }
}
