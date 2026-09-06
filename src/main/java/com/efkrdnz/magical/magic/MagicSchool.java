package com.efkrdnz.magical.magic;

public enum MagicSchool {
    ARCANE(0x72E4FF),
    FIRE(0xFF7A45),
    WATER(0x5BC0FF),
    SPATIAL(0x88DFFF),
    SOUL(0xD8F0FF),
    LIGHT(0xFFE17A),
    VOID(0xA57DFF);

    private final int color;

    MagicSchool(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public String translationKey() {
        return "school.magical." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
