package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Holding a slot that carries Writ opens the Ledger; letting go declares what is on it.
 *
 * <p>The same hold-and-release shape {@code SpaceAuthorityInput} gives Manipulate Space, for the
 * same reason - a grammar this wide is chosen, not pressed - but what it opens is a page of spells
 * the wielder has witnessed rather than a fixed set of dials.
 */
public final class ManaAuthorityInput {

    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private ManaAuthorityInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean writSlot = ClientMagicState.get().hasAuthority(AuthorityContent.MANA)
                && MagicContent.WRIT.id().equals(skillId);
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!writSlot) {
            if (WAS_DOWN[slot]) {
                WAS_DOWN[slot] = false;
                if (WritOverlay.active()) {
                    WritOverlay.finish();
                }
            }
            return false;
        }
        if (down && !WAS_DOWN[slot]) {
            WritOverlay.begin();
        } else if (!down && WAS_DOWN[slot]) {
            WritOverlay.finish();
        }
        WAS_DOWN[slot] = down;
        return true;
    }
}
