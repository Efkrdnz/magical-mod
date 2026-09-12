package com.efkrdnz.magical.boss.unwaking;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static org.junit.jupiter.api.Assertions.*;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind.*;

class UnwakingAssaultTest {
    private static AABB box(Vec3 p) { return new AABB(p,p).inflate(.3,.9,.3); }
    /** Straight down through the player and out the other side; the body is at {@code offset} at tick 46. */
    private static UnwakingHazard fragment(Vec3 offset,int variant) {
        return new UnwakingHazard(1,FIRMAMENT_FRAGMENT,0,offset.add(0,72,0),new Vec3(0,-1,0),144,variant,
                List.of(offset.add(0,-72,0)),UUID.randomUUID());
    }
    @Test void theFragmentIsATelegraphedBodyRatherThanAnInstantLine() {
        var h=fragment(Vec3.ZERO,0);
        // Twenty-eight ticks of warning before it can touch anything, and gone four ticks before
        // it despawns - the same shape of window the converging suns use.
        assertFalse(h.intersects(27,box(Vec3.ZERO),box(Vec3.ZERO)));
        assertTrue(h.intersects(46,box(Vec3.ZERO),box(Vec3.ZERO)));
        assertFalse(h.intersects(65,box(Vec3.ZERO),box(Vec3.ZERO)));
        assertEquals(64,h.lastContactTick());
        assertTrue(FIRMAMENT_FRAGMENT.major());
    }
    @Test void theVariantCarriesTheFragmentBodySize() {
        assertEquals(8,UnwakingSkyGeometry.fragmentRadius(fragment(Vec3.ZERO,0)));
        assertEquals(5,UnwakingSkyGeometry.fragmentRadius(fragment(Vec3.ZERO,10)));
        // Drawn radius is collision radius, so these bounds are also what the player sees.
        var big=fragment(Vec3.ZERO,0);
        assertTrue(big.intersects(46,box(new Vec3(8,0,0)),box(new Vec3(8,0,0))));
        assertFalse(big.intersects(46,box(new Vec3(9,0,0)),box(new Vec3(9,0,0))));
        var small=fragment(Vec3.ZERO,10);
        assertTrue(small.intersects(46,box(new Vec3(5,0,0)),box(new Vec3(5,0,0))));
        assertFalse(small.intersects(46,box(new Vec3(6,0,0)),box(new Vec3(6,0,0))));
    }
    @Test void crossingAFragmentBetweenTicksStillContactsAtDistantCoordinates() {
        Vec3 offset=new Vec3(120000,200,-180000); var h=fragment(offset,0);
        assertTrue(h.intersects(46,box(offset.add(-9,0,0)),box(offset.add(9,0,0))));
        assertFalse(h.intersects(46,box(offset.add(-9,20,0)),box(offset.add(9,20,0))));
    }
    @Test void verdictStopsForTwelveTicksThenRetracesItsOriginalPath() {
        var h=new UnwakingHazard(3,RETURNING_VERDICT,0,new Vec3(-48,0,0),new Vec3(1,0,0),96,0,List.of(new Vec3(48,0,0)),null);
        assertEquals(new Vec3(48,0,0),UnwakingAssaultGeometry.position(h,52));
        assertEquals(UnwakingAssaultGeometry.position(h,52),UnwakingAssaultGeometry.position(h,63));
        assertEquals(UnwakingAssaultGeometry.position(h,36),UnwakingAssaultGeometry.position(h,80));
        assertEquals(-1,h.contact(60));
        assertTrue(h.intersects(80,box(Vec3.ZERO),box(Vec3.ZERO)));
        // A body worth looking at rather than the 1.25-block splinter it was when five flew at once.
        assertEquals(5,UnwakingAssaultGeometry.VERDICT_RADIUS);
        assertTrue(h.intersects(36,box(new Vec3(0,5,0)),box(new Vec3(0,5,0))));
        assertFalse(h.intersects(36,box(new Vec3(0,7,0)),box(new Vec3(0,7,0))));
    }
    @Test void everySkyBeatFitsThePassageAndTheFinaleNeverDoubles() {
        var beats=UnwakingAssaultController.SKY_BEATS;
        for(int i=0;i<beats.size();i++) {
            var beat=beats.get(i);
            assertTrue(beat.count()>=1,beat.kind()+" throws nothing");
            assertTrue(beat.at()+beat.kind().end<=UnwakingAssaultState.SKY_TICKS,
                    beat.kind()+" thrown at "+beat.at()+" outlives the passage");
            if(i>0) assertTrue(beats.get(i-1).at()<=beat.at(),"beats must be listed in schedule order");
        }
        // The rain is allowed to pile up on itself - that is the whole point of it - but each of
        // the three finale shapes is arena-scale, and reading two at once is a coin flip.
        for(int t=300;t<UnwakingAssaultState.SKY_TICKS;t++)
            assertTrue(live(t)<=1,"something is alive beside a finale shape at tick "+t);
    }
    @Test void theRainIsRelentlessButStillFitsOnTheWire() {
        int thrown=0, peak=0;
        for(var beat:UnwakingAssaultController.SKY_BEATS) thrown+=beat.count();
        for(int t=0;t<UnwakingAssaultState.SKY_TICKS;t++) peak=Math.max(peak,live(t));
        // The snapshot payload refuses more hazards than this and refusing one disconnects the
        // client, so the schedule has to fit the wire, not merely a frame budget.
        assertEquals(16,UnwakingDomainCombat.SNAPSHOT_LIMIT);
        assertTrue(peak<=UnwakingDomainCombat.SNAPSHOT_LIMIT,peak+" bodies will not fit a snapshot");
        assertEquals(14,peak);
        assertEquals(25,thrown);
    }
    @Test void aVolleyLeavesEveryTwelveTicksForElevenSeconds() {
        var rain=UnwakingAssaultController.SKY_BEATS.stream()
                .filter(b->b.kind()==FIRMAMENT_FRAGMENT&&b.variant()>=10).toList();
        assertEquals(UnwakingAssaultController.RAIN_VOLLEYS,rain.size());
        for(int i=1;i<rain.size();i++) assertEquals(UnwakingAssaultController.RAIN_PERIOD,
                rain.get(i).at()-rain.get(i-1).at(),"the rain must not stutter");
        // Twelve ticks against twenty-eight of telegraph: the body being dodged is never the last
        // one thrown, which is what makes the passage a dodge rather than a string of reactions.
        assertTrue(UnwakingAssaultController.RAIN_PERIOD<FIRMAMENT_FRAGMENT.impact/2);
        assertEquals(20,rain.stream().mapToInt(UnwakingAssaultController.Beat::count).sum());
    }
    @Test void consecutiveWideVolleysFillEachOthersGaps() {
        Vec3 axis=new Vec3(0,-.5,1).normalize();
        double radius=UnwakingSkyGeometry.SHARD_RADIUS;
        var even=offsets(0,3,axis); var odd=offsets(1,3,axis);
        for(int i=0;i<even.size()-1;i++) {
            Vec3 gap=even.get(i).add(even.get(i+1)).scale(.5);
            assertTrue(odd.stream().anyMatch(o->o.distanceTo(gap)<radius),
                    "the next volley leaves the gap at "+gap+" open, so standing still works twice");
        }
        // And a gap has to be room a player can actually stand in, or it is not a dodge at all.
        assertTrue(UnwakingAssaultGeometry.RAIN_SPREAD-2*radius>=4,"the gaps are under four blocks");
        // A single body has nothing to spread around: it aims dead at where you are going.
        assertEquals(Vec3.ZERO,UnwakingAssaultGeometry.rainOffset(3,0,1,axis));
    }
    @Test void everyTravellingBodyIsADodgeAndTheClockIsNot() {
        for(var kind:List.of(FIRMAMENT_FRAGMENT,RETURNING_VERDICT,VORTEX_BEAM,FIRMAMENT_GUILLOTINE,
                SIXFOLD_BURIAL,NULL_HORIZON))
            assertTrue(UnwakingAssaultGeometry.moving(kind),kind+" must route to the devour window");
        // The clock pins you where you stand, so it keeps the counter every player can answer.
        assertFalse(UnwakingAssaultGeometry.moving(CLOCK_STRIKE));
        // And nothing outside the domain passages was quietly swept in with them.
        for(var kind:UnwakingHazard.Kind.values())
            if(UnwakingAssaultGeometry.moving(kind)) assertTrue(kind.openCombat(),kind+" is not a domain attack");
    }
    private static int live(int tick) {
        int count=0;
        for(var beat:UnwakingAssaultController.SKY_BEATS)
            if(tick>=beat.at()&&tick<beat.at()+beat.kind().end) count+=beat.count();
        return count;
    }
    private static List<Vec3> offsets(int volley,int count,Vec3 axis) {
        return java.util.stream.IntStream.range(0,count)
                .mapToObj(i->UnwakingAssaultGeometry.rainOffset(volley,i,count,axis)).toList();
    }
    @Test void aimChecksAngleRatherThanDistanceOrInitialOrientation() {
        Vec3 eye=new Vec3(8000,120,4000), target=eye.add(0,0,12);
        assertTrue(UnwakingAssaultGeometry.aimed(eye,new Vec3(0,0,1),target));
        assertTrue(UnwakingAssaultGeometry.aimed(eye,UnwakingHazard.rotate(new Vec3(0,0,1),new Vec3(0,1,0),Math.toRadians(11.9)),target));
        assertFalse(UnwakingAssaultGeometry.aimed(eye,UnwakingHazard.rotate(new Vec3(0,0,1),new Vec3(0,1,0),Math.toRadians(12.1)),target));
        assertFalse(UnwakingAssaultGeometry.aimed(eye,Vec3.ZERO,target));
    }
    @Test void everyOrderCoversItsWholeRotationAndExpiresWithoutFurtherPackets() {
        for(int order:UnwakingAssaultState.orders()) {
            var rotation=UnwakingAssaultState.rotation(order);
            var s=new UnwakingAssaultState(order,100,Vec3.ZERO,new Vec3(0,0,1),Vec3.ZERO,1300);
            int end=100+rotation.ticks();
            assertFalse(s.active(99)); assertTrue(s.active(end-1)); assertFalse(s.active(end)); assertFalse(s.locked(end));
            var seen=java.util.stream.IntStream.range(100,end).mapToObj(s::passage)
                    .collect(java.util.stream.Collectors.toSet());
            // An order visits its own rotation entirely and never reaches into another one. That is
            // the whole point of packing both into the byte, and if the arithmetic slips the
            // failure is phase two quietly running a phase three passage.
            assertEquals(new java.util.HashSet<>(rotation.passages()),seen,"order "+order+" wandered");
            assertEquals(rotation.passages().get(order%UnwakingAssaultState.MAX_ROTATION_SIZE),s.passage(100));
        }
        assertEquals(400,UnwakingAssaultState.REWARD_TICKS);
    }
    @Test void theOrderByteCarriesBothRotationAndStartAndRefusesAnythingElse() {
        for(int order:UnwakingAssaultState.orders()) {
            var out=new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
            new UnwakingAssaultState(order,7,new Vec3(1,2,3),new Vec3(0,0,1),Vec3.ZERO,9).write(out);
            var back=UnwakingAssaultState.read(out);
            assertEquals(order,back.order());
            assertEquals(UnwakingAssaultState.rotation(order),UnwakingAssaultState.rotation(back.order()));
        }
        // One byte past the last order. The validator used to read "order>2" - a literal that would
        // have gone on refusing the enraged rotations after they existed.
        // Rotations of different lengths leave holes in the byte space - CALM has three passages in
        // a block of four - so the validator has to reject a start its own rotation does not have,
        // not merely one past the end. It used to read "order>2", a literal that would have gone on
        // refusing the enraged rotations after they existed.
        for(int order=0;order<UnwakingAssaultState.MAX_ROTATION_SIZE*4;order++) {
            if(UnwakingAssaultState.orders().contains(order)) { assertTrue(UnwakingAssaultState.valid(order)); continue; }
            assertFalse(UnwakingAssaultState.valid(order),order+" is not a real rotation and start");
            var bad=new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(),net.minecraft.core.RegistryAccess.EMPTY);
            bad.writeByte(order);
            assertThrows(IllegalArgumentException.class,()->UnwakingAssaultState.read(bad));
        }
        // And every passage is reachable by name, which is what the debug command relies on.
        for(var passage:UnwakingAssaultState.Passage.values())
            assertEquals(passage,UnwakingAssaultState.passage(UnwakingAssaultState.orderStartingAt(passage),0));
    }
    @Test void theTunnelIsTheOneThingInTheFightYouCanAffordToClip() {
        // Every other body is a removal: base damage times twenty is hundreds against a twenty
        // health player. The tunnel throws fifteen volleys and asks you to thread them, which is
        // only worth attempting if clipping one costs health rather than the run.
        assertTrue(TUNNEL_SHARD.grazing());
        assertTrue(TUNNEL_SHARD.damageFor(20)<20,"a graze must not be able to kill from full");
        assertEquals(4,TUNNEL_SHARD.damageFor(20),1e-4);
        for(var kind:UnwakingHazard.Kind.values()) {
            if(kind==TUNNEL_SHARD||kind==ARRIVAL||kind==REFUSAL) continue;
            assertFalse(kind.grazing(),kind+" must not be survivable; the fight is a no-hit fight");
            assertTrue(kind.damageFor(20)>20,kind+" stopped being a removal");
        }
        // A chip obstacle has no business winning the HUD banner off a guillotine.
        assertFalse(TUNNEL_SHARD.major());
        assertTrue(TUNNEL_SHARD.openCombat());
    }
    @Test void everyTunnelVolleyLeavesExactlyOneLaneAndTheLaneMoves() {
        Vec3 axis=new Vec3(0,0,1);
        Integer previous=null;
        for(int v=0;v<UnwakingAssaultGeometry.TUNNEL_VOLLEYS;v++) {
            int count=UnwakingAssaultGeometry.tunnelCount(v);
            var lanes=new java.util.HashSet<Integer>();
            for(int i=0;i<count;i++) {
                Vec3 offset=UnwakingAssaultGeometry.tunnelOffset(v,i,count,axis);
                lanes.add((int)Math.round(offset.x/UnwakingAssaultGeometry.TUNNEL_SPREAD*2));
                // The stagger is decoration; a gap a grounded player cannot reach is not a gap.
                assertTrue(Math.abs(offset.y)<=2,"volley "+v+" put an obstacle out of reach");
            }
            assertEquals(count,lanes.size(),"volley "+v+" stacked two obstacles in one lane");
            Vec3 gap=UnwakingAssaultGeometry.tunnelGap(v,count,axis);
            for(int i=0;i<count;i++) {
                Vec3 offset=UnwakingAssaultGeometry.tunnelOffset(v,i,count,axis);
                assertTrue(Math.abs(offset.x-gap.x)>UnwakingSkyGeometry.TUNNEL_RADIUS*2,
                        "volley "+v+" put an obstacle in its own open lane");
            }
            // Standing in the gap must not be the answer twice running.
            if(previous!=null) assertNotEquals(previous.intValue(),(int)Math.round(gap.x),
                    "volley "+v+" left the gap exactly where the last one did");
            previous=(int)Math.round(gap.x);
        }
        // The gap has to be wide enough to stand in once the obstacle radius is taken off it.
        assertTrue(UnwakingAssaultGeometry.TUNNEL_SPREAD-2*UnwakingSkyGeometry.TUNNEL_RADIUS>=1);
    }
    @Test void theClockFigureSettlesOnTheBearingBeforeEveryStrike() {
        Vec3 forward=new Vec3(0,0,1);
        for(boolean closing:new boolean[]{false,true}) {
            for(int n=0;n<UnwakingAssaultGeometry.CLOCK_STRIKES.length;n++) {
                int at=UnwakingAssaultGeometry.CLOCK_STRIKES[n];
                Vec3 struck=UnwakingAssaultGeometry.clockAim(at,closing,forward);
                // Still for the whole settle window, and pointing exactly where it fires: the figure
                // IS the telegraph, so one still drifting at the strike tick would be telling the
                // player to look somewhere the strike is not.
                for(int back=0;back<=UnwakingAssaultGeometry.CLOCK_SETTLE;back++) {
                    Vec3 held=UnwakingAssaultGeometry.clockAim(at-back,closing,forward);
                    assertEquals(0,held.distanceTo(struck),1e-9,
                            "strike "+n+" was still moving "+back+" ticks out");
                }
                assertEquals(1,struck.length(),1e-9);
            }
        }
        // Consecutive bearings are far enough apart that the walk is visible rather than a twitch.
        for(int i=1;i<UnwakingAssaultGeometry.CLOCK_YAW.length;i++) {
            double turn=Math.abs(Math.IEEEremainder(
                    UnwakingAssaultGeometry.CLOCK_YAW[i]-UnwakingAssaultGeometry.CLOCK_YAW[i-1],360));
            assertTrue(turn>60,"strikes "+(i-1)+" and "+i+" are only "+turn+" degrees apart");
        }
        // The strike leaves from exactly where the figure stands, or looking at it aims at nothing.
        assertEquals(12,UnwakingAssaultGeometry.CLOCK_ORBIT);
    }
    @Test void everyRotationFillsTheAssaultExactly() {
        // segment() walks the passages subtracting lengths and returns the last index when it runs
        // out, so a rotation that does not total the assault leaves passageAge running off the end
        // of its own passage - silently, and only in whichever rotation was short.
        for(var rotation:UnwakingAssaultState.Rotation.values()) {
            assertTrue(rotation.size()<=UnwakingAssaultState.MAX_ROTATION_SIZE,
                    rotation+" has more passages than the order byte is packed for");
            assertEquals(rotation.passages().size(),java.util.Set.copyOf(rotation.passages()).size(),
                    rotation+" repeats a passage");
            // Rotations no longer have to be the same length, but each still has to fill its own
            // assault exactly or segment() walks off the end of its last passage.
            assertEquals(rotation.ticks(),
                    rotation.passages().stream().mapToInt(UnwakingAssaultState.Passage::ticks).sum());
        }
        assertEquals(UnwakingAssaultState.ASSAULT_TICKS,UnwakingAssaultState.Rotation.CALM.ticks(),
                "phase two must be exactly as long as it always was");
        // And no passage belongs to two rotations, or a debug order would preview the wrong one.
        assertEquals(UnwakingAssaultState.Passage.values().length,
                java.util.Arrays.stream(UnwakingAssaultState.Rotation.values())
                        .flatMap(r->r.passages().stream()).distinct().count());
    }
    @Test void everyEnragedPassageFitsItsLengthAndStaysUnderTheWire() {
        for(var passage:UnwakingAssaultState.Rotation.ENRAGED.passages()) {
            var beats=UnwakingAssaultController.beats(passage);
            assertFalse(beats.isEmpty(),passage+" has no schedule, so it would attack nobody");
            int peak=0;
            for(int t=0;t<passage.ticks();t++) {
                int live=0;
                for(var beat:beats) if(t>=beat.at()&&t<beat.at()+beat.kind().end) live+=beat.count();
                peak=Math.max(peak,live);
            }
            for(var beat:beats) {
                assertTrue(beat.count()>=1,passage+" throws nothing at "+beat.at());
                assertTrue(beat.at()+beat.kind().end<=passage.ticks(),
                        passage+" throws a "+beat.kind()+" at "+beat.at()+" that outlives the passage");
                // Nothing new: every body these passages throw is one the fight already had, so
                // none of them needed a wire constant, a devour-routing entry or a HUD name.
                assertTrue(UnwakingAssaultGeometry.moving(beat.kind()),beat.kind()+" is not a dodge");
            }
            // The snapshot payload refuses more than this, and refusing one disconnects the client.
            assertTrue(peak<=UnwakingDomainCombat.SNAPSHOT_LIMIT,passage+" peaks at "+peak+" bodies");
        }
    }
    @Test void theArenaScaleShapesNeverOverlapEachOther() {
        // Reading two whole-arena shapes at once is a coin flip, so every passage that has them
        // spaces them - the rule the Sky passage already held, applied to each schedule at once.
        for(var passage:UnwakingAssaultState.Passage.values()) {
            var wide=UnwakingAssaultController.beats(passage).stream()
                    .filter(b->b.kind()==FIRMAMENT_GUILLOTINE||b.kind()==SIXFOLD_BURIAL||b.kind()==NULL_HORIZON)
                    .toList();
            for(int i=1;i<wide.size();i++)
                assertTrue(wide.get(i-1).at()+wide.get(i-1).kind().end<=wide.get(i).at(),
                        passage+": "+wide.get(i-1).kind()+" is alive when "+wide.get(i).kind()+" starts");
        }
    }
    @Test void everyEyeIsReachableAndAVolleyNeverFiresOneTwice() {
        var fired=new java.util.HashSet<Integer>();
        for(int beat=0;beat<UnwakingAssaultGeometry.EYE_BEATS.length;beat++) {
            int count=UnwakingAssaultGeometry.EYE_BEATS[beat][1];
            var volley=new java.util.HashSet<Integer>();
            for(int slot=0;slot<count;slot++) volley.add(UnwakingAssaultGeometry.eyeFor(beat,slot,count));
            // Two beams from one eye is one beam the player paid for twice.
            assertEquals(count,volley.size(),"beat "+beat+" fires one eye twice");
            fired.addAll(volley);
        }
        // If a third of the ring never fires, the sky holds decoration rather than threats.
        assertTrue(fired.size()>=UnwakingAssaultGeometry.EYE_COUNT-2,"only "+fired.size()+" eyes ever fire");
        for(int i=0;i<UnwakingAssaultGeometry.EYE_COUNT;i++) {
            Vec3 bearing=UnwakingAssaultGeometry.eyeBearing(i,new Vec3(0,0,1));
            assertEquals(1,bearing.length(),1e-9);
            // Every eye is above the horizon: this is a sky to read, not a firing line.
            assertTrue(bearing.y>0.1,"eye "+i+" sits at or below the horizon");
        }
    }
    @Test void everyMirrorVolleyComesFromADifferentReflection() {
        var seen=new java.util.HashSet<Integer>();
        for(int v=0;v<UnwakingAssaultGeometry.MIRROR_VOLLEYS;v++) {
            int cell=UnwakingAssaultGeometry.mirrorCell(v);
            if(v>0) assertNotEquals(UnwakingAssaultGeometry.mirrorCell(v-1),cell,
                    "volley "+v+" lights the same reflection twice running");
            seen.add(cell);
            Vec3 offset=UnwakingAssaultGeometry.mirrorOffset(cell);
            assertEquals(UnwakingAssaultGeometry.MIRROR_SPAN,offset.length(),1e-9);
            assertEquals(0,offset.y,1e-9,"a lit reflection has to be one the player sees without looking up");
        }
        assertEquals(UnwakingAssaultGeometry.MIRROR_CELLS,seen.size(),"the ring is not walked evenly");
        // The tell has to be long enough to answer: the body it announces telegraphs for this long.
        assertTrue(UnwakingAssaultGeometry.MIRROR_TELL>=FIRMAMENT_FRAGMENT.impact);
    }
    @Test void locksAreExactlyEightyTicksAndNeverIncludeTheMovementVolley() {
        int locked=0;
        for(int age=0;age<300;age++) if(UnwakingAssaultState.clockLocked(age)) locked++;
        assertEquals(160,locked);
        assertFalse(UnwakingAssaultState.clockLocked(39)); assertTrue(UnwakingAssaultState.clockLocked(40));
        assertTrue(UnwakingAssaultState.clockLocked(119)); assertFalse(UnwakingAssaultState.clockLocked(120));
        assertTrue(UnwakingAssaultState.clockLocked(239)); assertFalse(UnwakingAssaultState.clockLocked(240));
    }
    private static UnwakingHazard beam(Vec3 offset) {
        return new UnwakingHazard(4,VORTEX_BEAM,0,offset.add(0,96,0),new Vec3(0,-1,0),160,0,
                List.of(offset.add(0,-64,0),offset),null);
    }
    @Test void showerWarningsAndFadesAreHarmlessButWholeBodiesTouchTheBeam() {
        var h=beam(Vec3.ZERO);
        assertFalse(h.intersects(23,box(Vec3.ZERO),box(Vec3.ZERO)));
        assertTrue(h.intersects(24,box(new Vec3(2.9,0,0)),box(new Vec3(2.9,0,0))));
        assertFalse(h.intersects(24,box(new Vec3(3.2,0,0)),box(new Vec3(3.2,0,0))));
        assertTrue(h.intersects(31,box(Vec3.ZERO),box(Vec3.ZERO)));
        assertFalse(h.intersects(32,box(Vec3.ZERO),box(Vec3.ZERO)));
        assertTrue(h.recovering(32));
        assertFalse(h.intersects(24,box(new Vec3(0,110,0)),box(new Vec3(0,110,0))));
    }
    @Test void crossingAnActiveBeamCannotTunnelEvenFarFromSpawn() {
        Vec3 offset=new Vec3(120000,180,-180000); var h=beam(offset);
        assertTrue(h.intersects(28,box(offset.add(-8,0,0)),box(offset.add(8,0,0))));
        assertFalse(h.intersects(28,box(offset.add(-8,0,5)),box(offset.add(8,0,5))));
        assertEquals(h.origin(),h.source(28,offset));
    }
    @Test void nineWavesFitTheSnapshotBudgetAndFinishBeforeTheClock() {
        int waves=0,peak=0;
        for(int tick=0;tick<300;tick++) {
            if(UnwakingAssaultGeometry.showerWave(tick)) waves++;
            int live=0;
            for(int start=0;start<=tick;start++) if(UnwakingAssaultGeometry.showerWave(start)&&tick-start<=VORTEX_BEAM.end) live+=7;
            peak=Math.max(peak,live);
            if(tick>=273) assertEquals(0,live);
        }
        // Nine waves rather than seven: the same window, a tighter period, and still never more
        // than two waves in the air, so the beams got denser without costing a byte on the wire.
        assertEquals(9,waves); assertEquals(14,peak); assertTrue(peak<=UnwakingDomainCombat.SNAPSHOT_LIMIT);
    }
    @Test void showerGapsRemainOpenInTheTighterFinalPattern() {
        for(boolean closing:new boolean[]{false,true}) for(int wave=0;wave<7;wave++) {
            int index=wave;
            var offsets=java.util.stream.IntStream.range(0,7).mapToObj(lane->UnwakingAssaultGeometry.showerOffset(index,lane,closing)).toList();
            assertEquals(Vec3.ZERO,offsets.getFirst());
            for(int i=0;i<7;i++) for(int j=i+1;j<7;j++) assertTrue(offsets.get(i).distanceTo(offsets.get(j))>2*UnwakingAssaultGeometry.BEAM_RADIUS+1.8);
        }
    }
    @Test void woundUsesCommandProportionsAndFullyClearsAmbientShapes() {
        Vec3 anchor=new Vec3(4000,140,8000),forward=new Vec3(0,0,1);
        var state=new UnwakingAssaultState(0,0,anchor,forward,Vec3.ZERO,0);
        var frame=state.woundFrame();
        assertEquals(.16/.26,(frame.center().y-anchor.y)/96,1e-9);
        assertEquals(.75/.26,frame.right().length()/96,1e-9);
        assertEquals(.80/.26,frame.up().length()/96,1e-9);
        assertEquals(0,frame.right().dot(frame.up()),1e-9);
        assertEquals(0,state.scenery(40)); assertEquals(0,state.scenery(250));
        assertTrue(state.scenery(599)>0); assertEquals(.15F,state.scenery(640));
        assertFalse(state.locked(340));
    }
    @Test void showerRetainsApexDamageAndVolleysKeepOneContactGroup() {
        assertEquals(48,VORTEX_BEAM.baseDamageFor(20)); assertEquals(140,VORTEX_BEAM.baseDamageFor(400));
        assertEquals(4,VORTEX_BEAM.powerTier()); assertEquals(UnwakingHazard.Defense.PARRY,VORTEX_BEAM.defense);
        assertTrue(UnwakingAssaultGeometry.volley(VORTEX_BEAM));
        assertFalse(UnwakingAssaultGeometry.projectile(VORTEX_BEAM));
        assertEquals(100,RETURNING_VERDICT.baseDamageFor(400));
        assertEquals(140,CLOCK_STRIKE.baseDamageFor(400));
        assertEquals(48,FIRMAMENT_FRAGMENT.baseDamageFor(20)); assertEquals(140,FIRMAMENT_FRAGMENT.baseDamageFor(400));
    }
}
