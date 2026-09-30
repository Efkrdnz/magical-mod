package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.efkrdnz.magical.magic.primordial.CycloneFeed.State;
import org.junit.jupiter.api.Test;

/** Feeding a storm: the same element deepens it, another replaces it, chaos cleanses it, the land only colours a calm one. */
class CycloneFeedTest {

    @Test
    void theSameElementDeepensToThree() {
        State s = State.CALM;
        s = CycloneFeed.swallow(s, StormElement.EMBER);
        assertEquals(new State(StormElement.EMBER, 1), s);
        s = CycloneFeed.swallow(s, StormElement.EMBER);
        s = CycloneFeed.swallow(s, StormElement.EMBER);
        s = CycloneFeed.swallow(s, StormElement.EMBER);
        assertEquals(new State(StormElement.EMBER, CycloneFeed.MAX_STACKS), s);
    }

    @Test
    void anotherElementReplacesItAtOne() {
        State s = new State(StormElement.EMBER, 3);
        assertEquals(new State(StormElement.TIDE, 1), CycloneFeed.swallow(s, StormElement.TIDE));
    }

    @Test
    void chaosCleanses() {
        assertSame(State.CALM, CycloneFeed.swallow(new State(StormElement.MAELSTROM, 2), StormElement.DUST));
    }

    @Test
    void theLandColoursOnlyACalmStormAndNeverDeepens() {
        assertEquals(new State(StormElement.EMBER, 1), CycloneFeed.land(State.CALM, StormElement.EMBER));
        State fed = new State(StormElement.MAELSTROM, 2);
        assertSame(fed, CycloneFeed.land(fed, StormElement.TIDE));
        State ember = new State(StormElement.EMBER, 1);
        assertSame(ember, CycloneFeed.land(ember, StormElement.EMBER));
        assertSame(State.CALM, CycloneFeed.land(State.CALM, null));
    }

    @Test
    void potencyAndWidthGrowWithStacks() {
        assertEquals(1.0F, CycloneFeed.potency(0), 1.0E-6F);
        assertEquals(1.0F, CycloneFeed.potency(1), 1.0E-6F);
        assertEquals(1.5F, CycloneFeed.potency(2), 1.0E-6F);
        assertEquals(2.0F, CycloneFeed.potency(3), 1.0E-6F);
        assertEquals(2.0F, CycloneFeed.potency(9), 1.0E-6F);
        assertEquals(1.0F, CycloneFeed.widen(0), 1.0E-6F);
        assertEquals(1.3F, CycloneFeed.widen(3), 1.0E-6F);
    }
}
