package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Which body the Causal Anchor hold was released on.
 *
 * <p>An entity id and nothing else, because an entity id is the only thing the client is entitled
 * to an opinion about here. Whether that body is alive, in this dimension, inside the reach and not
 * the wielder themselves are all re-asked on the server, so a client that names a body across the
 * map gets the same refusal a client that names nothing does.
 */
public record AnchorTargetPayload(int entityId) implements CustomPacketPayload {
    public static final Type<AnchorTargetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "anchor_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AnchorTargetPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, AnchorTargetPayload::entityId, AnchorTargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
