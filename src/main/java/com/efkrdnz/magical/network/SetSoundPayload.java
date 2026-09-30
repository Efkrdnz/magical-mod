package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The Score screen's save: a {@code song}, a {@code riff}, or both. Re-read and repaired on the server. */
public record SetSoundPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SetSoundPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "set_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetSoundPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeNbt(payload.data()),
            buf -> new SetSoundPayload(buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
