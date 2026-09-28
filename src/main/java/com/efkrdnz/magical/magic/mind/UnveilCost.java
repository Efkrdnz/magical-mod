package com.efkrdnz.magical.magic.mind;

/** The mana an unveil asks, before the wielder's cost scale. */
public final class UnveilCost {
    public static final int BASE = 10;
    public static final int PER_BLOCK = 1;
    public static final int PER_FIGMENT = 5;
    public static final int PER_SENSE_LAYER = 10;

    private UnveilCost() {}

    public static int of(Reverie reverie) {
        return BASE + PER_BLOCK * reverie.blocks().size() + PER_FIGMENT * reverie.figments().size()
                + PER_SENSE_LAYER * reverie.senseLayers();
    }
}
