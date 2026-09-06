package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CastWheelSkillPayload(ResourceLocation skillId) implements CustomPacketPayload {
    public static final Type<CastWheelSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "cast_wheel_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CastWheelSkillPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, CastWheelSkillPayload::skillId, CastWheelSkillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
