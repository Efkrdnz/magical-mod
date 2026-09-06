package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.client.ChronosClientEnvironment;
import com.efkrdnz.magical.magic.ChronosDimensionService;
import com.efkrdnz.magical.magic.ChronosEnvironmentService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * The ambient renderer for Chronos End. The void is dressed by two systems:
 *
 * ZONES - small, scattered visual biomes seeded on a coarse deterministic grid. Between them
 * lies the default Gilded End; inside them every shape (and, for the camera, the sky itself)
 * restyles: the Inversion Rift negates every color, Starfall goes deep-space blue, the Molten
 * Collapse burns amber-orange, and the Prismatic Fracture cycles the whole spectrum.
 *
 * FEATURES - eight paradox monuments seeded per 64-block cell: clockwork rings, stuttering
 * shard clusters, hourglass pillars, ringed planets with live moons, comets with streaming
 * tails, wormhole spirals, constellation webs, and monolith arrays - plus the colossal
 * backwards-running Last Clock at the origin.
 */
public final class ChronosEndRenderer {
    private static final int CELL = 64;
    private static final int CELL_RADIUS = 3;
    private static final int ZONE_CELL = 192;
    private static final double ORIGIN_CLEARANCE_SQR = 96.0D * 96.0D;

    private static final int THEME_GILDED = 0;
    private static final int THEME_RESONANCE = 1;
    private static final int THEME_STARFALL = 2;
    private static final int THEME_MOLTEN = 3;
    private static final int THEME_PRISMATIC = 4;

    private static final int[] GILDED_PRIMARY = {255, 214, 140};
    private static final int[] GILDED_SECONDARY = {130, 232, 220};
    private static final int[] GILDED_HIGHLIGHT = {255, 246, 220};
    private static final int[] GILDED_SKY_OUTER = {34, 26, 18};
    private static final int[] GILDED_SKY_INNER = {40, 30, 20};
    private static final float BEAT_PERIOD = 16.0F;
    private static final float THEME_SHIFT_DEGREES = 165.0F;

    // Sky-cut anchor, shared by the rift quad and the camera grab so they always agree:
    // the wound hangs along -Z rotated CUT_ANCHOR_YAW about Y, raised and pulled in close
    // (both as fractions of the sky radius) so it looms enormous overhead.
    private static final float CUT_ANCHOR_YAW = 35.0F;
    private static final float CUT_HEIGHT_RATIO = 0.16F;
    private static final float CUT_DISTANCE_RATIO = 0.26F;

    /** Boss-driven theme shift (gold/orange -> blue/purple), applied to every vertex. */
    private static float themeShiftLevel;
    /** Boss-driven palette override (PALETTE_* id + eased blend level, scenery/shapes split). */
    private static float paletteLevel;
    private static int paletteId;
    /** True while the sky spheres are being drawn: they take the palette's scenery color. */
    private static boolean paletteScenery;
    /** Monument presence: fades every shape out while the sky cut awaits the theme swap. */
    private static float globalAlphaScale = 1.0F;
    private static float hueA = 1.0F;
    private static float hueB;
    private static float hueC;
    private static final int[] SHIFT_SCRATCH = new int[3];
    /** Animation clock that the time-freeze effect can slow to a crawl. */
    private static double visualClock;
    private static double lastWorldTime = Double.NaN;

    private ChronosEndRenderer() {}

