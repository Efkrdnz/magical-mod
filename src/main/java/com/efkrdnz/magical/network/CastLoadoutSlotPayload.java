package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CastLoadoutSlotPayload(int slot, boolean sneakDown) implements CustomPacketPayload {
    public static final Type<CastLoadoutSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "cast_loadout_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastLoadoutSlotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    CastLoadoutSlotPayload::slot,
                    ByteBufCodecs.BOOL,
                    CastLoadoutSlotPayload::sneakDown,
                    CastLoadoutSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
