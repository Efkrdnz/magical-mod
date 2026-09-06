package com.efkrdnz.magical.client;

import com.efkrdnz.magical.arcane.ArcanePlayerData;

public final class ClientArcaneState {
    private static ArcanePlayerData current = new ArcanePlayerData();

    private ClientArcaneState() {}

    public static ArcanePlayerData get() {
        return current;
    }

    public static void set(ArcanePlayerData data) {
        current = data.copy();
    }
}
