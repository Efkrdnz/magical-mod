package com.efkrdnz.magical.boss.unwaking;

import java.util.ArrayList;
import java.util.List;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind.*;

/** Fixed passage score, independent of damage, mana, party size and client frame rate. */
public final class UnwakingDomainTimeline {
    public record Cue(UnwakingHazard.Kind kind, int variant) {}
    private UnwakingDomainTimeline() {}
    public static List<Cue> starting(UnwakingPhase phase, int age) {
        List<Cue> cues = new ArrayList<>();
        if (phase == UnwakingPhase.TRIAL_SKY && (age == 40 || age == 120 || age == 200)) cues.add(new Cue(SHELL, (age - 40) / 80));
        if (phase == UnwakingPhase.TRIAL_SKY && age == 240) cues.add(new Cue(INVERSION, 0));
        if (phase == UnwakingPhase.TRIAL_BREATH) {
            if (age == 40 || age == 100 || age == 160 || age == 220) cues.add(new Cue(PRESSURE, 0));
            if (age == 130 || age == 190) cues.add(new Cue(FRACTURE, age == 130 ? 0 : 1));
        }
        if (phase == UnwakingPhase.TRIAL_CHIME && age >= 44 && age <= 236 && (age - 44) % 48 == 0) cues.add(new Cue(ATTENTION, (age - 44) / 48));
        return List.copyOf(cues);
    }
    public static int reformationEnd(int fractures) { return 80 + 10 * Math.clamp(fractures, 0, 6); }
    public static UnwakingHazard.Kind deck(UnwakingPhase phase, int index) {
        return phase == UnwakingPhase.FINAL
                ? switch (index % 6) { case 0 -> SKY_REND; case 1 -> GESTURE; case 2 -> INVERSION; case 3 -> RETURNING; case 4 -> REFUSAL; default -> HAND; }
                : switch (index % 7) { case 0 -> SKY_REND; case 1 -> HAND; case 2 -> INVERSION; case 3 -> CONSTELLATION; case 4 -> STARFALL; case 5 -> RETURNING; default -> FOLD; };
    }
}
