package com.efkrdnz.magical.boss.unwaking;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.efkrdnz.magical.magic.ChronosEnvironmentService.*;

class UnwakingPresentationTest {
    private static UnwakingHazard hazard(UnwakingHazard.Kind kind) {
        return new UnwakingHazard(1,kind,0,Vec3.ZERO,new Vec3(1,0,0),512,0,List.of(),null);
    }
    @Test void majorAttacksDimSceneryWithoutDuplicatingTheirMeshes() {
        for (var kind : List.of(UnwakingHazard.Kind.WORLD_CUT,UnwakingHazard.Kind.HORIZON_HAND,UnwakingHazard.Kind.FALLEN_STAR)) {
            var hazards = List.of(hazard(kind));
            assertEquals(0.15F,UnwakingPresentation.scenery(hazards,30),0.001);
            for (int effect : new int[]{EFFECT_SKY_CUT,EFFECT_PULSE_STORM,EFFECT_STAR_RAIN,EFFECT_SKY_VORTEX})
                assertEquals(0,UnwakingPresentation.level(effect,UnwakingPhase.AWAKE,200,hazards,30,0));
        }
        assertEquals(1,UnwakingPresentation.scenery(List.of(),30));
    }
    @Test void paletteCrossfadesHaveTwoSecondDurationAndNoSingleTickFlash() {
        assertEquals(0,UnwakingPresentation.smooth(0)); assertEquals(1,UnwakingPresentation.smooth(1));
        for (int t=1;t<=40;t++) assertTrue(UnwakingPresentation.smooth(t/40F)-UnwakingPresentation.smooth((t-1)/40F)<0.04);
        assertEquals(1,UnwakingPresentation.inversion(UnwakingPhase.TRIAL_BREATH,40,List.of(),40));
        assertEquals(0,UnwakingPresentation.inversion(UnwakingPhase.TRIAL_CHIME,40,List.of(),40));
    }
    @Test void oneMajorWinsTheHudOverAPersonalFollowup() {
        var major=hazard(UnwakingHazard.Kind.WORLD_CUT);
        var personal=hazard(UnwakingHazard.Kind.DECREE);
        assertEquals(major,UnwakingPresentation.dominant(List.of(personal,major),20));
        assertNull(UnwakingPresentation.dominant(List.of(major),90));
    }
}
