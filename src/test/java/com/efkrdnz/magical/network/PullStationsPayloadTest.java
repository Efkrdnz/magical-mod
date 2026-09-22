package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.efkrdnz.magical.magic.skill.sword.TheBearingSkill;
import com.efkrdnz.magical.magic.sword.SwordArray;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * The Bearing's release survives the wire, and the mask is as wide as the Array is long.
 *
 * <p>A mask has no length field and no list, so there is nothing here for a hostile client to
 * over-run - which is why the codec does no validation at all. What has to hold instead is that
 * the width the client writes and the width the server reads are the same fact: {@link
 * TheBearingSkill#MASK_BITS} is {@link SwordArray#MAX_STATIONS}, so a full ring fits and there is
 * no thirteenth bit to lose. Everything else - whether a marked slot exists, whether the release
 * is charged - is re-asked in {@code TheBearingSkill.pull} against the live station count, and is
 * not this packet's business.
 */
class PullStationsPayloadTest {

    private static void roundTrip(PullStationsPayload packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            PullStationsPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, PullStationsPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void anEmptyReleaseRoundTrips() {
        roundTrip(new PullStationsPayload(0));
    }

    @Test
    void oneMarkedStationRoundTrips() {
        roundTrip(new PullStationsPayload(1 << 5));
    }

    @Test
    void aFullRingRoundTrips() {
        roundTrip(new PullStationsPayload((1 << TheBearingSkill.MASK_BITS) - 1));
    }

    @Test
    void everyBitAboveTheMaskSurvivesTheWireSoTheServerIsTheOneThatDropsIt() {
        // The codec deliberately does not clean the mask. If it did, the drop would happen in two
        // places and the server's check - the one that knows how many stations there actually are
        // - would look redundant to the next reader, who would then remove it.
        roundTrip(new PullStationsPayload(-1));
    }

    @Test
    void theMaskIsExactlyAsWideAsTheArrayIsLong() {
        assertEquals(SwordArray.MAX_STATIONS, TheBearingSkill.MASK_BITS,
                "a mask narrower than the Array hides a bearing the wielder marked; wider is a bit "
                        + "that can never name a slot");
    }
}
