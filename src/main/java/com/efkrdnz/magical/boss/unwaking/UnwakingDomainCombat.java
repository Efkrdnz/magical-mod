package com.efkrdnz.magical.boss.unwaking;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import static com.efkrdnz.magical.boss.unwaking.UnwakingHazard.Kind.*;

/** Open-space combination controller for both worlds. No attacks are centered on spawn. */
final class UnwakingDomainCombat {
    private record Pending(UnwakingHazard hazard, UUID player, long impact, int grace, Vec3 source) {}
    private final Map<Long,UnwakingHazard> hazards = new LinkedHashMap<>();
    private final Map<Long,Set<UUID>> hit = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private final UnwakingCounterWindows counters = new UnwakingCounterWindows();
    final UnwakingDivineGuard guard = new UnwakingDivineGuard();
    final UnwakingAssaultController assault = new UnwakingAssaultController();
    private int normalSinceAssault;
    private UnwakingCombinations.Score score;
    private long nextId, combination, started = -1, finisherId = -1, teleportAt;
    private int deckIndex;
    private UUID target;
    private Vec3 teleportDestination;
    private boolean finisherStruck, finisherEligible;
    int fractures, contacts, parries, completedCombinations, openings;
    float rawDamage, healthDamage;

    void clear() {
        counters.clear(); hazards.clear(); hit.clear(); pending.clear(); score=null; started=-1;
        teleportDestination=null; teleportAt=0; finisherId=-1; finisherStruck=false; finisherEligible=false;
    }
    /**
     * Reset for a new phase. Called from {@link UnwakingEncounterService#changePhase}, which has
     * already stopped any running assault and put the body back to visible and targetable.
     *
     * <p>{@code normalSinceAssault} starts at zero for every phase, FINAL included. It used to
     * start at two there - the threshold in {@link #tick} - to make phase three open with an
     * assault. It did: {@code assault.begin} sets the body invisible and {@code domainBody(true)},
     * so phase three opened with sixty seconds of a boss nobody could see or hit, which from the
     * floor is indistinguishable from one still stunned. Phase three now opens with the boss awake
     * and in reach, and its first assault arrives on the same two-combination clock as every other.
     */
    void enter(UnwakingPhase phase) { clear(); deckIndex=0; guard.reset(); normalSinceAssault=0; }
    long nextAttack() { return ++nextId; }
    void emit(UnwakingHazard hazard) { hazards.put(hazard.id(),hazard); }
    void afterAssault() { normalSinceAssault=0; }
    /**
     * One player's hazards, never more than the payload can carry.
     *
     * <p>{@code UnwakingSnapshotPayload.read} rejects a snapshot of more than
     * {@link #SNAPSHOT_LIMIT} hazards, and rejecting it disconnects the client. Every schedule is
     * budgeted to stay under that and a test pins it, but a schedule is a plan and this is the
     * wire: if some combination ever exceeds it, the oldest bodies are the ones closest to touching
     * you, so the newest are dropped. They reappear as room frees up, a tick or two into a warning
     * that runs for twenty-eight.
     */
    static final int SNAPSHOT_LIMIT = 16;
    List<UnwakingHazard> snapshot(UUID player) {
        List<UnwakingHazard> mine=hazards.values().stream()
                .filter(h->h.recipient()==null||h.recipient().equals(player)).toList();
        if(mine.size()<=SNAPSHOT_LIMIT) return mine;
        return mine.stream().sorted(java.util.Comparator.comparingLong(UnwakingHazard::start))
                .limit(SNAPSHOT_LIMIT).toList();
    }
    long combination() { return combination; }
    String combinationName() { return assault.active()?assault.name():score==null?"":score.id(); }
    boolean tearing(long now) { return teleportDestination!=null&&now<teleportAt; }
    boolean exposed(long now) { return guard.exposed(now); }
    boolean refuse(UnwakingEncounterService service, ServerPlayer player) { return false; }
    boolean majorActive(long now) { return hazards.values().stream().anyMatch(h->h.kind().major()&&h.visible(h.age(now))&&!h.recovering(h.age(now))); }

