package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "Fire the shape in slot {@code shapeIndex}, from loadout slot {@code slot}."
 *
 * <p>Separate from {@link CastLoadoutSlotPayload} only because of the shape index. The number key
 * the player pressed is a selection, not a second casting route: the server writes it onto the
 * player's state and then goes through the ordinary cast path, so cooldown, the Vessel, sin and the
 * loadout lock all still apply exactly as they do for every other ability.
 *
 * @param slot       which of the loadout slots the held ability key belongs to
 * @param shapeIndex which of the nine stored shapes the number row chose
 */
public record BloodShapeCastPayload(int slot, int shapeIndex) implements CustomPacketPayload {

    public static final Type<BloodShapeCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "blood_shape_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BloodShapeCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BloodShapeCastPayload::slot,
                    ByteBufCodecs.VAR_INT, BloodShapeCastPayload::shapeIndex,
                    BloodShapeCastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
