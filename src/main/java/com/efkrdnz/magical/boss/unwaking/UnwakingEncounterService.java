package com.efkrdnz.magical.boss.unwaking;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.ChronosDimensionService;
import com.efkrdnz.magical.magic.ChronosSequenceService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.network.UnwakingGuardPayload;
import com.efkrdnz.magical.network.UnwakingReadyPayload;
import com.efkrdnz.magical.network.UnwakingSnapshotPayload;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalBlocks;
import com.efkrdnz.magical.registry.MagicalEntities;
import com.efkrdnz.magical.tower.instance.InstanceManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Encounter lifecycle, participant ownership, phase gates and durable return handling. */
public final class UnwakingEncounterService {
    public static final Vec3 CENTER = new Vec3(0.5, 140, 0.5);
    private static final Map<MinecraftServer, UnwakingEncounterService> SERVICES = new WeakHashMap<>();
    private static final TicketType<UUID> TICKET = TicketType.create("magical_unwaking", Comparator.<UUID>naturalOrder());
    private static final ResourceLocation BRACE_SLOW = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "unwaking_brace");
    private final MinecraftServer server;
    private final Map<UUID, Ready> ready = new HashMap<>();
    private Run run;
    private long clock;
    private boolean internalTransfer;

    private record Ready(BlockPos shrine, long expires) {}
    private record Pending(UUID player, long impact, int grace, boolean held, boolean facing) {}
    static final class Participant {
        final UnwakingGuard parryInput = new UnwakingGuard();
        final float originalFlySpeed;
        final boolean originallyFlying;
        Vec3 previous;
        Vec3 safe = CENTER;
        boolean destinationReady;
        boolean hitboxes;
        boolean reducedEffects;
        int feedback;
        long feedbackUntil;
        long contactProtectedUntil;
        Participant(ServerPlayer player) {
            previous = player.getBoundingBox().getCenter();
            originalFlySpeed = player.getAbilities().getFlyingSpeed();
            originallyFlying = player.getAbilities().flying;
        }
    }
    static final class Run {
        final UUID id = UUID.randomUUID();
        final BlockPos shrine;
        final Map<UUID, Participant> players = new LinkedHashMap<>();
        final List<Pending> pending = new ArrayList<>();
        final UnwakingCounterWindows sleepCounters = new UnwakingCounterWindows();
        final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.magical.unwaking_god"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
        UnwakingGodEntity body;
        UnwakingPhase phase = UnwakingPhase.INTRO;
        long phaseStart;
        long revision;
        long attackStart = -1;
        long recoveryUntil;
        int targetIndex;
        UUID target;
        UnwakingGesture gesture;
        boolean domainTickets;
        final UnwakingDomainCombat combat = new UnwakingDomainCombat();
        final Map<net.minecraft.resources.ResourceKey<Level>, java.util.Set<ChunkPos>> movingTickets = new HashMap<>();
        float healthCeiling;
        boolean quiet, debug, victory;
        Run(BlockPos shrine, UnwakingGodEntity body, long clock) { this.shrine = shrine; this.body = body; phaseStart = clock; bar.setVisible(false); }
    }

    private UnwakingEncounterService(MinecraftServer server) { this.server = server; }
    public static UnwakingEncounterService get(MinecraftServer server) { return SERVICES.computeIfAbsent(server, UnwakingEncounterService::new); }
    public boolean reserved() { return run != null; }
    public boolean participant(UUID id) { return run != null && run.players.containsKey(id); }
    public boolean internalTransfer() { return internalTransfer; }
    public boolean movementLocked(ServerPlayer player) { return participant(player.getUUID()) && run.combat.assault.locked(player.getUUID(),clock); }
    public void enforceMovement(ServerPlayer player) {
        if(movementLocked(player)) run.combat.assault.pin(player,run.combat.assault.state(player.getUUID()));
    }
    Run current() { return run; }
    long now() { return clock; }
    ServerPlayer player(UUID id) { return server.getPlayerList().getPlayer(id); }
    public boolean protects(ServerPlayer player) {
        return participant(player.getUUID()) && run.phase.protectsPlayer()
                || UnwakingRecoveryData.get(server).point(player.getUUID()) != null && !participant(player.getUUID());
    }
    public boolean protectsShrine(BlockPos pos) { return run != null && run.shrine.equals(pos); }
    public static float maximumHealth(int players) {
        float base=com.efkrdnz.magical.MagicalConfig.SPEC.isLoaded()?com.efkrdnz.magical.MagicalConfig.UNWAKING_HEALTH.get():36000;
        return base*(1+0.65F*(Math.clamp(players,1,4)-1));
    }

    public String validateSite(BlockPos shrine, boolean existing) {
        ServerLevel level = server.overworld();
        if (!level.getWorldBorder().isWithinBounds(shrine.offset(-24, 0, -24)) || !level.getWorldBorder().isWithinBounds(shrine.offset(24, 0, 24))) return "border";
        if (!(existing ? level.getBlockState(shrine).is(MagicalBlocks.UNWAKING_SHRINE.get()) : level.getBlockState(shrine).isAir())) return "occupied";
        if (!level.getBlockState(shrine.below()).isFaceSturdy(level,shrine.below(),Direction.UP)) return "ground";
        for(int y=1;y<=4;y++) if(!level.getBlockState(shrine.above(y)).isAir()) return "headroom";
        return null;
    }

    public int createShrine(ServerPlayer player, BlockPos pos) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) return message(player, "overworld");
        String problem = validateSite(pos, false);
        if (problem != null) return message(player, "site." + problem);
        if (!server.overworld().setBlock(pos, MagicalBlocks.UNWAKING_SHRINE.get().defaultBlockState(), 3)) return message(player, "site.occupied");
        ensureDormant(pos);
        return message(player, "created");
    }

    public void ready(ServerPlayer player, BlockPos pos) {
        if (!player.level().dimension().equals(Level.OVERWORLD) || player.distanceToSqr(Vec3.atCenterOf(pos)) > 64) return;
        if (run != null || UnwakingRecoveryData.get(server).point(player.getUUID()) != null) { message(player, "busy"); return; }
        if (player.isSpectator() || InstanceManager.ownedBy(player.getUUID()) != null) { message(player, "unavailable"); return; }
        Ready old = ready.get(player.getUUID());
        if (old != null && old.shrine.equals(pos)) { ready.remove(player.getUUID()); message(player, "unready"); return; }
        if (ready.values().stream().filter(r -> r.shrine.equals(pos) && r.expires > clock).count() >= 4) { message(player, "full"); return; }
        ready.put(player.getUUID(), new Ready(pos.immutable(), clock + 1200));
        ensureDormant(pos);
        message(player, "ready");
    }

    private UnwakingGodEntity ensureDormant(BlockPos pos) {
        ServerLevel level = server.overworld();
        var bodies = level.getEntitiesOfClass(UnwakingGodEntity.class, new AABB(pos).inflate(8), e -> e.runId() == null && e.shrine().equals(pos));
        if (!bodies.isEmpty()) return bodies.getFirst();
        UnwakingGodEntity body = new UnwakingGodEntity(MagicalEntities.UNWAKING_GOD.get(), level);
        body.bind(pos, null); body.moveTo(pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5, 0, 0);
        level.addFreshEntity(body);
        return body;
    }

    public void validateBody(UnwakingGodEntity body) {
        if (body.runId() != null) {
            if (run == null || !run.id.equals(body.runId()) || run.body != body) body.discard();
        } else if (!body.level().dimension().equals(Level.OVERWORLD) || !body.level().getBlockState(body.shrine()).is(MagicalBlocks.UNWAKING_SHRINE.get())) body.discard();
    }

    public boolean hurtBody(UnwakingGodEntity body, DamageSource source, float damage) {
        if (!Float.isFinite(damage) || damage <= 0) return false;
        ServerPlayer attacker = source.getEntity() instanceof ServerPlayer p ? p
                : source.getEntity() instanceof TamableAnimal pet && pet.getOwner() instanceof ServerPlayer owner ? owner : null;
        if (attacker == null) return false;
        if (body.runId() == null) { start(attacker, body); return false; }
        if (run == null || run.body != body || !participant(attacker.getUUID()) || !run.phase.damageable(clock - run.phaseStart) || run.combat.tearing(clock) || run.combat.assault.active()) return false;
        if (run.combat.refuse(this, attacker)) {
            if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) projectile.discard();
            return false;
        }
        float gate = Math.max(1, body.getMaxHealth() * run.phase.healthFloor());
        float allowed = Math.min(damage * (clock < run.recoveryUntil ? 1.0F : run.combat.guard.multiplier(clock)), Math.max(0, body.getHealth() - gate));
        boolean hit = allowed > 0 && body.receiveDamage((ServerLevel) body.level(), source, allowed);
        // Plugins can alter damage later in the vanilla pipeline; the entity's setHealth gate
        // also clamps before lethal removal. Reconcile the phase immediately after the hit.
        if (body.getHealth() <= gate + 0.01F) {
            body.setHealth(gate);
            changePhase(run.phase == UnwakingPhase.SLEEPING ? UnwakingPhase.AWAKENING : UnwakingPhase.DEATH);
        }
        if(run!=null && run.phase==UnwakingPhase.AWAKE && body.getHealth()<=body.getMaxHealth()*.4F && !run.combat.assault.rewarding(clock)) changePhase(UnwakingPhase.UNBODYING);
        if (run != null && (run.phase == UnwakingPhase.FINAL || run.phase == UnwakingPhase.REFORMING) && body.getHealth() <= body.getMaxHealth() * 0.15F) run.quiet = true;
        return hit;
    }

    public float clampHealth(UnwakingGodEntity body, float health) {
        if (run != null && run.body == body) return Math.max(Math.max(1, body.getMaxHealth() * run.phase.healthFloor()), Math.min(run.healthCeiling > 0 ? run.healthCeiling : body.getMaxHealth(), health));
        return health;
    }

    private boolean start(ServerPlayer striker, UnwakingGodEntity body) {
        Ready selection = ready.get(striker.getUUID());
        if (run != null || selection == null || selection.expires <= clock || !selection.shrine.equals(body.shrine())) { message(striker, "touch_shrine"); return false; }
        String problem = validateSite(body.shrine(), true);
        if (problem != null) { message(striker, "site." + problem); return false; }
        ServerLevel domain = server.getLevel(ChronosDimensionService.CHRONOS_DIMENSION);
        if (domain == null) { message(striker, "missing_dimension"); return false; }
        if (!domain.players().isEmpty() || InstanceManager.all().stream().anyMatch(i -> i.isActive() && i.archetype().dimension().equals(domain.dimension()))) { message(striker, "domain_busy"); return false; }
        List<ServerPlayer> roster = server.getPlayerList().getPlayers().stream().filter(p -> {
            Ready r = ready.get(p.getUUID());
            return r != null && r.expires > clock && r.shrine.equals(body.shrine()) && p.level().dimension().equals(Level.OVERWORLD)
                    && p.distanceToSqr(Vec3.atCenterOf(body.shrine())) <= 576 && !p.isSpectator() && p.isAlive()
                    && UnwakingRecoveryData.get(server).point(p.getUUID()) == null;
        }).sorted(Comparator.comparing(ServerPlayer::getUUID)).limit(4).toList();
        if (!roster.contains(striker)) return false;
        Run next = new Run(body.shrine(), body, clock);
        for (ServerPlayer p : roster) next.players.put(p.getUUID(), new Participant(p));
        run = next;
        body.bind(body.shrine(), next.id); body.encounterHealth(maximumHealth(roster.size())); body.phase(UnwakingPhase.INTRO);
        next.healthCeiling = body.getMaxHealth();
        ticket(server.overworld(), new ChunkPos(body.shrine()), 3, true);
        ChronosSequenceService.stop();
        roster.forEach(p -> { ready.remove(p.getUUID()); next.bar.addPlayer(p); message(p, "preview_notice"); });
        UnwakingRecoveryData data = UnwakingRecoveryData.get(server);
        roster.forEach(p -> data.remember(p.getUUID(), new UnwakingRecoveryData.ReturnPoint(next.id, p.position(), p.getYRot(), p.getXRot(),
                next.players.get(p.getUUID()).originalFlySpeed, next.players.get(p.getUUID()).originallyFlying)));
        try { data.flushVerified(server); }
        catch (IOException | RuntimeException e) {
            MagicalMod.LOGGER.error("Cannot persist Unwaking starting positions", e); broadcast("save_failed"); abort(); return false;
        }
        sendAll();
        return true;
    }

    public void tick() {
        clock++;
        if (clock % 20 == 0) recoverPending();
        ready.entrySet().removeIf(e -> {
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            return e.getValue().expires <= clock || player == null || !player.level().dimension().equals(Level.OVERWORLD)
                    || player.distanceToSqr(Vec3.atCenterOf(e.getValue().shrine)) > 576;
        });
        if (run == null) return;
        if (run.phase == UnwakingPhase.RETURNING) { finishReturns(); return; }
        Run current = run;
        for (UUID id : List.copyOf(current.players.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive() || player.isSpectator()) { leave(id); continue; }
            boolean inDomain = player.level().dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION);
            boolean expectedDomain = current.domainTickets && clock - current.phaseStart >= 100 || current.phase.inDomain();
            if (!player.level().dimension().equals(inDomain ? ChronosDimensionService.CHRONOS_DIMENSION : Level.OVERWORLD)
                    || expectedDomain != inDomain) { leave(id); continue; }
            Participant member = current.players.get(id);
            applyBraceMovement(player, false, member.originalFlySpeed);
            if (inDomain) {
                player.getAbilities().mayfly = true;
                if (!player.getAbilities().flying) { player.getAbilities().flying = true; player.onUpdateAbilities(); }
                if (player.getY() > player.level().getMinY()+16 && UnwakingMovement.clear(player,player.position())) member.safe=player.position();
                if (player.getY() < player.level().getMinY()+4) {
                    current.combat.cancelPlayer(id);
                    member.contactProtectedUntil=clock+20;
                    player.teleportTo(member.safe.x,member.safe.y,member.safe.z);
                    player.setDeltaMovement(Vec3.ZERO); member.previous=player.getBoundingBox().getCenter();
                }
            }

        }
        if (run != current || current.phase == UnwakingPhase.RETURNING) return;
        if (current.players.isEmpty()) { abort(); return; }
        if (current.body == null || !current.body.isAlive()) { abort(); return; }
        if (current.domainTickets) {
            ServerLevel domain = server.getLevel(ChronosDimensionService.CHRONOS_DIMENSION);
            if (domain == null || domain.players().stream().anyMatch(p -> !current.players.containsKey(p.getUUID()))) { abort(); return; }
        }
        if(clock%20==0) refreshMovingTickets();
        long phaseAge = clock - current.phaseStart;
        switch (current.phase) {
            case INTRO -> { if (phaseAge >= 60) changePhase(UnwakingPhase.SLEEPING); }
            case SLEEPING -> current.combat.tick(this);
            case AWAKENING -> {
                if (phaseAge == 30) broadcast("awakening");
                if (phaseAge >= 100 && !current.domainTickets) transferToDomain();
                if (run == current && current.domainTickets && phaseAge >= 160) {
                    if (current.players.values().stream().allMatch(p -> p.destinationReady)) changePhase(UnwakingPhase.ORIENTATION);
                    else if (phaseAge >= 360) abort();
                }
            }
            case ORIENTATION -> { if (phaseAge >= 40) changePhase(UnwakingPhase.AWAKE); }
            case AWAKE, UNBODYING, TRIAL_SKY, TRIAL_BREATH, TRIAL_CHIME, REFORMING, FINAL, DEATH -> current.combat.tick(this);
            default -> {}
        }
        if (run != null) {
            run.bar.setProgress(run.body.getHealth() / run.body.getMaxHealth());
            if (clock % 4 == 0) sendAll();
            run.players.forEach((id, member) -> { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) member.previous = p.getBoundingBox().getCenter(); });
        }
    }

    void sound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        for (UUID id : run.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.playNotifySound(sound, SoundSource.HOSTILE, 0.8F, pitch);
        }
    }

    void changePhase(UnwakingPhase phase) {
        run.combat.assault.stop(this);
        cancelAttack(); run.phase = phase; run.phaseStart = clock; run.body.phase(phase);
        run.combat.enter(phase); run.recoveryUntil = 0;
        run.body.setInvisible(phase.hiddenBody());
        if (phase == UnwakingPhase.REFORMING && !run.players.isEmpty()) {
            ServerPlayer survivor = player(run.players.keySet().iterator().next());
            if (survivor != null) { Vec3 dest=UnwakingMovement.destination(run.body,survivor,3); if(dest!=null) UnwakingMovement.move(run.body,dest); }
        }
        if (phase == UnwakingPhase.AWAKENING) run.healthCeiling = run.body.getMaxHealth() * 0.7F;
        if (phase == UnwakingPhase.UNBODYING) run.healthCeiling = run.body.getMaxHealth() * 0.4F;
        if (phase == UnwakingPhase.DEATH) run.healthCeiling = 1;
        run.bar.setVisible(phase != UnwakingPhase.INTRO && phase != UnwakingPhase.RETURNING && !phase.trial());
        if (phase == UnwakingPhase.AWAKE || phase == UnwakingPhase.UNBODYING || phase.trial() || phase == UnwakingPhase.REFORMING || phase == UnwakingPhase.FINAL || phase == UnwakingPhase.DEATH) broadcast(phase.name().toLowerCase(java.util.Locale.ROOT));
        sendAll();
    }

    private void cancelAttack() { if (run != null) { run.sleepCounters.clear(); run.attackStart = -1; run.gesture = null; run.pending.clear(); } }

    private void transferToDomain() {
        ServerLevel domain = server.getLevel(ChronosDimensionService.CHRONOS_DIMENSION);
        if (domain == null || !domain.players().isEmpty()) { broadcast("domain_busy"); abort(); return; }
        ticket(domain, new ChunkPos(BlockPos.containing(CENTER)), 2, true);
        run.movingTickets.computeIfAbsent(domain.dimension(),key->new java.util.HashSet<>()).add(new ChunkPos(BlockPos.containing(CENTER)));
        run.domainTickets = true;
        // Only validate the arrival pocket; the encounter itself has no enclosing volume.
        for (BlockPos pos : BlockPos.betweenClosed(-8,138,-14,9,146,9)) {
            if(!domain.getBlockState(pos).isAir()) { broadcast("domain_terrain"); abort(); return; }
        }
        UnwakingRecoveryData data = UnwakingRecoveryData.get(server);
        // The original shrine-side positions were durably recorded at roster lock.
        try { data.flushVerified(server); }
        catch (IOException | RuntimeException e) { MagicalMod.LOGGER.error("Cannot persist Unwaking return markers; transfer canceled", e); broadcast("save_failed"); abort(); return; }
        float health = run.body.getHealth(), maximum = run.body.getMaxHealth();
        UnwakingGodEntity next = new UnwakingGodEntity(MagicalEntities.UNWAKING_GOD.get(), domain);
        next.bind(run.shrine, run.id); next.encounterHealth(maximum); next.setHealth(health); next.phase(run.phase);
        next.place(CENTER.add(0,0,-12));
        if (!domain.addFreshEntity(next)) { abort(); return; }
        run.body.discard(); run.body = next;
        int index = 0, count = run.players.size();
        internalTransfer = true;
        try {
            for (UUID id : List.copyOf(run.players.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                double angle = index++ * Math.PI * 2 / count;
                Vec3 destination = CENTER.add(Math.cos(angle) * 6, 0, Math.sin(angle) * 6);
                p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities();
                if (!p.teleportTo(domain, destination.x, destination.y, destination.z, EnumSet.noneOf(Relative.class), 180, 0, true)) { abort(); return; }
                p.setDeltaMovement(Vec3.ZERO); p.fallDistance = 0;
                run.players.get(id).safe = destination;
            }
        } finally { internalTransfer = false; }
        sendAll();
    }

    private void refreshMovingTickets() {
        if(run==null) return;
        Map<net.minecraft.resources.ResourceKey<Level>,java.util.Set<ChunkPos>> wanted=new HashMap<>();
        wanted.computeIfAbsent(run.body.level().dimension(),key->new java.util.HashSet<>()).add(run.body.chunkPosition());
        for(UUID id:run.players.keySet()) {
            ServerPlayer p=player(id);
            if(p!=null) wanted.computeIfAbsent(p.level().dimension(),key->new java.util.HashSet<>()).add(p.chunkPosition());
        }
        for(var entry:wanted.entrySet()) {
            ServerLevel level=server.getLevel(entry.getKey()); if(level==null) continue;
            var old=run.movingTickets.getOrDefault(entry.getKey(),java.util.Set.of());
            for(ChunkPos pos:entry.getValue()) if(!old.contains(pos)) ticket(level,pos,2,true);
        }
        for(var entry:run.movingTickets.entrySet()) {
            ServerLevel level=server.getLevel(entry.getKey()); if(level==null) continue;
            for(ChunkPos pos:entry.getValue()) if(!wanted.getOrDefault(entry.getKey(),java.util.Set.of()).contains(pos)) ticket(level,pos,2,false);
        }
        run.movingTickets.clear(); run.movingTickets.putAll(wanted);
    }
    private void releaseMovingTickets() {
        if(run==null) return;
        for(var entry:run.movingTickets.entrySet()) {
            ServerLevel level=server.getLevel(entry.getKey());
            if(level!=null) for(ChunkPos pos:entry.getValue()) ticket(level,pos,2,false);
        }
        run.movingTickets.clear();
    }
    int movingTicketCount() { return run==null?0:run.movingTickets.values().stream().mapToInt(java.util.Set::size).sum(); }
    public int visuals(ServerPlayer player, Boolean hitboxes, Boolean reduced) {
        if(!participant(player.getUUID())) return message(player,"touch_shrine");
        Participant member=run.players.get(player.getUUID());
        if(hitboxes!=null) member.hitboxes=hitboxes;
        if(reduced!=null) member.reducedEffects=reduced;
        sendAll(); return 1;
    }

    private void ticket(ServerLevel level, ChunkPos pos, int radius, boolean add) {
        if (add) {
            level.getChunkSource().addRegionTicket(TICKET, pos, radius, run.id, true);
            for (int x = pos.x - radius + 1; x < pos.x + radius; x++) for (int z = pos.z - radius + 1; z < pos.z + radius; z++) level.getChunk(x, z);
        } else level.getChunkSource().removeRegionTicket(TICKET, pos, radius, run.id, true);
    }

    /** Observe run-scoped B edges; counter responses still use the normal spell-counter route. */
    public void guard(ServerPlayer player, UnwakingGuardPayload payload) {
        if(run==null||!run.id.equals(payload.run())) return;
        var member=run.players.get(player.getUUID());
        if(member!=null&&player.isAlive()&&player.level()==run.body.level())
            member.parryInput.accept(payload.sequence(),payload.held(),player.level().getGameTime());
    }
    public void readyForDomain(ServerPlayer player, UnwakingReadyPayload payload) {
        if (run != null && run.id.equals(payload.run()) && participant(player.getUUID()) && payload.revision() > 0 && payload.revision() <= run.revision
                && player.level().dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION)) run.players.get(player.getUUID()).destinationReady = true;
    }

    public void leave(UUID id) {
        if (run == null) return;
        Participant removed = run.players.remove(id);
        if (removed == null) return;
        run.combat.cancelPlayer(id);
        run.combat.assault.remove(this,id);
        run.sleepCounters.remove(id);
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) { run.bar.removePlayer(player); applyBraceMovement(player, false, removed.originalFlySpeed); send(player, removed, true); recover(player); }
        if (run.players.isEmpty()) abort();
    }

    public void abort() {
        if (run == null) return;
        if (run.phase != UnwakingPhase.RETURNING) { broadcast("aborted"); changePhase(UnwakingPhase.RETURNING); run.bar.removeAllPlayers(); }
        finishReturns();
    }

    void complete() {
        if (run == null) return;
        run.victory = true;
        if (!run.debug) {
            var data = UnwakingRecoveryData.get(server);
            run.players.keySet().forEach(data::qualifyVictory);
            try { data.flushVerified(server); }
            catch (IOException | RuntimeException e) {
                MagicalMod.LOGGER.error("Cannot persist Unwaking victory; retrying while protected", e); return;
            }
        }
        broadcast(run.debug ? "debug_complete" : "victory");
        changePhase(UnwakingPhase.RETURNING); run.bar.removeAllPlayers(); finishReturns();
    }

    private void finishReturns() {
        if (run == null) return;
        for (UUID id : List.copyOf(run.players.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || recover(player)) {
                Participant participant = run.players.remove(id);
                if (player != null) send(player, participant, true);
            }
        }
        if (!run.players.isEmpty()) return;
        run.body.discard();
        ticket(server.overworld(), new ChunkPos(run.shrine), 3, false);
        ServerLevel domain = server.getLevel(ChronosDimensionService.CHRONOS_DIMENSION);
        releaseMovingTickets();
        BlockPos shrine = run.shrine;
        run = null;
        if (server.overworld().getBlockState(shrine).is(MagicalBlocks.UNWAKING_SHRINE.get())) ensureDormant(shrine);
    }

    public boolean recover(ServerPlayer player) {
        UnwakingRecoveryData data = UnwakingRecoveryData.get(server);
        if (data.victoryPending(player.getUUID())) {
            var advancement = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "unwaking_god_defeated"));
            if (advancement == null) return false;
            player.getAdvancements().award(advancement, "survived");
            player.getAdvancements().save();
            // The vanilla save method can swallow IO errors. Retain the qualification
            // until its advancement is actually on disk, including across a crash.
            var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("advancements").resolve(player.getUUID() + ".json");
            try (var reader = java.nio.file.Files.newBufferedReader(path)) {
                var saved = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("magical:unwaking_god_defeated");
                if (saved == null || !saved.get("done").getAsBoolean()) return false;
            } catch (IOException | RuntimeException e) { MagicalMod.LOGGER.error("Unwaking advancement has not persisted; qualification retained", e); return false; }
        }
        UnwakingRecoveryData.ReturnPoint point = data.point(player.getUUID());
        if (point == null) {
            if (data.victoryPending(player.getUUID())) {
                data.clearVictory(player.getUUID());
                try { data.flushVerified(server); }
                catch (IOException | RuntimeException e) { data.qualifyVictory(player.getUUID()); return false; }
            }
            return true;
        }
        ServerLevel level = server.overworld();
        Vec3 destination = safeReturn(level, player, BlockPos.containing(point.position()));
        if (destination == null && player.getRespawnPosition() != null && player.getRespawnDimension().equals(Level.OVERWORLD)) destination = safeReturn(level, player, player.getRespawnPosition());
        if (destination == null) {
            BlockPos spawn = level.getSharedSpawnPos();
            destination = safeReturn(level, player, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        if (destination == null) {
            player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.onUpdateAbilities();
            if (clock % 100 == 0) message(player, "return_wait");
            return false;
        }
        internalTransfer = true;
        try {
            if (!player.teleportTo(level, destination.x, destination.y, destination.z, EnumSet.noneOf(Relative.class), point.yaw(), point.pitch(), true)) return false;
        } finally { internalTransfer = false; }
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        boolean flight = player.isCreative() || player.isSpectator() || player.getData(MagicalAttachments.MAGIC_STATE).isPassiveEnabled(MagicPassiveContent.MANA_FLIGHT.id());
        player.getAbilities().mayfly = flight;
        player.getAbilities().flying = player.isSpectator() || flight && point.flying();
        applyBraceMovement(player, false, point.flySpeed()); player.onUpdateAbilities();
        data.forget(player.getUUID());
        boolean victoryPending = data.victoryPending(player.getUUID());
        data.clearVictory(player.getUUID());
        try { data.flushVerified(server); }
        catch (IOException | RuntimeException e) { data.remember(player.getUUID(), point); if (victoryPending) data.qualifyVictory(player.getUUID()); MagicalMod.LOGGER.error("Unwaking return completed but recovery record could not be cleared", e); return false; }
        return true;
    }

    private Vec3 safeReturn(ServerLevel level, ServerPlayer player, BlockPos near) {
        for (int radius = 0; radius <= 8; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            for (int y = 3; y >= -4; y--) {
                BlockPos feet = near.offset(x, y, z);
                level.getChunkAt(feet);
                if (!level.getWorldBorder().isWithinBounds(feet) || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                        || level.getBlockState(feet.below()).is(Blocks.MAGMA_BLOCK)
                        || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
                Vec3 dest = Vec3.atBottomCenterOf(feet);
                AABB box = player.getBoundingBox().move(dest.subtract(player.position()));
                if (level.noCollision(player, box)) return dest;
            }
        }
        return null;
    }

    private void recoverPending() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) if (!participant(player.getUUID())) recover(player);
    }

    private static void applyBraceMovement(ServerPlayer player, boolean held, float flySpeed) {
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            if (held && !movement.hasModifier(BRACE_SLOW)) movement.addTransientModifier(new AttributeModifier(BRACE_SLOW, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else if (!held) movement.removeModifier(BRACE_SLOW);
        }
        float speed = flySpeed * (held ? 0.75F : 1F);
        if (Math.abs(player.getAbilities().getFlyingSpeed() - speed) > 0.0001F) { player.getAbilities().setFlyingSpeed(speed); player.onUpdateAbilities(); }
    }

    void sendAll() {
        if (run == null) return;
        run.players.forEach((id, member) -> { ServerPlayer player = server.getPlayerList().getPlayer(id); if (player != null) send(player, member, false); });
    }

    private void send(ServerPlayer player, Participant member, boolean ended) {
        UnwakingGesture gesture = run.gesture;
        PacketDistributor.sendToPlayer(player, new UnwakingSnapshotPayload(run.id, ++run.revision, clock, run.phase, run.phaseStart,
                run.body.level().dimension().location(), run.body.getId(), run.attackStart < 0 ? -1 : (int) (clock - run.attackStart),
                gesture == null ? Vec3.ZERO : gesture.origin(), gesture == null ? new Vec3(0, 0, 1) : gesture.forward(),
                clock < member.feedbackUntil ? member.feedback : 0, ended, run.combat.snapshot(player.getUUID()), run.combat.fractures,
                run.quiet, Math.max(0, (int) (run.recoveryUntil - clock)), run.combat.combination(), run.combat.combinationName(), run.combat.guard.segments(clock), member.hitboxes, member.reducedEffects, run.combat.assault.state(player.getUUID())));
    }

    private void broadcast(String key) { if (run != null) run.players.keySet().forEach(id -> { ServerPlayer p = server.getPlayerList().getPlayer(id); if (p != null) message(p, key); }); }
    private static int message(ServerPlayer player, String key) { player.sendSystemMessage(Component.translatable("message.magical.unwaking." + key)); return 1; }

    public int debugAssault(ServerPlayer player,int order) {
        if(run==null||!run.debug||!run.domainTickets||!participant(player.getUUID())||run.players.values().stream().anyMatch(p->!p.destinationReady)) return message(player,"debug_enter_domain");
        // Enter the phase the rotation actually belongs to. Several attacks read phase==FINAL for
        // their harder variant, so previewing an enraged passage from AWAKE would show the calm one.
        changePhase(UnwakingAssaultState.rotation(order)==UnwakingAssaultState.Rotation.ENRAGED
                ? UnwakingPhase.FINAL : UnwakingPhase.AWAKE);
        run.combat.assault.begin(this,order); return 1;
    }
    public String status() {
        return run == null ? "No active Supreme Deity encounter." : run.id + " | " + run.phase + " tick " + (clock - run.phaseStart)
                + " | assault " + (run.combat.assault.active()?run.combat.assault.name():"none") + " | rewards " + run.combat.assault.completed
                + " | aim failures " + run.combat.assault.aimFailures + " | locks " + run.players.keySet().stream().filter(id->run.combat.assault.locked(id,clock)).count()
                + " | reward ticks " + Math.max(0,run.recoveryUntil-clock)
                + " | HP " + run.body.getHealth() + "/" + run.body.getMaxHealth() + " | participants " + run.players.size() + " | guard " + run.combat.guard.segments(clock) + " | combos " + run.combat.completedCombinations + " | openings " + run.combat.openings + " | hits/parries " + run.combat.contacts + "/" + run.combat.parries + " | raw/health damage " + run.combat.rawDamage + "/" + run.combat.healthDamage + " | tickets " + movingTicketCount() + " | fractures " + run.combat.fractures + (run.debug ? " | debug, no rewards" : "");
    }

    public int debugPhase(ServerPlayer player, boolean domain) {
        if (run == null) {
            var body = player.serverLevel().getEntitiesOfClass(UnwakingGodEntity.class, player.getBoundingBox().inflate(24)).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
            if (body.isEmpty()) return message(player, "touch_shrine");
            Ready selection = ready.get(player.getUUID());
            if (selection == null || selection.expires <= clock || !selection.shrine.equals(body.get().shrine())) ready(player, body.get().shrine());
            if (!start(player, body.get())) return 0;
        }
        run.debug = true;
        if (domain && !run.domainTickets) { run.body.setHealth(run.body.getMaxHealth() * 0.7F); changePhase(UnwakingPhase.AWAKENING); run.phaseStart = clock - 99; }
        else if (domain) changePhase(UnwakingPhase.AWAKE);
        else if (!run.domainTickets) changePhase(UnwakingPhase.SLEEPING);
        return 1;
    }

    public int debugDomainPhase(ServerPlayer player, UnwakingPhase phase) {
        if (run == null || !run.domainTickets || !participant(player.getUUID()) || run.players.values().stream().anyMatch(p -> !p.destinationReady)) return message(player, "debug_enter_domain");
        run.debug = true; run.healthCeiling = run.body.getMaxHealth() * 0.4F;
        changePhase(phase); run.body.setHealth(run.healthCeiling); sendAll(); return 1;
    }

    public static void stop(MinecraftServer server) {
        UnwakingEncounterService service = SERVICES.get(server);
        if (service != null) {
            service.abort();
            if (service.run != null) {
                service.ticket(server.overworld(), new ChunkPos(service.run.shrine), 3, false);
                ServerLevel domain = server.getLevel(ChronosDimensionService.CHRONOS_DIMENSION);
                service.releaseMovingTickets();
            }
        }
        SERVICES.remove(server);
    }
}
