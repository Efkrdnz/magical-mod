package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MagicBarrageHoldPayload(int slot, boolean release, int chargeTicks) implements CustomPacketPayload {
    public static final Type<MagicBarrageHoldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "magic_barrage_hold"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MagicBarrageHoldPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    MagicBarrageHoldPayload::slot,
                    ByteBufCodecs.BOOL,
                    MagicBarrageHoldPayload::release,
                    ByteBufCodecs.INT,
                    MagicBarrageHoldPayload::chargeTicks,
                    MagicBarrageHoldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
