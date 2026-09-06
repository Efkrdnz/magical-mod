package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CastWheelSubSkillPayload(ResourceLocation parentSkillId, int mode) implements CustomPacketPayload {
    public static final Type<CastWheelSubSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "cast_wheel_sub_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastWheelSubSkillPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    CastWheelSubSkillPayload::parentSkillId,
                    ByteBufCodecs.INT,
                    CastWheelSubSkillPayload::mode,
                    CastWheelSubSkillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
