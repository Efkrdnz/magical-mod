package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A request to make one loadout active. The index is validated server side.
 *
 * <p>Carries no skill ids: the server already knows what is in each loadout, and accepting them
 * from a client would let one rewrite its own bindings mid-fight.
 */
public record SelectLoadoutPayload(int index) implements CustomPacketPayload {
    public static final Type<SelectLoadoutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "select_loadout"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectLoadoutPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SelectLoadoutPayload::index, SelectLoadoutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
