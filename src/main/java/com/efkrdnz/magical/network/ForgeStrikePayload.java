package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One forged-weapon attack input. {@code kind} says what the attack key did; {@code whiff} is set
 * on a press that had no entity under the crosshair (so the server may snap the vanilla hit onto a
 * nearby enemy); {@code chargeTicks} is how long the key was held, and is only read on
 * {@link #CHARGE_RELEASE}. The client never cancels vanilla attack/mine — this rides alongside it.
 */
public record ForgeStrikePayload(int kind, boolean whiff, int chargeTicks) implements CustomPacketPayload {

    public static final int PRESS = 0;
    public static final int CHARGE_BEGIN = 1;
    public static final int CHARGE_RELEASE = 2;
    public static final int CHARGE_CANCEL = 3;

    public static final Type<ForgeStrikePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "forge_strike"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeStrikePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ForgeStrikePayload::kind,
            ByteBufCodecs.BOOL, ForgeStrikePayload::whiff,
            ByteBufCodecs.VAR_INT, ForgeStrikePayload::chargeTicks,
            ForgeStrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
