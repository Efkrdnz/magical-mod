package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's whole magic state, as the tag {@code PlayerMagicState.save()} produced.
 *
 * <p>The tag is carried rather than the state object so the sender serialises once. Holding the
 * live object meant a defensive deep copy on every send, because the packet is not encoded until
 * the network thread reaches it and the state keeps changing in the meantime; a tag taken at call
 * time is that snapshot already.
 */
public record PlayerMagicStatePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<PlayerMagicStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "player_magic_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerMagicStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeNbt(payload.data()),
            buf -> new PlayerMagicStatePayload(buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
