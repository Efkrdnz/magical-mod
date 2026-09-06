package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMagicCodexPayload() implements CustomPacketPayload {
    public static final Type<OpenMagicCodexPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_magic_codex"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMagicCodexPayload> STREAM_CODEC = StreamCodec.unit(new OpenMagicCodexPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
