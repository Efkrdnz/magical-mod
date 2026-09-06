package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.classes.MagicalClasses;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class MagicFusionService {
    public static final int BUTTON_BASE = 2200;

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
                    MagicContent.HEAVENS_GAZE, MagicContent.PRISM_CASCADE),
            consuming("fallen_sun", MagicContent.FALLEN_SUN, MagicalClasses.MAGIC_ORIGINATOR,
                    MagicContent.CRUCIBLE, MagicContent.CLEANSING_RAY),
            consuming("total_eclipse", MagicContent.TOTAL_ECLIPSE, MagicalClasses.MAGIC_ORIGINATOR,
                    MagicContent.FALLEN_FIRMAMENT, MagicContent.GLINT),
            consuming("tectonic_verdict", MagicContent.TECTONIC_VERDICT, MagicalClasses.MAGIC_ORIGINATOR,
                    MagicContent.MAGMA_VENT, MagicContent.CREASE_FOLD));

    private MagicFusionService() {}

    private static FusionRecipe preserving(String key, MagicSkillDefinition output, ResourceLocation requiredClass,
            MagicSkillDefinition firstInput, MagicSkillDefinition secondInput) {
        return new FusionRecipe(key, output.id(), requiredClass,
                Component.translatable("screen.magical.fusion." + key + ".requirement"),
                Component.translatable("screen.magical.fusion_preserves_inputs"),
                false, false, true, input(firstInput), input(secondInput));
    }

    private static FusionRecipe consuming(String key, MagicSkillDefinition output, ResourceLocation requiredClass,
            MagicSkillDefinition firstInput, MagicSkillDefinition secondInput) {
        return new FusionRecipe(key, output.id(), requiredClass,
                Component.translatable("screen.magical.fusion." + key + ".requirement"),
                Component.translatable("screen.magical.fusion." + key + ".loss"),
                true, true, true, input(firstInput), input(secondInput));
    }

    private static Predicate<MagicSkillDefinition> input(MagicSkillDefinition target) {
        return skill -> canAppearInSlot(skill) && target.id().equals(skill.id());
    }

    public static List<FusionRecipe> recipes() {
        return RECIPES;
    }

    public static List<MagicSkillDefinition> eligibleInputs(PlayerMagicState state) {
        List<MagicSkillDefinition> result = new ArrayList<>();
        for (ResourceLocation skillId : state.unlockedSkills()) {
            MagicSkillDefinition skill = MagicContent.get(skillId);
            if (skill == null || !canAppearInSlot(skill)) {
                continue;
            }
            if (RECIPES.stream().anyMatch(recipe -> recipe.acceptsEitherSlot(skill))) {
                result.add(skill);
            }
        }
        return result;
    }

    public static FusionRecipe recipeFor(ResourceLocation firstInput, ResourceLocation secondInput) {
        MagicSkillDefinition first = MagicContent.get(firstInput);
        MagicSkillDefinition second = MagicContent.get(secondInput);
        if (first == null || second == null) {
            return null;
        }
        return RECIPES.stream().filter(recipe -> recipe.matches(first, second)).findFirst().orElse(null);
    }

    public static boolean canCreate(PlayerMagicState state, ResourceLocation firstInput, ResourceLocation secondInput) {
        FusionRecipe recipe = recipeFor(firstInput, secondInput);
        return recipe != null && recipe.canCreate(state, firstInput, secondInput);
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
        int replacementSlot = -1;
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            ResourceLocation equipped = state.equippedSkill(slot);
            if (firstInput.equals(equipped) || secondInput.equals(equipped)) {
                replacementSlot = slot;
                break;
            }
        }
        boolean replaceWheel = state.hasWheelSkill(firstInput) || state.hasWheelSkill(secondInput);
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
            if (replacementSlot >= 0) {
                state.equip(replacementSlot, recipe.outputSkill());
            }
            if (replaceWheel) {
                state.addWheelSkill(recipe.outputSkill());
            }
            player.displayClientMessage(Component.translatable("message.magical.fusion_created", Component.translatable(skill.nameKey())), false);
            state.addClassXp(recipe.requiredClass(), 35);
            state.sync(player);
            return true;
        }
        return false;
    }

    private static boolean canAppearInSlot(MagicSkillDefinition skill) {
        return !MagicContent.isCreatedSkill(skill.id()) && !MagicContent.isSubSkill(skill.id());
    }

    public record FusionRecipe(
            String key,
            ResourceLocation outputSkill,
            ResourceLocation requiredClass,
            Component requirement,
            Component lossWarning,
            boolean consumesFirstInput,
            boolean consumesSecondInput,
            boolean commutative,
            Predicate<MagicSkillDefinition> firstInput,
            Predicate<MagicSkillDefinition> secondInput) {
        public boolean acceptsEitherSlot(MagicSkillDefinition skill) {
            return firstInput.test(skill) || secondInput.test(skill);
        }

        public boolean matches(MagicSkillDefinition first, MagicSkillDefinition second) {
            if (first.id().equals(second.id())) {
                return false;
            }
            boolean direct = firstInput.test(first) && secondInput.test(second);
            return direct || commutative && firstInput.test(second) && secondInput.test(first);
        }

        public boolean canCreate(PlayerMagicState state, ResourceLocation firstInputId, ResourceLocation secondInputId) {
            MagicSkillDefinition first = MagicContent.get(firstInputId);
            MagicSkillDefinition second = MagicContent.get(secondInputId);
            return first != null
                    && second != null
                    && state.hasClass(requiredClass)
                    && state.hasUnlocked(firstInputId)
                    && state.hasUnlocked(secondInputId)
                    && !state.hasUnlocked(outputSkill)
                    && matches(first, second);
        }
    }
}
