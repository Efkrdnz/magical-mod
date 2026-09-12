package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UnwakingGuardPayload(UUID run, long sequence, boolean held) implements CustomPacketPayload {
    public static final Type<UnwakingGuardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "unwaking_guard"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnwakingGuardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.run); buf.writeLong(p.sequence); buf.writeBoolean(p.held); },
            buf -> new UnwakingGuardPayload(buf.readUUID(), buf.readLong(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