    void tick(UnwakingEncounterService service) {
        var run=service.current(); long now=service.now(); int phaseAge=(int)(now-run.phaseStart);
        if(run.phase==UnwakingPhase.UNBODYING) { if(phaseAge>=60) service.changePhase(UnwakingPhase.FINAL); return; }
        if(run.phase==UnwakingPhase.REFORMING) {
            if(phaseAge==40) { run.recoveryUntil=now+80+20*Math.min(3,fractures); service.sound(SoundEvents.AMETHYST_CLUSTER_BREAK,0.65F); }
            if(phaseAge>=120+20*Math.min(3,fractures)) service.changePhase(UnwakingPhase.FINAL);
            return;
        }
        if(run.phase==UnwakingPhase.DEATH) { if(phaseAge>=120) service.complete(); return; }
        if(run.phase.trial()&&phaseAge>=500) {
            service.changePhase(switch(run.phase) { case TRIAL_SKY->UnwakingPhase.TRIAL_BREATH; case TRIAL_BREATH->UnwakingPhase.TRIAL_CHIME; default->UnwakingPhase.REFORMING; }); return;
        }
        if(now<run.recoveryUntil) return;
        if(run.phase==UnwakingPhase.AWAKE && run.body.getHealth()<=run.body.getMaxHealth()*.4F) { service.changePhase(UnwakingPhase.UNBODYING); return; }
        if(!assault.active() && (run.phase==UnwakingPhase.AWAKE||run.phase==UnwakingPhase.FINAL) && normalSinceAssault>=2) assault.begin(service,-1);
        if(assault.active()) {
            assault.tick(service);
            if(service.current()!=run || !assault.active() || now<run.recoveryUntil) return;
        }
        guard.segments(now);
        if(score==null && !assault.active()) start(service);
        int age=(int)(now-started);
        if(!assault.active()) {
            if(score==null) return;
            finishTeleport(service);
            for(var cue:score.cues()) if(age==cue.at()) spawn(service,cue);
            if(score==null) return;
            move(service);
        }
        for(var original:List.copyOf(hazards.values())) {
            UnwakingHazard h=track(service,original);
            int t=h.age(now);
            if(t==h.kind().impact-12 || t==h.kind().impact && h.kind()==WORLD_CUT) service.sendAll();
            if(t==h.kind().impact && h.kind()!=ARRIVAL && (!UnwakingAssaultGeometry.volley(h.kind())||assault.group(h.id())==h.id()))
                cueSound(service,h,h.kind().major()?SoundEvents.WARDEN_SONIC_BOOM:SoundEvents.AMETHYST_BLOCK_HIT,h.kind().major()?0.55F:0.85F);
            if(h.kind()==RETURNING_VERDICT && t==52 && assault.group(h.id())==h.id()) cueSound(service,h,SoundEvents.AMETHYST_BLOCK_CHIME,.65F);
            offerCounters(service,h,t);
            if(h.contact(t)>=0) for(UUID id:List.copyOf(run.players.keySet())) {
                ServerPlayer p=service.player(id); var member=run.players.get(id);
                if(p==null||member==null||h.recipient()!=null&&!h.recipient().equals(id)||hit.getOrDefault(h.id(),Set.of()).contains(id)) continue;
                AABB current=p.getBoundingBox();
                // A discontinuous long-distance teleport has no traversed physical path.
                AABB previous=member.previous.distanceToSqr(current.getCenter())>4096?current:current.move(member.previous.subtract(current.getCenter()));
                if(!h.intersects(t,previous,current)) continue;
                hit.computeIfAbsent(h.id(),key->new HashSet<>()).add(id);
                if(UnwakingAssaultGeometry.volley(h.kind())) for(var sibling:hazards.values())
                    if(assault.group(sibling.id())==assault.group(h.id())) hit.computeIfAbsent(sibling.id(),key->new HashSet<>()).add(id);
                if(now<member.contactProtectedUntil) { if(id.equals(target)) finisherEligible=false; continue; }
                Vec3 source=h.kind()==DECREE?p.getEyePosition().add(h.axis().scale(12)):h.source(t,p.getEyePosition());
                boolean facing=UnwakingGuard.facing(p.getLookAngle().dot(UnwakingHazard.unit(source.subtract(p.getEyePosition()))));
                pending.add(new Pending(h,id,now,h.kind().defense==UnwakingHazard.Defense.PARRY?UnwakingGuard.graceTicks(p.connection.latency()):0,source));
            }
        }
        resolve(service);
        if(service.current()!=run||run.phase.protectsPlayer()) return;
        for(var h:List.copyOf(hazards.values())) if(h.age(now)>=h.kind().end) { hazards.remove(h.id()); counters.retire(h.id()); hit.remove(h.id()); }
        if(!assault.active()&&score!=null&&!run.phase.trial()&&age>=score.end()&&pending.isEmpty()) finish(service);
    }

