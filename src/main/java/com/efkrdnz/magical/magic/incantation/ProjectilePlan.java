package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/**
 * One body: the prototype, the verse that added it, the state it was stamped with at that instant,
 * and the payload it releases, if any, as a whole shot of its own.
 */
public record ProjectilePlan(VersePrototype prototype, ResourceLocation verse, ShotState stamped,
                             PayloadKind payloadKind, int fuseTicks, ShotPlan payload) {

    public boolean hasPayload() {
        return payload != null;
    }
}
