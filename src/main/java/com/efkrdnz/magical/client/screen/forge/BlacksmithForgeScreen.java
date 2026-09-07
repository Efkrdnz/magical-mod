package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.client.ClientForgeResults;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.forge.chain.ForgeChainBuilder;
import com.efkrdnz.magical.forge.chain.ForgeChainBuilder.CommittedGlyph;
import com.efkrdnz.magical.forge.chain.ForgeError;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.RecognitionResult;
import com.efkrdnz.magical.forge.menu.BlacksmithForgeMenu;
import com.efkrdnz.magical.network.ForgeSubmitPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

/**
 * The Runeforge: a drawing canvas on the left, the committed rune chain beneath it, and a preview
 * or the glyph codex on the right. Drawing owns the mouse while a stroke is open, so the container's
 * slot dragging never fires mid-stroke; everything the screen predicts is re-checked by the server.
 */
public final class BlacksmithForgeScreen extends AbstractContainerScreen<BlacksmithForgeMenu> {

    private static final int CANVAS_X = 14;
    private static final int CANVAS_Y = 26;
    private static final int CANVAS_SIZE = 176;
    private static final int STRIP_X = 14;
    private static final int STRIP_Y = 206;
    private static final int RIGHT_X = 240;
    private static final int RIGHT_RIGHT = 430;
    private static final int RIGHT_TOP = 26;
    private static final int PREVIEW_BOTTOM = 168;
    private static final int CODEX_BOTTOM = 228;
    private static final int CODEX_LIST_BOTTOM = 202;
    private static final int APPLY_X = 14;
    private static final int APPLY_Y = 224;
    private static final int APPLY_W = 64;
    private static final int APPLY_H = 16;
    private static final int BUTTON_Y = 208;
    private static final int BUTTON_H = 18;
    private static final int INSCRIBE_X = 240;
    private static final int INSCRIBE_W = 70;
    private static final int UNDO_X = 314;
    private static final int UNDO_W = 44;
    private static final int CLEAR_X = 362;
    private static final int CLEAR_W = 44;
    private static final int CODEX_BUTTON_X = 410;
    private static final int CODEX_BUTTON_W = 20;
    private static final int BACK_X = 14;
    private static final int BACK_Y = 6;
    private static final int BACK_W = 44;
    private static final int BACK_H = 16;
    private static final int GATE_Y = 228;
    private static final int FLASH_Y = 176;
    private static final int READY_BUTTON = 0xFF8A6E1E;
    private static final int ACTIVE_BUTTON = 0xFF3F5C1A;
    private static final int DISABLED_BUTTON = 0xFF27354A;
    private static final int NEUTRAL_BUTTON = 0xFF2D3F5C;
    private static final int FAILURE_TEXT = 0xF38BA8;
    /** A result already sitting in the holder on the screen's first poll is from a prior session. */
    private static final long STALE_RESULT_MILLIS = 1000L;

    private final ForgeChainBuilder builder = new ForgeChainBuilder(ForgeGlyphLibrary.recognizer());
    private final ForgeCanvasRenderer canvas = new ForgeCanvasRenderer();
    private final ForgeChainStrip strip = new ForgeChainStrip();
    private final ForgePreviewPanel preview = new ForgePreviewPanel(new ForgeStatPreview());
    private final ForgeGlyphCodexPanel codex = new ForgeGlyphCodexPanel();
    private final ForgeResultFlash flash = new ForgeResultFlash();
    private final ForgeSlotPreload slotPreload = new ForgeSlotPreload();

    private boolean codexOpen;
    private boolean drawing;
    private int ticks;
    private boolean firstPoll = true;
    private ForgeCanvasRenderer.CommitAnimation animation;

    public BlacksmithForgeScreen(BlacksmithForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 444;
        imageHeight = 340;
        inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        // The screen draws its own gold title; park the vanilla container labels off-screen.
        titleLabelY = 10000;
    }

