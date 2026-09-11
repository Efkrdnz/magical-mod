package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.Arrays;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The nine shapes a blood mage keeps, one per number key.
 *
 * <p>Mutable rather than a record for the same reason {@link MagicLoadout} is: the state that owns
 * it is mutable throughout, and a record would mean rebuilding the book on every slot edit for no
 * gain. The shapes inside it are immutable, which is what makes the tag cache below safe.
 *
 * <p><b>Why the tag is cached.</b> {@code PlayerMagicState.sync} re-serialises the whole state to
 * build its dedupe key, from over a hundred call sites. Nine full shapes are about five kilobytes
 * of ints, and comparing them on every one of those calls would be five kilobytes of pointless work
 * per sync forever. Handing back the same {@link ListTag} instance until something actually changes
 * turns that comparison into a reference check, because {@code AbstractList.equals} short circuits
 * on identity.
 *
 * <p>The cache is rebuilt as a new instance on every edit and never mutated in place. The payload
 * that carries this state guarantees the tag taken at call time <em>is</em> the snapshot, and a
 * shared tag edited afterwards would quietly rewrite a snapshot already on its way out.
 */
public final class BloodShapeBook {

    private final BloodShape[] shapes = new BloodShape[BloodShapeRules.SHAPE_SLOTS];
    private ListTag cachedTag;

    public BloodShapeBook() {
        Arrays.fill(shapes, BloodShape.EMPTY);
    }

    public static boolean isSlot(int index) {
        return index >= 0 && index < BloodShapeRules.SHAPE_SLOTS;
    }

    /** The shape in a slot. Never null - an unset slot holds {@link BloodShape#EMPTY}. */
    public BloodShape shape(int index) {
        return isSlot(index) ? shapes[index] : BloodShape.EMPTY;
    }

    public void setShape(int index, BloodShape shape) {
        if (!isSlot(index)) {
            return;
        }
        shapes[index] = shape == null ? BloodShape.EMPTY : shape;
        cachedTag = null;
    }

    public boolean isEmpty() {
        for (BloodShape shape : shapes) {
            if (!shape.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return shapes.length;
    }

    public BloodShapeBook copy() {
        BloodShapeBook copy = new BloodShapeBook();
        // The shapes are immutable, so sharing them is sharing a value. The cache is deliberately
        // not carried across: a copy handing out the original's tag instance would make two states
        // look identical to a dedupe that exists to tell them apart.
        System.arraycopy(shapes, 0, copy.shapes, 0, shapes.length);
        return copy;
    }

    /**
     * Every slot, empty ones included.
     *
     * <p>Dropping the empties would shift the indices the number keys refer to, so a player who left
     * slot 1 blank would find slot 2 answering the 1 key after a relog. The same rule the loadout
     * list follows, for the same reason.
     */
    public ListTag save() {
        if (cachedTag != null) {
            return cachedTag;
        }
        ListTag list = new ListTag();
        for (BloodShape shape : shapes) {
            list.add(write(shape));
        }
        cachedTag = list;
        return list;
    }

    private static CompoundTag write(BloodShape shape) {
        CompoundTag tag = new CompoundTag();
        if (shape.isEmpty()) {
            // An unset slot is one byte. Writing its height and flags would persist settings for a
            // drawing that does not exist.
            return tag;
        }
        tag.put("p", new IntArrayTag(shape.pointsCopy()));
        int[] ends = shape.strokeEndsCopy();
        byte[] packedEnds = new byte[ends.length];
        for (int i = 0; i < ends.length; i++) {
            // The caps guarantee an end fits a byte: four strokes of thirty-two points is 128.
            packedEnds[i] = (byte) ends[i];
        }
        tag.put("e", new ByteArrayTag(packedEnds));
        tag.putByte("h", (byte) shape.heightPercent());
        // Signed, and -100..100 fits a byte with room to spare.
        tag.putByte("v", (byte) shape.spreadPercent());
        tag.putByte("f", (byte) shape.flags());
        return tag;
    }

    /**
     * Reads a book back, tolerating a tag from another build or from an editor.
     *
     * <p>A list longer than the book stops at nine rather than growing it - the same guard the
     * waypoint list uses, and the thing that keeps a hand-edited save from becoming unbounded memory
     * on the next login.
     */
    public static BloodShapeBook load(ListTag list) {
        BloodShapeBook book = new BloodShapeBook();
        if (list == null) {
            return book;
        }
        for (int i = 0; i < list.size() && i < book.shapes.length; i++) {
            Tag entry = list.get(i);
            if (entry instanceof CompoundTag tag) {
                book.shapes[i] = read(tag);
            }
        }
        return book;
    }

    private static BloodShape read(CompoundTag tag) {
        if (!tag.contains("p", Tag.TAG_INT_ARRAY) || !tag.contains("e", Tag.TAG_BYTE_ARRAY)) {
            return BloodShape.EMPTY;
        }
        int[] points = tag.getIntArray("p");
        byte[] rawEnds = tag.getByteArray("e");
        int[] ends = new int[rawEnds.length];
        for (int i = 0; i < rawEnds.length; i++) {
            ends[i] = rawEnds[i] & 0xFF;
        }
        // A tag written before the spread slider existed has no "v", and getByte answers zero for
        // a missing key - which is the centred default, so an old save reads as the sheet it was.
        return BloodShape.ofFlat(points, ends, tag.getByte("h"), tag.getByte("v"), tag.getByte("f"));
    }
}
