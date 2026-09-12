package com.efkrdnz.magical.boss.unwaking;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind.*;

class UnwakingHardeningTest {
    private static AABB box(Vec3 p) { return new AABB(p,p).inflate(.3,.9,.3); }
    private static UnwakingHazard hazard(UnwakingHazard.Kind kind,Vec3 from,Vec3 to) {
        return new UnwakingHazard(1,kind,0,from,to.subtract(from).normalize(),from.distanceTo(to),0,List.of(to),null);
    }
    @Test void twentyfoldDamageIncludesSleepingAttacksAndCannotBuffHarmlessMarkers() {
        assertEquals(20,UnwakingCombatRules.damageMultiplier());
        for(var kind:UnwakingHazard.Kind.values()) {
            // Grazing bodies sit outside the multiplier on purpose - it exists to make contact
            // lethal, and the whole point of the tunnel's obstacles is that they are not. Scaling
            // them with it would quietly undo the exception the moment anyone tuned the config.
            if(kind.grazing()) assertEquals(100*UnwakingHazard.Kind.GRAZE_FRACTION,kind.damageFor(100));
            else assertEquals(kind.baseDamageFor(100)*20,kind.damageFor(100));
            assertEquals(4,kind.powerTier());
        }
        assertEquals(480,PROCESSION_CUT.damageFor(20));
        assertEquals(960,CLOCK_STRIKE.damageFor(20));
        assertEquals(0,ARRIVAL.damageFor(20)); assertEquals(0,REFUSAL.damageFor(20));
        assertEquals(8,UnwakingCombatRules.PARRY_LEAD_TICKS);
    }
    @Test void guillotineHasARealWideBladeAndContinuousCollision() {
        var h=hazard(FIRMAMENT_GUILLOTINE,new Vec3(0,50,0),new Vec3(0,-50,0));
        assertTrue(h.intersects(52,box(new Vec3(0,0,180)),box(new Vec3(0,0,180))));
        assertFalse(h.intersects(52,box(new Vec3(0,0,199)),box(new Vec3(0,0,199))));
        assertTrue(h.intersects(52,box(new Vec3(-20,0,0)),box(new Vec3(20,0,0))));
        assertFalse(h.intersects(31,box(h.origin()),box(h.origin())));
        assertFalse(h.intersects(73,box(h.openings().getFirst()),box(h.openings().getFirst())));
    }
    @Test void convergingSunsHitTheirVolumeAndExpireBeforeTheRing() {
        var h=hazard(SIXFOLD_BURIAL,new Vec3(80,0,0),new Vec3(-24,0,0));
        assertTrue(h.intersects(68,box(Vec3.ZERO),box(Vec3.ZERO)));
        assertFalse(h.intersects(68,box(new Vec3(0,20,0)),box(new Vec3(0,20,0))));
        assertFalse(h.intersects(39,box(h.origin()),box(h.origin())));
        assertFalse(h.intersects(77,box(h.openings().getFirst()),box(h.openings().getFirst())));
        assertTrue(390+SIXFOLD_BURIAL.end<490);
    }
    @Test void ringHasAnActualWholeBodyOpeningAndAnOuterEscape() {
        Vec3 offset=new Vec3(100000,170,-180000);
        var h=hazard(NULL_HORIZON,offset.add(0,0,-96),offset.add(0,0,96));
        assertFalse(h.intersects(64,box(offset),box(offset)));
        assertTrue(h.intersects(64,box(offset.add(48,0,0)),box(offset.add(48,0,0))));
        assertFalse(h.intersects(64,box(offset.add(60,0,0)),box(offset.add(60,0,0))));
        assertTrue(h.intersects(64,box(offset.add(48,0,20)),box(offset.add(48,0,-20))));
        assertFalse(h.intersects(39,box(offset.add(48,0,-96)),box(offset.add(48,0,-96))));
        assertTrue(490+NULL_HORIZON.end<UnwakingAssaultState.SKY_TICKS);
    }
    @Test void everyOrderGetsEachPassageForExactlyItsOwnLength() {
        for(int order:UnwakingAssaultState.orders()) {
            var s=new UnwakingAssaultState(order,0,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,0);
            int[] counts=new int[UnwakingAssaultState.Passage.values().length];
            for(int t=0;t<UnwakingAssaultState.rotation(order).ticks();t++) {
                counts[s.passage(t).ordinal()]++;
                // The cheapest guard there is on the tick-sum invariant: if a rotation does not
                // total the assault, passageAge leaves its passage and this is where it shows.
                assertTrue(s.passageAge(t)>=0&&s.passageAge(t)<s.passage(t).ticks(),
                        "order "+order+" ran off passage "+s.passage(t)+" at "+t);
            }
            for(var passage:UnwakingAssaultState.Passage.values())
                assertEquals(UnwakingAssaultState.rotation(order).passages().contains(passage)?passage.ticks():0,
                        counts[passage.ordinal()],"order "+order+" spent the wrong time in "+passage);
        }
        var s=new UnwakingAssaultState(0,0,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,0);
        assertEquals(0,s.scenery(380)); assertEquals(1,s.palette(390)); assertEquals(0,s.palette(490));
        assertEquals(s.wound(),s.woundFrame(340).center());
        assertEquals(0,s.woundFrame(340).up().y,1e-8);
    }
    @Test void everyWindowTheControllerCanBuildSurvivesThePromptBounds() {
        // These two used to cover UnwakingGuard's own parry-claim resolver. That resolver had no
        // callers - parries go through MagicCounterService - and went with blocking. What actually
        // gates a parry now is the window arithmetic, and its failure mode is silence:
        // UnwakingCounterWindows.offer returns without a word for anything outside 3..16 ticks.
        int widest = UnwakingCombatRules.PARRY_LEAD_TICKS + UnwakingGuard.graceTicks(100000);
        assertEquals(4, UnwakingGuard.graceTicks(100000), "grace is capped so a bad line cannot widen the window forever");
        assertTrue(widest <= 16, "the widest window the controller can build is " + widest + ", which would be refused");
        // And the floor. A strike two ticks out on a local connection builds a window of 2 and is
        // dropped, so the lead has to stay comfortably above the minimum for the prompt to exist.
        assertTrue(UnwakingCombatRules.PARRY_LEAD_TICKS >= 3);
    }
}
