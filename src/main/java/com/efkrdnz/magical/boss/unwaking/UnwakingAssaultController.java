package com.efkrdnz.magical.boss.unwaking;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import static com.efkrdnz.magical.boss.unwaking.UnwakingAssaultState.*;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.*;

/** Recurring 60-second defensive passages with a guaranteed, attack-free 20-second reward. */
final class UnwakingAssaultController {
    /** One thrown volley: when it leaves, what it is, how big each body is, and how many. */
    record Beat(int at, Kind kind, int variant, int count) {}
    /**
     * The rain. A volley every twelve ticks for eleven seconds, every third one three bodies wide.
     *
     * <p>Twelve ticks is well inside a body's twenty-eight ticks of telegraph, so five or six
     * warnings are readable at once and the one being dodged is never the last one thrown. The
     * singles aim dead at where the player is about to be, which punishes moving in a straight
     * line; the wide ones leave gaps, and {@link UnwakingAssaultGeometry#rainOffset} shifts the
     * next wide one half a spacing so the gap just taken is the next thing filled.
     *
     * <p>{@link #RAIN_VOLLEYS} is set by the passage rather than by taste: the last volley leaves
     * at 220 and a body lives seventy-six ticks, which lands exactly on the guillotine at 300.
     */
    static final int RAIN_START = 88, RAIN_PERIOD = 12, RAIN_VOLLEYS = 12;
    /**
     * The Sky passage, in order. Kept as data rather than a ladder of tick literals because the
     * things that must hold about it - nothing outliving the passage, the arena-scale finale shapes
     * never overlapping, and never more bodies at once than one snapshot can carry - are arithmetic
     * properties of this list that no reader can eyeball out of the loop below.
     */
    static final List<Beat> SKY_BEATS = skyBeats();

    private static List<Beat> skyBeats() {
        List<Beat> beats=new ArrayList<>();
        // One large body first, alone, so the passage says what is falling before it buries anyone.
        beats.add(new Beat(40,Kind.FIRMAMENT_FRAGMENT,0,1));
        for(int i=0;i<RAIN_VOLLEYS;i++)
            beats.add(new Beat(RAIN_START+i*RAIN_PERIOD,Kind.FIRMAMENT_FRAGMENT,10,i%3==2?3:1));
        beats.add(new Beat(150,Kind.RETURNING_VERDICT,0,1));
        beats.add(new Beat(300,Kind.FIRMAMENT_GUILLOTINE,0,1));
        beats.add(new Beat(390,Kind.SIXFOLD_BURIAL,0,1));
        beats.add(new Beat(490,Kind.NULL_HORIZON,0,1));
        beats.sort(java.util.Comparator.comparingInt(Beat::at));
        return List.copyOf(beats);
    }


