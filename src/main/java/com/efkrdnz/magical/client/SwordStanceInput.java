package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Holding the slot that carries Sword Stance opens the six; letting go takes one.
 *
 * <p>The same hold-and-release shape the Fracture and the Causal Anchor use, and for the same
 * reason: a posture is chosen rather than pressed. No authority check, unlike those two - the
 * Sword Summoner is a class rather than an Authority, and holding the skill at all is the gate.
 */
public final class SwordStanceInput {

    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private SwordStanceInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean stanceSlot = MagicContent.SWORD_STANCE.id().equals(skillId);
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!stanceSlot) {
            if (WAS_DOWN[slot]) {
                WAS_DOWN[slot] = false;
                if (SwordStanceOverlay.isActive()) {
                    SwordStanceOverlay.finish();
                }
            }
            return false;
        }
        if (down && !WAS_DOWN[slot]) {
            SwordStanceOverlay.begin();
        } else if (!down && WAS_DOWN[slot]) {
            SwordStanceOverlay.finish();
        }
        WAS_DOWN[slot] = down;
        return true;
    }
}
