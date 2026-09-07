package com.efkrdnz.magical.forge.chain;

import java.util.List;
import java.util.Optional;

import com.efkrdnz.magical.forge.ModifierStack;

/**
 * One press of the left mouse button: the form (or forked group of forms) that fires, the modifiers
 * that shape it, and an optional payload nested inside it.
 *
 * @param forms   form glyph ids; one normally, two to four under a fork
 * @param mods    the modifiers attached to this step, and to this step only
 * @param payload a nested step that fires on impact, on a timer, or when this one expires
 */
public record ForgeStep(List<String> forms, ModifierStack mods, Optional<Payload> payload) {

    public ForgeStep {
        forms = List.copyOf(forms);
    }

    /** A plain step: one form, no payload. */
    public static ForgeStep of(String form, ModifierStack mods) {
        return new ForgeStep(List.of(form), mods, Optional.empty());
    }

    /** The lead form, which is the one that claims the press's vanilla-hit correction. */
    public String leadForm() {
        return forms.get(0);
    }

    /** How many forms fire together on this press. One unless a fork bound them. */
    public int width() {
        return forms.size();
    }

    public boolean isForked() {
        return forms.size() > 1;
    }

    /** This step carrying {@code nested}, replacing any payload it already had. */
    public ForgeStep withPayload(Payload nested) {
        return new ForgeStep(forms, mods, Optional.of(nested));
    }
}
