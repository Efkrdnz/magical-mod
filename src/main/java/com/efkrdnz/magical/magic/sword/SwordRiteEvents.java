package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Where the Answering is plugged into the game, and it is four subscribers and no logic.
 *
 * <p>The whole of the rite is in {@link SwordRiteService}; this class exists so that everything
 * the Answering listens to is in one place and so that the busiest of those listeners can be
 * looked at on its own. {@link #onItemToss} runs for <b>every item every player in the world ever
 * drops</b>, so its first statement is the field read that excludes almost all of them and there
 * is nothing above that line at all - no state lookup, no level read, no map access.
 *
 * <p>Death drops do not fire {@code ItemTossEvent}. That is not a gap: the rite must be a thing
 * you chose to do, and what fell out of you when you died did not choose anything.
 *
 * <p>The logout and dimension-change arms mirror {@code CausalityEvents}' pair for the same
 * reason - a rite in progress belongs to a session and a place, and the swords are handed back
 * rather than deleted when either of those ends. {@code SwordRiteService.forget} is idempotent, so
 * a second call from {@code MagicGameplayEvents.onPlayerLogout} costs nothing; so is
 * {@link SwordRiteService#tick}, which refuses a second run inside one server tick rather than
 * running the ceremony at double speed.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class SwordRiteEvents {

    private SwordRiteEvents() {}

    /**
     * A sword sneak-dropped at night under open sky hangs; four of them, laid out, are answered.
     *
     * <p>The stack test is first and everything else is behind it. A player emptying an inventory
     * of cobblestone pays one tag lookup per stack and nothing more.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity item = event.getEntity();
        if (!SwordRiteService.isRiteBlade(item.getItem())) {
            return;
        }
        if (event.getPlayer() instanceof ServerPlayer player) {
            SwordRiteService.tossed(player, item);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        SwordRiteService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SwordRiteService.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        // The swords do not come with you, and neither does a rite that was half performed.
        SwordRiteService.forget(event.getEntity().getUUID());
    }

    /**
     * Everything this service holds is an entity reference into a level that is about to stop
     * existing. In single player the next world starts in the same JVM, so without this the maps
     * would still be holding swords from the last one.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SwordRiteService.clear();
    }
}
