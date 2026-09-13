package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a Manipulate Space rule the server accepted and wrote into the subspace. The
 * three ordinals are exactly what the client sent up in {@link ApplySpaceRulePayload}; it only
 * comes back once the mana was spent and the rule is live, so the flash it triggers never lies.
 */
public record SpaceRuleAppliedPayload(int category, int operation, int targetGroup) implements CustomPacketPayload {
    public static final Type<SpaceRuleAppliedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "space_rule_applied"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceRuleAppliedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SpaceRuleAppliedPayload::category,
                    ByteBufCodecs.INT,
                    SpaceRuleAppliedPayload::operation,
                    ByteBufCodecs.INT,
                    SpaceRuleAppliedPayload::targetGroup,
                    SpaceRuleAppliedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
