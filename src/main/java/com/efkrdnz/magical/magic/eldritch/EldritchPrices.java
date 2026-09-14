package com.efkrdnz.magical.magic.eldritch;

import com.efkrdnz.magical.MagicalMod;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * What each call to the deep draws in Notice before the points move it.
 *
 * <p>One table, as {@code BloodPrices}: the codex prints it without loading a handler, and the
 * roster and the list are held together by a test. The points apply through
 * {@code EldritchService.cost}, at the factor mana is billed at.
 */
public final class EldritchPrices {

    private static final Map<String, Integer> BASE = Map.of(
            "grasp_of_the_deep", 12,
            "unblinking_eye", 8,
            "hungering_maw", 15,
            "tendril_lash", 6,
            "skin_of_the_deep", 10,
            // Paid again at every pulse of the call, which is why one pulse is the cheapest line.
            "call_of_the_deep", 5);

    private EldritchPrices() {
    }

    /** @throws IllegalArgumentException outside the kit: unknown is an error, not free. */
    public static int base(ResourceLocation skillId) {
        Integer base = skillId != null && MagicalMod.MODID.equals(skillId.getNamespace())
                ? BASE.get(skillId.getPath()) : null;
        if (base == null) {
            throw new IllegalArgumentException("no notice price for " + skillId);
        }
        return base;
    }
}
