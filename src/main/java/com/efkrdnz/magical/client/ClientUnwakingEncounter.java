package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultGeometry;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultState;
import com.efkrdnz.magical.boss.unwaking.UnwakingHazard;
import com.efkrdnz.magical.boss.unwaking.UnwakingOpenGeometry;
import com.efkrdnz.magical.boss.unwaking.UnwakingPhase;
import com.efkrdnz.magical.boss.unwaking.UnwakingPresentation;
import com.efkrdnz.magical.boss.unwaking.UnwakingSkyGeometry;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.efkrdnz.magical.client.renderer.UnwakingAssaultRenderer;
import com.efkrdnz.magical.client.renderer.UnwakingFxRenderer;
import com.efkrdnz.magical.client.renderer.UnwakingHazardRenderer;
import com.efkrdnz.magical.client.renderer.UnwakingOpenRenderer;
import com.efkrdnz.magical.magic.ChronosDimensionService;
import com.efkrdnz.magical.network.UnwakingGuardPayload;
import com.efkrdnz.magical.network.UnwakingReadyPayload;
import com.efkrdnz.magical.network.UnwakingSnapshotPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The client's half of the Supreme Deity encounter: the latest snapshot from the server, the clock
 * estimated from it, the world-space renderers, and the HUD cues.
 *
 * <p>The HUD is drawn from an {@link EncounterView} resolved once a client tick. It used to walk
 * the hazard list, run two streams and allocate its strings on every frame; now the frame does
 * nothing but draw what the tick left it. What it draws is unchanged.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientUnwakingEncounter {
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final int ENDED_RUNS_KEPT = 16;
    private static final int PALETTE_CROSSFADE_TICKS = 40;
    private static final int GUARD_RESEND_TICKS = 10;
    private static final int GUARD_PIPS = 3;
    private static final int GUARD_OUTLINE = 0xFF69B8A1;
    private static final int TRIAL_BAR_W = 120;
    private static final int TRIAL_PASSAGE_TICKS = 500;
    private static final int TRIAL_TOTAL_TICKS = 1500;
    private static final int QUIET_RAMP_TICKS = 100;
    private static final String[] CUES = {"behind", "up", "down", "right", "left"};

    private static final Component AIM_CLOCK = Component.translatable("hud.magical.unwaking.aim_clock");
    private static final Component EXPOSED = Component.translatable("hud.magical.unwaking.exposed");
    private static final Component BEAM_SHOWER = Component.translatable("hud.magical.unwaking.beam_shower");
    /** Face and direction cues, five each, built the first time they are needed. */
    private static final Component[] CUE_TEXT = new Component[CUES.length * 2];

    private static UnwakingSnapshotPayload snapshot;
    private static boolean readySent;
    private static long receivedAt;
    private static UUID inputRun;
    private static long inputSequence;
    private static long inputSentAt;
    private static boolean inputHeld;
    private static long quietSince = -1L;
    private static float paletteFrom;
    private static float paletteTarget;
    private static long paletteStart;
    private static final Set<UUID> ENDED_RUNS = new LinkedHashSet<>();

    private static EncounterView view;
    private static String titleKey;
    private static Component title;
    private static int surviveSeconds = -1;
    private static Component survive;
    private static UnwakingHazard defenseHazard;
    private static Component defense;
    private static int feedbackValue;
    private static Component feedback;
    private static Component flight;

    private ClientUnwakingEncounter() {}

    /** Everything the HUD draws, resolved once a tick. Null text means the line is not shown. */
    record EncounterView(
            boolean guards,
            int guardSegments,
            Component title,
            Component survive,
            boolean trial,
            int trialProgress,
            Component beamShower,
            Component defense,
            int defenseColor,
            Component cue,
            Component feedback,
            Component flight,
            int ink,
            int safe,
            int textBackground,
            boolean assaultActive) {}

    /** Observe B without consuming it: the wheel still owns every press outside a live prompt. */
    public static void observeCounterKey(Minecraft minecraft, boolean held) {
        if (snapshot == null || minecraft.player == null || minecraft.level == null) {
            inputRun = null;
            return;
        }
        long now = minecraft.level.getGameTime();
        boolean newRun = !snapshot.run().equals(inputRun);
        if (newRun || held != inputHeld || held && now - inputSentAt >= GUARD_RESEND_TICKS) {
            if (newRun) {
                inputRun = snapshot.run();
                inputSequence = 0L;
            }
            PacketDistributor.sendToServer(new UnwakingGuardPayload(inputRun, ++inputSequence, held));
            inputHeld = held;
            inputSentAt = now;
        }
    }

    public static void handle(UnwakingSnapshotPayload payload) {
        if (ENDED_RUNS.contains(payload.run())) {
            return;
        }
        if (payload.ended()) {
            if (snapshot != null && snapshot.run().equals(payload.run()) && payload.revision() >= snapshot.revision()) {
                clear();
            }
            return;
        }
        if (snapshot != null && snapshot.run().equals(payload.run()) && payload.revision() <= snapshot.revision()) {
            return;
        }
        if (snapshot == null || !snapshot.run().equals(payload.run())) {
            readySent = false;
        }
        if (payload.quiet() && (snapshot == null || !snapshot.quiet())) {
            quietSince = payload.tick();
        }
        if (!payload.quiet()) {
            quietSince = -1L;
        }
        float nextPalette = payload.assault().active(payload.tick()) ? payload.assault().palette(payload.tick())
                : payload.phase() == UnwakingPhase.TRIAL_BREATH
                        || payload.combinationName().equals("white_becomes_law")
                        || payload.combinationName().equals("last_law") ? 1.0F : 0.0F;
        if (nextPalette != paletteTarget) {
            paletteFrom = inversion();
            paletteTarget = nextPalette;
            paletteStart = payload.tick();
        }
        snapshot = payload;
        ClientLevel level = Minecraft.getInstance().level;
        receivedAt = level == null ? 0L : level.getGameTime();
    }

    /** Encounter synchronization never consumes or rebinds the loadout key. */
    public static void tick(Minecraft minecraft) {
        if (snapshot == null) {
            view = null;
            return;
        }
        if (minecraft.player == null || minecraft.getConnection() == null) {
            clear();
            return;
        }
        if (!readySent && minecraft.level != null
                && minecraft.level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION)
                && snapshot.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION.location())) {
            PacketDistributor.sendToServer(new UnwakingReadyPayload(snapshot.run(), snapshot.revision()));
            readySent = true;
        }
        view = minecraft.level == null ? null : buildView(minecraft);
    }

    @SubscribeEvent
    public static void movementBefore(PlayerTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() != minecraft.player || !ownsDomain()) {
            return;
        }
        UnwakingAssaultState state = assault();
        long now = estimatedTick();
        if (state.locked(now)) {
            minecraft.player.setPos(state.lockAnchor());
            minecraft.player.setDeltaMovement(Vec3.ZERO);
            minecraft.player.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent
    public static void movementAfter(PlayerTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getEntity() == minecraft.player && ownsDomain() && assault().locked(estimatedTick())) {
            minecraft.player.setPos(assault().lockAnchor());
            minecraft.player.setDeltaMovement(Vec3.ZERO);
            minecraft.player.fallDistance = 0.0F;
        }
    }

    // ---- the HUD view -----------------------------------------------------------------------------

    private static EncounterView buildView(Minecraft minecraft) {
        long now = estimatedTick();
        UnwakingAssaultState state = assault();
        boolean assaultActive = state.active(now);
        float inversion = ownsDomain() ? inversion() : 0.0F;
        int ink = ARGB.lerp(inversion, 0xFFE9E7E2, 0xFF161821);
        int safe = ARGB.lerp(inversion, 0xFF8CFFE4, 0xFF24614E);
        int textBackground = ARGB.lerp(inversion(), 0xDD030407, 0xDDF2F2F4);
        boolean hiddenBody = snapshot.phase().hiddenBody();

        String key = snapshot.combinationName().isEmpty()
                ? "hud.magical.unwaking." + snapshot.phase().name().toLowerCase(Locale.ROOT)
                : "combo.magical.unwaking." + snapshot.combinationName();
        if (!key.equals(titleKey)) {
            titleKey = key;
            title = Component.translatable(key);
        }

        Component surviveLine = null;
        if (assaultActive) {
            int seconds = (UnwakingAssaultState.ASSAULT_TICKS - state.age(now) + 19) / 20;
            if (seconds != surviveSeconds) {
                surviveSeconds = seconds;
                survive = Component.translatable("hud.magical.unwaking.survive_domain", seconds);
            }
            surviveLine = survive;
        }

        boolean trial = snapshot.phase().trial();
        int trialProgress = 0;
        if (trial) {
            int passage = snapshot.phase() == UnwakingPhase.TRIAL_SKY ? 0 : snapshot.phase() == UnwakingPhase.TRIAL_BREATH ? 1 : 2;
            trialProgress = (int) Math.clamp((passage * TRIAL_PASSAGE_TICKS + phaseAge()) * TRIAL_BAR_W / TRIAL_TOTAL_TICKS, 0, TRIAL_BAR_W);
        }

        Component beamShower = assaultActive && state.passage(now) == UnwakingAssaultState.Passage.VORTEX && state.passageAge(now) < 40
                ? BEAM_SHOWER : null;

        UnwakingHazard dominant = UnwakingPresentation.dominant(snapshot.hazards(), now);
        Component defenseLine;
        int defenseColor;
        if (state.locked(now)) {
            defenseLine = AIM_CLOCK;
            defenseColor = safe;
        } else if (snapshot.recoveryTicks() > 0 || snapshot.guardSegments() == 0 && !hiddenBody) {
            defenseLine = EXPOSED;
            defenseColor = safe;
        } else if (dominant != null) {
            if (dominant != defenseHazard) {
                // A travelling body is a movement check with a Gluttony-only escape hatch, which
                // "Dodge, or counter when prompted" reads as a promise the fight will not keep.
                String defenceKey = UnwakingAssaultGeometry.moving(dominant.kind()) ? "dodge"
                        : dominant.kind().defense.name().toLowerCase(Locale.ROOT);
                defense = Component.translatable("hud.magical.unwaking.attack",
                        Component.translatable(dominant.kind().nameKey()),
                        Component.translatable("hud.magical.unwaking.defense." + defenceKey, MagicalKeyMappings.OPEN_WHEEL.getTranslatedKeyMessage()));
                defenseHazard = dominant;
            }
            defenseLine = defense;
            defenseColor = ink;
        } else {
            defenseLine = null;
            defenseColor = ink;
        }

        Component cue = directionCue(minecraft, now, dominant, hiddenBody, assaultActive);

        Component feedbackLine = null;
        if (snapshot.feedback() > 0) {
            if (snapshot.feedback() != feedbackValue || feedback == null) {
                feedbackValue = snapshot.feedback();
                feedback = Component.translatable("hud.magical.unwaking.result." + feedbackValue);
            }
            feedbackLine = feedback;
        }

        Component flightLine = null;
        if (snapshot.phase() == UnwakingPhase.ORIENTATION) {
            // Rebuilt each tick of the one phase that shows it, so a rebound key is never stale.
            flight = Component.translatable("hud.magical.unwaking.flight",
                    minecraft.options.keyJump.getTranslatedKeyMessage(), minecraft.options.keyShift.getTranslatedKeyMessage());
            flightLine = flight;
        }

        return new EncounterView(!hiddenBody && !assaultActive, snapshot.guardSegments(), title, surviveLine, trial, trialProgress,
                beamShower, defenseLine, defenseColor, cue, feedbackLine, flightLine, ink, safe, textBackground, assaultActive);
    }

    /** An off-screen arrival remains readable without rotating the player's camera. */
    private static Component directionCue(Minecraft minecraft, long now, UnwakingHazard approaching, boolean hiddenBody, boolean assaultActive) {
        Vec3 destination = null;
        for (UnwakingHazard hazard : snapshot.hazards()) {
            if (hazard.kind() == UnwakingHazard.Kind.ARRIVAL) {
                destination = hazard.origin();
                break;
            }
        }
        if (destination == null) {
            Entity boss = minecraft.level.getEntity(snapshot.bossId());
            destination = boss == null ? minecraft.player.position() : boss.position();
        }
        boolean environmental = approaching != null && approaching.kind().major();
        if (environmental) {
            destination = switch (approaching.kind()) {
                case FIRMAMENT_GUILLOTINE, SIXFOLD_BURIAL, NULL_HORIZON, FIRMAMENT_FRAGMENT ->
                        UnwakingSkyGeometry.source(approaching, approaching.age(now), minecraft.player.getEyePosition());
                case RETURNING_VERDICT -> UnwakingAssaultGeometry.position(approaching, approaching.age(now));
                case FALLEN_STAR -> UnwakingOpenGeometry.starCenter(approaching, approaching.age(now));
                case WORLD_CUT -> UnwakingOpenGeometry.planeCenter(approaching, approaching.age(now));
                default -> approaching.origin();
            };
        }
        Vec3 direction = destination.subtract(minecraft.player.getEyePosition()).normalize();
        Vec3 look = minecraft.player.getLookAngle();
        if (!(((!hiddenBody && !assaultActive) || environmental) && look.dot(direction) < 0.3D)) {
            return null;
        }
        double side = look.cross(UP).dot(direction);
        double vertical = look.cross(UP).normalize().cross(look).dot(direction);
        int cue = look.dot(direction) < -0.5D ? 0
                : Math.abs(vertical) > Math.abs(side) ? (vertical > 0.0D ? 1 : 2)
                : (side > 0.0D ? 3 : 4);
        int index = (environmental ? CUES.length : 0) + cue;
        if (CUE_TEXT[index] == null) {
            CUE_TEXT[index] = Component.translatable("hud.magical.unwaking." + (environmental ? "direction." : "face.") + CUES[cue]);
        }
        return CUE_TEXT[index];
    }

    /** The {@code magical:encounter} layer; draws only what {@link #tick} resolved. */
    public static void renderHud(GuiGraphics graphics, Minecraft minecraft) {
        EncounterView current = view;
        if (current == null || snapshot == null || minecraft.options.hideGui || minecraft.player == null) {
            return;
        }
        int cx = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 84;
        if (current.guards()) {
            for (int i = 0; i < GUARD_PIPS; i++) {
                int x = cx - 18 + i * 14;
                graphics.renderOutline(x, 30, 9, 5, GUARD_OUTLINE);
                if (i < current.guardSegments()) {
                    graphics.fill(x + 1, 31, x + 8, 34, current.ink());
                }
            }
        }
        drawText(graphics, minecraft, current, current.title(), cx, 42, current.ink());
        if (current.survive() != null) {
            drawText(graphics, minecraft, current, current.survive(), cx, 29, current.safe());
        }
        if (current.trial()) {
            graphics.fill(cx - 60, 31, cx + 60, 34, 0x80000000);
            graphics.fill(cx - 60, 31, cx - 60 + current.trialProgress(), 34, GUARD_OUTLINE);
        }
        if (current.beamShower() != null) {
            drawText(graphics, minecraft, current, current.beamShower(), cx, y - 42, current.safe());
        }
        if (current.defense() != null) {
            drawText(graphics, minecraft, current, current.defense(), cx, y - 14, current.defenseColor());
        }
        if (current.cue() != null) {
            drawText(graphics, minecraft, current, current.cue(), cx, Math.min(graphics.guiHeight() / 2 + 40, y - 60), current.safe());
        }
        if (current.feedback() != null) {
            drawText(graphics, minecraft, current, current.feedback(), cx, y - 28, current.safe());
        }
        if (current.flight() != null) {
            drawText(graphics, minecraft, current, current.flight(), cx, y - 28, current.safe());
        }
    }

    private static void drawText(GuiGraphics graphics, Minecraft minecraft, EncounterView current, Component text, int x, int y, int color) {
        if (current.assaultActive()) {
            int half = minecraft.font.width(text) / 2 + 4;
            graphics.fill(x - half, y - 2, x + half, y + 11, current.textBackground());
        }
        graphics.drawCenteredString(minecraft.font, text, x, y, color);
    }

    // ---- shared state the world renderers read -------------------------------------------------

    public static float inversion() {
        return paletteFrom + (paletteTarget - paletteFrom) * UnwakingPresentation.smooth((estimatedTick() - paletteStart) / (float) PALETTE_CROSSFADE_TICKS);
    }

    public static float scenery() {
        long now = estimatedTick();
        if (assault().active(now)) {
            return assault().scenery(now);
        }
        return snapshot == null ? 1.0F : UnwakingPresentation.scenery(hazards(), now);
    }

    public static UnwakingAssaultState assault() {
        return snapshot == null ? UnwakingAssaultState.NONE : snapshot.assault();
    }

    public static long estimatedTick() {
        ClientLevel level = Minecraft.getInstance().level;
        return snapshot == null ? 0L : snapshot.tick() + Math.max(0L, level == null ? 0L : level.getGameTime() - receivedAt);
    }

    public static List<UnwakingHazard> hazards() {
        return snapshot == null ? List.of() : snapshot.hazards();
    }

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
        ClientLevel level = Minecraft.getInstance().level;
        return snapshot != null && level != null && level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION);
    }

    public static boolean ownsDomain() {
        ClientLevel level = Minecraft.getInstance().level;
        return snapshot != null && snapshot.phase().inDomain() && level != null
                && level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION);
    }

    public static UnwakingPhase phase() {
        return snapshot == null ? UnwakingPhase.DORMANT : snapshot.phase();
    }

    public static long phaseAge() {
        return snapshot == null ? 0L : estimatedTick() - snapshot.phaseStart();
    }

    public static boolean quiet() {
        return snapshot != null && snapshot.quiet();
    }

    public static float quietAmount() {
        return quietSince < 0L ? 0.0F : Math.clamp((estimatedTick() - quietSince) / (float) QUIET_RAMP_TICKS, 0.0F, 1.0F);
    }

    public static int bodyPose(int entityId) {
        if (snapshot == null || snapshot.bossId() != entityId) {
            return 0;
        }
        if (snapshot.recoveryTicks() > 0) {
            return 3;
        }
        long now = estimatedTick();
        for (UnwakingHazard hazard : snapshot.hazards()) {
            if (hazard.visible(hazard.age(now))) {
                if (hazard.recovering(hazard.age(now))) {
                    return 3;
                }
                return hazard.kind() == UnwakingHazard.Kind.REFUSAL ? 2 : 1;
            }
        }
        return 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (snapshot == null || minecraft.level == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || !minecraft.level.dimension().location().equals(snapshot.dimension())) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        boolean reduced = snapshot.reducedEffects();
        var orientation = event.getCamera().rotation();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = estimatedTick();
        UnwakingAssaultRenderer.render(pose, buffers, assault(), now, reduced);
        UnwakingAssaultRenderer.scenery(pose, buffers, assault(), now, partial, camera, orientation, reduced);
        // The background flushes before any body, and each body's opaque core flushes before the
        // shell and trail that go over it. None of these write depth, so the batch order is the
        // blend order - leaving one batch open until the end would paint the cores over their own
        // shells.
        buffers.endBatch(MagicalRenderTypes.unwakingSurface());
        for (UnwakingHazard hazard : snapshot.hazards()) {
            int age = hazard.age(now);
            if (!UnwakingFxRenderer.replaces(hazard.kind())) {
                UnwakingOpenRenderer.render(pose, buffers, hazard, age, camera, reduced);
            }
            buffers.endBatch(MagicalRenderTypes.unwakingSurface());
            UnwakingFxRenderer.render(pose, buffers, hazard, age, camera, orientation, partial, reduced);
        }
        if (snapshot.phase() == UnwakingPhase.AWAKENING) {
            UnwakingOpenRenderer.awakening(pose, buffers, camera, phaseAge());
        }
        if (snapshot.hitboxes()) {
            VertexConsumer out = buffers.getBuffer(RenderType.lines());
            for (UnwakingHazard hazard : snapshot.hazards()) {
                UnwakingHazardRenderer.render(pose, out, hazard, hazard.age(now), camera);
            }
            buffers.endBatch(RenderType.lines());
        }
        pose.popPose();
    }

    private static void clear() {
        if (snapshot != null) {
            ENDED_RUNS.add(snapshot.run());
            if (ENDED_RUNS.size() > ENDED_RUNS_KEPT) {
                ENDED_RUNS.remove(ENDED_RUNS.iterator().next());
            }
        }
        snapshot = null;
        view = null;
        readySent = false;
        quietSince = -1L;
        paletteFrom = 0.0F;
        paletteTarget = 0.0F;
        paletteStart = 0L;
        titleKey = null;
        defenseHazard = null;
        surviveSeconds = -1;
        feedbackValue = 0;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
        ENDED_RUNS.clear();
    }
}