    private void cueSound(UnwakingEncounterService service,UnwakingHazard h,net.minecraft.sounds.SoundEvent sound,float pitch) {
        if(h.recipient()==null) service.sound(sound,pitch);
        else { ServerPlayer p=service.player(h.recipient()); if(p!=null) p.playNotifySound(sound,net.minecraft.sounds.SoundSource.HOSTILE,.7F,pitch); }
    }
    private void start(UnwakingEncounterService service) {
        var run=service.current(); List<UUID> ids=new ArrayList<>(run.players.keySet());
        if(ids.isEmpty()) return;
        target=ids.get(Math.floorMod(run.targetIndex++,ids.size()));
        if(service.player(target)==null) return;
        started=run.phase.trial()?run.phaseStart:service.now(); combination++;
        score=run.phase.trial()?UnwakingCombinations.trial(run.phase):switch(run.phase) {
            case SLEEPING->UnwakingCombinations.sleeping(deckIndex++);
            case FINAL->UnwakingCombinations.closing(deckIndex++);
            default->UnwakingCombinations.awake(deckIndex++);
        };
        service.sendAll();
    }

    private void spawn(UnwakingEncounterService service, UnwakingCombinations.Cue cue) {
        ServerPlayer p=service.player(target); if(p==null) { clear(); return; }
        if(cue.kind()==ARRIVAL) { beginTeleport(service,p,cue.variant()); return; }
        if(cue.kind()==LAW_FRONT||cue.kind()==DECREE||cue.kind()==WORLD_CUT) {
            for(UUID id:service.current().players.keySet()) {
                ServerPlayer recipient=service.player(id); if(recipient==null) continue;
                add(service,make(service,cue,recipient,id),cue.finisher(),id.equals(target));
            }
        } else add(service,make(service,cue,p,null),cue.finisher(),true);
        service.current().body.swing(InteractionHand.MAIN_HAND);
        service.sound(SoundEvents.AMETHYST_BLOCK_CHIME,cue.kind().major()?0.55F:0.8F); service.sendAll();
    }

    private void add(UnwakingEncounterService service, UnwakingHazard hazard, boolean finish, boolean selected) {
        hazards.put(hazard.id(),hazard);
        if(finish&&selected) { finisherId=hazard.id(); finisherEligible=true; finisherStruck=false; }
    }

