package com.efkrdnz.magical.magic.incantation;

import net.minecraft.resources.ResourceLocation;

/** What happened during a recite, in order, with the payload depth it happened at. */
public record ReciteEvent(Kind kind, ResourceLocation verse, int depth) {

    public enum Kind {
        PLAYED,
        COPIED,
        FALTER_MANA,
        FALTER_SPENT,
        OVERRUN,
        REST,
        FRAYED,
        DISCARDED
    }
}
