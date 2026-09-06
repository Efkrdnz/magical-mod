package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayerMagicStatePayload(PlayerMagicState data) implements CustomPacketPayload {
    public static final Type<PlayerMagicStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "player_magic_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerMagicStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeNbt(payload.data.save()),
            buf -> {
                CompoundTag tag = buf.readNbt();
                return new PlayerMagicStatePayload(tag == null ? new PlayerMagicState() : PlayerMagicState.load(tag));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
