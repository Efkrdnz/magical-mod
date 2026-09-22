package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Holding a slot that carries The Bearing opens the azimuthal plot; letting go commits the pulls.
 *
 * <p>The same hold-and-release arm {@code ChaosAuthorityInput} and {@code CausalityAuthorityInput}
 * use, and for the same reason: what the wielder is choosing cannot be aimed at. Six of twelve
 * bearings are behind their head, so the press that opens this overlay is a press that must
 * <em>not</em> also cast - which is what returning true here buys, and why the caller's
 * {@code consumeClick} drain is a line of its own.
 *
 * <p><b>The release is the only thing that sends.</b> Marks are held in the overlay as bearings
 * and turned into a mask once, at release, so the packet names the shape as it stands at the
 * instant the key came up rather than as it stood when a bit was set - a Sword God's Array can
 * shed a station while the hold is open, and every slot after the gap renumbers.
 */
public final class SwordBearingInput {

    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private SwordBearingInput() {}

    /**
     * One slot, one tick. True while this slot owns the key, which suppresses the normal cast.
     *
     * <p>A slot that stops carrying The Bearing mid-hold - a loadout swapped out from under it, a
     * class lost - <b>cancels</b> rather than releases. A pull returns a station's Edge to loose
     * and cannot be undone by pressing the key again, so it is only ever spent on a real key-up.
     */
    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        boolean bearingSlot = SwordService.holds(ClientMagicState.get())
                && MagicContent.THE_BEARING.id().equals(skillId);
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        if (!bearingSlot) {
            if (WAS_DOWN[slot]) {
                WAS_DOWN[slot] = false;
                SwordBearingOverlay.cancel();
            }
            return false;
        }
        if (down) {
            if (!WAS_DOWN[slot]) {
                SwordBearingOverlay.open();
            }
            SwordBearingOverlay.tick(minecraft);
        } else if (WAS_DOWN[slot]) {
            commit();
        }
        WAS_DOWN[slot] = down;
        return true;
    }

    /**
     * The key came up: read the mask out of the overlay and send it.
     *
     * <p>The zero case is dropped twice on purpose - here for the reading, and again inside
     * {@link MagicalNetwork#sendPullStations} for anything else that ever learns to call it.
     * A hold that was opened, read and closed again is the commonest thing this overlay does.
     */
    private static void commit() {
        int mask = SwordBearingOverlay.release();
        if (mask != 0) {
            MagicalNetwork.sendPullStations(mask);
        }
    }
}
