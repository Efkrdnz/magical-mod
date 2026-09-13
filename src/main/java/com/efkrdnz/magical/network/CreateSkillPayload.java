package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: fuse these two skills. The server runs the same formula checks the screen
 * showed, so a stale or forged pair does nothing but print the requirements line.
 */
public record CreateSkillPayload(ResourceLocation first, ResourceLocation second) implements CustomPacketPayload {
    public static final Type<CreateSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "create_skill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateSkillPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    CreateSkillPayload::first,
                    ResourceLocation.STREAM_CODEC,
                    CreateSkillPayload::second,
                    CreateSkillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
