package com.efkrdnz.magical.magic.mind;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.UUID;

/** One mind asleep: whose dream it is in, when it ends, where its body lies, and why it should wake now. */
final class DreamSession {
    final UUID dreamer;
    final UUID owner;
    /** The wielder in their own Dreamscape: no clock, no Flaw, free to build. */
    final boolean own;
    final int plot;
    long wakesAt;
    int sleeperId = -1;
    /** Where the body lies, so it can be found and its chunk let go without the dreamer to ask. */
    ResourceKey<Level> sleeperLevel;
    ChunkPos sleeperChunk;
    /** Set by anything that wakes the dreamer; the tick does the waking, never the event that noticed. */
    boolean wakeNow;
    /** A blow on the body, landed on the dreamer once they are back in it. */
    DamageSource hurt;
    float hurtAmount;

    DreamSession(UUID dreamer, UUID owner, boolean own, int plot) {
        this.dreamer = dreamer;
        this.owner = owner;
        this.own = own;
        this.plot = plot;
    }
}
