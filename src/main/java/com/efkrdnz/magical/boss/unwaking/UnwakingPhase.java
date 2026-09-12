package com.efkrdnz.magical.boss.unwaking;

/** Server-owned encounter states; transitions invalidate every outstanding contact. */
public enum UnwakingPhase {
    DORMANT, INTRO, SLEEPING, AWAKENING, ORIENTATION, DOMAIN_PREVIEW, RETURNING,
    AWAKE, UNBODYING, TRIAL_SKY, TRIAL_BREATH, TRIAL_CHIME, REFORMING, FINAL, DEATH;

    public boolean inDomain() {
        return this == ORIENTATION || this == DOMAIN_PREVIEW || ordinal() >= AWAKE.ordinal();
    }

    public boolean protectsPlayer() {
        return this != SLEEPING && this != AWAKE && this != FINAL && !trial();
    }

    /**
     * Whether the boss has stopped being the phase-two boss, which is what picks the assault rotation.
     *
     * <p>The gate is UNBODYING rather than FINAL so there is no gap: UNBODYING is the sixty-tick
     * cinematic the forty-percent crossing opens and it cannot host an assault itself, so in play
     * every enraged assault runs in FINAL - but anything that asks "is it angry yet" during those
     * sixty ticks gets the right answer.
     *
     * <p>Ordinal comparison, the same shape as {@link #inDomain()}: the declaration order of this
     * enum is load-bearing and both of these read it.
     */
    public boolean enraged() { return ordinal() >= UNBODYING.ordinal(); }

    public boolean trial() { return this == TRIAL_SKY || this == TRIAL_BREATH || this == TRIAL_CHIME; }
    public boolean hiddenBody() { return trial(); }
    public boolean damageable(long age) { return this == SLEEPING || this == AWAKE || this == FINAL || this == REFORMING && age >= 40; }
    public float healthFloor() { return this == SLEEPING ? 0.7F : 0; }
}
