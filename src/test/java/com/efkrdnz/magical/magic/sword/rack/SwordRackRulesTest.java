package com.efkrdnz.magical.magic.sword.rack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Swords only, until the apex hands out Weapon God.
 */
class SwordRackRulesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyVanillaSwordIsASword() {
        for (var item : List.of(Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD,
                Items.DIAMOND_SWORD, Items.NETHERITE_SWORD)) {
            ItemStack stack = new ItemStack(item);
            assertTrue(SwordRackRules.accepts(stack, false), item + " is refused before Weapon God");
            assertTrue(SwordRackRules.accepts(stack, true), item + " is refused after Weapon God");
        }
    }

    @Test
    void otherWeaponsWaitForWeaponGod() {
        for (var item : List.of(Items.IRON_AXE, Items.NETHERITE_AXE, Items.MACE, Items.TRIDENT, Items.BOW,
                Items.CROSSBOW)) {
            ItemStack stack = new ItemStack(item);
            assertFalse(SwordRackRules.accepts(stack, false), item + " flies before Weapon God");
            assertTrue(SwordRackRules.accepts(stack, true), item + " is not a weapon to Weapon God");
        }
    }

    @Test
    void thingsThatAreNotWeaponsAreNeverRacked() {
        for (var item : List.of(Items.STICK, Items.DIAMOND, Items.IRON_PICKAXE, Items.SHIELD, Items.APPLE)) {
            ItemStack stack = new ItemStack(item);
            assertFalse(SwordRackRules.accepts(stack, false), item + " racked as a sword");
            assertFalse(SwordRackRules.accepts(stack, true), item + " racked as a weapon");
        }
        assertFalse(SwordRackRules.accepts(ItemStack.EMPTY, true), "an empty hand is a weapon");
    }
}
