package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicSchool;
import org.junit.jupiter.api.Test;

/**
 * A material body names its matter and its shape, and no other body names either; a spray and a
 * clod fly, a sea and a touch stand for one pulse; a material verse names a material body and a
 * passive none. The prototype refuses a matter without a shape and a shape without a matter.
 */
class MatterTest {

    @Test
    void everyMatterHasASchoolAndALifetime() {
        for (Matter matter : Matter.values()) {
            assertNotNull(matter.school(), matter + " has a school");
            assertTrue(matter.lifetimeTicks() > 0, matter + " stands for a while");
        }
        assertTrue(Matter.WATER.isFluid());
        assertTrue(Matter.LAVA.isFluid());
        assertFalse(Matter.STONE.isFluid());
    }

    @Test
    void aMaterialBodyNamesItsMatterAndNothingElseDoes() {
        int material = 0;
        for (VersePrototype prototype : VersePrototypes.all()) {
            if (prototype.laysMatter()) {
                material++;
                assertNotNull(prototype.matter(), prototype.id() + " lays a matter");
                assertEquals(prototype.matter().school(), prototype.school(), prototype.id() + " is drawn in its matter's colour");
                assertEquals(prototype.shape().flies(), !prototype.isStatic(), prototype.id() + " flies or stands as its shape says");
                assertEquals(0.0D, prototype.damage(), prototype.id() + " carries no damage of its own");
                assertFalse(prototype.explodes(), prototype.id() + " carries no explosion");
            } else {
                assertNull(prototype.matter(), prototype.id() + " names no matter");
                assertEquals(MatterShape.NONE, prototype.shape(), prototype.id() + " has no shape");
            }
        }
        assertEquals(10, material, "the ten material bodies");
    }

    @Test
    void aStandingMaterialBodyStandsForOnePulse() {
        for (VersePrototype prototype : new VersePrototype[] {VersePrototypes.SEA_WATER, VersePrototypes.SEA_FLAME,
                VersePrototypes.SEA_LAVA, VersePrototypes.TOUCH_STONE, VersePrototypes.TOUCH_GLASS, VersePrototypes.TOUCH_WATER, VersePrototypes.TOUCH_ICE}) {
            assertTrue(prototype.isStatic(), prototype.id() + " stands");
            assertEquals(VersePrototypes.MATTER_STAND_TICKS, prototype.durationTicks(), prototype.id() + " stands for the mark's fade");
            assertEquals(1, Landing.pulses(prototype), prototype.id() + " pulses once");
        }
        assertTrue(VersePrototypes.SPRAY_WATER.shape().flies());
        assertTrue(VersePrototypes.CLOD.shape().flies());
        assertFalse(VersePrototypes.SPRAY_WATER.isStatic());
    }

    @Test
    void everyMaterialVerseNamesAMaterialBodyAndEveryPassiveNone() {
        for (Verse verse : VerseContent.CATALOGUE.ofType(VerseType.MATERIAL)) {
            assertTrue(verse.hasPrototype() && verse.prototype().laysMatter(), verse.id() + " names a material body");
            assertEquals(0, verse.declared().draw(), verse.id() + " draws nothing: it is a leaf");
        }
        for (Verse verse : VerseContent.CATALOGUE.ofType(VerseType.PASSIVE)) {
            assertFalse(verse.hasPrototype(), verse.id() + " names no body");
        }
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            if (verse.hasPrototype() && verse.prototype().laysMatter()) {
                assertEquals(VerseType.MATERIAL, verse.type(), verse.id() + " lays matter, so it is a material");
            }
        }
    }

    @Test
    void aPrototypeRefusesAMatterWithoutAShapeAndAShapeWithoutAMatter() {
        assertThrows(IllegalArgumentException.class, () -> new VersePrototype(VerseIds.body("odd"), MagicSchool.WATER, 0.0D, 0.0D, 0.0D, 0, 1.0F, true, 20,
                0.0D, 0.0D, null, null, 20, VersePrototype.Look.RING, false, Matter.WATER, MatterShape.NONE));
        assertThrows(IllegalArgumentException.class, () -> new VersePrototype(VerseIds.body("odd"), MagicSchool.WATER, 0.0D, 0.0D, 0.0D, 0, 1.0F, true, 20,
                0.0D, 0.0D, null, null, 20, VersePrototype.Look.RING, false, null, MatterShape.FLOOD));
    }
}
