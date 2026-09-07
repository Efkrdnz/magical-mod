package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.forge.chain.ForgeError;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.network.ForgeResultPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The banner that reports the last forge attempt: the server's verdict, or a refusal the screen
 * predicted before it bothered the server. It fades out on its own after {@link #LIFETIME_TICKS}.
 */
public final class ForgeResultFlash {

    /** How long a verdict stays on screen. */
    public static final int LIFETIME_TICKS = 80;

    private static final int SUCCESS = 0xA6E3A1;
    private static final int FAILURE = 0xF38BA8;
    private static final int LINE_HEIGHT = 11;

    private Component text;
    private int color = SUCCESS;
    private int ticks;

    /** Shows the server's reply: the quality on success, the translated error otherwise. */
    public void accept(ForgeResultPayload result) {
        if (result.success()) {
            show(Component.translatable("message.magical.forge.success", result.quality()), SUCCESS);
            return;
        }
        ForgeError error = result.error().orElse(ForgeError.BAD_PAYLOAD);
        show(Component.translatable(error.langKey(), argument(error, result.argument())), FAILURE);
    }

    /** Shows a refusal the client worked out for itself. */
    public void showFailure(Component message) {
        show(message, FAILURE);
    }

    public void tick() {
        if (ticks > 0) {
            ticks--;
        }
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int width) {
        if (ticks <= 0 || text == null) {
            return;
        }
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += LINE_HEIGHT;
        }
    }

    private void show(Component message, int messageColor) {
        text = message;
        color = messageColor;
        ticks = LIFETIME_TICKS;
    }

    /** MATERIAL_CAP carries a grade ordinal; every other error's argument is already a number. */
    private static Object argument(ForgeError error, int argument) {
        if (error != ForgeError.MATERIAL_CAP || argument < 0 || argument >= ForgeGrade.values().length) {
            return argument;
        }
        return ForgePreviewPanel.gradeName(ForgeGrade.values()[argument]);
    }
}
