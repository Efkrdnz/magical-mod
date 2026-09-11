package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.network.BloodShapeCastPayload;
import com.efkrdnz.magical.network.BloodShapeSubmitPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server side of the shape editor: the two things a client may ask about its drawn shapes.
 *
 * <p>The split between them is the whole security model. {@link #submit} is a <em>format</em> check
 * and nothing more - it asks whether the numbers are storable, never whether the player is allowed
 * to reach that far. {@link #fire} is where reach is spent, and the skill clips against the reach
 * the caster actually has at that moment.
 *
 * <p>Clipping at submit time would be the wrong answer twice over: it would permanently delete the
 * far half of a shape the moment the editor was opened after a respec, and those points are meant
 * to be ignored rather than destroyed. Buying the range back has to bring the shape back.
 */
public final class BloodShapeService {

    private BloodShapeService() {
    }

    /**
     * Stores one edited shape.
     *
     * <p>Everything hostile about the packet is already handled by the time a shape exists:
     * {@link BloodShapeSubmitPayload#toShape()} runs it through the same distrustful builder the NBT
     * reader uses, which caps the strokes, caps the points and clamps the coordinates. What is left
     * here is the slot index, the one number that could reach outside an array.
     */
    public static void submit(ServerPlayer player, BloodShapeSubmitPayload payload) {
        if (!BloodShapeBook.isSlot(payload.slot())) {
            return;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.bloodShapes().setShape(payload.slot(), payload.toShape());
        state.sync(player);
    }

    /**
     * Selects a shape and casts with it.
     *
     * <p>The number key is a selection, not a casting route of its own: this writes the choice onto
     * the state and then goes in through {@link MagicCastingService#castSlot}, so cooldown, the
     * Vessel, the sin ledger and the post-cast loadout lock all run exactly as they do for a plain
     * press. Sneaking is passed as false because the client only sends this while not sneaking; the
     * sneaking gesture opens the editor instead.
     *
     * @param slot       the loadout slot the held ability key belongs to
     * @param shapeIndex which of the nine stored shapes the number row chose
     */
    public static void fire(ServerPlayer player, int slot, int shapeIndex) {
        if (slot < 0 || slot >= MagicContent.LOADOUT_SIZE || !BloodShapeBook.isSlot(shapeIndex)) {
            return;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!MagicContent.BLOOD_MANIPULATION.id().equals(state.equippedSkill(slot))) {
            // The slot holds something else, so this packet means nothing. Refused rather than
            // forwarded: a number key should never fire an ability it was not aimed at.
            return;
        }

        BloodShape shape = state.bloodShapes().shape(shapeIndex);
        if (shape.isEmpty()) {
            // Caught before the cast so an empty slot costs no cooldown and no blood. The skill
            // checks this again, because it is also reachable from a plain slot press.
            player.displayClientMessage(
                    Component.translatable("message.magical.blood_shape_empty"), true);
            return;
        }

        state.setSelectedBloodShape(shapeIndex);
        MagicCastingService.castSlot(player, slot, false);
        // The cast usually syncs on its own, and sync() drops a byte-identical snapshot, so this
        // costs nothing when it did. It is here for the paths that refuse a cast without syncing,
        // which would otherwise leave the client showing the previous selection.
        state.sync(player);
    }

    /** Convenience for the payload handler, which holds the record rather than the two fields. */
    public static void fire(ServerPlayer player, BloodShapeCastPayload payload) {
        fire(player, payload.slot(), payload.shapeIndex());
    }
}
