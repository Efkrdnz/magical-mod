package com.efkrdnz.magical.magic.mind;

import java.util.EnumSet;
import java.util.Set;

/** A layer laid over an element that makes it easier to believe; each is paid for at the unveil. */
public enum Sense {
    SOUND(1.25F),
    SHADOW(1.15F),
    SCENT(1.10F);

    private final float gain;

    Sense(float gain) {
        this.gain = gain;
    }

    public float gain() {
        return gain;
    }

    public static float multiplier(Set<Sense> senses) {
        float product = 1.0F;
        for (Sense sense : senses) {
            product *= sense.gain;
        }
        return product;
    }

    public static int mask(Set<Sense> senses) {
        int mask = 0;
        for (Sense sense : senses) {
            mask |= 1 << sense.ordinal();
        }
        return mask;
    }

    public static Set<Sense> fromMask(int mask) {
        EnumSet<Sense> senses = EnumSet.noneOf(Sense.class);
        for (Sense sense : values()) {
            if ((mask & (1 << sense.ordinal())) != 0) {
                senses.add(sense);
            }
        }
        return senses;
    }
}
