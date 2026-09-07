package com.efkrdnz.magical.client.screen.forge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeForms;
import com.efkrdnz.magical.forge.ForgeIds;
import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ForgeModifiers;
import com.efkrdnz.magical.forge.ForgeSpecials;
import com.efkrdnz.magical.forge.ForgeTempers;
import com.efkrdnz.magical.forge.FormDefinition;
import com.efkrdnz.magical.forge.ModifierDefinition;
import com.efkrdnz.magical.forge.TemperDefinition;
import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.art.ForgeArt;
import com.efkrdnz.magical.forge.chain.ForgeRecipe;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.FormStats;
import com.efkrdnz.magical.forge.strike.TemperStats;

import net.minecraft.network.chat.Component;

/**
 * The real preview provider: what the drawn chain would actually swing like. Every number comes out
 * of {@link ForgeStrikeMath} and the forge registries, the same arithmetic the server runs, so the
 * panel is a prediction of the server's answer rather than a second rule set.
 *
 * <p>Two things the chain cannot know are assumed and said out loud: the weapon's own attack rating
 * (the damage line names the 7.0 it was worked out at) and its shape, which is taken as a sword
 * because the panel is drawn before a weapon is committed to.</p>
 */
public final class ForgeStatPreview implements ForgePreviewText {

    /** A plain iron sword's attack rating - the yardstick the damage line is quoted against. */
    public static final float NOMINAL_WEAPON_ATTACK = 7.0f;

    private static final WeaponClass ASSUMED_CLASS = WeaponClass.SWORD;
    private static final float FULL_CHARGE = 1.0f;
    private static final float NO_CHARGE = 0.0f;

    @Override
    public List<Component> statLines(ForgeRecipe recipe, int quality) {
        if (recipe == null || recipe.forms().isEmpty()) {
            return List.of();
        }
        Optional<FormDefinition> lead = ForgeForms.get(ForgeIds.id(recipe.forms().get(0)));
        if (lead.isEmpty()) {
            return List.of();
        }
        FormStats form = lead.get().stats();
        TemperStats temper = temperOf(recipe);
        int flags = flagsOf(recipe);
        List<Component> lines = new ArrayList<>();
        lines.add(damageLine(recipe, quality, form));
        lines.add(shapeLine(form, temper, flags, elementKindOf(recipe)));
        lines.add(elementLine(recipe));
        lines.addAll(artLines(recipe));
        return List.copyOf(lines);
    }

    // --- lines --------------------------------------------------------------------------------

    /** Light and heavy hits of the chain's first form, at a full charge for the heavy. */
    private static Component damageLine(ForgeRecipe recipe, int quality, FormStats form) {
        float hit = ForgeStrikeMath.baseHit(NOMINAL_WEAPON_ATTACK, recipe.grade(), quality);
        float light = ForgeStrikeMath.strikeDamage(hit, form, false, NO_CHARGE, false);
        float heavy = ForgeStrikeMath.strikeDamage(hit, form, true, FULL_CHARGE, false);
        return Component.translatable("screen.magical.forge_stat_damage", oneDecimal(light), oneDecimal(heavy));
    }

    /**
     * The light strike's shape. The element is in it because one Art - GALE's Gale Step - is worth
     * reach rather than an on-hit effect, and the panel would understate a gale flurry by a block
     * if it quoted the plain form reach.
     */
    private static Component shapeLine(FormStats form, TemperStats temper, int flags, ForgeElementKind element) {
        float reach = ForgeStrikeMath.reach(form, temper, ASSUMED_CLASS, flags,
                ForgeStrikeMath.artReachBonus(element, form.family(), false));
        int recovery = ForgeStrikeMath.recovery(form, temper, ASSUMED_CLASS, false, flags);
        // windowEnd from tick zero with no recovery is exactly the window the combo gets.
        long window = ForgeStrikeMath.windowEnd(0L, 0, temper, flags);
        return Component.translatable("screen.magical.forge_stat_shape", oneDecimal(reach), recovery, window);
    }

    /** The chain's element, or {@code null} when it names one this build cannot resolve. */
    private static ForgeElementKind elementKindOf(ForgeRecipe recipe) {
        return ForgeElements.get(ForgeIds.id(recipe.element())).map(ElementDefinition::kind).orElse(null);
    }

    private static Component elementLine(ForgeRecipe recipe) {
        return Component.translatable("screen.magical.forge_stat_element",
                Component.translatable("forge.magical.rider." + recipe.element()));
    }

    /** One line per Art the chain unlocks: the element tested against each form it carries. */
    private static List<Component> artLines(ForgeRecipe recipe) {
        List<Component> lines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>(recipe.forms());
        for (String formId : seen) {
            if (!ForgeSpecials.has(ForgeIds.id(recipe.element()), ForgeIds.id(formId))) {
                continue;
            }
            ForgeArt art = ForgeArt.of(recipe.element(), formId).orElse(null);
            if (art != null) {
                lines.add(Component.translatable("screen.magical.forge_stat_art",
                        Component.translatable(art.langKey()), ForgePreviewPanel.glyphName(formId)));
            }
        }
        return lines;
    }

    // --- shared arithmetic --------------------------------------------------------------------

    private static TemperStats temperOf(ForgeRecipe recipe) {
        return recipe.temper()
                .flatMap(id -> ForgeTempers.get(ForgeIds.id(id)))
                .map(TemperDefinition::stats)
                .orElse(TemperStats.NONE);
    }

    private static int flagsOf(ForgeRecipe recipe) {
        List<ForgeModifierKind> kinds = new ArrayList<>();
        for (String modifierId : recipe.modifiers()) {
            ForgeModifiers.get(ForgeIds.id(modifierId)).map(ModifierDefinition::kind).ifPresent(kinds::add);
        }
        return ForgeStrikeMath.flagsOf(kinds);
    }

    private static String oneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
