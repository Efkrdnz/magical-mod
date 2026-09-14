package com.efkrdnz.magical.magic.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What the blood kit charges, and how the points move the bill.
 *
 * <p>Mana skills pay through resolve: points spent elsewhere raise the mana, efficiency lowers
 * it, and a floor stops a nerf from buying a free spell. Blood registers at zero mana, so until
 * now the points did nothing to its price at all. Every blood cost goes through one scale now,
 * read off the same factor mana is billed at, so the two currencies feel the budget the same way.
 */
class BloodPricesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
    }

    @Test
    void everyAbilityOfTheKitHasABasePriceAndNothingElseDoes() {
        for (String path : List.of("blood_manipulation", "vein_walk", "open_vein", "crimson_spear",
                "coagulate", "blood_rite")) {
            assertTrue(BloodPrices.base(id(path)) > 0, path + " has no price");
        }
        // Unknown is an error, not free: a skill left out of the table would otherwise cast for
        // nothing, and nobody would notice until a player did.
        assertThrows(IllegalArgumentException.class, () -> BloodPrices.base(id("hemorrhage")));
    }

    @Test
    void bloodManipulationsBaseIsTheDrawingRulesBase() {
        // Two numbers for one price would drift. The drawn-length rule owns it.
        assertEquals(BloodShapeRules.BASE_COST, BloodPrices.base(MagicContent.BLOOD_MANIPULATION.id()));
    }

    @Test
    void thriftLowersThePriceAndPointsElsewhereRaiseIt() {
        MagicSkillDefinition skill = MagicContent.BLOOD_MANIPULATION;
        int base = BloodPrices.base(skill.id());
        assertEquals(base, BloodService.cost(skill.resolve(MagicSkillTuning.DEFAULT)));
        assertTrue(BloodService.cost(skill.resolve(new MagicSkillTuning(0, 0, 0, 0, 3))) < base,
                "Thrift must make a cast cheaper");
        assertTrue(BloodService.cost(skill.resolve(new MagicSkillTuning(3, 0, 0, 0, 0))) > base,
                "Bite must make a cast dearer");
        assertEquals(Math.round(base * 0.25F),
                BloodService.cost(skill.resolve(new MagicSkillTuning(-11, 0, -11, 0, 11))),
                "the floor is a quarter of the base, as it is for mana");
    }

    @Test
    void aDrawnLengthIsPricedThroughTheSameScale() {
        // Blood Manipulation charges by the length drawn on top of its base; the points scale the
        // whole bill, not just the base.
        MagicSkillResolvedStats thrifty = MagicContent.BLOOD_MANIPULATION.resolve(new MagicSkillTuning(0, 0, 0, 0, 3));
        int drawn = BloodShapeRules.bloodCost(5.0D);
        assertTrue(drawn > BloodShapeRules.BASE_COST);
        assertEquals(Math.round(drawn * thrifty.costScale()), BloodService.scale(thrifty, drawn));
        assertEquals(0, BloodService.scale(thrifty, 0), "nothing drawn costs nothing");
        MagicSkillResolvedStats floored = MagicContent.BLOOD_MANIPULATION.resolve(new MagicSkillTuning(-11, 0, -11, 0, 11));
        assertEquals(1, BloodService.scale(floored, 1), "a price never rounds away to free");
    }
}
