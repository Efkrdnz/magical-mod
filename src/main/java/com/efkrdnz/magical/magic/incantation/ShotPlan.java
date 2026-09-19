package com.efkrdnz.magical.magic.incantation;

import java.util.List;

/** One shot: its bodies and the final state the spawner reads the shot-wide numbers from. */
public record ShotPlan(List<ProjectilePlan> bodies, ShotState state) {

    public ShotPlan {
        bodies = List.copyOf(bodies);
    }

    /** Bodies at every depth, payloads included. */
    public int countAll() {
        int count = 0;
        for (ProjectilePlan body : bodies) {
            count += 1 + (body.hasPayload() ? body.payload().countAll() : 0);
        }
        return count;
    }
}
