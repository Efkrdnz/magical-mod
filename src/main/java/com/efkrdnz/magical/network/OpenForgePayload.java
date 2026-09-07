package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to open the Runeforge menu. */
public record OpenForgePayload() implements CustomPacketPayload {
    public static final Type<OpenForgePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_forge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenForgePayload> STREAM_CODEC =
            StreamCodec.unit(new OpenForgePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