    // --- tick --------------------------------------------------------------------------------

    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (!drawing) {
            builder.tick();
        }
        flash.tick();
        pollForgeResult();
        slotPreload.sync(builder, menu.weaponStack());
    }

    /**
     * The committed chain survives a verdict either way, so the player can reforge from it. A
     * result already sitting in the holder the moment this screen opens is a stale reply from a
     * forge attempted, and abandoned, in an earlier session: discard it without flashing it.
     */
    private void pollForgeResult() {
        boolean stalePollAllowed = firstPoll;
        firstPoll = false;
        ClientForgeResults.latest().ifPresent(result -> {
            long age = Util.getMillis() - ClientForgeResults.receivedAtMillis();
            if (stalePollAllowed && age > STALE_RESULT_MILLIS) {
                ClientForgeResults.clear();
                return;
            }
            flash.accept(result);
            ClientForgeResults.clear();
        });
    }

    /** Drains a leftover verdict so it cannot flash on the next Runeforge session. */
    @Override
    public void removed() {
        super.removed();
        ClientForgeResults.clear();
    }

    private void onCommitted(CommittedGlyph glyph) {
        List<CommittedGlyph> committed = builder.committed();
        int index = committed.size() - 1;
        animation = ForgeGlyphLibrary.byId(glyph.id())
                .flatMap(template -> strip.cellCentre(index, leftPos + STRIP_X, topPos + STRIP_Y, committed)
                        .flatMap(cell -> ForgeCanvasRenderer.CommitAnimation.of(
                                ticks, glyph.strokes(), template, cell[0], cell[1])))
                .orElse(null);
    }

    // --- rendering ---------------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MagicalGuiStyle.screenBackground(graphics, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        MagicalGuiStyle.button(graphics, font, leftPos + BACK_X, topPos + BACK_Y, BACK_W, BACK_H,
                NEUTRAL_BUTTON, Component.translatable("screen.magical.forge_back"));
        MagicalGuiStyle.sectionLabel(graphics, font, leftPos + BACK_X + BACK_W + 8, topPos + BACK_Y + 3,
                Component.translatable("screen.magical.runeforge"), MagicalGuiStyle.ACCENT_GOLD);
        canvas.render(graphics, font, leftPos + CANVAS_X, topPos + CANVAS_Y, CANVAS_SIZE, builder,
                animationOrNull(), ticks);
        strip.render(graphics, leftPos + STRIP_X, topPos + STRIP_Y, builder.committed());
        drawWeaponSlot(graphics);
        drawRightColumn(graphics, mouseX, mouseY);
        drawInventory(graphics);
    }

    /** Drops the animation once it has played out, so it never replays on a later frame. */
    private ForgeCanvasRenderer.CommitAnimation animationOrNull() {
        if (animation != null && ticks - animation.startTick() >= ForgeCanvasRenderer.CommitAnimation.TOTAL_TICKS) {
            animation = null;
        }
        return animation;
    }

    private void drawWeaponSlot(GuiGraphics graphics) {
        int x = leftPos + BlacksmithForgeMenu.WEAPON_SLOT_X;
        int y = topPos + BlacksmithForgeMenu.WEAPON_SLOT_Y;
        graphics.drawString(font, Component.translatable("screen.magical.forge_weapon_slot"), x - 8, y - 12,
                MagicalGuiStyle.TEXT_MUTED, false);
        MagicalGuiStyle.slot(graphics, x, y, MagicalGuiStyle.withAlpha(MagicalGuiStyle.ACCENT_GOLD, 0xAA));
    }

    private void drawRightColumn(GuiGraphics graphics, int mouseX, int mouseY) {
        if (codexOpen) {
            codex.render(graphics, font, leftPos + RIGHT_X, topPos + RIGHT_TOP, leftPos + RIGHT_RIGHT,
                    topPos + CODEX_BOTTOM, topPos + CODEX_LIST_BOTTOM, mouseX, mouseY);
        } else {
            preview.render(graphics, font, leftPos + RIGHT_X, topPos + RIGHT_TOP, leftPos + RIGHT_RIGHT,
                    topPos + PREVIEW_BOTTOM, builder.recognizedChain(), ClientMagicState.get().maxMana());
        }
        // The flash must stay visible whether or not the codex panel is open; both panels clear y 176.
        flash.render(graphics, font, leftPos + RIGHT_X, topPos + FLASH_Y, RIGHT_RIGHT - RIGHT_X);
        drawButtons(graphics);
    }

    private void drawButtons(GuiGraphics graphics) {
        Optional<ForgePreviewPanel.PredictedError> gate = gate();
        boolean ready = canSubmit(gate);
        MagicalGuiStyle.button(graphics, font, leftPos + APPLY_X, topPos + APPLY_Y, APPLY_W, APPLY_H,
                builder.current().status() == RecognitionResult.Status.ACCEPTED
                        ? READY_BUTTON : DISABLED_BUTTON,
                Component.translatable("screen.magical.forge_apply"));
        MagicalGuiStyle.button(graphics, font, leftPos + INSCRIBE_X, topPos + BUTTON_Y, INSCRIBE_W, BUTTON_H,
                ready ? READY_BUTTON : DISABLED_BUTTON, Component.translatable("screen.magical.forge_inscribe"));
        MagicalGuiStyle.button(graphics, font, leftPos + UNDO_X, topPos + BUTTON_Y, UNDO_W, BUTTON_H,
                NEUTRAL_BUTTON, Component.translatable("screen.magical.forge_undo"));
        MagicalGuiStyle.button(graphics, font, leftPos + CLEAR_X, topPos + BUTTON_Y, CLEAR_W, BUTTON_H,
                NEUTRAL_BUTTON, Component.translatable("screen.magical.forge_clear"));
        MagicalGuiStyle.button(graphics, font, leftPos + CODEX_BUTTON_X, topPos + BUTTON_Y, CODEX_BUTTON_W, BUTTON_H,
                codexOpen ? ACTIVE_BUTTON : NEUTRAL_BUTTON, Component.literal("?"));
        gate.ifPresent(error -> graphics.drawString(font,
                font.plainSubstrByWidth(error.message().getString(), RIGHT_RIGHT - RIGHT_X),
                leftPos + INSCRIBE_X, topPos + GATE_Y, FAILURE_TEXT, false));
    }

    private void drawInventory(GuiGraphics graphics) {
        int x = leftPos + BlacksmithForgeMenu.PLAYER_INV_X;
        int y = topPos + BlacksmithForgeMenu.PLAYER_INV_Y;
        MagicalGuiStyle.panel(graphics, x - 10, y - 18, x + 172, topPos + BlacksmithForgeMenu.HOTBAR_Y + 28,
                MagicalGuiStyle.ACCENT_GOLD);
        graphics.drawString(font, Component.translatable("container.inventory"), x, y - 12,
                MagicalGuiStyle.TEXT_MUTED, false);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                MagicalGuiStyle.slot(graphics, x + column * 18, y + row * 18, DISABLED_BUTTON);
            }
        }
        for (int column = 0; column < 9; column++) {
            MagicalGuiStyle.slot(graphics, x + column * 18, topPos + BlacksmithForgeMenu.HOTBAR_Y, DISABLED_BUTTON);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> tooltip = hoverTooltip(mouseX, mouseY);
        if (tooltip.isEmpty()) {
            renderTooltip(graphics, mouseX, mouseY);
        } else {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> hoverTooltip(int mouseX, int mouseY) {
        if (inside(mouseX, mouseY, CODEX_BUTTON_X, BUTTON_Y, CODEX_BUTTON_W, BUTTON_H)) {
            return List.of(Component.translatable("screen.magical.forge_codex"));
        }
        if (codexOpen) {
            return codex.tooltipAt(mouseX, mouseY, leftPos + RIGHT_X, topPos + RIGHT_TOP,
                    leftPos + RIGHT_RIGHT, topPos + CODEX_LIST_BOTTOM);
        }
        List<CommittedGlyph> committed = builder.committed();
        int index = strip.cellAt(mouseX, mouseY, leftPos + STRIP_X, topPos + STRIP_Y, committed);
        if (index < 0) {
            return List.of();
        }
        CommittedGlyph glyph = committed.get(index);
        List<Component> lines = new ArrayList<>(3);
        lines.add(Component.translatable("forge.magical.glyph." + glyph.id()));
        lines.add(Component.translatable("screen.magical.forge_quality", glyph.quality()));
        if (glyph.kept()) {
            lines.add(Component.translatable("screen.magical.forge_kept"));
        }
        return lines;
    }

    // --- input ------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insideCanvas(mouseX, mouseY)) {
            return canvasClicked(mouseX, mouseY, button);
        }
        if (codexOpen && codex.mouseClicked(mouseX, mouseY, leftPos + RIGHT_X, topPos + RIGHT_TOP,
                leftPos + RIGHT_RIGHT, topPos + CODEX_LIST_BOTTOM)) {
            return true;
        }
        int cell = strip.cellAt(mouseX, mouseY, leftPos + STRIP_X, topPos + STRIP_Y, builder.committed());
        if (button == 0 && cell >= 0) {
            builder.removeCommitted(cell);
            return true;
        }
        if (button == 0 && handleButtons(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean canvasClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            builder.undoStroke();
            return true;
        }
        if (button == 0) {
            drawing = builder.beginStroke(canvasX(mouseX), canvasY(mouseY));
        }
        return true;
    }

    private boolean handleButtons(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, BACK_X, BACK_Y, BACK_W, BACK_H)) {
            MagicalNetwork.sendOpenCodexRequest();
            return true;
        }
        if (inside(mouseX, mouseY, APPLY_X, APPLY_Y, APPLY_W, APPLY_H)) {
            commitCurrent();
            return true;
        }
        if (inside(mouseX, mouseY, INSCRIBE_X, BUTTON_Y, INSCRIBE_W, BUTTON_H)) {
            submit();
            return true;
        }
        if (inside(mouseX, mouseY, UNDO_X, BUTTON_Y, UNDO_W, BUTTON_H)) {
            builder.undoStroke();
            return true;
        }
        if (inside(mouseX, mouseY, CLEAR_X, BUTTON_Y, CLEAR_W, BUTTON_H)) {
            builder.clearCurrent();
            return true;
        }
        if (inside(mouseX, mouseY, CODEX_BUTTON_X, BUTTON_Y, CODEX_BUTTON_W, BUTTON_H)) {
            codexOpen = !codexOpen;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (drawing) {
            builder.extendStroke(canvasX(mouseX), canvasY(mouseY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (drawing) {
            builder.extendStroke(canvasX(mouseX), canvasY(mouseY));
            builder.endStroke();
            drawing = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (codexOpen && codex.mouseScrolled(mouseX, mouseY, scrollY, leftPos + RIGHT_X, topPos + RIGHT_TOP,
                leftPos + RIGHT_RIGHT, topPos + CODEX_BOTTOM, topPos + CODEX_LIST_BOTTOM)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitCurrent();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                builder.undoStroke();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                builder.clearCurrent();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    /**
     * Enter commits only what the server would accept. An ambiguous drawing is refused there, so it
     * is refused here too - and said out loud, because Enter doing nothing is the one outcome the
     * amber canvas readout does not explain on its own.
     */
    private void commitCurrent() {
        Optional<CommittedGlyph> glyph = builder.commitNow();
        if (glyph.isPresent()) {
            onCommitted(glyph.get());
            return;
        }
        if (builder.current().status() == RecognitionResult.Status.AMBIGUOUS) {
            flash.showFailure(Component.translatable("screen.magical.forge_ambiguous_commit"));
        }
    }

    // --- submission --------------------------------------------------------------------------

    /**
     * Sends the chain when the local gate sees nothing wrong, or when the only objection is one the
     * server owns (cooldown and mana move behind the client's back). Anything else flashes locally.
     */
    private void submit() {
        Optional<ForgePreviewPanel.PredictedError> gate = gate();
        if (!canSubmit(gate)) {
            flash.showFailure(gate.map(ForgePreviewPanel.PredictedError::message)
                    .orElseGet(() -> Component.translatable("screen.magical.forge_preview_empty")));
            return;
        }
        MagicalNetwork.sendForgeSubmit(
                ForgeSubmitPayload.fromCanvas(menu.containerId, builder.committed()));
    }

    private boolean canSubmit(Optional<ForgePreviewPanel.PredictedError> gate) {
        if (builder.committed().isEmpty()) {
            return false;
        }
        return gate.map(error -> error.error() == ForgeError.COOLDOWN || error.error() == ForgeError.NO_MANA)
                .orElse(true);
    }

    private Optional<ForgePreviewPanel.PredictedError> gate() {
        return preview.predict(builder.recognizedChain(), menu.weaponStack(), ClientMagicState.get(), gameTime());
    }

    private long gameTime() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    // --- geometry ----------------------------------------------------------------------------

    private boolean insideCanvas(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, CANVAS_X, CANVAS_Y, CANVAS_SIZE, CANVAS_SIZE);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    /** Canvas coordinates clamp at the edges, so a stroke dragged off the plate stops on its border. */
    private float canvasX(double mouseX) {
        return Mth.clamp((float) (mouseX - (leftPos + CANVAS_X)) / CANVAS_SIZE, 0f, 1f);
    }

    private float canvasY(double mouseY) {
        return Mth.clamp((float) (mouseY - (topPos + CANVAS_Y)) / CANVAS_SIZE, 0f, 1f);
    }
}
