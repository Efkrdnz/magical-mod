package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Every station the Bearing marked, committed in one packet when the key came up.
 *
 * <p>A mask rather than a list of slots, and the whole release rather than one pull each: the
 * overlay is a hold, so the wielder walks the marks around the disc and may mark and unmark the
 * same bearing several times before letting go. Only the state at release is a decision, and
 * sending each toggle would let a client drive twelve pulls through the server's rule checks for
 * one press. Twelve bits fit an int with room to spare, so there is no length to read and no cap
 * to enforce on the way in - a mask is the one shape that cannot be over-long.
 *
 * <p><b>Nothing here validates.</b> The wire carries whatever the client wrote; every bit is
 * re-checked against the live station count in {@code TheBearingSkill.pull}, which drops a bit
 * naming a slot that is not there rather than clamping it onto a neighbour, so a forged packet
 * writes nothing and a stale one pulls only what still exists. The mask width the client is
 * expected to use is {@code TheBearingSkill.MASK_BITS}; bits above it are simply discarded.
 */
public record PullStationsPayload(int mask) implements CustomPacketPayload {

    public static final Type<PullStationsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "pull_stations"));

    /**
     * A fixed four bytes.
     *
     * <p>{@code INT} rather than {@code VAR_INT} because a full ring is bit 11 set, which a
     * variable-length encoding spends two bytes on anyway, and a fixed width has no length field
     * for a hostile client to lie about.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, PullStationsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    PullStationsPayload::mask,
                    PullStationsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
