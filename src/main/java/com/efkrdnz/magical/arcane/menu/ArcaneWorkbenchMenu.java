package com.efkrdnz.magical.arcane.menu;

import com.efkrdnz.magical.arcane.ArcaneContent;
import com.efkrdnz.magical.arcane.ArcanePlayerData;
import com.efkrdnz.magical.arcane.ArcaneSpellResolver;
import com.efkrdnz.magical.arcane.SpellPreset;
import com.efkrdnz.magical.arcane.SpellRecipe;
import com.efkrdnz.magical.arcane.SpellResolution;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalMenus;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;

public final class ArcaneWorkbenchMenu extends AbstractContainerMenu {
    public static final int BUTTON_NEXT_RUNE = 0;
    public static final int BUTTON_NEXT_SHAPE = 1;
    public static final int BUTTON_TOGGLE_MODIFIER_BASE = 10;
    public static final int BUTTON_SET_RUNE_BASE = 100;
    public static final int BUTTON_SET_SHAPE_BASE = 200;
    public static final int BUTTON_ENABLE_MODIFIER_BASE = 300;
    public static final int BUTTON_DISABLE_MODIFIER_BASE = 400;
    public static final int BUTTON_CLEAR_MODIFIERS = 500;
    public static final int BUTTON_SAVE = 30;
    public static final int BUTTON_LOAD_NEXT = 31;
    public static final int BUTTON_DELETE = 32;
    public static final int BUTTON_SET_ACTIVE = 33;

    private final Player player;
    private final ArcanePlayerData playerData;
    private final SpellRecipe workingRecipe;
    private final ContainerData data;
    private int loadedPresetIndex = -1;

    public ArcaneWorkbenchMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.ARCANE_WORKBENCH.get(), containerId);
        this.player = inventory.player;
        this.playerData = player.getData(MagicalAttachments.ARCANE_DATA);
        SpellPreset activePreset = playerData.activePreset();
        this.workingRecipe = activePreset != null ? activePreset.recipe().copy() : SpellRecipe.starter();
        this.loadedPresetIndex = playerData.activePresetIndex();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                SpellResolution resolution = ArcaneSpellResolver.resolve(workingRecipe);
                return switch (index) {
                    case 0 -> ArcaneContent.runeIndex(workingRecipe.runeId());
                    case 1 -> ArcaneContent.shapeIndex(workingRecipe.shapeId());
                    case 2 -> modifierBitMask();
                    case 3 -> resolution.manaCost();
                    case 4 -> resolution.stability();
                    case 5 -> playerData.presets().size();
                    case 6 -> loadedPresetIndex;
                    case 7 -> playerData.activePresetIndex();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return 8;
            }
        };
        addDataSlots(data);
        addDataSlot(DataSlot.standalone());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_NEXT_RUNE) {
            workingRecipe.setRuneId(ArcaneContent.runeIdByIndex(data.get(0) + 1));
            return true;
        }
        if (id == BUTTON_NEXT_SHAPE) {
            workingRecipe.setShapeId(ArcaneContent.shapeIdByIndex(data.get(1) + 1));
            return true;
        }
        if (id >= BUTTON_SET_RUNE_BASE && id < BUTTON_SET_RUNE_BASE + ArcaneContent.RUNES.size()) {
            workingRecipe.setRuneId(ArcaneContent.runeIdByIndex(id - BUTTON_SET_RUNE_BASE));
            return true;
        }
        if (id >= BUTTON_SET_SHAPE_BASE && id < BUTTON_SET_SHAPE_BASE + ArcaneContent.SHAPES.size()) {
            workingRecipe.setShapeId(ArcaneContent.shapeIdByIndex(id - BUTTON_SET_SHAPE_BASE));
            return true;
        }
        if (id >= BUTTON_TOGGLE_MODIFIER_BASE && id < BUTTON_TOGGLE_MODIFIER_BASE + ArcaneContent.MODIFIERS.size()) {
            ResourceLocation modifierId = ArcaneContent.modifierIdByIndex(id - BUTTON_TOGGLE_MODIFIER_BASE);
            if (playerData.hasUnlocked(modifierId)) {
                workingRecipe.toggleModifier(modifierId);
            }
            return true;
        }
        if (id >= BUTTON_ENABLE_MODIFIER_BASE && id < BUTTON_ENABLE_MODIFIER_BASE + ArcaneContent.MODIFIERS.size()) {
            ResourceLocation modifierId = ArcaneContent.modifierIdByIndex(id - BUTTON_ENABLE_MODIFIER_BASE);
            if (playerData.hasUnlocked(modifierId) && !workingRecipe.modifierIds().contains(modifierId)) {
                workingRecipe.toggleModifier(modifierId);
            }
            return true;
        }
        if (id >= BUTTON_DISABLE_MODIFIER_BASE && id < BUTTON_DISABLE_MODIFIER_BASE + ArcaneContent.MODIFIERS.size()) {
            ResourceLocation modifierId = ArcaneContent.modifierIdByIndex(id - BUTTON_DISABLE_MODIFIER_BASE);
            if (workingRecipe.modifierIds().contains(modifierId)) {
                workingRecipe.toggleModifier(modifierId);
            }
            return true;
        }
        if (id == BUTTON_CLEAR_MODIFIERS) {
            workingRecipe.setModifiers(List.of());
            return true;
        }
        if (id == BUTTON_SAVE) {
            loadedPresetIndex = playerData.savePreset(workingRecipe, loadedPresetIndex);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                playerData.sync(serverPlayer);
            }
            return true;
        }
        if (id == BUTTON_LOAD_NEXT) {
            if (playerData.presets().isEmpty()) {
                loadedPresetIndex = -1;
                return true;
            }
            loadedPresetIndex = Math.floorMod(loadedPresetIndex + 1, playerData.presets().size());
            SpellPreset preset = playerData.presets().get(loadedPresetIndex);
            workingRecipe.setRuneId(preset.recipe().runeId());
            workingRecipe.setShapeId(preset.recipe().shapeId());
            workingRecipe.setModifiers(preset.recipe().modifierIds());
            return true;
        }
        if (id == BUTTON_DELETE) {
            playerData.deletePreset(loadedPresetIndex);
            if (playerData.presets().isEmpty()) {
                loadedPresetIndex = -1;
            } else {
                loadedPresetIndex = Math.min(loadedPresetIndex, playerData.presets().size() - 1);
            }
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                playerData.sync(serverPlayer);
            }
            return true;
        }
        if (id == BUTTON_SET_ACTIVE) {
            if (loadedPresetIndex < 0) {
                loadedPresetIndex = playerData.savePreset(workingRecipe, -1);
            }
            playerData.setActivePresetIndex(loadedPresetIndex);
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                playerData.sync(serverPlayer);
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        broadcastChanges();
    }

    public SpellRecipe workingRecipe() {
        return workingRecipe;
    }

    public ArcanePlayerData playerData() {
        return playerData;
    }

    public int runeIndex() {
        return data.get(0);
    }

    public int shapeIndex() {
        return data.get(1);
    }

    public int modifierBitMask() {
        int bits = 0;
        for (ResourceLocation modifierId : workingRecipe.uniqueModifierIds()) {
            bits |= 1 << ArcaneContent.modifierIndex(modifierId);
        }
        return bits;
    }

    public int manaCost() {
        return data.get(3);
    }

    public int stability() {
        return data.get(4);
    }

    public int presetCount() {
        return data.get(5);
    }

    public int loadedPresetIndex() {
        return data.get(6);
    }

    public int activePresetIndex() {
        return data.get(7);
    }
}
