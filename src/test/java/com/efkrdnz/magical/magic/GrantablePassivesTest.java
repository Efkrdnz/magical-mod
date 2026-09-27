package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a command may hand out. The two halves of a Blood Sacrifice pact are registered as normal
 * passives so they can carry a clock, and that made them look like anything else to
 * {@code /magical passive unlockall}: it granted all thirty-two for good, the prices landed in the
 * codex passives column with a checkbox, and none of them ever ran out.
 */
class GrantablePassivesTest {

    @Test
    void noHalfOfAPactCanBeGrantedByCommand() {
        for (MagicPassiveDefinition definition : MagicPassiveContent.grantablePassives()) {
            assertFalse(MagicPassiveContent.isRitual(definition.id()),
                    definition.id() + " is half of a pact, and only a pact grants it");
        }
    }

    @Test
    void everyOtherNormalPassiveStillCan() {
        List<MagicPassiveDefinition> expected = MagicPassiveContent.normalPassives().stream()
                .filter(definition -> !MagicPassiveContent.isRitual(definition.id()))
                .toList();
        assertEquals(expected, MagicPassiveContent.grantablePassives());
    }

    @Test
    void theCommandsDoNotSuggestOne() {
        List<String> suggested = MagicPassiveContent.normalCommandIds();
        for (var id : MagicPassiveContent.ritualBoons()) {
            assertFalse(suggested.contains(id.getPath()), id + " is a boon");
        }
        for (var id : MagicPassiveContent.ritualPrices()) {
            assertFalse(suggested.contains(id.getPath()), id + " is a price");
        }
    }
}
