package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.mind.Consensus;
import com.efkrdnz.magical.network.BeliefSyncPayload;
import com.efkrdnz.magical.network.IllusionEndPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientMindHardeningTest {
    private static BeliefSyncPayload real(int scene, Integer... elements) {
        return new BeliefSyncPayload(scene, List.of(), List.of(elements));
    }

    @BeforeEach
    @AfterEach
    void forget() {
        ClientMind.clear();
    }

    @Test
    void anElementThatBecomesRealWearsAFullRimThatFadesOverTheHardening() {
        ClientMind.record(real(5), 100L);
        ClientMind.record(real(5, 2), 110L);
        assertTrue(ClientMind.manifested(5, 2));
        assertFalse(ClientMind.manifested(5, 1));
        assertEquals(1.0F, ClientMind.hardeningAt(5, 2, 0.0F, 110L), 1.0E-6F);
        assertEquals(0.5F, ClientMind.hardeningAt(5, 2, 0.0F, 110L + Consensus.HARDEN_TICKS / 2), 1.0E-6F);
        assertEquals(0.5F - 0.5F / Consensus.HARDEN_TICKS,
                ClientMind.hardeningAt(5, 2, 0.5F, 110L + Consensus.HARDEN_TICKS / 2), 1.0E-6F);
        assertEquals(0.0F, ClientMind.hardeningAt(5, 2, 0.0F, 110L + Consensus.HARDEN_TICKS), 1.0E-6F);
        assertEquals(0.0F, ClientMind.hardeningAt(5, 1, 0.0F, 110L), 1.0E-6F, "an element that is not real has no rim");
    }

    @Test
    void aSceneThatArrivesAlreadyRealWearsNoRim() {
        ClientMind.record(real(5, 2), 1000L);
        assertTrue(ClientMind.manifested(5, 2), "it is real all the same");
        assertEquals(0.0F, ClientMind.hardeningAt(5, 2, 0.0F, 1000L), 1.0E-6F,
                "a client that starts watching a lie that hardened long ago did not see it harden");
    }

    @Test
    void anElementThatStaysRealIsNotStampedAgain() {
        ClientMind.record(real(5), 100L);
        ClientMind.record(real(5, 2), 105L);
        ClientMind.record(real(5, 2), 110L);
        assertEquals(1.0F - 5.0F / Consensus.HARDEN_TICKS, ClientMind.hardeningAt(5, 2, 0.0F, 110L), 1.0E-6F);
    }

    @Test
    void anElementThatRevertsLosesItsRimAndHardensAfreshIfItReturns() {
        ClientMind.record(real(5), 100L);
        ClientMind.record(real(5, 2), 105L);
        ClientMind.record(real(5), 107L);
        assertFalse(ClientMind.manifested(5, 2));
        assertEquals(0.0F, ClientMind.hardeningAt(5, 2, 0.0F, 107L), 1.0E-6F);
        ClientMind.record(real(5, 2), 200L);
        assertEquals(1.0F, ClientMind.hardeningAt(5, 2, 0.0F, 200L), 1.0E-6F);
    }

    @Test
    void anEndedSceneForgetsWhatWasReal() {
        ClientMind.record(real(5), 100L);
        ClientMind.record(real(5, 2), 105L);
        ClientMind.record(real(6), 100L);
        ClientMind.record(real(6, 2), 105L);
        ClientMind.accept(new IllusionEndPayload(5));
        assertFalse(ClientMind.manifested(5, 2));
        assertEquals(0.0F, ClientMind.hardeningAt(5, 2, 0.0F, 105L), 1.0E-6F);
        assertTrue(ClientMind.manifested(6, 2), "another scene keeps its own");
        assertEquals(1.0F, ClientMind.hardeningAt(6, 2, 0.0F, 105L), 1.0E-6F);
    }

    @Test
    void theKeyKeepsSceneAndElementApart() {
        assertNotEquals(ClientMind.key(1, 0), ClientMind.key(0, 1));
        assertNotEquals(ClientMind.key(1, -1), ClientMind.key(0, -1));
        assertEquals(7, (int) (ClientMind.key(7, -3) >> 32));
    }
}
