package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.MagicalKeyMappings;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.client.Minecraft;

/** The Daydream key is a toggle the client owns: no cast packet is ever sent for it. */
public final class DaydreamInput {
    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private DaydreamInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        boolean daydreamSlot = ClientMagicState.get().hasAuthority(AuthorityContent.MIND)
                && MagicContent.DAYDREAM.id().equals(ClientMagicState.get().equippedSkill(slot));
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!daydreamSlot) {
            WAS_DOWN[slot] = false;
            return false;
        }
        if (down && !WAS_DOWN[slot]) {
            DaydreamMode.toggle(minecraft);
        }
        WAS_DOWN[slot] = down;
        return true;
    }
}
