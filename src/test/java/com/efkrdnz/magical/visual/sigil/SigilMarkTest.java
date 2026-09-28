package com.efkrdnz.magical.visual.sigil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.efkrdnz.magical.magic.visual.sigil.Sigil;
import com.efkrdnz.magical.magic.visual.sigil.SigilInk;
import com.efkrdnz.magical.magic.visual.sigil.SigilMark;
import com.efkrdnz.magical.magic.visual.sigil.SigilMotion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SigilMarkTest {

    @Test
    void aMarkDefaultsToVioletRisingAtSizeOne() {
        SigilMark mark = SigilMark.of(Sigil.EYE);
        assertEquals(List.of(Sigil.EYE), mark.sigils());
        assertEquals(List.of(SigilInk.VIOLET), mark.inks());
        assertEquals(SigilMotion.RISE, mark.motion());
        assertEquals(1.0F, mark.scale());
    }

    @Test
    void theIthSpawnTakesTheIthSymbolAndTheIthInkEachCycling() {
        SigilMark mark = SigilMark.of(Sigil.EYE, Sigil.KEY, Sigil.CROWN).inks(SigilInk.GOLD, SigilInk.NIGHT);
        Sigil[] sigils = {Sigil.EYE, Sigil.KEY, Sigil.CROWN, Sigil.EYE, Sigil.KEY, Sigil.CROWN};
        SigilInk[] inks = {SigilInk.GOLD, SigilInk.NIGHT, SigilInk.GOLD, SigilInk.NIGHT, SigilInk.GOLD, SigilInk.NIGHT};
        for (int i = 0; i < sigils.length; i++) {
            assertEquals(sigils[i], mark.sigilAt(i), "sigil " + i);
            assertEquals(inks[i], mark.inkAt(i), "ink " + i);
        }
        assertEquals(Sigil.CROWN, mark.sigilAt(-1));
    }

    @Test
    void aMarkIsItsOwnAndItsWithersLeaveItAlone() {
        List<Sigil> mine = new ArrayList<>(List.of(Sigil.EYE));
        SigilMark mark = SigilMark.of(mine);
        mine.add(Sigil.KEY);
        assertEquals(List.of(Sigil.EYE), mark.sigils());
        assertThrows(UnsupportedOperationException.class, () -> mark.sigils().add(Sigil.KEY));
        SigilMark changed = mark.ink(SigilInk.EMBER).motion(SigilMotion.FALL).scale(2.0F);
        assertEquals(List.of(SigilInk.VIOLET), mark.inks());
        assertEquals(SigilMotion.RISE, mark.motion());
        assertEquals(List.of(SigilInk.EMBER), changed.inks());
        assertEquals(SigilMotion.FALL, changed.motion());
        assertEquals(2.0F, changed.scale());
    }

    @Test
    void aMarkRefusesToBeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> SigilMark.of(List.of()));
        assertThrows(IllegalArgumentException.class, () -> SigilMark.of(Sigil.EYE).inks(new SigilInk[0]));
    }
}
