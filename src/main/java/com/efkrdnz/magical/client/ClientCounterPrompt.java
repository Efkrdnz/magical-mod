package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.network.CounterClearPayload;
import com.efkrdnz.magical.network.CounterPromptPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ClientCounterPrompt {
    private static int threatId = -1;
    private static ResourceLocation incomingSkillId;
    private static ResourceLocation counterSkillId;
    private static int ticksLeft;
    private static int totalTicks;
    private static boolean wheelKeyWasDown;
    private static boolean suppressWheelUntilRelease;

    private ClientCounterPrompt() {}

    public static void receive(CounterPromptPayload payload) {
        threatId = payload.threatId();
        incomingSkillId = payload.incomingSkillId();
        counterSkillId = payload.counterSkillId();
        totalTicks = Math.max(1, payload.windowTicks());
        ticksLeft = totalTicks;
    }

    public static void clear(CounterClearPayload payload) {
        if (threatId == payload.threatId()) {
            clear();
        }
    }

    public static boolean tickAndConsumeWheel(Minecraft minecraft, boolean wheelKeyDown) {
        if (minecraft.player == null || minecraft.screen != null) {
            clear();
            return false;
        }
        if (suppressWheelUntilRelease) {
            if (!wheelKeyDown) {
                suppressWheelUntilRelease = false;
            }
            wheelKeyWasDown = wheelKeyDown;
            return true;
        }
        if (!active()) {
            wheelKeyWasDown = wheelKeyDown;
            return false;
        }
        boolean wasActive = true;
        if (wheelKeyDown && !wheelKeyWasDown) {
            MagicalNetwork.sendCounterResponse(threatId);
            clear();
            suppressWheelUntilRelease = true;
            wheelKeyWasDown = wheelKeyDown;
            return true;
        }
        wheelKeyWasDown = wheelKeyDown;
        ticksLeft--;
        if (ticksLeft <= 0) {
            clear();
        }
        return wasActive;
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (!active() || minecraft.options.hideGui) {
            return;
        }
        MagicSkillDefinition incoming = MagicContent.get(incomingSkillId);
        MagicSkillDefinition counter = MagicContent.get(counterSkillId);
        MagicPassiveDefinition passiveCounter = counter == null ? MagicPassiveContent.get(counterSkillId) : null;
        if (incoming == null || (counter == null && passiveCounter == null)) {
            return;
        }
        Component counterName = counter == null ? Component.translatable(passiveCounter.nameKey()) : Component.translatable(counter.nameKey());
        int counterColor = counter == null ? passiveCounter.color() : counter.color();

        int centerX = guiGraphics.guiWidth() / 2;
        int y = guiGraphics.guiHeight() / 2 - 60;
        float progress = Mth.clamp(ticksLeft / (float) Math.max(1, totalTicks), 0.0F, 1.0F);
        int width = 184;
        int left = centerX - width / 2;
        guiGraphics.fill(left, y, left + width, y + 43, 0xE20B101C);
        guiGraphics.fill(left + 4, y + 4, left + width - 4, y + 8, 0xFF000000 | incoming.color());
        guiGraphics.fill(left + 4, y + 36, left + 4 + Math.round((width - 8) * progress), y + 39, 0xFF000000 | counterColor);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("hud.magical.counter_title"), centerX, y + 12, 0xFFF8FCFF);
        String prompt = Component.translatable("hud.magical.counter_prompt", counterName, Component.translatable(incoming.nameKey())).getString();
        guiGraphics.drawCenteredString(minecraft.font, minecraft.font.plainSubstrByWidth(prompt, width - 12), centerX, y + 23, counterColor);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("hud.magical.counter_key"), centerX, y + 32, 0xFFE7D7A2);
    }

    private static boolean active() {
        return threatId >= 0 && ticksLeft > 0 && incomingSkillId != null && counterSkillId != null;
    }

    private static void clear() {
        threatId = -1;
        incomingSkillId = null;
        counterSkillId = null;
        ticksLeft = 0;
        totalTicks = 0;
    }
}
