package com.efkrdnz.magical.forge;

import java.util.Optional;

import com.efkrdnz.magical.forge.chain.ForgeMaterial;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;
import com.efkrdnz.magical.item.MagicalWeaponItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.Repairable;

/** Detects the material tier and weapon shape of a stack, for forge-eligibility and grade caps. */
public final class ForgeMaterials {

    private static final TagKey<Item> WOOD_TAG = TagKey.create(Registries.ITEM, ForgeIds.id("forge_material/wood"));
    private static final TagKey<Item> STONE_TAG = TagKey.create(Registries.ITEM, ForgeIds.id("forge_material/stone"));
    private static final TagKey<Item> IRON_TAG = TagKey.create(Registries.ITEM, ForgeIds.id("forge_material/iron"));
    private static final TagKey<Item> GOLD_TAG = TagKey.create(Registries.ITEM, ForgeIds.id("forge_material/gold"));
    private static final TagKey<Item> DIAMOND_TAG = TagKey.create(Registries.ITEM, ForgeIds.id("forge_material/diamond"));
    private static final TagKey<Item> NETHERITE_TAG = TagKey.create(Registries.ITEM, ForgeIds.id("forge_material/netherite"));
    private static final TagKey<Item> FORGEABLE = TagKey.create(Registries.ITEM, ForgeIds.id("forgeable"));

    private ForgeMaterials() {}

    public static ForgeMaterial detect(ItemStack stack) {
        return catalogue(stack).map(WeaponDefinition::gradeCap)
                .or(() -> detectByTag(stack))
                .or(() -> detectByRepairable(stack))
                .or(() -> detectByPrefix(stack))
                .orElse(ForgeMaterial.UNKNOWN);
    }

    public static boolean isForgeable(ItemStack stack) {
        return stack.getItem() instanceof MagicalWeaponItem
                || stack.is(FORGEABLE) || stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }

    /**
     * The archetype, which decides reach, knockback, recovery and half the glyph vocabulary.
     *
     * <p>A catalogue weapon declares its own rather than being guessed at from item tags, which is
     * the only way a scythe and a greatsword can be told apart at all - both are built on vanilla's
     * sword properties, so both are in {@code ItemTags.SWORDS}.
     */
    public static Optional<WeaponClass> weaponClass(ItemStack stack) {
        Optional<WeaponDefinition> catalogue = catalogue(stack);
        if (catalogue.isPresent()) {
            return catalogue.map(WeaponDefinition::archetype);
        }
        if (stack.is(ItemTags.AXES) || stack.getItem() instanceof AxeItem) {
            return Optional.of(WeaponClass.AXE);
        }
        if (stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem) {
            return Optional.of(WeaponClass.SWORD);
        }
        return Optional.empty();
    }

    /** The catalogue row behind a stack, or empty for a vanilla weapon. */
    public static Optional<WeaponDefinition> catalogue(ItemStack stack) {
        return stack.getItem() instanceof MagicalWeaponItem weapon
                ? Optional.of(weapon.definition())
                : Optional.empty();
    }

    private static Optional<ForgeMaterial> detectByTag(ItemStack stack) {
        if (stack.is(WOOD_TAG)) {
            return Optional.of(ForgeMaterial.WOOD);
        }
        if (stack.is(STONE_TAG)) {
            return Optional.of(ForgeMaterial.STONE);
        }
        if (stack.is(IRON_TAG)) {
            return Optional.of(ForgeMaterial.IRON);
        }
        if (stack.is(GOLD_TAG)) {
            return Optional.of(ForgeMaterial.GOLD);
        }
        if (stack.is(DIAMOND_TAG)) {
            return Optional.of(ForgeMaterial.DIAMOND);
        }
        if (stack.is(NETHERITE_TAG)) {
            return Optional.of(ForgeMaterial.NETHERITE);
        }
        return Optional.empty();
    }

    private static Optional<ForgeMaterial> detectByRepairable(ItemStack stack) {
        Repairable repairable = stack.get(DataComponents.REPAIRABLE);
        if (repairable == null) {
            return Optional.empty();
        }
        Optional<TagKey<Item>> tagKey = repairable.items().unwrapKey();
        if (tagKey.isEmpty()) {
            return Optional.empty();
        }
        TagKey<Item> key = tagKey.get();
        if (key.equals(ItemTags.WOODEN_TOOL_MATERIALS)) {
            return Optional.of(ForgeMaterial.WOOD);
        }
        if (key.equals(ItemTags.STONE_TOOL_MATERIALS)) {
            return Optional.of(ForgeMaterial.STONE);
        }
        if (key.equals(ItemTags.IRON_TOOL_MATERIALS)) {
            return Optional.of(ForgeMaterial.IRON);
        }
        if (key.equals(ItemTags.GOLD_TOOL_MATERIALS)) {
            return Optional.of(ForgeMaterial.GOLD);
        }
        if (key.equals(ItemTags.DIAMOND_TOOL_MATERIALS)) {
            return Optional.of(ForgeMaterial.DIAMOND);
        }
        if (key.equals(ItemTags.NETHERITE_TOOL_MATERIALS)) {
            return Optional.of(ForgeMaterial.NETHERITE);
        }
        return Optional.empty();
    }

    private static Optional<ForgeMaterial> detectByPrefix(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        if (path.startsWith("wooden_")) {
            return Optional.of(ForgeMaterial.WOOD);
        }
        if (path.startsWith("stone_")) {
            return Optional.of(ForgeMaterial.STONE);
        }
        if (path.startsWith("iron_")) {
            return Optional.of(ForgeMaterial.IRON);
        }
        if (path.startsWith("golden_")) {
            return Optional.of(ForgeMaterial.GOLD);
        }
        if (path.startsWith("diamond_")) {
            return Optional.of(ForgeMaterial.DIAMOND);
        }
        if (path.startsWith("netherite_")) {
            return Optional.of(ForgeMaterial.NETHERITE);
        }
        return Optional.empty();
    }
}
