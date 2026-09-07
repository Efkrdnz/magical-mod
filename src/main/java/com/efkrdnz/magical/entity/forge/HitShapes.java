package com.efkrdnz.magical.entity.forge;

import com.efkrdnz.magical.forge.FormFamily;

/** The one place a form family is turned into the volume it hits with. */
public final class HitShapes {

    private HitShapes() {}

    public static HitShape forFamily(FormFamily family) {
        return switch (family) {
            case SLASH, FLURRY, RISING -> SlashShape.INSTANCE;
            case CLEAVE -> CleaveShape.INSTANCE;
            case THRUST -> ThrustShape.INSTANCE;
            case SPIN -> SpinShape.INSTANCE;
            case SLAM -> SlamShape.INSTANCE;
            case WAVE -> WaveShape.INSTANCE;
        };
    }
}
