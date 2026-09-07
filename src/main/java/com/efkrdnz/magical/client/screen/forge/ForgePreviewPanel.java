package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeIds;
import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.ForgeRuneCosts;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.chain.ForgeChainGrammar;
import com.efkrdnz.magical.forge.chain.ForgeError;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.chain.ForgeMaterial;
import com.efkrdnz.magical.forge.chain.ForgeRecipe;
import com.efkrdnz.magical.forge.chain.ForgeRules;
import com.efkrdnz.magical.forge.chain.ForgeValidation;
import com.efkrdnz.magical.forge.chain.RecognizedGlyph;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * What the drawn chain would produce, and why it would be refused. The preview runs the same
 * grammar and the same arithmetic the server does, so the panel is a prediction rather than a
 * second rule set; the server's reply always wins.
 */
public final class ForgePreviewPanel {

    /** A refusal the client can foresee, with the message to show under the Inscribe button. */
    public record PredictedError(ForgeError error, Component message) {
    }

    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 6;

    private final ForgePreviewText provider;

    public ForgePreviewPanel(ForgePreviewText provider) {
        if (provider == null) {
            throw new IllegalArgumentException("the preview panel needs a text provider");
        }
        this.provider = provider;
    }

    // --- rendering ---------------------------------------------------------------------------

    /** Draws the panel body between the given corners. */
    public void render(GuiGraphics graphics, Font font, int x0, int y0, int x1, int y1,
            List<RecognizedGlyph> chain, int maxMana) {
        MagicalGuiStyle.panel(graphics, x0, y0, x1, y1, MagicalGuiStyle.ACCENT_GOLD);
        MagicalGuiStyle.sectionLabel(graphics, font, x0 + PADDING, y0 + PADDING,
                Component.translatable("screen.magical.forge_preview"), MagicalGuiStyle.ACCENT_GOLD);
        int textX = x0 + PADDING;
        int textY = y0 + PADDING + 16;
        int width = x1 - x0 - PADDING * 2;
        ForgeValidation validation = chain.isEmpty() ? null : ForgeChainGrammar.validate(chain);
        if (!(validation instanceof ForgeValidation.Valid valid)) {
            drawWrapped(graphics, font, emptyOrError(validation), textX, textY, width, MagicalGuiStyle.TEXT_MUTED);
            return;
        }
        drawRecipe(graphics, font, valid.recipe(), textX, textY, width, maxMana);
    }

    private static Component emptyOrError(ForgeValidation validation) {
        if (validation instanceof ForgeValidation.Invalid invalid) {
            return Component.translatable(invalid.error().langKey(), invalid.argument());
        }
        return Component.translatable("screen.magical.forge_preview_empty");
    }

    private void drawRecipe(GuiGraphics graphics, Font font, ForgeRecipe recipe,
            int x, int y, int width, int maxMana) {
        Optional<ForgeRuneCosts> totalsResult = ForgeRuneCosts.of(recipe);
        if (totalsResult.isEmpty()) {
            // Same condition checkMana() gates on (an unresolvable modifier or temper id): show the
            // same BAD_PAYLOAD error instead of a quality/cost line computed from fabricated zeros.
            drawWrapped(graphics, font, Component.translatable(ForgeError.BAD_PAYLOAD.langKey()),
                    x, y, width, MagicalGuiStyle.TEXT_MUTED);
            return;
        }
        ForgeRuneCosts totals = totalsResult.get();
        int quality = ForgeRules.quality(recipe.meanGlyphQuality(), totals.stability());
        int cost = ForgeRules.manaCost(recipe.grade(), totals.mana(), maxMana);
        int percent = maxMana > 0 ? Math.round(cost * 100f / maxMana) : 0;

        String heading = gradeName(recipe.grade()).getString() + " " + glyphName(recipe.element()).getString();
        graphics.drawString(font, font.plainSubstrByWidth(heading, width), x, y, elementColor(recipe.element()), false);
        int lineY = y + LINE_HEIGHT;
        lineY = drawLine(graphics, font, joined(recipe.forms()), x, lineY, width, MagicalGuiStyle.TEXT_PRIMARY);
        lineY = drawLine(graphics, font, modifierLine(recipe), x, lineY, width, MagicalGuiStyle.TEXT_PRIMARY);
        lineY = drawLine(graphics, font, temperLine(recipe, quality), x, lineY, width, MagicalGuiStyle.TEXT_PRIMARY);
        lineY = drawLine(graphics, font, Component.translatable("screen.magical.forge_quality", quality),
                x, lineY, width, MagicalGuiStyle.ACCENT_GOLD);
        lineY = drawLine(graphics, font, Component.translatable("screen.magical.forge_cost_percent", cost, percent),
                x, lineY, width, MagicalGuiStyle.ACCENT_GOLD);
        lineY = drawLine(graphics, font,
                Component.translatable("screen.magical.forge_slots", recipe.grade().formSlots(),
                        recipe.grade().modifierSlots()),
                x, lineY, width, MagicalGuiStyle.TEXT_MUTED);
        for (Component line : provider.statLines(recipe, quality)) {
            lineY = drawLine(graphics, font, line, x, lineY, width, MagicalGuiStyle.TEXT_MUTED);
        }
    }

