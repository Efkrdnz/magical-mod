package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Holding a slot that carries Weave Rules opens the three wheels; letting go writes the rule.
 *
 * <p>The same shape {@code SpaceAuthorityInput} gives Manipulate Space, and for the same reason: a
 * grammar of thirty-six declarations is chosen, not pressed.
 */
public final class ManaAuthorityInput {

    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private ManaAuthorityInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean weaveSlot = ClientMagicState.get().hasAuthority(AuthorityContent.MANA)
                && MagicContent.WEAVE_RULES.id().equals(skillId);
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!weaveSlot) {
            if (WAS_DOWN[slot]) {
                WAS_DOWN[slot] = false;
                if (WeaveRuleOverlay.active()) {
                    WeaveRuleOverlay.finish();
                }
            }
            return false;
        }
        if (down && !WAS_DOWN[slot]) {
            WeaveRuleOverlay.begin();
        } else if (!down && WAS_DOWN[slot]) {
            WeaveRuleOverlay.finish();
        }
        WAS_DOWN[slot] = down;
        return true;
    }
}
