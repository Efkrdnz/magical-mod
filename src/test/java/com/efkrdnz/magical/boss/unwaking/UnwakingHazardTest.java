package com.efkrdnz.magical.boss.unwaking;

import static org.junit.jupiter.api.Assertions.*;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind.*;
import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class UnwakingHazardTest {
    private static UnwakingHazard hazard(UnwakingHazard.Kind kind, Vec3 axis, int variant) { return new UnwakingHazard(1,kind,0,Vec3.ZERO,axis,24,variant,List.of(new Vec3(0,0,1)),null); }
    private static AABB player(double x,double y,double z) { return new AABB(x-0.3,y-0.9,z-0.3,x+0.3,y+0.9,z+0.3); }
    private static boolean hit(UnwakingHazard h,int tick,double x,double y,double z) { AABB b=player(x,y,z); return h.intersects(tick,b,b); }

    @Test void clockHasAnActualHoleAndOnlyItsHandDamages() {
        var h=hazard(HAND,new Vec3(1,0,0),0);
        assertFalse(hit(h,43,20,0,0)); assertTrue(hit(h,44,20,0,0));
        assertFalse(hit(h,44,0,0,0)); assertFalse(hit(h,44,0,0,20));
        assertFalse(hit(h,44,20,6,0)); assertFalse(hit(h,105,20,0,0));
    }
    @Test void rotatingHandAndFastPlayersCannotTunnel() {
        var h=hazard(HAND,new Vec3(1,0,0),0);
        assertTrue(h.intersects(45,player(30,0,-7),player(30,0,7)));
        assertFalse(h.intersects(45,player(30,5,-7),player(30,5,7)));
    }
    @Test void tiltedClockUsesItsAnnouncedPlane() {
        var h=hazard(HAND,new Vec3(Math.cos(Math.toRadians(35)),Math.sin(Math.toRadians(35)),0),1);
        Vec3 on=h.axis().scale(20), off=on.add(h.planeNormal().scale(6));
        assertTrue(hit(h,44,on.x,on.y,on.z)); assertFalse(hit(h,44,off.x,off.y,off.z));
    }
    @Test void cageCenterIsDangerousButTheWholeOpenCorridorIsSafe() {
        for(Vec3 axis:UnwakingHazard.CARDINALS) {
            var h=hazard(CONSTELLATION,axis,0);
            assertTrue(hit(h,60,0,0,0));
            Vec3 safe=axis.scale(10); assertFalse(hit(h,60,safe.x,safe.y,safe.z));
            Vec3 outside=axis.scale(18); assertFalse(hit(h,60,outside.x,outside.y,outside.z));
            assertFalse(hit(h,59,0,0,0)); assertFalse(hit(h,61,0,0,0));
        }
    }
    @Test void cageCorridorTestsFullHitboxAndSweptCrossing() {
        var h=hazard(CONSTELLATION,new Vec3(1,0,0),0);
        assertTrue(hit(h,60,2,0,0)); assertTrue(hit(h,60,10,5,0));
        assertTrue(h.intersects(60,player(-8,0,0),player(8,0,0)));
    }
    @Test void returningScarIsInertBetweenDistinctLeadingEdges() {
        var h=hazard(RETURNING,new Vec3(1,0,0),0);
        assertTrue(hit(h,46,10,0,0)); assertFalse(hit(h,65,10,0,0)); assertTrue(hit(h,91,10,0,0));
        assertEquals(0,h.contact(46)); assertEquals(1,h.contact(91));
        assertTrue(h.source(46,new Vec3(10,0,0)).x<10); assertTrue(h.source(91,new Vec3(10,0,0)).x>10);
    }
    @Test void foldHasSideAndVerticalEscapes() {
        var h=hazard(FOLD,new Vec3(1,0,0),0);
        assertTrue(hit(h,60,7,0,0)); assertFalse(hit(h,60,0,9,0)); assertFalse(hit(h,60,0,0,9)); assertFalse(hit(h,60,45,0,0));
    }
    @Test void shellsExcludeConesButLaterCentralPulsesPreventCamping() {
        var first=hazard(SHELL,new Vec3(0,1,0),0);
        var second=hazard(SHELL,new Vec3(0,1,0),1);
        assertFalse(hit(first,56,0,0,20)); assertTrue(hit(first,56,20,0,0));
        assertFalse(hit(first,64,0,0,0)); assertTrue(hit(second,64,0,0,0));
        assertFalse(hit(second,64,0,0,13));
    }
    @Test void shellOpeningRejectsPartiallyIntersectingPlayer() {
        var h=hazard(SHELL,new Vec3(0,1,0),0);
        assertTrue(h.safeOpening(player(0,0,20))); assertFalse(h.safeOpening(player(7.1,0,20)));
    }
    @Test void targetedPulsesHaveNoPretendMovementEscape() {
        for(var kind:List.of(PRESSURE,ATTENTION)) {
            var h=hazard(kind,new Vec3(0,0,1),0);
            assertTrue(hit(h,kind.impact,40,0,0)); assertFalse(hit(h,kind.impact-1,40,0,0));
        }
        // Both answer to the prompt. There is no guard tier any more: a shield changes nothing.
        for(var kind:List.of(PRESSURE,ATTENTION)) assertEquals(UnwakingHazard.Defense.PARRY,kind.defense);
        for(var kind:UnwakingHazard.Kind.values())
            assertTrue(kind.defense==UnwakingHazard.Defense.MOVE||kind.defense==UnwakingHazard.Defense.PARRY);
    }
    @Test void skyRendCutsWholeArenaHeightAndThenOrthogonalPlane() {
        var h=hazard(SKY_REND,new Vec3(1,0,0),0);
        assertTrue(hit(h,52,11,40,40)); assertFalse(hit(h,52,15,0,0));
        assertFalse(hit(h,51,0,0,0)); assertFalse(hit(h,53,0,0,0));
        assertTrue(hit(h,88,40,-40,11)); assertFalse(hit(h,88,0,0,15));
        assertEquals(0,h.contact(52)); assertEquals(1,h.contact(88));
    }
    @Test void inversionRequiresTheEntireBodyInsideARefuge() {
        var h=new UnwakingHazard(1,INVERSION,0,Vec3.ZERO,new Vec3(0,1,0),96,0,List.of(new Vec3(20,0,0)),null);
        assertFalse(hit(h,64,20,0,0)); assertFalse(hit(h,64,25,0,0));
        assertTrue(hit(h,64,27,0,0)); assertTrue(hit(h,64,0,40,0));
        assertFalse(hit(h,63,0,0,0));
    }
    @Test void fallingStarsThreatenAFullHeightColumnWithARealExit() {
        var h=hazard(STARFALL,new Vec3(0,1,0),0);
        assertTrue(hit(h,48,8,40,0)); assertTrue(hit(h,48,0,-40,0));
        assertFalse(hit(h,48,11,0,0)); assertFalse(hit(h,47,0,0,0));
    }
    @Test void damageFloorsAndHealthScalingKeepEndgameMistakesConsequential() {
        assertEquals(48,SKY_REND.baseDamageFor(20)); assertEquals(350,SKY_REND.baseDamageFor(1000));
        assertEquals(350,INVERSION.baseDamageFor(1000)); assertEquals(250,HAND.baseDamageFor(1000));
        assertEquals(180,PRESSURE.baseDamageFor(1000)); assertEquals(0,REFUSAL.baseDamageFor(1000));
        // Every kind that removes you floors well above a full health bar. The exceptions are the
        // two markers that do no damage at all, and the tunnel's obstacles, which are the one body
        // in the fight meant to be survivable - see the graze branch in damageFor.
        for (var kind : UnwakingHazard.Kind.values())
            if (kind != REFUSAL && kind != ARRIVAL && !kind.grazing()) assertTrue(kind.baseDamageFor(20)>=24);
    }
    @Test void finalGestureHasSeparateCutContactsAndCounterUsesOne() {
        assertEquals(1,hazard(GESTURE,new Vec3(0,0,1),0).contact(56));
        assertEquals(-1,hazard(GESTURE,new Vec3(0,0,1),1).contact(56));
    }
}
