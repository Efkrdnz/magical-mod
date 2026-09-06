package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpaceSubspaceHoldPayload(int slot, boolean release, int chargeTicks, boolean followOwner) implements CustomPacketPayload {
    public static final Type<SpaceSubspaceHoldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "space_subspace_hold"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceSubspaceHoldPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SpaceSubspaceHoldPayload::slot,
                    ByteBufCodecs.BOOL,
                    SpaceSubspaceHoldPayload::release,
                    ByteBufCodecs.INT,
                    SpaceSubspaceHoldPayload::chargeTicks,
                    ByteBufCodecs.BOOL,
                    SpaceSubspaceHoldPayload::followOwner,
                    SpaceSubspaceHoldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
