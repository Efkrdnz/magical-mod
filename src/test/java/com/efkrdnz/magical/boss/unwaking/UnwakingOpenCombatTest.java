package com.efkrdnz.magical.boss.unwaking;

import java.util.List;
import java.util.HashSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind.*;

class UnwakingOpenCombatTest {
    private static AABB player(Vec3 p) { return new AABB(p.x-.3,p.y-.9,p.z-.3,p.x+.3,p.y+.9,p.z+.3); }
    private static UnwakingHazard hazard(UnwakingHazard.Kind kind, Vec3 origin, List<Vec3> gaps) {
        return new UnwakingHazard(1,kind,0,origin,new Vec3(1,0,0),512,0,gaps,null);
    }
    @Test void onlyThreeDistinctCleanFinishersOpenGuardAndItResetsAfterTenSeconds() {
        var guard = new UnwakingDivineGuard();
        assertEquals(.5F,guard.multiplier(0));
        assertFalse(guard.finish(1,false,0)); assertEquals(3,guard.segments(0));
        assertFalse(guard.finish(2,true,10)); assertFalse(guard.finish(2,true,11));
        assertFalse(guard.finish(1,true,12)); assertEquals(2,guard.segments(12)); assertEquals(.5F,guard.multiplier(12));
        assertFalse(guard.finish(3,true,20)); assertTrue(guard.finish(4,true,30));
        assertEquals(1.0F,guard.multiplier(30));
        assertEquals(0,guard.segments(110)); assertEquals(1.0F,guard.multiplier(110));
        assertEquals(0,guard.segments(229)); assertEquals(1.0F,guard.multiplier(229));
        assertEquals(3,guard.segments(230)); assertEquals(.5F,guard.multiplier(230));
        guard.reset(); assertEquals(3,guard.segments(231));
    }
    @Test void giantPlaneHitsAcrossChunksAndItsOpeningRequiresTheWholePlayer() {
        Vec3 far = new Vec3(12000,180,-19000);
        var h=hazard(WORLD_CUT,far,List.of(far.add(0,0,14)));
        assertTrue(h.intersects(58,player(far),player(far)));
        assertTrue(h.intersects(58,player(far.add(0,0,200)),player(far.add(0,0,200))));
        assertFalse(h.intersects(58,player(far.add(0,0,14)),player(far.add(0,0,14))));
        assertTrue(h.intersects(58,player(far.add(0,0,20.9)),player(far.add(0,0,20.9))));
        assertFalse(h.intersects(39,player(far),player(far)));
        assertFalse(h.intersects(77,player(far),player(far)));
    }
    @Test void fastCrossingAndStationaryPlayersAreBothCaughtByMovingPlanes() {
        var h=hazard(WORLD_CUT,Vec3.ZERO,List.of(new Vec3(0,20,0)));
        assertTrue(h.intersects(58,player(new Vec3(-10,0,0)),player(new Vec3(10,0,0))));
        assertTrue(h.intersects(58,player(Vec3.ZERO),player(Vec3.ZERO)));
        assertFalse(h.intersects(58,player(new Vec3(-10,20,0)),player(new Vec3(10,20,0))));
    }
    @Test void starHandAndLawHaveLargePhysicalReachWithoutSpawnClamps() {
        var hand=hazard(HORIZON_HAND,Vec3.ZERO,List.of());
        assertTrue(hand.intersects(40,player(new Vec3(240,0,0)),player(new Vec3(240,0,0))));
        assertFalse(hand.intersects(40,player(new Vec3(240,6,0)),player(new Vec3(240,6,0))));
        var star=hazard(FALLEN_STAR,Vec3.ZERO,List.of());
        assertTrue(star.intersects(60,player(new Vec3(38,0,0)),player(new Vec3(38,0,0))));
        assertFalse(star.intersects(60,player(new Vec3(44,0,0)),player(new Vec3(44,0,0))));
        var law=hazard(LAW_FRONT,new Vec3(10000,200,10000),List.of());
        assertTrue(law.intersects(64,player(new Vec3(9980,200,10000)),player(new Vec3(9980,200,10000))));
        assertFalse(law.intersects(64,player(new Vec3(10025,200,10000)),player(new Vec3(10025,200,10000))));
    }
    @Test void shellOpeningDoesNotForgiveAnIntersectingShoulder() {
        var h=hazard(STAR_FRONT,Vec3.ZERO,List.of(new Vec3(1,0,0)));
        double r=UnwakingOpenGeometry.frontRadius(36,false);
        assertFalse(h.intersects(36,player(new Vec3(r,0,0)),player(new Vec3(r,0,0))));
        Vec3 edge=new Vec3(Math.cos(Math.toRadians(25))*r,0,Math.sin(Math.toRadians(25))*r);
        assertTrue(h.intersects(36,player(edge),player(edge)));
    }
    @Test void everyScoreFinishesItsAttacksBeforeTransitionAndNeverLayersMajorVolumes() {
        var scores=new java.util.ArrayList<UnwakingCombinations.Score>();
        for(int i=0;i<3;i++) { scores.add(UnwakingCombinations.sleeping(i)); scores.add(UnwakingCombinations.closing(i)); }
        for(int i=0;i<4;i++) scores.add(UnwakingCombinations.awake(i));
        for(var phase:List.of(UnwakingPhase.TRIAL_SKY,UnwakingPhase.TRIAL_BREATH,UnwakingPhase.TRIAL_CHIME)) scores.add(UnwakingCombinations.trial(phase));
        assertEquals(scores.size(),new HashSet<>(scores.stream().map(UnwakingCombinations.Score::id).toList()).size());
        for(var score:scores) {
            for(var cue:score.cues()) assertTrue(cue.at()+cue.kind().end<=score.end(),score.id()+" truncates "+cue.kind());
            for(int t=0;t<score.end();t++) {
                int tick=t;
                assertTrue(score.cues().stream().filter(c->c.kind().major() && tick>=c.at() && tick<c.at()+c.kind().end).count()<=1,score.id());
            }
        }
    }
}
