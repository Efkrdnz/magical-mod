package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.boss.unwaking.*;
import com.efkrdnz.magical.magic.*;
import com.efkrdnz.magical.network.*;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.HashSet;
import java.util.Set;

/** Compiled only by scripts/test-unwaking-client.ps1 into its isolated development run. */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class UnwakingClientProbe {
    private static final Set<String> captured = new HashSet<>();
    private static int menuTicks;
    private static int ticks, readyAt, startAt, finishAt;
    private static boolean setup, domain, inputChecked, loadoutChecked;
    private static boolean frozenLoadout, frozenLoadoutConfirmed, aimSent, clockRotationChecked;
    private static Vec3 frozenPosition;
    private static float frozenYaw;
    private static int frozenTicks;
    private static String pendingCapture;
    private static int cameraSettledAt;
    private static boolean beamShowerChecked;
    private static volatile boolean aimedCounterConfirmed;
    private static Vec3 showerPosition;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("magical.unwaking.clientProbe")) return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null) {
            if(++menuTicks%100==0 && mc.screen!=null) {
                MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE MENU: {} / {}",mc.screen.getClass().getName(),mc.screen.getTitle().getString());
                Screenshot.grab(mc.gameDirectory,"startup-"+menuTicks+".png",mc.getMainRenderTarget(),message->{});
            }
            return;
        }
        ticks++;
        if(mc.screen!=null) mc.setScreen(null);
        if(!setup && ticks>40) {
            setup=true; readyAt=ticks;
            mc.options.pauseOnLostFocus=false;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            mc.options.renderDistance().set(20); mc.options.simulationDistance().set(6);
            var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
            server.execute(()-> {
                server.tickRateManager().setTickRate(20);
                var p=server.getPlayerList().getPlayer(id); p.setGameMode(GameType.CREATIVE);
                var state=p.getData(MagicalAttachments.MAGIC_STATE);
                state.chooseRace(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("magical","human"));
                state.createLoadout("Probe alternate"); state.sync(p);
                BlockPos shrine=new BlockPos(1000,80,1000);
                server.overworld().getChunkAt(shrine);
                for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) server.overworld().setBlock(shrine.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
                p.teleportTo(server.overworld(),1003.5,80,1000.5,java.util.Set.of(),180,0,true);
                p.getAbilities().flying=true; p.onUpdateAbilities();
                UnwakingEncounterService.get(server).createShrine(p,shrine);
            });
        }
        if(setup&&!inputChecked&&ticks-readyAt>60) {
            inputChecked=true;
            check(!ClientCounterPrompt.tickAndConsumeWheel(mc,false),"Idle encounter must not consume B");
            MagicWheelOverlay.tick(mc,true); check(MagicWheelOverlay.isActive(),"B must open loadouts outside a QTE");
            MagicWheelOverlay.handleScroll(-1); MagicWheelOverlay.tick(mc,false);
            ClientCounterPrompt.receive(new CounterPromptPayload(2000000000,MagicContent.DIVINE_DIVIDER.id(),MagicCounterService.FORCED_COUNTER,0,8,"attack.magical.unwaking.decree"));
            check(ClientCounterPrompt.tickAndConsumeWheel(mc,true),"A live QTE must consume B");
            MagicWheelOverlay.tick(mc,false); check(!MagicWheelOverlay.isActive(),"A counter must not open loadouts");
            ClientCounterPrompt.tickAndConsumeWheel(mc,false);
            check(!ClientCounterPrompt.tickAndConsumeWheel(mc,true),"B must be released to loadouts after answering");
            MagicWheelOverlay.tick(mc,true); check(MagicWheelOverlay.isActive(),"Loadouts must reopen after a QTE");
            MagicWheelOverlay.tick(mc,false); ClientCounterPrompt.tickAndConsumeWheel(mc,false);
            MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE: contextual B priority and loadout release passed");
            mc.gui.getChat().clearMessages(true);
            startAt=ticks;
        }
        if(inputChecked&&!loadoutChecked&&ticks-startAt>30) {
            loadoutChecked=true;
            check(ClientMagicState.get().activeLoadoutIndex()==1,"The server must accept an ordinary loadout change");
            MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE: loadout change confirmed by server sync");
            var id=mc.player.getUUID(); var server=mc.getSingleplayerServer();
            server.execute(()-> {var p=server.getPlayerList().getPlayer(id); var service=UnwakingEncounterService.get(server);service.ready(p,new BlockPos(1000,80,1000));service.debugPhase(p,false);});
        }
        var hazards=ClientUnwakingEncounter.hazards();
        for(var h:hazards) {
            int age=h.age(ClientUnwakingEncounter.estimatedTick());
            if (age==8) mc.gui.getChat().clearMessages(true);
            String name=switch(h.kind()) {
                case PROCESSION_CUT -> "01-sleeping";
                case WORLD_CUT -> "02-sky-plane";
                case HORIZON_HAND -> "03-horizon-hand";
                case LAW_FRONT -> "04-white-world";
                case FALLEN_STAR -> "05-fallen-star";
                case FIRMAMENT_GUILLOTINE -> "10-firmament-guillotine";
                case SIXFOLD_BURIAL -> "11-sixfold-burial";
                case NULL_HORIZON -> "12-null-horizon";
                default -> null;
            };
            if(name==null||captured.contains(name)) continue;
            Vec3 focus=switch(h.kind()) {
                case WORLD_CUT -> UnwakingOpenGeometry.planeCenter(h,age);
                case FIRMAMENT_GUILLOTINE, SIXFOLD_BURIAL, NULL_HORIZON -> UnwakingSkyGeometry.center(h,age);
                case FALLEN_STAR -> UnwakingOpenGeometry.starCenter(h,age);
                case HORIZON_HAND -> h.origin().add(UnwakingOpenGeometry.hand(h,age).scale(100));
                default -> h.origin();
            };
            Vec3 d=focus.subtract(mc.player.getEyePosition());
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
            int captureAge=UnwakingSkyGeometry.attack(h.kind())?h.kind().impact+12:h.kind()==UnwakingHazard.Kind.LAW_FRONT?50:24;
            if(age>=captureAge) {
                captured.add(name);
                Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),message->MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE: {}",message.getString()));
            }
        }
        var assault=ClientUnwakingEncounter.assault(); long now=ClientUnwakingEncounter.estimatedTick();
        if(assault.active(now)) {
            int age=assault.passageAge(now);
            String capture=null; Vec3 focus=null;
            switch(assault.passage(now)) {
                case SKY -> { if(age>=70&&age<140) {check(ClientUnwakingEncounter.scenery()==0,"Open sky cut must hide every ambient shape"); capture="06-sky-wound"; focus=assault.wound();} }
                case VORTEX -> {
                    if(age>=40&&age<60) {
                        if(showerPosition==null) showerPosition=mc.player.position();
                        check(mc.player.position().distanceTo(showerPosition)<.12,"Vortex must not displace a stationary player: anchor="+showerPosition+", current="+mc.player.position()+", velocity="+mc.player.getDeltaMovement()+", input="+mc.player.input.keyPresses);
                        check(!assault.locked(now),"Beam shower must leave movement unlocked");
                    }
                    if(age>=97&&age<102) {
                        capture="07-vortex"; focus=assault.vortex();
                        check(hazards.stream().anyMatch(h->h.kind()==UnwakingHazard.Kind.VORTEX_BEAM&&h.contact(h.age(now))>=0),"Vortex must emit real damaging beams");
                        check(hazards.stream().noneMatch(h->h.kind()==UnwakingHazard.Kind.WORLD_CUT),"No sheet may overlap the shower");
                        beamShowerChecked=showerPosition!=null;
                    }
                }
                case CLOCK -> {
                    if(age>=24&&age<38) {capture="08-frozen-dial"; focus=assault.anchor().add(0,-40,0);}
                    if(assault.locked(now)&&age>=43&&age<120) {
                        if(frozenPosition==null) { frozenPosition=mc.player.position(); frozenYaw=mc.player.getYRot(); }
                        check(mc.player.position().distanceTo(frozenPosition)<.12,"Clock must hold position while movement is pressed");
                        mc.options.keyUp.setDown(age<85); frozenTicks++;
                        if(age<48) mc.player.setYRot(mc.player.getYRot()+6);
                        if(age>=48&&!clockRotationChecked) {check(Math.abs(mc.player.getYRot()-frozenYaw)>3,"Clock must leave rotation free"); clockRotationChecked=true;}
                        if(!frozenLoadout&&age<51) {
                            check(!ClientCounterPrompt.tickAndConsumeWheel(mc,false),"Clock warmup must leave B available between prompts");
                            MagicWheelOverlay.tick(mc,true); check(MagicWheelOverlay.isActive(),"B must open loadouts during a time lock between prompts");
                            MagicWheelOverlay.handleScroll(-1); MagicWheelOverlay.tick(mc,false); frozenLoadout=true;
                        }
                        var target=hazards.stream().filter(h->h.kind()==UnwakingHazard.Kind.CLOCK_STRIKE).findFirst().orElse(null);
                        if(target!=null&&age>=48) {
                            focus=target.origin(); look(mc,focus);
                            if(age>=58&&age<60&&!aimSent) {
                                ClientCounterPrompt.tickAndConsumeWheel(mc,false);
                                check(ClientCounterPrompt.tickAndConsumeWheel(mc,true),"An aimed clock QTE must use the ordinary B response");
                                ClientCounterPrompt.tickAndConsumeWheel(mc,false); aimSent=true;
                                var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
                                server.execute(()-> { var status=UnwakingEncounterService.get(server).status(); MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE AIM: {}",status); });
                            }
                            if(age>=52&&age<60) capture="09-aimed-clock";
                        }
                        if(age>=70&&frozenLoadout&&!frozenLoadoutConfirmed) {
                            check(ClientMagicState.get().activeLoadoutIndex()==0,"Server must accept a loadout change during the time lock");
                            frozenLoadoutConfirmed=true;
                            MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE: frozen movement, free rotation, and in-lock loadout change verified");
                            var server=mc.getSingleplayerServer();
                            server.execute(()-> {
                                String status=UnwakingEncounterService.get(server).status();
                                var counters=java.util.regex.Pattern.compile("hits/blocks/parries [0-9]+/[0-9]+/([0-9]+)").matcher(status);
                                boolean accepted=counters.find()&&Integer.parseInt(counters.group(1))>0;
                                mc.execute(()-> {check(accepted,"The server must accept the precisely aimed clock key edge: "+status); aimedCounterConfirmed=true;});
                            });
                        }
                    }
                    if(age>=120) mc.options.keyUp.setDown(false);
                }
            }
            if(capture!=null&&!captured.contains(capture)) {
                if(focus!=null) look(mc,focus);
                if(!capture.equals(pendingCapture)) {pendingCapture=capture; cameraSettledAt=ticks;}
                if(ticks-cameraSettledAt>=2) {
                    captured.add(capture);
                    Screenshot.grab(mc.gameDirectory,capture+".png",mc.getMainRenderTarget(),message->MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE: {}",message.getString()));
                }
            }
        }
        if(captured.contains("01-sleeping")&&!domain) {
            domain=true; var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(()->UnwakingEncounterService.get(server).debugPhase(server.getPlayerList().getPlayer(id),true));
        }
        if(captured.size()==12&&frozenLoadoutConfirmed&&clockRotationChecked&&aimedCounterConfirmed&&frozenTicks>40&&beamShowerChecked&&finishAt==0) {
            finishAt=ticks;
            MagicalMod.LOGGER.info("UNWAKING CLIENT PROBE: all twelve live attack views captured");
        }
        if(finishAt>0&&ticks-finishAt>50) mc.stop();
        if(ticks>5400) { MagicalMod.LOGGER.error("UNWAKING CLIENT PROBE: timed out, captured {}",captured); mc.stop(); }
    }
    private static void look(Minecraft mc,Vec3 focus) {
        Vec3 d=focus.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
        mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
    }
    private static void check(boolean condition,String message) {
        if(!condition) throw new IllegalStateException("UNWAKING CLIENT PROBE FAILED: "+message);
    }
}
