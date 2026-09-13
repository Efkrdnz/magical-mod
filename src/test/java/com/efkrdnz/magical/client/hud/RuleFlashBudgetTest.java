package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.HudSnapshotBudgetTest.QuadCounter;
import com.efkrdnz.magical.client.hud.RuleFlash.Shot;
import com.efkrdnz.magical.magic.SpaceRuleChange;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The rule flash rendered into a counting sink: every kind of change, at every stage of its life,
 * costs at most {@link HudBudget#FLASH_QUADS} quads and {@link HudBudget#FLASH_TEXT_DRAWS} strings,
 * and a finished or faded flash costs nothing at all.
 */
class RuleFlashBudgetTest {

    private static final long START = 100L;
    private static final float[] PHASES = {0.05F, 0.2F, 0.5F, 0.9F};
    private static final HudOptions REDUCED = new HudOptions(true, HudAnchor.TOP_LEFT, 1.0F, 1.0F, true, true, false, true, false);
    private static final HudOptions DISABLED = new HudOptions(false, HudAnchor.TOP_LEFT, 1.0F, 1.0F, true, true, false, false, false);
    private static final HudLayout LAYOUT = HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HudState.fade().snap(1.0F);
    }

    private static Label label(int width, int color) {
        return new Label(FormattedCharSequence.EMPTY, width, color);
    }

    /** A shot shaped like {@code F = m·g} would be: three pieces, all present, the symbol one glyph wide. */
    private static Shot shot(SpaceRuleChange change, HudOptions options) {
        int tint = HudPalette.change(change);
        int lifetime = options.reducedMotion() ? RuleFlash.REDUCED_LIFETIME_TICKS : RuleFlash.LIFETIME_TICKS;
        return new Shot(change, change == SpaceRuleChange.AIM ? 2 : 0, tint,
                label(24, HudPalette.TEXT_PRIMARY), label(6, tint), label(30, HudPalette.TEXT_PRIMARY), label(6, tint),
                label(120, HudPalette.TEXT_MUTED), START, lifetime, 17);
    }

    private static float at(Shot shot, float phase) {
        return START + phase * shot.lifetime();
    }

    private static int quads(Shot shot, float now, HudOptions options) {
        QuadCounter sink = new QuadCounter();
        HudBatch batch = new HudBatch().begin(sink, new Matrix4f());
        RuleFlashRenderer.emit(batch, shot, LAYOUT, now, 0.5F, options);
        assertEquals(0, sink.vertices % 4, "a quad was left unfinished");
        assertEquals(batch.quads() * 4, sink.vertices, "the batch miscounted its own quads");
        return batch.quads();
    }

    private static int draws(Shot shot, float now, HudOptions options) {
        HudText.Counting text = new HudText.Counting();
        RuleFlashRenderer.text(text, shot, LAYOUT, now, 0.5F, options);
        return text.draws();
    }

    @Test
    void everyKindStaysInsideTheBudgetWhileLive() {
        for (SpaceRuleChange change : SpaceRuleChange.values()) {
            Shot shot = shot(change, HudOptions.DEFAULTS);
            for (float phase : PHASES) {
                float now = at(shot, phase);
                int quads = quads(shot, now, HudOptions.DEFAULTS);
                assertTrue(quads > 0, change + " at " + phase + " drew nothing");
                assertTrue(quads <= HudBudget.FLASH_QUADS, change + " at " + phase + " emitted " + quads + " quads");
                int draws = draws(shot, now, HudOptions.DEFAULTS);
                assertTrue(draws > 0, change + " at " + phase + " wrote nothing");
                int allowed = change == SpaceRuleChange.ZERO ? HudBudget.FLASH_TEXT_DRAWS : HudBudget.FLASH_TEXT_DRAWS - 1;
                assertTrue(draws <= allowed, change + " at " + phase + " wrote " + draws + " strings");
            }
        }
    }

    @Test
    void onlyTheStruckSymbolWritesAFifthString() {
        Shot zero = shot(SpaceRuleChange.ZERO, HudOptions.DEFAULTS);
        // before, the symbol, after, the zero over it, the caption
        assertEquals(HudBudget.FLASH_TEXT_DRAWS, draws(zero, at(zero, 0.5F), HudOptions.DEFAULTS));
        Shot raise = shot(SpaceRuleChange.RAISE, HudOptions.DEFAULTS);
        assertEquals(HudBudget.FLASH_TEXT_DRAWS - 1, draws(raise, at(raise, 0.5F), HudOptions.DEFAULTS));
    }

    @Test
    void aFinishedFlashDrawsNothing() {
        for (SpaceRuleChange change : SpaceRuleChange.values()) {
            Shot shot = shot(change, HudOptions.DEFAULTS);
            assertEquals(0, quads(shot, at(shot, 1.0F), HudOptions.DEFAULTS), change + " still drew when over");
            assertEquals(0, draws(shot, at(shot, 1.0F), HudOptions.DEFAULTS), change + " still wrote when over");
            assertEquals(0, quads(shot, at(shot, 3.0F), HudOptions.DEFAULTS), change + " drew long after");
        }
    }

    @Test
    void aFadedOutOrDisabledHudDrawsNoFlash() {
        Shot shot = shot(SpaceRuleChange.SURGE, HudOptions.DEFAULTS);
        float now = at(shot, 0.5F);
        assertEquals(0, quads(shot, now, DISABLED));
        assertEquals(0, draws(shot, now, DISABLED));
        HudState.fade().snap(0.0F);
        try {
            assertEquals(0, quads(shot, now, HudOptions.DEFAULTS));
            assertEquals(0, draws(shot, now, HudOptions.DEFAULTS));
        } finally {
            HudState.fade().snap(1.0F);
        }
    }

    @Test
    void reducedMotionStaysInsideTheBudgetAndIsShorter() {
        assertTrue(RuleFlash.REDUCED_LIFETIME_TICKS < RuleFlash.LIFETIME_TICKS);
        for (SpaceRuleChange change : SpaceRuleChange.values()) {
            Shot shot = shot(change, REDUCED);
            assertEquals(RuleFlash.REDUCED_LIFETIME_TICKS, shot.lifetime());
            for (float phase : PHASES) {
                float now = at(shot, phase);
                int quads = quads(shot, now, REDUCED);
                assertTrue(quads > 0 && quads <= HudBudget.FLASH_QUADS, change + " reduced at " + phase + " emitted " + quads);
                assertTrue(draws(shot, now, REDUCED) <= HudBudget.FLASH_TEXT_DRAWS, change + " reduced at " + phase + " wrote too much");
            }
        }
    }

    @Test
    void theShotKnowsItsPhase() {
        Shot shot = shot(SpaceRuleChange.LOCK, HudOptions.DEFAULTS);
        assertEquals(0.0F, shot.phase(START), 1e-6F);
        assertEquals(0.5F, shot.phase(START + shot.lifetime() / 2.0F), 1e-6F);
        assertEquals(1.0F, shot.phase(START + shot.lifetime()), 1e-6F);
        assertEquals(1.0F, shot.phase(START + 10 * shot.lifetime()), 1e-6F);
        assertEquals(0.0F, shot.phase(START - 5), 1e-6F);
    }

    @Test
    void theTickRetiresAFlashAtTheEndOfItsLifeAndANewShotReplacesTheOld() {
        RuleFlash.reset();
        assertNull(RuleFlash.current());
        Shot first = shot(SpaceRuleChange.FLIP, HudOptions.DEFAULTS);
        RuleFlash.show(first);
        RuleFlash.tick(START + first.lifetime() - 1);
        assertEquals(first, RuleFlash.current(), "retired early");
        Shot second = shot(SpaceRuleChange.AIM, HudOptions.DEFAULTS);
        RuleFlash.show(second);
        assertEquals(second, RuleFlash.current(), "a new rule did not replace the running flash");
        RuleFlash.tick(START + second.lifetime());
        assertNull(RuleFlash.current(), "still live at the end of its life");
        RuleFlash.show(first);
        assertNotNull(RuleFlash.current());
        RuleFlash.reset();
        assertNull(RuleFlash.current());
    }
}
