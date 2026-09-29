package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One slot of the Daydream belt, set to a lie by name, or cleared with an empty key. The server
 * accepts only impressions the wielder has learned, so a forged packet can name nothing new.
 */
public record SetBeltSlotPayload(int slot, String key) implements CustomPacketPayload {
    public static final Type<SetBeltSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "set_belt_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetBeltSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetBeltSlotPayload::slot,
            ByteBufCodecs.stringUtf8(256), SetBeltSlotPayload::key,
            SetBeltSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
