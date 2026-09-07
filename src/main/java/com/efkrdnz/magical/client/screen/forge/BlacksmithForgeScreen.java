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
 * The Runeforge: a drawing canvas on the left, the committed rune chain beneath it, and the
 * predicted-weapon preview on the right - which keeps that column at all times. Drawing owns the
 * mouse while a stroke is open, so the container's slot dragging never fires mid-stroke; everything
 * the screen predicts is re-checked by the server. The glyph codex is a modal overlay
 * ({@link ForgeCodexOverlay}) laid over the whole screen, and changes no forging state.
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
    private static final int FLASH_Y = 176;
    /** A result already sitting in the holder on the first poll is from a prior session. */
    private static final long STALE_RESULT_MILLIS = 1000L;

    private final ForgeChainBuilder builder = new ForgeChainBuilder(ForgeGlyphLibrary.recognizer());
    private final ForgeCanvasRenderer canvas = new ForgeCanvasRenderer();
    private final ForgeChainStrip strip = new ForgeChainStrip();
    private final ForgePreviewPanel preview = new ForgePreviewPanel(new ForgeStatPreview());
    private final ForgeCodexOverlay codex = new ForgeCodexOverlay();
    private final ForgeResultFlash flash = new ForgeResultFlash();

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

    /**
     * Nothing commits on its own; a drawing becomes a sigil only when the player presses Apply or
     * Enter. The idle counter still runs for the canvas hint, and is held while the codex overlay
     * is open so browsing the reference does not age the drawing underneath it.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        ticks++;
        if (!drawing && !codex.isOpen()) {
            builder.tick();
        }
        flash.tick();
        pollForgeResult();
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
        ForgeChrome.header(graphics, font, leftPos, topPos);
        canvas.render(graphics, font, leftPos + CANVAS_X, topPos + CANVAS_Y, CANVAS_SIZE, builder,
                animationOrNull(), ticks);
        strip.render(graphics, leftPos + STRIP_X, topPos + STRIP_Y, builder.committed());
        ForgeChrome.apply(graphics, font, leftPos, topPos,
                builder.current().status() == RecognitionResult.Status.ACCEPTED);
        ForgeChrome.weaponSlot(graphics, font, leftPos, topPos);
        drawRightColumn(graphics);
        ForgeChrome.inventory(graphics, font, leftPos, topPos);
    }

    /** Drops the animation once it has played out, so it never replays on a later frame. */
    private ForgeCanvasRenderer.CommitAnimation animationOrNull() {
        if (animation != null && ticks - animation.startTick() >= ForgeCanvasRenderer.CommitAnimation.TOTAL_TICKS) {
            animation = null;
        }
        return animation;
    }

    /** The preview owns this column outright; the codex never displaces it. */
    private void drawRightColumn(GuiGraphics graphics) {
        preview.render(graphics, font, leftPos + RIGHT_X, topPos + RIGHT_TOP, leftPos + RIGHT_RIGHT,
                topPos + PREVIEW_BOTTOM, builder.recognizedChain(), ClientMagicState.get().maxMana());
        flash.render(graphics, font, leftPos + RIGHT_X, topPos + FLASH_Y, RIGHT_RIGHT - RIGHT_X);
        Optional<ForgePreviewPanel.PredictedError> gate = gate();
        ForgeChrome.buttons(graphics, font, leftPos, topPos, canSubmit(gate), codex.isOpen(),
                gate.map(ForgePreviewPanel.PredictedError::message));
    }

    /** The overlay draws after the slots and their items, so nothing shows through its backdrop. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        codex.render(graphics, font, guiRect(), width, height, mouseX, mouseY);
        List<Component> tooltip = hoverTooltip(mouseX, mouseY);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        } else if (!codex.isOpen()) {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private List<Component> hoverTooltip(int mouseX, int mouseY) {
        if (codex.isOpen()) {
            return codex.tooltipAt(mouseX, mouseY, guiRect());
        }
        if (ForgeChrome.hit(mouseX, mouseY, leftPos, topPos) == ForgeChrome.Hit.CODEX) {
            return List.of(Component.translatable("screen.magical.forge_codex"));
        }
        List<CommittedGlyph> committed = builder.committed();
        int index = strip.cellAt(mouseX, mouseY, leftPos + STRIP_X, topPos + STRIP_Y, committed);
        if (index < 0) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable("forge.magical.glyph." + committed.get(index).id()));
        lines.add(Component.translatable("screen.magical.forge_quality", committed.get(index).quality()));
        return lines;
    }

    // --- input ------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (codex.mouseClicked(mouseX, mouseY, guiRect())) {
            return true;
        }
        if (insideCanvas(mouseX, mouseY)) {
            return canvasClicked(mouseX, mouseY, button);
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
        switch (ForgeChrome.hit(mouseX, mouseY, leftPos, topPos)) {
            case BACK -> MagicalNetwork.sendOpenCodexRequest();
            case APPLY -> commitCurrent();
            case INSCRIBE -> submit();
            case UNDO -> builder.undoStroke();
            case CLEAR -> builder.clearCurrent();
            case CODEX -> openCodex();
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    /**
     * A stroke can only still be open here if the player hit the codex button with the other mouse
     * button held down on the canvas. Close it exactly the way releasing would have: the points
     * already drawn are kept and re-recognized, so nothing is committed, nothing is cancelled, and
     * no half-drawn stroke is left to resume when the overlay closes.
     */
    private void openCodex() {
        if (drawing) {
            builder.endStroke();
            drawing = false;
        }
        codex.toggle();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (codex.isOpen()) {
            return true;
        }
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
        if (codex.mouseScrolled(mouseX, mouseY, scrollY, guiRect())) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (codex.keyPressed(keyCode)) {
            return true;
        }
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
                ForgeSubmitPayload.fromCanvas(menu.containerId, builder.toPayloadGlyphs()));
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

    private ForgeCodexOverlay.Rect guiRect() {
        return new ForgeCodexOverlay.Rect(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
    }

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
