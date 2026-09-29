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

    public static Dreamscape load(CompoundTag tag) {
        Dreamscape scape = new Dreamscape(tag.getInt("plot"));
        Offset arrival = offset(tag, "arrival");
        scape.setArrival(arrival == null ? new Offset(0, 0, 0) : arrival, tag.getFloat("yaw"));
        Offset flawBlock = offset(tag, "flaw_block");
        if (flawBlock != null) {
            scape.markBlock(flawBlock);
        } else if (tag.hasUUID("flaw_figment")) {
            scape.markFigment(tag.getUUID("flaw_figment"));
        }
        return scape;
    }

    private static void putOffset(CompoundTag tag, String key, Offset offset) {
        tag.putIntArray(key, new int[] {offset.dx(), offset.dy(), offset.dz()});
    }

    private static Offset offset(CompoundTag tag, String key) {
        int[] values = tag.getIntArray(key);
        return values.length == 3 ? new Offset(values[0], values[1], values[2]) : null;
    }
}
