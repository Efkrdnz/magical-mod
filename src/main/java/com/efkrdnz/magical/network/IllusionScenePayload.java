package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A scene's imagined blocks, sent to each player near it; figments travel as entities. */
public record IllusionScenePayload(int scene, boolean mine, List<Cell> cells) implements CustomPacketPayload {
    /** Twice the largest budget: a reverie is at most 128 elements. */
    public static final int MAX_CELLS = 256;

    public record Cell(BlockPos pos, int state, int element) {
        public static final StreamCodec<ByteBuf, Cell> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Cell::pos,
                ByteBufCodecs.VAR_INT, Cell::state,
                ByteBufCodecs.VAR_INT, Cell::element,
                Cell::new);
    }

    public static final Type<IllusionScenePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "illusion_scene"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IllusionScenePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, IllusionScenePayload::scene,
            ByteBufCodecs.BOOL, IllusionScenePayload::mine,
            Cell.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)), IllusionScenePayload::cells,
            IllusionScenePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
