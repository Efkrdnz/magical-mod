package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DaydreamCoreTest {
    private static final Offset O = new Offset(0, 0, 0);

    @Test
    void aPointIsTheCursorAndALineRunsCornerToCorner() {
        assertEquals(List.of(new Offset(2, 1, 0)), Brush.POINT.cells(O, new Offset(2, 1, 0)));
        List<Offset> line = Brush.LINE.cells(O, new Offset(3, 0, 1));
        assertEquals(4, line.size());
        assertEquals(O, line.get(0));
        assertEquals(new Offset(3, 0, 1), line.get(3));
    }

    @Test
    void aWallStandsAlongItsLongerSideAndABoxFillsItsCuboid() {
        List<Offset> wall = Brush.WALL.cells(O, new Offset(2, 1, 1));
        assertEquals(6, wall.size(), "x 0..2 by y 0..1, at the first corner's z");
        assertTrue(wall.stream().allMatch(o -> o.dz() == 0));
        assertEquals(8, Brush.BOX.cells(O, new Offset(1, 1, 1)).size());
        assertEquals(Brush.MAX_CELLS, Brush.BOX.cells(O, new Offset(9, 9, 9)).size());
        assertEquals(Brush.POINT, Brush.BOX.next());
    }

    @Test
    void theRayStopsInFrontOfTheFirstSolidCell() {
        DraftRay.Hit down = DraftRay.march(new Vec3(0.5, 3.5, 0.5), new Vec3(0, -1, 0), 6.0, pos -> pos.getY() <= 0);
        assertEquals(new BlockPos(0, 0, 0), down.solid());
        assertEquals(new BlockPos(0, 1, 0), down.place());

        DraftRay.Hit wall = DraftRay.march(new Vec3(0.5, 1.5, 0.5), new Vec3(1, 0, 0), 6.0, pos -> pos.getX() >= 3);
        assertEquals(new BlockPos(3, 1, 0), wall.solid());
        assertEquals(new BlockPos(2, 1, 0), wall.place());
    }

    @Test
    void aRayThatMeetsNothingEndsInTheAirAtItsReach() {
        DraftRay.Hit air = DraftRay.march(new Vec3(0.5, 3.5, 0.5), new Vec3(0, 0, 1), 6.0, pos -> false);
        assertNull(air.solid());
        assertEquals(new BlockPos(0, 3, 6), air.place());
    }

    @Test
    void aReverieIsRefusedForWhatItsWielderNeverStudiedOrCannotAfford() {
        Lexicon lexicon = new Lexicon();
        lexicon.gaze("block:minecraft:stone");
        Reverie reverie = new Reverie();
        reverie.addBlock(O, "minecraft:stone", lexicon);
        assertEquals(Reverie.Refusal.NONE, reverie.validate(lexicon));

        reverie.put(new ImaginedBlock(new Offset(1, 0, 0), "minecraft:gold_block", java.util.Set.of()));
        assertEquals(Reverie.Refusal.UNKNOWN, reverie.validate(lexicon));

        Reverie big = new Reverie();
        for (int i = 0; i < 20; i++) {
            big.put(new ImaginedBlock(new Offset(i - 10, 0, 0), "minecraft:stone", java.util.Set.of()));
        }
        assertEquals(Reverie.Refusal.FULL, big.validate(lexicon), "one impression buys 18, not 20");
    }

    @Test
    void aNameIsCappedWhereverItComesFrom() {
        Reverie direct = new Reverie();
        direct.setName("x".repeat(500));
        assertEquals(Reverie.MAX_NAME_LENGTH, direct.name().length());

        net.minecraft.nbt.CompoundTag forged = new net.minecraft.nbt.CompoundTag();
        forged.putString("name", "y".repeat(30000));
        assertEquals(Reverie.MAX_NAME_LENGTH, ReverieNbt.load(forged).name().length());
    }
}
