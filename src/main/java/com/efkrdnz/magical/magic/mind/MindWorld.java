package com.efkrdnz.magical.magic.mind;

/** The little that plausibility needs to know about the real world around a lie. */
public interface MindWorld {
    /** A real block a body would stand on or bump into. */
    boolean solid(int x, int y, int z);

    /** The real block's id, {@code minecraft:air} where there is none. */
    String blockId(int x, int y, int z);

    /** Daytime and nothing between this cell and the sky. */
    boolean openSkyDaylight(int x, int y, int z);

    boolean water(int x, int y, int z);
}
