package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The stance the picker was left on, sent once as the key comes up.
 *
 * <p>One ordinal and nothing else, which is the whole of what the client is allowed to decide.
 * Every question about it is asked again on the server by {@code SwordStanceSkill.take} - does
 * this wielder have the skill, does the ordinal name a real stance, has their rung opened it -
 * so a forged packet naming stance 99 is clamped to the default and then refused for costing
 * nothing to refuse.
 *
 * <p>It replaces {@code PullStationsPayload}, which carried a twelve-bit mask of lattice
 * bearings to unwrite. There is no lattice and nothing to unwrite: the shape is chosen rather
 * than authored, so the packet that used to carry a demolition order now carries a choice.
 */
public record SetStancePayload(int ordinal) implements CustomPacketPayload {

    public static final Type<SetStancePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "set_stance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetStancePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    SetStancePayload::ordinal,
                    SetStancePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
