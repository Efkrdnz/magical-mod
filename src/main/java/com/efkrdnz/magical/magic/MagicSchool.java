package com.efkrdnz.magical.magic;

public enum MagicSchool {
    ARCANE(0x72E4FF),
    FIRE(0xFF7A45),
    WATER(0x5BC0FF),
    SPATIAL(0x88DFFF),
    SOUL(0xD8F0FF),
    LIGHT(0xFFE17A),
    VOID(0xA57DFF),
    // The forbidden schools, one per negative pyramid layer. Each is defined by what it costs the
    // caster rather than by what it does: body, decay, certainty, the world, being noticed.
    BLOOD(0xC4122B),
    /** Not VOID. Void is absence; Dark is a debt you signed. See MagicAttribute.DARK, which both share. */
    DARK(0x5B3A78),
    CHAOS(0xFF4FD8),
    PRIMORDIAL(0x7A6A4A),
    ELDRITCH(0x2FBF9E);

    private final int color;

    MagicSchool(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    /** True for the schools that live below the line and charge a price no positive tier asks for. */
    public boolean isForbidden() {
        return this == BLOOD || this == DARK || this == CHAOS || this == PRIMORDIAL || this == ELDRITCH;
    }

    public String translationKey() {
        return "school.magical." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