    private UnwakingHazard make(UnwakingEncounterService service, UnwakingCombinations.Cue cue, ServerPlayer p, UUID recipient) {
        var body=service.current().body; var kind=cue.kind(); int variant=cue.variant();
        Vec3 predicted=prediction(p), c=body.position().add(0,0.9,0), a=UnwakingHazard.unit(predicted.subtract(c));
        List<Vec3> openings=List.of(); double length=72;
        Vec3 horizontal=UnwakingHazard.unit(new Vec3(p.getLookAngle().x,0,p.getLookAngle().z));
        Vec3 sideways=new Vec3(-horizontal.z,0,horizontal.x);
        switch(kind) {
            case ECHO -> c=p.getBoundingBox().getCenter();
            case WORLD_CUT -> {
                a=switch(Math.floorMod(variant,3)) { case 0->horizontal; case 1->sideways; default->new Vec3(0,1,0); };
                c=predicted; length=512;
                Vec3 right=UnwakingHazard.unit(a.cross(Math.abs(a.y)>0.95?new Vec3(1,0,0):new Vec3(0,1,0)));
                Vec3 gap=p.getBoundingBox().getCenter().add(right.scale(14));
                openings=List.of(gap);
            }
            case HORIZON_HAND -> {
                c=body.position().add(0,0.9,0); length=256;
                Vec3 normal=variant%2==0?new Vec3(0,1,0):new Vec3(-Math.sin(Math.toRadians(35)),Math.cos(Math.toRadians(35)),0);
                Vec3 delta=predicted.subtract(c); delta=delta.subtract(normal.scale(delta.dot(normal)));
                a=UnwakingHazard.rotate(UnwakingHazard.unit(delta),normal,-Math.PI/3);
            }
            case LAW_FRONT -> { a=sideways.scale(variant%2==0?1:-1); c=p.getBoundingBox().getCenter().subtract(a.scale(8)); length=512; }
            case FALLEN_STAR -> { c=predicted.add(sideways.scale(32)); a=new Vec3(0,-1,0); length=80; }
            case EARTH_FRONT, STAR_FRONT -> {
                c=p.getBoundingBox().getCenter().subtract(sideways.scale(12)); a=new Vec3(0,1,0); length=kind==EARTH_FRONT?64:96;
                openings=List.of(UnwakingHazard.rotate(sideways,a,Math.toRadians(38)));
            }
            case DECREE -> { a=UnwakingHazard.rotate(p.getLookAngle(),new Vec3(0,1,0),Math.toRadians(variant%2==0?25:-25)); c=p.getEyePosition().add(a.scale(12)); }
            default -> {}
        }
        // Movement-only volumes in terrain need a checked route. If terrain denies one,
        // announce a counterable decree instead, before publishing any attack geometry.
        Vec3 escape = switch (kind) {
            case WORLD_CUT -> openings.getFirst().subtract(p.getBoundingBox().getCenter());
            case LAW_FRONT -> a.scale(15);
            case HORIZON_HAND -> (variant % 2 == 0 ? new Vec3(0,1,0)
                    : new Vec3(-Math.sin(Math.toRadians(35)),Math.cos(Math.toRadians(35)),0)).scale(7);
            case FALLEN_STAR -> UnwakingHazard.unit(p.getBoundingBox().getCenter().subtract(c)).scale(16);
            case EARTH_FRONT, STAR_FRONT -> c.add(openings.getFirst().scale(12)).subtract(p.getBoundingBox().getCenter());
            default -> null;
        };
        if (escape != null && !UnwakingMovement.canEscape(p, escape)) {
            kind=DECREE; c=p.getEyePosition().add(horizontal.scale(12)); a=horizontal; openings=List.of(); recipient=p.getUUID();
        }
        return new UnwakingHazard(++nextId,kind,service.now(),c,a,length,variant,openings,recipient);
    }

    private static Vec3 prediction(ServerPlayer p) {
        Vec3 lead=p.getDeltaMovement().scale(10);
        if(lead.length()>9) lead=lead.normalize().scale(9);
        return p.getBoundingBox().getCenter().add(lead);
    }

    private UnwakingHazard track(UnwakingEncounterService service, UnwakingHazard h) {
        int age=h.age(service.now());
        if(age<0||age>h.kind().impact-12) return h;
        ServerPlayer p=service.player(target); if(p==null) return h;
        if(h.kind()==PROCESSION_CUT||h.kind()==PALM||h.kind()==THRUST) {
            Vec3 c=service.current().body.position().add(0,0.9,0);
            h=h.frame(c,UnwakingHazard.unit(prediction(p).subtract(c)),h.length()); hazards.put(h.id(),h);
            if(h.id()==finisherId&&age==h.kind().impact-12) finisherEligible=p.position().distanceTo(c)<=64;
        }
        return h;
    }

