package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A Daydream draft sent home. The server reads it through {@code ReverieNbt.load} and
 * {@code Reverie.validate}, so a forged packet can at worst describe a smaller scene than it meant to.
 */
public record SaveReveriePayload(int slot, CompoundTag data) implements CustomPacketPayload {
    /**
     * The quota vanilla gives every packet's tag. {@code NbtAccounter} charges far more than a byte
     * per byte (each compound, key and string carries overhead), so a maximal legitimate draft - 128
     * elements - costs several times its ~10 KB on the wire; a tighter quota would throw on it and
     * disconnect an honest player. {@code SaveReveriePayloadTest} holds a maximal draft under it.
     */
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    public static final Type<SaveReveriePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "save_reverie"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveReveriePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slot());
                buf.writeNbt(payload.data());
            },
            buf -> {
                int slot = buf.readVarInt();
                Tag data = buf.readNbt(NbtAccounter.create(MAX_BYTES));
                return new SaveReveriePayload(slot, data instanceof CompoundTag tag ? tag : new CompoundTag());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
