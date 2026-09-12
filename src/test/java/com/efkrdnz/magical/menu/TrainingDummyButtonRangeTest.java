package com.efkrdnz.magical.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.screen.TrainingDummyScreen;
import com.efkrdnz.magical.entity.TrainingDummyEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.menu.TrainingDummyMenu;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The dummy's whole client-to-server channel is one int, carved into ranges sized by content
 * counts that grow as the mod grows. Nothing in the type system stops the skill range running into
 * the passive range; when it does, clicking a skill silently toggles a passive instead. This pins
 * the ranges to the real counts so growth fails the build rather than the GUI.
 */
class TrainingDummyButtonRangeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everySkillFitsBeneathThePassiveRange() {
        int skills = TrainingDummyMenu.skillOptions().size();
        assertTrue(skills > 0, "the skill list is empty, so the dummy can cast nothing");
        assertTrue(TrainingDummyMenu.BUTTON_SKILL_BASE + skills <= TrainingDummyMenu.BUTTON_PASSIVE_BASE,
                "skill ids run into the passive range: " + skills + " skills from "
                        + TrainingDummyMenu.BUTTON_SKILL_BASE);
    }

    @Test
    void everyPassiveFitsBeneathTheDelayRange() {
        int passives = TrainingDummyMenu.passiveOptions().size();
        assertTrue(passives > 0, "the passive list is empty");
        assertTrue(TrainingDummyMenu.BUTTON_PASSIVE_BASE + passives <= TrainingDummyMenu.BUTTON_DELAY_BASE,
                "passive ids run into the delay range: " + passives + " passives");
    }

    @Test
    void everyDelayValueFitsBeneathTheActionRange() {
        assertTrue(TrainingDummyMenu.BUTTON_DELAY_BASE + TrainingDummyEntity.MAX_DELAY
                        < TrainingDummyMenu.BUTTON_CLEAR_SKILLS,
                "the longest delay encodes as an action button");
        assertTrue(TrainingDummyEntity.MIN_DELAY > 0
                        && TrainingDummyEntity.MIN_DELAY < TrainingDummyEntity.MAX_DELAY,
                "the delay bounds are not an interval");
    }

    @Test
    void theActionButtonsAreDistinct() {
        List<Integer> actions = List.of(
                TrainingDummyMenu.BUTTON_CLEAR_SKILLS,
                TrainingDummyMenu.BUTTON_RESET_METER,
                TrainingDummyMenu.BUTTON_TOGGLE_PARRY,
                TrainingDummyMenu.BUTTON_TOGGLE_QTE,
                TrainingDummyMenu.BUTTON_REMOVE);
        assertEquals(actions.size(), Set.copyOf(actions).size(), "two actions share a button id");
        for (int id : actions) {
            assertTrue(id >= TrainingDummyMenu.BUTTON_CLEAR_SKILLS,
                    "an action sits below its own range and would be read as a delay");
        }
    }

    /**
     * The user asked for every skill in the game "including the subskills of parent skills", and
     * {@code MagicContent.allSkills()} - the obvious list, and the one every other screen uses -
     * filters exactly those out. This is the assertion that keeps the right list in use.
     */
    @Test
    void theSkillListCarriesSubSkillsThatAllSkillsLeavesOut() {
        Set<ResourceLocation> offered = new HashSet<>(TrainingDummyMenu.skillOptions());
        Set<ResourceLocation> ordinary = new HashSet<>();
        MagicContent.allSkills().forEach(skill -> ordinary.add(skill.id()));
        assertFalse(MagicContent.SUB_SKILLS.isEmpty(), "there are no sub-skills to check against");
        for (ResourceLocation sub : MagicContent.SUB_SKILLS) {
            assertTrue(offered.contains(sub), "the dummy cannot be given the sub-skill " + sub);
            assertFalse(ordinary.contains(sub), sub + " is no longer a sub-skill, so this test proves nothing");
        }
    }

    /**
     * The list is ten rows of sixteen pixels sitting above a column of controls in a fixed-height
     * panel. Growing the list or moving a control is a one-line edit with no compiler consequence
     * and a GUI that silently overlaps, so the bands are checked rather than eyeballed.
     */
    @Test
    void noControlBandOverlapsAnother() {
        int listBottom = TrainingDummyScreen.LIST_TOP + TrainingDummyScreen.ROWS * TrainingDummyScreen.ROW_HEIGHT;
        assertTrue(TrainingDummyScreen.TAB_TOP < TrainingDummyScreen.LIST_TOP,
                "the tabs sit on top of the list");
        assertTrue(listBottom <= TrainingDummyScreen.HEIGHT,
                "the list runs off the bottom of the panel: ends at " + listBottom);
        int[] bands = {
                TrainingDummyScreen.SLIDER_TOP,
                TrainingDummyScreen.CLEAR_TOP,
                TrainingDummyScreen.PARRY_TOP,
                TrainingDummyScreen.QTE_TOP,
                TrainingDummyScreen.RESET_TOP,
                TrainingDummyScreen.REMOVE_TOP};
        for (int i = 1; i < bands.length; i++) {
            assertTrue(bands[i] >= bands[i - 1] + TrainingDummyScreen.BUTTON_HEIGHT,
                    "control band " + i + " starts at " + bands[i] + ", inside the one above it");
        }
        assertTrue(bands[bands.length - 1] + TrainingDummyScreen.BUTTON_HEIGHT <= TrainingDummyScreen.HEIGHT,
                "the last control runs off the bottom of the panel");
    }

    /**
     * Two counter windows can never overlap, at any delay the slider can reach.
     *
     * <p>{@code MagicCounterService} holds one prompt per player and a new offer overwrites it, so
     * an overlap is not a second chance - it silently eats the first window and lands that spell
     * unprompted, which is the setting failing while still looking switched on.
     */
    @Test
    void noTwoCounterWindowsCanOverlap() {
        for (int delay = TrainingDummyEntity.MIN_DELAY; delay <= TrainingDummyEntity.MAX_DELAY; delay++) {
            int window = TrainingDummyEntity.qteWindow(delay);
            assertTrue(window >= 1, "delay " + delay + " leaves no window at all");
            assertTrue(window < delay,
                    "delay " + delay + " opens a " + window + "-tick window, which outlives the beat");
            assertTrue(window <= TrainingDummyEntity.QTE_WINDOW,
                    "delay " + delay + " opens a window longer than the ceiling");
        }
    }

    @Test
    void thePassiveListLeavesOutCurses() {
        for (MagicPassiveDefinition passive : TrainingDummyMenu.passiveOptions()) {
            assertFalse(passive.curse(), passive.id() + " is a curse and should not be offered");
        }
    }
}
