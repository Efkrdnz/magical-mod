package com.efkrdnz.magical.forge;

public enum WeaponClass {
    SWORD(0, 1.0f, 0.0f),
    AXE(3, 1.25f, -0.3f);

    private final int recoveryDelta;
    private final float knockbackScale;
    private final float reachDelta;

    WeaponClass(int recoveryDelta, float knockbackScale, float reachDelta) {
        this.recoveryDelta = recoveryDelta;
        this.knockbackScale = knockbackScale;
        this.reachDelta = reachDelta;
    }

    public int recoveryDelta() {
        return recoveryDelta;
    }

    public float knockbackScale() {
        return knockbackScale;
    }

    public float reachDelta() {
        return reachDelta;
    }
}
