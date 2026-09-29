package com.efkrdnz.magical.client.mind;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LexiconShelvesTest {
    private static final LexiconShelves.Shelf BUILDING = new LexiconShelves.Shelf("minecraft:building_blocks",
            List.of("block:minecraft:stone", "block:minecraft:dirt", "block:minecraft:stone"));
    private static final LexiconShelves.Shelf EGGS = new LexiconShelves.Shelf("minecraft:spawn_eggs",
            List.of("creature:minecraft:cow", "creature:minecraft:pig"));
    private static final LexiconShelves.Shelf MODDED = new LexiconShelves.Shelf("othermod:gadgets",
            List.of("block:othermod:widget"));

    @Test
    void onlyLearnedLiesAreShelvedInTheirTabsOrder() {
        var shelves = LexiconShelves.shelve(List.of(BUILDING, EGGS, MODDED),
                Set.of("block:minecraft:stone", "creature:minecraft:pig"));
        assertEquals(List.of("minecraft:building_blocks", "minecraft:spawn_eggs"), shelves.stream().map(LexiconShelves.Shelf::id).toList());
        assertEquals(List.of("block:minecraft:stone"), shelves.get(0).keys(), "a tab kept a duplicate or an unlearned lie");
        assertEquals(List.of("creature:minecraft:pig"), shelves.get(1).keys());
    }

    @Test
    void aModdedTabAppearsOnceSomethingInItIsLearned() {
        var shelves = LexiconShelves.shelve(List.of(BUILDING, MODDED), Set.of("block:othermod:widget"));
        assertEquals(List.of("othermod:gadgets"), shelves.stream().map(LexiconShelves.Shelf::id).toList());
    }

    @Test
    void whatNoTabHoldsGoesOnTheOtherShelf() {
        var shelves = LexiconShelves.shelve(List.of(BUILDING),
                Set.of("block:minecraft:water", "block:minecraft:stone", "creature:minecraft:iron_golem"));
        var other = shelves.get(shelves.size() - 1);
        assertEquals("other", other.id());
        assertEquals(List.of("block:minecraft:water", "creature:minecraft:iron_golem"), other.keys());
    }

    @Test
    void searchMatchesNamesAnyCaseAndBlankIsEverything() {
        var known = Set.of("block:minecraft:stone", "block:minecraft:dirt");
        Function<String, String> name = k -> k.endsWith("stone") ? "Stone" : "Dirt";
        assertEquals(List.of("block:minecraft:stone"), LexiconShelves.search(known, name, "sTo"));
        assertEquals(List.of("block:minecraft:dirt", "block:minecraft:stone"), LexiconShelves.search(known, name, " "));
    }
}
