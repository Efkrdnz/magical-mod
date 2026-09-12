package com.efkrdnz.magical.boss.unwaking;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class UnwakingRecoveryDataTest {
    @Test void victoryQualificationSurvivesRestartAndClearsIdempotently() {
        UUID player=UUID.randomUUID(); UnwakingRecoveryData data=new UnwakingRecoveryData();
        data.qualifyVictory(player); data.qualifyVictory(player);
        UnwakingRecoveryData restored=UnwakingRecoveryData.load(data.save(new CompoundTag(),null),null);
        assertTrue(restored.victoryPending(player)); restored.clearVictory(player); restored.clearVictory(player);
        assertFalse(UnwakingRecoveryData.load(restored.save(new CompoundTag(),null),null).victoryPending(player));
    }
    @Test void returnMarkersRoundTripWithoutLosingRunOrFlightState() {
        UUID player = UUID.randomUUID();
        var point = new UnwakingRecoveryData.ReturnPoint(UUID.randomUUID(), new Vec3(-50.5, 71, 22.5), 123, -20, 0.075F, true);
        UnwakingRecoveryData data = new UnwakingRecoveryData(); data.remember(player, point);
        UnwakingRecoveryData restored = UnwakingRecoveryData.load(data.save(new CompoundTag(), null), null);
        assertEquals(point, restored.point(player));
        assertTrue(restored.hasPending());
        restored.forget(player);
        assertFalse(UnwakingRecoveryData.load(restored.save(new CompoundTag(), null), null).hasPending());
    }

    @Test void malformedCoordinatesNeverBecomeTeleportDestinations() {
        UUID player = UUID.randomUUID();
        UnwakingRecoveryData data = new UnwakingRecoveryData();
        data.remember(player, new UnwakingRecoveryData.ReturnPoint(UUID.randomUUID(), new Vec3(Double.NaN, 2, 3), 0, 0, 0.05F, false));
        assertFalse(UnwakingRecoveryData.load(data.save(new CompoundTag(), null), null).hasPending());
    }
}
