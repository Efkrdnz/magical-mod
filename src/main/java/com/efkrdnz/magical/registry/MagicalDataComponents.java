package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.forge.ForgedWeapon;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MagicalMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgedWeapon>> FORGED_WEAPON =
            COMPONENTS.registerComponentType("forged_weapon", b -> b.persistent(ForgedWeapon.CODEC)
                    .networkSynchronized(ForgedWeapon.STREAM_CODEC).cacheEncoding());

    private MagicalDataComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
