package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.network.BeliefSyncPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeliefSightTest {
    @Test
    void doubtIsADashedLilacRingThatBrightensAndConvictionIsSolidGold() {
        assertNull(BeliefSight.mark(0.0F));
        assertNull(BeliefSight.mark(-1.0F));
        BeliefSight.Mark doubt = BeliefSight.mark(0.2F);
        assertEquals(GlyphKind.DASHED_RING, doubt.kind());
        assertEquals(BeliefSight.DOUBT, doubt.rgb());
        assertEquals(0.35F + 0.6F * 0.2F, doubt.opacity(), 1.0E-6F);
        BeliefSight.Mark sure = BeliefSight.mark(0.5F);
        assertEquals(GlyphKind.SOLID_RING, sure.kind());
        assertEquals(BeliefSight.CONVINCED, sure.rgb());
    }

    @Test
    void aViewerReadsAsTheirStrongestBelief() {
        List<BeliefSyncPayload.Entry> rows = List.of(
                new BeliefSyncPayload.Entry(1, 0, (byte) 20, false),
                new BeliefSyncPayload.Entry(1, 1, (byte) 70, false),
                new BeliefSyncPayload.Entry(2, 0, (byte) 0, true));
        assertEquals(0.70F, BeliefSight.strongest(rows, 1), 1.0E-6F);
        assertEquals(-1.0F, BeliefSight.strongest(rows, 2), 1.0E-6F, "seen through everything");
        assertEquals(0.0F, BeliefSight.strongest(rows, 3), 1.0E-6F);
    }
}
