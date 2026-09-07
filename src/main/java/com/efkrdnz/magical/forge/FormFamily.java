package com.efkrdnz.magical.forge;

public enum FormFamily {
    SLASH, CLEAVE, THRUST, SPIN, SLAM, WAVE, RISING, FLURRY;

    public boolean grounded() {
        return this == SLAM;
    }

    public boolean projectile() {
        return this == WAVE;
    }

    public boolean anchored() {
        return this != WAVE && this != SLAM;
    }
}
