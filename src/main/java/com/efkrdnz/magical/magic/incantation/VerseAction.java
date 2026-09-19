package com.efkrdnz.magical.magic.incantation;

/**
 * A card's function: {@code data.action(recursion_level, iteration)}. The return is the Refrain
 * family's {@code iter_max}; every other verse returns {@link #NONE}, which is the Lua's nil.
 */
@FunctionalInterface
public interface VerseAction {

    int NONE = 0;

    int run(Recital recital, int recursion, int iteration);
}