    /**
     * THE EYES. Lidless eyes open across the sky and fire from every bearing at once.
     *
     * <p>Escalating width rather than escalating speed: one eye, then one from behind you, then
     * pairs, then threes. Each beam is the vortex passage's beam, unchanged and lead-aimed, so it is
     * individually as dodgeable as it has always been. What is new is that there is no direction to
     * face - the passage is a reading test, not a reaction test - and the finale opens every eye at
     * once behind the converging suns.
     *
     * <p>{@code variant} is the volley index, which is what {@link UnwakingAssaultGeometry#eyeFor}
     * turns into bearings; the client derives the same bearings from the same function, so nothing
     * about an eye is sent.
     */
    static final List<Beat> EYE_BEATS = beats(b -> {
        int[][] table=UnwakingAssaultGeometry.EYE_BEATS;
        for(int i=0;i<table.length;i++) b.add(new Beat(table[i][0],Kind.VORTEX_BEAM,i,table[i][1]));
        b.add(new Beat(UnwakingAssaultGeometry.EYE_BURIAL,Kind.SIXFOLD_BURIAL,0,1));
    });
    /**
     * THE MIRROR. The domain becomes a hall of reflections and one of them is the one throwing.
     *
     * <p>A volley every twenty-two ticks from a different cell of the lattice each time. The copies
     * are the difficulty and the telegraph at once: the lit reflection is honest, on the fragment's
     * usual twenty-eight tick clock, but you have to find it among its duplicates first. Every third
     * volley is two bodies, which is what stops the answer being "watch one bearing".
     */
    static final List<Beat> MIRROR_BEATS = beats(b -> {
        for(int i=0;i<UnwakingAssaultGeometry.MIRROR_VOLLEYS;i++)
            b.add(new Beat(UnwakingAssaultGeometry.MIRROR_START+i*UnwakingAssaultGeometry.MIRROR_PERIOD,
                    Kind.FIRMAMENT_FRAGMENT,10+i,i%3==2?2:1));
    });
    /**
     * THE GIANT. The boss stands at the edge of the world, as tall as it, and gestures.
     *
     * <p>Six hundred ticks, so it carries the passage structure the Sky passage already proved: a
     * long rain of flicked fragments, then three arena-scale shapes that never overlap each other -
     * the sweeping hand, the opened palm, the closing fists. Each gesture is the telegraph for the
     * shape that follows it, which is the only reason to draw a figure that big.
     */
    static final List<Beat> GIANT_BEATS = beats(b -> {
        for(int i=0;i<UnwakingAssaultGeometry.FLICK_VOLLEYS;i++)
            b.add(new Beat(UnwakingAssaultGeometry.FLICK_START+i*UnwakingAssaultGeometry.FLICK_PERIOD,
                    Kind.FIRMAMENT_FRAGMENT,10,i%3==2?3:1));
        for(int at:UnwakingAssaultGeometry.GIANT_LATE_FLICKS) b.add(new Beat(at,Kind.FIRMAMENT_FRAGMENT,10,1));
        b.add(new Beat(UnwakingAssaultGeometry.GIANT_SWEEP,Kind.FIRMAMENT_GUILLOTINE,0,1));
        b.add(new Beat(UnwakingAssaultGeometry.GIANT_PALM,Kind.SIXFOLD_BURIAL,0,1));
        b.add(new Beat(UnwakingAssaultGeometry.GIANT_FISTS,Kind.NULL_HORIZON,0,1));
    });
    /** Build a schedule in whatever order reads best, then put it back in tick order. */
    private static List<Beat> beats(java.util.function.Consumer<List<Beat>> fill) {
        List<Beat> beats=new ArrayList<>(); fill.accept(beats);
        beats.sort(java.util.Comparator.comparingInt(Beat::at));
        return List.copyOf(beats);
    }
    /**
     * THE TUNNEL. The world becomes a shaft of colour and the shaft comes to the player.
     *
     * <p>Fifteen volleys in eleven seconds - by some way the densest thing the boss does - and the
     * only passage where clipping a body costs health instead of the run. That exchange is the whole
     * design: the obstacles are small, they are relentless, and they leave exactly one lane open, so
     * the passage is a thread-the-needle rather than another set of one-shot telegraphs.
     */
    static final List<Beat> TUNNEL_BEATS = beats(b -> {
        for(int i=0;i<UnwakingAssaultGeometry.TUNNEL_VOLLEYS;i++)
            b.add(new Beat(UnwakingAssaultGeometry.TUNNEL_START+i*UnwakingAssaultGeometry.TUNNEL_PERIOD,
                    Kind.TUNNEL_SHARD,i,UnwakingAssaultGeometry.tunnelCount(i)));
    });
    /** Every schedule by passage, so a test can walk them all rather than name them one at a time. */
    static List<Beat> beats(Passage passage) {
        return switch(passage) {
            case SKY -> SKY_BEATS;
            case EYES -> EYE_BEATS;
            case MIRROR -> MIRROR_BEATS;
            case GIANT -> GIANT_BEATS;
            case TUNNEL -> TUNNEL_BEATS;
            case VORTEX, CLOCK -> List.of();
        };
    }

    private final Map<UUID,UnwakingAssaultState> states=new HashMap<>();
    private final Map<Long,Long> volleys=new HashMap<>();
    private long started=-1, rewardUntil;
    private int order, nextOrder, passage=-1;
    int completed, aimFailures;

