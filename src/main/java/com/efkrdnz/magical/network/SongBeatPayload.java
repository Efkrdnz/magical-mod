package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** An action made to the Song: which, and the note the client heard it land on, or -1. */
public record SongBeatPayload(int action, long index) implements CustomPacketPayload {
    public static final Type<SongBeatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "song_beat"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SongBeatPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.action());
                buf.writeLong(payload.index());
            },
            buf -> new SongBeatPayload(buf.readVarInt(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
