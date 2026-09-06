package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.arcane.ArcanePlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ArcanePlayerDataPayload(ArcanePlayerData data) implements CustomPacketPayload {
    public static final Type<ArcanePlayerDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "arcane_player_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcanePlayerDataPayload> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buf, ArcanePlayerDataPayload payload) -> buf.writeNbt(payload.data.save()),
            (RegistryFriendlyByteBuf buf) -> {
                CompoundTag tag = buf.readNbt();
                return new ArcanePlayerDataPayload(tag == null ? new ArcanePlayerData() : ArcanePlayerData.load(tag));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
