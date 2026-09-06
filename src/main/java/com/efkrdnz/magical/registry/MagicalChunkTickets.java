package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

public final class MagicalChunkTickets {
    public static final TicketController SOVEREIGN_SEALS = new TicketController(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sovereign_seals"));

    private MagicalChunkTickets() {}

    public static void registerTicketControllers(RegisterTicketControllersEvent event) {
        event.register(SOVEREIGN_SEALS);
    }
}
