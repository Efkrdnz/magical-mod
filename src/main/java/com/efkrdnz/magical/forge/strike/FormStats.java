package com.efkrdnz.magical.forge.strike;

import com.efkrdnz.magical.forge.FormFamily;

/**
 * MC-free mirror of {@code FormDefinition}: the shape and pacing numbers for one combo form.
 */
public record FormStats(FormFamily family, float lightScale, float heavyScale, float reach, float halfWidth,
        float arcDegrees, float speed, int lifeTicks, float knockback, int recoveryTicks) {
}
