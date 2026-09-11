package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * That a drawn shape actually survives being stored - on disk, and across the wire.
 *
 * <p>{@code save()} is simultaneously the disk format and the network format, and the client
 * rebuilds its mirror through {@code copy()} rather than through either of them. A field wired into
 * two of those three works perfectly in single player and comes up empty in multiplayer, with
 * nothing logged and nothing thrown. {@code PlayerMagicState} warns about this in its own comments
 * three times over and it has still happened, so the third path gets an assertion rather than
 * another comment.
 */
class PlayerMagicStateBloodShapeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BloodShape drawing(int heightPercent, int flags) {
        return drawing(heightPercent, BloodShapeRules.DEFAULT_SPREAD_PERCENT, flags);
    }

    private static BloodShape drawing(int heightPercent, int spreadPercent, int flags) {
        return BloodShape.of(List.of(
                new int[] {BloodShapeRules.pack(0, 0), BloodShapeRules.pack(48, 16),
                        BloodShapeRules.pack(-32, 64)},
                new int[] {BloodShapeRules.pack(16, -16), BloodShapeRules.pack(16, 96)}),
                heightPercent, spreadPercent, flags);
    }

    private static PlayerMagicState populated() {
        PlayerMagicState state = new PlayerMagicState();
        state.bloodShapes().setShape(0, drawing(40, BloodShapeRules.FLAG_TRACK_YAW));
        state.bloodShapes().setShape(4, drawing(100,
                BloodShapeRules.FLAG_TRACK_PITCH | BloodShapeRules.FLAG_KEEP_ROTATING));
        state.bloodShapes().setShape(8, drawing(0, 0));
        return state;
    }

    private static void assertSameDrawing(BloodShape expected, BloodShape actual, String where) {
        assertEquals(expected.strokeCount(), actual.strokeCount(), where + " stroke count");
        assertEquals(expected.pointCount(), actual.pointCount(), where + " point count");
        for (int i = 0; i < expected.pointCount(); i++) {
            assertEquals(expected.packed(i), actual.packed(i), where + " point " + i);
        }
        assertEquals(expected.heightPercent(), actual.heightPercent(), where + " height");
        assertEquals(expected.flags(), actual.flags(), where + " flags");
    }

    @Test
    void theBookSurvivesTheDiskFormatUnchanged() {
        PlayerMagicState original = populated();
        PlayerMagicState reloaded = PlayerMagicState.load(original.save());

        for (int slot = 0; slot < BloodShapeRules.SHAPE_SLOTS; slot++) {
            assertSameDrawing(original.bloodShapes().shape(slot), reloaded.bloodShapes().shape(slot),
                    "slot " + slot);
        }
    }

    @Test
    void writingTheReloadedStateBackOutProducesTheSameBytes() {
        // A round trip that loses nothing still fails the player if it writes something different
        // the second time: the sync dedupe compares tags, so an unstable format would resend the
        // whole state every tick forever.
        PlayerMagicState original = populated();
        CompoundTag once = original.save();
        CompoundTag twice = PlayerMagicState.load(once).save();

        assertEquals(once.getList("bloodShapes", Tag.TAG_COMPOUND),
                twice.getList("bloodShapes", Tag.TAG_COMPOUND),
                "save -> load -> save must be byte identical");
    }

    @Test
    void theClientMirrorGetsTheBookToo() {
        // The assertion this whole file exists for. copy() is how ClientMagicState rebuilds, and it
        // is the one of the three paths that fails silently.
        PlayerMagicState original = populated();
        PlayerMagicState mirror = original.copy();

        for (int slot = 0; slot < BloodShapeRules.SHAPE_SLOTS; slot++) {
            assertSameDrawing(original.bloodShapes().shape(slot), mirror.bloodShapes().shape(slot),
                    "slot " + slot + " did not reach the client");
        }
    }

    @Test
    void aCopiedBookIsItsOwnBookRatherThanAViewOfTheOriginal() {
        PlayerMagicState original = populated();
        PlayerMagicState mirror = original.copy();
        mirror.bloodShapes().setShape(0, BloodShape.EMPTY);

        assertFalse(original.bloodShapes().shape(0).isEmpty(),
                "editing the client mirror must not reach back into the server state");
    }

    @Test
    void aPlayerWhoHasNeverDrawnAnythingCostsNothingOnTheWire() {
        // Every player syncs this state and almost none of them will ever own the skill, so an
        // empty book must not put a key on the wire at all.
        assertFalse(new PlayerMagicState().save().contains("bloodShapes"),
                "an empty book belongs nowhere near the packet");
        assertTrue(PlayerMagicState.load(new CompoundTag()).bloodShapes().isEmpty(),
                "and a state that never had one loads an empty book rather than null");
    }

    @Test
    void theSavedTagIsTheSameInstanceUntilSomethingActuallyChanges() {
        // The sync dedupe compares the whole state tag on every one of a hundred-odd call sites.
        // Identity is what keeps five kilobytes of ints from being walked each time.
        BloodShapeBook book = new BloodShapeBook();
        book.setShape(0, drawing(50, 0));

        ListTag first = book.save();
        assertSame(first, book.save(), "an unchanged book must hand back the tag it already built");

        book.setShape(1, drawing(50, 0));
        assertNotSame(first, book.save(), "and must build a new one the moment it is edited");
    }

    @Test
    void aBookTagLongerThanTheBookIsTruncatedRatherThanGrownInto() {
        // The defence against a hand-edited save. Without the cap this is unbounded memory on login.
        ListTag oversized = new ListTag();
        for (int i = 0; i < 60; i++) {
            oversized.add(new CompoundTag());
        }
        assertEquals(BloodShapeRules.SHAPE_SLOTS, BloodShapeBook.load(oversized).size());
        assertTrue(BloodShapeBook.load(null).isEmpty(), "a missing list is an empty book, not a crash");
    }
}
