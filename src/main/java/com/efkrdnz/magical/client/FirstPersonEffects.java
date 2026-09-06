package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.network.FirstPersonEffectPayload;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

/**
 * Single global first-person channel: overlays stack (one fp_overlay quad each), shake / freeze /
 * fov max-merge. Camera code is unchanged; the old per-cell vignette fill is gone.
 */
public final class FirstPersonEffects {
    private static final List<OverlayEffect> OVERLAYS = new ArrayList<>();
    private static int shakeTicks;
    private static int shakeDuration;
    private static float shakeStrength;
    private static int freezeTicks;
    private static float freezeYaw;
    private static float freezePitch;
    private static float freezeRoll;
    private static boolean freezeCameraSet;
    private static float fovKick;
    private static int fovTicks;
    private static int fovDuration;
    private static int age;

    private FirstPersonEffects() {}

    public static void apply(FirstPersonEffectPayload payload) {
        if (payload.overlayTicks() > 0 && payload.maxAlpha() > 0.0F) {
            OVERLAYS.add(new OverlayEffect(payload.color(), payload.overlayTicks(), Mth.clamp(payload.maxAlpha(), 0.0F, 1.0F), payload.overlayKind(), payload.intensity(), payload.hitYaw()));
            if (OVERLAYS.size() > 8) {
                OVERLAYS.remove(0);
            }
        }
        if (payload.shakeTicks() > 0 && payload.shakeStrength() > 0.0F) {
            shakeTicks = Math.max(shakeTicks, payload.shakeTicks());
            shakeDuration = Math.max(shakeDuration, payload.shakeTicks());
            shakeStrength = Math.max(shakeStrength, payload.shakeStrength());
        }
        if (payload.freezeTicks() > 0) {
            freezeTicks = Math.max(freezeTicks, payload.freezeTicks());
            freezeCameraSet = false;
        }
        if (payload.fovKick() != 0.0F) {
            fovKick = payload.fovKick();
            fovTicks = Math.max(1, Math.max(payload.overlayTicks(), payload.shakeTicks()));
            fovDuration = fovTicks;
        }
    }

    public static void tick(Minecraft minecraft) {
        age++;
        for (Iterator<OverlayEffect> iterator = OVERLAYS.iterator(); iterator.hasNext();) {
            OverlayEffect effect = iterator.next();
            effect.age++;
            if (effect.age >= effect.duration) {
                iterator.remove();
            }
        }
        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks == 0) {
                shakeStrength = 0.0F;
                shakeDuration = 0;
            }
        }
        if (freezeTicks > 0) {
            freezeTicks--;
            if (freezeTicks == 0) {
                freezeCameraSet = false;
            }
        }
        if (fovTicks > 0) {
            fovTicks--;
            if (fovTicks == 0) {
                fovKick = 0.0F;
                fovDuration = 0;
            }
        }
    }

    public static void renderOverlay(GuiGraphics guiGraphics) {
        if (OVERLAYS.isEmpty()) {
            return;
        }
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int aspectSeed = Mth.clamp(Math.round(width / (float) Math.max(1, height) * 16.0F), 1, 63);
        guiGraphics.drawSpecial(buffers -> {
            VertexConsumer consumer = buffers.getBuffer(MagicalFxRenderTypes.fpOverlay());
            Matrix4f matrix = guiGraphics.pose().last().pose();
            for (OverlayEffect effect : OVERLAYS) {
                float progress = Mth.clamp((effect.age + partial) / (float) Math.max(1, effect.duration), 0.0F, 1.0F);
                if (effect.maxAlpha <= 0.002F) {
                    continue;
                }
                // mode 0 = directional (paramB = yaw bucket), mode 1 = omnidirectional
                boolean omni = effect.hitYaw >= FirstPersonEffectPayload.OMNI;
                int packed = MagicVertex.pack(effect.kind & 31, effect.intensity & 63, effect.hitYaw & 31, progress, aspectSeed, omni ? 1 : 0);
                MagicVertex.emit(consumer, matrix, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, effect.color, effect.maxAlpha, packed);
                MagicVertex.emit(consumer, matrix, 0.0F, height, 0.0F, 0.0F, 1.0F, effect.color, effect.maxAlpha, packed);
                MagicVertex.emit(consumer, matrix, width, height, 0.0F, 1.0F, 1.0F, effect.color, effect.maxAlpha, packed);
                MagicVertex.emit(consumer, matrix, width, 0.0F, 0.0F, 1.0F, 0.0F, effect.color, effect.maxAlpha, packed);
            }
        });
    }

    public static void applyCamera(ViewportEvent.ComputeCameraAngles event) {
        if (freezeTicks > 0) {
            if (!freezeCameraSet) {
                freezeYaw = event.getYaw();
                freezePitch = event.getPitch();
                freezeRoll = event.getRoll();
                freezeCameraSet = true;
            }
            event.setYaw(freezeYaw);
            event.setPitch(freezePitch);
            event.setRoll(freezeRoll);
            return;
        }
        float strength = shakeAmount((float) event.getPartialTick());
        if (strength <= 0.0F) {
            return;
        }
        float t = age + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + Mth.sin(t * 2.91F) * strength * 0.75F);
        event.setPitch(event.getPitch() + Mth.sin(t * 3.73F + 1.4F) * strength * 0.45F);
        event.setRoll(event.getRoll() + Mth.sin(t * 4.47F + 0.8F) * strength * 1.15F);
    }

    public static void applyFov(ViewportEvent.ComputeFov event) {
        if (fovTicks <= 0 || fovDuration <= 0 || fovKick == 0.0F) {
            return;
        }
        float progress = 1.0F - (fovTicks / (float) fovDuration);
        float fade = Mth.sin((1.0F - progress) * Mth.HALF_PI);
        event.setFOV(event.getFOV() + fovKick * fade);
    }

    private static float shakeAmount(float partialTick) {
        if (shakeTicks <= 0 || shakeDuration <= 0) {
            return 0.0F;
        }
        float remaining = Mth.clamp((shakeTicks - partialTick) / shakeDuration, 0.0F, 1.0F);
        return shakeStrength * remaining * remaining;
    }

    private static final class OverlayEffect {
        private final int color;
        private final int duration;
        private final float maxAlpha;
        private final int kind;
        private final int intensity;
        private final int hitYaw;
        private int age;

        private OverlayEffect(int color, int duration, float maxAlpha, int kind, int intensity, int hitYaw) {
            this.color = color;
            this.duration = duration;
            this.maxAlpha = maxAlpha;
            this.kind = kind;
            this.intensity = intensity;
            this.hitYaw = hitYaw;
        }
    }
}
