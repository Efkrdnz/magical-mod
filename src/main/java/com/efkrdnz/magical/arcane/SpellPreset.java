package com.efkrdnz.magical.arcane;

import net.minecraft.nbt.CompoundTag;

public record SpellPreset(String name, SpellRecipe recipe) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.put("recipe", recipe.save());
        return tag;
    }

    public static SpellPreset load(CompoundTag tag) {
        return new SpellPreset(tag.getString("name"), SpellRecipe.load(tag.getCompound("recipe")));
    }
}
