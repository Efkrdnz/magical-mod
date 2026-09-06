package com.efkrdnz.magical.magic.cast;

/** How opponent-mob AI scores and uses a skill. NONE = mobs never cast it. */
public record MobCastProfile(Role role, float minRange, float maxRange, boolean needsGroundTarget, boolean selfCast) {
    public static final MobCastProfile NONE = new MobCastProfile(Role.NONE, 0.0F, 0.0F, false, false);

    public enum Role {
        NONE, ATTACK, CONTROL, DEFENCE, SUMMON, UTILITY
    }

    public static MobCastProfile attack(float minRange, float maxRange) {
        return new MobCastProfile(Role.ATTACK, minRange, maxRange, false, false);
    }

    public static MobCastProfile control(float minRange, float maxRange) {
        return new MobCastProfile(Role.CONTROL, minRange, maxRange, false, false);
    }

    public static MobCastProfile defence() {
        return new MobCastProfile(Role.DEFENCE, 0.0F, 12.0F, false, true);
    }

    public static MobCastProfile summon() {
        return new MobCastProfile(Role.SUMMON, 0.0F, 16.0F, true, true);
    }

    public boolean usable() {
        return role != Role.NONE;
    }
}
