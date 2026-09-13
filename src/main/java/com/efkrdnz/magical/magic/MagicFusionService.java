package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.classes.MagicalClasses;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The formulas of the Spell Creator: which two skills make which third one, and where each
 * formula stands for a given player. Everything but {@link #create} is pure and server-safe; the
 * creator screen reads {@link #formulas} and {@link #status} for its Formulas tab and result card,
 * and the server re-runs the same checks in {@link #create} before anything changes.
 */
public final class MagicFusionService {

    /** Where a formula stands for one player. Declaration order is the Formulas tab's sort order. */
    public enum Status { READY, MISSING, LOCKED, CREATED }

    /** A recipe read against a player state: its status and, whatever the status, the inputs the player lacks. */
    public record FormulaState(FusionRecipe recipe, Status status, List<ResourceLocation> missing) {}

    // Spell Creator recipes preserve their inputs (experimental hybrids you can keep tinkering with);
    // Magic Originator recipes consume both inputs (a greater spell originated by sacrifice).
    private static final List<FusionRecipe> RECIPES = List.of(
            // --- Spell Creator: elemental hybrids, inputs preserved ---
            preserving("black_flames", MagicContent.BLACK_FLAMES, MagicalClasses.SPELL_CREATOR,
                    MagicContent.WILDFIRE, MagicContent.ABYSSAL_DISCHARGE),
            preserving("scalding_geyser", MagicContent.SCALDING_GEYSER, MagicalClasses.SPELL_CREATOR,
                    MagicContent.MAGMA_VENT, MagicContent.DELUGE_JET),
            preserving("dawnwell", MagicContent.DAWNWELL, MagicalClasses.SPELL_CREATOR,
                    MagicContent.GLINT, MagicContent.RIP_CURRENT),
            preserving("black_orrery", MagicContent.BLACK_ORRERY, MagicalClasses.SPELL_CREATOR,
                    MagicContent.GRAVEMOONS, MagicContent.RIGID_FRAME),
            preserving("cinder_chariot", MagicContent.CINDER_CHARIOT, MagicalClasses.SPELL_CREATOR,
                    MagicContent.SLAG_ROLLER, MagicContent.PUPPET_SIGIL),
            // --- Magic Originator: ultimates, both inputs consumed ---
            consuming("gabriel", MagicContent.GABRIEL, MagicalClasses.MAGIC_ORIGINATOR,
                    MagicContent.SOVEREIGN_AEGIS, MagicContent.JUDGEMENT),
            consuming("fallen_sun", MagicContent.FALLEN_SUN, MagicalClasses.MAGIC_ORIGINATOR,
                    MagicContent.CRUCIBLE, MagicContent.CLEANSING_RAY),
            consuming("total_eclipse", MagicContent.TOTAL_ECLIPSE, MagicalClasses.MAGIC_ORIGINATOR,
                    MagicContent.FALLEN_FIRMAMENT, MagicContent.GLINT),
            consuming("tectonic_verdict", MagicContent.TECTONIC_VERDICT, MagicalClasses.MAGIC_ORIGINATOR,
                    MagicContent.MAGMA_VENT, MagicContent.CREASE_FOLD));

    private MagicFusionService() {}

    private static FusionRecipe preserving(String key, MagicSkillDefinition output, ResourceLocation requiredClass,
            MagicSkillDefinition firstInput, MagicSkillDefinition secondInput) {
        return new FusionRecipe(key, output.id(), requiredClass, firstInput.id(), secondInput.id(),
                Component.translatable("screen.magical.fusion." + key + ".requirement"),
                Component.translatable("screen.magical.fusion_preserves_inputs"),
                false, false, true);
    }

    private static FusionRecipe consuming(String key, MagicSkillDefinition output, ResourceLocation requiredClass,
            MagicSkillDefinition firstInput, MagicSkillDefinition secondInput) {
        return new FusionRecipe(key, output.id(), requiredClass, firstInput.id(), secondInput.id(),
                Component.translatable("screen.magical.fusion." + key + ".requirement"),
                Component.translatable("screen.magical.fusion." + key + ".loss"),
                true, true, true);
    }

    public static List<FusionRecipe> recipes() {
        return RECIPES;
    }

    /** The owned skills that appear in any formula, in registration order, so the list reads the same every time. */
    public static List<MagicSkillDefinition> eligibleInputs(PlayerMagicState state) {
        List<MagicSkillDefinition> result = new ArrayList<>();
        for (ResourceLocation skillId : state.unlockedSkills()) {
            MagicSkillDefinition skill = MagicContent.get(skillId);
            if (skill == null || !canAppearInSlot(skillId)) {
                continue;
            }
            if (RECIPES.stream().anyMatch(recipe -> recipe.acceptsEitherSlot(skillId))) {
                result.add(skill);
            }
        }
        result.sort(Comparator.comparingInt(skill -> MagicContent.skillIndex(skill.id())));
        return result;
    }

    public static FusionRecipe recipeFor(ResourceLocation firstInput, ResourceLocation secondInput) {
        if (firstInput == null || secondInput == null
                || MagicContent.get(firstInput) == null || MagicContent.get(secondInput) == null) {
            return null;
        }
        return RECIPES.stream().filter(recipe -> recipe.matches(firstInput, secondInput)).findFirst().orElse(null);
    }

    public static boolean canCreate(PlayerMagicState state, ResourceLocation firstInput, ResourceLocation secondInput) {
        FusionRecipe recipe = recipeFor(firstInput, secondInput);
        return recipe != null && recipe.canCreate(state, firstInput, secondInput);
    }

    /**
     * One formula against one player. Created wins over everything: after a consuming fusion the
     * inputs are gone, and the row still has to read as done rather than as missing them.
     */
    public static FormulaState status(PlayerMagicState state, FusionRecipe recipe) {
        List<ResourceLocation> missing = new ArrayList<>(2);
        if (!state.hasUnlocked(recipe.firstInputId())) {
            missing.add(recipe.firstInputId());
        }
        if (!state.hasUnlocked(recipe.secondInputId())) {
            missing.add(recipe.secondInputId());
        }
        Status status;
        if (state.hasUnlocked(recipe.outputSkill())) {
            status = Status.CREATED;
        } else if (!state.hasClass(recipe.requiredClass())) {
            status = Status.LOCKED;
        } else if (!missing.isEmpty()) {
            status = Status.MISSING;
        } else {
            status = Status.READY;
        }
        return new FormulaState(recipe, status, List.copyOf(missing));
    }

    /** Every formula, ready ones first, then missing, locked and created; recipe order within a status. */
    public static List<FormulaState> formulas(PlayerMagicState state) {
        List<FormulaState> result = new ArrayList<>(RECIPES.size());
        for (FusionRecipe recipe : RECIPES) {
            result.add(status(state, recipe));
        }
        result.sort(Comparator.comparingInt(formula -> formula.status().ordinal()));
        return result;
    }

    public static boolean create(ServerPlayer player, PlayerMagicState state, ResourceLocation firstInput, ResourceLocation secondInput) {
        FusionRecipe recipe = recipeFor(firstInput, secondInput);
        if (recipe == null || !recipe.canCreate(state, firstInput, secondInput)) {
            player.displayClientMessage(Component.translatable("message.magical.fusion_requirements_missing"), true);
            return false;
        }
        if (state.hasUnlocked(recipe.outputSkill())) {
            player.displayClientMessage(Component.translatable("message.magical.fusion_already_created"), true);
            return false;
        }
        // Where the inputs sat, across every loadout - captured before removeSkill unbinds them.
        // The old code could only replace one slot of the one active set, so a fusion silently
        // emptied whatever other places the inputs had been bound into.
        List<int[]> replacements = new ArrayList<>();
        for (int index = 0; index < state.loadouts().size(); index++) {
            MagicLoadout loadout = state.loadouts().get(index);
            for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
                ResourceLocation equipped = loadout.slot(slot);
                if (firstInput.equals(equipped) || secondInput.equals(equipped)) {
                    replacements.add(new int[]{index, slot});
                }
            }
        }
        if (recipe.consumesFirstInput() && !state.removeSkill(firstInput)) {
            player.displayClientMessage(Component.translatable("message.magical.fusion_requirements_missing"), true);
            return false;
        }
        if (recipe.consumesSecondInput() && !state.removeSkill(secondInput)) {
            player.displayClientMessage(Component.translatable("message.magical.fusion_requirements_missing"), true);
            return false;
        }
        if (state.unlock(recipe.outputSkill())) {
            MagicSkillDefinition skill = MagicContent.get(recipe.outputSkill());
            for (int[] at : replacements) {
                state.setLoadoutSlot(at[0], at[1], recipe.outputSkill());
            }
            player.displayClientMessage(Component.translatable("message.magical.fusion_created", Component.translatable(skill.nameKey())), false);
            state.addClassXp(recipe.requiredClass(), 35);
            state.sync(player);
            return true;
        }
        return false;
    }

    /** Created skills and wheel modes are never ingredients. */
    public static boolean canAppearInSlot(ResourceLocation skillId) {
        return !MagicContent.isCreatedSkill(skillId) && !MagicContent.isSubSkill(skillId);
    }

    public record FusionRecipe(
            String key,
            ResourceLocation outputSkill,
            ResourceLocation requiredClass,
            ResourceLocation firstInputId,
            ResourceLocation secondInputId,
            Component requirement,
            Component lossWarning,
            boolean consumesFirstInput,
            boolean consumesSecondInput,
            boolean commutative) {
        public boolean acceptsEitherSlot(ResourceLocation skillId) {
            return firstInputId.equals(skillId) || secondInputId.equals(skillId);
        }

        public boolean matches(ResourceLocation first, ResourceLocation second) {
            if (first == null || second == null || first.equals(second)) {
                return false;
            }
            boolean direct = firstInputId.equals(first) && secondInputId.equals(second);
            return direct || commutative && firstInputId.equals(second) && secondInputId.equals(first);
        }

        public boolean consumesInputs() {
            return consumesFirstInput || consumesSecondInput;
        }

        public boolean canCreate(PlayerMagicState state, ResourceLocation first, ResourceLocation second) {
            return matches(first, second)
                    && state.hasClass(requiredClass)
                    && state.hasUnlocked(first)
                    && state.hasUnlocked(second)
                    && !state.hasUnlocked(outputSkill);
        }
    }
}
