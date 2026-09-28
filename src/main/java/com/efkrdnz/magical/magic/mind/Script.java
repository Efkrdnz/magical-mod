package com.efkrdnz.magical.magic.mind;

/** A figment's behaviour: how it stands, and how it answers a viewer. */
public record Script(Stance stance, Reaction reaction) {
    public static final Script DEFAULT = new Script(Stance.IDLE, Reaction.IGNORE);
}
