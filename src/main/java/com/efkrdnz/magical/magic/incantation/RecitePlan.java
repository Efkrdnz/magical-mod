package com.efkrdnz.magical.magic.incantation;

import java.util.List;

/**
 * What one press produced. {@code manaSpent} is negative for a net refund. The cooldown is the
 * larger of beat and rest, never their sum, because that is what Noita waits for.
 */
public record RecitePlan(ShotPlan root, int beatTicks, int restTicks, boolean rests, int manaSpent, int manaLeft,
                         boolean frayed, List<ReciteEvent> events) {

    public RecitePlan {
        events = List.copyOf(events);
    }

    public int cooldownTicks() {
        return Math.max(0, Math.max(beatTicks, rests ? restTicks : 0));
    }

    public List<ProjectilePlan> bodies() {
        return root.bodies();
    }
}
