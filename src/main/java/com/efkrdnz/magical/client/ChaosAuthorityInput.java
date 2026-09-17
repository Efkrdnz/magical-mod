package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Holding a slot that carries the Fracture opens the five gates; letting go commits them.
 *
 * <p>The same hold-and-release shape the other Authorities use for the thing they author, because a
 * sequence is chosen rather than pressed - but what it opens is a row read left to right, not a
 * ring of dials.
 */
public final class ChaosAuthorityInput {

    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private ChaosAuthorityInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean fractureSlot = ClientMagicState.get().hasAuthority(AuthorityContent.CHAOS)
                && MagicContent.FRACTURE.id().equals(skillId);
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!fractureSlot) {
            if (WAS_DOWN[slot]) {
                WAS_DOWN[slot] = false;
                if (FractureOverlay.active()) {
                    FractureOverlay.finish();
                }
            }
            return false;
        }
        if (down && !WAS_DOWN[slot]) {
            FractureOverlay.begin();
        } else if (!down && WAS_DOWN[slot]) {
            FractureOverlay.finish();
        }
        WAS_DOWN[slot] = down;
        return true;
    }
}
