package com.efkrdnz.magical.client;

import com.efkrdnz.magical.network.ChronosEnvironmentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import com.efkrdnz.magical.boss.unwaking.UnwakingPhase;
import com.efkrdnz.magical.magic.ChronosEnvironmentService;

/**
 * Client-side state for Chronos End's boss-fight environment effects. Each effect eases
 * smoothly between 0 and 1 over its ramp; retriggering mid-ramp continues from the current
 * level instead of snapping.
 */
public final class ChronosClientEnvironment {
    private static final int EFFECT_COUNT = 8;
    private static final EffectState[] STATES = new EffectState[EFFECT_COUNT];

    private ChronosClientEnvironment() {}

    public static void handle(ChronosEnvironmentPayload payload) {
        if (payload.effect() < 0 || payload.effect() >= EFFECT_COUNT) {
            return; // unknown future effect - ignore gracefully
        }
        Minecraft minecraft = Minecraft.getInstance();
        double now = minecraft.level != null ? minecraft.level.getGameTime() : 0.0D;
        EffectState state = STATES[payload.effect()];
        if (state == null) {
            state = new EffectState();
            STATES[payload.effect()] = state;
        }
        state.baseLevel = level(payload.effect(), now);
        state.target = payload.active() ? 1.0F : 0.0F;
        state.startTime = now;
        state.rampTicks = Math.max(1, payload.rampTicks());
        if (payload.active()) {
            // Deactivation keeps the old strength so the fade-out keeps its identity
            // (a strength-2 star rain thins out as a strength-2 rain; a palette eases
            // back from the palette it was actually showing).
            state.strength = payload.strength();
        }
    }

    /** Current eased level of an effect, 0..1. {@code worldTime} is unwrapped game time + partial tick. */
    public static float level(int effect, double worldTime) {
        if (ClientUnwakingEncounter.ownsDomain()) {
            var assault=ClientUnwakingEncounter.assault(); long now=ClientUnwakingEncounter.estimatedTick();
            if(assault.active(now)) {
                if(effect==ChronosEnvironmentService.EFFECT_TIME_FREEZE) return assault.locked(now)?1:0;
                if(effect==ChronosEnvironmentService.EFFECT_CLOCKS_ONLY) return assault.passage(now)==com.efkrdnz.magical.boss.unwaking.UnwakingAssaultState.Passage.CLOCK?assault.effect(now):0;
            }
            return com.efkrdnz.magical.boss.unwaking.UnwakingPresentation.level(effect, ClientUnwakingEncounter.phase(),
                    ClientUnwakingEncounter.phaseAge(), ClientUnwakingEncounter.hazards(), ClientUnwakingEncounter.estimatedTick(), ClientUnwakingEncounter.quietAmount());
        }
        if (effect < 0 || effect >= EFFECT_COUNT) {
            return 0.0F;
        }
        EffectState state = STATES[effect];
        if (state == null) {
            return 0.0F;
        }
        float t = Mth.clamp((float) ((worldTime - state.startTime) / state.rampTicks), 0.0F, 1.0F);
        float eased = t * t * (3.0F - 2.0F * t);
        return Mth.lerp(eased, state.baseLevel, state.target);
    }

    public static float strength(int effect) {
        if (ClientUnwakingEncounter.ownsDomain()) return effect == ChronosEnvironmentService.EFFECT_COLOR_PALETTE ? ChronosEnvironmentService.PALETTE_BLACK_WHITE : 1;
        if (effect < 0 || effect >= EFFECT_COUNT || STATES[effect] == null) {
            return 1.0F;
        }
        return STATES[effect].strength;
    }

    public static float inversionMix() {
        return ClientUnwakingEncounter.ownsDomain() ? ClientUnwakingEncounter.inversion() : -1;
    }

    private static final class EffectState {
        private float baseLevel;
        private float target;
        private double startTime;
        private int rampTicks = 1;
        private float strength = 1.0F;
    }
}
