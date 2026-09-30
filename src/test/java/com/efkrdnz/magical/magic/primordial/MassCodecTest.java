package com.efkrdnz.magical.magic.primordial;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/** Every offset a mass can carry survives the trip to the client. */
class MassCodecTest {

    @Test
    void everyOffsetRoundTrips() {
        for (int x = -8; x <= 7; x++) {
            for (int y = -8; y <= 7; y++) {
                for (int z = -8; z <= 7; z++) {
                    assertArrayEquals(new int[] {x, y, z}, MassCodec.unpack(MassCodec.pack(x, y, z)));
                }
            }
        }
    }
}
