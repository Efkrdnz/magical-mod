package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Play-to-client: a status on the receiving player started (ticks > 0) or ended (ticks == 0). */
public record StatusSyncPayload(int status, int ticks, int amplifier, float value) implements CustomPacketPayload {
    public static final Type<StatusSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "status_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatusSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.status);
                buf.writeVarInt(payload.ticks);
                buf.writeByte(payload.amplifier);
                buf.writeFloat(payload.value);
            },
            buf -> new StatusSyncPayload(buf.readByte(), buf.readVarInt(), buf.readByte(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
