package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;

/** A Dreamscape to and from NBT; offsets as three-int arrays, the Flaw as whichever half it is. */
public final class DreamscapeNbt {
    private DreamscapeNbt() {}

    public static CompoundTag save(Dreamscape scape) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("plot", scape.plot());
        putOffset(tag, "arrival", scape.arrival());
        tag.putFloat("yaw", scape.arrivalYaw());
        Dreamscape.Flaw flaw = scape.flaw();
        if (flaw != null && flaw.block() != null) {
            putOffset(tag, "flaw_block", flaw.block());
        } else if (flaw != null) {
            tag.putUUID("flaw_figment", flaw.figment());
        }
        return tag;
    }

    /**
     * A save is trusted only as far as its plot: an arrival outside it is put back on the plot's
     * floor, and a Flaw block outside it is dropped, since nobody dreaming there could ever reach it.
     */
    public static Dreamscape load(CompoundTag tag) {
        Dreamscape scape = new Dreamscape(tag.getInt("plot"));
        Offset arrival = offset(tag, "arrival");
        boolean arrivalInside = arrival != null && standsInside(scape.plot(), arrival);
        scape.setArrival(arrivalInside ? arrival : new Offset(0, 0, 0), tag.getFloat("yaw"));
        Offset flawBlock = offset(tag, "flaw_block");
        if (flawBlock != null) {
            if (blockInside(scape.plot(), flawBlock)) {
                scape.markBlock(flawBlock);
            }
        } else if (tag.hasUUID("flaw_figment")) {
            scape.markFigment(tag.getUUID("flaw_figment"));
        }
        return scape;
    }

    /** Where a dreamer's feet would be: the middle of the cell across, its floor up. */
    private static boolean standsInside(int plot, Offset at) {
        Offset origin = DreamRules.origin(plot);
        return DreamRules.inside(plot, origin.dx() + at.dx() + 0.5, origin.dy() + at.dy(), origin.dz() + at.dz() + 0.5);
    }

    /** A block by its middle, as the preset writes its cells. */
    private static boolean blockInside(int plot, Offset at) {
        Offset origin = DreamRules.origin(plot);
        return DreamRules.inside(plot, origin.dx() + at.dx() + 0.5, origin.dy() + at.dy() + 0.5, origin.dz() + at.dz() + 0.5);
    }

    private static void putOffset(CompoundTag tag, String key, Offset offset) {
        tag.putIntArray(key, new int[] {offset.dx(), offset.dy(), offset.dz()});
    }

    private static Offset offset(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        return values.length == 3 ? new Offset(values[0], values[1], values[2]) : null;
    }
}
