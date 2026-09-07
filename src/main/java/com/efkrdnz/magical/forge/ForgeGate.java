package com.efkrdnz.magical.forge;

import java.util.Optional;

import com.efkrdnz.magical.forge.chain.ForgeError;
import com.efkrdnz.magical.forge.fusion.ForgeFusion;
import com.efkrdnz.magical.magic.PlayerMagicState;

import net.minecraft.resources.ResourceLocation;

/**
 * Whether a smith is allowed the fusion they drew.
 *
 * <p>One place, called from two: the server decides it for real when a chain is submitted, and the
 * client asks the same question to grey out the preview. Both already hold a
 * {@link PlayerMagicState} - the client through the synced mirror - so nothing new has to be sent
 * for the forge to explain itself before the player spends a drawing on it.
 */
public final class ForgeGate {

    private ForgeGate() {}

    /**
     * The rule the smith breaks by forging {@code fusion}, or empty when they may.
     *
     * <p>The class check comes first because it is the harder thing to fix: a smith who is not a
     * Divinesmith cannot learn the sorcery either, and naming the sorcery to someone who cannot
     * take the class would be the less useful of the two answers.
     */
    public static Optional<ForgeError> checkFusion(ForgeFusion fusion, PlayerMagicState state) {
        Optional<String> requiredClass = fusion.requiredClass();
        if (requiredClass.isPresent() && !state.hasClass(ForgeIds.id(requiredClass.get()))) {
            return Optional.of(ForgeError.FUSION_LOCKED_CLASS);
        }
        for (String skill : fusion.requiredSkills()) {
            if (!state.hasUnlocked(ForgeIds.id(skill))) {
                return Optional.of(ForgeError.FUSION_LOCKED_SKILL);
            }
        }
        return Optional.empty();
    }

    /** The fusion an already-resolved element id came from, if it came from one at all. */
    public static Optional<ForgeFusion> fusionOf(ResourceLocation elementId) {
        return ForgeFusions.byElement(elementId);
    }
}
