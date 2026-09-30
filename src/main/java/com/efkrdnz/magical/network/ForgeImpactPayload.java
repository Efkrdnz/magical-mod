package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a forged strike struck something, here. The client throws the element's
 * matter for it ({@code ForgeImpactParticles}); no entity, nothing that lingers.
 *
 * @param nx      the struck surface's outward normal, back toward whatever struck it
 * @param radius  half the struck body's width, so the matter comes off the side the viewer sees;
 *                0 for a block face
 * @param form    a {@code ForgeImpactForm} ordinal, clamped on use
 * @param element a {@code ForgeElementKind} ordinal, clamped on use
 * @param grade   a {@code ForgeGrade} ordinal; one this build does not know still throws something
 * @param flags   {@link #HEAVY} and {@link #ECHO}
 * @param block   the struck block's state id ({@code Block.getId}), or 0 - air - for none
 * @param primary the element's colours as 0xRRGGBB; three bytes each on the wire
 */
public record ForgeImpactPayload(double x, double y, double z, float nx, float ny, float nz, float radius,
        int form, int element, int grade, int flags, int block, int primary, int secondary, int edge)
        implements CustomPacketPayload {

    public static final int HEAVY = 1;
    public static final int ECHO = 2;

    public ForgeImpactPayload {
        primary &= 0xFFFFFF;
        secondary &= 0xFFFFFF;
        edge &= 0xFFFFFF;
    }

    public boolean heavy() {
        return (flags & HEAVY) != 0;
    }

    public boolean echo() {
        return (flags & ECHO) != 0;
    }

    public static final Type<ForgeImpactPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "forge_impact"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeImpactPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
                buf.writeFloat(payload.nx);
                buf.writeFloat(payload.ny);
                buf.writeFloat(payload.nz);
                buf.writeFloat(payload.radius);
                buf.writeByte(payload.form);
                buf.writeByte(payload.element);
                buf.writeByte(payload.grade);
                buf.writeByte(payload.flags);
                buf.writeVarInt(payload.block);
                buf.writeMedium(payload.primary);
                buf.writeMedium(payload.secondary);
                buf.writeMedium(payload.edge);
            },
            buf -> new ForgeImpactPayload(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readUnsignedByte(),
                    buf.readUnsignedByte(),
                    buf.readUnsignedByte(),
                    buf.readUnsignedByte(),
                    buf.readVarInt(),
                    buf.readUnsignedMedium(),
                    buf.readUnsignedMedium(),
                    buf.readUnsignedMedium()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
