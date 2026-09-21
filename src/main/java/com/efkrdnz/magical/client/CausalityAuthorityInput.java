package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Holding a slot that carries the Causal Anchor opens the chooser; letting go marks what it settled
 * on.
 *
 * <p>The same hold-and-release shape the other Authorities use for the thing they author. What the
 * Anchor authors is not a rule but a <em>referent</em>: which body all the marked pins on the board
 * are talking about. That is worth the same ceremony as a law or a fault, and a good deal more than
 * a keypress.
 */
public final class CausalityAuthorityInput {

    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private CausalityAuthorityInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean anchorSlot = ClientMagicState.get().hasAuthority(AuthorityContent.CAUSALITY)
                && MagicContent.CAUSAL_ANCHOR.id().equals(skillId);
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!anchorSlot) {
            if (WAS_DOWN[slot]) {
                WAS_DOWN[slot] = false;
                if (CausalAnchorOverlay.active()) {
                    CausalAnchorOverlay.finish();
                }
            }
            return false;
        }
        if (down && !WAS_DOWN[slot]) {
            CausalAnchorOverlay.begin(minecraft);
        } else if (!down && WAS_DOWN[slot]) {
            CausalAnchorOverlay.finish();
        } else if (down) {
            // The world moves under an open hold: bodies die, walk out of reach, walk into it.
            CausalAnchorOverlay.tick(minecraft);
        }
        WAS_DOWN[slot] = down;
        return true;
    }
}
