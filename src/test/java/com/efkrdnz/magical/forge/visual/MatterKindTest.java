package com.efkrdnz.magical.forge.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The table every piece of forge matter moves by. Each of these is a failure that draws something
 * and fails nothing: an offset out of step shows one kind in another kind's frames, a ramp that runs
 * backwards cools a coal to white, and a kind that never fades pops out of the air.
 */
class MatterKindTest {

    private static final float EPSILON = 1.0E-5f;

    @Test
    void theKindsLieEndToEndOnOneSpriteList() {
        int at = 0;
        for (MatterKind kind : MatterKind.values()) {
            assertEquals(at, kind.offset(), kind.name());
            assertTrue(kind.frames() > 0, kind.name());
            at += kind.frames();
        }
        assertEquals(at, MatterKind.SPRITES);
    }

    @Test
    void lightIsFullBrightAllItsLifeAndMatterIsNever() {
        for (MatterKind kind : MatterKind.values()) {
            for (int i = 0; i < 20; i++) {
                float t = i / 20.0f;
                if (kind.hot() >= 1.0f) {
                    assertTrue(kind.fullBright(t), kind + " goes dark at " + t);
                } else if (kind.hot() <= 0.0f) {
                    assertFalse(kind.fullBright(t), kind + " glows at " + t);
                }
            }
        }
        assertTrue(MatterKind.EMBER.fullBright(0.1f), "a coal does not glow as it leaves the blade");
        assertFalse(MatterKind.EMBER.fullBright(0.9f), "a coal is still burning as it dies");
    }

    @Test
    void theColourOnlyEverRunsForward() {
        for (MatterKind kind : MatterKind.values()) {
            float previous = kind.ramp(0.0f);
            assertEquals(kind.hot() > 0.0f ? 0.0f : 1.0f, previous, EPSILON, kind + " starts on the wrong stop");
            for (int i = 1; i <= 40; i++) {
                float ramp = kind.ramp(i / 40.0f);
                assertTrue(ramp >= previous - EPSILON, kind + " runs its colours backwards");
                assertTrue(ramp <= 2.0f + EPSILON, kind + " runs past its last colour");
                previous = ramp;
            }
            assertEquals(kind.hot() >= 1.0f ? 1.0f : 2.0f, previous, EPSILON, kind + " ends on the wrong stop");
        }
    }

    @Test
    void everythingFadesOutAndNothingIsEverMoreThanOpaque() {
        for (MatterKind kind : MatterKind.values()) {
            for (int i = 0; i <= 40; i++) {
                float alpha = kind.alpha(i / 40.0f);
                assertTrue(alpha >= 0.0f && alpha <= 1.0f, kind + " alpha " + alpha);
            }
            assertEquals(0.0f, kind.alpha(1.0f), EPSILON, kind + " is still there at the end of its life");
        }
        assertTrue(MatterKind.SMOKE.alpha(0.0f) <= 0.5f, "smoke drawn solid is a grey card");
    }

    @Test
    void anAgingKindWalksEveryFrameInOrder() {
        for (MatterKind kind : MatterKind.values()) {
            int previous = 0;
            for (int i = 0; i <= 100; i++) {
                int frame = kind.frameAt(i / 100.0f);
                assertTrue(frame >= previous && frame < kind.frames(), kind + " frame " + frame);
                previous = frame;
            }
            assertEquals(kind.frames() - 1, previous, kind + " never reaches its last frame");
        }
    }

    @Test
    void onlyWhatFallsBounces() {
        for (MatterKind kind : MatterKind.values()) {
            if (kind.gravity() <= 0.0f) {
                assertEquals(0.0f, kind.bounce(), kind + " bounces but never lands");
            }
            assertTrue(kind.bounce() < 1.0f, kind + " gains speed off a floor");
            assertTrue(kind.friction() > 0.0f && kind.friction() <= 1.0f, kind.name());
            assertTrue(kind.life() > 0, kind.name());
        }
    }

    @Test
    void everyKindIsFoundByItsOwnName() {
        for (MatterKind kind : MatterKind.values()) {
            assertEquals(kind, MatterKind.byName(kind.serializedName()).orElseThrow());
        }
        assertTrue(MatterKind.byName("plasma").isEmpty());
    }
}
