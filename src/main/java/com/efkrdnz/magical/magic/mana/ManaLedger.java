package com.efkrdnz.magical.magic.mana;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The book an Authority over Mana keeps, and the whole of what that Authority owns.
 *
 * <p>There is no region here and nothing ticks. The Authority of Space owns a volume and pushes
 * laws through it every tick; the Authority of Mana owns <em>prices</em>, and a price is not
 * anywhere. So this is a book: a list of spells the wielder has actually watched someone cast, and
 * a short list of rulings they have written about them. It is read once, at the instant a cast
 * resolves its numbers, and swept never.
 *
 * <p>The witnessed list is the half that makes it a Ledger rather than a menu. A wielder cannot
 * legislate a spell they have never seen, so what this Authority really spends is <em>presence</em>
 * - having been in the room when magic was used. The book fills by standing in fights.
 */
public final class ManaLedger {

    /** Spells the book holds at once. Past this the oldest sighting is forgotten, not refused. */
    public static final int MAX_WITNESSED = 48;

    /** Standing rulings. Small on purpose: an Authority that legislates everything legislates nothing. */
    public static final int MAX_WRITS = 6;

    /** How near an open book a cast has to be to be entered in it. */
    public static final double WITNESS_RANGE = 32.0D;

    /** What happened when a writ was offered to the book. */
    public enum Outcome {
        WRITTEN,
        STRUCK,
        NOTHING_TO_STRIKE,
        FULL,
        LOCKED
    }

    private boolean open;
    private final LinkedHashSet<ResourceLocation> witnessed = new LinkedHashSet<>();
    private final List<Writ> writs = new ArrayList<>();

    public boolean isOpen() {
        return open;
    }

    public void open() {
        open = true;
    }

    /** The book keeps what it recorded; closing only stops it recording more. */
    public void close() {
        open = false;
    }

    public void clear() {
        open = false;
        witnessed.clear();
        writs.clear();
    }

    /**
     * Enters a spell in the book. True when this is the first time the wielder has seen it, which is
     * what the announcement hangs on.
     */
    public boolean witness(ResourceLocation skillId) {
        if (skillId == null || !witnessed.add(skillId)) {
            return false;
        }
        while (witnessed.size() > MAX_WITNESSED) {
            Iterator<ResourceLocation> oldest = witnessed.iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    public boolean hasWitnessed(ResourceLocation skillId) {
        return witnessed.contains(skillId);
    }

    public List<ResourceLocation> witnessed() {
        return List.copyOf(witnessed);
    }

    public List<Writ> writs() {
        return List.copyOf(writs);
    }

    /**
     * Declares a writ, replaces the one already on that line, or strikes it out.
     *
     * <p>A line is one aspect of one target, so a spell carries one ruling about its price rather
     * than a stack of them, and rewriting a line the book already holds takes no new room.
     */
    public Outcome write(Writ writ) {
        if (writ == null) {
            return Outcome.NOTHING_TO_STRIKE;
        }
        int line = -1;
        for (int i = 0; i < writs.size(); i++) {
            if (writs.get(i).sameLineAs(writ)) {
                line = i;
                break;
            }
        }
        if (writ.operation().clears()) {
            if (line < 0) {
                return Outcome.NOTHING_TO_STRIKE;
            }
            writs.remove(line);
            return Outcome.STRUCK;
        }
        if (line >= 0) {
            // A lock refuses every hand, the author included. That is what a lock is for.
            if (writs.get(line).operation() == WritOperation.LOCK) {
                return Outcome.LOCKED;
            }
            writs.set(line, writ);
            return Outcome.WRITTEN;
        }
        if (writs.size() >= MAX_WRITS) {
            return Outcome.FULL;
        }
        writs.add(writ);
        return Outcome.WRITTEN;
    }

    public void copyFrom(ManaLedger other) {
        open = other.open;
        witnessed.clear();
        witnessed.addAll(other.witnessed);
        writs.clear();
        writs.addAll(other.writs);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Open", open);
        ListTag seen = new ListTag();
        for (ResourceLocation id : witnessed) {
            seen.add(StringTag.valueOf(id.toString()));
        }
        tag.put("Witnessed", seen);
        ListTag written = new ListTag();
        for (Writ writ : writs) {
            written.add(writ.save());
        }
        tag.put("Writs", written);
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        if (tag == null) {
            return;
        }
        open = tag.getBoolean("Open");
        ListTag seen = tag.getList("Witnessed", Tag.TAG_STRING);
        for (int i = 0; i < seen.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(seen.getString(i));
            if (id != null) {
                witness(id);
            }
        }
        ListTag written = tag.getList("Writs", Tag.TAG_COMPOUND);
        for (int i = 0; i < written.size() && writs.size() < MAX_WRITS; i++) {
            Writ writ = Writ.load(written.getCompound(i));
            if (writ != null) {
                writs.add(writ);
            }
        }
    }
}
