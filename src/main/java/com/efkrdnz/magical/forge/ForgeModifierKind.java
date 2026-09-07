package com.efkrdnz.magical.forge;

public enum ForgeModifierKind {
    ECHO, PIERCE, SEEKING, LEECH, BINDING, REACH, HASTE, BRAND, SHATTER, GUARD;

    public int flag() {
        return 1 << ordinal();
    }
}
