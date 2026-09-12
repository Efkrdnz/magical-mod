package com.efkrdnz.magical.boss.unwaking;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.registry.MagicalBlocks;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Loaded by NeoForge's existing development GameTest run, not ordinary world gameplay. */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class UnwakingGameTests {
    private UnwakingGameTests() {}

    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> domainTests() {
        // Vanilla GameTestServer deliberately discards datapack dimensions. The script
        // supplies an isolated flat preset including Chronos, then enables this test.
        if (!Boolean.getBoolean("magical.unwaking.domainTests")) return java.util.List.of();
        return java.util.List.of(new net.minecraft.gametest.framework.TestFunction("unwaking_full_domain", "domain_trial_survival", "magical:unwaking_empty", 8000, 0, true, UnwakingGameTests::domainTrialSurvivalReformsAndLethalReturnsSafely));
    }

    public static void domainTrialSurvivalReformsAndLethalReturnsSafely(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var service = UnwakingEncounterService.get(server);
        if (server.getWorldData() instanceof net.minecraft.world.level.storage.PrimaryLevelData data) data.withConfirmedWarning(true);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "unwaking-test"), false);
        var player = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) {
                super.sendSystemMessage(message);
                com.efkrdnz.magical.MagicalMod.LOGGER.info("Unwaking test feedback: {}", message.getString());
            }
        };
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var peerCookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"unwaking-peer"),false);
        var peer=new net.minecraft.server.level.ServerPlayer(server,helper.getLevel(),peerCookie.gameProfile(),peerCookie.clientInformation());
        var peerConnection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(peerConnection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(peerConnection);
        server.getPlayerList().placeNewPlayer(peerConnection,peer,peerCookie);
        peer.setGameMode(net.minecraft.world.level.GameType.CREATIVE); peer.setNoGravity(true);
        helper.assertTrue(server.getLevel(com.efkrdnz.magical.magic.ChronosDimensionService.CHRONOS_DIMENSION)!=null,"The isolated GameTest fixture must include Chronos End in its flat preset");
        BlockPos shrine = helper.absolutePos(new BlockPos(60, 2, 60));
        var shrineChunk = new net.minecraft.world.level.ChunkPos(shrine);
        for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) server.overworld().setChunkForced(shrineChunk.x+x,shrineChunk.z+z,true);
        // A new platform in this disposable GameTest world; never alter an existing save.
        for (int x=-12; x<=12; x++) for (int z=-12; z<=12; z++) if (x*x+z*z<=144) server.overworld().setBlock(shrine.offset(x,-1,z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        player.teleportTo(shrine.getX()+3.5,shrine.getY(),shrine.getZ()+0.5);
        helper.assertTrue(service.validateSite(shrine,false)==null,"Test clearing rejected: "+service.validateSite(shrine,false));
        service.createShrine(player,shrine);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(!server.overworld().getEntitiesOfClass(UnwakingGodEntity.class, new net.minecraft.world.phys.AABB(shrine).inflate(8)).isEmpty(), "Waiting for the fixture chunk and its entity tracking"))
                .thenExecute(() -> {
            player.teleportTo(shrine.getX()+3.5,shrine.getY(),shrine.getZ()+0.5);
            peer.teleportTo(shrine.getX()+4.5,shrine.getY(),shrine.getZ()+.5); service.ready(peer,shrine);
            service.ready(player,shrine);
            service.debugPhase(player,false);
            helper.assertTrue(service.current()!=null && service.current().players.size()==2,"Shrine must create an active two-player run");
            var initial=service.current(); initial.debug=false;
            var ownMember=initial.players.remove(player.getUUID()); var peerMember=initial.players.remove(peer.getUUID());
            initial.players.put(player.getUUID(),ownMember); initial.players.put(peer.getUUID(),peerMember);
            var originalReturn=UnwakingRecoveryData.get(server).point(player.getUUID());
            player.getAbilities().flying=true; player.setNoGravity(true);
            player.teleportTo(shrine.getX()+403.5,shrine.getY()+5,shrine.getZ()+0.5);
            peer.teleportTo(shrine.getX()+407.5,shrine.getY()+5,shrine.getZ()+0.5); peer.getAbilities().flying=true;
            player.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE).setMana(0);
            int[] stage={0};
            boolean[] staleChecked={false};
            var combinations=new java.util.HashSet<String>();
            var observed=new java.util.HashSet<UnwakingHazard.Kind>();
            var passages=new java.util.HashSet<UnwakingAssaultState.Passage>();
            long[] rewardStarted={-1}; boolean[] clockChecks={false,false,false}; int[] clockParries={-1};
            helper.onEachTick(() -> {
                player.doTick(); peer.doTick(); // Embedded connections have no ordinary client heartbeat.
                var run=service.current();
                if(run!=null) for(var defender:java.util.List.of(player,peer))
                    service.guard(defender,new com.efkrdnz.magical.network.UnwakingGuardPayload(run.id,defender.level().getGameTime()*4,false));
                if(run==null) {
                    helper.assertTrue(stage[0]==4,"Encounter ended before its death sequence");
                    helper.assertTrue(player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD),"Survivor must return to the Overworld");
                    helper.assertTrue(UnwakingRecoveryData.get(server).point(player.getUUID())==null,"Verified return must clear its recovery marker");
                    helper.assertTrue(service.movingTicketCount()==0,"Moving chunk tickets must be released");
                    var advancement=server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID,"unwaking_god_defeated"));
                    helper.assertTrue(advancement!=null&&player.getAdvancements().getOrStartProgress(advancement).isDone(),"Normal completion awards Memory of Waking");
                    server.getPlayerList().remove(player); server.getPlayerList().remove(peer);
                    for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) server.overworld().setChunkForced(shrineChunk.x+x,shrineChunk.z+z,false);
                    helper.succeed(); return;
                }
                helper.assertTrue(service.movingTicketCount()<=5,"One participant must not accumulate stale movement tickets");
                if(peer.level().dimension().equals(com.efkrdnz.magical.magic.ChronosDimensionService.CHRONOS_DIMENSION)) {
                    service.readyForDomain(peer,new com.efkrdnz.magical.network.UnwakingReadyPayload(run.id,run.revision));
                    // Keep reward placements inside the already-ticking chunk. Embedded clients
                    // do not acknowledge neighboring chunks like real player connections do.
                    if(peer.getX()>-300) peer.teleportTo(-424,160,8);
                }
                var frames=run.combat.snapshot(player.getUUID()); frames.forEach(h->observed.add(h.kind()));
                helper.assertTrue(frames.size()<=16,"Personal snapshot must stay within its bounded hazard budget");
                helper.assertTrue(frames.stream().allMatch(h->h.recipient()==null||h.recipient().equals(player.getUUID())),"Personal attacks must not leak between recipients");
                if(run.combat.assault.active()) {
                    var ownIds=frames.stream().map(UnwakingHazard::id).collect(java.util.stream.Collectors.toSet());
                    helper.assertTrue(run.combat.snapshot(peer.getUUID()).stream().noneMatch(h->ownIds.contains(h.id())),"One participant's parry must not address another's attack");
                }
                if(!run.combat.combinationName().isEmpty()) combinations.add(run.combat.combinationName());
                if(run.combat.assault.active()) {
                    var firstClock=frames.stream().filter(h->h.kind()==UnwakingHazard.Kind.CLOCK_STRIKE&&h.variant()==0).findFirst().orElse(null);
                    if(firstClock!=null) {
                        var d=firstClock.origin().subtract(player.getEyePosition());
                        player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
                        player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
                        if(clockParries[0]<0) clockParries[0]=run.combat.parries;
                        if(firstClock.age(service.now())>20) clockChecks[1]=run.combat.parries>clockParries[0];
                    }
                }
                if(stage[0]<2) {
                    for(var defender:java.util.List.of(player,peer)) for(var threat:defender.serverLevel().getEntitiesOfClass(UnwakingCounterThreatEntity.class,defender.getBoundingBox().inflate(100))) {
                        if(!com.efkrdnz.magical.magic.MagicCounterService.hasActivePrompt(defender,threat)) continue;
                        long combo=run.combat.combination();
                        if(!staleChecked[0]) {
                            com.efkrdnz.magical.magic.MagicCounterService.respond(defender,threat.getId()+100000);
                            helper.assertTrue(com.efkrdnz.magical.magic.MagicCounterService.hasActivePrompt(defender,threat),"Stale responses must not consume a current QTE");
                            staleChecked[0]=true;
                        }
                        if(threat.aimPoint()!=null) lookAt(defender,threat.aimPoint());
                        service.guard(defender,new com.efkrdnz.magical.network.UnwakingGuardPayload(run.id,defender.level().getGameTime()*4+1,true));
                        com.efkrdnz.magical.magic.MagicCounterService.respond(defender,threat.getId());
                        helper.assertTrue(run.combat.combination()==combo&&!run.combat.combinationName().isEmpty(),"Parrying one strike must preserve the combination and its follow-ups");
                    }
                }
                if(stage[0]==0 && run.combat.completedCombinations>=3) {
                    helper.assertTrue(combinations.containsAll(java.util.List.of("absent_procession","between_heartbeats","earth_remembers")),"Sleeping must use all three moving combinations");
                    helper.assertTrue(player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(shrine))>300,"Overworld combat must not pull the player back");
                    helper.assertTrue(run.body.distanceTo(player)<64,"Boss must pursue distant participants");
                    helper.assertTrue(run.combat.parries>0&&staleChecked[0],"Live attacks must use the existing contextual QTE service");
                    helper.assertTrue(run.combat.openings>0,"Three clean finishers must expose the body");
                    var before=run.body.position(); run.body.teleportTo(before.x+100,before.y,before.z);
                    helper.assertTrue(run.body.position().equals(before),"External teleport must not relocate the boss");
                    helper.assertTrue(UnwakingCapabilities.refuseControl(player,run.body),"Sealing the boss must be refused before spending resources");
                    helper.assertTrue(UnwakingCapabilities.refuseTravel(player,com.efkrdnz.magical.magic.SpacePocketService.POCKET_DIMENSION),"Cross-dimension skills must fail before resource expenditure");
                    run.body.hurtServer(player.serverLevel(),player.damageSources().playerAttack(player),10000000);
                    helper.assertTrue(run.phase==UnwakingPhase.AWAKENING,"Damage must clamp at 70 percent and awaken the god");
                    stage[0]=1;
                }
                if(stage[0]==1 && player.level().dimension().equals(com.efkrdnz.magical.magic.ChronosDimensionService.CHRONOS_DIMENSION)) {
                    service.readyForDomain(player,new com.efkrdnz.magical.network.UnwakingReadyPayload(run.id,run.revision));
                    helper.assertTrue(originalReturn.equals(UnwakingRecoveryData.get(server).point(player.getUUID())),"Transfer must preserve the original safe return point");
                    if(player.getX()<300) player.teleportTo(424,160,8);
                    if(run.combat.completedCombinations>=7 && run.combat.assault.completed>=2 && run.combat.assault.rewarding(service.now())) {
                        helper.assertTrue(run.phase==UnwakingPhase.AWAKE,"All four domain combinations must complete normally");
                        helper.assertTrue(observed.containsAll(java.util.List.of(UnwakingHazard.Kind.WORLD_CUT,UnwakingHazard.Kind.HORIZON_HAND,UnwakingHazard.Kind.LAW_FRONT,UnwakingHazard.Kind.FALLEN_STAR)),"All large domain geometries must actually be scheduled");
                        helper.assertTrue(player.getX()>300,"Domain flight must remain unrestricted beyond the old radius");
                        verifySkillAndGluttonyCounters(helper,player,run.body);
                        run.body.setHealth(run.body.getMaxHealth()*.401F);
                        float health=run.body.getHealth();
                        run.body.hurtServer(player.serverLevel(),player.damageSources().playerAttack(player),1000);
                        helper.assertTrue(run.phase==UnwakingPhase.AWAKE && run.body.getHealth()==health-1000,"An earned reward must take 100% damage through the 40% threshold");
                        helper.assertTrue(run.combat.snapshot(player.getUUID()).isEmpty(),"Reward must have no lingering hazards");
                        rewardStarted[0]=service.now();
                        helper.assertTrue(run.recoveryUntil-service.now()==400,"Reward must start with exactly twenty fully attackable seconds");
                        stage[0]=2;
                    }
                }
                if(run.combat.assault.active()) {
                    var assault=run.combat.assault.state(player.getUUID());
                    if(assault.active(service.now())) passages.add(assault.passage(service.now()));
                    helper.assertTrue(UnwakingAssaultState.rotation(assault.order())==UnwakingAssaultState.Rotation.CALM,"Phase two must run the calm rotation");
                    if(assault.active(service.now())&&assault.passage(service.now())==UnwakingAssaultState.Passage.VORTEX) {
                        helper.assertTrue(!service.movementLocked(player),"Beam shower must leave movement available");
                        helper.assertTrue(frames.stream().allMatch(h->h.kind()==UnwakingHazard.Kind.VORTEX_BEAM),"Vortex passage must contain only beams, with no forced-flow sheets");
                    }
                    helper.assertTrue(run.body.isInvisible()&&!run.body.isPickable(),"Assault must dissolve its body");
                    float health=run.body.getHealth(); run.body.hurtServer(player.serverLevel(),player.damageSources().playerAttack(player),10000000);
                    helper.assertTrue(health==run.body.getHealth(),"The domain must be untargetable during its defensive passage");
                    if(assault.locked(service.now())&&!clockChecks[0]) {
                        float yaw=player.getYRot(),pitch=player.getXRot();
                        player.setPos(assault.lockAnchor().add(5,2,0)); service.enforceMovement(player);
                        helper.assertTrue(player.position().equals(assault.lockAnchor()),"Clock must enforce its position anchor");
                        helper.assertTrue(yaw==player.getYRot()&&pitch==player.getXRot(),"Clock must preserve rotation");
                        helper.assertTrue(UnwakingCapabilities.refuseMovement(player),"Movement casts must be refused while frozen");
                        clockChecks[0]=true;
                    }
                }
                if(stage[0]==2 && service.now()<rewardStarted[0]+400) {
                    helper.assertTrue(run.phase==UnwakingPhase.AWAKE && !run.body.isInvisible() && !run.combat.assault.active(),"Threshold crossing must not truncate the full reward");
                    helper.assertTrue(run.combat.snapshot(player.getUUID()).isEmpty(),"No attacks may leak into the reward");
                    if((service.now()-rewardStarted[0])%40==20) {
                        float health=run.body.getHealth(); boolean accepted=run.body.hurtServer(player.serverLevel(),player.damageSources().playerAttack(player),10);
                        helper.assertTrue(run.body.getHealth()==health-10,"Reward must keep accepting normal damage below forty percent: before="+health+", after="+run.body.getHealth()+", accepted="+accepted+", invulnerability="+run.body.invulnerableTime+", age="+(service.now()-rewardStarted[0]));
                    }
                }
                if(stage[0]==2 && run.phase==UnwakingPhase.FINAL) {
                    helper.assertTrue(passages.size()==UnwakingAssaultState.Rotation.CALM.size() && clockChecks[0] && clockChecks[1],"Every passage of the rotation, translation locks, and correctly aimed clock parries must execute");
                    // Phase three opens awake. enter(FINAL) used to preload the assault counter to
                    // its own threshold, so the first tick of the phase dissolved the body for a
                    // thousand two hundred ticks - which from the floor is a boss still stunned.
                    helper.assertTrue(!run.combat.assault.active(),"Phase three must not open with an assault already running");
                    helper.assertTrue(!run.body.isInvisible(),"Phase three must open with a visible body");
                    helper.assertTrue(run.body.isPickable(),"Phase three must open with a body that can be hit");
                    helper.assertTrue(UnwakingPhase.FINAL.enraged(),"Phase three must select the enraged rotation");
                    helper.assertTrue(observed.contains(UnwakingHazard.Kind.VORTEX_BEAM),"The vortex must schedule its damaging beam shower");
                    helper.assertTrue(observed.containsAll(java.util.List.of(UnwakingHazard.Kind.FIRMAMENT_GUILLOTINE,UnwakingHazard.Kind.SIXFOLD_BURIAL,UnwakingHazard.Kind.NULL_HORIZON)),"Every new sky-cut attack must execute before the passage ends");
                    stage[0]=3;
                }
                if(stage[0]==3 && run.combat.assault.completed>=3 && run.combat.assault.rewarding(service.now())) {
                    helper.assertTrue(!run.body.isInvisible()&&run.body.isPickable(),"The next assault must restore an attackable body");
                    com.efkrdnz.magical.MagicalMod.LOGGER.info("Open combat integration: {}",service.status());
                    run.body.hurtServer(player.serverLevel(),player.damageSources().playerAttack(player),10000000);
                    helper.assertTrue(run.phase==UnwakingPhase.DEATH&&run.body.getHealth()==1,"Lethal must enter the protected ending exactly once"); stage[0]=4;
                }
            });
        });
    }

    private static void verifySkillAndGluttonyCounters(GameTestHelper helper, net.minecraft.server.level.ServerPlayer player, UnwakingGodEntity boss) {
        var state=player.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE);
        var skill=com.efkrdnz.magical.magic.MagicContent.MANIPULATE_SPACE;
        state.unlock(skill.id()); state.setMana(state.maxMana());
        int before=state.mana(); boolean[] accepted={false};
        var windows=new UnwakingCounterWindows();
        windows.offer(90001,0,player,boss,com.efkrdnz.magical.magic.MagicContent.CREASE_FOLD,12,()->accepted[0]=true,player.getEyePosition());
        var marker=player.serverLevel().getEntitiesOfClass(UnwakingCounterThreatEntity.class,player.getBoundingBox().inflate(100)).stream()
                .filter(t->com.efkrdnz.magical.magic.MagicCounterService.hasActivePrompt(player,t)).findFirst().orElseThrow();
        com.efkrdnz.magical.magic.MagicCounterService.respond(player,marker.getId());
        helper.assertTrue(accepted[0]&&state.mana()<before&&state.isSkillOnCooldown(skill.id()),"Opposite-attribute counters must spend mana and apply their existing cooldown");
        windows.clear(); accepted[0]=false;
        state.unlockPassive(com.efkrdnz.magical.magic.MagicPassiveContent.SIN_GLUTTONY.id()); state.setMana(0);
        windows.offer(90002,0,player,boss,com.efkrdnz.magical.magic.MagicContent.DIVINE_DIVIDER,12,()->accepted[0]=true,player.getEyePosition());
        marker=player.serverLevel().getEntitiesOfClass(UnwakingCounterThreatEntity.class,player.getBoundingBox().inflate(100)).stream()
                .filter(t->com.efkrdnz.magical.magic.MagicCounterService.hasActivePrompt(player,t)).findFirst().orElseThrow();
        com.efkrdnz.magical.magic.MagicCounterService.respond(player,marker.getId());
        helper.assertTrue(accepted[0]&&state.mana()>0&&state.gluttonyCooldownTicks()>0,"Gluttony must devour boss threats through the normal counter path");
        windows.clear();
        var protection=com.efkrdnz.magical.magic.MagicContent.AEGIS_ULTIMATE_PROTECTION;
        var stats=protection.resolve(state.tuningFor(protection.id()));
        state.setMana(state.maxMana());
        com.efkrdnz.magical.entity.SovereignAegisEntity.toggleUltimate(player.serverLevel(),player,stats);
        float personal=com.efkrdnz.magical.entity.SovereignAegisEntity.rewriteIncomingDamage(player,new UnwakingDamageSource(boss,UnwakingHazard.Kind.PROCESSION_CUT),50);
        float domain=com.efkrdnz.magical.entity.SovereignAegisEntity.rewriteIncomingDamage(player,new UnwakingDamageSource(boss,UnwakingHazard.Kind.WORLD_CUT),50);
        helper.assertTrue(personal==50&&domain==50,"All god attacks must now carry apex rank through Ultimate Protection");
        if(com.efkrdnz.magical.entity.SovereignAegisEntity.hasUltimate(player)) com.efkrdnz.magical.entity.SovereignAegisEntity.toggleUltimate(player.serverLevel(),player,stats);
        helper.assertTrue(!com.efkrdnz.magical.magic.MagicCounterService.hasActivePrompt(player),"Canceled windows must release prompt ownership");
        state.clearCooldowns(); state.setGluttonyCooldown(100); state.setMana(state.maxMana());
        int mana=state.mana(); float yaw=player.getYRot(),pitch=player.getXRot();
        var target=player.getEyePosition().add(player.getLookAngle().scale(-12)); boolean[] aimed={false};
        windows.offerAimed(90003,player,boss,UnwakingHazard.Kind.FIRMAMENT_FRAGMENT,12,()->aimed[0]=true,target,()->{},player.getEyePosition());
        marker=player.serverLevel().getEntitiesOfClass(UnwakingCounterThreatEntity.class,player.getBoundingBox().inflate(100)).stream()
                .filter(t->com.efkrdnz.magical.magic.MagicCounterService.hasActivePrompt(player,t)).findFirst().orElseThrow();
        // Isolate aim/resource validation; real timed key edges are exercised throughout the encounter.
        marker.attack(null);
        com.efkrdnz.magical.magic.MagicCounterService.respond(player,marker.getId());
        helper.assertTrue(!aimed[0]&&state.mana()==mana,"Wrong aim must not accept the strike or spend counter resources"); windows.clear();
        var toward=target.subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-toward.x,toward.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(toward.y,Math.sqrt(toward.x*toward.x+toward.z*toward.z))));
        windows.offerAimed(90004,player,boss,UnwakingHazard.Kind.CLOCK_STRIKE,12,()->aimed[0]=true,target,()->{},player.getEyePosition());
        marker=player.serverLevel().getEntitiesOfClass(UnwakingCounterThreatEntity.class,player.getBoundingBox().inflate(100)).stream()
                .filter(t->com.efkrdnz.magical.magic.MagicCounterService.hasActivePrompt(player,t)).findFirst().orElseThrow();
        marker.attack(null);
        com.efkrdnz.magical.magic.MagicCounterService.respond(player,marker.getId());
        helper.assertTrue(aimed[0],"Correct aim must accept the existing contextual counter"); windows.clear();
        player.setYRot(yaw); player.setXRot(pitch);

    }

    private static void lookAt(net.minecraft.server.level.ServerPlayer player,net.minecraft.world.phys.Vec3 target) {
        var d=target.subtract(player.getEyePosition());
        player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
    }

    @GameTest(template="unwaking_empty",timeoutTicks=180,batch="unwaking_armor")
    public static void protectionNetheriteCannotEraseDivineDamage(GameTestHelper helper) {
        var server=helper.getLevel().getServer();
        var cookie=net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"unwaking-armor"),false);
        var player=new net.minecraft.server.level.ServerPlayer(server,helper.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.setNoGravity(true);
        player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2,4,2)));
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        var enchant=player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
        var slots=new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET};
        var items=new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.NETHERITE_HELMET,net.minecraft.world.item.Items.NETHERITE_CHESTPLATE,net.minecraft.world.item.Items.NETHERITE_LEGGINGS,net.minecraft.world.item.Items.NETHERITE_BOOTS};
        for(int i=0;i<slots.length;i++) { var stack=new net.minecraft.world.item.ItemStack(items[i]); stack.enchant(enchant,4); player.setItemSlot(slots[i],stack); }
        var body=helper.spawn(MagicalEntities.UNWAKING_GOD.get(),new BlockPos(2,2,2));
        body.bind(helper.absolutePos(new BlockPos(2,1,2)),null);
        helper.onEachTick(player::doTick);
        helper.runAtTickTime(100,()-> {
            var state=player.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE);
            helper.assertTrue(player.getArmorValue()==20,"Fixture must wear a complete netherite armor set");
            float raw=UnwakingHazard.Kind.CLOCK_STRIKE.damageFor(20); // Conservative base: ordinary 20 HP target.
            var source=new UnwakingDamageSource(body,UnwakingHazard.Kind.CLOCK_STRIKE);
            state.setBarrier(state.maxBarrier()); player.setHealth(1000); player.invulnerableTime=0;
            player.hurtServer(player.serverLevel(),source,raw);
            float unblocked=1000-player.getHealth();
            helper.assertTrue(unblocked>20,"Protection IV plus a full magic barrier must not make an unblocked god hit tankable");
            // Blocking is gone, and UnwakingDamageSource forces BYPASSES_SHIELD so a raised vanilla
            // shield cannot quietly put it back. Same hit, shield up, facing the source: same damage.
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
            player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
            state.setBarrier(state.maxBarrier()); player.setHealth(1000); player.invulnerableTime=0;
            player.hurtServer(player.serverLevel(),source,raw);
            float shielded=1000-player.getHealth();
            player.stopUsingItem();
            helper.assertTrue(Math.abs(shielded-unblocked)<0.01F,"A raised shield must not reduce a Supreme Deity attack");
            MagicalMod.LOGGER.info("Supreme Deity armor verification: raw={}, Protection IV netherite + barrier={}, with shield raised={}",raw,unblocked,shielded);
            server.getPlayerList().remove(player); helper.succeed();
        });
    }

    @GameTest(template = "unwaking_empty", timeoutTicks = 60, batch = "unwaking")
    public static void whiteBipedHealthAndDormantHover(GameTestHelper helper) {
        BlockPos localShrine = new BlockPos(2, 1, 2);
        helper.setBlock(localShrine, MagicalBlocks.UNWAKING_SHRINE.get());
        UnwakingGodEntity body = helper.spawn(MagicalEntities.UNWAKING_GOD.get(), new BlockPos(2, 3, 2));
        body.bind(helper.absolutePos(localShrine), null);
        body.encounterHealth(UnwakingEncounterService.maximumHealth(4));
        double height = body.getY();
        helper.assertTrue(body.getMaxHealth() == UnwakingEncounterService.maximumHealth(4) && body.getHealth() == body.getMaxHealth(), "Boss-local max health must exceed vanilla's 1024 cap without truncation");
        helper.assertTrue(Math.abs(body.getBbWidth() - 0.6F) < 0.001 && Math.abs(body.getBbHeight() - 1.8F) < 0.001, "Boss must have normal player proportions");
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(body.isAlive() && Math.abs(body.getY() - height) < 0.01, "Dormant shrine boss must persist and hover");
            helper.assertTrue(body.phase() == UnwakingPhase.DORMANT, "Dormant boss must not start itself");
            helper.succeed();
        });
    }

    @GameTest(template = "unwaking_empty", timeoutTicks = 40, batch = "unwaking")
    public static void orphanedEncounterBodyIsRemoved(GameTestHelper helper) {
        UnwakingGodEntity body = helper.spawn(MagicalEntities.UNWAKING_GOD.get(), new BlockPos(2, 2, 2));
        body.bind(helper.absolutePos(new BlockPos(2, 1, 2)), java.util.UUID.randomUUID());
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(body.isRemoved(), "A body from a missing/crashed run must not survive into a new encounter");
            helper.succeed();
        });
    }

    @GameTest(template = "unwaking_empty", timeoutTicks = 60, batch = "unwaking_recovery")
    public static void recoveryRecordIsVerifiedOnDisk(GameTestHelper helper) throws java.io.IOException {
        var server = helper.getLevel().getServer();
        var data = UnwakingRecoveryData.get(server);
        var player = java.util.UUID.randomUUID();
        var point = new UnwakingRecoveryData.ReturnPoint(java.util.UUID.randomUUID(), helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)), 35, -10, 0.075F, true);
        data.remember(player, point);
        data.flushVerified(server);
        helper.assertTrue(point.equals(data.point(player)), "Verified save must retain the complete return marker");
        data.forget(player);
        data.flushVerified(server);
        helper.assertTrue(data.point(player) == null, "Completed return must be cleared on disk");
        helper.succeed();
    }
}
