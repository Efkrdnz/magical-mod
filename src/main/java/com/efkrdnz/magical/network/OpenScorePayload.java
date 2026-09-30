package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Opens the Score. The Song and the Riff themselves ride the magic-state sync. */
public record OpenScorePayload() implements CustomPacketPayload {
    public static final Type<OpenScorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_score"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenScorePayload> STREAM_CODEC = StreamCodec.unit(new OpenScorePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
