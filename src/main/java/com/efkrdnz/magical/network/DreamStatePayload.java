package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** To the wielder: whether they are in their own dream now, and where its Flaw is (a block, or an entity id, or neither). */
public record DreamStatePayload(boolean ownDream, Optional<BlockPos> flawBlock, int flawEntity) implements CustomPacketPayload {
    public static final Type<DreamStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DreamStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DreamStatePayload::ownDream,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), DreamStatePayload::flawBlock,
            ByteBufCodecs.VAR_INT, DreamStatePayload::flawEntity,
            DreamStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
