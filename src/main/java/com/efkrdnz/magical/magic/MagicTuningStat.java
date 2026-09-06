package com.efkrdnz.magical.magic;

public enum MagicTuningStat {
    DAMAGE,
    SPEED,
    SIZE,
    DURATION,
    EFFICIENCY;

    public String translationKey() {
        return "screen.magical.tuning." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
