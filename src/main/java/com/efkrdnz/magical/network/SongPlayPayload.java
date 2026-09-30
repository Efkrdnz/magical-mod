package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A performance to play, from the tick step 0 falls on; an empty song is silence. */
public record SongPlayPayload(int owner, long start, CompoundTag song) implements CustomPacketPayload {
    public static final Type<SongPlayPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "song_play"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SongPlayPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.owner());
                buf.writeLong(payload.start());
                buf.writeNbt(payload.song());
            },
            buf -> new SongPlayPayload(buf.readVarInt(), buf.readLong(), buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
