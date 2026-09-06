package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlackFlamesCastPayload(int slot, int mode) implements CustomPacketPayload {
    public static final Type<BlackFlamesCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "black_flames_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlackFlamesCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    BlackFlamesCastPayload::slot,
                    ByteBufCodecs.INT,
                    BlackFlamesCastPayload::mode,
                    BlackFlamesCastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
