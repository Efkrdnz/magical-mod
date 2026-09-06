package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class MagicWheelOverlay {
    private static final float SECTOR_SLIDE = 0.18F;
    private static final double STEP_DEADZONE = 7.0D;
    private static final int STEP_COOLDOWN_TICKS = 7;
    private static final double HELD_SENSITIVITY = 0.0D;
    private static final int OUTER_RADIUS = 106;
    private static final int INNER_RADIUS = 42;
    private static final int LABEL_RADIUS = 74;
    private static final int SELECTED_RADIUS = 112;
    private static boolean active;
    private static boolean wheelKeyWasDown;
    private static boolean sensitivityAdjusted;
    private static float displaySpin;
    private static int stepCooldown;
    private static int selectedIndex = -1;
    private static ResourceLocation previewSkillId;
    private static ResourceLocation chosenSkillId;
    private static double originalSensitivity = -1.0D;

    private MagicWheelOverlay() {}

    private static void beginSelection(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.screen != null) {
            reset();
            return;
        }

        List<ResourceLocation> skills = wheelSkills();
        if (skills.isEmpty()) {
            reset();
            return;
        }

        active = true;
        selectedIndex = initialSelectedIndex(skills);
        displaySpin = selectedIndex * (360.0F / skills.size());
        stepCooldown = 0;
        previewSkillId = skills.get(selectedIndex);
        minecraft.mouseHandler.setIgnoreFirstMove();
        applyHeldSensitivity(minecraft);
    }

    private static void finishSelection() {
        if (active && previewSkillId != null) {
            chosenSkillId = previewSkillId;
        }
        reset();
    }

    public static void castChosenSkill() {
        if (active) {
            return;
        }

        if (chosenSkillId == null) {
            return;
        }
        if (isSubWheelParent(chosenSkillId)) {
            beginSubWheelParent(chosenSkillId);
            return;
        }
        MagicalNetwork.sendWheelCastRequest(chosenSkillId);
    }

    public static boolean handleScroll(double scrollDeltaY) {
        if (!active || scrollDeltaY == 0.0D) {
            return false;
        }

        List<ResourceLocation> skills = wheelSkills();
        if (skills.isEmpty()) {
            return true;
        }

        int direction = scrollDeltaY > 0.0D ? -1 : 1;
        selectedIndex = Math.floorMod(selectedIndex + direction, skills.size());
        previewSkillId = skills.get(selectedIndex);
        stepCooldown = STEP_COOLDOWN_TICKS;
        return true;
    }

    public static void tick(Minecraft minecraft, boolean wheelKeyDown) {
        if (minecraft.player == null || minecraft.screen != null) {
            reset();
            return;
        }

        List<ResourceLocation> skills = wheelSkills();
        if (skills.isEmpty()) {
            reset();
            return;
        }

        if (wheelKeyDown && !wheelKeyWasDown) {
            beginSelection(minecraft);
        } else if (!wheelKeyDown && wheelKeyWasDown) {
            finishSelection();
        }
        wheelKeyWasDown = wheelKeyDown;

        if (!active) {
            return;
        }

        stepSelection(minecraft, skills.size());
        if (selectedIndex >= 0 && selectedIndex < skills.size()) {
            previewSkillId = skills.get(selectedIndex);
        }
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (!active) {
            return;
        }

        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0x76030A12);

        List<ResourceLocation> skills = wheelSkills();
        if (skills.isEmpty()) {
            return;
        }

        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2;
        float segmentAngle = 360.0F / skills.size();
        if (selectedIndex >= 0 && selectedIndex < skills.size()) {
            previewSkillId = skills.get(selectedIndex);
        }
        slideDisplaySpinToSelected(segmentAngle);
        fillCircleRows(guiGraphics, centerX, centerY, SELECTED_RADIUS + 22, 0x44040A12);
        fillCircleRows(guiGraphics, centerX, centerY, SELECTED_RADIUS + 11, 0xCC0B1422);
        fillCircleRows(guiGraphics, centerX, centerY, OUTER_RADIUS + 1, 0xEE101827);
        fillRing(centerX, centerY, OUTER_RADIUS + 6, SELECTED_RADIUS + 2, 0xAAE7D7A2);
        fillRing(centerX, centerY, OUTER_RADIUS + 1, OUTER_RADIUS + 5, 0xD8F6E6B5);
        fillRing(centerX, centerY, INNER_RADIUS - 5, INNER_RADIUS + 1, 0xC8B9D6FF);
        for (int i = 0; i < skills.size(); i++) {
            MagicSkillDefinition skill = MagicContent.get(skills.get(i));
            if (skill == null) {
                continue;
            }
            boolean selected = i == selectedIndex;
            float centerAngle = displayedAngle(i, segmentAngle);
            float startAngle = centerAngle - (segmentAngle * 0.5F) + 1.2F;
            float endAngle = centerAngle + (segmentAngle * 0.5F) - 1.2F;
            int fill = selected ? brighten(skill.color(), 0.42F) : darken(skill.color(), 0.52F);
            if (selected) {
                fillRingSlice(centerX, centerY, OUTER_RADIUS, SELECTED_RADIUS, startAngle, endAngle, 0xF0000000 | brighten(skill.color(), 0.22F));
            }
            fillRingSlice(centerX, centerY, INNER_RADIUS, OUTER_RADIUS, startAngle, endAngle, 0xDC000000 | fill);
            fillRingSlice(centerX, centerY, OUTER_RADIUS - 10, OUTER_RADIUS, startAngle, endAngle, 0xFF000000 | skill.color());

            double labelAngle = Math.toRadians(centerAngle - 90.0F);
            int labelX = centerX + (int) (Math.cos(labelAngle) * LABEL_RADIUS);
            int labelY = centerY + (int) (Math.sin(labelAngle) * LABEL_RADIUS);
            int textColor = selected ? 0xFFF8FCFF : 0xDCE7F5;
            int labelWidth = selected ? 72 : 60;
            guiGraphics.fill(labelX - labelWidth / 2, labelY - 9, labelX + labelWidth / 2, labelY + 9, selected ? 0xD8162438 : 0x92121B2A);
            guiGraphics.fill(labelX - labelWidth / 2, labelY + 7, labelX + labelWidth / 2, labelY + 9, 0xFF000000 | skill.color());
            guiGraphics.drawCenteredString(
                minecraft.font,
                minecraft.font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), selected ? 64 : 52),
                labelX + 1,
                labelY - 3,
                0xAA050912
            );
            guiGraphics.drawCenteredString(
                minecraft.font,
                minecraft.font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), selected ? 64 : 52),
                labelX,
                labelY - 4,
                textColor
            );
        }
        guiGraphics.flush();

        fillCircleRows(guiGraphics, centerX, centerY, INNER_RADIUS - 2, 0xF0101724);
        fillRing(centerX, centerY, INNER_RADIUS - 3, INNER_RADIUS - 1, 0xFFE7D7A2);

        if (previewSkillId != null) {
            MagicSkillDefinition skill = MagicContent.get(previewSkillId);
            if (skill == null) {
                return;
            }
            guiGraphics.drawCenteredString(minecraft.font, Component.translatable(skill.nameKey()), centerX, centerY - 11, skill.color());
            guiGraphics.drawCenteredString(minecraft.font, Component.translatable("screen.magical.wheel_confirm_hint"), centerX, centerY + 5, 0xBFD7FF);
        }
    }

    private static List<ResourceLocation> wheelSkills() {
        return new ArrayList<>(ClientMagicState.get().wheelSkills());
    }

    private static int initialSelectedIndex(List<ResourceLocation> skills) {
        if (chosenSkillId != null) {
            int index = skills.indexOf(chosenSkillId);
            if (index >= 0) {
                return index;
            }
        }
        return 0;
    }

    private static void reset() {
        restoreSensitivity(Minecraft.getInstance());
        active = false;
        displaySpin = 0.0F;
        stepCooldown = 0;
        selectedIndex = -1;
        previewSkillId = null;
    }

    private static boolean isSubWheelParent(ResourceLocation skillId) {
        return MagicContent.GABRIEL.id().equals(skillId)
                || MagicContent.BLACK_FLAMES.id().equals(skillId)
                || MagicContent.SPATIAL_ARSENAL.id().equals(skillId)
                || MagicContent.SOUL_VOW.id().equals(skillId);
    }

    private static void beginSubWheelParent(ResourceLocation parentId) {
        if (MagicContent.GABRIEL.id().equals(parentId)) {
            SovereignAegisInput.beginWheelCast(parentId);
        } else if (MagicContent.BLACK_FLAMES.id().equals(parentId)) {
            BlackFlamesInput.beginWheelCast();
        } else if (MagicContent.SPATIAL_ARSENAL.id().equals(parentId)) {
            SpaceOffenseInput.beginWheelCast();
        } else if (MagicContent.SOUL_VOW.id().equals(parentId)) {
            SoulVowInput.beginWheelCast();
        }
    }

    private static void stepSelection(Minecraft minecraft, int count) {
        if (count <= 0) {
            return;
        }

        double mouseDelta = minecraft.mouseHandler.getXVelocity();
        if (stepCooldown > 0) {
            stepCooldown--;
            return;
        }
        if (Math.abs(mouseDelta) < STEP_DEADZONE) {
            return;
        }

        int direction = mouseDelta > 0.0D ? 1 : -1;
        selectedIndex = Math.floorMod(selectedIndex + direction, count);
        stepCooldown = STEP_COOLDOWN_TICKS;
    }

    private static void applyHeldSensitivity(Minecraft minecraft) {
        if (sensitivityAdjusted) {
            return;
        }

        originalSensitivity = minecraft.options.sensitivity().get();
        minecraft.options.sensitivity().set(HELD_SENSITIVITY);
        sensitivityAdjusted = true;
    }

    private static void restoreSensitivity(Minecraft minecraft) {
        if (!sensitivityAdjusted || originalSensitivity < 0.0D) {
            sensitivityAdjusted = false;
            originalSensitivity = -1.0D;
            return;
        }

        minecraft.options.sensitivity().set(originalSensitivity);
        sensitivityAdjusted = false;
        originalSensitivity = -1.0D;
    }

    private static float displayedAngle(int index, float segmentAngle) {
        return wrapDegrees((index * segmentAngle) - displaySpin);
    }

    private static void slideDisplaySpinToSelected(float segmentAngle) {
        if (selectedIndex < 0) {
            return;
        }

        float selectedSpin = selectedIndex * segmentAngle;
        displaySpin += wrapDegrees(selectedSpin - displaySpin) * SECTOR_SLIDE;
    }

    private static float wrapDegrees(float value) {
        return Mth.wrapDegrees(value);
    }

    private static void fillRingSlice(int centerX, int centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int color) {
        int steps = Math.max(8, Mth.ceil(Math.abs(endAngle - startAngle) / 10.0F));
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int step = 0; step < steps; step++) {
            float stepStart = Mth.lerp((float) step / steps, startAngle, endAngle);
            float stepEnd = Mth.lerp((float) (step + 1) / steps, startAngle, endAngle);

            float outerStartX = pointX(centerX, outerRadius, stepStart);
            float outerStartY = pointY(centerY, outerRadius, stepStart);
            float outerEndX = pointX(centerX, outerRadius, stepEnd);
            float outerEndY = pointY(centerY, outerRadius, stepEnd);
            float innerStartX = pointX(centerX, innerRadius, stepStart);
            float innerStartY = pointY(centerY, innerRadius, stepStart);
            float innerEndX = pointX(centerX, innerRadius, stepEnd);
            float innerEndY = pointY(centerY, innerRadius, stepEnd);

            addTriangle(builder, innerStartX, innerStartY, outerStartX, outerStartY, outerEndX, outerEndY, color);
            addTriangle(builder, innerStartX, innerStartY, outerEndX, outerEndY, innerEndX, innerEndY, color);
        }
        RenderType.gui().draw(builder.buildOrThrow());
    }

    private static void fillCircleRows(GuiGraphics guiGraphics, int centerX, int centerY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int width = Mth.floor(Math.sqrt((radius * radius) - (y * y)));
            guiGraphics.fill(centerX - width, centerY + y, centerX + width, centerY + y + 1, color);
        }
    }

    private static void fillRing(int centerX, int centerY, float innerRadius, float outerRadius, int color) {
        fillRingSlice(centerX, centerY, innerRadius, outerRadius, 0.0F, 360.0F, color);
    }

    private static void addTriangle(
        BufferBuilder builder,
        float x1,
        float y1,
        float x2,
        float y2,
        float x3,
        float y3,
        int color
    ) {
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

    private static int darken(int color, float amount) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Mth.clamp((int) (r * (1.0F - amount)), 0, 255);
        g = Mth.clamp((int) (g * (1.0F - amount)), 0, 255);
        b = Mth.clamp((int) (b * (1.0F - amount)), 0, 255);
        return (r << 16) | (g << 8) | b;
    }
}
