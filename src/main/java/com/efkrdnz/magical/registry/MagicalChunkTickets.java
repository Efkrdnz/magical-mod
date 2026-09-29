package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.util.List;

public final class MagicalChunkTickets {
    public static final TicketController SOVEREIGN_SEALS = new TicketController(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sovereign_seals"));
    /**
     * Keeps a dreamer's body loaded while their mind is in the Dream, one ticket per dreamer. A dream
     * never outlives the server, so any ticket a crash left behind is dropped when the level loads.
     */
    public static final TicketController DREAM_SLEEPERS = new TicketController(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream_sleepers"),
            (level, tickets) -> List.copyOf(tickets.getEntityTickets().keySet()).forEach(tickets::removeAllTickets));

    private MagicalChunkTickets() {}

    public static void registerTicketControllers(RegisterTicketControllersEvent event) {
        event.register(SOVEREIGN_SEALS);
        event.register(DREAM_SLEEPERS);
    }
}
