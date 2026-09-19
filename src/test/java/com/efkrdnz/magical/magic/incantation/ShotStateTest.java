package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicSchool;
import org.junit.jupiter.api.Test;

/**
 * The bag every verse writes into. A copy is a snapshot: what a body is stamped with must not move
 * when the shot goes on being written, and the speed multiplier is clamped on every write.
 */
class ShotStateTest {

    @Test
    void aCopyDoesNotFollowTheOriginal() {
        ShotState state = new ShotState();
        state.addDamage(2.5D);
        state.behaviour(Behaviour.SEEKER);
        ShotState copy = state.copy();
        state.addDamage(1.0D);
        state.behaviour(Behaviour.PUNCTURE);
        assertEquals(2.5D, copy.damageAdd(), 1e-9);
        assertTrue(copy.has(Behaviour.SEEKER));
        assertFalse(copy.has(Behaviour.PUNCTURE));
        assertEquals(3.5D, state.damageAdd(), 1e-9);
    }

    @Test
    void speedIsClampedOnEveryWrite() {
        ShotState state = new ShotState();
        for (int i = 0; i < 10; i++) {
            state.multiplySpeed(2.5D);
        }
        assertEquals(ShotState.MAX_SPEED_MULTIPLIER, state.speedMultiplier(), 1e-9);
        state.multiplySpeed(0.0D);
        assertEquals(0.0D, state.speedMultiplier(), 1e-9);
    }

    @Test
    void anExplosionRadiusCannotGoNegative() {
        ShotState state = new ShotState();
        state.addExplosionRadius(1.5D);
        state.addExplosionRadius(-4.0D);
        assertEquals(0.0D, state.explosionRadius(), 1e-9);
    }

    @Test
    void theDefaultsAreNoitasZeroState() {
        ShotState state = new ShotState();
        assertEquals(0, state.beatTicks());
        assertEquals(1.0D, state.speedMultiplier(), 1e-9);
        assertEquals(0.0D, state.spreadDegrees(), 1e-9);
        assertEquals(0.0D, state.patternDegrees(), 1e-9);
        assertFalse(state.nullsDamage());
        assertFalse(state.friendlyFire());
        assertEquals(null, state.school());
        state.setSchool(MagicSchool.FIRE);
        assertEquals(MagicSchool.FIRE, state.school());
    }

    @Test
    void theTypePredicatesAreNoitas() {
        assertTrue(VerseType.PROJECTILE.spawnsBodies());
        assertTrue(VerseType.STATIC.spawnsBodies());
        assertTrue(VerseType.MATERIAL.spawnsBodies());
        assertFalse(VerseType.MODIFIER.spawnsBodies());
        assertTrue(VerseType.CONTROL.spendsUseAlone());
        assertTrue(VerseType.UTILITY.spendsUseAlone());
        assertFalse(VerseType.PROJECTILE.spendsUseAlone());
        assertTrue(VerseType.MULTICAST.imposeScans());
        assertTrue(VerseType.PASSIVE.imposeScans());
        assertFalse(VerseType.UTILITY.imposeScans());
        assertTrue(VerseType.UTILITY.payloadWorthy());
        assertFalse(VerseType.MODIFIER.payloadWorthy());
    }
}
