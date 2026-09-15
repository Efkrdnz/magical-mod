package com.efkrdnz.magical.client.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.efkrdnz.magical.client.screen.CodexPassiveTooltip.Body;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Which body a passive's tooltip prints, and how much room that body needs.
 *
 * <p>A pact half is registered as a normal passive so it can carry a clock, so asking the
 * definition what it is gives the wrong answer twice over: a price would print the toggle hint and
 * a level it does not have, and a boon would promise a checkbox that {@code togglePassive} refuses.
 * Neither can be switched off, so neither gets the passive body. A price is not a curse either -
 * it has no dispel cost, and printing one would advertise an escape hatch that is not there.
 */
class CodexPassiveTooltipTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void aPactPriceReadsAsNeitherAPassiveNorACurse() {
        assertEquals(Body.PACT, CodexPassiveTooltip.body(MagicPassiveContent.GLASS_BONES, 1800));
    }

    @Test
    void aPactBoonReadsAsAPactToo() {
        // It is the half the player wanted, but it is just as locked in as the half they owed.
        assertEquals(Body.PACT, CodexPassiveTooltip.body(MagicPassiveContent.CRIMSON_EDGE, 1200));
    }

    @Test
    void anOrdinaryPassiveStillReadsAsOne() {
        assertEquals(Body.PASSIVE, CodexPassiveTooltip.body(MagicPassiveContent.MANA_SKIN, 0));
    }

    @Test
    void aLastingCurseStillReadsAsOne() {
        assertEquals(Body.CURSE, CodexPassiveTooltip.body(MagicPassiveContent.MANA_LEAK_CURSE, 0));
    }

    @Test
    void aPactHalfWhoseClockHasRunOutIsNoLongerAPactHalf() {
        // The clock is the whole of what makes it one. Once it is gone the passive is either
        // already removed or an ordinary one, and it reads as whatever it actually is.
        assertEquals(Body.PASSIVE, CodexPassiveTooltip.body(MagicPassiveContent.GLASS_BONES, 0));
    }

    @Test
    void aPactBodyLeavesRoomForItsOneLine() {
        assertEquals(CodexPassiveTooltip.extraHeight(Body.PASSIVE, false),
                CodexPassiveTooltip.extraHeight(Body.PACT, false),
                "a pact prints one line under the description, the same as a passive");
    }

    @Test
    void aCurseBodyLeavesRoomForMoreThanAPassiveBody() {
        // Two lines rather than one, so a tooltip sized for a passive would print over its frame.
        assertNotEquals(CodexPassiveTooltip.extraHeight(Body.PASSIVE, false),
                CodexPassiveTooltip.extraHeight(Body.CURSE, false));
    }

    @Test
    void aSinCurseLeavesRoomForTheLineNamingThePassiveThatLiftsIt() {
        assertEquals(CodexPassiveTooltip.extraHeight(Body.CURSE, false) + 14,
                CodexPassiveTooltip.extraHeight(Body.CURSE, true),
                "a linked sin curse prints one line more than a plain one");
    }

    @Test
    void onlyACurseCaresWhetherASinIsLinked() {
        // The flag comes from linkedSinPassiveForCurse, which answers for any id at all. A pact
        // half that happened to be linked must not grow a gap where a dispel block would go.
        assertEquals(CodexPassiveTooltip.extraHeight(Body.PACT, false),
                CodexPassiveTooltip.extraHeight(Body.PACT, true));
        assertEquals(CodexPassiveTooltip.extraHeight(Body.PASSIVE, false),
                CodexPassiveTooltip.extraHeight(Body.PASSIVE, true));
    }
}
