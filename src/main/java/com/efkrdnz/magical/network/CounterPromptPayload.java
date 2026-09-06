package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CounterPromptPayload(
        int threatId,
        ResourceLocation incomingSkillId,
        ResourceLocation counterSkillId,
        long deadlineTick,
        int windowTicks) implements CustomPacketPayload {
    public static final Type<CounterPromptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "counter_prompt"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CounterPromptPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.threatId);
                        ResourceLocation.STREAM_CODEC.encode(buf, payload.incomingSkillId);
                        ResourceLocation.STREAM_CODEC.encode(buf, payload.counterSkillId);
                        buf.writeLong(payload.deadlineTick);
                        buf.writeInt(payload.windowTicks);
                    },
                    buf -> new CounterPromptPayload(
                            buf.readInt(),
                            ResourceLocation.STREAM_CODEC.decode(buf),
                            ResourceLocation.STREAM_CODEC.decode(buf),
                            buf.readLong(),
                            buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
