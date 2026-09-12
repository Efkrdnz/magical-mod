package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UnwakingReadyPayload(UUID run, long revision) implements CustomPacketPayload {
    public static final Type<UnwakingReadyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "unwaking_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnwakingReadyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeUUID(p.run); buf.writeLong(p.revision); },
            buf -> new UnwakingReadyPayload(buf.readUUID(), buf.readLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
