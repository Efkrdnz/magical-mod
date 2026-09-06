package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SpaceWalkerActionPayload(int action, int index, String name, String dimension, int x, int y, int z) implements CustomPacketPayload {
    public static final int TELEPORT_COORDINATES = 0;
    public static final int SAVE_CURRENT = 1;
    public static final int TELEPORT_WAYPOINT = 2;
    public static final int DELETE_WAYPOINT = 3;
    public static final int CREATE_PORTAL_COORDINATES = 4;
    public static final int CREATE_PORTAL_WAYPOINT = 5;

    public static final Type<SpaceWalkerActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "space_walker_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpaceWalkerActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.action);
                buf.writeInt(payload.index);
                buf.writeUtf(payload.name, 32);
                buf.writeUtf(payload.dimension, 128);
                buf.writeInt(payload.x);
                buf.writeInt(payload.y);
                buf.writeInt(payload.z);
            },
            buf -> new SpaceWalkerActionPayload(
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf(32),
                    buf.readUtf(128),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
