package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.MagicOpponentEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@EventBusSubscriber(modid = MagicalMod.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class MagicalEntityEvents {
    private MagicalEntityEvents() {}

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(MagicalEntities.MAGIC_OPPONENT.get(), MagicOpponentEntity.createAttributes().build());
        event.put(MagicalEntities.EFFIGY.get(), com.efkrdnz.magical.entity.fx.EffigyEntity.createAttributes().build());
        event.put(MagicalEntities.SPIRIT_WOLF.get(), com.efkrdnz.magical.entity.fx.SpiritWolfEntity.createAttributes().build());
        event.put(MagicalEntities.POLYMORPH_SHELL.get(), com.efkrdnz.magical.entity.fx.PolymorphShellEntity.createAttributes().build());
    }
}
