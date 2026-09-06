package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Play-to-server: the loadout slot key is (still) held / was released. */
public record CastHoldPayload(int slot, boolean held) implements CustomPacketPayload {
    public static final Type<CastHoldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "cast_hold"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastHoldPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.slot);
                buf.writeBoolean(payload.held);
            },
            buf -> new CastHoldPayload(buf.readByte(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