    private void move(UnwakingEncounterService service) {
        ServerPlayer p=service.player(target); if(p==null) return;
        var body=service.current().body; Vec3 delta=p.getBoundingBox().getCenter().subtract(body.position().add(0,0.9,0));
        body.setYRot(Mth.rotateIfNecessary(body.getYRot(),(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),service.current().phase==UnwakingPhase.SLEEPING?6:12));
        body.setYHeadRot(body.getYRot()); body.yBodyRot=body.getYRot();
        if(teleportDestination!=null) return;
        boolean committed=hazards.values().stream().anyMatch(h->h.kind()!=ARRIVAL&&h.age(service.now())>=h.kind().impact-12&&!h.recovering(h.age(service.now())));
        if(committed) return;
        if(delta.length()>64) { beginTeleport(service,p,deckIndex%3); return; }
        Vec3 toward=UnwakingHazard.unit(delta), lateral=new Vec3(-toward.z,0,toward.x);
        double speed=service.current().phase==UnwakingPhase.SLEEPING?0.28:0.55;
        Vec3 movement=delta.length()>18?toward.scale(speed):lateral.scale(speed*(combination%2==0?1:-1));
        double desired=p.getY()+(service.current().phase==UnwakingPhase.SLEEPING?1.5:3);
        movement=movement.add(0,Mth.clamp(desired-body.getY(),-0.18,0.18),0);
        UnwakingMovement.move(body,body.position().add(movement));
    }

    private void beginTeleport(UnwakingEncounterService service, ServerPlayer p, int variant) {
        if(teleportDestination!=null) return;
        Vec3 destination=UnwakingMovement.destination(service.current().body,p,variant);
        if(destination==null) return;
        teleportDestination=destination; teleportAt=service.now()+16;
        hazards.put(++nextId,new UnwakingHazard(nextId,ARRIVAL,service.now(),destination,new Vec3(0,1,0),1,variant,List.of(),null));
        service.current().body.setInvisible(true); service.sendAll();
    }
    private void finishTeleport(UnwakingEncounterService service) {
        if(teleportDestination==null||service.now()<teleportAt) return;
        UnwakingMovement.move(service.current().body,teleportDestination);
        service.current().body.setInvisible(false); teleportDestination=null; teleportAt=0; service.sendAll();
    }

    private void offerCounters(UnwakingEncounterService service, UnwakingHazard h, int age) {
        if(h.kind().defense!=UnwakingHazard.Defense.PARRY||age<0||h.recovering(age)) return;
        var run=service.current();
        for(UUID id:run.players.keySet()) {
            ServerPlayer p=service.player(id);
            if(p==null||h.recipient()!=null&&!h.recipient().equals(id)||hit.getOrDefault(h.id(),Set.of()).contains(id)) continue;
            int impact=h.kind().impact;
            if(UnwakingAssaultGeometry.moving(h.kind())) {
                impact=-1;
                for(int offset=1;offset<=UnwakingCombatRules.PARRY_LEAD_TICKS;offset++) if(h.intersects(age+offset,p.getBoundingBox(),p.getBoundingBox())) { impact=age+offset; break; }
            }
            int remaining=impact-age;
            if(remaining<1||remaining>UnwakingCombatRules.PARRY_LEAD_TICKS||!h.intersects(impact,p.getBoundingBox(),p.getBoundingBox())) continue;
            int window=remaining+UnwakingGuard.graceTicks(p.connection.latency());
            Vec3 clashAt=clashPoint(h,age,p);
            if(h.kind()==CLOCK_STRIKE)
                // The one attack nobody can step out of - the clock pins you where you stand, so
                // the aimed counter is the whole defence and it stays open to every player.
                counters.offerAimed(h.id(),p,run.body,h.kind(),window,()->acceptCounter(service,h,id),h.origin(),()->assault.aimFailures++,clashAt);
            else if(UnwakingAssaultGeometry.moving(h.kind()))
                // Everything that travels: fragments, the verdict, the vortex beams, the blade, the
                // suns, the ring. These are movement checks, and only Gluttony may devour one.
                counters.offerDevour(h.id(),h.kind()==RETURNING_VERDICT&&impact>=64?1:0,p,run.body,h.kind(),window,()->acceptCounter(service,h,id),clashAt);
            else
                counters.offer(h.id(),0,p,run.body,h.kind(),window,()->acceptCounter(service,h,id),clashAt);
        }
    }
    /**
     * Where the parry should look like it happened: three blocks off the defender's eye, along the
     * line the attack is arriving on. Far enough out that the clash renderer's near fade does not
     * hide it from the one person who earned it, close enough that it reads as their block.
     */
    private static Vec3 clashPoint(UnwakingHazard h, int age, ServerPlayer p) {
        Vec3 eye=p.getEyePosition();
        return eye.add(UnwakingHazard.unit(h.source(age,eye).subtract(eye)).scale(3.0));
    }

