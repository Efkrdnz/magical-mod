package com.efkrdnz.magical.magic.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveSceneClockTest {
    @Test
    void aSceneOutlivesItsClockOnlyWhileSomethingInItIsReal() {
        assertFalse(LiveScene.over(LiveScene.LIFE_TICKS - 1, false));
        assertTrue(LiveScene.over(LiveScene.LIFE_TICKS, false));
        assertFalse(LiveScene.over(LiveScene.LIFE_TICKS * 5L, true));
    }
}
