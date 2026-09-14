package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.client.hud.HudBatch;
import com.efkrdnz.magical.client.hud.HudGlyphs;
import com.efkrdnz.magical.client.hud.HudKind;
import com.efkrdnz.magical.client.hud.HudPalette;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;

/**
 * Queues the emblems a screen wants drawn and flushes them in one {@code drawSpecial} on the HUD
 * sigil render type. Order matters: everything filled before the flush lands under the emblems
 * and everything drawn after lands over them, so a screen paints its chrome, flushes, then writes
 * its text. One instance per screen; nothing is allocated per frame once the looks are cached.
 */
public final class EmblemPainter implements Consumer<MultiBufferSource> {
    private static final int CAPACITY = 64;

    private final HudBatch batch = new HudBatch();
    private final Map<ResourceLocation, int[]> looks = new HashMap<>();
    private final float[] cx = new float[CAPACITY];
    private final float[] cy = new float[CAPACITY];
    private final float[] half = new float[CAPACITY];
    private final float[] alpha = new float[CAPACITY];
    private final int[] cell = new int[CAPACITY];
    private final int[] rgb = new int[CAPACITY];
    private int count;
    private GuiGraphics graphics;

    public void begin(GuiGraphics graphics) {
        this.graphics = graphics;
        count = 0;
    }

    /** An emblem in its own tint. */
    public void add(ResourceLocation id, float cx, float cy, float half, float alpha) {
        add(id, cx, cy, half, alpha, false);
    }

    /** {@code dim} draws it in the muted text grey: a missing ingredient, a result not yet within reach. */
    public void add(ResourceLocation id, float cx, float cy, float half, float alpha, boolean dim) {
        if (id == null || count >= CAPACITY) {
            return;
        }
        int[] look = looks.computeIfAbsent(id, EmblemPainter::look);
        if (look[0] < 0) {
            return;
        }
        this.cx[count] = cx;
        this.cy[count] = cy;
        this.half[count] = half;
        this.alpha[count] = alpha;
        cell[count] = look[0];
        rgb[count] = dim ? HudPalette.TEXT_MUTED : look[1];
        count++;
    }

    /** The tint a skill's emblem is drawn with; never the raw skill colour, which for Black Flames is nearly black. */
    public int tint(ResourceLocation id) {
        return looks.computeIfAbsent(id, EmblemPainter::look)[1] & 0xFFFFFF;
    }

    /** The one {@code drawSpecial} of the frame; a no-op when nothing was queued. */
    public void flush() {
        if (count > 0 && graphics != null) {
            graphics.drawSpecial(this);
        }
        count = 0;
    }

    @Override
    public void accept(MultiBufferSource buffers) {
        batch.begin(buffers.getBuffer(MagicalFxRenderTypes.hudSigil()), graphics.pose().last().pose());
        for (int i = 0; i < count; i++) {
            batch.square(cx[i], cy[i], half[i], rgb[i], alpha[i], HudKind.EMBLEM, cell[i] & 63, cell[i] >> 6, 0.0F, 0);
        }
    }

    /** The atlas cell and the card tint of a skill, the same pair the HUD draws it with. */
    private static int[] look(ResourceLocation id) {
        int cell = HudGlyphs.skillCell(id);
        int tint = cell < 0 ? HudPalette.TEXT_MUTED : HudPalette.cardTint(VisualProfiles.of(id));
        return new int[] {cell, tint};
    }
}
