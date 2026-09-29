package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** The impression reel along the bottom while Daydreaming: shadowed text over a soft scrim, three lines. */
public final class ImpressionReelOverlay {
    private static final int CHOSEN = 0xFFFFFFFF;
    private static final int NEIGHBOUR = 0xFF9A93B0;
    private static final int STATUS = 0xFFBDA4FF;
    private static final int HINT = 0xFF8C86A0;
    /** The scrim fades out in columns this wide; six of them span a full feather. */
    private static final int STRIP = 4;

    private ImpressionReelOverlay() {}

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!DaydreamMode.active() || minecraft.screen != null) {
            return;
        }
        ImpressionReelLayout layout = new ImpressionReelLayout(graphics.guiWidth(), graphics.guiHeight());
        int lineLimit = graphics.guiWidth() - 2 * ImpressionReelLayout.MARGIN;
        var lexicon = ClientMagicState.get().mind().lexicon();
        Component status = Component.translatable("mind.magical.brush." + DaydreamMode.brush().name().toLowerCase(Locale.ROOT))
                .append("   " + DaydreamMode.draft().size() + " / " + lexicon.budget());
        String statusLine = minecraft.font.plainSubstrByWidth(status.getString(), lineLimit);
        Component hint = Component.translatable("mind.magical.daydream.hint", minecraft.options.keyInventory.getTranslatedKeyMessage());
        String hintLine = minecraft.font.plainSubstrByWidth(hint.getString(), lineLimit);

        scrim(graphics, layout, Math.max(minecraft.font.width(statusLine), minecraft.font.width(hintLine)));

        List<String> keys = DaydreamMode.keys();
        String chosen = DaydreamMode.impression();
        int at = chosen == null ? 0 : keys.indexOf(chosen);
        for (int i = -ImpressionReelLayout.SIDE; i <= ImpressionReelLayout.SIDE && !keys.isEmpty(); i++) {
            String key = keys.get(Math.floorMod(at + i, keys.size()));
            Rect cell = layout.cell(i);
            String name = minecraft.font.plainSubstrByWidth(MindGazeService.displayName(key).getString(), cell.w());
            int x = cell.x() + (cell.w() - minecraft.font.width(name)) / 2;
            graphics.drawString(minecraft.font, name, x, cell.y(), i == 0 ? CHOSEN : NEIGHBOUR, true);
        }
        graphics.drawString(minecraft.font, statusLine, (graphics.guiWidth() - minecraft.font.width(statusLine)) / 2, layout.statusY(), STATUS, true);
        graphics.drawString(minecraft.font, hintLine, (graphics.guiWidth() - minecraft.font.width(hintLine)) / 2, layout.hintY(), HINT, true);
    }

    /** Black at {@link ImpressionReelLayout#SCRIM_ALPHA} behind the lines, fading to nothing at every edge. */
    private static void scrim(GuiGraphics graphics, ImpressionReelLayout layout, int textWidth) {
        Rect scrim = layout.scrim(textWidth);
        int feather = layout.scrimFeather(textWidth);
        band(graphics, scrim.x() + feather, scrim.right() - feather, scrim, 1.0F);
        for (int offset = 0; offset < feather; offset += STRIP) {
            int width = Math.min(STRIP, feather - offset);
            float strength = (offset + width / 2.0F) / feather;
            band(graphics, scrim.x() + offset, scrim.x() + offset + width, scrim, strength);
            band(graphics, scrim.right() - offset - width, scrim.right() - offset, scrim, strength);
        }
    }

    /** One column of the scrim: a feather up, solid across the lines, a feather down. */
    private static void band(GuiGraphics graphics, int left, int right, Rect scrim, float strength) {
        int solid = Math.round(ImpressionReelLayout.SCRIM_ALPHA * strength) << 24;
        int top = scrim.y();
        int bottom = scrim.bottom();
        int feather = ImpressionReelLayout.FEATHER_Y;
        graphics.fillGradient(left, top, right, top + feather, 0, solid);
        graphics.fill(left, top + feather, right, bottom - feather, solid);
        graphics.fillGradient(left, bottom - feather, right, bottom, solid, 0);
    }
}