    private static int drawLine(GuiGraphics graphics, Font font, Component text,
            int x, int y, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), width), x, y, color, false);
        return y + LINE_HEIGHT;
    }

    private static void drawWrapped(GuiGraphics graphics, Font font, Component text,
            int x, int y, int width, int color) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, width)) {
            graphics.drawString(font, line, x, lineY, color, false);
            lineY += LINE_HEIGHT;
        }
    }

    private static Component modifierLine(ForgeRecipe recipe) {
        return recipe.modifiers().isEmpty()
                ? Component.translatable("tooltip.magical.forge.no_modifiers")
                : Component.translatable("tooltip.magical.forge.modifiers", joined(recipe.modifiers()));
    }

    private static Component temperLine(ForgeRecipe recipe, int quality) {
        return recipe.temper()
                .map(ForgePreviewPanel::glyphName)
                .orElseGet(() -> Component.translatable("tooltip.magical.forge.no_temper", quality));
    }

    private static Component joined(List<String> ids) {
        String separator = Component.translatable("tooltip.magical.forge.chain_sep").getString();
        StringBuilder text = new StringBuilder();
        for (String id : ids) {
            if (text.length() > 0) {
                text.append(separator);
            }
            text.append(glyphName(id).getString());
        }
        return Component.literal(text.toString());
    }

    // --- gate prediction ----------------------------------------------------------------------

    /**
     * The first refusal the client can foresee, in the order the server checks them. Cooldown and
     * mana are predictions like the rest: the screen still submits and lets the server answer.
     */
    public Optional<PredictedError> predict(
            List<RecognizedGlyph> chain, ItemStack weapon, PlayerMagicState state, long gameTime) {
        if (weapon.isEmpty()) {
            return failure(ForgeError.NO_WEAPON);
        }
        if (!ForgeMaterials.isForgeable(weapon)) {
            return failure(ForgeError.NOT_FORGEABLE);
        }
        if (!state.hasClass(MagicalClasses.BLACKSMITH)) {
            return failure(ForgeError.CLASS_REQUIRED);
        }
        ForgeValidation validation = ForgeChainGrammar.validate(chain);
        if (validation instanceof ForgeValidation.Invalid invalid) {
            return failure(invalid.error(), invalid.argument());
        }
        ForgeRecipe recipe = ((ForgeValidation.Valid) validation).recipe();
        if (recipe.grade() == ForgeGrade.DIVINE && !state.hasClass(MagicalClasses.DIVINESMITH)) {
            return failure(ForgeError.DIVINESMITH_REQUIRED);
        }
        Optional<ForgedWeapon> existing = ForgedWeapons.get(weapon);
        Optional<PredictedError> gradeFailure = checkGrade(weapon, recipe.grade(), existing);
        if (gradeFailure.isPresent()) {
            return gradeFailure;
        }
        Optional<PredictedError> cooldown = checkCooldown(existing, gameTime);
        return cooldown.isPresent() ? cooldown : checkMana(recipe, state);
    }

    private static Optional<PredictedError> checkGrade(
            ItemStack weapon, ForgeGrade grade, Optional<ForgedWeapon> existing) {
        ForgeMaterial material = ForgeMaterials.detect(weapon);
        Optional<ForgeError> error = ForgeRules.checkGrade(material, grade, existing.map(ForgedWeapon::grade));
        if (error.isEmpty()) {
            return Optional.empty();
        }
        if (error.get() != ForgeError.MATERIAL_CAP) {
            return failure(error.get());
        }
        return Optional.of(new PredictedError(ForgeError.MATERIAL_CAP,
                Component.translatable("screen.magical.forge_material_cap",
                        Component.translatable("forge.magical.material." + material.serializedName()),
                        gradeName(material.maxGrade()))));
    }

    private static Optional<PredictedError> checkCooldown(Optional<ForgedWeapon> existing, long gameTime) {
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        ForgedWeapon weapon = existing.get();
        long remaining = ForgeRules.remainingCooldown(weapon.forgedAtGameTime(), weapon.grade(), gameTime);
        return remaining <= 0
                ? Optional.empty()
                : failure(ForgeError.COOLDOWN, (int) Math.ceil(remaining / 20.0));
    }

    private static Optional<PredictedError> checkMana(ForgeRecipe recipe, PlayerMagicState state) {
        Optional<ForgeRuneCosts> totals = ForgeRuneCosts.of(recipe);
        if (totals.isEmpty()) {
            return failure(ForgeError.BAD_PAYLOAD);
        }
        int cost = ForgeRules.manaCost(recipe.grade(), totals.get().mana(), state.maxMana());
        return state.mana() < cost ? failure(ForgeError.NO_MANA, cost) : Optional.empty();
    }

    private static Optional<PredictedError> failure(ForgeError error) {
        return failure(error, 0);
    }

    private static Optional<PredictedError> failure(ForgeError error, int argument) {
        return Optional.of(new PredictedError(error, Component.translatable(error.langKey(), argument)));
    }

    // --- shared arithmetic --------------------------------------------------------------------

    static Component glyphName(String id) {
        return Component.translatable("forge.magical.glyph." + id);
    }

    static Component gradeName(ForgeGrade grade) {
        return Component.translatable("forge.magical.grade." + grade.serializedName());
    }

    private static int elementColor(String element) {
        return ForgeElements.get(ForgeIds.id(element))
                .map(definition -> 0xFF000000 | definition.primaryColor())
                .orElse(MagicalGuiStyle.TEXT_PRIMARY);
    }
}
