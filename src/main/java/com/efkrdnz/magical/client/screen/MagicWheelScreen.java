package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class MagicWheelScreen extends Screen {
    private int hoveredIndex = -1;

    public MagicWheelScreen() {
        super(Component.translatable("screen.magical.wheel"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        List<ResourceLocation> skills = wheelSkills();
        int centerX = width / 2;
        int centerY = height / 2;
        int radius = 86;
        hoveredIndex = resolveHovered(mouseX, mouseY, skills.size(), centerX, centerY, radius);

        guiGraphics.fill(centerX - 42, centerY - 16, centerX + 42, centerY + 16, 0xCC0F1724);
        guiGraphics.drawCenteredString(font, title, centerX, centerY - 28, 0xF4F9FF);

        for (int i = 0; i < skills.size(); i++) {
            MagicSkillDefinition skill = MagicContent.get(skills.get(i));
            double angle = (-Math.PI / 2D) + (Math.PI * 2D * i / skills.size());
            int x = centerX + (int) (Math.cos(angle) * radius);
            int y = centerY + (int) (Math.sin(angle) * radius);
            int color = i == hoveredIndex ? 0xFF2D5A74 : 0xDD162130;
            guiGraphics.fill(x - 34, y - 12, x + 34, y + 12, color);
            guiGraphics.fill(x - 34, y + 10, x + 34, y + 12, 0xFF000000 | skill.color());
            guiGraphics.drawCenteredString(font, font.plainSubstrByWidth(Component.translatable(skill.nameKey()).getString(), 62), x, y - 4, 0xF4F9FF);
        }

        if (hoveredIndex >= 0 && hoveredIndex < skills.size()) {
            MagicSkillDefinition skill = MagicContent.get(skills.get(hoveredIndex));
            guiGraphics.drawCenteredString(font, Component.translatable(skill.nameKey()), centerX, centerY - 6, skill.color());
            guiGraphics.drawCenteredString(font, Component.translatable(skill.descriptionKey()), centerX, centerY + 6, 0xBFD7FF);
        } else {
            guiGraphics.drawCenteredString(font, Component.translatable("screen.magical.wheel_hint"), centerX, centerY - 2, 0xBFD7FF);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<ResourceLocation> skills = wheelSkills();
        if (hoveredIndex >= 0 && hoveredIndex < skills.size()) {
            MagicalNetwork.sendWheelCastRequest(skills.get(hoveredIndex));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<ResourceLocation> wheelSkills() {
        return new ArrayList<>(ClientMagicState.get().wheelSkills());
    }

    private static int resolveHovered(int mouseX, int mouseY, int count, int centerX, int centerY, int radius) {
        if (count == 0) {
            return -1;
        }
        for (int i = 0; i < count; i++) {
            double angle = (-Math.PI / 2D) + (Math.PI * 2D * i / count);
            int x = centerX + (int) (Math.cos(angle) * radius);
            int y = centerY + (int) (Math.sin(angle) * radius);
            if (mouseX >= x - 34 && mouseX < x + 34 && mouseY >= y - 12 && mouseY < y + 12) {
                return i;
            }
        }
        return -1;
    }
}
