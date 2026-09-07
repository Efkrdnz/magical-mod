package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The combo pips the HUD draws after every forged strike: where in the chain the next press lands,
 * how long the window and the recovery still have to run, and the element colour to tint them.
 */
public record ForgeComboSyncPayload(int comboIndex, int chainLength, int windowTicksLeft, int readyInTicks,
        int elementColor) implements CustomPacketPayload {

    public static final Type<ForgeComboSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "forge_combo_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeComboSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ForgeComboSyncPayload::comboIndex,
            ByteBufCodecs.VAR_INT, ForgeComboSyncPayload::chainLength,
            ByteBufCodecs.VAR_INT, ForgeComboSyncPayload::windowTicksLeft,
            ByteBufCodecs.VAR_INT, ForgeComboSyncPayload::readyInTicks,
            ByteBufCodecs.VAR_INT, ForgeComboSyncPayload::elementColor,
            ForgeComboSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
