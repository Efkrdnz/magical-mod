package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Tracks "is the slot key held" for any equipped skill whose profile is holdable and mirrors it to
 * the server (refreshed every 10 ticks, released immediately). It never consumes the press itself:
 * the normal cast request still fires on the initial click.
 */
public final class GenericHoldInput {
    private static final boolean[] HELD = new boolean[MagicContent.LOADOUT_SIZE];
    private static final int[] REFRESH = new int[MagicContent.LOADOUT_SIZE];

    private GenericHoldInput() {}

    public static void tick(Minecraft minecraft) {
        var state = ClientMagicState.get();
        for (int slot = 0; slot < MagicalKeyMappings.CAST_SLOTS.length && slot < HELD.length; slot++) {
            ResourceLocation skill = state.equippedSkill(slot);
            boolean holdable = skill != null && VisualProfiles.of(skill).holdable();
            boolean down = holdable && MagicalKeyMappings.CAST_SLOTS[slot].isDown() && minecraft.screen == null;
            if (down) {
                if (!HELD[slot] || --REFRESH[slot] <= 0) {
                    MagicalNetwork.sendCastHold(slot, true);
                    REFRESH[slot] = 10;
                }
                HELD[slot] = true;
            } else if (HELD[slot]) {
                MagicalNetwork.sendCastHold(slot, false);
                HELD[slot] = false;
            }
        }
    }
}
