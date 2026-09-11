package com.efkrdnz.magical.client.screen.blood;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.client.screen.forge.ForgeInk;
import com.efkrdnz.magical.magic.BloodShapeBook;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import com.efkrdnz.magical.network.BloodShapeSubmitPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Where the player draws the shapes the blood will follow.
 *
 * <p>A plain {@link Screen} rather than a container screen, and the only one in the mod. There is no
 * server state to open against - the book already rides on the synced player state, because that
 * state's save format is also its wire format - so a menu would buy nothing and cost an open round
 * trip on a key that gets tapped mid-fight, plus a run of button ids inside a band the fusion
 * buttons already shadow.
 *
 * <p>Two consequences of that choice are paid for here: {@link #isPauseScreen()} returns false,
 * because unlike every other screen in the mod this one is opened during a fight, and the edit is
 * submitted from {@link #removed()}, because there is no container tick to hang it off.
 */
public class BloodShapeEditorScreen extends Screen {

    private static final int[] FLAG_FOR_ROW = {
            BloodShapeRules.FLAG_TRACK_YAW,
            BloodShapeRules.FLAG_TRACK_PITCH,
            BloodShapeRules.FLAG_KEEP_ROTATING};

    private static final String[] ROW_KEY = {
            "screen.magical.blood_shape.track_yaw",
            "screen.magical.blood_shape.track_pitch",
            "screen.magical.blood_shape.keep_rotating"};

    private static final int ACTION_CLEAR = 0;
    private static final int ACTION_UNDO = 1;
    private static final int ACTION_DONE = 2;

    private static final int INK = 0xFFE06470;

    private int leftPos;
    private int topPos;

    private int slot;
    private BloodStrokeBuilder builder = new BloodStrokeBuilder();
    private int heightPercent = BloodShapeRules.DEFAULT_HEIGHT_PERCENT;
    private int flags;
    private boolean dirty;
    private boolean draggingSlider;

    public BloodShapeEditorScreen() {
        super(Component.translatable("screen.magical.blood_shape.title"));
    }

    @Override
    protected void init() {
        leftPos = (width - BloodShapeLayout.PANEL_W) / 2;
        topPos = (height - BloodShapeLayout.PANEL_H) / 2;
        load(state().selectedBloodShape());
    }

    /** Opened mid-fight, so it must not stop the world the way every other screen here does. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        submit();
        super.removed();
    }

    // ---- drawing ------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int x0 = leftPos;
        int y0 = topPos;
        MagicalGuiStyle.panel(g, x0, y0, x0 + BloodShapeLayout.PANEL_W,
                y0 + BloodShapeLayout.PANEL_H, MagicalGuiStyle.ACCENT_BLOOD);
        MagicalGuiStyle.legend(g, font, x0 + 16, y0 + 9, title, 0xFFB9C2);

        BloodShapeCanvas.draw(g, font, x0, y0, builder, halfExtent(),
                BloodShapeRules.has(flags, BloodShapeRules.FLAG_TRACK_YAW));

        drawControls(g, x0, y0);
        drawStrip(g, x0, y0);
    }

    private void drawControls(GuiGraphics g, int x0, int y0) {
        MagicalGuiStyle.sectionLabel(g, font, x0 + BloodShapeLayout.SIDE_X,
                y0 + BloodShapeLayout.TITLE_Y,
                Component.translatable("screen.magical.blood_shape.slot", slot + 1),
                MagicalGuiStyle.TEXT_PRIMARY);

        g.drawString(font, Component.translatable("screen.magical.blood_shape.height"),
                x0 + BloodShapeLayout.SIDE_X, y0 + BloodShapeLayout.SLIDER_LABEL_Y,
                MagicalGuiStyle.TEXT_MUTED, false);
        MagicalGuiStyle.slider(g, x0 + BloodShapeLayout.SLIDER_X, y0 + BloodShapeLayout.SLIDER_Y,
                BloodShapeLayout.SLIDER_W, BloodShapeLayout.SLIDER_H, heightPercent / 100.0F,
                MagicalGuiStyle.ACCENT_BLOOD);
        g.drawString(font, Component.literal(heightPercent + "%"),
                x0 + BloodShapeLayout.sliderValue().x(), y0 + BloodShapeLayout.SLIDER_Y + 1,
                MagicalGuiStyle.TEXT_PRIMARY, false);

        for (int row = 0; row < BloodShapeLayout.CHECK_COUNT; row++) {
            int rowY = y0 + BloodShapeLayout.CHECK_Y + row * BloodShapeLayout.CHECK_STRIDE;
            MagicalGuiStyle.checkbox(g, x0 + BloodShapeLayout.SIDE_X, rowY,
                    BloodShapeRules.has(flags, FLAG_FOR_ROW[row]), MagicalGuiStyle.ACCENT_BLOOD);
            g.drawString(font, Component.translatable(ROW_KEY[row]),
                    x0 + BloodShapeLayout.SIDE_X + 16, rowY + 1, MagicalGuiStyle.TEXT_PRIMARY, false);
        }

        drawReadout(g, x0, y0);

        button(g, x0, y0, ACTION_CLEAR, 0xFF2A1A20, "screen.magical.blood_shape.clear");
        button(g, x0, y0, ACTION_UNDO, 0xFF2A1A20, "screen.magical.blood_shape.undo");
        button(g, x0, y0, ACTION_DONE, 0xFF3A2028, "screen.magical.blood_shape.done");
    }

    private void button(GuiGraphics g, int x0, int y0, int action, int base, String key) {
        MagicalGuiStyle.button(g, font, x0 + BloodShapeLayout.actionButton(action).x(),
                y0 + BloodShapeLayout.ACTION_Y, BloodShapeLayout.ACTION_W,
                BloodShapeLayout.ACTION_H, base, Component.translatable(key));
    }

    /**
     * Reach, drawn length and blood cost, all from the same pure functions the server bills with, so
     * the number shown is the number charged rather than an estimate that can drift away from it.
     */
    private void drawReadout(GuiGraphics g, int x0, int y0) {
        double extent = halfExtent();
        double drawn = BloodShapeGeometry.arcLength(shape(), extent);
        int line = y0 + BloodShapeLayout.READOUT_Y;
        int x = x0 + BloodShapeLayout.SIDE_X;
        g.drawString(font, Component.translatable("screen.magical.blood_shape.reach",
                String.format("%.1f", extent)), x, line, MagicalGuiStyle.TEXT_MUTED, false);
        g.drawString(font, Component.translatable("screen.magical.blood_shape.drawn",
                String.format("%.1f", drawn)), x, line + 12, MagicalGuiStyle.TEXT_MUTED, false);
        g.drawString(font, Component.translatable("screen.magical.blood_shape.cost",
                BloodShapeRules.bloodCost(drawn)), x, line + 24, INK, false);
    }

    private void drawStrip(GuiGraphics g, int x0, int y0) {
        PlayerMagicState state = state();
        for (int i = 0; i < BloodShapeRules.SHAPE_SLOTS; i++) {
            int cellX = x0 + BloodShapeLayout.stripCellX(i);
            int cellY = y0 + BloodShapeLayout.STRIP_Y;
            MagicalGuiStyle.listRow(g, cellX, cellY, BloodShapeLayout.STRIP_CELL,
                    BloodShapeLayout.STRIP_CELL, i == slot, MagicalGuiStyle.ACCENT_BLOOD);
            // The open slot draws from the builder, not the book: the book still holds what was last
            // submitted, and showing that would make an unsaved edit look as though it had been lost.
            drawThumbnail(g, cellX, cellY,
                    i == slot ? builder.strokes() : state.bloodShapes().shape(i).strokes());
            Component index = Component.literal(String.valueOf(i + 1));
            g.drawString(font, index, cellX + BloodShapeLayout.STRIP_CELL - 1 - font.width(index),
                    cellY + BloodShapeLayout.STRIP_CELL - 8,
                    i == slot ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED, false);
        }
    }

    /**
     * A thumbnail scaled to its own extent rather than to the reach: at this size the job is telling
     * nine shapes apart, not judging how far any of them goes.
     */
    private void drawThumbnail(GuiGraphics g, int cellX, int cellY, List<int[]> strokes) {
        double extent = 0.0D;
        for (int[] stroke : strokes) {
            for (int packed : stroke) {
                extent = Math.max(extent, Math.abs(BloodShapeRules.unpackX(packed)));
                extent = Math.max(extent, Math.abs(BloodShapeRules.unpackY(packed)));
            }
        }
        if (extent <= 0.0D) {
            return;
        }
        double scale = (BloodShapeLayout.STRIP_CELL / 2.0D - 3.0D) / extent;
        double centreX = cellX + BloodShapeLayout.STRIP_CELL / 2.0D;
        double centreY = cellY + BloodShapeLayout.STRIP_CELL / 2.0D;
        for (int[] stroke : strokes) {
            List<int[]> points = new ArrayList<>(stroke.length);
            for (int packed : stroke) {
                points.add(new int[] {
                        (int) Math.round(centreX + BloodShapeRules.unpackX(packed) * scale),
                        (int) Math.round(centreY - BloodShapeRules.unpackY(packed) * scale)});
            }
            ForgeInk.polyline(g, points, 1, INK);
        }
    }

    // ---- input --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;

        if (button == 1) {
            // Right-click undoes, exactly as the forge canvas does.
            builder.undo();
            dirty = true;
            return true;
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (BloodShapeLayout.contains(BloodShapeLayout.canvas(), lx, ly)) {
            builder.begin(BloodShapeLayout.canvasU(lx), BloodShapeLayout.canvasV(ly));
            return true;
        }
        if (grabsSlider(lx, ly)) {
            draggingSlider = true;
            setHeightFrom(lx);
            return true;
        }
        int check = BloodShapeLayout.checkAt(lx, ly);
        if (check >= 0) {
            flags = BloodShapeRules.clampFlags(flags ^ FLAG_FOR_ROW[check]);
            dirty = true;
            return true;
        }
        int action = BloodShapeLayout.actionAt(lx, ly);
        if (action >= 0) {
            act(action);
            return true;
        }
        int picked = BloodShapeLayout.slotAt(lx, ly);
        if (picked >= 0) {
            select(picked);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        if (draggingSlider) {
            setHeightFrom(lx);
            return true;
        }
        if (builder.isDrawing()) {
            // The cursor is clamped rather than ignored past the edge, so a stroke dragged off the
            // plate rides the boundary instead of stopping where the hand left the square.
            builder.extend(BloodShapeLayout.canvasU(lx), BloodShapeLayout.canvasV(ly));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = false;
        if (builder.isDrawing()) {
            builder.finish(halfExtent());
            dirty = true;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The same one-to-nine gesture that fires a shape also picks one to edit.
        if (keyCode >= 49 && keyCode <= 57) {
            select(keyCode - 49);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- state --------------------------------------------------------------------------------

    private void act(int action) {
        switch (action) {
            case ACTION_CLEAR -> {
                builder.clear();
                dirty = true;
            }
            case ACTION_UNDO -> {
                builder.undo();
                dirty = true;
            }
            default -> onClose();
        }
    }

    private void select(int index) {
        if (index == slot || !BloodShapeBook.isSlot(index)) {
            return;
        }
        submit();
        load(index);
    }

    private void load(int index) {
        slot = BloodShapeBook.isSlot(index) ? index : 0;
        BloodShape stored = state().bloodShapes().shape(slot);
        builder = BloodStrokeBuilder.of(stored);
        heightPercent = stored.heightPercent();
        flags = stored.flags();
        dirty = false;
    }

    /**
     * Sends the edit, and mirrors it locally so the strip and the canvas do not flick back to the
     * old shape in the frames before the server's sync lands.
     */
    private void submit() {
        if (!dirty) {
            return;
        }
        BloodShape edited = shape();
        state().bloodShapes().setShape(slot, edited);
        MagicalNetwork.sendBloodShapeSubmit(BloodShapeSubmitPayload.of(slot, edited));
        dirty = false;
    }

    private BloodShape shape() {
        return builder.toShape(heightPercent, flags);
    }

    private void setHeightFrom(double lx) {
        int percent = BloodShapeRules.clampHeightPercent(
                Math.round(BloodShapeLayout.sliderFraction(lx) * 100.0F));
        if (percent != heightPercent) {
            heightPercent = percent;
            dirty = true;
        }
    }

    /** A few pixels of slop above and below the track, because a ten-pixel bar is a small target. */
    private boolean grabsSlider(double lx, double ly) {
        return lx >= BloodShapeLayout.SLIDER_X
                && lx < BloodShapeLayout.SLIDER_X + BloodShapeLayout.SLIDER_W
                && ly >= BloodShapeLayout.SLIDER_Y - 3
                && ly < BloodShapeLayout.SLIDER_Y + BloodShapeLayout.SLIDER_H + 3;
    }

    /**
     * The reach this canvas represents, read from the caster's own tuning. The server recomputes it
     * at cast time from its own copy, so this is a preview rather than an authority.
     */
    private double halfExtent() {
        return BloodShapeRules.halfExtentBlocks(MagicContent.BLOOD_MANIPULATION
                .resolve(state().tuningFor(MagicContent.BLOOD_MANIPULATION.id())).size());
    }

    private static PlayerMagicState state() {
        return ClientMagicState.get();
    }
}
