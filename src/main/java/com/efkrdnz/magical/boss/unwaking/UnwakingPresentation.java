package com.efkrdnz.magical.boss.unwaking;

import java.util.Comparator;
import java.util.List;
import static com.efkrdnz.magical.magic.ChronosEnvironmentService.*;

/** One presentation owner per moment. Scenery never duplicates an attack's world mesh. */
public final class UnwakingPresentation {
    private UnwakingPresentation() {}
    public static UnwakingHazard dominant(List<UnwakingHazard> hazards,long now) {
        return hazards.stream().filter(h->h.visible(h.age(now))&&!h.recovering(h.age(now)))
                .max(Comparator.comparingInt((UnwakingHazard h)->h.kind().major()?1:0).thenComparingLong(UnwakingHazard::start)).orElse(null);
    }
    public static float level(int effect,UnwakingPhase phase,long phaseAge,List<UnwakingHazard> hazards,long now,float quiet) {
        return switch(effect) {
            case EFFECT_THEME_SHIFT -> phase.inDomain()?1:0;
            case EFFECT_COLOR_PALETTE -> phase.inDomain()?smooth(phase==UnwakingPhase.ORIENTATION?phaseAge/40F:1):0;
            case EFFECT_CLOCKS_ONLY -> phase==UnwakingPhase.TRIAL_CHIME||phase==UnwakingPhase.DEATH?smooth(phaseAge/40F):quiet;
            case EFFECT_TIME_FREEZE -> phase==UnwakingPhase.TRIAL_BREATH?smooth(phaseAge/40F):0;
            default -> 0; // The wound, star, and hand have exactly one world-space renderer.
        };
    }
    public static float inversion(UnwakingPhase phase,long phaseAge,List<UnwakingHazard> hazards,long now) {
        // Inversion is held between boundaries; reversals don't flash the whole screen.
        return phase==UnwakingPhase.TRIAL_BREATH?smooth(phaseAge/40F):phase==UnwakingPhase.TRIAL_CHIME?1-smooth(phaseAge/40F):0;
    }
    public static float scenery(List<UnwakingHazard> hazards,long now) {
        var h=dominant(hazards,now);
        return h!=null&&h.kind().major()?1-0.85F*smooth(h.age(now)/12F):1;
    }
    public static float smooth(float t) { t=Math.clamp(t,0,1); return t*t*(3-2*t); }
}
