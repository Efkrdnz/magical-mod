package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One rule, chosen on the three Weave wheels and released. Validated again on the server. */
public record ApplyWeaveRulePayload(int aspect, int operation, int subject) implements CustomPacketPayload {
    public static final Type<ApplyWeaveRulePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "apply_weave_rule"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyWeaveRulePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    ApplyWeaveRulePayload::aspect,
                    ByteBufCodecs.INT,
                    ApplyWeaveRulePayload::operation,
                    ByteBufCodecs.INT,
                    ApplyWeaveRulePayload::subject,
                    ApplyWeaveRulePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
