package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.arcane.ArcaneContent;
import com.efkrdnz.magical.arcane.ArcaneModifierDefinition;
import com.efkrdnz.magical.arcane.ArcaneRuneDefinition;
import com.efkrdnz.magical.arcane.ArcaneShapeDefinition;
import com.efkrdnz.magical.arcane.menu.ArcaneWorkbenchMenu;
import com.efkrdnz.magical.client.ClientArcaneState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public final class ArcaneWorkbenchScreen extends AbstractContainerScreen<ArcaneWorkbenchMenu> {
    private static final int CIRCLE_SIZE = 180;
    private static final int CIRCLE_LEFT = 18;
    private static final int CIRCLE_TOP = 28;
    private static final float RUNE_ZONE = 0.34F;
    private static final float SHAPE_ZONE = 0.68F;

    private final List<StrokePoint> runeStroke = new ArrayList<>();
    private final List<StrokePoint> shapeStroke = new ArrayList<>();
    private final List<List<StrokePoint>> modifierStrokeHistory = new ArrayList<>();
    private final List<StrokePoint> currentStroke = new ArrayList<>();

    private boolean drawingActive;
    private Button saveButton;
    private Button loadButton;
    private Button deleteButton;
    private Button activeButton;
    private Button clearModifiersButton;

    private Component circleHint = Component.translatable("screen.magical.draw_hint_circle");
    private Component lastDetection = Component.translatable("screen.magical.draw_hint_layers");

    public ArcaneWorkbenchScreen(ArcaneWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 356;
        this.imageHeight = 256;
        this.inventoryLabelY = 228;
    }

    @Override
    protected void init() {
        super.init();
        int buttonTop = topPos + 224;
        saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.magical.save_short"), button -> press(ArcaneWorkbenchMenu.BUTTON_SAVE))
                .bounds(leftPos + 16, buttonTop, 62, 20)
                .build());
        loadButton = addRenderableWidget(Button.builder(Component.translatable("screen.magical.load_short"), button -> press(ArcaneWorkbenchMenu.BUTTON_LOAD_NEXT))
                .bounds(leftPos + 84, buttonTop, 62, 20)
                .build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("screen.magical.delete_short"), button -> press(ArcaneWorkbenchMenu.BUTTON_DELETE))
                .bounds(leftPos + 152, buttonTop, 62, 20)
                .build());
        activeButton = addRenderableWidget(Button.builder(Component.translatable("screen.magical.active_short"), button -> press(ArcaneWorkbenchMenu.BUTTON_SET_ACTIVE))
                .bounds(leftPos + 220, buttonTop, 62, 20)
                .build());
        clearModifiersButton = addRenderableWidget(Button.builder(Component.translatable("screen.magical.clear_mods"), button -> {
                    modifierStrokeHistory.clear();
                    lastDetection = Component.translatable("screen.magical.modifiers_cleared");
                    press(ArcaneWorkbenchMenu.BUTTON_CLEAR_MODIFIERS);
                })
                .bounds(leftPos + 288, buttonTop, 52, 20)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        deleteButton.active = menu.presetCount() > 0;
        loadButton.active = menu.presetCount() > 0;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF00B1320, 0xF0162337);
        guiGraphics.fill(leftPos + 8, topPos + 18, leftPos + imageWidth - 8, topPos + imageHeight - 10, 0xC00F1724);

        drawMagicCircle(guiGraphics);

        renderStroke(guiGraphics, runeStroke, 0xFFF38BA8);
        renderStroke(guiGraphics, shapeStroke, 0xFF89DCEB);
        for (List<StrokePoint> stroke : modifierStrokeHistory) {
            renderStroke(guiGraphics, stroke, 0xFFA6E3A1);
        }
        if (drawingActive) {
            renderStroke(guiGraphics, currentStroke, 0xFFF9E2AF);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        ArcaneRuneDefinition rune = ArcaneContent.RUNES.get(menu.runeIndex());
        ArcaneShapeDefinition shape = ArcaneContent.SHAPES.get(menu.shapeIndex());
        int activeModifiers = Integer.bitCount(menu.modifierBitMask());

        guiGraphics.drawString(font, title, leftPos + 16, topPos + 8, 0xF5E6C0, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.detected_rune", rune.displayName()), leftPos + 214, topPos + 36, 0xF9E2AF, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.detected_shape", shape.displayName()), leftPos + 214, topPos + 50, 0xCBEAFD, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.mana_cost", menu.manaCost()), leftPos + 214, topPos + 74, 0xD9DDE8, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.stability", menu.stability()), leftPos + 214, topPos + 88, menu.stability() >= 65 ? 0xA6E3A1 : 0xF38BA8, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.preset_count", menu.presetCount()), leftPos + 214, topPos + 112, 0xD9DDE8, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.active_index", Math.max(menu.activePresetIndex() + 1, 0)), leftPos + 214, topPos + 126, 0xD9DDE8, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.active_modifiers", activeModifiers), leftPos + 214, topPos + 140, 0xA6E3A1, false);

        guiGraphics.drawWordWrap(font, circleHint, leftPos + 214, topPos + 150, 124, 0xE4E1D0);
        guiGraphics.drawWordWrap(font, lastDetection, leftPos + 214, topPos + 188, 124, 0xCDECC8);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x90A4B5, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insideCircleBounds(mouseX, mouseY)) {
            drawingActive = true;
            currentStroke.clear();
            addPoint(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && drawingActive) {
            addPoint(mouseX, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && drawingActive) {
            addPoint(mouseX, mouseY);
            finishStroke();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void finishStroke() {
        if (currentStroke.size() < 3) {
            currentStroke.clear();
            drawingActive = false;
            return;
        }

        GlyphMetrics metrics = GlyphMetrics.of(currentStroke);
        float averageRadius = averageRadiusNormalized(currentStroke);
        if (averageRadius <= RUNE_ZONE) {
            runeStroke.clear();
            runeStroke.addAll(currentStroke);
            int runeIndex = detectRune(metrics);
            press(ArcaneWorkbenchMenu.BUTTON_SET_RUNE_BASE + runeIndex);
            lastDetection = Component.translatable(
                    "screen.magical.detect_result_layer",
                    Component.translatable("screen.magical.layer_rune"),
                    ArcaneContent.RUNES.get(runeIndex).displayName());
        } else if (averageRadius <= SHAPE_ZONE) {
            shapeStroke.clear();
            shapeStroke.addAll(currentStroke);
            int shapeIndex = detectShape(metrics);
            press(ArcaneWorkbenchMenu.BUTTON_SET_SHAPE_BASE + shapeIndex);
            lastDetection = Component.translatable(
                    "screen.magical.detect_result_layer",
                    Component.translatable("screen.magical.layer_shape"),
                    ArcaneContent.SHAPES.get(shapeIndex).displayName());
        } else {
            int modifierIndex = detectModifier(metrics);
            ArcaneModifierDefinition modifier = ArcaneContent.MODIFIERS.get(modifierIndex);
            if (ClientArcaneState.get().hasUnlocked(modifier.id())) {
                modifierStrokeHistory.add(List.copyOf(currentStroke));
                boolean enabled = (menu.modifierBitMask() & (1 << modifierIndex)) == 0;
                press((enabled ? ArcaneWorkbenchMenu.BUTTON_ENABLE_MODIFIER_BASE : ArcaneWorkbenchMenu.BUTTON_DISABLE_MODIFIER_BASE) + modifierIndex);
                lastDetection = Component.translatable(
                        "screen.magical.detect_result_layer",
                        Component.translatable("screen.magical.layer_modifier"),
                        modifier.displayName());
            } else {
                lastDetection = Component.translatable("screen.magical.detect_locked", modifier.displayName());
            }
        }

        currentStroke.clear();
        drawingActive = false;
    }

    private void addPoint(double mouseX, double mouseY) {
        StrokePoint point = clampToCircle(mouseX, mouseY);
        int x = point.x;
        int y = point.y;
        if (currentStroke.isEmpty() || currentStroke.get(currentStroke.size() - 1).x != x || currentStroke.get(currentStroke.size() - 1).y != y) {
            currentStroke.add(point);
        }
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private boolean insideCircleBounds(double mouseX, double mouseY) {
        int left = leftPos + CIRCLE_LEFT;
        int top = topPos + CIRCLE_TOP;
        int right = left + CIRCLE_SIZE;
        int bottom = top + CIRCLE_SIZE;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private StrokePoint clampToCircle(double mouseX, double mouseY) {
        float centerX = circleCenterX();
        float centerY = circleCenterY();
        float radius = circleRadius();
        float dx = (float) mouseX - centerX;
        float dy = (float) mouseY - centerY;
        float distance = Mth.sqrt(dx * dx + dy * dy);
        if (distance > radius) {
            float scale = radius / Math.max(distance, 0.001F);
            dx *= scale;
            dy *= scale;
        }
        return new StrokePoint(Mth.floor(centerX + dx), Mth.floor(centerY + dy));
    }

    private float averageRadiusNormalized(List<StrokePoint> points) {
        float centerX = circleCenterX();
        float centerY = circleCenterY();
        float radius = circleRadius();
        float total = 0.0F;
        for (StrokePoint point : points) {
            float dx = point.x - centerX;
            float dy = point.y - centerY;
            total += Mth.sqrt(dx * dx + dy * dy);
        }
        return total / points.size() / Math.max(radius, 1.0F);
    }

    private float circleCenterX() {
        return leftPos + CIRCLE_LEFT + CIRCLE_SIZE / 2.0F;
    }

    private float circleCenterY() {
        return topPos + CIRCLE_TOP + CIRCLE_SIZE / 2.0F;
    }

    private float circleRadius() {
        return CIRCLE_SIZE / 2.0F - 4.0F;
    }

    private void drawMagicCircle(GuiGraphics guiGraphics) {
        int left = leftPos + CIRCLE_LEFT;
        int top = topPos + CIRCLE_TOP;
        int centerX = left + CIRCLE_SIZE / 2;
        int centerY = top + CIRCLE_SIZE / 2;

        guiGraphics.fill(left, top, left + CIRCLE_SIZE, top + CIRCLE_SIZE, 0x90111A28);
        guiGraphics.renderOutline(left, top, CIRCLE_SIZE, CIRCLE_SIZE, 0xFF314053);

        drawCircleOutline(guiGraphics, centerX, centerY, (int) (CIRCLE_SIZE * 0.49F), 0xFF8E7E62);
        drawCircleOutline(guiGraphics, centerX, centerY, (int) (CIRCLE_SIZE * SHAPE_ZONE * 0.5F), 0xFF496C8A);
        drawCircleOutline(guiGraphics, centerX, centerY, (int) (CIRCLE_SIZE * RUNE_ZONE * 0.5F), 0xFF9A5A4F);

        guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.layer_rune"), centerX, top + 78, 0xF6C5CF);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.layer_shape"), centerX, top + 44, 0xCBEAFD);
        guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.layer_modifier"), centerX, top + 12, 0xCDECC8);
    }

    private void drawCircleOutline(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int color) {
        int steps = 96;
        int previousX = centerX + radius;
        int previousY = centerY;
        for (int i = 1; i <= steps; i++) {
            float angle = (float) (Math.PI * 2.0D * i / steps);
            int x = centerX + Mth.floor(Mth.cos(angle) * radius);
            int y = centerY + Mth.floor(Mth.sin(angle) * radius);
            drawLine(guiGraphics, previousX, previousY, x, y, color);
            previousX = x;
            previousY = y;
        }
    }

    private void drawLine(GuiGraphics guiGraphics, int fromX, int fromY, int toX, int toY, int color) {
        int steps = Math.max(Math.abs(toX - fromX), Math.abs(toY - fromY));
        for (int step = 0; step <= steps; step++) {
            float progress = steps == 0 ? 0.0F : (float) step / (float) steps;
            int drawX = Mth.floor(Mth.lerp(progress, fromX, toX));
            int drawY = Mth.floor(Mth.lerp(progress, fromY, toY));
            guiGraphics.fill(drawX, drawY, drawX + 1, drawY + 1, color);
        }
    }

    private void renderStroke(GuiGraphics guiGraphics, List<StrokePoint> stroke, int color) {
        if (stroke.size() < 2) {
            return;
        }
        for (int i = 1; i < stroke.size(); i++) {
            StrokePoint from = stroke.get(i - 1);
            StrokePoint to = stroke.get(i);
            int steps = Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y));
            for (int step = 0; step <= steps; step++) {
                float progress = steps == 0 ? 0.0F : (float) step / (float) steps;
                int drawX = Mth.floor(Mth.lerp(progress, from.x, to.x));
                int drawY = Mth.floor(Mth.lerp(progress, from.y, to.y));
                guiGraphics.fill(drawX - 1, drawY - 1, drawX + 1, drawY + 1, color);
            }
        }
    }

    private int detectRune(GlyphMetrics metrics) {
        if (metrics.horizontalBias > 1.25F && metrics.yDirectionChanges >= 3) {
            return ArcaneContent.RUNES.indexOf(ArcaneContent.WATER_RUNE);
        }
        if (metrics.sharpTurns >= 5 || (metrics.verticalBias > 1.1F && metrics.totalDirectionChanges >= 4)) {
            return ArcaneContent.RUNES.indexOf(ArcaneContent.FIRE_RUNE);
        }
        if (metrics.straightness > 0.78F || metrics.verticalBias > 1.5F || metrics.closureRatio < 0.32F) {
            return ArcaneContent.RUNES.indexOf(ArcaneContent.LIGHT_RUNE);
        }
        return ArcaneContent.RUNES.indexOf(ArcaneContent.FORCE_RUNE);
    }

    private int detectShape(GlyphMetrics metrics) {
        if (metrics.closureRatio < 0.32F || metrics.aspectBalance > 0.8F) {
            return ArcaneContent.SHAPES.indexOf(ArcaneContent.BARRIER_SHAPE);
        }
        if (metrics.horizontalBias > 1.2F && metrics.yDirectionChanges >= 2) {
            return ArcaneContent.SHAPES.indexOf(ArcaneContent.WAVE_SHAPE);
        }
        if (metrics.sharpTurns >= 6 || metrics.totalDirectionChanges >= 7) {
            return ArcaneContent.SHAPES.indexOf(ArcaneContent.BURST_SHAPE);
        }
        return ArcaneContent.SHAPES.indexOf(ArcaneContent.BOLT_SHAPE);
    }

    private int detectModifier(GlyphMetrics metrics) {
        if (metrics.closureRatio < 0.34F) {
            return ArcaneContent.MODIFIERS.indexOf(ArcaneContent.STABILIZE_MODIFIER);
        }
        if (metrics.straightness > 0.82F && metrics.longestSide > 38.0F) {
            return ArcaneContent.MODIFIERS.indexOf(ArcaneContent.RANGE_MODIFIER);
        }
        if (metrics.horizontalBias > 1.0F && metrics.xDirectionChanges >= 2 && metrics.yDirectionChanges >= 2) {
            return ArcaneContent.MODIFIERS.indexOf(ArcaneContent.SPLIT_MODIFIER);
        }
        if (metrics.totalDirectionChanges >= 6 && metrics.sharpTurns >= 3) {
            return ArcaneContent.MODIFIERS.indexOf(ArcaneContent.AMPLIFY_MODIFIER);
        }
        if (metrics.totalDirectionChanges >= 4) {
            return ArcaneContent.MODIFIERS.indexOf(ArcaneContent.DURATION_MODIFIER);
        }
        return ArcaneContent.MODIFIERS.indexOf(ArcaneContent.HOMING_MODIFIER);
    }

    private record StrokePoint(int x, int y) {
    }

    private record GlyphMetrics(
            float width,
            float height,
            float longestSide,
            float horizontalBias,
            float verticalBias,
            float aspectBalance,
            float pathLength,
            float endpointDistance,
            float straightness,
            float closureRatio,
            int xDirectionChanges,
            int yDirectionChanges,
            int totalDirectionChanges,
            int sharpTurns) {

        static GlyphMetrics of(List<StrokePoint> points) {
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxY = Integer.MIN_VALUE;
            float pathLength = 0.0F;
            int xDirectionChanges = 0;
            int yDirectionChanges = 0;
            int sharpTurns = 0;
            int lastXDir = 0;
            int lastYDir = 0;

            for (StrokePoint point : points) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }

            for (int i = 1; i < points.size(); i++) {
                StrokePoint previous = points.get(i - 1);
                StrokePoint current = points.get(i);
                int dx = current.x - previous.x;
                int dy = current.y - previous.y;
                pathLength += Math.sqrt(dx * dx + dy * dy);

                int xDir = Integer.compare(dx, 0);
                int yDir = Integer.compare(dy, 0);
                if (xDir != 0 && lastXDir != 0 && xDir != lastXDir) {
                    xDirectionChanges++;
                }
                if (yDir != 0 && lastYDir != 0 && yDir != lastYDir) {
                    yDirectionChanges++;
                }
                if (xDir != 0) {
                    lastXDir = xDir;
                }
                if (yDir != 0) {
                    lastYDir = yDir;
                }

                if (i >= 2) {
                    StrokePoint before = points.get(i - 2);
                    int ax = previous.x - before.x;
                    int ay = previous.y - before.y;
                    int bx = current.x - previous.x;
                    int by = current.y - previous.y;
                    float aLen = (float) Math.sqrt(ax * ax + ay * ay);
                    float bLen = (float) Math.sqrt(bx * bx + by * by);
                    if (aLen > 0.1F && bLen > 0.1F) {
                        float dot = (ax * bx + ay * by) / (aLen * bLen);
                        if (dot < 0.35F) {
                            sharpTurns++;
                        }
                    }
                }
            }

            float width = Math.max(1.0F, maxX - minX);
            float height = Math.max(1.0F, maxY - minY);
            StrokePoint first = points.getFirst();
            StrokePoint last = points.getLast();
            float endpointDistance = (float) Math.sqrt(Math.pow(last.x - first.x, 2) + Math.pow(last.y - first.y, 2));
            float straightness = pathLength <= 0.001F ? 1.0F : endpointDistance / pathLength;
            float longestSide = Math.max(width, height);
            float closureRatio = longestSide <= 0.001F ? 1.0F : endpointDistance / longestSide;
            int totalDirectionChanges = xDirectionChanges + yDirectionChanges;

            return new GlyphMetrics(
                    width,
                    height,
                    longestSide,
                    width / Math.max(1.0F, height),
                    height / Math.max(1.0F, width),
                    1.0F - Math.abs(width - height) / Math.max(width, height),
                    pathLength,
                    endpointDistance,
                    straightness,
                    closureRatio,
                    xDirectionChanges,
                    yDirectionChanges,
                    totalDirectionChanges,
                    sharpTurns);
        }
    }
}
