package com.efkrdnz.magical.magic.blood;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The synced field, across the wire and across versions.
 *
 * <p>Two things were added for the kit: where the field is anchored (the caster, as Manipulation
 * always was, or the effect entity itself, so a thrown spear carries its blood with it) and an
 * integrity that erodes a shell from the top instead of its age. Both default, so a tag written
 * before they existed still reads as the field it was.
 */
class BloodFieldDataTest {

    private static BloodFieldData sample() {
        List<double[]> lines = List.of(
                new double[] {0.0D, 0.0D, 1.5D, 0.0D, 3.0D, 0.5D},
                new double[] {-1.0D, -1.0D, -2.0D, -1.0D});
        return BloodFieldData.of(lines, 0.9F, 1.6F, 0.08F, 0.0625F, 37.0F, -12.0F, 9.0F,
                BloodShapeRules.FLAG_KEEP_ROTATING, 77);
    }

    @Test
    void aFieldSurvivesTheWireWhole() {
        BloodFieldData data = sample().withAnchor(BloodFieldData.ANCHOR_ENTITY).withIntegrity(0.4F);
        BloodFieldData back = BloodFieldData.decode(data.encode());
        assertNotNull(back);
        assertArrayEquals(data.spine(), back.spine());
        assertArrayEquals(data.ends(), back.ends());
        assertEquals(2, back.polylineCount());
        assertEquals(0.9F, back.heightOffset());
        assertEquals(1.6F, back.wallHeight());
        assertEquals(0.08F, back.thickness());
        assertEquals(0.0625F, back.pitch());
        assertEquals(37.0F, back.baseYaw());
        assertEquals(-12.0F, back.basePitch());
        assertEquals(9.0F, back.formTicks());
        assertTrue(back.keepRotating());
        assertEquals(77, back.ownerId());
        assertEquals(BloodFieldData.ANCHOR_ENTITY, back.anchor());
        assertEquals(0.4F, back.integrity(), 1.0E-6F);
    }

    @Test
    void aFieldIsAnchoredOnItsOwnerAndWholeUnlessAsked() {
        assertEquals(BloodFieldData.ANCHOR_OWNER, sample().anchor());
        assertEquals(1.0F, sample().integrity());
        assertEquals(BloodFieldData.ANCHOR_ENTITY, sample().withAnchor(BloodFieldData.ANCHOR_ENTITY).anchor());
    }

    @Test
    void aTagFromBeforeTheKitStillReadsAsTheFieldItWas() {
        // Manipulation never wrote these keys. Missing means the old behaviour, not a null field.
        CompoundTag tag = sample().encode();
        tag.remove(BloodFieldData.KEY_ANCHOR);
        tag.remove(BloodFieldData.KEY_INTEGRITY);
        BloodFieldData back = BloodFieldData.decode(tag);
        assertNotNull(back);
        assertEquals(BloodFieldData.ANCHOR_OWNER, back.anchor());
        assertEquals(1.0F, back.integrity());
    }

    @Test
    void integrityIsHeldToTheUnitRangeWhereverItComesFrom() {
        assertEquals(1.0F, sample().withIntegrity(3.0F).integrity(), "over the top is whole");
        assertEquals(0.0F, sample().withIntegrity(-1.0F).integrity(), "below the bottom is gone");
        CompoundTag tag = sample().encode();
        tag.putFloat(BloodFieldData.KEY_INTEGRITY, 9.0F);
        assertEquals(1.0F, BloodFieldData.decode(tag).integrity(), "and a tag is not trusted either");
        tag.putByte(BloodFieldData.KEY_ANCHOR, (byte) 9);
        assertEquals(BloodFieldData.ANCHOR_OWNER, BloodFieldData.decode(tag).anchor(),
                "an anchor nobody defined falls back to the owner");
    }
}
