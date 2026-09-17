package com.efkrdnz.magical.magic.chaos;

import net.minecraft.core.BlockPos;

/**
 * One place stress can sit: a block, or a body.
 *
 * <p>Exactly one of the two is set. Bodies are held by entity id rather than by reference so the
 * Pile never keeps a dead entity alive, and blocks are immutable positions so a site can be a map
 * key without the caller having to think about it.
 */
public record PileSite(BlockPos block, int entityId) {

    public static PileSite of(BlockPos block) {
        return new PileSite(block.immutable(), -1);
    }

    public static PileSite of(int entityId) {
        return new PileSite(null, entityId);
    }

    public boolean isBlock() {
        return block != null;
    }

    @Override
    public String toString() {
        return isBlock() ? "b" + block.getX() + "," + block.getY() + "," + block.getZ() : "e" + entityId;
    }
}
