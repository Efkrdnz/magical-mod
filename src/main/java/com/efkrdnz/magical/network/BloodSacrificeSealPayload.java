package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: seal this pact - these boons, paid for with these prices.
 *
 * <p>Nothing in here is trusted. The server recomputes the budget from the player's own tuning,
 * refuses ids that are not on the two lists, refuses anything chosen twice and checks the Vessel
 * last, so the worst a forged packet achieves is the refusal line. The cap below is the one
 * defence that has to live on the wire itself: without it a single packet could ask the server to
 * allocate a list of any length at all.
 */
public record BloodSacrificeSealPayload(List<ResourceLocation> boons, List<ResourceLocation> prices)
        implements CustomPacketPayload {

    /**
     * The longest list either half may carry.
     *
     * <p>Sixteen is the whole catalogue on each side, which is far more than any budget can pay
     * for. It is a ceiling on a forgery, not a rule of the game - the rules are all in
     * {@code BloodSacrificeService.validate}.
     */
    public static final int MAX_ENTRIES = 16;

    public static final Type<BloodSacrificeSealPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "blood_sacrifice_seal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BloodSacrificeSealPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    BloodSacrificeSealPayload::boons,
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    BloodSacrificeSealPayload::prices,
                    BloodSacrificeSealPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
