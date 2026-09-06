package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RefillBarrierPayload() implements CustomPacketPayload {
    public static final Type<RefillBarrierPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "refill_barrier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefillBarrierPayload> STREAM_CODEC = StreamCodec.unit(new RefillBarrierPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
