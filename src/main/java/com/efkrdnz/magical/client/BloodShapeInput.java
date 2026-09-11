package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.screen.blood.BloodShapeEditorScreen;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The two halves of the ability key: crouch and press to edit, hold and press a number to cast.
 *
 * <p>Only one slot can ever be armed, so this is a single machine rather than a per-slot array. It
 * diverges on purpose from the four hold handlers it otherwise mirrors: those fire on release, and
 * this one fires on the number press and then sits in LOCKED until the key comes up. Releasing does
 * nothing, because the hold is a selection gesture rather than a charge.
 *
 * <p>Sneak is read from the key, not from the player's pose. The gesture is decided on one tick's
 * edge and the pose lags the key by a tick or two, so a quick crouch-and-tap would open the editor
 * sometimes and cast others. The two existing client sites read the pose, but both ship the answer
 * to a server that re-reads sneak anyway; this branch never leaves the client.
 */
public final class BloodShapeInput {

    private static final int IDLE = 0;
    private static final int ARMED = 1;
    /** Fired already: the key is still down, and one hold casts one shape. */
    private static final int LOCKED = 2;

    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];

    private static int state = IDLE;
    private static int activeSlot = -1;
    private static float fade;

    private BloodShapeInput() {
    }

    public static boolean armed() {
        return state == ARMED;
    }

    /**
     * Drops the hold without casting: when a screen opens, when the loadout is swapped mid-hold,
     * and when the key comes up.
     */
    public static void cancel() {
        state = IDLE;
        activeSlot = -1;
    }

    /**
     * @return true when this handler owns the slot, so the cast ladder leaves the key alone
     */
    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        if (!MagicContent.BLOOD_MANIPULATION.id().equals(skillId)) {
            if (activeSlot == slot) {
                cancel();
            }
            WAS_DOWN[slot] = false;
            return false;
        }

        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        boolean sneaking = minecraft.options.keyShift.isDown();

        if (down && !WAS_DOWN[slot]) {
            if (sneaking) {
                // Fire and forget: the editor submits its own work, so there is nothing here to
                // remember once the screen is up.
                cancel();
                minecraft.setScreen(new BloodShapeEditorScreen());
            } else {
                state = ARMED;
                activeSlot = slot;
                fade = 0.0F;
            }
        } else if (!down && WAS_DOWN[slot] && activeSlot == slot) {
            cancel();
        } else if (down && activeSlot == slot && state == ARMED && sneaking) {
            // Crouching part-way through a hold means the player changed their mind about which
            // half of the ability they wanted. Disarm rather than fire something unintended.
            cancel();
        }

        if (state == ARMED && activeSlot == slot) {
            fade = Math.min(1.0F, fade + 0.18F);
        }
        WAS_DOWN[slot] = down;
        return true;
    }

    /**
     * A number key arrived while armed. Called from the tick guard that drains it, which is the
     * same act that stops the hotbar selection from following it.
     */
    public static void chooseShape(int index) {
        if (state != ARMED || activeSlot < 0 || index < 0 || index >= BloodShapeRules.SHAPE_SLOTS) {
            return;
        }
        MagicalNetwork.sendBloodShapeCast(activeSlot, index);
        state = LOCKED;
    }

    /** The nine slots, while the key is held: which are filled, and which number picks them. */
    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (state != ARMED || minecraft.player == null) {
            fade = Math.max(0.0F, fade - 0.12F);
            return;
        }
        int alpha = Math.round(Math.min(1.0F, fade) * 220.0F) << 24;
        int centreX = guiGraphics.guiWidth() / 2;
        int y = guiGraphics.guiHeight() / 2 + 64;

        int cell = 18;
        int stride = 20;
        int total = BloodShapeRules.SHAPE_SLOTS * stride - (stride - cell);
        int x = centreX - total / 2;
        guiGraphics.fill(x - 6, y - 6, x + total + 6, y + cell + 6, (alpha & 0xFF000000) | 0x101822);

        for (int i = 0; i < BloodShapeRules.SHAPE_SLOTS; i++) {
            int cellX = x + i * stride;
            boolean filled = !ClientMagicState.get().bloodShapes().shape(i).isEmpty();
            guiGraphics.fill(cellX, y, cellX + cell, y + cell, alpha | (filled ? 0x401A22 : 0x161E2C));
            guiGraphics.fill(cellX, y, cellX + cell, y + 1, alpha | 0xE06470);
            guiGraphics.drawCenteredString(minecraft.font, String.valueOf(i + 1),
                    cellX + cell / 2, y + 5, filled ? 0xFFF4A8AF : 0xFF60718A);
        }
        guiGraphics.drawCenteredString(minecraft.font,
                Component.translatable("screen.magical.blood_shape.hint"), centreX, y + cell + 8,
                0xFFC9D6E8);
    }
}
