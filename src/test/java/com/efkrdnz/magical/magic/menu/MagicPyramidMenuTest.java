package com.efkrdnz.magical.magic.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A cast card in the codex says which skill a key throws. Clicking it used to move the equip
 * cursor and nothing else, so the one skill the player was pointing at was the one they could not
 * read or tune. The card names its skill.
 */
class MagicPyramidMenuTest {

    @Test
    void aCardNamesTheSkillBoundToIt() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockAll(Set.of(MagicContent.GRASP_OF_THE_DEEP.id(), MagicContent.TENDRIL_LASH.id()));
        state.equip(0, MagicContent.GRASP_OF_THE_DEEP.id());
        state.equip(2, MagicContent.TENDRIL_LASH.id());

        MagicSkillDefinition first = MagicPyramidMenu.skillForSlot(state, 0);
        MagicSkillDefinition third = MagicPyramidMenu.skillForSlot(state, 2);
        assertEquals(MagicContent.GRASP_OF_THE_DEEP.id(), first == null ? null : first.id(), "the first card holds the grasp");
        assertEquals(MagicContent.TENDRIL_LASH.id(), third == null ? null : third.id(), "the third card holds the lash");
    }

    @Test
    void anEmptyCardNamesNothing() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockAll(Set.of(MagicContent.GRASP_OF_THE_DEEP.id()));
        state.equip(0, MagicContent.GRASP_OF_THE_DEEP.id());
        assertNull(MagicPyramidMenu.skillForSlot(state, 1), "an empty card selects nothing, so the cursor just moves");
    }

    @Test
    void theFirstPressOnACardOnlyMovesTheCursor() {
        assertFalse(MagicPyramidMenu.cardPressOpensSkill(2, 0),
                "pointing at a key must not throw the pyramid and the detail panel at whatever is already in it");
    }

    @Test
    void aSecondPressOnTheSameCardOpensItsSkill() {
        assertTrue(MagicPyramidMenu.cardPressOpensSkill(2, 2),
                "the card the cursor is already on is the one a press reads");
    }

    @Test
    void aCardOutsideTheLoadoutNamesNothing() {
        PlayerMagicState state = new PlayerMagicState();
        assertNull(MagicPyramidMenu.skillForSlot(state, -1), "a slot below the first");
        assertNull(MagicPyramidMenu.skillForSlot(state, MagicContent.LOADOUT_SIZE), "a slot past the last");
    }
}
