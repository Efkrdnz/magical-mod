package com.efkrdnz.magical.boss.unwaking;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnwakingDomainTimelineTest {
    @Test void passagesHaveFixedWarningsAndQuietEnds() {
        for(var phase:List.of(UnwakingPhase.TRIAL_SKY,UnwakingPhase.TRIAL_BREATH,UnwakingPhase.TRIAL_CHIME)) {
            for(int tick=0;tick<40;tick++) assertTrue(UnwakingDomainTimeline.starting(phase,tick).isEmpty());
            for(int tick=280;tick<360;tick++) assertTrue(UnwakingDomainTimeline.starting(phase,tick).isEmpty());
        }
        List<Integer> pressure=new ArrayList<>(),fracture=new ArrayList<>(),attention=new ArrayList<>();
        for(int t=0;t<360;t++) {
            for(var cue:UnwakingDomainTimeline.starting(UnwakingPhase.TRIAL_BREATH,t)) (cue.kind()==UnwakingHazard.Kind.PRESSURE?pressure:fracture).add(t+cue.kind().impact);
            for(var cue:UnwakingDomainTimeline.starting(UnwakingPhase.TRIAL_CHIME,t)) attention.add(t+cue.kind().impact);
        }
        assertEquals(List.of(80,140,200,260),pressure); assertEquals(List.of(170,230),fracture); assertEquals(List.of(80,128,176,224,272),attention);
        assertTrue(pressure.stream().noneMatch(fracture::contains));
    }
    @Test void bodyIsUntargetableDuringTrialAndExposedAfterFortyReformationTicks() {
        for(var phase:List.of(UnwakingPhase.TRIAL_SKY,UnwakingPhase.TRIAL_BREATH,UnwakingPhase.TRIAL_CHIME)) {
            assertTrue(phase.hiddenBody()); assertFalse(phase.protectsPlayer()); assertFalse(phase.damageable(300));
        }
        assertFalse(UnwakingPhase.REFORMING.damageable(39)); assertTrue(UnwakingPhase.REFORMING.damageable(40));
        assertEquals(0.7F,UnwakingPhase.SLEEPING.healthFloor()); assertEquals(0,UnwakingPhase.AWAKE.healthFloor()); assertEquals(0,UnwakingPhase.FINAL.healthFloor());
    }
    @Test void parriesIncreaseOpeningWithoutMakingThemMandatory() {
        assertEquals(80,UnwakingDomainTimeline.reformationEnd(0)); assertEquals(140,UnwakingDomainTimeline.reformationEnd(100));
    }
    @Test void aerialAndFinalDecksHaveDeterministicDistinctLessons() {
        assertEquals(UnwakingHazard.Kind.SKY_REND,UnwakingDomainTimeline.deck(UnwakingPhase.AWAKE,0));
        assertEquals(UnwakingHazard.Kind.STARFALL,UnwakingDomainTimeline.deck(UnwakingPhase.AWAKE,4));
        assertEquals(UnwakingHazard.Kind.INVERSION,UnwakingDomainTimeline.deck(UnwakingPhase.AWAKE,9));
        assertEquals(UnwakingHazard.Kind.SKY_REND,UnwakingDomainTimeline.deck(UnwakingPhase.FINAL,0));
        assertEquals(UnwakingHazard.Kind.REFUSAL,UnwakingDomainTimeline.deck(UnwakingPhase.FINAL,10));
    }
}
