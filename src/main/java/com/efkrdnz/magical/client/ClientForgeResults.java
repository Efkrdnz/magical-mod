package com.efkrdnz.magical.client;

import com.efkrdnz.magical.network.ForgeResultPayload;
import java.util.Optional;
import net.minecraft.Util;

/**
 * Client-only holder for the most recent {@link ForgeResultPayload}. The Runeforge screen polls
 * this rather than reacting to the payload directly, so it works whether or not the screen is open
 * when the server replies.
 */
public final class ClientForgeResults {
    private static Optional<ForgeResultPayload> latest = Optional.empty();
    private static long receivedAtMillis;

    private ClientForgeResults() {}

    public static void accept(ForgeResultPayload payload) {
        latest = Optional.of(payload);
        receivedAtMillis = Util.getMillis();
    }

    public static Optional<ForgeResultPayload> latest() {
        return latest;
    }

    public static long receivedAtMillis() {
        return receivedAtMillis;
    }

    public static void clear() {
        latest = Optional.empty();
    }
}