    private void acceptCounter(UnwakingEncounterService service, UnwakingHazard h, UUID id) {
        var run=service.current(); if(run==null||!hazards.containsKey(h.id())||!run.players.containsKey(id)) return;
        parries++; var member=run.players.get(id); member.feedback=2; member.feedbackUntil=service.now()+16;
        if(run.phase==UnwakingPhase.TRIAL_CHIME&&h.kind()==DECREE&&h.variant()%2==1&&id.equals(target)) fractures=Math.min(3,fractures+1);
        hazards.remove(h.id()); pending.removeIf(p->p.hazard.id()==h.id()); counters.retire(h.id());
        if(h.id()==finisherId) finisherStruck=false;
        // Only this strike breaks. The score and remaining follow-ups keep running.
        service.sendAll();
    }
    private void resolve(UnwakingEncounterService service) {
        var run=service.current(); long now=service.now();
        for(var p:List.copyOf(pending)) {
            long deadline=pending.stream().filter(other->other.hazard.id()==p.hazard.id()).mapToLong(other->other.impact+other.grace).max().orElse(p.impact+p.grace);
            if(now<deadline) continue;
            pending.remove(p); var member=run.players.get(p.player); ServerPlayer player=service.player(p.player);
            if(member==null||player==null) continue;
            if(p.hazard.id()==finisherId&&p.player.equals(target)) finisherStruck=true;
            if(now<member.contactProtectedUntil) continue;
            contacts++;
            member.feedback=3; member.feedbackUntil=now+16;
            member.contactProtectedUntil=now+10;
            float damage=p.hazard.kind().damageFor(player.getMaxHealth());
            rawDamage+=damage; float before=player.getHealth();
            player.hurtServer(player.serverLevel(),new UnwakingDamageSource(run.body,p.hazard.kind()),damage);
            healthDamage+=Math.max(0,before-player.getHealth());
            if(service.current()!=run||run.phase==UnwakingPhase.RETURNING) return;
        }
    }
    private void finish(UnwakingEncounterService service) {
        var run=service.current(); boolean clean=finisherEligible&&!finisherStruck&&run.players.containsKey(target);
        completedCombinations++; normalSinceAssault++;
        boolean opened=guard.finish(combination,clean,service.now());
        ServerPlayer p=service.player(target);
        clear(); run.body.setInvisible(false);
        if(opened) {
            openings++; run.recoveryUntil=guard.exposedUntil();
            if(p!=null) { Vec3 dest=UnwakingMovement.destination(run.body,p,3); if(dest!=null) UnwakingMovement.move(run.body,dest); }
            service.sound(SoundEvents.AMETHYST_CLUSTER_BREAK,0.55F);
        }
        service.sendAll();
    }
    void cancelPlayer(UUID player) {
        counters.remove(player); pending.removeIf(p->p.player.equals(player));
        if(player.equals(target)) finisherEligible=false;
    }
}
