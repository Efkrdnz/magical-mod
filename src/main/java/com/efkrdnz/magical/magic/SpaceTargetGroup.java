package com.efkrdnz.magical.magic;

import java.util.Locale;

public enum SpaceTargetGroup {
    EVERYTHING_EXCEPT_USER,
    EVERYTHING,
    LIVING_ENTITIES,
    PROJECTILES,
    PLAYERS;

    public String translationKey() {
        return "space.magical.target." + name().toLowerCase(Locale.ROOT);
    }
}