    boolean active() { return started>=0; }
    boolean rewarding(long now) { return now<rewardUntil; }
    UnwakingAssaultState state(UUID id) { return states.getOrDefault(id,NONE); }
    boolean locked(UUID id,long now) { return state(id).locked(now); }
    long group(long attack) { return volleys.getOrDefault(attack,attack); }
    /**
     * The HUD caption and the {@code /unwaking debug assault <name>} argument, one per order.
     *
     * <p>All six are addressable: three calm, three enraged. The name is the rotation's opening
     * passage, which is what the player is about to be standing in.
     */
    String name() {
        return switch(UnwakingAssaultState.passage(order,0)) {
            case SKY->"heaven_unwritten"; case VORTEX->"world_refuses"; case CLOCK->"no_next_moment";
            case EYES->"eyes_unlidded"; case MIRROR->"mirrored_world"; case TUNNEL->"the_long_fall";
            case GIANT->"one_that_fills_the_sky";
        };
    }
    void begin(UnwakingEncounterService service, int selectedOrder) {
        stop(service); service.current().combat.clear();
        // An explicit order picks itself - that is what the debug command is for. Left to itself
        // the boss cycles the starting passage and takes the rotation from how angry it is, so
        // phase three never repeats phase two's three passages.
        Rotation rotation=service.current().phase.enraged()?Rotation.ENRAGED:Rotation.CALM;
        order=UnwakingAssaultState.valid(selectedOrder)?selectedOrder
                : rotation.ordinal()*MAX_ROTATION_SIZE+Math.floorMod(nextOrder++,rotation.size());
        started=service.now(); passage=-1;
        service.current().body.setInvisible(true); service.current().body.domainBody(true);
        service.current().recoveryUntil=0;
        tick(service); service.sendAll();
    }
    void stop(UnwakingEncounterService service) {
        for(UUID id:List.copyOf(states.keySet())) release(service,id);
        states.clear(); volleys.clear(); started=-1; rewardUntil=0; passage=-1;
        if(service.current()!=null) service.current().body.domainBody(false);
    }
    void remove(UnwakingEncounterService service,UUID id) { release(service,id); states.remove(id); }
    private void release(UnwakingEncounterService service,UUID id) {
        ServerPlayer p=service.player(id); var state=states.get(id);
        if(p!=null&&state!=null&&state.locked(service.now())) {
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked=true; p.fallDistance=0;
        }
    }
    void tick(UnwakingEncounterService service) {
        if(!active()) return;
        long now=service.now(); int age=(int)(now-started);
        if(age>=UnwakingAssaultState.rotation(order).ticks()) { reward(service); return; }
        var run=service.current();
        if(UnwakingAssaultState.segment(order,age)!=passage) {
            for(UUID id:List.copyOf(states.keySet())) release(service,id);
            states.clear(); volleys.clear(); run.combat.clear(); passage=UnwakingAssaultState.segment(order,age);
            for(UUID id:run.players.keySet()) {
                ServerPlayer p=service.player(id); if(p==null) continue;
                Vec3 forward=unit(new Vec3(p.getLookAngle().x,0,p.getLookAngle().z));
                states.put(id,new UnwakingAssaultState(order,started,p.position(),forward,Vec3.ZERO,0));
            }
            service.sound(SoundEvents.AMETHYST_CLUSTER_BREAK,.5F); service.sendAll();
        }
        for(UUID id:List.copyOf(run.players.keySet())) {
            ServerPlayer p=service.player(id); var state=states.get(id); if(p==null||state==null) continue;
            int t=state.passageAge(now);
            switch(state.passage(now)) {
                case SKY -> {
                    // One idea, escalating: pieces of the cut sky fall on you. The first is alone
                    // and large; the Procession is three smaller bodies down one line, single file,
                    // forty-five ticks apart - far enough outside the three-to-sixteen tick prompt
                    // window that each one actually offers its own parry, where four volleys of
                    // five shared a hit group and bought four parries between twenty objects.
                    for(var beat:SKY_BEATS) if(t==beat.at()) {
                        switch(beat.kind()) {
                            case FIRMAMENT_FRAGMENT -> fragment(service,p,state,beat,(t-RAIN_START)/RAIN_PERIOD);
                            case RETURNING_VERDICT -> verdict(service,p,state);
                            default -> skyFinale(service,p,state,t);
                        }
                    }
                }
                case VORTEX -> {
                    if(UnwakingAssaultGeometry.showerWave(t)) shower(service,p,state,(t-40)/32);
                }
                case CLOCK -> {
                    if(t==40||t==160) {
                        Vec3 anchor=p.position();
                        state=new UnwakingAssaultState(order,started,state.anchor(),state.forward(),anchor,now+80);
                        states.put(id,state); p.stopRiding(); p.setDeltaMovement(Vec3.ZERO); p.hurtMarked=true;
                        service.sound(SoundEvents.BELL_BLOCK,.55F); service.sendAll();
                    }
                    if(state.locked(now)) pin(p,state);
                    if(t==120||t==240) { p.setDeltaMovement(Vec3.ZERO); p.hurtMarked=true; service.sendAll(); }
                    if(t==40||t==64||t==88||t==160||t==184||t==208) clockStrike(service,p,state,t);
                    // Sixteen ticks before the pin releases, so the bodies are already in the air
                    // while the player is still held and free again well before their contact
                    // window opens. Also the last tick a seventy-six tick body fits in a
                    // three-hundred tick passage: the boundary clears every hazard outright.
                    if(t==224) fragment(service,p,state,new Beat(t,Kind.FIRMAMENT_FRAGMENT,10,3),0);
                }
                case EYES -> {
                    for(var beat:EYE_BEATS) if(t==beat.at()) {
                        if(beat.kind()==Kind.SIXFOLD_BURIAL) burial(service,p,state);
                        else eyeVolley(service,p,state,beat);
                    }
                }
                case MIRROR -> {
                    for(var beat:MIRROR_BEATS) if(t==beat.at())
                        fragment(service,p,state,beat,beat.variant()-10,mirrorSource(p,beat.variant()-10));
                }
                case GIANT -> {
                    for(var beat:GIANT_BEATS) if(t==beat.at()) {
                        switch(beat.kind()) {
                            case FIRMAMENT_FRAGMENT -> fragment(service,p,state,beat,
                                    (t-UnwakingAssaultGeometry.FLICK_START)/UnwakingAssaultGeometry.FLICK_PERIOD,
                                    state.giant());
                            case SIXFOLD_BURIAL -> burial(service,p,state);
                            default -> giantShape(service,p,state,beat.kind());
                        }
                    }
                }
                case TUNNEL -> {
                    for(var beat:TUNNEL_BEATS) if(t==beat.at()) tunnel(service,p,state,beat);
                }
                // Statement switches over an enum accept a missing constant silently, and a passage
                // with no case here is three hundred ticks in which the boss attacks nobody. Fail
                // loudly instead: a new passage that forgot its schedule is a bug, not a lull.
                default -> throw new IllegalStateException("Assault passage has no attacks: "+state.passage(now));
            }
        }
    }
    void pin(ServerPlayer p,UnwakingAssaultState state) {
        p.setPos(state.lockAnchor()); p.setDeltaMovement(Vec3.ZERO); p.fallDistance=0; p.hurtMarked=true;
    }
    /**
     * A volley of the wound falling on one player.
     *
     * <p>{@code beat.variant()} is the body size: ten and above is the five-block shard the rain
     * throws, below it the eight-block opening fragment. It rides in the variant because a hazard
     * has no size field; {@link UnwakingSkyGeometry#fragmentRadius} is the other half of that.
     *
     * <p>Every body in one volley shares a hit group, so clipping any of them is one attack landing
     * rather than three. They also leave the wound already spread, at a fraction of their arrival
     * spacing, so a volley reads as a widening fan instead of three things appearing out of a
     * single point.
     */
    private void fragment(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,Beat beat,int volley) {
        fragment(service,p,state,beat,volley,state.wound());
    }
    /** The same volley, thrown from somewhere other than the wound: a reflection, or a vast hand. */
    private void fragment(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,Beat beat,int volley,Vec3 wound) {
        var combat=service.current().combat;
        Vec3 lead=p.getDeltaMovement().scale(8); if(lead.length()>12) lead=lead.normalize().scale(12);
        Vec3 target=p.getBoundingBox().getCenter().add(lead);
        Vec3 approach=unit(target.subtract(wound));
        long group=-1;
        for(int i=0;i<beat.count();i++) {
            Vec3 offset=UnwakingAssaultGeometry.rainOffset(volley,i,beat.count(),approach);
            Vec3 origin=wound.add(offset.scale(.35)), aim=target.add(offset);
            Vec3 axis=unit(aim.subtract(origin)), end=aim.add(axis.scale(24));
            long id=combat.nextAttack(); if(group<0) group=id;
            combat.emit(new UnwakingHazard(id,Kind.FIRMAMENT_FRAGMENT,service.now(),origin,axis,
                    origin.distanceTo(end),beat.variant(),List.of(end),p.getUUID()));
            volleys.put(id,group);
        }
        // Only the wide volleys announce themselves. At one every twelve ticks a charge cue per
        // body is a continuous tone; the per-volley impact cue already carries the rhythm.
        if(beat.count()>1||beat.variant()<10)
            p.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE,net.minecraft.sounds.SoundSource.HOSTILE,.65F,beat.variant()>=10?.75F:.5F);
        service.sendAll();
    }
    /**
     * The Procession's middle beat: one body that overshoots, hangs for twelve ticks, and comes
     * back down the line it arrived on. Twenty-four blocks of overshoot is enough for a five-block
     * body to fully clear the player before it stops, so the return leg is a second real threat
     * rather than a continuation of the first.
     */
    private void verdict(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state) {
        var combat=service.current().combat;
        Vec3 lead=p.getDeltaMovement().scale(10); if(lead.length()>12) lead=lead.normalize().scale(12);
        Vec3 target=p.getBoundingBox().getCenter().add(lead), origin=state.wound();
        Vec3 axis=unit(target.subtract(origin)), end=target.add(axis.scale(24));
        combat.emit(new UnwakingHazard(combat.nextAttack(),Kind.RETURNING_VERDICT,service.now(),origin,axis,
                origin.distanceTo(end),0,List.of(end),p.getUUID()));
        p.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE,net.minecraft.sounds.SoundSource.HOSTILE,.65F,.65F);
        service.sendAll();
    }
    /**
     * A strike from wherever the figure came to rest.
     *
     * <p>The direction is no longer three fixed yaws off the passage axis - a bearing the player had
     * no way to find without already knowing it. It is {@link UnwakingAssaultGeometry#clockAim},
     * which the renderer draws a figure walking along and settling on ten ticks before this fires,
     * twelve blocks out: exactly the point {@code offerAimed} then asks the player to look at.
     */
    private void clockStrike(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,int t) {
        int index=UnwakingAssaultGeometry.clockStrikeIndex(t);
        Vec3 direction=UnwakingAssaultGeometry.clockAim(t,service.current().phase==UnwakingPhase.FINAL,state.forward());
        service.current().combat.emit(new UnwakingHazard(service.current().combat.nextAttack(),Kind.CLOCK_STRIKE,service.now(),
                p.getEyePosition().add(direction.scale(UnwakingAssaultGeometry.CLOCK_ORBIT)),direction,
                UnwakingAssaultGeometry.CLOCK_ORBIT,index,List.of(),p.getUUID()));
        service.sendAll();
    }
    /**
     * One volley of the tunnel: obstacles down the shaft, with one lane left open.
     *
     * <p>They share a hit group, so threading three and clipping the fourth is one contact rather
     * than four - which matters far more here than anywhere else, because this is the one passage a
     * player is expected to be clipped by.
     */
    private void tunnel(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,Beat beat) {
        var combat=service.current().combat;
        Vec3 axis=unit(state.forward()), here=p.getBoundingBox().getCenter();
        long group=-1;
        for(int i=0;i<beat.count();i++) {
            Vec3 offset=UnwakingAssaultGeometry.tunnelOffset(beat.variant(),i,beat.count(),axis);
            // Straight down the shaft, not aimed: the tunnel does not chase, it simply arrives.
            Vec3 end=here.add(offset).subtract(axis.scale(16));
            Vec3 origin=here.add(offset).add(axis.scale(UnwakingAssaultGeometry.TUNNEL_REACH));
            long id=combat.nextAttack(); if(group<0) group=id;
            combat.emit(new UnwakingHazard(id,Kind.TUNNEL_SHARD,service.now(),origin,unit(end.subtract(origin)),
                    origin.distanceTo(end),beat.variant(),List.of(end),p.getUUID()));
            volleys.put(id,group);
        }
        p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.HOSTILE,.5F,
                1.4F-beat.variant()*.03F);
        service.sendAll();
    }
    private void shower(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,int wave) {
        var combat=service.current().combat;
        Vec3 lead=p.getDeltaMovement().scale(8); if(lead.length()>12) lead=lead.normalize().scale(12);
        Vec3 target=p.getBoundingBox().getCenter().add(lead);
        long group=-1;
        for(int lane=0;lane<7;lane++) {
            // Lock every path on announcement. The vortex changes targets, never player movement.
            Vec3 offset=UnwakingAssaultGeometry.showerOffset(wave,lane,service.current().phase==UnwakingPhase.FINAL);
            Vec3 origin=state.vortex().add(offset.scale(.65)), center=target.add(offset);
            Vec3 axis=unit(center.subtract(origin)), end=center.add(axis.scale(64));
            long id=combat.nextAttack(); if(group<0) group=id;
            combat.emit(new UnwakingHazard(id,Kind.VORTEX_BEAM,service.now(),origin,axis,origin.distanceTo(end),lane,List.of(end,center),p.getUUID()));
            volleys.put(id,group);
        }
        p.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE,net.minecraft.sounds.SoundSource.HOSTILE,.65F,.85F);
        service.sendAll();
    }
    private void skyFinale(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,int t) {
        if(t==390) burial(service,p,state);
        else sweep(service,p,state,t==490?Kind.NULL_HORIZON:Kind.FIRMAMENT_GUILLOTINE,state.wound());
    }
    /**
     * The converging suns, unchanged. Six bodies closing on the player from the cardinals at once.
     *
     * <p>Every passage that wants an arena-scale finale reaches for this one rather than inventing
     * another, because it is the attack that already reads: it warns with a ring at the body true
     * radius instead of the same shape dimmer, and its surface carries a luminance ramp across it,
     * so it arrives as a lit solid rather than a flat silhouette.
     */
    private void burial(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state) {
        var combat=service.current().combat;
        Vec3 lead=p.getDeltaMovement().scale(8); if(lead.length()>12) lead=lead.normalize().scale(12);
        Vec3 target=p.getBoundingBox().getCenter().add(lead);
        long group=-1;
        for(int i=0;i<CARDINALS.size();i++) {
            Vec3 outward=CARDINALS.get(i);
            if(service.current().phase==UnwakingPhase.FINAL) outward=rotate(outward,state.forward(),Math.toRadians(35));
            Vec3 origin=target.add(outward.scale(80)), end=target.subtract(outward.scale(24));
            long id=combat.nextAttack(); if(group<0) group=id;
            combat.emit(new UnwakingHazard(id,Kind.SIXFOLD_BURIAL,service.now(),origin,unit(end.subtract(origin)),104,i,List.of(end),p.getUUID()));
            volleys.put(id,group);
        }
        p.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE,net.minecraft.sounds.SoundSource.HOSTILE,.8F,.55F);
        service.sendAll();
    }
    /** An arena-scale shape leaving {@code source}: the cut that sweeps, or the ring that closes. */
    private void sweep(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,Kind kind,Vec3 source) {
        var combat=service.current().combat;
        Vec3 lead=p.getDeltaMovement().scale(8); if(lead.length()>12) lead=lead.normalize().scale(12);
        Vec3 target=p.getBoundingBox().getCenter().add(lead);
        Vec3 origin=source, axis=unit(target.subtract(origin));
        if(kind==Kind.NULL_HORIZON) {
            // Put the player on the approaching annulus, with a real central escape opening.
            axis=state.forward(); Vec3 right=unit(axis.cross(new Vec3(0,1,0)));
            origin=target.subtract(axis.scale(96)).subtract(right.scale(UnwakingSkyGeometry.RING_RADIUS));
        }
        Vec3 end=kind==Kind.NULL_HORIZON?origin.add(axis.scale(192)):target.add(axis.scale(64));
        combat.emit(new UnwakingHazard(combat.nextAttack(),kind,service.now(),origin,axis,origin.distanceTo(end),0,List.of(end),p.getUUID()));
        p.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE,net.minecraft.sounds.SoundSource.HOSTILE,.8F,.75F);
        service.sendAll();
    }
    /**
     * One beat of the Eyes: a beam per eye, from bearings spread across the ring.
     *
     * <p>The body is the vortex beam and nothing about it changes - same twelve-sided tube, same
     * capsule, same lead-aimed target locked on announcement, same Gluttony-only devour window.
     * Only where it starts is new, and that is the entire passage: a beam that cannot be answered
     * by facing it, because the next one is behind you.
     */
    private void eyeVolley(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,Beat beat) {
        var combat=service.current().combat;
        Vec3 lead=p.getDeltaMovement().scale(8); if(lead.length()>12) lead=lead.normalize().scale(12);
        Vec3 target=p.getBoundingBox().getCenter().add(lead);
        long group=-1;
        for(int slot=0;slot<beat.count();slot++) {
            int eye=UnwakingAssaultGeometry.eyeFor(beat.variant(),slot,beat.count());
            Vec3 origin=target.add(UnwakingAssaultGeometry.eyeBearing(eye,state.forward())
                    .scale(UnwakingAssaultGeometry.EYE_DISTANCE));
            Vec3 axis=unit(target.subtract(origin)), end=target.add(axis.scale(48));
            long id=combat.nextAttack(); if(group<0) group=id;
            combat.emit(new UnwakingHazard(id,Kind.VORTEX_BEAM,service.now(),origin,axis,
                    origin.distanceTo(end),eye,List.of(end,target),p.getUUID()));
            volleys.put(id,group);
        }
        p.playNotifySound(SoundEvents.WARDEN_SONIC_CHARGE,net.minecraft.sounds.SoundSource.HOSTILE,.65F,.95F);
        service.sendAll();
    }
    /** The lit reflection a Mirror volley is thrown from, on the lattice the renderer draws. */
    private static Vec3 mirrorSource(ServerPlayer p,int volley) {
        return p.getBoundingBox().getCenter()
                .add(UnwakingAssaultGeometry.mirrorOffset(UnwakingAssaultGeometry.mirrorCell(volley)));
    }
    /** A gesture landing: the sweeping hand and the closing fists both leave the figure itself. */
    private void giantShape(UnwakingEncounterService service,ServerPlayer p,UnwakingAssaultState state,Kind kind) {
        sweep(service,p,state,kind,state.giant());
    }
    private void reward(UnwakingEncounterService service) {
        var run=service.current(); run.combat.clear();
        for(UUID id:List.copyOf(states.keySet())) release(service,id);
        states.clear(); volleys.clear();
        // Put the body back first, then try to place it. These used to be one step, so a frame in
        // which UnwakingMovement found no landing spot returned early with the assault still
        // "active", the body still invisible and still domainBody(true) - which makes hurtBody
        // reject every hit - and then aborted the whole encounter a hundred ticks later. Landing
        // where it already stands is a worse-looking reward than landing next to someone; being
        // untouchable for five seconds and then cancelling the fight is not a reward at all.
        run.body.domainBody(false); run.body.setInvisible(false);
        Vec3 destination=null;
        List<UUID> ids=new ArrayList<>(run.players.keySet());
        for(int i=0;i<ids.size()&&destination==null;i++) {
            ServerPlayer p=service.player(ids.get(Math.floorMod(run.targetIndex+i,ids.size())));
            if(p!=null) destination=UnwakingMovement.destination(run.body,p,3);
        }
        if(destination!=null) UnwakingMovement.move(run.body,destination);
        started=-1; completed++; rewardUntil=service.now()+REWARD_TICKS; run.recoveryUntil=rewardUntil;
        run.combat.guard.reset(); run.combat.afterAssault();
        service.sound(SoundEvents.AMETHYST_CLUSTER_BREAK,.5F); service.sendAll();
    }
}
