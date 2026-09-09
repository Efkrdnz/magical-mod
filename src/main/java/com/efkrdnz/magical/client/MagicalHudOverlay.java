package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The persistent top-left magic HUD: mana/barrier bars, proficiency progress, wheel count,
 * loadout slots with bound keys and live cooldowns, plus the conditional passive/sin gauge
 * column (Greed hoard, Sloth stillness, Mana Charge, Gluttony cooldown, Wrath, Pride).
 */
public final class MagicalHudOverlay {
    private static final int PANEL_X = 6;
    private static final int PANEL_Y = 6;
    private static final int PANEL_W = 168;
    private static final int MAIN_H = 44;
    private static final int ACCENT = 0x5FD4FF;
    private static final int GOLD = 0xF7D774;

    private MagicalHudOverlay() {}

    public static void render(GuiGraphics g, Minecraft minecraft, PlayerMagicState state) {
        Font font = minecraft.font;
        int x = PANEL_X;
        int y = PANEL_Y;

        // --- Main panel: mana, barrier, proficiency, wheel ---
        panel(g, x, y, x + PANEL_W, y + MAIN_H, ACCENT);
        boolean greedVault = state.hasPassive(MagicPassiveContent.SIN_GREED.id());
        int barX = x + 6;
        int manaBarW = greedVault ? 110 : 156;
        bar(g, barX, y + 5, manaBarW, 9, fraction(state.mana(), state.maxMana()), 0xFF58C8F5, 0xFF1D5D86);
        g.drawString(font, state.mana() + "/" + state.maxMana(), barX + 4, y + 6, 0xFFF2FBFF, false);
        if (greedVault) {
            chip(g, font, x + 120, y + 4, 42, 11, "V " + state.manaVault(), 0xD7F75B);
        }
        bar(g, barX, y + 16, 156, 9, fraction(state.barrier(), state.maxBarrier()), 0xFF7AF1FF, 0xFF25707E);
        g.drawString(font, state.barrier() + "/" + state.maxBarrier(), barX + 4, y + 17, 0xFFE8FBFF, false);

        chip(g, font, x + 6, y + 29, 34, 11, "Lv " + state.proficiencyLevel(), GOLD);
        int xp = state.proficiencyXp();
        int into = MagicContent.xpIntoLevel(xp);
        int toNext = MagicContent.xpForNextLevel(xp);
        float xpFraction = toNext <= 0 ? 1.0F : into / (float) Math.max(1, into + toNext);
        bar(g, x + 46, y + 32, 66, 5, xpFraction, 0xFFF3CE63, 0xFF87681F);
        // The active loadout by name: with four keys that change meaning, which set you are on is
        // the single most useful thing the HUD can say.
        chip(g, font, x + PANEL_W - 62, y + 29, 56, 11,
                font.plainSubstrByWidth(state.activeLoadout().name(), 50), 0xBFD7FF);

        // --- Loadout slots with bound keys and cooldowns ---
        int slotY = y + MAIN_H + 4;
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            int slotX = x + slot * 36;
            drawLoadoutSlot(g, font, minecraft, state, slot, slotX, slotY);
        }

