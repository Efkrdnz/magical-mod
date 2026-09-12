package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.boss.unwaking.UnwakingPresentation;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultState;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultGeometry;
import com.efkrdnz.magical.client.renderer.UnwakingAssaultRenderer;
import com.efkrdnz.magical.client.renderer.UnwakingFxRenderer;
import com.efkrdnz.magical.client.renderer.UnwakingOpenRenderer;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.efkrdnz.magical.boss.unwaking.UnwakingPhase;
import com.efkrdnz.magical.boss.unwaking.UnwakingHazard;
import com.efkrdnz.magical.client.renderer.UnwakingHazardRenderer;
import com.efkrdnz.magical.magic.ChronosDimensionService;
import com.efkrdnz.magical.network.UnwakingReadyPayload;
import com.efkrdnz.magical.network.UnwakingSnapshotPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientUnwakingEncounter {
    private static UnwakingSnapshotPayload snapshot;
    private static boolean readySent;
    private static long receivedAt;
    private static java.util.UUID inputRun;
    private static long inputSequence, inputSentAt;
    private static boolean inputHeld;
    private static long quietSince = -1;
    private static float paletteFrom, paletteTarget;
    private static long paletteStart;
    private static final java.util.Set<java.util.UUID> ENDED_RUNS = new java.util.LinkedHashSet<>();

    private ClientUnwakingEncounter() {}

    /** Observe B without consuming it: the wheel still owns every press outside a live prompt. */
    public static void observeCounterKey(Minecraft minecraft,boolean held) {
        if(snapshot==null||minecraft.player==null||minecraft.level==null) { inputRun=null; return; }
        long now=minecraft.level.getGameTime();
        boolean newRun=!snapshot.run().equals(inputRun);
        if(newRun||held!=inputHeld||held&&now-inputSentAt>=10) {
            if(newRun) { inputRun=snapshot.run(); inputSequence=0; }
            PacketDistributor.sendToServer(new com.efkrdnz.magical.network.UnwakingGuardPayload(inputRun,++inputSequence,held));
            inputHeld=held; inputSentAt=now;
        }
    }

    public static void handle(UnwakingSnapshotPayload payload) {
        if (ENDED_RUNS.contains(payload.run())) return;
        if (payload.ended()) {
            if (snapshot != null && snapshot.run().equals(payload.run()) && payload.revision() >= snapshot.revision()) clear();
            return;
        }
        if (snapshot != null && snapshot.run().equals(payload.run()) && payload.revision() <= snapshot.revision()) return;
        if (snapshot == null || !snapshot.run().equals(payload.run())) {
            readySent = false;
        }
        if (payload.quiet() && (snapshot == null || !snapshot.quiet())) quietSince = payload.tick();
        if (!payload.quiet()) quietSince = -1;
        float nextPalette = payload.assault().active(payload.tick()) ? payload.assault().palette(payload.tick()) : payload.phase() == UnwakingPhase.TRIAL_BREATH
                || payload.combinationName().equals("white_becomes_law") || payload.combinationName().equals("last_law") ? 1 : 0;
        if (nextPalette != paletteTarget) {
            paletteFrom = inversion(); paletteTarget = nextPalette; paletteStart = payload.tick();
        }
        snapshot = payload;
        receivedAt = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
    }

    /** Encounter synchronization never consumes or rebinds the loadout key. */
    public static void tick(Minecraft minecraft) {
        if (snapshot == null) return;
        if (minecraft.player == null || minecraft.getConnection() == null) { clear(); return; }
        if (!readySent && minecraft.level != null && minecraft.level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION)
                && snapshot.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION.location())) {
            PacketDistributor.sendToServer(new UnwakingReadyPayload(snapshot.run(), snapshot.revision()));
            readySent = true;
        }
    }

    @SubscribeEvent public static void movementBefore(net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre event) {
        Minecraft mc=Minecraft.getInstance();
        if(event.getEntity()!=mc.player||!ownsDomain()) return;
        var state=assault(); long now=estimatedTick();
        if(state.locked(now)) { mc.player.setPos(state.lockAnchor()); mc.player.setDeltaMovement(Vec3.ZERO); mc.player.fallDistance=0; }
    }
    @SubscribeEvent public static void movementAfter(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(event.getEntity()==mc.player&&ownsDomain()&&assault().locked(estimatedTick())) {
            mc.player.setPos(assault().lockAnchor()); mc.player.setDeltaMovement(Vec3.ZERO); mc.player.fallDistance=0;
        }
    }

    public static void renderHud(GuiGraphics graphics, Minecraft minecraft) {
        if (snapshot == null || minecraft.options.hideGui || minecraft.player == null) return;
        int cx = graphics.guiWidth() / 2, y = graphics.guiHeight() - 84;
        long now = estimatedTick();
        int ink = net.minecraft.util.ARGB.lerp(ownsDomain() ? inversion() : 0, 0xFFE9E7E2, 0xFF161821);
        int safe = net.minecraft.util.ARGB.lerp(ownsDomain() ? inversion() : 0, 0xFF8CFFE4, 0xFF24614E);
        if (!snapshot.phase().hiddenBody() && !assault().active(now)) {
            for (int i = 0; i < 3; i++) {
                int x = cx - 18 + i * 14;
                graphics.renderOutline(x, 30, 9, 5, 0xFF69B8A1);
                if (i < snapshot.guardSegments()) graphics.fill(x + 1, 31, x + 8, 34, ink);
            }
        }
        String title = snapshot.combinationName().isEmpty()
                ? "hud.magical.unwaking." + snapshot.phase().name().toLowerCase(java.util.Locale.ROOT)
                : "combo.magical.unwaking." + snapshot.combinationName();
        drawText(graphics,minecraft, Component.translatable(title), cx, 42, ink);
        if(assault().active(now)) drawText(graphics,minecraft,Component.translatable("hud.magical.unwaking.survive_domain",
                (UnwakingAssaultState.ASSAULT_TICKS-assault().age(now)+19)/20),cx,29,safe);
        if (snapshot.phase().trial()) {
            int passage = snapshot.phase() == UnwakingPhase.TRIAL_SKY ? 0 : snapshot.phase() == UnwakingPhase.TRIAL_BREATH ? 1 : 2;
            int progress = (int) Math.clamp((passage * 500 + phaseAge()) * 120 / 1500, 0, 120);
            graphics.fill(cx - 60, 31, cx + 60, 34, 0x80000000);
            graphics.fill(cx - 60, 31, cx - 60 + progress, 34, 0xFF69B8A1);
        }
        if(assault().active(now)&&assault().passage(now)==UnwakingAssaultState.Passage.VORTEX) {
            if(assault().passageAge(now)<40) drawText(graphics,minecraft,Component.translatable("hud.magical.unwaking.beam_shower"),cx,y-42,safe);
        }
        if(assault().locked(now)) {
            drawText(graphics,minecraft,Component.translatable("hud.magical.unwaking.aim_clock"),cx,y-14,safe);
        } else if (snapshot.recoveryTicks() > 0 || snapshot.guardSegments() == 0 && !snapshot.phase().hiddenBody()) {
            drawText(graphics,minecraft, Component.translatable("hud.magical.unwaking.exposed"), cx, y - 14, safe);
        } else {
            var hazard = UnwakingPresentation.dominant(snapshot.hazards(), now);
            if (hazard != null) {
                // A travelling body is a movement check with a Gluttony-only escape hatch, which
                // "Dodge, or counter when prompted" reads as a promise the fight will not keep.
                String defense = UnwakingAssaultGeometry.moving(hazard.kind()) ? "dodge"
                        : hazard.kind().defense.name().toLowerCase(java.util.Locale.ROOT);
                drawText(graphics,minecraft, Component.translatable("hud.magical.unwaking.attack",
                        Component.translatable(hazard.kind().nameKey()), Component.translatable("hud.magical.unwaking.defense." + defense,
                                MagicalKeyMappings.OPEN_WHEEL.getTranslatedKeyMessage())), cx, y - 14, ink);
            }
        }
        // An off-screen arrival remains readable without rotating the player's camera.
        Vec3 destination = snapshot.hazards().stream().filter(h -> h.kind() == UnwakingHazard.Kind.ARRIVAL)
                .map(UnwakingHazard::origin).findFirst().orElseGet(() -> {
                    var boss = minecraft.level.getEntity(snapshot.bossId());
                    return boss == null ? minecraft.player.position() : boss.position();
                });
        var approaching = UnwakingPresentation.dominant(snapshot.hazards(), now);
        boolean environmental = approaching != null && approaching.kind().major();
        if (environmental) destination = switch (approaching.kind()) {
            case FIRMAMENT_GUILLOTINE, SIXFOLD_BURIAL, NULL_HORIZON, FIRMAMENT_FRAGMENT -> com.efkrdnz.magical.boss.unwaking.UnwakingSkyGeometry.source(approaching,approaching.age(now),minecraft.player.getEyePosition());
            case RETURNING_VERDICT -> UnwakingAssaultGeometry.position(approaching,approaching.age(now));
            case FALLEN_STAR -> com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry.starCenter(approaching, approaching.age(now));
            case WORLD_CUT -> com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry.planeCenter(approaching, approaching.age(now));
            default -> approaching.origin();
        };
        Vec3 direction = destination.subtract(minecraft.player.getEyePosition()).normalize(), look = minecraft.player.getLookAngle();
        if (((!snapshot.phase().hiddenBody() && !assault().active(now)) || environmental) && look.dot(direction) < 0.3) {
            double side = look.cross(new Vec3(0, 1, 0)).dot(direction);
            double vertical = look.cross(new Vec3(0, 1, 0)).normalize().cross(look).dot(direction);
            String cue = look.dot(direction) < -0.5 ? "behind" : Math.abs(vertical) > Math.abs(side) ? vertical > 0 ? "up" : "down" : side > 0 ? "right" : "left";
            drawText(graphics,minecraft, Component.translatable("hud.magical.unwaking." + (environmental ? "direction." : "face.") + cue), cx, Math.min(graphics.guiHeight() / 2 + 40,y-60), safe);
        }
        if (snapshot.feedback() > 0) drawText(graphics,minecraft,
                Component.translatable("hud.magical.unwaking.result." + snapshot.feedback()), cx, y - 28, safe);
        if (snapshot.phase() == UnwakingPhase.ORIENTATION) drawText(graphics,minecraft,
                Component.translatable("hud.magical.unwaking.flight", minecraft.options.keyJump.getTranslatedKeyMessage(), minecraft.options.keyShift.getTranslatedKeyMessage()), cx, y - 28, safe);
    }

    private static void drawText(GuiGraphics graphics,Minecraft minecraft,Component text,int x,int y,int color) {
        if(assault().active(estimatedTick())) {
            int half=minecraft.font.width(text)/2+4;
            int background=net.minecraft.util.ARGB.lerp(inversion(),0xDD030407,0xDDF2F2F4);
            graphics.fill(x-half,y-2,x+half,y+11,background);
        }
        graphics.drawCenteredString(minecraft.font,text,x,y,color);
    }

    public static float inversion() {
        return paletteFrom + (paletteTarget - paletteFrom) * UnwakingPresentation.smooth((estimatedTick() - paletteStart) / 40F);
    }
    public static float scenery() { return assault().active(estimatedTick())?assault().scenery(estimatedTick()):snapshot == null ? 1 : UnwakingPresentation.scenery(hazards(), estimatedTick()); }
    public static UnwakingAssaultState assault() { return snapshot==null?UnwakingAssaultState.NONE:snapshot.assault(); }

    public static long estimatedTick() {
        var level=Minecraft.getInstance().level;
        return snapshot==null?0:snapshot.tick()+Math.max(0,level==null?0:level.getGameTime()-receivedAt);
    }
    public static java.util.List<UnwakingHazard> hazards() { return snapshot == null ? java.util.List.of() : snapshot.hazards(); }
    /**
     * True while this client is a live participant standing in the domain.
     *
     * <p>Deliberately wider than {@link #ownsDomain()}: that one gates on {@code phase().inDomain()},
     * which excludes AWAKENING - and AWAKENING is exactly when the transfer happens, so it would
     * leave a few silent seconds between landing and ORIENTATION. A live snapshot plus the
     * dimension is enough, and both ends clean themselves up: the run ending nulls the snapshot,
     * and leaving the dimension fails the second half.
     */
    public static boolean inDomainFight() {
        var level = Minecraft.getInstance().level;
        return snapshot != null && level != null
                && level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION);
    }

    public static boolean ownsDomain() { return snapshot != null && snapshot.phase().inDomain() && Minecraft.getInstance().level != null && Minecraft.getInstance().level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION); }
    public static UnwakingPhase phase() { return snapshot==null?UnwakingPhase.DORMANT:snapshot.phase(); }
    public static long phaseAge() { return snapshot==null?0:estimatedTick()-snapshot.phaseStart(); }
    public static boolean quiet() { return snapshot != null && snapshot.quiet(); }
    public static float quietAmount() { return quietSince < 0 ? 0 : Math.clamp((estimatedTick()-quietSince)/100F,0,1); }
    public static int bodyPose(int entityId) {
        if (snapshot == null || snapshot.bossId() != entityId) return 0;
        if (snapshot.recoveryTicks() > 0) return 3;
        for (var hazard : snapshot.hazards()) if (hazard.visible(hazard.age(estimatedTick()))) {
            if (hazard.recovering(hazard.age(estimatedTick()))) return 3;
            return hazard.kind() == UnwakingHazard.Kind.REFUSAL ? 2 : 1;
        }
        return 0;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.level == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !minecraft.level.dimension().location().equals(snapshot.dimension())) return;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        boolean reduced = snapshot.reducedEffects();
        var orientation = event.getCamera().rotation();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        UnwakingAssaultRenderer.render(pose,buffers,assault(),estimatedTick(),reduced);
        UnwakingAssaultRenderer.scenery(pose,buffers,assault(),estimatedTick(),partial,camera,orientation,reduced);
        // The background flushes before any body, and each body's opaque core flushes before the
        // shell and trail that go over it. None of these write depth, so the batch order is the
        // blend order - leaving one batch open until the end would paint the cores over their own
        // shells.
        buffers.endBatch(MagicalRenderTypes.unwakingSurface());
        for (var hazard : snapshot.hazards()) {
            int age = hazard.age(estimatedTick());
            if (!UnwakingFxRenderer.replaces(hazard.kind()))
                UnwakingOpenRenderer.render(pose, buffers, hazard, age, camera, reduced);
            buffers.endBatch(MagicalRenderTypes.unwakingSurface());
            UnwakingFxRenderer.render(pose, buffers, hazard, age, camera, orientation, partial, reduced);
        }
        if (snapshot.phase() == UnwakingPhase.AWAKENING) UnwakingOpenRenderer.awakening(pose, buffers, camera, phaseAge());
        if (snapshot.hitboxes()) {
            VertexConsumer out = buffers.getBuffer(RenderType.lines());
            for (var hazard : snapshot.hazards()) UnwakingHazardRenderer.render(pose, out, hazard, hazard.age(estimatedTick()), camera);
            buffers.endBatch(RenderType.lines());
        }
        pose.popPose();
    }

    private static void clear() {
        if (snapshot != null) {
            ENDED_RUNS.add(snapshot.run());
            if (ENDED_RUNS.size() > 16) ENDED_RUNS.remove(ENDED_RUNS.iterator().next());
        }
        snapshot = null; readySent = false; quietSince = -1; paletteFrom = 0; paletteTarget = 0; paletteStart = 0;
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); ENDED_RUNS.clear(); }
}
