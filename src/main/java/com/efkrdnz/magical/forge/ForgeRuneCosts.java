package com.efkrdnz.magical.forge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.efkrdnz.magical.forge.chain.ForgeRecipe;
import com.efkrdnz.magical.forge.chain.ForgeRules;

/**
 * What a chain of runes costs: mana added to the grade share, and the stability that shifts the
 * final quality.
 *
 * <p>One place on purpose. The client predicts these numbers to draw the preview and the server
 * recomputes them as the authority, and if the two ever disagreed the player would be told one
 * price and charged another.
 *
 * @param mana      percentage points added to the grade mana share
 * @param stability points added to the mean glyph quality; negative for most runes
 */
public record ForgeRuneCosts(int mana, int stability) {

    /**
     * Totals a recipe, or empty when it names a rune this build does not know.
     *
     * <p>Repeated runes cost full mana per copy but only half stability per repeat - see
     * {@link ForgeRules#stackedStability}. Mana is the price of the stack; stability is the risk,
     * and charging it in full would make a stack impossible rather than expensive.
     */
    public static Optional<ForgeRuneCosts> of(ForgeRecipe recipe) {
        return of(recipe.modifiers(), recipe.temper());
    }

    public static Optional<ForgeRuneCosts> of(List<String> modifierRun, Optional<String> temperId) {
        Map<String, Integer> copies = new LinkedHashMap<>();
        for (String id : modifierRun) {
            copies.merge(id, 1, Integer::sum);
        }
        int mana = 0;
        int stability = 0;
        for (Map.Entry<String, Integer> entry : copies.entrySet()) {
            Optional<ModifierDefinition> definition = ForgeModifiers.get(ForgeIds.id(entry.getKey()));
            if (definition.isEmpty()) {
                return Optional.empty();
            }
            int count = entry.getValue();
            mana += definition.get().manaDelta() * count;
            stability += ForgeRules.stackedStability(definition.get().stabilityDelta(), count);
        }
        if (temperId.isPresent()) {
            Optional<TemperDefinition> temper = ForgeTempers.get(ForgeIds.id(temperId.get()));
            if (temper.isEmpty()) {
                return Optional.empty();
            }
            stability += temper.get().stabilityDelta();
        }
        return Optional.of(new ForgeRuneCosts(mana, stability));
    }
}
