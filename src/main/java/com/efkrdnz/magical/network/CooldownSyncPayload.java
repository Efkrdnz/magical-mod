package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The skill cooldowns the HUD draws, sent when they change rather than every tick.
 *
 * <p>{@code PlayerMagicState.save()} deliberately leaves cooldowns out of the state blob: a value
 * that changes every tick would defeat the change-gated sync. So they travel here instead, as a
 * start point the client extrapolates from. An entry with {@code remainingTicks == 0} removes the
 * skill; {@code replaceAll} wipes the client's map first (login, respawn, a dimension change, a
 * full clear). {@code totalTicks} is carried because the client cannot reproduce the server's
 * adjustments - the echo passive, the capped parent cooldowns, Circle Arsenal's field time.
 */
public record CooldownSyncPayload(boolean replaceAll, List<Entry> entries) implements CustomPacketPayload {

    public record Entry(ResourceLocation skillId, int remainingTicks, int totalTicks) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::skillId,
                ByteBufCodecs.VAR_INT, Entry::remainingTicks,
                ByteBufCodecs.VAR_INT, Entry::totalTicks,
                Entry::new);
    }

    /** More than the roster has skills; a hostile length is refused by the codec, not by us. */
    public static final int MAX_ENTRIES = 256;

    public static final Type<CooldownSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "cooldown_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CooldownSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CooldownSyncPayload::replaceAll,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), CooldownSyncPayload::entries,
            CooldownSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
