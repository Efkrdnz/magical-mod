package com.efkrdnz.magical.entity.verse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The core review's first hand-off: a body applies its prototype's own effect (Ember's BURN, Arc
 * Bolt's SHOCK, a ring's pulse) as well as what a Wreath, Uplift or Displace stamped on it, the
 * body's own first, each once, in the order they were written.
 */
class VerseHitEffectsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ProjectilePlan body(VersePrototype prototype, HitEffect... stamped) {
        ShotState state = new ShotState();
        for (HitEffect effect : stamped) {
            state.hitEffect(effect);
        }
        return new ProjectilePlan(prototype, prototype.id(), state, PayloadKind.NONE, 0, null);
    }

    @Test
    void theBodysOwnEffectComesFirstAndTheStampedOnesFollow() {
        assertEquals(List.of(HitEffect.BURN, HitEffect.FREEZE, HitEffect.UPLIFT),
                VerseHitEffects.effectsOf(body(VersePrototypes.EMBER, HitEffect.FREEZE, HitEffect.UPLIFT)));
        assertEquals(List.of(HitEffect.SHOCK), VerseHitEffects.effectsOf(body(VersePrototypes.ARC)));
    }

    @Test
    void anEffectStampedTwiceOrAlreadyTheBodysOwnIsAppliedOnce() {
        assertEquals(List.of(HitEffect.BURN, HitEffect.WITHER),
                VerseHitEffects.effectsOf(body(VersePrototypes.EMBER, HitEffect.BURN, HitEffect.WITHER, HitEffect.WITHER)));
    }

    @Test
    void aBodyWithNothingWrittenOnItHasNoEffect() {
        assertTrue(VerseHitEffects.effectsOf(body(VersePrototypes.NEEDLE)).isEmpty());
        assertTrue(VerseHitEffects.pulseEffectsOf(body(VersePrototypes.NEEDLE)).isEmpty());
    }

    @Test
    void aStaticPulsesItsOwnEffectThenTheStampedOnes() {
        assertEquals(List.of(HitEffect.FREEZE, HitEffect.SHOCK),
                VerseHitEffects.pulseEffectsOf(body(VersePrototypes.RING_RIME, HitEffect.SHOCK)));
        assertEquals(List.of(HitEffect.UPLIFT), VerseHitEffects.pulseEffectsOf(body(VersePrototypes.RING_UPLIFT)));
    }
}
