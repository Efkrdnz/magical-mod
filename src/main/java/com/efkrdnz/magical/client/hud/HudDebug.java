package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.MagicalKeyMappings;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Development hooks for looking at the HUD without a hand on the keyboard. All off unless their
 * property is present, so a normal run never touches them.
 *
 * <ul>
 *   <li>{@code -Dmagical.autoCommands=a;b;c} ({@code -PautoCommands=...}): sent as chat commands
 *       forty ticks after the player is in a world; from tick eighty any screen the mod opened is
 *       closed every ten ticks - so a fresh world can be given a race, a class, skills and a forced
 *       HUD state before a capture.</li>
 *   <li>{@code -Dmagical.autoScreenshot=N} ({@code -PautoScreenshot=N}): saves one screenshot N
 *       ticks after the player is in a world.</li>
 *   <li>{@code -Dmagical.autoHold=cast_slot_2} ({@code -PautoHold=...}): holds that key mapping
 *       down from sixty ticks before the screenshot until it is taken, so a hold overlay - a
 *       radial, the space dials, the switcher, the blood strip - can be captured.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class HudDebug {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int AUTO_SCREENSHOT_TICKS = Integer.getInteger("magical.autoScreenshot", -1);
    private static final String AUTO_COMMANDS = System.getProperty("magical.autoCommands", "");
    private static final String AUTO_HOLD = System.getProperty("magical.autoHold", "");
    private static final int COMMANDS_AT_TICK = 40;
    private static final int CLOSE_SCREEN_AT_TICK = 80;
    private static final int HOLD_BEFORE_SCREENSHOT = 60;

    private static int ticksInWorld;
    private static boolean commandsSent;
    private static boolean taken;

    private HudDebug() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (AUTO_SCREENSHOT_TICKS < 0 && AUTO_COMMANDS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ticksInWorld = 0;
            return;
        }
        ticksInWorld++;
        if (!commandsSent && !AUTO_COMMANDS.isEmpty() && ticksInWorld >= COMMANDS_AT_TICK) {
            commandsSent = true;
            for (String command : AUTO_COMMANDS.split(";")) {
                String trimmed = command.trim();
                if (!trimmed.isEmpty()) {
                    LOGGER.info("HUD auto-command: /{}", trimmed);
                    minecraft.player.connection.sendCommand(trimmed);
                }
            }
        }
        // The mod's onboarding opens a screen per unanswered choice; keep clearing them until the capture.
        if (!AUTO_COMMANDS.isEmpty() && ticksInWorld >= CLOSE_SCREEN_AT_TICK && ticksInWorld % 10 == 0 && !taken) {
            if (minecraft.screen instanceof AbstractContainerScreen<?>) {
                minecraft.player.closeContainer();
            } else if (minecraft.screen != null) {
                minecraft.setScreen(null);
            }
        }
        if (!AUTO_HOLD.isEmpty() && !taken && AUTO_SCREENSHOT_TICKS >= 0 && ticksInWorld >= AUTO_SCREENSHOT_TICKS - HOLD_BEFORE_SCREENSHOT) {
            KeyMapping held = heldMapping();
            if (held != null) {
                held.setDown(true);
            }
        }
        if (!taken && AUTO_SCREENSHOT_TICKS >= 0 && ticksInWorld >= AUTO_SCREENSHOT_TICKS) {
            taken = true;
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
                    message -> LOGGER.info("HUD auto-screenshot: {}", message.getString()));
        }
    }

    private static KeyMapping heldMapping() {
        for (KeyMapping mapping : MagicalKeyMappings.CAST_SLOTS) {
            if (mapping.getName().endsWith(AUTO_HOLD)) {
                return mapping;
            }
        }
        return MagicalKeyMappings.OPEN_WHEEL.getName().endsWith(AUTO_HOLD) ? MagicalKeyMappings.OPEN_WHEEL : null;
    }
}