        // --- Conditional gauge column ---
        int gaugeY = slotY + 25;
        for (Gauge gauge : collectGauges(state)) {
            drawGauge(g, font, x, gaugeY, gauge);
            gaugeY += 15;
        }
    }

    private static void drawLoadoutSlot(GuiGraphics g, Font font, Minecraft minecraft, PlayerMagicState state, int slot, int slotX, int slotY) {
        ResourceLocation skillId = state.equippedSkill(slot);
        MagicSkillDefinition skill = skillId == null ? null : MagicContent.get(skillId);
        int accent = skill == null ? 0x33465C : skill.color();
        g.fill(slotX - 1, slotY - 1, slotX + 33, slotY + 21, 0xE0060B14);
        g.fillGradient(slotX, slotY, slotX + 32, slotY + 20, 0xD2141F31, 0xD20C1322);
        g.fill(slotX, slotY, slotX + 32, slotY + 1, withAlpha(accent, skill == null ? 0x44 : 0x99));
        String key = font.plainSubstrByWidth(MagicalKeyMappings.CAST_SLOTS[slot].getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT), 12);
        g.drawString(font, key, slotX + 3, slotY + 3, GOLD | 0xFF000000, false);
        g.fill(slotX + 2, slotY + 15, slotX + 30, slotY + 18, skill == null ? 0xFF0A0F1B : 0xFF000000 | skill.color());
        if (skill == null) {
            g.drawString(font, "-", slotX + 22, slotY + 3, 0xFF56647D, false);
            return;
        }
        int cooldown = state.cooldown(slot);
        if (cooldown > 0) {
            g.fill(slotX, slotY, slotX + 32, slotY + 20, 0x99060A12);
            String seconds = Math.max(1, (cooldown + 19) / 20) + "s";
            g.drawString(font, seconds, slotX + 30 - font.width(seconds), slotY + 3, 0xFFF38BA8, false);
        }
    }

    private static List<Gauge> collectGauges(PlayerMagicState state) {
        List<Gauge> gauges = new ArrayList<>();
        if (state.isSinEnabled(MagicPassiveContent.SIN_GREED.id()) && state.greedHoard() > 0) {
            gauges.add(new Gauge("Greed +" + state.greedHoard(), 0xD7F75B, state.greedHoard() / (float) PlayerMagicState.MAX_GREED_HOARD));
        }
        if (state.isSinEnabled(MagicPassiveContent.SIN_SLOTH.id()) && state.slothStillness() > 0) {
            String rested = state.restedStillnessTicks() > 0 ? " R" : "";
            gauges.add(new Gauge("Still " + Math.round(state.slothStillness() * 100.0F / PlayerMagicState.MAX_SIN_GAUGE) + "%" + rested,
                    0x8EA0C8, state.slothStillness() / (float) PlayerMagicState.MAX_SIN_GAUGE));
        }
        if (state.manaChargeTicks() > 0 && state.manaChargeLevel() > 0) {
            gauges.add(new Gauge("Charge L" + state.manaChargeLevel(), 0x7FEFD4, Math.min(1.0F, state.manaChargeTicks() / 1200.0F)));
        }
        if (state.isSinEnabled(MagicPassiveContent.SIN_GLUTTONY.id()) && state.gluttonyCooldownTicks() > 0) {
            gauges.add(new Gauge("Glut CD " + seconds(state.gluttonyCooldownTicks()), 0xB878FF, Math.min(1.0F, state.gluttonyCooldownTicks() / 320.0F)));
        }
        if (state.isSinEnabled(MagicPassiveContent.SIN_WRATH.id()) && state.wrathGauge() > 0) {
            gauges.add(new Gauge("Wrath " + Math.round(state.wrathGauge() * 100.0F / PlayerMagicState.MAX_SIN_GAUGE) + "%",
                    0xFF3C38, state.wrathGauge() / (float) PlayerMagicState.MAX_SIN_GAUGE));
        }
        if (state.isSinEnabled(MagicPassiveContent.SIN_PRIDE.id()) && state.prideGauge() > 0) {
            gauges.add(new Gauge("Pride " + Math.round(state.prideGauge() * 100.0F / PlayerMagicState.MAX_SIN_GAUGE) + "%",
                    0xFFD166, state.prideGauge() / (float) PlayerMagicState.MAX_SIN_GAUGE));
        }
        return gauges;
    }

    private static void drawGauge(GuiGraphics g, Font font, int x, int y, Gauge gauge) {
        g.fill(x - 1, y - 1, x + PANEL_W + 1, y + 13, 0xC8050A12);
        g.fillGradient(x, y, x + PANEL_W, y + 12, 0xC0121C2D, 0xC00B121F);
        g.fill(x, y, x + 3, y + 12, 0xFF000000 | gauge.color());
        g.drawString(font, gauge.label(), x + 8, y + 2, 0xFF000000 | gauge.color(), false);
        bar(g, x + PANEL_W - 52, y + 4, 46, 4, gauge.progress(), 0xFF000000 | gauge.color(), 0xFF000000 | dim(gauge.color()));
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1, int accent) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xE0060B14);
        g.fillGradient(x0, y0, x1, y1, 0xD2111C2E, 0xD20A101C);
        g.fill(x0, y0, x1, y0 + 1, withAlpha(accent, 0x88));
    }

    private static void bar(GuiGraphics g, int x, int y, int width, int height, float fraction, int bright, int dark) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xC0050A12);
        g.fill(x, y, x + width, y + height, 0xB80B1422);
        int fillWidth = Math.round(width * Mth.clamp(fraction, 0.0F, 1.0F));
        if (fillWidth > 0) {
            g.fillGradient(x, y, x + fillWidth, y + height, bright, dark);
            g.fill(x, y, x + fillWidth, y + 1, 0x5CFFFFFF);
        }
    }

    private static void chip(GuiGraphics g, Font font, int x, int y, int width, int height, String text, int color) {
        g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xE0060B14);
        g.fillGradient(x, y, x + width, y + height, 0xD2162134, 0xD20D1524);
        g.fill(x, y + height - 1, x + width, y + height, withAlpha(color, 0xAA));
        g.drawString(font, font.plainSubstrByWidth(text, width - 4), x + 3, y + 2, 0xFF000000 | color, false);
    }

    private static float fraction(int value, int max) {
        return max <= 0 ? 0.0F : value / (float) max;
    }

    private static String seconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20) + "s";
    }

    private static int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0xFFFFFF);
    }

    private static int dim(int color) {
        int red = ((color >> 16) & 0xFF) / 3;
        int green = ((color >> 8) & 0xFF) / 3;
        int blue = (color & 0xFF) / 3;
        return (red << 16) | (green << 8) | blue;
    }

    private record Gauge(String label, int color, float progress) {}
}
