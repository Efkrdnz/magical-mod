package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A whole Fracture, as the five gates were left when the key came up.
 *
 * <p>Five ints rather than a list because {@code Fracture.LENGTH} is five and a fixed-arity codec
 * needs no length handling; change one and the other must follow. Every ordinal is checked against
 * {@code Fault.values()} on the server, so a forged packet writes nothing.
 */
public record ApplyFracturePayload(int first, int second, int third, int fourth, int fifth)
        implements CustomPacketPayload {

    public static final Type<ApplyFracturePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "apply_fracture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyFracturePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    ApplyFracturePayload::first,
                    ByteBufCodecs.INT,
                    ApplyFracturePayload::second,
                    ByteBufCodecs.INT,
                    ApplyFracturePayload::third,
                    ByteBufCodecs.INT,
                    ApplyFracturePayload::fourth,
                    ByteBufCodecs.INT,
                    ApplyFracturePayload::fifth,
                    ApplyFracturePayload::new);

    public int[] ordinals() {
        return new int[] {first, second, third, fourth, fifth};
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