    public static void render(RenderLevelStageEvent event, Minecraft minecraft) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || minecraft.level == null
                || !minecraft.level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION)) {
            return;
        }
        double worldTime = minecraft.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffer.getBuffer(MagicalRenderTypes.chrono());

        // Boss-fight environment levels.
        float cutOpen = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_SKY_CUT, worldTime);
        float themeShift = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_THEME_SHIFT, worldTime);
        float pulseStorm = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_PULSE_STORM, worldTime)
                * ChronosClientEnvironment.strength(ChronosEnvironmentService.EFFECT_PULSE_STORM);
        float vortex = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_SKY_VORTEX, worldTime);
        float freeze = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_TIME_FREEZE, worldTime);
        float starRain = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_STAR_RAIN, worldTime)
                * ChronosClientEnvironment.strength(ChronosEnvironmentService.EFFECT_STAR_RAIN);
        float clocksOnly = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_CLOCKS_ONLY, worldTime);
        paletteLevel = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_COLOR_PALETTE, worldTime);
        paletteId = Math.round(ChronosClientEnvironment.strength(ChronosEnvironmentService.EFFECT_COLOR_PALETTE));

        // Time freeze slows the shared animation clock to a near-standstill.
        double delta = Double.isNaN(lastWorldTime) ? 0.0D : Mth.clamp(worldTime - lastWorldTime, 0.0D, 5.0D);
        lastWorldTime = worldTime;
        visualClock += delta * (1.0D - freeze * 0.985D);
        float time = (float) (visualClock % 240000.0D);

        setThemeShift(themeShift);
        float stormThrob = 1.0F + beat(time) * 0.35F * pulseStorm;

        // Sky follows the camera's zone, cross-fading at the zone edge.
        Zone cameraZone = zoneAt(camera.x, camera.z);
        Style skyStyle = styleFor(cameraZone.theme(), time);
        float blend = cameraZone.blend();
        int[] skyOuter = lerpColor(GILDED_SKY_OUTER, skyStyle.skyOuter(), blend);
        int[] skyInner = lerpColor(GILDED_SKY_INNER, skyStyle.skyInner(), blend);
        float skyRadius = Math.max(96.0F, minecraft.options.renderDistance().get() * 16.0F * 0.7F);
        paletteScenery = true;
        renderTimeSky(poseStack, consumer, time, skyRadius, skyOuter, skyInner);
        paletteScenery = false;
        // Dying stars ride the sky shell but are colored as shapes, so they follow the
        // theme shift, the camera zone, and any palette override along with the monuments.
        int[] starMain = lerpColor(GILDED_PRIMARY, skyStyle.primary(), blend);
        int[] starHighlight = lerpColor(GILDED_HIGHLIGHT, skyStyle.highlight(), blend);
        renderSkyStars(poseStack, consumer, time, skyRadius * 0.94F, starMain, starHighlight);
        // The sky cut, vortex, and freeze clock each have their own shader, so flush the sky shell
        // first, draw those discs on top of it in their own batches, then resume the shared chrono batch.
        if (cutOpen > 0.01F || vortex > 0.01F || freeze > 0.01F) {
            buffer.endBatch(MagicalRenderTypes.chrono());
            if (cutOpen > 0.01F) {
                VertexConsumer riftConsumer = buffer.getBuffer(MagicalRenderTypes.chronoRift());
                renderSkyCut(poseStack, riftConsumer, time, skyRadius, cutOpen);
                buffer.endBatch(MagicalRenderTypes.chronoRift());
            }
            if (vortex > 0.01F) {
                VertexConsumer vortexConsumer = buffer.getBuffer(MagicalRenderTypes.chronoVortex());
                renderSkyVortex(poseStack, vortexConsumer, time, skyRadius, vortex);
                buffer.endBatch(MagicalRenderTypes.chronoVortex());
            }
            if (freeze > 0.01F) {
                VertexConsumer clockConsumer = buffer.getBuffer(MagicalRenderTypes.chronoClock());
                renderFreezeClock(poseStack, clockConsumer, time, skyRadius, freeze);
                buffer.endBatch(MagicalRenderTypes.chronoClock());
            }
            consumer = buffer.getBuffer(MagicalRenderTypes.chrono());
        }
        if (starRain > 0.01F) {
            renderStarRain(poseStack, consumer, time, skyRadius, starRain);
        }

        // While the cut hangs open awaiting the swap, the monuments dissolve out of reality;
        // once the theme shift completes they re-materialize wearing the new theme.
        float vanish = Mth.clamp(cutOpen * 2.0F, 0.0F, 1.0F)
                * (1.0F - Mth.clamp((themeShift - 0.5F) * 4.0F, 0.0F, 1.0F));
        float presence = 1.0F - vanish;
        if (presence > 0.01F) {
            globalAlphaScale = presence;
            renderLastClock(poseStack, consumer, camera, time);

            boolean resonanceView = cameraZone.theme() == THEME_RESONANCE && cameraZone.blend() > 0.4F;
            int cameraCellX = Mth.floor(camera.x / CELL);
            int cameraCellZ = Mth.floor(camera.z / CELL);
            for (int dx = -CELL_RADIUS; dx <= CELL_RADIUS; dx++) {
                for (int dz = -CELL_RADIUS; dz <= CELL_RADIUS; dz++) {
                    renderCell(poseStack, consumer, camera, time, cameraCellX + dx, cameraCellZ + dz, resonanceView, stormThrob, clocksOnly);
                }
            }
            globalAlphaScale = 1.0F;
        }
        buffer.endBatch(MagicalRenderTypes.chrono());
        setThemeShift(0.0F);
    }

    private static void renderCell(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, float time, int cellX, int cellZ, boolean riftView, float stormThrob, float clocksOnly) {
        long seed = hash(cellX, cellZ);
        int count = 1 + (int) (seed & 1L);
        for (int i = 0; i < count; i++) {
            double x = (cellX + unit(seed, i * 9 + 1)) * CELL;
            double y = 50.0D + unit(seed, i * 9 + 2) * 160.0D;
            double z = (cellZ + unit(seed, i * 9 + 3)) * CELL;
            if (x * x + z * z < ORIGIN_CLEARANCE_SQR) {
                continue; // keep the Last Clock's court empty
            }
            int type = (int) (unit(seed, i * 9 + 4) * 8.0F);
            float scale = (0.7F + unit(seed, i * 9 + 5) * 1.1F) * stormThrob;
            float phase = unit(seed, i * 9 + 6) * 628.0F;
            boolean reversed = unit(seed, i * 9 + 7) < 0.45F;
            Zone featureZone = zoneAt(x, z);
            Style style = styleFor(featureZone.theme(), time);

            // Clocks-only: everything that is not a clockwork ring dissolves from reality,
            // while the surviving clocks swell into looming, dominant dials.
            boolean isClock = type == 0 && !(riftView && featureZone.theme() == THEME_RESONANCE);
            float featurePresence = isClock ? 1.0F : 1.0F - clocksOnly;
            if (featurePresence <= 0.01F) {
                continue;
            }
            if (isClock) {
                scale *= 1.0F + 0.6F * clocksOnly;
            }
            float outerAlphaScale = globalAlphaScale;
            globalAlphaScale = outerAlphaScale * featurePresence;

            poseStack.pushPose();
            poseStack.translate(x - camera.x, y - camera.y, z - camera.z);
            long featureSeed = seed + i * 0x51ED2701L;
            if (riftView && featureZone.theme() == THEME_RESONANCE) {
                // Inside a Resonance zone the monuments transform entirely.
                switch (type % 3) {
                    case 0 -> renderRainLines(poseStack, consumer, time, phase, scale, featureSeed, style);
                    case 1 -> renderChains(poseStack, consumer, time, phase, scale, featureSeed, style);
                    default -> renderPulseStar(poseStack, consumer, time, phase, scale, featureSeed, style);
                }
            } else {
                switch (type) {
                    case 0 -> renderClockworkRing(poseStack, consumer, time, phase, scale, reversed, featureSeed, style);
                    case 1 -> renderShardCluster(poseStack, consumer, time, phase, scale, featureSeed, style);
                    case 2 -> renderHourglassPillar(poseStack, consumer, time, phase, scale, style);
                    case 3 -> renderPlanetSystem(poseStack, consumer, time, phase, scale, featureSeed, style);
                    case 4 -> renderComet(poseStack, consumer, time, phase, scale, featureSeed, style);
                    case 5 -> renderWormhole(poseStack, consumer, time, phase, scale, featureSeed, style);
                    case 6 -> renderConstellation(poseStack, consumer, time, phase, scale, featureSeed, style);
                    default -> renderMonolithArray(poseStack, consumer, time, phase, scale, featureSeed, style);
                }
            }
            poseStack.popPose();
            globalAlphaScale = outerAlphaScale;
        }
    }

    /**
     * Screen shake while the sky cut tears open: it ramps in once the wound starts widening and
     * stops the moment the cut is fully open, matching the shader's form -> open sequence. Called
     * from the camera-angles event so it applies to the actual view.
     */
    public static void applyCutShake(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || !minecraft.level.dimension().equals(ChronosDimensionService.CHRONOS_DIMENSION)) {
            return;
        }
        double worldTime = minecraft.level.getGameTime() + event.getPartialTick();
        float level = ChronosClientEnvironment.level(ChronosEnvironmentService.EFFECT_SKY_CUT, worldTime);
        if (level <= 0.0F) {
            return;
        }
        float open = smoothstep(0.12F, 1.0F, level);

        // The wound seizes every gaze in the dimension: the view is dragged smoothly onto the
        // cut while it tears open, held there through the worst of it, and released once the
        // rift hangs fully open. The blend weight eases 0 -> 1 -> 0, so both the capture and
        // the release are gradual and the player's own aim is never actually modified.
        float grab = smoothstep(0.02F, 0.18F, open) * (1.0F - smoothstep(0.75F, 0.96F, open));
        if (grab > 0.0F) {
            // Derived from the anchor: facing (-sin yaw, +h, -cos yaw) gives 180 - CUT_ANCHOR_YAW.
            float targetYaw = 180.0F - CUT_ANCHOR_YAW;
            float targetPitch = (float) -Math.toDegrees(Math.atan2(CUT_HEIGHT_RATIO, CUT_DISTANCE_RATIO));
            event.setYaw(event.getYaw() + Mth.wrapDegrees(targetYaw - event.getYaw()) * grab);
            event.setPitch(event.getPitch() + (targetPitch - event.getPitch()) * grab);
        }

        float shake = smoothstep(0.02F, 0.20F, open) * (1.0F - smoothstep(0.80F, 1.0F, open));
        if (shake <= 0.0F) {
            return;
        }
        float t = (float) worldTime;
        event.setYaw(event.getYaw() + Mth.sin(t * 2.91F) * shake * 0.9F);
        event.setPitch(event.getPitch() + Mth.sin(t * 3.73F + 1.4F) * shake * 0.6F);
        event.setRoll(event.getRoll() + Mth.sin(t * 4.47F + 0.8F) * shake * 1.4F);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Sharp dubstep thump: spikes at the start of each bar, decays fast. Globally synced. */
    private static float beat(float time) {
        float barProgress = (time % BEAT_PERIOD) / BEAT_PERIOD;
        return (1.0F - barProgress) * (1.0F - barProgress) * (1.0F - barProgress);
    }

    // --- The Sky Cut: a wound torn across the sky, drawn as one shader-lit rift onto another realm ---

    private static void renderSkyCut(PoseStack poseStack, VertexConsumer consumer, float time, float skyRadius, float level) {
        poseStack.pushPose();
        // Anchor the rift in a fixed patch of sky; it stays put as the player turns and moves.
        // Pulled in close so the wound dominates the view instead of reading as sky decoration.
        poseStack.mulPose(Axis.YP.rotationDegrees(CUT_ANCHOR_YAW));
        poseStack.translate(0.0D, skyRadius * CUT_HEIGHT_RATIO, -skyRadius * CUT_DISTANCE_RATIO);
        // Roll the whole quad so the tear runs diagonally instead of straight up and down.
        poseStack.mulPose(Axis.ZP.rotationDegrees(35.0F));
        Matrix4f matrix = poseStack.last().pose();
        float halfW = skyRadius * 0.75F;
        float halfH = skyRadius * 0.80F;
        int alpha = Math.round(255.0F * Mth.clamp(level, 0.0F, 1.0F));
        // One tall vertical quad facing the player. The shader carves the wound, the waving lips, the
        // aura, and the abyss behind it, and leaves the rest of the quad transparent; the vertex color
        // carries the (theme-shifted) aura tint and, in its alpha, the effect level that drives the
        // form -> open sequence. The saturated primary keeps the wound's glow from washing to white.
        quad(consumer, matrix,
                -halfW, -halfH, 0.0F, 0.0F, 0.0F,
                -halfW, halfH, 0.0F, 0.0F, 1.0F,
                halfW, halfH, 0.0F, 1.0F, 1.0F,
                halfW, -halfH, 0.0F, 1.0F, 0.0F,
                GILDED_PRIMARY[0], GILDED_PRIMARY[1], GILDED_PRIMARY[2], alpha);
        poseStack.popPose();
    }

    // --- The Sky Vortex: a colossal spiral maw at the zenith, drawn as one shader-lit disc ---

    private static void renderSkyVortex(PoseStack poseStack, VertexConsumer consumer, float time, float skyRadius, float level) {
        poseStack.pushPose();
        // Hang the disc high above the camera so it follows the player and fills the upward view.
        poseStack.translate(0.0D, skyRadius * 0.5D, 0.0D);
        // A slow bodily rotation so time-freeze can arrest the churn; the shader adds the fine swirl.
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.6F));
        Matrix4f matrix = poseStack.last().pose();
        float half = skyRadius * 0.95F;
        int alpha = Math.round(255.0F * Mth.clamp(level, 0.0F, 1.0F));
        // One large horizontal quad facing straight down at the player. The shader carves the spiral,
        // the eye, and the glow from the UVs and masks the disc to a circle; the vertex color carries
        // the (theme-shifted) tint and, in its alpha, the current vortex level.
        quad(consumer, matrix,
                -half, 0.0F, -half, 0.0F, 0.0F,
                -half, 0.0F, half, 0.0F, 1.0F,
                half, 0.0F, half, 1.0F, 1.0F,
                half, 0.0F, -half, 1.0F, 0.0F,
                GILDED_PRIMARY[0], GILDED_PRIMARY[1], GILDED_PRIMARY[2], alpha);
        poseStack.popPose();
    }

    // --- The Freeze Clock: a colossal frozen dial lying flat beneath the world, drawn as one shader-lit disc ---

    private static void renderFreezeClock(PoseStack poseStack, VertexConsumer consumer, float time, float skyRadius, float level) {
        poseStack.pushPose();
        // Hang the great dial low beneath the player, lying flat and facing straight up so it fills
        // the downward view. Time itself has stopped, so - unlike the vortex - it never turns.
        poseStack.translate(0.0D, -skyRadius * 0.45D, 0.0D);
        Matrix4f matrix = poseStack.last().pose();
        float half = skyRadius * 0.95F;
        int alpha = Math.round(255.0F * Mth.clamp(level, 0.0F, 1.0F));
        // One large horizontal quad. The shader engraves the whole clock - bezel, ticks, hour pips,
        // gearwork, and the three frozen hands - from the UVs and masks the disc to a circle; the
        // vertex color carries the (theme-shifted) tint and, in its alpha, the current freeze level.
        quad(consumer, matrix,
                -half, 0.0F, -half, 0.0F, 0.0F,
                -half, 0.0F, half, 0.0F, 1.0F,
                half, 0.0F, half, 1.0F, 1.0F,
                half, 0.0F, -half, 1.0F, 0.0F,
                GILDED_PRIMARY[0], GILDED_PRIMARY[1], GILDED_PRIMARY[2], alpha);
        poseStack.popPose();
    }

    // --- Star Rain: a meteor storm streaking across the whole sky ---

    private static void renderStarRain(PoseStack poseStack, VertexConsumer consumer, float time, float skyRadius, float level) {
        Matrix4f matrix = poseStack.last().pose();
        Vec3 fall = new Vec3(-0.45D, -1.0D, 0.28D).normalize();
        int meteors = Math.round(26.0F * Math.min(1.5F, level));
        float span = skyRadius * 0.85F;
        for (int i = 0; i < meteors; i++) {
            long seed = hash(i * 733L + 5L, i * 271L + 29L);
            float period = 70.0F + unit(seed, 1) * 90.0F;
            float progress = ((time * (0.8F + unit(seed, 2) * 0.6F) + unit(seed, 3) * period) % period) / period;
            float fade = Mth.sin(progress * Mth.PI);
            double startX = (unit(seed, 4) - 0.5F) * 2.0F * span;
            double startY = skyRadius * (0.35D + unit(seed, 5) * 0.3D);
            double startZ = (unit(seed, 6) - 0.5F) * 2.0F * span;
            double travel = progress * skyRadius * 1.1D;
            Vec3 head = new Vec3(startX + fall.x * travel, startY + fall.y * travel, startZ + fall.z * travel);
            Vec3 tail = head.subtract(fall.scale(5.0D + unit(seed, 7) * 7.0D));
            beam(consumer, matrix, tail, head, 0.5F, GILDED_PRIMARY, Math.round(120.0F * fade * level));
            beam(consumer, matrix, head.subtract(fall.scale(2.2D)), head, 0.22F, GILDED_HIGHLIGHT, Math.round(235.0F * fade * level));
        }
    }

    // --- Resonance zone interior shapes: rain lines, chains, and pulsing beat stars ---

    private static void renderRainLines(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        Matrix4f matrix = poseStack.last().pose();
        int lines = 8 + (int) (unit(seed, 200) * 6.0F);
        float height = 34.0F * scale;
        for (int i = 0; i < lines; i++) {
            float angle = Mth.TWO_PI * i / lines + unit(seed, 201 + i) * 0.7F;
            float distance = (1.5F + unit(seed, 210 + i) * 6.0F) * scale;
            float x = Mth.cos(angle) * distance;
            float z = Mth.sin(angle) * distance;
            // The standing line, top to bottom.
            beam(consumer, matrix, new Vec3(x, height * 0.5F, z), new Vec3(x, -height * 0.5F, z), 0.1F * scale, style.primary(), 110);
            // A bright droplet racing down it.
            float speed = 0.5F + unit(seed, 220 + i) * 0.6F;
            float drop = height * 0.5F - ((time * speed + phase + i * 11.0F) % height);
            beam(consumer, matrix, new Vec3(x, drop + 1.1F, z), new Vec3(x, drop - 1.1F, z), 0.24F * scale, style.highlight(), 235);
        }
    }

    private static void renderChains(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        int chains = 3 + (int) (unit(seed, 230) * 3.0F);
        for (int c = 0; c < chains; c++) {
            float angle = Mth.TWO_PI * c / chains + unit(seed, 231 + c) * 0.9F;
            float distance = (2.5F + unit(seed, 240 + c) * 4.5F) * scale;
            int links = 9 + (int) (unit(seed, 250 + c) * 5.0F);
            float linkSize = 0.55F * scale;
            float sway = Mth.sin(time * 0.05F + phase + c * 2.0F) * 9.0F;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(angle) * distance, links * linkSize * 0.95F * 0.5F, Mth.sin(angle) * distance);
            poseStack.mulPose(Axis.ZP.rotationDegrees(sway));
            for (int link = 0; link < links; link++) {
                poseStack.pushPose();
                poseStack.translate(0.0D, -link * linkSize * 1.9D, 0.0D);
                if ((link & 1) == 1) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                }
                ring(consumer, poseStack.last().pose(), linkSize, linkSize * 0.26F,
                        (link & 1) == 0 ? style.primary() : style.secondary(), 210);
                poseStack.popPose();
            }
            poseStack.popPose();
        }
    }

    private static void renderPulseStar(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        float thump = beat(time);
        int bar = (int) (time / BEAT_PERIOD);
        // Symmetric configuration steps on every bar: point count and orientation snap.
        int points = (bar + (int) (unit(seed, 260) * 2.0F)) % 2 == 0 ? 6 : 12;
        float barRotation = bar * 15.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(unit(seed, 261) * 360.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(unit(seed, 262) * 100.0F - 50.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(barRotation + time * 0.15F));
        Matrix4f matrix = poseStack.last().pose();

        float inner = 1.2F * scale;
        float outer = (5.0F + 3.5F * thump) * scale;
        for (int i = 0; i < points; i++) {
            float angle = Mth.TWO_PI * i / points;
            radial(consumer, matrix, angle, inner, outer, (0.35F + 0.45F * thump) * scale, style.primary(), Math.round(150.0F + 100.0F * thump));
        }
        // Expanding shockwave ring launched by every thump.
        float barProgress = (time % BEAT_PERIOD) / BEAT_PERIOD;
        ring(consumer, matrix, barProgress * 9.0F * scale, 0.24F * scale, style.secondary(), Math.round(190.0F * (1.0F - barProgress)));
        ring(consumer, matrix, inner, 0.2F * scale, style.highlight(), Math.round(180.0F + 75.0F * thump));
    }

    // --- Zones: scattered visual biomes on a coarse grid ---

    private static Zone zoneAt(double x, double z) {
        int zoneX = Mth.floor(x / ZONE_CELL);
        int zoneZ = Mth.floor(z / ZONE_CELL);
        long seed = hash(zoneX * 7349L + 13L, zoneZ * 9151L + 71L);
        if (unit(seed, 5) < 0.25F) {
            return Zone.DEFAULT; // some cells hold no rift at all
        }
        double centerX = (zoneX + 0.35D + unit(seed, 1) * 0.3D) * ZONE_CELL;
        double centerZ = (zoneZ + 0.35D + unit(seed, 2) * 0.3D) * ZONE_CELL;
        float radius = 44.0F + unit(seed, 3) * 20.0F;
        double dx = x - centerX;
        double dz = z - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > radius) {
            return Zone.DEFAULT;
        }
        int theme = 1 + (int) (unit(seed, 4) * 4.0F) % 4;
        float blend = Mth.clamp((float) ((radius - distance) / 18.0D), 0.0F, 1.0F);
        return new Zone(theme, blend);
    }

    private static Style styleFor(int theme, float time) {
        return switch (theme) {
            case THEME_RESONANCE -> new Style(
                    new int[] {150, 120, 255}, new int[] {90, 200, 255}, new int[] {240, 240, 255},
                    new int[] {16, 10, 36}, new int[] {24, 16, 48});
            case THEME_STARFALL -> new Style(
                    new int[] {150, 180, 255}, new int[] {255, 255, 255}, new int[] {205, 170, 255},
                    new int[] {8, 10, 28}, new int[] {12, 14, 36});
            case THEME_MOLTEN -> new Style(
                    new int[] {255, 158, 46}, new int[] {255, 108, 28}, new int[] {255, 232, 176},
                    new int[] {30, 18, 8}, new int[] {40, 24, 10});
            case THEME_PRISMATIC -> {
                // Confined to the blue-violet-pink arc so it never lands on muddy greens or oranges.
                float hue = 195.0F + 135.0F * (0.5F + 0.5F * Mth.sin(time * 0.009F));
                yield new Style(
                        hsv(hue, 0.75F, 1.0F), hsv(hue + 45.0F, 0.65F, 1.0F), new int[] {255, 255, 255},
                        hsv(hue, 0.6F, 0.16F), hsv(hue + 25.0F, 0.6F, 0.2F));
            }
            default -> new Style(GILDED_PRIMARY, GILDED_SECONDARY, GILDED_HIGHLIGHT, GILDED_SKY_OUTER, GILDED_SKY_INNER);
        };
    }

    private static int[] lerpColor(int[] from, int[] to, float t) {
        return new int[] {
                Math.round(Mth.lerp(t, from[0], to[0])),
                Math.round(Mth.lerp(t, from[1], to[1])),
                Math.round(Mth.lerp(t, from[2], to[2]))};
    }

    private static int[] hsv(float hue, float saturation, float value) {
        float h = ((hue % 360.0F) + 360.0F) % 360.0F / 60.0F;
        int sector = (int) h;
        float f = h - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - saturation * f);
        float t = value * (1.0F - saturation * (1.0F - f));
        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return new int[] {Math.round(r * 255.0F), Math.round(g * 255.0F), Math.round(b * 255.0F)};
    }

    // --- The temporal sky: two camera-locked nested spheres of time-fog and dying stars ---

    private static void renderTimeSky(PoseStack poseStack, VertexConsumer consumer, float time, float radius, int[] outer, int[] inner) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.011F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(4.0F));
        drawSkySphere(poseStack.last().pose(), consumer, radius, 12.0F, 6.0F, outer[0], outer[1], outer[2], sceneryAlpha(46));
        poseStack.popPose();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-time * 0.007F));
        poseStack.mulPose(Axis.XP.rotationDegrees(9.0F));
        drawSkySphere(poseStack.last().pose(), consumer, radius * 0.72F, 9.0F, 5.0F, inner[0], inner[1], inner[2], sceneryAlpha(30));
        poseStack.popPose();
    }

    /**
     * The dying stars: sparse square specks scattered over the sky shell, each burning out
     * on its own rhythm. Drawn as real quads (shapes role) so every color system - theme
     * shift, zones, palettes - applies to them exactly as it does to the monuments.
     */
    private static void renderSkyStars(PoseStack poseStack, VertexConsumer consumer, float time, float radius, int[] main, int[] highlight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.011F)); // drift with the outer sky shell
        Matrix4f matrix = poseStack.last().pose();
        for (int i = 0; i < 150; i++) {
            long seed = hash(i * 977L + 3L, i * 389L + 17L);
            float yaw = unit(seed, 1) * Mth.TWO_PI;
            float pitch = (unit(seed, 2) - 0.5F) * Mth.PI * 0.9F; // keep clear of the exact poles
            float cosPitch = Mth.cos(pitch);
            Vec3 direction = new Vec3(cosPitch * Mth.cos(yaw), Mth.sin(pitch), cosPitch * Mth.sin(yaw));

            // Each star flickers at its own pace, sinking to near-dark as it "burns out".
            float twinkle = 0.5F + 0.5F * Mth.sin(time * (0.18F + unit(seed, 3) * 0.55F) + unit(seed, 4) * 628.0F);
            int alpha = Math.round(40.0F + 195.0F * twinkle * twinkle);

            float size = radius * (0.0035F + unit(seed, 5) * 0.0045F);
            Vec3 reference = Math.abs(direction.y) > 0.85D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
            Vec3 u = direction.cross(reference).normalize().scale(size);
            Vec3 v = direction.cross(u).normalize().scale(size);
            Vec3 c = direction.scale(radius);
            int[] color = unit(seed, 6) > 0.65F ? highlight : main;
            quad(consumer, matrix,
                    (float) (c.x + u.x + v.x), (float) (c.y + u.y + v.y), (float) (c.z + u.z + v.z), 0.0F, 0.0F,
                    (float) (c.x - u.x + v.x), (float) (c.y - u.y + v.y), (float) (c.z - u.z + v.z), 0.0F, 1.0F,
                    (float) (c.x - u.x - v.x), (float) (c.y - u.y - v.y), (float) (c.z - u.z - v.z), 1.0F, 1.0F,
                    (float) (c.x + u.x - v.x), (float) (c.y + u.y - v.y), (float) (c.z + u.z - v.z), 1.0F, 0.0F,
                    color[0], color[1], color[2], alpha);
        }
        poseStack.popPose();
    }

    /**
     * The dim sky spheres are far too faint to read as "white scenery" over the black void,
     * so the white palettes thicken them as the palette blends in.
     */
    private static int sceneryAlpha(int base) {
        boolean whiteScenery = paletteId == ChronosEnvironmentService.PALETTE_WHITE_BLACK
                || paletteId == ChronosEnvironmentService.PALETTE_WHITE_GOLD;
        if (paletteLevel <= 0.0F || !whiteScenery) {
            return base;
        }
        return Math.min(255, Math.round(base * (1.0F + 2.6F * paletteLevel)));
    }

    private static void drawSkySphere(Matrix4f matrix, VertexConsumer consumer, float radius, float uRepeat, float vRepeat, int red, int green, int blue, int alpha) {
        drawSphere(matrix, consumer, radius, 16, 32, uRepeat, vRepeat, red, green, blue, alpha);
    }

    /** Auto-tessellated sphere: segment counts scale with radius so tiny moons and huge planets both read as round. */
    private static void drawSphere(Matrix4f matrix, VertexConsumer consumer, float radius,
            float uRepeat, float vRepeat, int red, int green, int blue, int alpha) {
        int lonSegments = Mth.clamp(Math.round(radius * 6.0F), 12, 48);
        drawSphere(matrix, consumer, radius, Math.max(6, lonSegments / 2), lonSegments, uRepeat, vRepeat, red, green, blue, alpha);
    }

    private static void drawSphere(Matrix4f matrix, VertexConsumer consumer, float radius, int latSegments, int lonSegments,
            float uRepeat, float vRepeat, int red, int green, int blue, int alpha) {
        for (int lat = 0; lat < latSegments; lat++) {
            for (int lon = 0; lon < lonSegments; lon++) {
                sphereVertex(consumer, matrix, lat, lon, latSegments, lonSegments, radius, uRepeat, vRepeat, red, green, blue, alpha);
                sphereVertex(consumer, matrix, lat + 1, lon, latSegments, lonSegments, radius, uRepeat, vRepeat, red, green, blue, alpha);
                sphereVertex(consumer, matrix, lat + 1, lon + 1, latSegments, lonSegments, radius, uRepeat, vRepeat, red, green, blue, alpha);
                sphereVertex(consumer, matrix, lat, lon + 1, latSegments, lonSegments, radius, uRepeat, vRepeat, red, green, blue, alpha);
            }
        }
    }

    private static void sphereVertex(VertexConsumer consumer, Matrix4f matrix, int lat, int lon, int latSegments, int lonSegments,
            float radius, float uRepeat, float vRepeat, int red, int green, int blue, int alpha) {
        float theta = Mth.PI * lat / latSegments;
        float phi = Mth.TWO_PI * lon / lonSegments;
        float sinTheta = Mth.sin(theta);
        float x = radius * sinTheta * Mth.cos(phi);
        float y = radius * Mth.cos(theta);
        float z = radius * sinTheta * Mth.sin(phi);
        float u = (1.0F - Math.abs(1.0F - 2.0F * lon / (float) lonSegments)) * uRepeat;
        float v = (lat / (float) latSegments) * vRepeat;
        shiftColor(red, green, blue);
        consumer.addVertex(matrix, x, y, z).setUv(u, v)
                .setColor(SHIFT_SCRATCH[0], SHIFT_SCRATCH[1], SHIFT_SCRATCH[2], scaledAlpha(alpha));
    }

    // --- The Last Clock: a 48-block clock face at the origin, running backwards ---

    private static void renderLastClock(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, float time) {
        poseStack.pushPose();
        poseStack.translate(0.5D - camera.x, 140.0D - camera.y, 0.5D - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.045F));
        Matrix4f matrix = poseStack.last().pose();

        ring(consumer, matrix, 48.0F, 1.2F, GILDED_PRIMARY, 215);
        ring(consumer, matrix, 44.5F, 0.35F, GILDED_SECONDARY, 140);
        ring(consumer, matrix, 20.0F, 0.4F, GILDED_PRIMARY, 130);
        ring(consumer, matrix, 2.4F, 0.8F, GILDED_HIGHLIGHT, 235);
        for (int i = 0; i < 12; i++) {
            float angle = Mth.TWO_PI * i / 12.0F;
            boolean quarter = i % 3 == 0;
            radial(consumer, matrix, angle, 40.0F, quarter ? 46.5F : 44.0F, quarter ? 1.1F : 0.6F, GILDED_PRIMARY, 200);
        }
        hand(consumer, matrix, -time * 0.028F, 22.0F, 1.5F, GILDED_PRIMARY, 225);
        hand(consumer, matrix, -time * 0.19F, 33.0F, 1.0F, GILDED_PRIMARY, 205);
        hand(consumer, matrix, -time * 2.4F, 42.0F, 0.45F, GILDED_SECONDARY, 235);

        for (int i = 0; i < 8; i++) {
            float angle = Mth.TWO_PI * i / 8.0F + time * 0.006F;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(angle) * 60.0F, Mth.sin(angle * 2.0F + time * 0.01F) * 6.0F, Mth.sin(angle) * 60.0F);
            shard(poseStack, consumer, time * 0.4F + i * 40.0F, 3.4F, 1.1F, GILDED_SECONDARY, 190);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    // --- Time features ---

    private static void renderClockworkRing(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, boolean reversed, long seed, Style style) {
        float spin = (reversed ? -1.0F : 1.0F) * (time * 0.35F + phase);
        poseStack.mulPose(Axis.YP.rotationDegrees(unit(seed, 40) * 360.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(unit(seed, 41) * 140.0F - 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        Matrix4f matrix = poseStack.last().pose();
        float radius = 9.0F * scale;
        ring(consumer, matrix, radius, 0.4F * scale, style.primary(), 190);
        ring(consumer, matrix, radius * 0.72F, 0.16F * scale, style.secondary(), 130);
        for (int i = 0; i < 12; i++) {
            float angle = Mth.TWO_PI * i / 12.0F;
            radial(consumer, matrix, angle, radius * 0.86F, radius * 0.97F, 0.22F * scale, style.primary(), 170);
        }
        hand(consumer, matrix, -spin * 2.2F, radius * 0.62F, 0.24F * scale, style.highlight(), 200);
    }

    private static void renderShardCluster(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        int shards = 5 + (int) (unit(seed, 50) * 4.0F);
        for (int i = 0; i < shards; i++) {
            float stutter = Mth.floor((time + phase) / 32.0F) * 32.0F;
            float orbit = (stutter * 0.02F + i * Mth.TWO_PI / shards) + unit(seed, 51 + i) * 0.8F;
            float orbitRadius = (3.0F + unit(seed, 60 + i) * 4.5F) * scale;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(orbit) * orbitRadius,
                    (unit(seed, 70 + i) - 0.5F) * 6.0F * scale + Mth.sin(time * 0.02F + i) * 0.8F,
                    Mth.sin(orbit) * orbitRadius);
            int[] color = (i & 1) == 0 ? style.secondary() : style.primary();
            shard(poseStack, consumer, time * 0.5F + i * 55.0F, (1.6F + unit(seed, 80 + i) * 2.2F) * scale, (0.5F + unit(seed, 90 + i) * 0.4F) * scale, color, 205);
            poseStack.popPose();
        }
    }

    private static void renderHourglassPillar(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, Style style) {
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.4F + phase));
        Matrix4f matrix = poseStack.last().pose();
        float height = 34.0F * scale;
        float maxWidth = 4.2F * scale;
        int steps = 10;
        int[] color = style.primary();
        for (int plane = 0; plane < 2; plane++) {
            for (int i = 0; i < steps; i++) {
                float t0 = i / (float) steps;
                float t1 = (i + 1) / (float) steps;
                float w0 = pinch(t0) * maxWidth + 0.12F;
                float w1 = pinch(t1) * maxWidth + 0.12F;
                float y0 = (t0 - 0.5F) * height;
                float y1 = (t1 - 0.5F) * height;
                int alpha = Math.round(165.0F * (0.55F + 0.45F * Math.abs(t0 - 0.5F) * 2.0F));
                if (plane == 0) {
                    quad(consumer, matrix, -w0, y0, 0.0F, t0 * 0.5F, 0.1F, w0, y0, 0.0F, t0 * 0.5F, 0.9F,
                            w1, y1, 0.0F, t1 * 0.5F, 0.9F, -w1, y1, 0.0F, t1 * 0.5F, 0.1F, color[0], color[1], color[2], alpha);
                } else {
                    quad(consumer, matrix, 0.0F, y0, -w0, t0 * 0.5F, 0.1F, 0.0F, y0, w0, t0 * 0.5F, 0.9F,
                            0.0F, y1, w1, t1 * 0.5F, 0.9F, 0.0F, y1, -w1, t1 * 0.5F, 0.1F, color[0], color[1], color[2], alpha);
                }
            }
        }
        ring(consumer, matrix, 1.0F * scale, 0.18F * scale, style.secondary(), 200);
    }

    private static float pinch(float t) {
        float offset = Math.abs(t - 0.5F) * 2.0F;
        return (float) Math.pow(offset, 1.4D);
    }

    // --- Space features ---

    private static void renderPlanetSystem(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 0.12F + phase));
        poseStack.mulPose(Axis.ZP.rotationDegrees(unit(seed, 100) * 44.0F - 22.0F));
        Matrix4f matrix = poseStack.last().pose();
        float radius = (2.6F + unit(seed, 101) * 2.0F) * scale;
        int[] body = style.primary();
        drawSphere(matrix, consumer, radius, 2.0F, 1.4F, body[0], body[1], body[2], 225);

        // Planetary ring(s), tilted like Saturn's.
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(74.0F));
        Matrix4f ringMatrix = poseStack.last().pose();
        ring(consumer, ringMatrix, radius * 1.7F, radius * 0.22F, style.secondary(), 170);
        if (unit(seed, 102) > 0.5F) {
            ring(consumer, ringMatrix, radius * 2.1F, radius * 0.09F, style.highlight(), 110);
        }
        poseStack.popPose();

        // Live moons on inclined orbits.
        int moons = 1 + (int) (unit(seed, 103) * 3.0F);
        for (int i = 0; i < moons; i++) {
            float orbit = time * (0.01F + 0.008F * i) + unit(seed, 104 + i) * 6.28F;
            float orbitRadius = radius * (2.6F + i * 0.9F);
            poseStack.pushPose();
            poseStack.translate(Mth.cos(orbit) * orbitRadius, Mth.sin(orbit + i) * radius * 0.7F, Mth.sin(orbit) * orbitRadius);
            drawSphere(poseStack.last().pose(), consumer, radius * 0.22F, 1.0F, 1.0F,
                    style.highlight()[0], style.highlight()[1], style.highlight()[2], 210);
            poseStack.popPose();
        }
    }

    private static void renderComet(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        // The comet loops its anchor; the tail streams opposite its motion.
        float orbit = time * 0.02F + phase;
        float loopRadius = (8.0F + unit(seed, 110) * 8.0F) * scale;
        float headX = Mth.cos(orbit) * loopRadius;
        float headZ = Mth.sin(orbit) * loopRadius;
        float headY = Mth.sin(orbit * 1.7F) * 3.0F * scale;
        poseStack.pushPose();
        poseStack.translate(headX, headY, headZ);
        Matrix4f matrix = poseStack.last().pose();

        // Head: bright core.
        drawSphere(matrix, consumer, 0.9F * scale, 1.0F, 1.0F,
                style.highlight()[0], style.highlight()[1], style.highlight()[2], 240);
        // Tail: three wavering ribbons trailing along the reversed tangent.
        Vec3 tangent = new Vec3(-Mth.sin(orbit), 0.35F * Mth.cos(orbit * 1.7F), Mth.cos(orbit)).normalize();
        Vec3 back = tangent.scale(-1.0D);
        float tailLength = (9.0F + unit(seed, 111) * 7.0F) * scale;
        for (int ribbon = 0; ribbon < 3; ribbon++) {
            Vec3 previous = Vec3.ZERO;
            int segments = 6;
            for (int i = 1; i <= segments; i++) {
                float t = i / (float) segments;
                double wave = Math.sin(time * 0.12D + ribbon * 2.1D + t * 6.0D) * 0.8D * t * scale;
                Vec3 point = back.scale(t * tailLength)
                        .add(0.0D, wave + ribbon * 0.35D * t, wave * (ribbon - 1) * 0.5D);
                int alpha = Math.round(200.0F * (1.0F - t));
                beam(consumer, matrix, previous, point, (0.55F - 0.4F * t) * scale, style.primary(), alpha);
                previous = point;
            }
        }
        poseStack.popPose();
    }

    private static void renderWormhole(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        poseStack.mulPose(Axis.YP.rotationDegrees(unit(seed, 120) * 360.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(unit(seed, 121) * 120.0F - 60.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * 1.2F + phase));
        Matrix4f matrix = poseStack.last().pose();
        float maxRadius = 8.0F * scale;

        for (int arm = 0; arm < 3; arm++) {
            float armOffset = arm * Mth.TWO_PI / 3.0F;
            float previousX = Mth.cos(armOffset) * maxRadius;
            float previousY = Mth.sin(armOffset) * maxRadius;
            int steps = 14;
            for (int i = 1; i <= steps; i++) {
                float t = i / (float) steps;
                float radius = maxRadius * (1.0F - t);
                float angle = armOffset + t * 4.2F;
                float x = Mth.cos(angle) * radius;
                float y = Mth.sin(angle) * radius;
                float width = (0.65F - 0.5F * t) * scale;
                int alpha = Math.round(120.0F + 110.0F * t);
                int[] color = t > 0.72F ? style.highlight() : style.primary();
                beam(consumer, matrix, new Vec3(previousX, previousY, 0.0D), new Vec3(x, y, 0.0D), width, color, alpha);
                previousX = x;
                previousY = y;
            }
        }
        ring(consumer, matrix, maxRadius * 0.16F, 0.14F * scale, style.secondary(), 235);
    }

    private static void renderConstellation(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        Matrix4f matrix = poseStack.last().pose();
        int points = 5 + (int) (unit(seed, 130) * 4.0F);
        Vec3[] stars = new Vec3[points];
        for (int i = 0; i < points; i++) {
            stars[i] = new Vec3(
                    (unit(seed, 131 + i * 3) - 0.5F) * 13.0F * scale,
                    (unit(seed, 132 + i * 3) - 0.5F) * 10.0F * scale,
                    (unit(seed, 133 + i * 3) - 0.5F) * 13.0F * scale);
        }
        // Chain plus one cross-link; edges pulse in sequence like a signal travelling.
        for (int i = 0; i < points; i++) {
            int next = (i + 1) % points;
            float pulse = 0.5F + 0.5F * Mth.sin(time * 0.1F + phase + i * 1.3F);
            beam(consumer, matrix, stars[i], stars[next], 0.09F * scale, style.secondary(), Math.round(70.0F + 130.0F * pulse));
        }
        beam(consumer, matrix, stars[0], stars[points / 2], 0.07F * scale, style.secondary(), 90);
        for (int i = 0; i < points; i++) {
            poseStack.pushPose();
            poseStack.translate(stars[i].x, stars[i].y, stars[i].z);
            shard(poseStack, consumer, time * 0.8F + i * 30.0F, 0.7F * scale, 0.35F * scale, style.highlight(), 235);
            poseStack.popPose();
        }
    }

    private static void renderMonolithArray(PoseStack poseStack, VertexConsumer consumer, float time, float phase, float scale, long seed, Style style) {
        int monoliths = 3 + (int) (unit(seed, 140) * 3.0F);
        for (int i = 0; i < monoliths; i++) {
            float angle = Mth.TWO_PI * i / monoliths + phase * 0.01F;
            float distance = (5.0F + unit(seed, 141 + i) * 5.0F) * scale;
            float bob = Mth.sin(time * 0.045F + i * 2.0F) * 1.6F;
            poseStack.pushPose();
            poseStack.translate(Mth.cos(angle) * distance, bob, Mth.sin(angle) * distance);
            poseStack.mulPose(Axis.YP.rotationDegrees(unit(seed, 150 + i) * 360.0F + time * 0.08F));
            float halfWidth = (0.7F + unit(seed, 160 + i) * 0.5F) * scale;
            float halfHeight = (4.5F + unit(seed, 170 + i) * 3.5F) * scale;
            box(consumer, poseStack.last().pose(), halfWidth, halfHeight, halfWidth * 0.4F, style.primary(), style.highlight(), 215);
            poseStack.popPose();
        }
    }

    // --- Geometry helpers (chrono render type: POSITION_TEX_COLOR quads, no cull) ---

    private static void ring(VertexConsumer consumer, Matrix4f matrix, float radius, float thickness, int[] color, int alpha) {
        int segments = Mth.clamp(Math.round(radius * 3.5F), 32, 128);
        for (int i = 0; i < segments; i++) {
            float a = Mth.TWO_PI * i / segments;
            float b = Mth.TWO_PI * (i + 1) / segments;
            float ua = 1.0F - Math.abs(1.0F - 2.0F * i / (float) segments);
            float ub = 1.0F - Math.abs(1.0F - 2.0F * (i + 1) / (float) segments);
            quad(consumer, matrix,
                    Mth.cos(a) * (radius - thickness), Mth.sin(a) * (radius - thickness), 0.0F, ua, 0.2F,
                    Mth.cos(a) * (radius + thickness), Mth.sin(a) * (radius + thickness), 0.0F, ua, 0.8F,
                    Mth.cos(b) * (radius + thickness), Mth.sin(b) * (radius + thickness), 0.0F, ub, 0.8F,
                    Mth.cos(b) * (radius - thickness), Mth.sin(b) * (radius - thickness), 0.0F, ub, 0.2F,
                    color[0], color[1], color[2], alpha);
        }
    }

    private static void radial(VertexConsumer consumer, Matrix4f matrix, float angle, float innerRadius, float outerRadius, float thickness, int[] color, int alpha) {
        float nx = -Mth.sin(angle) * thickness * 0.5F;
        float ny = Mth.cos(angle) * thickness * 0.5F;
        float ix = Mth.cos(angle) * innerRadius;
        float iy = Mth.sin(angle) * innerRadius;
        float ox = Mth.cos(angle) * outerRadius;
        float oy = Mth.sin(angle) * outerRadius;
        quad(consumer, matrix, ix - nx, iy - ny, 0.0F, 0.1F, 0.1F, ix + nx, iy + ny, 0.0F, 0.1F, 0.9F,
                ox + nx, oy + ny, 0.0F, 0.9F, 0.9F, ox - nx, oy - ny, 0.0F, 0.9F, 0.1F, color[0], color[1], color[2], alpha);
    }

    private static void hand(VertexConsumer consumer, Matrix4f matrix, float angleDegrees, float length, float thickness, int[] color, int alpha) {
        float angle = angleDegrees * Mth.DEG_TO_RAD + Mth.HALF_PI;
        float nx = -Mth.sin(angle) * thickness;
        float ny = Mth.cos(angle) * thickness;
        float tx = Mth.cos(angle) * length;
        float ty = Mth.sin(angle) * length;
        quad(consumer, matrix, -nx, -ny, 0.0F, 0.1F, 0.1F, nx, ny, 0.0F, 0.1F, 0.9F,
                tx + nx * 0.1F, ty + ny * 0.1F, 0.0F, 0.9F, 0.9F, tx - nx * 0.1F, ty - ny * 0.1F, 0.0F, 0.9F, 0.1F,
                color[0], color[1], color[2], alpha);
    }

    private static void shard(PoseStack poseStack, VertexConsumer consumer, float spinDegrees, float halfHeight, float halfWidth, int[] color, int alpha) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees));
        Matrix4f matrix = poseStack.last().pose();
        quad(consumer, matrix, 0.0F, halfHeight, 0.0F, 0.5F, 0.0F, halfWidth, 0.0F, 0.0F, 0.9F, 0.5F,
                0.0F, -halfHeight, 0.0F, 0.5F, 1.0F, -halfWidth, 0.0F, 0.0F, 0.1F, 0.5F, color[0], color[1], color[2], alpha);
        quad(consumer, matrix, 0.0F, halfHeight, 0.0F, 0.5F, 0.0F, 0.0F, 0.0F, halfWidth, 0.9F, 0.5F,
                0.0F, -halfHeight, 0.0F, 0.5F, 1.0F, 0.0F, 0.0F, -halfWidth, 0.1F, 0.5F, color[0], color[1], color[2], alpha);
        poseStack.popPose();
    }

    /** Crossed-ribbon beam between two local-space points; visible from every angle. */
    private static void beam(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to, float thickness, int[] color, int alpha) {
        Vec3 direction = to.subtract(from);
        if (direction.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 reference = Math.abs(direction.y) > direction.length() * 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 side1 = direction.cross(reference).normalize().scale(thickness * 0.5F);
        Vec3 side2 = direction.cross(side1).normalize().scale(thickness * 0.5F);
        beamQuad(consumer, matrix, from, to, side1, color, alpha);
        beamQuad(consumer, matrix, from, to, side2, color, alpha);
    }

    private static void beamQuad(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 side, int[] color, int alpha) {
        quad(consumer, matrix,
                (float) (from.x + side.x), (float) (from.y + side.y), (float) (from.z + side.z), 0.1F, 0.1F,
                (float) (from.x - side.x), (float) (from.y - side.y), (float) (from.z - side.z), 0.1F, 0.9F,
                (float) (to.x - side.x), (float) (to.y - side.y), (float) (to.z - side.z), 0.9F, 0.9F,
                (float) (to.x + side.x), (float) (to.y + side.y), (float) (to.z + side.z), 0.9F, 0.1F,
                color[0], color[1], color[2], alpha);
    }

    /** Axis-aligned box in local space: four sides plus caps. */
    private static void box(VertexConsumer consumer, Matrix4f matrix, float halfWidth, float halfHeight, float halfDepth, int[] side, int[] cap, int alpha) {
        quad(consumer, matrix, -halfWidth, -halfHeight, -halfDepth, 0.1F, 0.1F, halfWidth, -halfHeight, -halfDepth, 0.9F, 0.1F,
                halfWidth, halfHeight, -halfDepth, 0.9F, 0.9F, -halfWidth, halfHeight, -halfDepth, 0.1F, 0.9F, side[0], side[1], side[2], alpha);
        quad(consumer, matrix, -halfWidth, -halfHeight, halfDepth, 0.1F, 0.1F, halfWidth, -halfHeight, halfDepth, 0.9F, 0.1F,
                halfWidth, halfHeight, halfDepth, 0.9F, 0.9F, -halfWidth, halfHeight, halfDepth, 0.1F, 0.9F, side[0], side[1], side[2], alpha);
        quad(consumer, matrix, -halfWidth, -halfHeight, -halfDepth, 0.1F, 0.1F, -halfWidth, -halfHeight, halfDepth, 0.9F, 0.1F,
                -halfWidth, halfHeight, halfDepth, 0.9F, 0.9F, -halfWidth, halfHeight, -halfDepth, 0.1F, 0.9F, side[0], side[1], side[2], alpha);
        quad(consumer, matrix, halfWidth, -halfHeight, -halfDepth, 0.1F, 0.1F, halfWidth, -halfHeight, halfDepth, 0.9F, 0.1F,
                halfWidth, halfHeight, halfDepth, 0.9F, 0.9F, halfWidth, halfHeight, -halfDepth, 0.1F, 0.9F, side[0], side[1], side[2], alpha);
        quad(consumer, matrix, -halfWidth, halfHeight, -halfDepth, 0.1F, 0.1F, halfWidth, halfHeight, -halfDepth, 0.9F, 0.1F,
                halfWidth, halfHeight, halfDepth, 0.9F, 0.9F, -halfWidth, halfHeight, halfDepth, 0.1F, 0.9F, cap[0], cap[1], cap[2], alpha);
        quad(consumer, matrix, -halfWidth, -halfHeight, -halfDepth, 0.1F, 0.1F, halfWidth, -halfHeight, -halfDepth, 0.9F, 0.1F,
                halfWidth, -halfHeight, halfDepth, 0.9F, 0.9F, -halfWidth, -halfHeight, halfDepth, 0.1F, 0.9F, cap[0], cap[1], cap[2], alpha);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            float x4, float y4, float z4, float u4, float v4,
            int red, int green, int blue, int alpha) {
        shiftColor(red, green, blue);
        int r = SHIFT_SCRATCH[0];
        int g = SHIFT_SCRATCH[1];
        int b = SHIFT_SCRATCH[2];
        int a = scaledAlpha(alpha);
        consumer.addVertex(matrix, x1, y1, z1).setUv(u1, v1).setColor(r, g, b, a);
        consumer.addVertex(matrix, x2, y2, z2).setUv(u2, v2).setColor(r, g, b, a);
        consumer.addVertex(matrix, x3, y3, z3).setUv(u3, v3).setColor(r, g, b, a);
        consumer.addVertex(matrix, x4, y4, z4).setUv(u4, v4).setColor(r, g, b, a);
    }

    private static int scaledAlpha(int alpha) {
        return globalAlphaScale >= 1.0F ? alpha : Math.round(alpha * globalAlphaScale);
    }

    /** Configure the global theme shift: a hue rotation toward the blue/purple otherworld. */
    private static void setThemeShift(float level) {
        themeShiftLevel = Mth.clamp(level, 0.0F, 1.0F);
        if (themeShiftLevel <= 0.0F) {
            return;
        }
        float angle = themeShiftLevel * THEME_SHIFT_DEGREES * Mth.DEG_TO_RAD;
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float third = (1.0F - cos) / 3.0F;
        hueA = cos + third;
        hueB = third - 0.57735F * sin;
        hueC = third + 0.57735F * sin;
    }

    /**
     * Universal color choke point: every emitted vertex color runs through the theme-shift
     * hue rotation, then blends toward the active palette override (scenery and shapes
     * take different palette colors; shapes keep their structure via luminance).
     */
    private static void shiftColor(int red, int green, int blue) {
        int r = red;
        int g = green;
        int b = blue;
        if (themeShiftLevel > 0.0F) {
            r = Mth.clamp(Math.round(hueA * red + hueB * green + hueC * blue), 0, 255);
            g = Mth.clamp(Math.round(hueC * red + hueA * green + hueB * blue), 0, 255);
            b = Mth.clamp(Math.round(hueB * red + hueC * green + hueA * blue), 0, 255);
        }
        if (paletteLevel > 0.0F && paletteId != ChronosEnvironmentService.PALETTE_DEFAULT) {
            // Luminance preserves each shape's internal light/dark structure under the flat palette.
            float lum = (0.299F * r + 0.587F * g + 0.114F * b) / 255.0F;
            float tr;
            float tg;
            float tb;
            if (paletteScenery) {
                switch (paletteId) {
                    case ChronosEnvironmentService.PALETTE_BLACK_WHITE -> { tr = 0.0F; tg = 0.0F; tb = 0.0F; }
                    case ChronosEnvironmentService.PALETTE_WHITE_GOLD -> { tr = 246.0F; tg = 240.0F; tb = 226.0F; }
                    default -> { tr = 242.0F; tg = 242.0F; tb = 244.0F; } // PALETTE_WHITE_BLACK
                }
            } else {
                float body = 0.55F + 0.45F * lum;
                switch (paletteId) {
                    case ChronosEnvironmentService.PALETTE_BLACK_WHITE -> { tr = 255.0F * body; tg = 255.0F * body; tb = 255.0F * body; }
                    case ChronosEnvironmentService.PALETTE_WHITE_GOLD -> { tr = 255.0F * body; tg = 198.0F * body; tb = 64.0F * body; }
                    default -> { tr = 8.0F + 30.0F * lum; tg = 8.0F + 30.0F * lum; tb = 12.0F + 34.0F * lum; } // ink shapes
                }
            }
            r = Mth.clamp(Math.round(Mth.lerp(paletteLevel, r, tr)), 0, 255);
            g = Mth.clamp(Math.round(Mth.lerp(paletteLevel, g, tg)), 0, 255);
            b = Mth.clamp(Math.round(Mth.lerp(paletteLevel, b, tb)), 0, 255);
        }
        SHIFT_SCRATCH[0] = r;
        SHIFT_SCRATCH[1] = g;
        SHIFT_SCRATCH[2] = b;
    }

    private static long hash(long x, long z) {
        long h = x * 341873128712L + z * 132897987541L;
        h = (h ^ (h >>> 31)) * 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 27);
    }

    private static float unit(long seed, int salt) {
        long h = seed + salt * 0x632BE59BD9B4E019L;
        h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
        return ((h >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private record Zone(int theme, float blend) {
        static final Zone DEFAULT = new Zone(THEME_GILDED, 0.0F);
    }

    private record Style(int[] primary, int[] secondary, int[] highlight, int[] skyOuter, int[] skyInner) {}
}

