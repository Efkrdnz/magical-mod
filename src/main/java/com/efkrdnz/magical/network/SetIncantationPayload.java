package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.incantation.SetIncantationPayloadCaps;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A whole incantation as the editor left it: the slot, the breath and the verse ids in order.
 * The list is capped at the core's verse cap and each id at a fixed length on the wire, and the
 * server re-validates everything ({@code IncantationValidator}) before writing a byte of it.
 */
public record SetIncantationPayload(int slot, int breath, List<String> ids) implements CustomPacketPayload {

    public static final Type<SetIncantationPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "set_incantation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetIncantationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SetIncantationPayload::slot,
                    ByteBufCodecs.INT,
                    SetIncantationPayload::breath,
                    ByteBufCodecs.stringUtf8(SetIncantationPayloadCaps.MAX_ID_LENGTH).apply(ByteBufCodecs.list(SetIncantationPayloadCaps.MAX_IDS)),
                    SetIncantationPayload::ids,
                    SetIncantationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
