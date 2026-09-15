package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open the Blood Sacrifice pact screen.
 *
 * <p>It carries only the two durations. Everything else the screen needs - the Vessel, the tuning,
 * whether a Hellbroker is holding the pact - is already in the synced state; these two are the one
 * thing that would otherwise mean resolving the skill's stats a second time on the client.
 */
public record OpenBloodSacrificePayload(int boonTicks, int priceTicks) implements CustomPacketPayload {

    public static final Type<OpenBloodSacrificePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_blood_sacrifice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBloodSacrificePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OpenBloodSacrificePayload::boonTicks,
                    ByteBufCodecs.VAR_INT,
                    OpenBloodSacrificePayload::priceTicks,
                    OpenBloodSacrificePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
