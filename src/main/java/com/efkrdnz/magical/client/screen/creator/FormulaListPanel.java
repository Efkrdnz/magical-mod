package com.efkrdnz.magical.client.screen.creator;

import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.FORMULA_EMBLEM_HALF;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.FORMULA_ROWS;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.FORMULA_ROW_STRIDE;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.FORMULA_W;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.FORMULA_X;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.FORMULA_Y;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.clampScroll;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.formulaChip;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.formulaEmblem;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.formulaHint;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.formulaRow;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.formulaRowAt;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.formulaScrollbar;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.formulaText;
import static com.efkrdnz.magical.client.screen.creator.SpellCreatorLayout.hit;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.EmblemPainter;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicFusionService;
import com.efkrdnz.magical.magic.MagicFusionService.FormulaState;
import com.efkrdnz.magical.magic.MagicFusionService.Status;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * The Formulas tab: every formula on one row with its output, its two ingredients and where it
 * stands for this player, sorted ready first. Reference and shortcut at once: the rows tell the
 * player what to go and find, and a Ready row loads itself into the Create tab when clicked.
 */
final class FormulaListPanel {
    /** The green the codex has always used for something already created. */
    static final int CREATED_GREEN = 0xFFA6E3A1;
    /** An ingredient the player does not own, in the formula line and the status text. */
    static final int MISSING_RED = MagicalGuiStyle.ACCENT_BLOOD;

    private List<FormulaState> rows = List.of();
    private int scroll;

    void refresh(PlayerMagicState state) {
        rows = MagicFusionService.formulas(state);
        scroll = clampScroll(scroll, rows.size(), FORMULA_ROWS);
    }

