package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClassActionPayload(ResourceLocation classId, int action) implements CustomPacketPayload {
    public static final int EVOLVE = 1;
    public static final Type<ClassActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "class_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClassActionPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, ClassActionPayload::classId, ByteBufCodecs.INT, ClassActionPayload::action, ClassActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
