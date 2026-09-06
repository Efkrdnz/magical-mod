package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SoulVowCastPayload(int slot, int mode) implements CustomPacketPayload {
    public static final Type<SoulVowCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "soul_vow_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulVowCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SoulVowCastPayload::slot,
                    ByteBufCodecs.INT,
                    SoulVowCastPayload::mode,
                    SoulVowCastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
