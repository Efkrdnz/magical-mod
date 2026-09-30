package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.efkrdnz.magical.magic.MagicSchool;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which element each school feeds a storm, and that every element looks like itself. */
class StormElementTest {

    @Test
    void everySchoolFeedsSomething() {
        for (MagicSchool school : MagicSchool.values()) {
            StormElement fed = StormElement.fed(school, "anything", false);
            if (school == MagicSchool.CHAOS) {
                assertEquals(StormElement.DUST, fed, "chaos cleanses");
            } else {
                assertNotEquals(StormElement.DUST, fed, school + " must feed an element, or casting it into a storm cleanses it");
            }
        }
    }

    @Test
    void frostTrumpsTheSchoolButNotChaos() {
        assertEquals(StormElement.FROST, StormElement.fed(MagicSchool.WATER, "rime_snap", true));
        assertEquals(StormElement.DUST, StormElement.fed(MagicSchool.CHAOS, "x", true));
    }

    @Test
    void thePrimordialsFeedTheDisasterTheyAre() {
        assertEquals(StormElement.EMBER, StormElement.fed(MagicSchool.PRIMORDIAL, "caldera", false));
        assertEquals(StormElement.TIDE, StormElement.fed(MagicSchool.PRIMORDIAL, "tsunami", false));
        assertEquals(StormElement.STONE, StormElement.fed(MagicSchool.PRIMORDIAL, "upheaval", false));
        assertEquals(StormElement.STONE, StormElement.fed(MagicSchool.PRIMORDIAL, "skyfall", false));
    }

    @Test
    void theLandFeedsLavaAndWaterOnly() {
        assertEquals(StormElement.EMBER, StormElement.ground(true, true), "lava wins over water");
        assertEquals(StormElement.TIDE, StormElement.ground(false, true));
        assertNull(StormElement.ground(false, false));
    }

    @Test
    void noTwoElementsShareAColour() {
        Set<Integer> colours = new HashSet<>();
        for (StormElement element : StormElement.values()) {
            assertEquals(true, colours.add(element.color()), element + " shares a colour, so the storm cannot say which it is");
        }
    }
}
