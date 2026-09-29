package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** The impression reel along the bottom while Daydreaming: frameless, shadowed, three lines. */
public final class ImpressionReelOverlay {
    private static final int CHOSEN = 0xFFFFFFFF;
    private static final int NEIGHBOUR = 0xFF9A93B0;
    private static final int STATUS = 0xFFBDA4FF;
    private static final int HINT = 0xFF8C86A0;

    private ImpressionReelOverlay() {}

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!DaydreamMode.active() || minecraft.screen != null) {
            return;
        }
        ImpressionReelLayout layout = new ImpressionReelLayout(graphics.guiWidth(), graphics.guiHeight());
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
        var lexicon = ClientMagicState.get().mind().lexicon();
        Component status = Component.translatable("mind.magical.brush." + DaydreamMode.brush().name().toLowerCase(Locale.ROOT))
                .append("   " + DaydreamMode.draft().size() + " / " + lexicon.budget());
        graphics.drawString(minecraft.font, status, (graphics.guiWidth() - minecraft.font.width(status)) / 2, layout.statusY(), STATUS, true);
        Component hint = Component.translatable("mind.magical.daydream.hint", minecraft.options.keyInventory.getTranslatedKeyMessage());
        String line = minecraft.font.plainSubstrByWidth(hint.getString(), graphics.guiWidth() - 2 * ImpressionReelLayout.MARGIN);
        graphics.drawString(minecraft.font, line, (graphics.guiWidth() - minecraft.font.width(line)) / 2, layout.hintY(), HINT, true);
    }
}
