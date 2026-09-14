package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * What each blood ability asks for before the points move it: the base of the bill, in blood.
 *
 * <p>One table rather than a constant in each skill file, so the codex can print a price without
 * loading a handler, and so the roster and the price list can be held together by a test. The
 * points are applied by {@code BloodService.cost}, through the same factor mana is billed at.
 */
public final class BloodPrices {

    private static final Map<String, Integer> BASE = Map.of(
            // The drawn-length rule owns Blood Manipulation: this is its base, and the length is
            // billed on top of it by BloodShapeRules.bloodCost.
            "blood_manipulation", BloodShapeRules.BASE_COST,
            "vein_walk", 12,
            "open_vein", 16,
            "crimson_spear", 24,
            // Coagulate takes the pools it can reach for nothing; this is the most it draws from
            // the Vessel on top of them.
            "coagulate", 40,
            // Blood Rite is paid in health. This is the blood one heart pours out.
            "blood_rite", 8);

    private BloodPrices() {
    }

    /**
     * The base price of a skill in the kit.
     *
     * @throws IllegalArgumentException for anything outside it. Unknown is an error rather than
     *         free: a skill left out of the table would otherwise cast for nothing.
     */
    public static int base(ResourceLocation skillId) {
        Integer base = skillId != null && MagicalMod.MODID.equals(skillId.getNamespace())
                ? BASE.get(skillId.getPath()) : null;
        if (base == null) {
            throw new IllegalArgumentException("no blood price for " + skillId);
        }
        return base;
    }
}
