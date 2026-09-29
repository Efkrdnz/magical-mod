package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.magic.mind.Impression;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * What a lie looks like on the belt and on the Lexicon's shelves: a picture, never a thing. The
 * stacks made here are for drawing only and must never reach a slot, a menu, an inventory, a
 * payload or the server; a lie is its key.
 */
public final class LieIcons {
    private LieIcons() {}

    /** The block's own item, a creature's spawn egg, else paper for a block or a name tag for a creature; empty for no key. */
    public static ItemStack stack(String key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        Impression impression = Impression.parse(key);
        if (impression == null) {
            return new ItemStack(Items.PAPER);
        }
        ResourceLocation id = ResourceLocation.tryParse(impression.id());
        return impression.kind() == Impression.Kind.BLOCK ? blockIcon(id) : creatureIcon(id);
    }

    private static ItemStack blockIcon(ResourceLocation id) {
        if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
            Item item = BuiltInRegistries.BLOCK.getValue(id).asItem();
            if (item != Items.AIR) {
                return new ItemStack(item);
            }
        }
        return new ItemStack(Items.PAPER);
    }

    private static ItemStack creatureIcon(ResourceLocation id) {
        if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
            SpawnEggItem egg = SpawnEggItem.byId(type);
            if (egg != null) {
                return new ItemStack(egg);
            }
        }
        return new ItemStack(Items.NAME_TAG);
    }
}
