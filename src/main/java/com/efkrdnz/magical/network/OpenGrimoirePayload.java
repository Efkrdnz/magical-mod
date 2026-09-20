package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open the Grimoire screen. A press on the Grimoire skill sends it, and so does
 * {@code /magical grimoire}. It carries nothing, because the screen reads the synced player state
 * (the Grimoire rides in it) and writes back through {@link SetIncantationPayload}; this packet is
 * the whole of the open handshake, as {@link OpenSpellCreatorPayload} is for the creator.
 */
public record OpenGrimoirePayload() implements CustomPacketPayload {
    public static final Type<OpenGrimoirePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_grimoire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenGrimoirePayload> STREAM_CODEC =
            StreamCodec.unit(new OpenGrimoirePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
