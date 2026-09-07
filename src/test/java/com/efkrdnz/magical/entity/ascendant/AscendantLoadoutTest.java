package com.efkrdnz.magical.entity.ascendant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.chain.ForgeProgram;
import com.efkrdnz.magical.forge.chain.ForgeStep;
import com.efkrdnz.magical.forge.chain.TriggerKind;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The Ascendants' blades are built straight from the record, skipping every forge gate. That makes
 * it entirely possible to write an inscription the forge could never produce and would not know how
 * to read back, so what they actually contain is pinned here.
 */
class AscendantLoadoutTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ForgedWeapon weapon(AscendantTier tier) {
        ItemStack stack = AscendantLoadout.weaponFor(tier);
        assertFalse(stack.isEmpty(), tier + " has no weapon");
        return ForgedWeapons.get(stack).orElseThrow(() -> new AssertionError(tier + " weapon is not forged"));
    }

    @Test
    void everyTierCarriesACoherentInscription() {
        for (AscendantTier tier : AscendantTier.values()) {
            ForgedWeapon forged = weapon(tier);
            assertEquals(100, forged.quality(), tier + " quality");
            assertTrue(forged.temper().isPresent(), tier + " temper");
            assertTrue(ForgeElements.get(forged.element()).isPresent(),
                    tier + " element " + forged.element() + " does not resolve");
            assertFalse(forged.program().isEmpty(), tier + " program");
            for (ResourceLocation id : forged.program()) {
                assertTrue(ForgeGlyphLibrary.byId(id.getPath()).isPresent(),
                        tier + " program names an unknown rune: " + id);
            }
        }
    }

    @Test
    void theFlatViewsAgreeWithTheProgram() {
        // A weapon whose forms and modifiers disagreed with its program would show the player one
        // chain in the tooltip and fire another.
        for (AscendantTier tier : AscendantTier.values()) {
            ForgedWeapon forged = weapon(tier);
            List<String> program = forged.program().stream().map(ResourceLocation::getPath).toList();
            for (ResourceLocation form : forged.forms()) {
                assertTrue(program.contains(form.getPath()), tier + " form not in program: " + form);
            }
            for (ResourceLocation modifier : forged.modifiers()) {
                assertTrue(program.contains(modifier.getPath()), tier + " modifier not in program: " + modifier);
            }
            assertFalse(forged.forms().isEmpty(), tier + " has no form to swing");
        }
    }

    @Test
    void gradesRiseWithTheTier() {
        ForgeGrade previous = null;
        for (AscendantTier tier : AscendantTier.values()) {
            ForgeGrade grade = weapon(tier).grade();
            if (previous != null) {
                assertTrue(grade.ordinal() >= previous.ordinal(),
                        tier + " carries a worse blade than the tier below");
            }
            previous = grade;
        }
        assertEquals(ForgeGrade.DIVINE, weapon(AscendantTier.AUTHORITY).grade());
    }

    @Test
    void authorityCarriesThreeForkedWavesEachBirthingASlamWhereItLands() {
        // The comment on that entry makes a specific claim. This is the claim.
        ForgeProgram program = weapon(AscendantTier.AUTHORITY).compiled();
        assertEquals(1, program.length(), "the whole chain is one press");

        ForgeStep opener = program.stepAt(0);
        assertEquals(3, opener.width(), "two forks bind three waves");
        assertEquals("wave", opener.leadForm());
        assertTrue(opener.payload().isPresent(), "the slam is nested, not a press of its own");
        assertEquals(TriggerKind.EXPIRY, opener.payload().get().kind(),
                "a wake fires where the carrier's flight ends, hit or miss");
        assertEquals("slam", opener.payload().get().step().leadForm());
    }

    @Test
    void theExecutionersChainBurstsOnTheFirstBodyItTouches() {
        ForgeStep opener = weapon(AscendantTier.BLACK_FLAME).compiled().stepAt(0);
        assertEquals("wave", opener.leadForm());
        assertEquals(TriggerKind.IMPACT, opener.payload().orElseThrow().kind());
        assertEquals("slam", opener.payload().get().step().leadForm());
    }

    @Test
    void theFusionElementsAreTheOnesThatNeedNoGlyph() {
        // black_flame and explosion are fusion results: they exist as elements but have no rune, so
        // a loadout naming them proves the code path really does bypass the fusion gate.
        assertEquals("black_flame", weapon(AscendantTier.AUTHORITY).element().getPath());
        assertEquals("explosion", weapon(AscendantTier.FALLEN).element().getPath());
        assertTrue(ForgeGlyphLibrary.byId("black_flame").isEmpty(),
                "if a black_flame rune ever exists this weapon stops being unforgeable");
    }

    @Test
    void eachCallReturnsAFreshStack() {
        ItemStack first = AscendantLoadout.weaponFor(AscendantTier.ECHO);
        ItemStack second = AscendantLoadout.weaponFor(AscendantTier.ECHO);
        assertFalse(first == second, "a shared stack would let one Ascendant's damage follow another");
    }
}
