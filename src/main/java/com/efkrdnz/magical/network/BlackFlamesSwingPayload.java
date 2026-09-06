package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlackFlamesSwingPayload() implements CustomPacketPayload {
    public static final Type<BlackFlamesSwingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "black_flames_swing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlackFlamesSwingPayload> STREAM_CODEC =
            StreamCodec.unit(new BlackFlamesSwingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
