package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.SpaceAuthorityService;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class SpaceAuthorityInput {
    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];
    private static final int[] CHARGE_TICKS = new int[MagicContent.LOADOUT_SIZE];

    private SpaceAuthorityInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean authoritySlot = ClientMagicState.get().hasAuthority(AuthorityContent.SPACE)
                && (MagicContent.CREATE_SUBSPACE.id().equals(skillId) || MagicContent.MANIPULATE_SPACE.id().equals(skillId));
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!authoritySlot) {
            if (WAS_DOWN[slot]) {
                WAS_DOWN[slot] = false;
                CHARGE_TICKS[slot] = 0;
                if (SpaceManipulationOverlay.active()) {
                    SpaceManipulationOverlay.finish();
                }
            }
            return false;
        }

        if (MagicContent.CREATE_SUBSPACE.id().equals(skillId)) {
            handleCreateSubspace(minecraft, slot, down);
        } else {
            handleManipulateSpace(slot, down);
        }
        WAS_DOWN[slot] = down;
        return true;
    }

    private static void handleCreateSubspace(Minecraft minecraft, int slot, boolean down) {
        if (down && !WAS_DOWN[slot]) {
            CHARGE_TICKS[slot] = 0;
            MagicalNetwork.sendSpaceSubspaceHold(slot, false, 0, false);
        } else if (down) {
            CHARGE_TICKS[slot] = Math.min(SpaceAuthorityService.MAX_CHARGE_TICKS, CHARGE_TICKS[slot] + 1);
        } else if (WAS_DOWN[slot]) {
            MagicalNetwork.sendSpaceSubspaceHold(slot, true, CHARGE_TICKS[slot], minecraft.player != null && minecraft.player.isShiftKeyDown());
            CHARGE_TICKS[slot] = 0;
        }
    }

    private static void handleManipulateSpace(int slot, boolean down) {
        if (down && !WAS_DOWN[slot]) {
            SpaceManipulationOverlay.begin();
        } else if (!down && WAS_DOWN[slot]) {
            SpaceManipulationOverlay.finish();
        }
    }
}
