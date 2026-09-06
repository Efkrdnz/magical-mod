package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CounterResponsePayload(int threatId) implements CustomPacketPayload {
    public static final Type<CounterResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "counter_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CounterResponsePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, CounterResponsePayload::threatId, CounterResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
