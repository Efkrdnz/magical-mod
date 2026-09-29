package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Open the Lexicon inventory: {@code /magical mind lexicon open}, for captures. Nothing on it: the
 * lexicon and the belt are already on the client as part of the magic state.
 */
public record OpenLexiconPayload() implements CustomPacketPayload {
    public static final Type<OpenLexiconPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_lexicon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLexiconPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenLexiconPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