    /** The chrome pass: row plates and status chips, with every emblem queued on the painter. */
    void paint(GuiGraphics g, EmblemPainter emblems, int x0, int y0, double lx, double ly) {
        int visible = Math.min(FORMULA_ROWS, rows.size() - scroll);
        int hovered = formulaRowAt(lx, ly);
        for (int row = 0; row < visible; row++) {
            FormulaState formula = rows.get(scroll + row);
            Rect rect = formulaRow(row);
            int accent = accent(formula.status());
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), row == hovered, accent);
            Rect chip = formulaChip(row);
            g.fill(x0 + chip.x(), y0 + chip.y(), x0 + chip.right(), y0 + chip.bottom(), MagicalGuiStyle.withAlpha(accent, 0x2E));
            g.fill(x0 + chip.x(), y0 + chip.y(), x0 + chip.right(), y0 + chip.y() + 1, MagicalGuiStyle.withAlpha(accent, 0xAA));
            g.fill(x0 + chip.x(), y0 + chip.bottom() - 1, x0 + chip.right(), y0 + chip.bottom(), MagicalGuiStyle.withAlpha(accent, 0x66));
            Rect emblem = formulaEmblem(row);
            boolean lit = formula.status() == Status.READY || formula.status() == Status.CREATED;
            emblems.add(formula.recipe().outputSkill(), x0 + emblem.x() + emblem.w() / 2.0F, y0 + emblem.y() + emblem.h() / 2.0F,
                    FORMULA_EMBLEM_HALF, lit ? 1.0F : 0.55F, !lit);
        }
        Rect bar = formulaScrollbar();
        MagicalGuiStyle.scrollbar(g, x0 + bar.x() + 1, y0 + bar.y(), bar.h(), rows.size(), FORMULA_ROWS, scroll);
    }

    /** The text pass: names, the two ingredients, the chip labels and the footer hint. */
    void text(GuiGraphics g, Font font, int x0, int y0, PlayerMagicState state) {
        int visible = Math.min(FORMULA_ROWS, rows.size() - scroll);
        for (int row = 0; row < visible; row++) {
            FormulaState formula = rows.get(scroll + row);
            Rect text = formulaText(row);
            int nameColor = formula.status() == Status.CREATED ? CREATED_GREEN : MagicalGuiStyle.TEXT_PRIMARY;
            g.drawString(font, font.plainSubstrByWidth(outputName(formula).getString(), text.w()),
                    x0 + text.x(), y0 + text.y(), nameColor, false);
            g.drawString(font, ingredientsLine(formula, state), x0 + text.x(), y0 + text.y() + 11, MagicalGuiStyle.TEXT_MUTED, false);
            Rect chip = formulaChip(row);
            String label = font.plainSubstrByWidth(chipLabel(formula).getString(), chip.w() - 6);
            g.drawCenteredString(font, label, x0 + chip.x() + chip.w() / 2, y0 + chip.y() + 3, accent(formula.status()));
        }
        Rect hint = formulaHint();
        g.drawString(font, Component.translatable("screen.magical.creator.formula_hint"),
                x0 + hint.x(), y0 + hint.y(), MagicalGuiStyle.TEXT_MUTED, false);
    }

    /** The full story of the hovered row: output, status, requirement and what the formula costs. */
    List<Component> tooltipAt(double lx, double ly) {
        FormulaState formula = rowAt(lx, ly);
        if (formula == null) {
            return List.of();
        }
        List<Component> lines = new ArrayList<>(4);
        lines.add(outputName(formula).copy().withColor(accent(formula.status()) & 0xFFFFFF));
        lines.add(statusLine(formula).copy().withColor(accent(formula.status()) & 0xFFFFFF));
        lines.add(formula.recipe().requirement());
        lines.add(formula.recipe().lossWarning());
        return lines;
    }

    FormulaState rowAt(double lx, double ly) {
        int row = formulaRowAt(lx, ly);
        return row < 0 || scroll + row >= rows.size() ? null : rows.get(scroll + row);
    }

    boolean mouseScrolled(double lx, double ly, double amount) {
        Rect area = new Rect("formula list", FORMULA_X, FORMULA_Y, FORMULA_W, FORMULA_ROWS * FORMULA_ROW_STRIDE);
        if (!hit(area, lx, ly)) {
            return false;
        }
        scroll = clampScroll(scroll - (int) Math.signum(amount), rows.size(), FORMULA_ROWS);
        return true;
    }

    // ---- shared with the result card ----------------------------------------------------------------

    static int accent(Status status) {
        return switch (status) {
            case READY -> MagicalGuiStyle.ACCENT_GOLD;
            case MISSING -> 0xFF000000 | MagicalGuiStyle.TEXT_MUTED;
            case LOCKED -> MagicalGuiStyle.ACCENT_VIOLET;
            case CREATED -> CREATED_GREEN;
        };
    }

    static Component outputName(FormulaState formula) {
        return skillName(formula.recipe().outputSkill());
    }

    static Component skillName(ResourceLocation id) {
        MagicSkillDefinition skill = MagicContent.get(id);
        return skill == null ? Component.literal(id.getPath()) : Component.translatable(skill.nameKey());
    }

    /** The status in full: which inputs are missing, which class is needed. */
    static Component statusLine(FormulaState formula) {
        return switch (formula.status()) {
            case READY -> Component.translatable("screen.magical.creator.status.ready");
            case MISSING -> Component.translatable("screen.magical.creator.status.missing", missingNames(formula));
            case LOCKED -> Component.translatable("screen.magical.creator.status.locked", className(formula.recipe().requiredClass()));
            case CREATED -> Component.translatable("screen.magical.creator.status.created");
        };
    }

    /** The chip is a hundred and thirty pixels: the missing names go in the row and the tooltip instead. */
    private static Component chipLabel(FormulaState formula) {
        return formula.status() == Status.MISSING
                ? Component.translatable("screen.magical.creator.status.missing_short")
                : statusLine(formula);
    }

    private static Component missingNames(FormulaState formula) {
        MutableComponent names = Component.empty();
        for (int i = 0; i < formula.missing().size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(skillName(formula.missing().get(i)));
        }
        return names;
    }

    static Component className(ResourceLocation classId) {
        MagicalClassDefinition definition = MagicalClasses.get(classId);
        return definition == null ? Component.literal(classId.getPath()) : Component.translatable(definition.nameKey());
    }

    private static Component ingredientsLine(FormulaState formula, PlayerMagicState state) {
        MutableComponent line = Component.empty();
        line.append(inputName(formula.recipe().firstInputId(), state));
        line.append(Component.literal(" + ").withColor(MagicalGuiStyle.TEXT_MUTED));
        line.append(inputName(formula.recipe().secondInputId(), state));
        return line;
    }

    private static Component inputName(ResourceLocation id, PlayerMagicState state) {
        int color = state.hasUnlocked(id) ? MagicalGuiStyle.TEXT_PRIMARY : MISSING_RED & 0xFFFFFF;
        return skillName(id).copy().withColor(color);
    }
}
