package com.efkrdnz.magical.magic;

/**
 * Which half of a domain law one pass over an entity is allowed to carry out.
 *
 * <p>A mob is simulated on the server, so the whole of a law reaches it there. A player is not.
 * Vanilla forwards a motion packet to the player it is about through {@code hurtMarked} alone
 * ({@code ServerEntity.sendChanges}; the {@code hasImpulse} route goes out over {@code broadcast},
 * and {@code ChunkMap.TrackedEntity.updatePlayer} never puts a player in its own audience), and
 * that packet replaces the client's velocity rather than nudging it - while the server's copy of a
 * player's velocity is barely a velocity at all, since nothing in {@code handleMovePlayer} ever
 * writes one. A law written onto a server-side player therefore either vanishes or fights the
 * player's own input.
 *
 * <p>So the two halves of a law run on two machines. The server hands out what a law costs -
 * damage, effects, air, flight, the body moved across the shell - and each client pushes the one
 * player whose movement it owns.
 */
public enum DomainPass {
    /** The server, acting on something it simulates itself. Both halves. */
    WHOLE(true, true),
    /** The server, acting on a player. Everything except the push. */
    CONSEQUENCES(false, true),
    /** A client, acting on its own player. The push and nothing else. */
    MOTION(true, false);

    private final boolean moves;
    private final boolean consequences;

    DomainPass(boolean moves, boolean consequences) {
        this.moves = moves;
        this.consequences = consequences;
    }

    /** True when this pass may write an entity's velocity. */
    public boolean moves() {
        return moves;
    }

    /** True when this pass may deal damage, grant an effect, move a body, or hand out flight. */
    public boolean consequences() {
        return consequences;
    }
}
