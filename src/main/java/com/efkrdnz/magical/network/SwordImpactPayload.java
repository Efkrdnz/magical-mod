package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a sword hit something, here. The client throws a small wave of vanilla
 * particles for it ({@code SwordImpactParticles}); no entity, no tracking, nothing that lingers.
 *
 * @param nx the struck surface's outward normal, back toward whatever struck it
 * @param radius how far the wave runs, in blocks
 * @param kind an {@code ImpactWave.Kind} ordinal, clamped on use because it arrives off the wire
 * @param block the struck block's state id ({@code Block.getId}), or 0 - air - for none
 */
public record SwordImpactPayload(double x, double y, double z, float nx, float ny, float nz, float radius,
        int kind, int block) implements CustomPacketPayload {

    public static final Type<SwordImpactPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sword_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwordImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
                buf.writeFloat(payload.nx);
                buf.writeFloat(payload.ny);
                buf.writeFloat(payload.nz);
                buf.writeFloat(payload.radius);
                buf.writeByte(payload.kind);
                buf.writeVarInt(payload.block);
            },
            buf -> new SwordImpactPayload(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readByte(),
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
