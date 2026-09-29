package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.network.BeliefSyncPayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MindSyncTest {
    @Test
    void theManifestedListIsCutToWhatTheWireTakes() {
        List<Integer> real = new ArrayList<>();
        for (int i = 0; i < BeliefSyncPayload.MAX_MANIFESTED + 10; i++) {
            real.add(i);
        }
        List<Integer> sent = MindSync.forTheWire(real);
        assertEquals(BeliefSyncPayload.MAX_MANIFESTED, sent.size());
        assertEquals(real.subList(0, BeliefSyncPayload.MAX_MANIFESTED), sent, "the lowest indices go first");
        assertEquals(List.of(0, 3), MindSync.forTheWire(List.of(0, 3)), "a short list goes whole");
    }
}
