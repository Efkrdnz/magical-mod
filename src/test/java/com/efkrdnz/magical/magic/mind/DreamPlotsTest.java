package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DreamPlotsTest {
    @Test
    void aDreamscapeSurvivesTheDiskWithItsFlaw() {
        Dreamscape block = new Dreamscape(4);
        block.setArrival(new Offset(2, 0, -1), 45.0F);
        block.markBlock(new Offset(3, 1, 0));
        Dreamscape back = DreamscapeNbt.load(DreamscapeNbt.save(block));
        assertEquals(4, back.plot());
        assertEquals(new Offset(2, 0, -1), back.arrival());
        assertEquals(45.0F, back.arrivalYaw());
        assertEquals(new Offset(3, 1, 0), back.flaw().block());

        Dreamscape figment = new Dreamscape(5);
        UUID cow = UUID.randomUUID();
        figment.markFigment(cow);
        assertEquals(cow, DreamscapeNbt.load(DreamscapeNbt.save(figment)).flaw().figment());
        assertNull(DreamscapeNbt.load(DreamscapeNbt.save(new Dreamscape(6))).flaw());
    }

    @Test
    void everyOwnerHasTheirOwnPlotAndKeepsItAcrossALoad() {
        DreamPlots plots = new DreamPlots();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(0, plots.claim(a).plot());
        assertEquals(1, plots.claim(b).plot());
        DreamPlots loaded = DreamPlots.load(plots.save(new CompoundTag(), null), null);
        assertEquals(0, loaded.get(a).plot());
        assertEquals(1, loaded.get(b).plot());
        assertEquals(b, loaded.ownerOf(1));
        assertEquals(2, loaded.claim(UUID.randomUUID()).plot());
    }
}
