package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Boss-fight environment directive for Chronos End: toggles a named dimension-wide effect
 * (sky cut, world inversion, pulse storm, ...) with a ramp time and strength.
 */
public record ChronosEnvironmentPayload(int effect, boolean active, int rampTicks, float strength) implements CustomPacketPayload {
    public static final Type<ChronosEnvironmentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "chronos_environment"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChronosEnvironmentPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ChronosEnvironmentPayload::effect,
            ByteBufCodecs.BOOL, ChronosEnvironmentPayload::active,
            ByteBufCodecs.INT, ChronosEnvironmentPayload::rampTicks,
            ByteBufCodecs.FLOAT, ChronosEnvironmentPayload::strength,
            ChronosEnvironmentPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
