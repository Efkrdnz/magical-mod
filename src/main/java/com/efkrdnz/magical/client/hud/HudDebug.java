package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.MagicalKeyMappings;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 *   <li>{@code -Dmagical.autoCommands=a;120:b;c} ({@code -PautoCommands=...}): sent as chat
 *       commands forty ticks after the player is in a world, or at the tick a {@code N:} prefix
 *       names; from tick eighty any screen the mod opened is closed every ten ticks - so a fresh
 *       world can be given a race, a class, skills and a forced HUD state before a capture.</li>
 *   <li>{@code -Dmagical.autoScreenshot=N[,M...]} ({@code -PautoScreenshot=...}): saves a
 *       screenshot N ticks after the player is in a world, and one more at each further tick
 *       listed.</li>
 *   <li>{@code -Dmagical.autoHold=cast_slot_2} ({@code -PautoHold=...}): holds that key mapping
 *       down from sixty ticks before the first screenshot until it is taken, so a hold overlay -
 *       a radial, the space dials, the switcher, the blood strip - can be captured.</li>
 *   <li>{@code -Dmagical.autoExit=true} ({@code -PautoExit}): closes the game twenty ticks after
 *       the last screenshot, so a capture can run unattended.</li>
 * </ul>
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class HudDebug {
    /** A screen the auto-closer must leave alone, because a capture is of it. */
    public interface Captured {}

    private record Command(int tick, String text) {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COMMANDS_AT_TICK = 40;
    private static final int CLOSE_SCREEN_AT_TICK = 80;
    private static final int HOLD_BEFORE_SCREENSHOT = 60;
    private static final int EXIT_AFTER_SCREENSHOT = 20;
    private static final int[] SCREENSHOT_TICKS = ticks(System.getProperty("magical.autoScreenshot", ""));
    private static final Command[] COMMANDS = commands(System.getProperty("magical.autoCommands", ""));
    private static final String AUTO_HOLD = System.getProperty("magical.autoHold", "");
    private static final boolean AUTO_EXIT = Boolean.getBoolean("magical.autoExit");

    private static final boolean[] SENT = new boolean[COMMANDS.length];
    private static int ticksInWorld;
    private static int screenshotsTaken;

    private HudDebug() {}

    /** {@code "52,112"} to sorted ticks; anything unparseable is ignored, an empty string is none. */
    private static int[] ticks(String property) {
        List<Integer> ticks = new ArrayList<>();
        for (String part : property.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int tick = Integer.parseInt(trimmed);
                if (tick >= 0) {
                    ticks.add(tick);
                }
            } catch (NumberFormatException bad) {
                LOGGER.warn("HUD auto-screenshot: not a tick: {}", trimmed);
            }
        }
        int[] out = ticks.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(out);
        return out;
    }

    /** {@code "a;120:b"}: each command at the tick its prefix names, or the default tick without one. */
    private static Command[] commands(String property) {
        List<Command> commands = new ArrayList<>();
        for (String part : property.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            int tick = COMMANDS_AT_TICK;
            if (colon > 0 && trimmed.substring(0, colon).chars().allMatch(Character::isDigit)) {
                tick = Integer.parseInt(trimmed.substring(0, colon));
                trimmed = trimmed.substring(colon + 1).trim();
            }
            commands.add(new Command(tick, trimmed));
        }
        return commands.toArray(new Command[0]);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (SCREENSHOT_TICKS.length == 0 && COMMANDS.length == 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            ticksInWorld = 0;
            return;
        }
        ticksInWorld++;
        for (int i = 0; i < COMMANDS.length; i++) {
            if (!SENT[i] && ticksInWorld >= COMMANDS[i].tick()) {
                SENT[i] = true;
                LOGGER.info("HUD auto-command: /{}", COMMANDS[i].text());
                minecraft.player.connection.sendCommand(COMMANDS[i].text());
            }
        }
        boolean allTaken = screenshotsTaken >= SCREENSHOT_TICKS.length;
        // The mod's onboarding opens a screen per unanswered choice; keep clearing them until the last capture.
        if (COMMANDS.length > 0 && ticksInWorld >= CLOSE_SCREEN_AT_TICK && ticksInWorld % 10 == 0 && !allTaken) {
            if (minecraft.screen instanceof AbstractContainerScreen<?>) {
                minecraft.player.closeContainer();
            } else if (minecraft.screen != null && !(minecraft.screen instanceof Captured)) {
                minecraft.setScreen(null);
            }
        }
        if (!AUTO_HOLD.isEmpty() && screenshotsTaken == 0 && SCREENSHOT_TICKS.length > 0 && ticksInWorld >= SCREENSHOT_TICKS[0] - HOLD_BEFORE_SCREENSHOT) {
            KeyMapping held = heldMapping();
            if (held != null) {
                held.setDown(true);
            }
        }
        if (!allTaken && ticksInWorld >= SCREENSHOT_TICKS[screenshotsTaken]) {
            screenshotsTaken++;
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
                    message -> LOGGER.info("HUD auto-screenshot: {}", message.getString()));
        }
        if (AUTO_EXIT && SCREENSHOT_TICKS.length > 0 && screenshotsTaken >= SCREENSHOT_TICKS.length
                && ticksInWorld >= SCREENSHOT_TICKS[SCREENSHOT_TICKS.length - 1] + EXIT_AFTER_SCREENSHOT) {
            LOGGER.info("HUD auto-exit: every screenshot is taken");
            minecraft.stop();
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
