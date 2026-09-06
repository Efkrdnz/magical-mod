package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ApplySpaceRulePayload(int category, int operation, int targetGroup) implements CustomPacketPayload {
    public static final Type<ApplySpaceRulePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "apply_space_rule"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplySpaceRulePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    ApplySpaceRulePayload::category,
                    ByteBufCodecs.INT,
                    ApplySpaceRulePayload::operation,
                    ByteBufCodecs.INT,
                    ApplySpaceRulePayload::targetGroup,
                    ApplySpaceRulePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
