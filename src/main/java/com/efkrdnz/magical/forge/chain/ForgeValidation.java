package com.efkrdnz.magical.forge.chain;

/** Result of validating a rune chain: either a recipe or the first error found. */
public sealed interface ForgeValidation permits ForgeValidation.Valid, ForgeValidation.Invalid {

    /** The chain is well formed. */
    record Valid(ForgeRecipe recipe) implements ForgeValidation {
    }

    /**
     * The chain is rejected.
     *
     * @param error    which rule failed
     * @param argument context for the message: a glyph index, or a slot count
     */
    record Invalid(ForgeError error, int argument) implements ForgeValidation {
    }
}
