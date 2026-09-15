package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.magic.MagicPassiveContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;

/**
 * What a Blood Sacrifice pact may be built from, and what each piece costs.
 *
 * <p>The point cost is the only thing the pick screen and the sealing service both have to agree
 * on, so it lives here and nowhere else.
 *
 * <p>Order is the order the screen lists them in: cheapest first, so the top of each column is
 * where a small pact is built and nobody has to scroll to find the affordable half.
 */
public final class SacrificeCatalogue {

    private static final Map<ResourceLocation, Integer> COSTS = new LinkedHashMap<>();

    public static final List<ResourceLocation> BOONS = List.of(
            priced(MagicPassiveContent.CRIMSON_EDGE.id(), 1),
            priced(MagicPassiveContent.LONG_REACH.id(), 1),
            priced(MagicPassiveContent.UNFEELING.id(), 1),
            priced(MagicPassiveContent.SURE_FOOTING.id(), 1),
            priced(MagicPassiveContent.SANGUINE_MIGHT.id(), 2),
            priced(MagicPassiveContent.QUICKENED_PULSE.id(), 2),
            priced(MagicPassiveContent.THINNED_BLOOD.id(), 2),
            priced(MagicPassiveContent.CLOTTED_HIDE.id(), 2),
            priced(MagicPassiveContent.VESSEL_SIPHON.id(), 2),
            priced(MagicPassiveContent.SCARLET_TIDE.id(), 2),
            priced(MagicPassiveContent.BLOOD_SCENT.id(), 2),
            priced(MagicPassiveContent.SECOND_HEART.id(), 3),
            priced(MagicPassiveContent.HAEMOPHAGE.id(), 3),
            priced(MagicPassiveContent.RACING_HEART.id(), 3),
            priced(MagicPassiveContent.BLOODBORNE_FURY.id(), 3),
            priced(MagicPassiveContent.IRONBLOOD.id(), 4));

    public static final List<ResourceLocation> PRICES = List.of(
            priced(MagicPassiveContent.OPEN_WOUND.id(), 1),
            priced(MagicPassiveContent.DULLED_SENSES.id(), 1),
            priced(MagicPassiveContent.THIN_SKIN.id(), 1),
            priced(MagicPassiveContent.LEADEN_STEP.id(), 1),
            priced(MagicPassiveContent.WEEPING_VESSEL.id(), 1),
            priced(MagicPassiveContent.HEMORRHAGE.id(), 2),
            priced(MagicPassiveContent.LIFE_TAX.id(), 2),
            priced(MagicPassiveContent.BRITTLE_BARRIER.id(), 2),
            priced(MagicPassiveContent.SLOW_BLOOD.id(), 2),
            priced(MagicPassiveContent.GLASS_BONES.id(), 2),
            priced(MagicPassiveContent.MANA_DROUGHT.id(), 2),
            priced(MagicPassiveContent.BINDING_CHAINS.id(), 2),
            priced(MagicPassiveContent.THE_UNKNOWN.id(), 2),
            priced(MagicPassiveContent.ECHOING_MISERY.id(), 3),
            priced(MagicPassiveContent.SPELL_FIZZLE.id(), 3),
            priced(MagicPassiveContent.BLOOD_DEBT.id(), 4));

    private SacrificeCatalogue() {
    }

    private static ResourceLocation priced(ResourceLocation id, int cost) {
        COSTS.put(id, cost);
        return id;
    }

    /** Both lists, boons first, in the order the screen draws them. */
    public static List<ResourceLocation> all() {
        return Stream.concat(BOONS.stream(), PRICES.stream()).toList();
    }

    /** Zero for anything not on either list, so an unknown id can never be spent or paid with. */
    public static int cost(ResourceLocation id) {
        return COSTS.getOrDefault(id, 0);
    }

    public static boolean isBoon(ResourceLocation id) {
        return BOONS.contains(id);
    }

    public static boolean isPrice(ResourceLocation id) {
        return PRICES.contains(id);
    }

    /**
     * The point total of a chosen list, counting only what belongs on the list being summed.
     *
     * <p>The seal packet is untrusted input: a price smuggled into the boon list has to be worth
     * nothing rather than quietly funding the pact.
     */
    public static int sum(List<ResourceLocation> chosen, boolean boons) {
        int total = 0;
        for (ResourceLocation id : chosen) {
            if (boons ? isBoon(id) : isPrice(id)) {
                total += cost(id);
            }
        }
        return total;
    }
}
