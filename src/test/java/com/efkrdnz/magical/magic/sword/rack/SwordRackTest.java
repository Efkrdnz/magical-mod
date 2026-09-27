package com.efkrdnz.magical.magic.sword.rack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The rack holds real items, and an item is the one thing the mod must never lose for a player.
 */
class SwordRackTest {

    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void whatIsRackedComesBackFromDiskInTheSocketItWasIn() {
        SwordRack rack = new SwordRack();
        rack.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        rack.setItem(7, new ItemStack(Items.IRON_SWORD));
        rack.setItem(11, new ItemStack(Items.NETHERITE_AXE));
        CompoundTag saved = rack.save(registries);
        SwordRack loaded = SwordRack.load(saved, registries);
        for (int i = 0; i < SwordRack.SIZE; i++) {
            assertTrue(ItemStack.matches(rack.getItem(i), loaded.getItem(i)), "socket " + i + " came back different");
        }
        assertTrue(loaded.getItem(3).isEmpty(), "an empty socket came back with something in it");
    }

    @Test
    void aCopyForDeathSharesNoStackWithTheOriginal() {
        SwordRack rack = new SwordRack();
        rack.setItem(2, new ItemStack(Items.GOLDEN_SWORD));
        SwordRack copy = rack.copy();
        assertTrue(ItemStack.matches(rack.getItem(2), copy.getItem(2)));
        assertNotSame(rack.getItem(2), copy.getItem(2), "the clone moves the same stack between two players");
        copy.removeItem(2, 1);
        assertEquals(1, rack.getItem(2).getCount(), "emptying the copy emptied the original");
    }

    @Test
    void aSocketHoldsOneWeapon() {
        SwordRack rack = new SwordRack();
        rack.setItem(0, new ItemStack(Items.IRON_SWORD, 1));
        assertEquals(1, rack.getMaxStackSize());
        assertEquals(SwordRack.SIZE, rack.getContainerSize());
        assertTrue(rack.getItem(-1).isEmpty() && rack.getItem(SwordRack.SIZE).isEmpty(),
                "a socket off the end of the rack answers with an item");
    }
}
