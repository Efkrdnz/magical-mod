package com.efkrdnz.magical.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicTuningStat;
import com.efkrdnz.magical.magic.menu.MagicPyramidMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Every codex control is a numeric button id in one shared space, and each range is sized by a
 * content count that grows as the mod grows. Nothing in the type system stops one range from
 * running into the next; when that happens a click silently does the wrong thing, or nothing.
 * This pins the ranges to the real counts so growth fails the build instead of the GUI.
 */
class CodexButtonRangeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** One past the highest id any codex control may use. Not a runtime limit - a budget. */
    private static final int BUTTON_ID_CEILING = 4000;

    private record Band(String name, int start, int size) {
        int endExclusive() {
            return start + size;
        }
    }

    private static List<Band> bands() {
        List<Band> bands = new ArrayList<>();
        bands.add(new Band("skill select", MagicPyramidMenu.BUTTON_SKILL_BASE, MagicContent.orderedSkillIds().size()));
        bands.add(new Band("loadout slot", MagicPyramidMenu.BUTTON_SLOT_BASE, MagicContent.LOADOUT_SIZE));
        bands.add(new Band("loadout select", MagicPyramidMenu.BUTTON_LOADOUT_SELECT_BASE, MagicContent.MAX_LOADOUTS));
        bands.add(new Band("loadout bind", MagicPyramidMenu.BUTTON_LOADOUT_BIND_BASE, MagicContent.LOADOUT_SIZE));
        bands.add(new Band("loadout clear", MagicPyramidMenu.BUTTON_LOADOUT_CLEAR_BASE, MagicContent.LOADOUT_SIZE));
        bands.add(new Band("class select", MagicPyramidMenu.BUTTON_CLASS_SELECT_BASE, MagicalClasses.all().size()));
        bands.add(new Band("class evolve", MagicPyramidMenu.BUTTON_EVOLVE_CLASS_BASE, MagicalClasses.all().size()));
        bands.add(new Band("skill tuning", MagicPyramidMenu.BUTTON_TUNE_BASE, MagicTuningStat.values().length * 10));
        bands.add(new Band("passive toggle", MagicPyramidMenu.BUTTON_PASSIVE_TOGGLE_BASE, MagicPassiveContent.normalPassives().size()));
        bands.add(new Band("curse dispel", MagicPyramidMenu.BUTTON_CURSE_DISPEL_BASE, MagicPassiveContent.curses().size()));
        // A zero-width band past the top of the id space, so the highest real band is bounded by
        // something too. Without it the last band in the sort order is checked against nothing,
        // which is exactly the band most likely to grow.
        bands.add(new Band("ceiling", BUTTON_ID_CEILING, 0));
        bands.sort((a, b) -> Integer.compare(a.start(), b.start()));
        return bands;
    }

    @Test
    void noButtonRangeRunsIntoTheNext() {
        List<Band> bands = bands();
        for (int i = 0; i < bands.size() - 1; i++) {
            Band band = bands.get(i);
            Band next = bands.get(i + 1);
            assertTrue(band.endExclusive() <= next.start(),
                    band.name() + " occupies " + band.start() + ".." + (band.endExclusive() - 1)
                            + " and collides with " + next.name() + " starting at " + next.start()
                            + "; give it a larger gap or move the next base up");
        }
    }

    /** The two single-id controls that sit inside the aegis tuning span rely on test ordering. */
    @Test
    void singleIdControlsStayClearOfTheirNeighbours() {
        assertTrue(MagicPyramidMenu.BUTTON_OPEN_CLASS_SELECT >= MagicPyramidMenu.BUTTON_AEGIS_TUNE_BASE,
                "the class-select id is documented as living inside the aegis span");
        assertTrue(MagicPyramidMenu.BUTTON_OPEN_CLASS_TREE != MagicPyramidMenu.BUTTON_OPEN_CLASS_SELECT,
                "the two class buttons must not share an id");
    }

    /**
     * The passives tab sends BASE + index into normalPassives and the menu reads it back out of the
     * same list. If the two ever disagree, a click toggles somebody else's passive.
     */
    @Test
    void everyPassiveIndexRoundTripsThroughItsButtonId() {
        List<com.efkrdnz.magical.magic.MagicPassiveDefinition> all = MagicPassiveContent.normalPassives();
        for (int index = 0; index < all.size(); index++) {
            int id = MagicPyramidMenu.BUTTON_PASSIVE_TOGGLE_BASE + index;
            assertTrue(id >= MagicPyramidMenu.BUTTON_PASSIVE_TOGGLE_BASE
                            && id < MagicPyramidMenu.BUTTON_PASSIVE_TOGGLE_BASE + all.size(),
                    all.get(index).id() + " at index " + index + " falls outside the toggle range");
            assertTrue(all.get(id - MagicPyramidMenu.BUTTON_PASSIVE_TOGGLE_BASE).id().equals(all.get(index).id()),
                    "index " + index + " does not round-trip");
        }
    }
}
