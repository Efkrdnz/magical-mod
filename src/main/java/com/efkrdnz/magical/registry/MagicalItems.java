package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.forge.weapon.MagicalWeapons;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;
import com.efkrdnz.magical.item.MagicalWeaponItem;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MagicalMod.MODID);

    public static final DeferredItem<BlockItem> UNWAKING_SHRINE = ITEMS.registerSimpleBlockItem(MagicalBlocks.UNWAKING_SHRINE, new Item.Properties());

    public static final DeferredItem<BlockItem> ASTRAL_STEP_SLAB = ITEMS.registerSimpleBlockItem(
            MagicalBlocks.ASTRAL_STEP_SLAB,
            new Item.Properties());

    public static final DeferredItem<BlockItem> ASTRAL_GATE = ITEMS.registerSimpleBlockItem(
            MagicalBlocks.ASTRAL_GATE,
            new Item.Properties());

    public static final DeferredItem<BlockItem> SACRIFICIAL_CORE = ITEMS.registerSimpleBlockItem(
            MagicalBlocks.SACRIFICIAL_CORE,
            new Item.Properties());

    /** Right-click a block to place a practice dummy on it. */
    public static final DeferredItem<com.efkrdnz.magical.item.TrainingDummyItem> TRAINING_DUMMY =
            ITEMS.registerItem("training_dummy", com.efkrdnz.magical.item.TrainingDummyItem::new, new Item.Properties());

    /**
     * The catalogue weapons, in {@link MagicalWeapons#orderedIds()} order.
     *
     * <p>Registered in a loop rather than one field each: the catalogue is meant to grow by someone
     * adding a row to {@code MagicalWeapons}, and a matching field here would be a second place to
     * remember. Anything needing one by name looks it up with {@link #weapon(ResourceLocation)}.
     */
    private static final Map<ResourceLocation, DeferredItem<MagicalWeaponItem>> WEAPONS = registerWeapons();

    private static Map<ResourceLocation, DeferredItem<MagicalWeaponItem>> registerWeapons() {
        Map<ResourceLocation, DeferredItem<MagicalWeaponItem>> map = new LinkedHashMap<>();
        for (ResourceLocation id : MagicalWeapons.orderedIds()) {
            WeaponDefinition definition = MagicalWeapons.get(id).orElseThrow();
            map.put(id, ITEMS.registerItem(definition.path(),
                    properties -> new MagicalWeaponItem(definition,
                            MagicalWeaponItem.propertiesFor(definition, properties))));
        }
        return Collections.unmodifiableMap(map);
    }

    /** The registered item for a catalogue id, or null for an id no longer in the catalogue. */
    public static DeferredItem<MagicalWeaponItem> weapon(ResourceLocation id) {
        return WEAPONS.get(id);
    }

    /** Every catalogue weapon, in catalogue order. */
    public static Collection<DeferredItem<MagicalWeaponItem>> weapons() {
        return WEAPONS.values();
    }

    private MagicalItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
