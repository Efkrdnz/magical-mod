package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Open the board. Nothing on it: the Weave itself is already on the client as part of the magic
 * state, which is synced wholesale, so the screen reads it rather than being handed a copy.
 */
public record OpenCausalBoardPayload() implements CustomPacketPayload {
    public static final Type<OpenCausalBoardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_causal_board"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCausalBoardPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenCausalBoardPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
