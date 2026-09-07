package com.efkrdnz.magical.client;

import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.ForgeTempers;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.TemperDefinition;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.TemperStats;
import com.efkrdnz.magical.network.ForgeStrikePayload;
import com.efkrdnz.magical.network.MagicalNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Turns the vanilla attack key into forged-weapon combat input. Nothing here cancels vanilla: the
 * press still swings, mines or misses exactly as it always did, and this only rides alongside it.
 *
 * <p>Two vanilla behaviours shape the design. {@code InteractionKeyMappingTriggered} re-fires every
 * tick while the key is held against a block, so a press is only taken when the key was not already
 * down ({@link #wasDown}). And a held key on a block is mining, not attacking, so a pending press is
 * dropped outright while {@code isDestroying()} and any running charge is cancelled.</p>
 */
public final class ForgeComboInput {

    private static final int PRESS_DEBOUNCE_TICKS = 3;

    private static boolean wasDown;
    private static boolean pressPending;
    private static boolean pressWhiff;
    private static boolean charging;
    private static int heldTicks;
    private static long clientTick;
    private static long lastPressTick = Long.MIN_VALUE / 4;
    private static ItemStack lastMainHand = ItemStack.EMPTY;
    /**
     * The held weapon's charge threshold, refreshed once a tick from the stack in hand. A constant
     * {@code BASE_CHARGE_THRESHOLD} here would silently disagree with the server, which resolves the
     * threshold per temper: HEAVY's {@code chargeThresholdDelta} of -4 means a six-tick hold is
     * already a charge the server would accept, while a client counting to ten never sets
     * {@link #charging}, never sends a release, and makes HEAVY's charge bonus inert.
     */
    private static int chargeThreshold = ForgeStrikeMath.BASE_CHARGE_THRESHOLD;

    private ForgeComboInput() {}

    /**
     * Called from the attack half of {@code InteractionKeyMappingTriggered}. Only records intent —
     * the packet goes out from {@link #tick(Minecraft)}, once the mining state for this tick is known.
     */
    public static void onAttackKeyTriggered(Minecraft minecraft) {
        if (minecraft.player == null || wasDown) {
            return; // a re-fire from continueAttack() while the key is still held: not a new press
        }
        if (clientTick - lastPressTick < PRESS_DEBOUNCE_TICKS) {
            return;
        }
        pressPending = true;
        pressWhiff = minecraft.crosshairPickEntity == null;
    }

    /** Called once per client tick, right after {@code FirstPersonEffects.tick}. */
    public static void tick(Minecraft minecraft) {
        clientTick++;
        if (minecraft.player == null) {
            reset();
            return;
        }
        boolean down = minecraft.options.keyAttack.isDown();
        if (minecraft.screen != null) {
            // A screen swallows the key, so drop everything - but tell the server first, and keep
            // tracking the key, or closing the screen on a still-held key reads as a fresh press.
            cancelCharge();
            reset();
            wasDown = down;
            return;
        }
        ItemStack mainHand = minecraft.player.getMainHandItem();
        if (!ItemStack.isSameItem(mainHand, lastMainHand)) {
            // Only a real swap counts: comparing components would reset on every point of
            // durability the weapon loses, dropping the charge the player is still holding.
            cancelCharge();
            reset();
            lastMainHand = mainHand.copy();
        }
        if (!isForgedWeapon(mainHand)) {
            reset();
            chargeThreshold = ForgeStrikeMath.BASE_CHARGE_THRESHOLD;
            wasDown = down; // keep tracking the key so an unforged hold never reads as a fresh press
            return;
        }
        chargeThreshold = thresholdOf(mainHand);
        boolean destroying = minecraft.gameMode != null && minecraft.gameMode.isDestroying();
        flushPress(destroying);
        accumulateHold(down, destroying);
        flushRelease(down);
        cancelChargeWhileMining(destroying);
        wasDown = down;
    }

    /** How far into a heavy charge the wielder is, 0 when not charging. Feeds the HUD ring only. */
    public static float chargeFraction() {
        return charging ? ForgeStrikeMath.chargeFraction(heldTicks, chargeThreshold) : 0.0f;
    }

    /**
     * Held ticks before the weapon in hand turns a press into a charge, the same number the server
     * gates the release on. Falls back to the base threshold when nothing forged is held.
     */
    public static int chargeThresholdTicks() {
        return chargeThreshold;
    }

    private static int thresholdOf(ItemStack stack) {
        return ForgeStrikeMath.chargeThreshold(ForgedWeapons.get(stack)
                .flatMap(ForgedWeapon::temper)
                .flatMap(ForgeTempers::get)
                .map(TemperDefinition::stats)
                .orElse(TemperStats.NONE));
    }

    public static void reset() {
        pressPending = false;
        pressWhiff = false;
        charging = false;
        heldTicks = 0;
        wasDown = false;
    }

    private static void flushPress(boolean destroying) {
        if (!pressPending) {
            return;
        }
        pressPending = false;
        if (destroying) {
            return; // the key is chewing through a block, not swinging at anything
        }
        MagicalNetwork.sendForgeStrike(ForgeStrikePayload.PRESS, pressWhiff, 0);
        lastPressTick = clientTick;
        heldTicks = 0;
        charging = false;
    }

    private static void accumulateHold(boolean down, boolean destroying) {
        if (!down || destroying) {
            return;
        }
        heldTicks++;
        // >= rather than ==: two forged weapons of the same item type swap without tripping the
        // reset above, and a hold already past the new weapon's shorter threshold must still charge.
        if (!charging && heldTicks >= chargeThreshold) {
            MagicalNetwork.sendForgeStrike(ForgeStrikePayload.CHARGE_BEGIN, false, 0);
            charging = true;
        }
    }

    private static void flushRelease(boolean down) {
        if (!wasDown || down) {
            return;
        }
        if (charging) {
            MagicalNetwork.sendForgeStrike(ForgeStrikePayload.CHARGE_RELEASE, false,
                    Math.min(heldTicks, ForgeStrikeMath.MAX_CHARGE_TICKS));
        }
        heldTicks = 0;
        charging = false;
    }

    private static void cancelChargeWhileMining(boolean destroying) {
        if (!charging || !destroying) {
            return;
        }
        cancelCharge();
        heldTicks = 0;
    }

    /**
     * Drops a charge the server still believes in. A local {@link #reset()} on its own would leave
     * the telegraph entity standing and, on a GUARD weapon, the whole charge guard running unearned,
     * so every path that abandons a charge has to say so out loud.
     */
    private static void cancelCharge() {
        if (!charging) {
            return;
        }
        MagicalNetwork.sendForgeStrike(ForgeStrikePayload.CHARGE_CANCEL, false, 0);
        charging = false;
    }

    public static boolean isForgedWeapon(ItemStack stack) {
        return ForgedWeapons.get(stack).isPresent() && ForgeMaterials.isForgeable(stack);
    }
}
