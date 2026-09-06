package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent whenever a player swings a melee weapon. The server fires a flying slash in the look
 * direction; {@code whiff} tells it the swing found no entity under the crosshair, so it should
 * also snap a real vanilla hit onto a nearby enemy in front (forgiving aim for fast combat).
 */
public record MeleeSwingPayload(boolean whiff) implements CustomPacketPayload {
    public static final Type<MeleeSwingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "melee_swing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MeleeSwingPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, MeleeSwingPayload::whiff, MeleeSwingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
