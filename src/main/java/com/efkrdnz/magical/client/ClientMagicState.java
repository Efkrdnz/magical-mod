package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.PlayerMagicState;

public final class ClientMagicState {
    private static PlayerMagicState current = new PlayerMagicState();

    private ClientMagicState() {}

    public static PlayerMagicState get() {
        return current;
    }

    public static void set(PlayerMagicState data) {
        current = data.copy();
    }
}
