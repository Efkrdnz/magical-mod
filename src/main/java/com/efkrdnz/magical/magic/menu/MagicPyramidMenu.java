package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicFusionService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillTuningView;
import com.efkrdnz.magical.magic.MagicTuningStat;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class MagicPyramidMenu extends AbstractContainerMenu {
    private static final int PLAYER_INV_X = 40;
    private static final int PLAYER_INV_Y = 242;
    private static final int HOTBAR_Y = 300;
    private static final int PLAYER_INVENTORY_START = 0;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;

    public static final int BUTTON_TIER_BASE = 100;
    public static final int BUTTON_BELOW_TIER_BASE = 120;
    public static final int BUTTON_SKILL_BASE = 200;
    public static final int BUTTON_SLOT_BASE = 300;
    public static final int BUTTON_EQUIP_SELECTED = 400;
    public static final int BUTTON_CLEAR_SLOT = 401;
    public static final int BUTTON_TOGGLE_WHEEL = 402;
    public static final int BUTTON_REMOVE_WHEEL_SELECTED = 403;
    public static final int BUTTON_MOVE_WHEEL_UP = 404;
    public static final int BUTTON_MOVE_WHEEL_DOWN = 405;
    public static final int BUTTON_WHEEL_SELECT_BASE = 500;
    public static final int BUTTON_CLASS_SELECT_BASE = 700;
    public static final int BUTTON_EVOLVE_SELECTED_CLASS = 800;
    public static final int BUTTON_EVOLVE_CLASS_BASE = 900;
    public static final int BUTTON_TUNE_BASE = 1000;
    public static final int BUTTON_PASSIVE_TOGGLE_BASE = 1300;
    public static final int BUTTON_CURSE_DISPEL_BASE = 1500;
    public static final int BUTTON_AEGIS_TUNE_BASE = 1800;
    public static final int BUTTON_OPEN_CLASS_SELECT = 1900;
    // 1800-1999 is the aegis tune span; these two only work because their equality tests run
    // before that range test in clickMenuButton. Do not add more ids in that band.
    public static final int BUTTON_OPEN_CLASS_TREE = 1901;
    public static final int BUTTON_FUSION_CREATE_BASE = MagicFusionService.BUTTON_BASE;

    private final Player player;
    private final PlayerMagicState state;
    private int selectedTier;
    private int selectedSkillIndex;
    private int selectedSlot;
    private int selectedWheelIndex;
    private int selectedClassIndex;
    /** Sub-view the codex was opened into, synced so the client screen can jump straight there. */
    private int pendingView;
    private final ContainerData data;

    public MagicPyramidMenu(int containerId, Inventory inventory) {
        super(MagicalMenus.MAGIC_PYRAMID.get(), containerId);
        this.player = inventory.player;
        this.state = player.getData(MagicalAttachments.MAGIC_STATE);
        this.selectedTier = initialSelectedTier();
        this.selectedSkillIndex = firstUnlockedSkillIndexForTier(selectedTier);
        this.selectedSlot = 0;
        this.pendingView = player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ? com.efkrdnz.magical.magic.MagicCodexService.claimPendingView(serverPlayer.getUUID()).ordinal()
                : 0;
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> selectedTier;
                    case 1 -> selectedSkillIndex;
                    case 2 -> selectedSlot;
                    case 3 -> selectedWheelIndex;
                    case 4 -> selectedClassIndex;
                    case 5 -> pendingView;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                    case 0 -> selectedTier = value;
                    case 1 -> selectedSkillIndex = value;
                    case 2 -> selectedSlot = value;
                    case 3 -> selectedWheelIndex = value;
                    case 4 -> selectedClassIndex = value;
                    case 5 -> pendingView = value;
                    default -> {
                    }
                }
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
        addDataSlots(data);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_INV_X + column * 18, HOTBAR_Y));
        }
    }

    /** The codex holds no slots of its own, so there is nowhere to shift-click an item to. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    public int playerInventoryStart() {
        return PLAYER_INVENTORY_START;
    }

    public int playerInventoryEnd() {
        return PLAYER_INVENTORY_END;
    }


    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_TIER_BASE && id < BUTTON_TIER_BASE + MagicContent.maxTier() + 1) {
            int tier = id - BUTTON_TIER_BASE;
            if (!tierHasUnlockedSkill(tier)) {
                return true;
            }
            selectedTier = tier;
            selectedSkillIndex = firstUnlockedSkillIndexForTier(tier);
            return true;
        }
        if (id >= BUTTON_BELOW_TIER_BASE && id < BUTTON_BELOW_TIER_BASE + belowTierCount()) {
            int tier = -1 - (id - BUTTON_BELOW_TIER_BASE);
            if (!tierHasUnlockedSkill(tier)) {
                return true;
            }
            selectedTier = tier;
            selectedSkillIndex = firstUnlockedSkillIndexForTier(tier);
            return true;
        }
        if (id >= BUTTON_SKILL_BASE && id < BUTTON_SKILL_BASE + MagicContent.orderedSkillIds().size()) {
            ResourceLocation skillId = MagicContent.skillIdByIndex(id - BUTTON_SKILL_BASE);
            MagicSkillDefinition definition = MagicContent.get(skillId);
            if (definition != null && definition.tier() == selectedTier) {
                selectedSkillIndex = id - BUTTON_SKILL_BASE;
            }
            return true;
        }
        if (id >= BUTTON_SLOT_BASE && id < BUTTON_SLOT_BASE + MagicContent.LOADOUT_SIZE) {
            selectedSlot = id - BUTTON_SLOT_BASE;
            return true;
        }
        if (id == BUTTON_EQUIP_SELECTED) {
            ResourceLocation skillId = selectedSkillId();
            if (skillId != null) {
                state.equip(selectedSlot, skillId);
                syncIfServer();
            }
            return true;
        }
        if (id == BUTTON_CLEAR_SLOT) {
            state.clearSlot(selectedSlot);
            syncIfServer();
            return true;
        }
        if (id == BUTTON_TOGGLE_WHEEL) {
            ResourceLocation skillId = selectedSkillId();
            if (skillId != null) {
                state.toggleWheelSkill(skillId);
                clampSelectedWheelIndex();
                syncIfServer();
            }
            return true;
        }
        if (id >= BUTTON_WHEEL_SELECT_BASE && id < BUTTON_WHEEL_SELECT_BASE + state.wheelSkills().size()) {
            selectedWheelIndex = id - BUTTON_WHEEL_SELECT_BASE;
            return true;
        }
        if (id == BUTTON_REMOVE_WHEEL_SELECTED) {
            state.removeWheelSkillAt(selectedWheelIndex);
            clampSelectedWheelIndex();
            syncIfServer();
            return true;
        }
        if (id == BUTTON_MOVE_WHEEL_UP) {
            state.moveWheelSkill(selectedWheelIndex, -1);
            selectedWheelIndex = Math.max(0, selectedWheelIndex - 1);
            syncIfServer();
            return true;
        }
        if (id == BUTTON_MOVE_WHEEL_DOWN) {
            state.moveWheelSkill(selectedWheelIndex, 1);
            selectedWheelIndex = Math.min(state.wheelSkills().size() - 1, selectedWheelIndex + 1);
            syncIfServer();
            return true;
        }
        if (id == BUTTON_OPEN_CLASS_TREE) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && state.hasAnyRootClass()) {
                com.efkrdnz.magical.magic.ClassTreeService.open(serverPlayer);
            }
            return true;
        }
        if (id == BUTTON_OPEN_CLASS_SELECT) {
            // Reopen path for a player who still has no root class: swap the codex for the chooser.
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && !state.hasAnyRootClass()) {
                com.efkrdnz.magical.magic.ClassSelectService.open(serverPlayer);
            }
            return true;
        }
        if (id >= BUTTON_CLASS_SELECT_BASE && id < BUTTON_CLASS_SELECT_BASE + MagicalClasses.all().size()) {
            selectedClassIndex = id - BUTTON_CLASS_SELECT_BASE;
            return true;
        }
        if (id == BUTTON_EVOLVE_SELECTED_CLASS) {
            MagicalClassDefinition selected = selectedClass();
            if (selected != null) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    state.evolveClass(serverPlayer, selected.id());
                } else {
                    state.evolveClass(selected.id());
                }
                syncIfServer();
            }
            return true;
        }
        if (id >= BUTTON_EVOLVE_CLASS_BASE && id < BUTTON_EVOLVE_CLASS_BASE + MagicalClasses.all().size()) {
            int index = id - BUTTON_EVOLVE_CLASS_BASE;
            int current = 0;
            for (MagicalClassDefinition definition : MagicalClasses.all()) {
                if (current == index) {
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        state.evolveClass(serverPlayer, definition.id());
                    } else {
                        state.evolveClass(definition.id());
                    }
                    syncIfServer();
                    return true;
                }
                current++;
            }
            return true;
        }
        int fusionSkillCount = MagicContent.orderedSkillIds().size();
        if (id >= BUTTON_FUSION_CREATE_BASE && id < BUTTON_FUSION_CREATE_BASE + fusionSkillCount * fusionSkillCount) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                int encoded = id - BUTTON_FUSION_CREATE_BASE;
                ResourceLocation firstInput = MagicContent.skillIdByIndex(encoded / fusionSkillCount);
                ResourceLocation secondInput = MagicContent.skillIdByIndex(encoded % fusionSkillCount);
                MagicFusionService.create(serverPlayer, state, firstInput, secondInput);
            }
            return true;
        }
        if (id >= BUTTON_TUNE_BASE && id < BUTTON_TUNE_BASE + (MagicTuningStat.values().length * 10)) {
            ResourceLocation skillId = selectedSkillId();
            if (skillId == null) {
                return true;
            }
            int encoded = id - BUTTON_TUNE_BASE;
            MagicTuningStat stat = MagicTuningStat.values()[encoded / 10];
            MagicSkillDefinition skill = MagicContent.get(skillId);
            if (!MagicSkillTuningView.statsFor(skill).contains(stat)) {
                return true;
            }
            int delta = encoded % 10 == 0 ? -1 : 1;
            state.adjustTuning(skillId, stat, delta);
            syncIfServer();
            return true;
        }
        int parentSubskillTuneLimit = java.util.stream.Stream.of(
                        MagicContent.gabrielSubSkills(),
                        MagicContent.blackFlamesSubSkills(),
                        MagicContent.spatialArsenalSubSkills(),
                        MagicContent.soulVowSubSkills())
                .mapToInt(java.util.List::size)
                .max()
                .orElse(0) * MagicTuningStat.values().length * 10;
        if (id >= BUTTON_AEGIS_TUNE_BASE && id < BUTTON_AEGIS_TUNE_BASE + parentSubskillTuneLimit) {
            ResourceLocation selectedId = selectedSkillId();
            java.util.List<MagicSkillDefinition> subSkills;
            if (MagicContent.GABRIEL.id().equals(selectedId) && state.hasUnlocked(MagicContent.GABRIEL.id())) {
                subSkills = MagicContent.gabrielSubSkills();
            } else if (MagicContent.BLACK_FLAMES.id().equals(selectedId) && state.hasUnlocked(MagicContent.BLACK_FLAMES.id())) {
                subSkills = MagicContent.blackFlamesSubSkills();
            } else if (MagicContent.SPATIAL_ARSENAL.id().equals(selectedId) && state.hasUnlocked(MagicContent.SPATIAL_ARSENAL.id())) {
                subSkills = MagicContent.spatialArsenalSubSkills();
            } else if (MagicContent.SOUL_VOW.id().equals(selectedId) && state.hasUnlocked(MagicContent.SOUL_VOW.id())) {
                subSkills = MagicContent.soulVowSubSkills();
            } else {
                return true;
            }
            int encoded = id - BUTTON_AEGIS_TUNE_BASE;
            int subIndex = encoded / (MagicTuningStat.values().length * 10);
            int remainder = encoded % (MagicTuningStat.values().length * 10);
            if (subIndex < 0 || subIndex >= subSkills.size()) {
                return true;
            }
            MagicSkillDefinition skill = subSkills.get(subIndex);
            MagicTuningStat stat = MagicTuningStat.values()[remainder / 10];
            if (!MagicSkillTuningView.statsFor(skill).contains(stat)) {
                return true;
            }
            int delta = remainder % 10 == 0 ? -1 : 1;
            state.adjustTuning(skill.id(), stat, delta);
            syncIfServer();
            return true;
        }
        if (id >= BUTTON_PASSIVE_TOGGLE_BASE && id < BUTTON_PASSIVE_TOGGLE_BASE + MagicPassiveContent.normalPassives().size()) {
            ResourceLocation passiveId = MagicPassiveContent.normalPassives().get(id - BUTTON_PASSIVE_TOGGLE_BASE).id();
            state.togglePassive(passiveId);
            syncIfServer();
            return true;
        }
        if (id >= BUTTON_CURSE_DISPEL_BASE && id < BUTTON_CURSE_DISPEL_BASE + MagicPassiveContent.curses().size()) {
            ResourceLocation curseId = MagicPassiveContent.curses().get(id - BUTTON_CURSE_DISPEL_BASE).id();
            state.dispelCurse(curseId);
            syncIfServer();
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    public int selectedTier() {
        return data.get(0);
    }

    public int selectedSkillIndex() {
        return data.get(1);
    }

    public int selectedSlot() {
        return data.get(2);
    }

    public int selectedWheelIndex() {
        return data.get(3);
    }

    /** Sub-view requested when the codex was opened; the screen consumes this once on init. */
    public int pendingView() {
        return data.get(5);
    }

    public int selectedClassIndex() {
        return data.get(4);
    }

    public MagicalClassDefinition selectedClass() {
        int index = 0;
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            if (index == selectedClassIndex) {
                return definition;
            }
            index++;
        }
        return MagicalClasses.get(MagicalClasses.BLACKSMITH);
    }

    public ResourceLocation selectedSkillId() {
        if (selectedSkillIndex < 0 || selectedSkillIndex >= MagicContent.orderedSkillIds().size()) {
            return null;
        }
        ResourceLocation skillId = MagicContent.skillIdByIndex(selectedSkillIndex);
        MagicSkillDefinition definition = MagicContent.get(skillId);
        return definition != null && definition.tier() == selectedTier ? skillId : null;
    }

    private int firstUnlockedSkillIndexForTier(int tier) {
        for (MagicSkillDefinition definition : MagicContent.skillsForTier(tier)) {
            if (state.hasUnlocked(definition.id())) {
                return MagicContent.skillIndex(definition.id());
            }
        }
        return -1;
    }

    private int initialSelectedTier() {
        int preferred = Math.min(state.proficiencyLevel(), MagicContent.maxTier());
        for (int tier = preferred; tier >= 0; tier--) {
            if (tierHasUnlockedSkill(tier)) {
                return tier;
            }
        }
        for (int tier = MagicContent.maxTier(); tier >= MagicContent.minTier(); tier--) {
            if (tierHasUnlockedSkill(tier)) {
                return tier;
            }
        }
        return 0;
    }

    private boolean tierHasUnlockedSkill(int tier) {
        for (MagicSkillDefinition definition : MagicContent.skillsForTier(tier)) {
            if (state.hasUnlocked(definition.id())) {
                return true;
            }
        }
        return false;
    }

    private int belowTierCount() {
        int count = Math.max(3, -Math.min(MagicContent.minTier(), -1));
        return tierHasUnlockedSkill(-5) ? Math.max(count, 5) : count;
    }

    private void clampSelectedWheelIndex() {
        if (state.wheelSkills().isEmpty()) {
            selectedWheelIndex = 0;
        } else {
            selectedWheelIndex = Math.max(0, Math.min(selectedWheelIndex, state.wheelSkills().size() - 1));
        }
    }

    private void syncIfServer() {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            state.sync(serverPlayer);
        }
    }

}
