package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Takes the number row away from the hotbar while something of ours is claiming it.
 *
 * <p>{@code consumeClick()} is simultaneously the read and the suppression: draining a key mapping
 * leaves nothing for anyone downstream to consume. That is the whole mechanism, and the only reason
 * it works is where it runs. {@code InputEvent.Key} is not cancellable in this version, and
 * {@code KeyboardHandler.keyPress} has already queued the click on the mapping before any listener
 * sees the event, so cancelling would be too late even if it were possible. Inside
 * {@code Minecraft.tick()} the order is client-tick-Pre, then {@code handleKeybinds()} - which
 * consumes that same counter and writes the selected hotbar slot - then client-tick-Post. Draining
 * in Pre is the one window where the press can be taken cleanly.
 *
 * <p>Reading the key mapping rather than a raw GLFW code also keeps a rebound number row working,
 * which the event-based handler this replaces did not.
 *
 * <p>The screen check has to live here too, and it is why this is a separate subscriber rather than
 * more code in the existing one: the cast ladder returns early when a screen is open, so an armed
 * handler would never see the release and would go on eating the number row inside every screen the
 * player opened afterwards.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class MagicalHotbarGuard {

    private MagicalHotbarGuard() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.screen != null) {
            // Nothing queues while a screen is open - keyPress only clicks a mapping when there is
            // no screen - so this is a disarm rather than a drain.
            BloodShapeInput.cancel();
            return;
        }

        boolean wheel = SpaceManipulationOverlay.active();
        boolean armed = BloodShapeInput.armed();
        if (!armed && !wheel) {
            return;
        }

        boolean save = minecraft.options.keyShift.isDown();
        for (int i = 0; i < 9 && i < minecraft.options.keyHotbarSlots.length; i++) {
            boolean pressed = false;
            while (minecraft.options.keyHotbarSlots[i].consumeClick()) {
                pressed = true;
            }
            if (!pressed) {
                continue;
            }
            if (armed) {
                BloodShapeInput.chooseShape(i);
                // One hold casts one shape: the rest of the row is still drained, so the hotbar
                // stays put, but it no longer selects anything.
                armed = false;
            } else if (wheel) {
                SpaceManipulationOverlay.handlePresetSlot(i, save);
            }
        }
    }
}
