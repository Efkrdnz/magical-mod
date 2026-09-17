package com.efkrdnz.magical.magic.chaos;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * The wielder half of the Authority of Chaos, and the only part of it they carry.
 *
 * <p>Five {@link Fault}s in an order. Generation one of an avalanche gives way by the first, the
 * second generation by the second, and a cascade deeper than the sequence keeps the last thing it
 * was told. So a collapse <em>changes character as it grows</em>, along a curve its owner chose:
 * {@code SLUMP SLUMP HEAP SHED} is a landslide that gathers, converges and detonates.
 *
 * <p>This is deliberately portable. The Pile is out in the world where anyone can break it and it
 * does not survive the chunk unloading; the Fracture is on the wielder, and it is what makes two
 * Chaos wielders different from each other. Seven faults across five positions is 16807 machines,
 * all of them available on the day the Authority is taken, none of them farmed.
 */
public final class Fracture {

    /** Positions in the sequence. Generation n uses position n-1, clamped at the end. */
    public static final int LENGTH = 5;

    private final Fault[] faults = {Fault.SLUMP, Fault.SLUMP, Fault.BLOOM, Fault.HUNT, Fault.SHED};

    /** The fault a cascade at this generation gives way by. Generations are one-based. */
    public Fault at(int generation) {
        return faults[Math.max(0, Math.min(LENGTH - 1, generation - 1))];
    }

    public Fault get(int index) {
        return index >= 0 && index < LENGTH ? faults[index] : faults[0];
    }

    public boolean set(int index, Fault fault) {
        if (index < 0 || index >= LENGTH || fault == null) {
            return false;
        }
        faults[index] = fault;
        return true;
    }

    /** Back to the sequence every Chaos wielder starts with. Used when the Authority is lost. */
    public void reset() {
        faults[0] = Fault.SLUMP;
        faults[1] = Fault.SLUMP;
        faults[2] = Fault.BLOOM;
        faults[3] = Fault.HUNT;
        faults[4] = Fault.SHED;
    }

    public void copyFrom(Fracture other) {
        System.arraycopy(other.faults, 0, faults, 0, LENGTH);
    }

    public ListTag save() {
        ListTag tag = new ListTag();
        for (Fault fault : faults) {
            tag.add(StringTag.valueOf(fault.name()));
        }
        return tag;
    }

    /** Anything unreadable keeps the default at that position rather than collapsing the sequence. */
    public void load(ListTag tag) {
        if (tag == null) {
            return;
        }
        for (int i = 0; i < Math.min(LENGTH, tag.size()); i++) {
            for (Fault fault : Fault.values()) {
                if (fault.name().equals(tag.getString(i))) {
                    faults[i] = fault;
                    break;
                }
            }
        }
    }

    public static int tagType() {
        return Tag.TAG_STRING;
    }
}
