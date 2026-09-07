package com.efkrdnz.magical.forge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.forge.art.ForgeArt;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.chain.ForgeKeptChain;
import com.efkrdnz.magical.forge.chain.LegacyRuneNames;
import com.efkrdnz.magical.registry.MagicalDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * The only read/write API for forged weapons. Combat, tooltips, and the forge UI all go through
 * this class. A weapon is "forged" either by carrying the {@link MagicalDataComponents#FORGED_WEAPON}
 * component, or - for weapons runeforged before this rework - by carrying the legacy
 * {@code magical_infusion} custom-data tag the old runeforge wrote.
 */
public final class ForgedWeapons {

    /**
     * The custom-data key the pre-rework runeforge wrote its infusion under. This class is the only
     * thing left in the mod that knows the name, and it is the only thing keeping weapons forged
     * before the rework alive: {@link #getOrMigrate} reads the tag, writes the component, and
     * strips the tag. Renaming or dropping it silently unforges every old save's weapons.
     */
    private static final String LEGACY_INFUSION_KEY = MagicalMod.MODID + "_infusion";

    private static final String LEGACY_ATTRIBUTE = "attribute";
    private static final String LEGACY_GRADE = "grade";
    private static final String LEGACY_TEMPER = "temper";
    private static final String LEGACY_BINDING = "binding";
    private static final String LEGACY_QUALITY = "quality";
    private static final int LEGACY_DEFAULT_BINDING = 2;
    private static final int UNKNOWN_ELEMENT_COLOR = 0xD8E4FF;

    private ForgedWeapons() {}

    /** The forged-weapon data if present: the component if set, else a non-mutating legacy parse. */
    public static Optional<ForgedWeapon> get(ItemStack stack) {
        ForgedWeapon component = stack.get(MagicalDataComponents.FORGED_WEAPON.get());
        return component != null ? Optional.of(component) : parseLegacy(stack);
    }

    /** Server-side: like {@link #get(ItemStack)}, but migrates a legacy-tag result onto the component. */
    public static Optional<ForgedWeapon> getOrMigrate(ItemStack stack) {
        ForgedWeapon component = stack.get(MagicalDataComponents.FORGED_WEAPON.get());
        if (component != null) {
            return Optional.of(component);
        }
        Optional<ForgedWeapon> legacy = parseLegacy(stack);
        legacy.ifPresent(weapon -> write(stack, weapon));
        return legacy;
    }

    /** Sets the component and strips any legacy tag, so a stack never carries both representations. */
    public static void write(ItemStack stack, ForgedWeapon weapon) {
        stack.set(MagicalDataComponents.FORGED_WEAPON.get(), weapon);
        stripLegacyTag(stack);
    }

    public static boolean isForged(ItemStack stack) {
        return get(stack).isPresent();
    }

    /**
     * The inscription as the bare glyph paths a kept rune names, for the forge UI to preload and
     * for the server to check kept glyphs against.
     *
     * <p>Ids from another namespace are dropped rather than matched on their path alone, so a
     * foreign {@code othermod:fire} can never back a kept {@code fire} core.</p>
     */
    public static Optional<ForgeKeptChain> keptChain(Optional<ForgedWeapon> weapon) {
        return weapon.map(forged -> new ForgeKeptChain(
                forged.grade().serializedName(),
                localPath(forged.element()).orElse(""),
                forged.temper().flatMap(ForgedWeapons::localPath),
                localPaths(forged.forms()),
                localPaths(forged.modifiers()),
                localPaths(forged.program()),
                forged.quality()));
    }

    private static Optional<String> localPath(ResourceLocation id) {
        return MagicalMod.MODID.equals(id.getNamespace()) ? Optional.of(id.getPath()) : Optional.empty();
    }

    private static List<String> localPaths(List<ResourceLocation> ids) {
        return ids.stream().flatMap(id -> localPath(id).stream()).toList();
    }

    public static List<Component> tooltip(ItemStack stack) {
        return get(stack).map(weapon -> buildTooltip(stack, weapon)).orElse(List.of());
    }

    // --- legacy tag parsing ------------------------------------------------------------------

    private static Optional<ForgedWeapon> parseLegacy(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag root = data.copyTag();
        if (!root.contains(LEGACY_INFUSION_KEY)) {
            return Optional.empty();
        }
        return buildFromLegacyTag(root.getCompound(LEGACY_INFUSION_KEY));
    }

    private static Optional<ForgedWeapon> buildFromLegacyTag(CompoundTag infusion) {
        Optional<String> elementPath = LegacyRuneNames.elementPath(infusion.getString(LEGACY_ATTRIBUTE));
        Optional<ForgeGrade> grade = LegacyRuneNames.grade(infusion.getString(LEGACY_GRADE));
        if (elementPath.isEmpty() || grade.isEmpty()) {
            return Optional.empty();
        }
        String temperPath = LegacyRuneNames.temperPath(infusion.getString(LEGACY_TEMPER));
        int binding = infusion.contains(LEGACY_BINDING) ? infusion.getInt(LEGACY_BINDING) : LEGACY_DEFAULT_BINDING;
        boolean hasQuality = infusion.contains(LEGACY_QUALITY);
        int quality = LegacyRuneNames.quality(hasQuality, hasQuality ? infusion.getInt(LEGACY_QUALITY) : 0);
        List<ResourceLocation> modifiers = LegacyRuneNames.modifiers(binding).stream().map(ForgeIds::id).toList();
        return Optional.of(new ForgedWeapon(ForgeIds.id(elementPath.get()), grade.get(), Optional.of(ForgeIds.id(temperPath)),
                // A legacy weapon has no draw order to recover, so it stores no program and keeps
                // the old whole-weapon modifier behaviour through ForgeChainCompiler.fromLegacy.
                List.of(ForgeIds.id("slash")), modifiers, List.of(), quality, 0L));
    }

    private static void stripLegacyTag(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(LEGACY_INFUSION_KEY));
        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
    }

    // --- tooltip -------------------------------------------------------------------------------

    private static List<Component> buildTooltip(ItemStack stack, ForgedWeapon weapon) {
        List<Component> lines = new ArrayList<>();
        lines.add(headerLine(stack, weapon));
        lines.add(chainLine(weapon));
        lines.add(modifiersLine(weapon));
        lines.add(temperQualityLine(weapon));
        artsLine(weapon).ifPresent(lines::add);
        return lines;
    }

    /**
     * The Arts this weapon's element and forms combine into. A chain whose pairs carry none - and
     * a weapon whose element this build cannot resolve - gets no line at all rather than an empty one.
     */
    private static Optional<Component> artsLine(ForgedWeapon weapon) {
        MutableComponent joined = null;
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (ResourceLocation formId : weapon.forms()) {
            if (!seen.add(formId)) {
                continue;
            }
            Optional<ForgeArt> art = ForgeSpecials.art(weapon.element(), formId);
            if (art.isEmpty()) {
                continue;
            }
            Component name = Component.translatable(art.get().langKey());
            joined = joined == null ? name.copy() : joined.append(", ").append(name);
        }
        return Optional.ofNullable(joined)
                .map(arts -> Component.translatable("tooltip.magical.forge.arts", arts)
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component headerLine(ItemStack stack, ForgedWeapon weapon) {
        Component gradeName = Component.translatable("forge.magical.grade." + weapon.grade().serializedName());
        boolean elementKnown = ForgeElements.get(weapon.element()).isPresent();
        Component elementName = glyphName(weapon.element(), elementKnown);
        int color = ForgeElements.get(weapon.element()).map(ElementDefinition::primaryColor).orElse(UNKNOWN_ELEMENT_COLOR);
        return Component.translatable("tooltip.magical.forge.header", gradeName, elementName, stack.getHoverName())
                .withColor(color);
    }

    private static Component chainLine(ForgedWeapon weapon) {
        MutableComponent chain = null;
        for (ResourceLocation formId : weapon.forms()) {
            Component name = glyphName(formId, ForgeForms.get(formId).isPresent());
            chain = chain == null ? name.copy() : chain.append(Component.translatable("tooltip.magical.forge.chain_sep")).append(name);
        }
        return Component.translatable("tooltip.magical.forge.chain", chain == null ? Component.empty() : chain)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component modifiersLine(ForgedWeapon weapon) {
        if (weapon.modifiers().isEmpty()) {
            return Component.translatable("tooltip.magical.forge.no_modifiers").withStyle(ChatFormatting.GRAY);
        }
        MutableComponent joined = null;
        for (ResourceLocation modifierId : weapon.modifiers()) {
            Component name = glyphName(modifierId, ForgeModifiers.get(modifierId).isPresent());
            joined = joined == null ? name.copy() : joined.append(", ").append(name);
        }
        return Component.translatable("tooltip.magical.forge.modifiers", joined).withStyle(ChatFormatting.GRAY);
    }

    private static Component temperQualityLine(ForgedWeapon weapon) {
        if (weapon.temper().isEmpty()) {
            return Component.translatable("tooltip.magical.forge.no_temper", weapon.quality()).withStyle(ChatFormatting.GRAY);
        }
        ResourceLocation temperId = weapon.temper().get();
        Component temperName = glyphName(temperId, ForgeTempers.get(temperId).isPresent());
        return Component.translatable("tooltip.magical.forge.temper_quality", temperName, weapon.quality())
                .withStyle(ChatFormatting.GRAY);
    }

    /** Known ids resolve through {@code forge.magical.glyph.<path>}; unknown ids fall back to the raw path. */
    private static MutableComponent glyphName(ResourceLocation id, boolean known) {
        return known ? Component.translatable("forge.magical.glyph." + id.getPath()) : Component.literal(id.getPath());
    }
}
