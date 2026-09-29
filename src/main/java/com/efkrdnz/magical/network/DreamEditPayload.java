package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.mind.Brush;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** One Daydream edit in the wielder's own dream: real blocks placed or unmade, or the Flaw marked. Re-checked on arrival. */
public record DreamEditPayload(int action, List<BlockPos> cells, String impression, int entity) implements CustomPacketPayload {
    public static final int PLACE = 0;
    public static final int ERASE = 1;
    public static final int FLAW = 2;
    public static final int MAX_CELLS = Brush.MAX_CELLS;

    public static final Type<DreamEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "dream_edit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DreamEditPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DreamEditPayload::action,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CELLS)), DreamEditPayload::cells,
            ByteBufCodecs.stringUtf8(128), DreamEditPayload::impression,
            ByteBufCodecs.VAR_INT, DreamEditPayload::entity,
            DreamEditPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
