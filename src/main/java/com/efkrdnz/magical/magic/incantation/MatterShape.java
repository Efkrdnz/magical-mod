package com.efkrdnz.magical.magic.incantation;

/**
 * How a material body lays its matter: on the floor under its line as it flies (a spray), over the
 * floor within its radius where it stands (a flood), into what is already there within its radius
 * (a touch), or as a heap where it ends (a mound). {@link #NONE} on every body that is not matter.
 */
public enum MatterShape {
    NONE,
    SPRAY,
    FLOOD,
    TOUCH,
    MOUND;

    /** A spray and a mound fly; a flood and a touch stand. */
    public boolean flies() {
        return this == SPRAY || this == MOUND;
    }
}
