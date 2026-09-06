package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpaceOffenseCastPayload(int slot, int mode) implements CustomPacketPayload {
    public static final Type<SpaceOffenseCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "space_offense_cast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceOffenseCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SpaceOffenseCastPayload::slot,
                    ByteBufCodecs.INT,
                    SpaceOffenseCastPayload::mode,
                    SpaceOffenseCastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
