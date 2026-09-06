package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SovereignAegisCastPayload(int slot, int mode) implements CustomPacketPayload {
    public static final Type<SovereignAegisCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sovereign_aegis_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SovereignAegisCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SovereignAegisCastPayload::slot,
                    ByteBufCodecs.INT,
                    SovereignAegisCastPayload::mode,
                    SovereignAegisCastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
