package com.efkrdnz.magical.arcane;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class SpellRecipe {
    private ResourceLocation runeId;
    private ResourceLocation shapeId;
    private final List<ResourceLocation> modifierIds;

    public SpellRecipe(ResourceLocation runeId, ResourceLocation shapeId, List<ResourceLocation> modifierIds) {
        this.runeId = runeId;
        this.shapeId = shapeId;
        this.modifierIds = new ArrayList<>(modifierIds);
    }

    public static SpellRecipe starter() {
        return new SpellRecipe(
                ArcaneContent.DEFAULT_RUNE.id(),
                ArcaneContent.DEFAULT_SHAPE.id(),
                List.of());
    }

    public ResourceLocation runeId() {
        return runeId;
    }

    public ResourceLocation shapeId() {
        return shapeId;
    }

    public List<ResourceLocation> modifierIds() {
        return modifierIds;
    }

    public void setRuneId(ResourceLocation runeId) {
        this.runeId = runeId;
    }

    public void setShapeId(ResourceLocation shapeId) {
        this.shapeId = shapeId;
    }

    public void setModifiers(List<ResourceLocation> ids) {
        this.modifierIds.clear();
        this.modifierIds.addAll(ids);
    }

    public boolean toggleModifier(ResourceLocation modifierId) {
        if (modifierIds.contains(modifierId)) {
            modifierIds.remove(modifierId);
            return false;
        }
        modifierIds.add(modifierId);
        return true;
    }

    public SpellRecipe copy() {
        return new SpellRecipe(runeId, shapeId, modifierIds);
    }

    public Set<ResourceLocation> uniqueModifierIds() {
        return new LinkedHashSet<>(modifierIds);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("rune", runeId.toString());
        tag.putString("shape", shapeId.toString());
        ListTag list = new ListTag();
        for (ResourceLocation modifierId : modifierIds) {
            list.add(StringTag.valueOf(modifierId.toString()));
        }
        tag.put("modifiers", list);
        return tag;
    }

    public static SpellRecipe load(CompoundTag tag) {
        ResourceLocation runeId = ResourceLocation.parse(tag.getString("rune"));
        ResourceLocation shapeId = ResourceLocation.parse(tag.getString("shape"));
        List<ResourceLocation> modifierIds = new ArrayList<>();
        ListTag list = tag.getList("modifiers", Tag.TAG_STRING);
        for (Tag entry : list) {
            modifierIds.add(ResourceLocation.parse(entry.getAsString()));
        }
        return new SpellRecipe(runeId, shapeId, modifierIds);
    }
}
