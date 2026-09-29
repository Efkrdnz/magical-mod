package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DreamscapeNbtTest {
    @Test
    void aDreamscapeComesBackAsItWent() {
        Dreamscape scape = new Dreamscape(5);
        scape.setArrival(new Offset(3, 2, -4), 45.0F);
        scape.markBlock(new Offset(-6, 0, 7));
        Dreamscape back = DreamscapeNbt.load(DreamscapeNbt.save(scape));
        assertEquals(5, back.plot());
        assertEquals(new Offset(3, 2, -4), back.arrival());
        assertEquals(45.0F, back.arrivalYaw());
        assertEquals(new Offset(-6, 0, 7), back.flaw().block());
    }

    @Test
    void anArrivalOutsideThePlotIsPutBackOnItsFloor() {
        Dreamscape scape = new Dreamscape(2);
        scape.setArrival(new Offset(DreamRules.PLOT_HALF + 40, 0, 0), 0.0F);
        assertEquals(new Offset(0, 0, 0), DreamscapeNbt.load(DreamscapeNbt.save(scape)).arrival());
        scape.setArrival(new Offset(0, DreamRules.PLOT_ABOVE + 10, 0), 0.0F);
        assertEquals(new Offset(0, 0, 0), DreamscapeNbt.load(DreamscapeNbt.save(scape)).arrival());
    }

    @Test
    void aFlawOutsideThePlotIsDropped() {
        Dreamscape scape = new Dreamscape(2);
        scape.markBlock(new Offset(0, 0, -(DreamRules.PLOT_HALF + 1)));
        assertNull(DreamscapeNbt.load(DreamscapeNbt.save(scape)).flaw());
        scape.markBlock(new Offset(0, -(DreamRules.PLOT_BELOW + 5), 0));
        assertNull(DreamscapeNbt.load(DreamscapeNbt.save(scape)).flaw());
    }

    @Test
    void aFlawOnThePlotEdgeIsKept() {
        Dreamscape scape = new Dreamscape(0);
        scape.markBlock(new Offset(DreamRules.PLOT_HALF, 0, -DreamRules.PLOT_HALF));
        assertEquals(new Offset(DreamRules.PLOT_HALF, 0, -DreamRules.PLOT_HALF), DreamscapeNbt.load(DreamscapeNbt.save(scape)).flaw().block());
    }

    @Test
    void aSavedTagWithNoArrivalArrivesOnTheFloor() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("plot", 1);
        assertEquals(new Offset(0, 0, 0), DreamscapeNbt.load(tag).arrival());
    }
}
