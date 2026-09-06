package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleOperation;
import com.efkrdnz.magical.magic.SpaceTargetGroup;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class SpaceManipulationOverlay {
    private static final int OUTER_RADIUS = 98;
    private static final int INNER_RADIUS = 38;
    private static final int SELECTED_RADIUS = 110;
    private static final int LABEL_RADIUS = 69;
    private static boolean active;
    private static int activeWheel;
    private static int selectedCategory;
    private static int selectedOperation;
    private static int selectedTarget;

    private SpaceManipulationOverlay() {}

    public static void begin() {
        active = true;
        activeWheel = 0;
        selectedCategory = 0;
        selectedOperation = 0;
        selectedTarget = 0;
    }

    public static void finish() {
        if (active) {
            SpaceRuleCategory category = SpaceRuleCategory.values()[selectedCategory];
            SpaceRuleOperation operation = operations().get(selectedOperation);
            SpaceTargetGroup target = SpaceTargetGroup.values()[selectedTarget];
            MagicalNetwork.sendSpaceRule(category.ordinal(), operation.ordinal(), target.ordinal());
        }
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static boolean handleScroll(double scrollDeltaY) {
        if (!active || scrollDeltaY == 0.0D) {
            return false;
        }
        int direction = scrollDeltaY > 0.0D ? -1 : 1;
        if (activeWheel == 0) {
            selectedCategory = Math.floorMod(selectedCategory + direction, SpaceRuleCategory.values().length);
            selectedOperation = 0;
        } else if (activeWheel == 1) {
            selectedOperation = Math.floorMod(selectedOperation + direction, operations().size());
        } else {
            selectedTarget = Math.floorMod(selectedTarget + direction, SpaceTargetGroup.values().length);
        }
        return true;
    }

    public static boolean handleMouseButton(int button, int action) {
        if (!active || action != GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            activeWheel = Math.floorMod(activeWheel - 1, 3);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            activeWheel = Math.floorMod(activeWheel + 1, 3);
            return true;
        }
        return false;
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (!active) {
            return;
        }
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0x76030A12);
        int centerY = guiGraphics.guiHeight() / 2;
        int[] centers = wheelCenters(guiGraphics.guiWidth());
        drawWheel(guiGraphics, minecraft, centers[0], centerY, 0, categoryLabels(), selectedCategory, 0x88DFFF, Component.translatable("space.magical.wheel.category"));
        drawWheel(guiGraphics, minecraft, centers[1], centerY, 1, operationLabels(), selectedOperation, 0xB5F4FF, Component.translatable("space.magical.wheel.operation"));
        drawWheel(guiGraphics, minecraft, centers[2], centerY, 2, targetLabels(), selectedTarget, 0x63C7FF, Component.translatable("space.magical.wheel.target"));
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("space.magical.release_hint"), guiGraphics.guiWidth() / 2, centerY + 138, 0xD8F6FF);
    }

    private static int[] wheelCenters(int width) {
        int margin = SELECTED_RADIUS + 18;
        int minGap = SELECTED_RADIUS * 2 + 18;
        if (width >= minGap * 2 + margin * 2) {
            return new int[] { margin, width / 2, width - margin };
        }
        int spacing = Math.max(OUTER_RADIUS * 2 + 12, Math.min(250, width / 3));
        return new int[] { width / 2 - spacing, width / 2, width / 2 + spacing };
    }

    private static void drawWheel(GuiGraphics guiGraphics, Minecraft minecraft, int centerX, int centerY, int wheelIndex, List<Component> labels, int selected, int color, Component title) {
        boolean activeRing = wheelIndex == activeWheel;
        fillCircleRows(guiGraphics, centerX, centerY, SELECTED_RADIUS + 12, activeRing ? 0x44040A12 : 0x26040A12);
        fillCircleRows(guiGraphics, centerX, centerY, SELECTED_RADIUS + 2, activeRing ? 0xC80B1422 : 0x9C0B1422);
        fillRing(centerX, centerY, OUTER_RADIUS + 2, SELECTED_RADIUS, activeRing ? 0xAA000000 | color : 0x556A8FA5);
        fillRing(centerX, centerY, OUTER_RADIUS - 1, OUTER_RADIUS + 2, 0xE8000000 | color);
        fillRing(centerX, centerY, INNER_RADIUS - 5, INNER_RADIUS, activeRing ? 0xD8D8F6FF : 0x8FA7CAD8);
        float segment = 360.0F / labels.size();
        for (int i = 0; i < labels.size(); i++) {
            float centerAngle = i * segment;
            float startAngle = centerAngle - segment * 0.5F + 1.2F;
            float endAngle = centerAngle + segment * 0.5F - 1.2F;
            if (i == selected) {
                fillRingSlice(centerX, centerY, OUTER_RADIUS, SELECTED_RADIUS, startAngle, endAngle, 0xE8000000 | brighten(color, 0.22F));
            }
            int sliceColor = i == selected ? 0xDC000000 | brighten(color, 0.12F) : 0xBA102030;
            fillRingSlice(centerX, centerY, INNER_RADIUS, OUTER_RADIUS, startAngle, endAngle, sliceColor);
            fillRingSlice(centerX, centerY, OUTER_RADIUS - 8, OUTER_RADIUS, startAngle, endAngle, 0xF0000000 | color);
            double labelAngle = Math.toRadians(centerAngle - 90.0F);
            int labelX = centerX + (int) (Math.cos(labelAngle) * LABEL_RADIUS);
            int labelY = centerY + (int) (Math.sin(labelAngle) * LABEL_RADIUS);
            int labelWidth = i == selected ? 68 : 56;
            guiGraphics.fill(labelX - labelWidth / 2, labelY - 9, labelX + labelWidth / 2, labelY + 9, i == selected ? 0xD8162438 : 0x7E121B2A);
            guiGraphics.fill(labelX - labelWidth / 2, labelY + 7, labelX + labelWidth / 2, labelY + 9, 0xFF000000 | color);
            guiGraphics.drawCenteredString(minecraft.font, minecraft.font.plainSubstrByWidth(labels.get(i).getString(), i == selected ? 62 : 50), labelX + 1, labelY - 3, 0xAA050912);
            guiGraphics.drawCenteredString(minecraft.font, minecraft.font.plainSubstrByWidth(labels.get(i).getString(), i == selected ? 62 : 50), labelX, labelY - 4, i == selected ? 0xFFFFFF : 0xDCE7F5);
        }
        fillCircleRows(guiGraphics, centerX, centerY, INNER_RADIUS - 3, activeRing ? 0xF0101724 : 0xCC101724);
        fillRing(centerX, centerY, INNER_RADIUS - 4, INNER_RADIUS - 2, activeRing ? 0xFFD8F6FF : 0xAA8FB4C6);
        guiGraphics.drawCenteredString(minecraft.font, title, centerX, centerY - 128, activeRing ? color : 0x89A8B8);
        guiGraphics.drawCenteredString(minecraft.font, labels.get(selected), centerX, centerY - 4, activeRing ? 0xF4FCFF : 0xBFD7DD);
    }

    private static List<SpaceRuleOperation> operations() {
        return SpaceRuleOperation.forCategory(SpaceRuleCategory.values()[selectedCategory]);
    }

    private static List<Component> categoryLabels() {
        return java.util.Arrays.stream(SpaceRuleCategory.values()).<Component>map(category -> Component.translatable(category.translationKey())).toList();
    }

    private static List<Component> operationLabels() {
        return operations().stream().<Component>map(operation -> Component.translatable(operation.translationKey())).toList();
    }

    private static List<Component> targetLabels() {
        return java.util.Arrays.stream(SpaceTargetGroup.values()).<Component>map(target -> Component.translatable(target.translationKey())).toList();
    }

    private static void fillRingSlice(int centerX, int centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int color) {
        int steps = Math.max(8, Mth.ceil(Math.abs(endAngle - startAngle) / 12.0F));
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int step = 0; step < steps; step++) {
            float stepStart = Mth.lerp((float) step / steps, startAngle, endAngle);
            float stepEnd = Mth.lerp((float) (step + 1) / steps, startAngle, endAngle);
            addTriangle(builder, pointX(centerX, innerRadius, stepStart), pointY(centerY, innerRadius, stepStart), pointX(centerX, outerRadius, stepStart), pointY(centerY, outerRadius, stepStart), pointX(centerX, outerRadius, stepEnd), pointY(centerY, outerRadius, stepEnd), color);
            addTriangle(builder, pointX(centerX, innerRadius, stepStart), pointY(centerY, innerRadius, stepStart), pointX(centerX, outerRadius, stepEnd), pointY(centerY, outerRadius, stepEnd), pointX(centerX, innerRadius, stepEnd), pointY(centerY, innerRadius, stepEnd), color);
        }
        RenderType.gui().draw(builder.buildOrThrow());
    }

    private static void fillRing(int centerX, int centerY, float innerRadius, float outerRadius, int color) {
        fillRingSlice(centerX, centerY, innerRadius, outerRadius, 0.0F, 360.0F, color);
    }

    private static void fillCircleRows(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int width = Mth.floor(Math.sqrt((radius * radius) - (y * y)));
            guiGraphics.fill(centerX - width, centerY + y, centerX + width, centerY + y + 1, color);
        }
    }

    private static void addTriangle(BufferBuilder builder, float x1, float y1, float x2, float y2, float x3, float y3, int color) {
        builder.addVertex(x1, y1, 0.0F).setColor(color);
        builder.addVertex(x2, y2, 0.0F).setColor(color);
        builder.addVertex(x3, y3, 0.0F).setColor(color);
    }

    private static float pointX(int centerX, float radius, float angleDegrees) {
        return centerX + Mth.cos((angleDegrees - 90.0F) * Mth.DEG_TO_RAD) * radius;
    }

    private static float pointY(int centerY, float radius, float angleDegrees) {
        return centerY + Mth.sin((angleDegrees - 90.0F) * Mth.DEG_TO_RAD) * radius;
    }

    private static int brighten(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Mth.clamp((int) (r + (255 - r) * amount), 0, 255);
        g = Mth.clamp((int) (g + (255 - g) * amount), 0, 255);
        b = Mth.clamp((int) (b + (255 - b) * amount), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
