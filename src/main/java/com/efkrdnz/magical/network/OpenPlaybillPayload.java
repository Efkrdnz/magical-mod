package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Open the Playbill. Nothing on it: the three reveries and the lexicon are already on the client
 * as part of the magic state, so the screen reads them rather than being handed a copy.
 */
public record OpenPlaybillPayload() implements CustomPacketPayload {
    public static final Type<OpenPlaybillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_playbill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPlaybillPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenPlaybillPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
