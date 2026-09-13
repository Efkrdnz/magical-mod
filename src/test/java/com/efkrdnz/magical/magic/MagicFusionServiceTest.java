package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.MagicFusionService.FormulaState;
import com.efkrdnz.magical.magic.MagicFusionService.FusionRecipe;
import com.efkrdnz.magical.magic.MagicFusionService.Status;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The formulas as the creator screen reads them: which skills go in, what comes out, and the
 * status of each one against a player state. The status is what the Formulas tab sorts and
 * labels by, so its order and its precedence are pinned here rather than in the screen.
 */
class MagicFusionServiceTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FusionRecipe recipe(String key) {
        return MagicFusionService.recipes().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no recipe " + key));
    }

    private static PlayerMagicState creator() {
        PlayerMagicState state = new PlayerMagicState();
        assertTrue(state.unlockClass(MagicalClasses.SPELL_CREATOR));
        return state;
    }

    @Test
    void everyRecipeNamesRealInputsAClassAndACreatedOutput() {
        for (FusionRecipe recipe : MagicFusionService.recipes()) {
            assertNotNull(MagicContent.get(recipe.firstInputId()), recipe.key() + " first input is unknown");
            assertNotNull(MagicContent.get(recipe.secondInputId()), recipe.key() + " second input is unknown");
            assertNotNull(MagicContent.get(recipe.outputSkill()), recipe.key() + " output is unknown");
            assertNotNull(MagicalClasses.get(recipe.requiredClass()), recipe.key() + " class is unknown");
            assertTrue(MagicContent.isCreatedSkill(recipe.outputSkill()), recipe.key() + " output is not a created skill");
            assertTrue(MagicFusionService.canAppearInSlot(recipe.firstInputId()), recipe.key() + " first input could never be loaded");
            assertTrue(MagicFusionService.canAppearInSlot(recipe.secondInputId()), recipe.key() + " second input could never be loaded");
            assertFalse(recipe.firstInputId().equals(recipe.secondInputId()), recipe.key() + " fuses a skill with itself");
        }
    }

    @Test
    void noTwoRecipesShareAPair() {
        Set<Set<ResourceLocation>> pairs = new HashSet<>();
        for (FusionRecipe recipe : MagicFusionService.recipes()) {
            assertTrue(pairs.add(Set.of(recipe.firstInputId(), recipe.secondInputId())),
                    recipe.key() + " repeats another recipe's pair");
        }
    }

    @Test
    void aPairMatchesEitherWayRoundAndNeverASkillWithItself() {
        for (FusionRecipe recipe : MagicFusionService.recipes()) {
            FusionRecipe direct = MagicFusionService.recipeFor(recipe.firstInputId(), recipe.secondInputId());
            FusionRecipe swapped = MagicFusionService.recipeFor(recipe.secondInputId(), recipe.firstInputId());
            assertSame(recipe, direct, recipe.key() + " does not match its own pair");
            assertSame(recipe, swapped, recipe.key() + " does not match its pair swapped");
            assertNull(MagicFusionService.recipeFor(recipe.firstInputId(), recipe.firstInputId()));
        }
    }

    @Test
    void theStatusWalksFromLockedThroughMissingToReadyToCreated() {
        FusionRecipe blackFlames = recipe("black_flames");
        PlayerMagicState state = new PlayerMagicState();

        FormulaState locked = MagicFusionService.status(state, blackFlames);
        assertEquals(Status.LOCKED, locked.status());
        assertEquals(List.of(MagicContent.WILDFIRE.id(), MagicContent.ABYSSAL_DISCHARGE.id()), locked.missing(),
                "a locked formula still says which inputs are missing");

        assertTrue(state.unlockClass(MagicalClasses.SPELL_CREATOR));
        assertEquals(Status.MISSING, MagicFusionService.status(state, blackFlames).status());

        state.unlock(MagicContent.WILDFIRE.id());
        FormulaState oneMissing = MagicFusionService.status(state, blackFlames);
        assertEquals(Status.MISSING, oneMissing.status());
        assertEquals(List.of(MagicContent.ABYSSAL_DISCHARGE.id()), oneMissing.missing());

        state.unlock(MagicContent.ABYSSAL_DISCHARGE.id());
        FormulaState ready = MagicFusionService.status(state, blackFlames);
        assertEquals(Status.READY, ready.status());
        assertTrue(ready.missing().isEmpty());
        assertTrue(MagicFusionService.canCreate(state, MagicContent.WILDFIRE.id(), MagicContent.ABYSSAL_DISCHARGE.id()));

        state.unlock(MagicContent.BLACK_FLAMES.id());
        assertEquals(Status.CREATED, MagicFusionService.status(state, blackFlames).status());
    }

    @Test
    void createdWinsOverAMissingClassAndMissingInputs() {
        // After a consuming fusion the inputs are gone, so this is exactly what the screen shows next.
        PlayerMagicState state = new PlayerMagicState();
        state.unlock(MagicContent.GABRIEL.id());
        assertEquals(Status.CREATED, MagicFusionService.status(state, recipe("gabriel")).status());
    }

    @Test
    void anOriginatorFormulaIsLockedForAPlainCreatorEvenWithBothInputs() {
        PlayerMagicState state = creator();
        state.unlock(MagicContent.SOVEREIGN_AEGIS.id());
        state.unlock(MagicContent.JUDGEMENT.id());
        FormulaState gabriel = MagicFusionService.status(state, recipe("gabriel"));
        assertEquals(Status.LOCKED, gabriel.status());
        assertTrue(gabriel.missing().isEmpty());
        assertFalse(MagicFusionService.canCreate(state, MagicContent.SOVEREIGN_AEGIS.id(), MagicContent.JUDGEMENT.id()));
    }

    @Test
    void theFormulaListIsSortedReadyMissingLockedCreatedAndCompleteAndStable() {
        PlayerMagicState state = creator();
        state.unlock(MagicContent.WILDFIRE.id());
        state.unlock(MagicContent.ABYSSAL_DISCHARGE.id());      // black_flames: ready
        state.unlock(MagicContent.GLINT.id());                  // dawnwell: missing rip current
        state.unlock(MagicContent.SCALDING_GEYSER.id());        // created

        List<FormulaState> formulas = MagicFusionService.formulas(state);
        assertEquals(MagicFusionService.recipes().size(), formulas.size());
        assertEquals(MagicFusionService.recipes().size(),
                formulas.stream().map(f -> f.recipe().key()).distinct().count(), "a recipe is listed twice");

        assertEquals("black_flames", formulas.get(0).recipe().key());
        assertEquals(Status.READY, formulas.get(0).status());
        assertEquals(Status.CREATED, formulas.get(formulas.size() - 1).status());
        assertEquals("scalding_geyser", formulas.get(formulas.size() - 1).recipe().key());
        for (int i = 1; i < formulas.size(); i++) {
            Status previous = formulas.get(i - 1).status();
            Status current = formulas.get(i).status();
            assertTrue(previous.ordinal() <= current.ordinal(),
                    previous + " sorts after " + current + " at row " + i);
            if (previous == current) {
                int before = MagicFusionService.recipes().indexOf(formulas.get(i - 1).recipe());
                int after = MagicFusionService.recipes().indexOf(formulas.get(i).recipe());
                assertTrue(before < after, "equal statuses left recipe order at row " + i);
            }
        }
    }

    @Test
    void theIngredientsAreTheOwnedFormulaInputsInRegistrationOrder() {
        PlayerMagicState state = creator();
        state.unlock(MagicContent.ABYSSAL_DISCHARGE.id());
        state.unlock(MagicContent.WILDFIRE.id());
        state.unlock(MagicContent.GLINT.id());
        state.unlock(MagicContent.BLACK_FLAMES.id());

        List<ResourceLocation> ids = MagicFusionService.eligibleInputs(state).stream()
                .map(MagicSkillDefinition::id)
                .toList();
        assertEquals(Set.of(MagicContent.ABYSSAL_DISCHARGE.id(), MagicContent.WILDFIRE.id(), MagicContent.GLINT.id()),
                new HashSet<>(ids), "created skills and non-ingredients must not be offered");
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(MagicContent.skillIndex(ids.get(i - 1)) < MagicContent.skillIndex(ids.get(i)),
                    "ingredients are not in registration order at " + i);
        }
    }

    @Test
    void everyRequirementAndLossLineIsTranslated() throws IOException {
        String lang;
        try (InputStream in = MagicFusionServiceTest.class.getResourceAsStream("/assets/magical/lang/en_us.json")) {
            assertNotNull(in, "missing language file");
            lang = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (FusionRecipe recipe : MagicFusionService.recipes()) {
            for (Component line : List.of(recipe.requirement(), recipe.lossWarning())) {
                String key = ((TranslatableContents) line.getContents()).getKey();
                assertTrue(lang.contains('"' + key + '"'), recipe.key() + " uses the untranslated key " + key);
            }
        }
    }
}
