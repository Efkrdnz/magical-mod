package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CounterClearPayload(int threatId) implements CustomPacketPayload {
    public static final Type<CounterClearPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "counter_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CounterClearPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, CounterClearPayload::threatId, CounterClearPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
