package com.efkrdnz.magical.arcane;

import com.efkrdnz.magical.MagicalConfig;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ArcanePlayerData {
    private int mana = MagicalConfig.MAX_MANA.get();
    private final Set<ResourceLocation> unlockedComponents = new LinkedHashSet<>();
    private final List<SpellPreset> presets = new ArrayList<>();
    private int activePresetIndex = -1;

    public int mana() {
        return mana;
    }

    public void setMana(int mana) {
        this.mana = Math.max(0, Math.min(MagicalConfig.MAX_MANA.get(), mana));
    }

    public void refillMana() {
        this.mana = MagicalConfig.MAX_MANA.get();
    }

    public boolean spendMana(int amount) {
        if (mana < amount) {
            return false;
        }
        mana -= amount;
        return true;
    }

    public Set<ResourceLocation> unlockedComponents() {
        return unlockedComponents;
    }

    public boolean unlock(ResourceLocation id) {
        return unlockedComponents.add(id);
    }

    public void unlockAll(Set<ResourceLocation> ids) {
        unlockedComponents.addAll(ids);
    }

    public boolean hasUnlocked(ResourceLocation id) {
        return unlockedComponents.contains(id);
    }

    public List<SpellPreset> presets() {
        return presets;
    }

    public int activePresetIndex() {
        return activePresetIndex;
    }

    public void setActivePresetIndex(int index) {
        if (index < 0 || index >= presets.size()) {
            activePresetIndex = -1;
            return;
        }
        activePresetIndex = index;
    }

    public SpellPreset activePreset() {
        return activePresetIndex >= 0 && activePresetIndex < presets.size() ? presets.get(activePresetIndex) : null;
    }

    public int savePreset(SpellRecipe recipe, int overwriteIndex) {
        SpellPreset preset = new SpellPreset(ArcaneContent.buildPresetName(recipe), recipe.copy());
        if (overwriteIndex >= 0 && overwriteIndex < presets.size()) {
            presets.set(overwriteIndex, preset);
            return overwriteIndex;
        }
        presets.add(preset);
        return presets.size() - 1;
    }

    public void deletePreset(int index) {
        if (index < 0 || index >= presets.size()) {
            return;
        }
        presets.remove(index);
        if (activePresetIndex == index) {
            activePresetIndex = -1;
        } else if (activePresetIndex > index) {
            activePresetIndex--;
        }
    }

    public ArcanePlayerData copy() {
        ArcanePlayerData copy = new ArcanePlayerData();
        copy.mana = mana;
        copy.unlockedComponents.addAll(unlockedComponents);
        presets.forEach(preset -> copy.presets.add(new SpellPreset(preset.name(), preset.recipe().copy())));
        copy.activePresetIndex = activePresetIndex;
        return copy;
    }

    public void sync(ServerPlayer player) {
        player.setData(MagicalAttachments.ARCANE_DATA.get(), this.copy());
        MagicalNetwork.syncArcaneData(player, this);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("mana", mana);

        ListTag unlocked = new ListTag();
        for (ResourceLocation unlockedId : unlockedComponents) {
            unlocked.add(StringTag.valueOf(unlockedId.toString()));
        }
        tag.put("unlocked", unlocked);

        ListTag savedPresets = new ListTag();
        for (SpellPreset preset : presets) {
            savedPresets.add(preset.save());
        }
        tag.put("presets", savedPresets);
        tag.putInt("activePresetIndex", activePresetIndex);
        return tag;
    }

    public static ArcanePlayerData load(CompoundTag tag) {
        ArcanePlayerData data = new ArcanePlayerData();
        data.mana = tag.contains("mana") ? tag.getInt("mana") : MagicalConfig.MAX_MANA.get();

        ListTag unlocked = tag.getList("unlocked", Tag.TAG_STRING);
        for (Tag entry : unlocked) {
            data.unlockedComponents.add(ResourceLocation.parse(entry.getAsString()));
        }

        ListTag presets = tag.getList("presets", Tag.TAG_COMPOUND);
        for (Tag entry : presets) {
            data.presets.add(SpellPreset.load((CompoundTag) entry));
        }

        data.activePresetIndex = tag.getInt("activePresetIndex");
        if (data.activePresetIndex >= data.presets.size()) {
            data.activePresetIndex = -1;
        }
        return data;
    }
}
