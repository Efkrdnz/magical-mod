package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.client.renderer.ForgeStrikeRenderer;
import com.efkrdnz.magical.client.renderer.forge.ForgeRibbon.Sweep;
import com.efkrdnz.magical.client.renderer.forge.ForgeSmear;
import com.efkrdnz.magical.client.renderer.forge.ForgeSparks;
import com.efkrdnz.magical.forge.visual.ForgeMatter;
import com.efkrdnz.magical.forge.visual.ForgeMatter.Emission;
import com.efkrdnz.magical.forge.visual.ForgeMatter.Ink;
import com.efkrdnz.magical.particle.ForgeMatterOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Throws a forged strike's matter off its blade: real particles, born at the lip as the blade
 * passes, flying on along the swing or out from it, with the element's own table deciding what.
 *
 * <p>Called from the strike's renderer, because that is the one place that knows where the blade is
 * drawn this frame: a form that lifts, tilts, rolls or fans its arc has already put all of that in
 * the pose, and the pose maps the blade's own frame straight into camera-relative world space - the
 * same fact {@code ForgeView.eye} reads. So a point on the lip in world space is the camera's
 * position plus the pose applied to it, and no form's arithmetic is written twice.
 *
 * <p>A renderer runs once a frame and a particle must be born once. Each particle of a strike has a
 * birth - a point in the strike's progress, from its seed - and each strike keeps a watermark of how
 * far its progress has been emitted up to, so a frame emits exactly the births it passed and a
 * second draw of the same frame emits nothing. Nothing is sent over the network: every client
 * throws the same shower from the same seed.
 */
public final class ForgeMatterEmitter {

    /** How far into a strike's progress the last particle is born; after it, the blade throws nothing. */
    private static final float LAST_BIRTH = 0.6f;
    /** An open arc throws from this last fraction of itself: the head, where the blade is. */
    private static final float HEAD_BAND = 0.3f;
    /** How far inside the lip a particle may be born, as a fraction across the blade. */
    private static final float LIP_BAND = 0.15f;
    /** How far outside the lip an inward kind is born, in blocks. */
    private static final float IN_START = 0.45f;
    /** How much outward push an ALONG launch carries, and how much along-push an OUT one does. */
    private static final float ALONG_OUT = 0.35f;
    private static final float OUT_ALONG = 0.2f;
    /** A particle's speed is its share's speed times somewhere in this range. */
    private static final float SLOWEST = 0.6f;
    private static final float SPREAD = 0.8f;
    /** A heavy blow throws larger matter. */
    private static final float HEAVY_SCALE = 1.15f;
    /** A lance throws only off its outer half: its hilt is at the wielder's eye. */
    private static final float LANCE_FROM = 0.5f;
    /** Nothing is thrown past this far from the camera. */
    private static final double MAX_DISTANCE_SQR = 1024.0D;
    /** Soot: the last colour of a coal and the colour of dark smoke. */
    private static final int SOOT = 0x2A2622;
    /** A strike unseen for this long is forgotten, and the map is swept only when it grows past this. */
    private static final long FORGET_TICKS = 100L;
    private static final int SWEEP_AT = 64;
    /** The step either side of a point along an arc that its direction of travel is measured over. */
    private static final float RUN_STEP = 0.02f;

    /** Salts, so one seed gives every particle several independent numbers. */
    private static final int BIRTH = 11;
    private static final int ALONG = 12;
    private static final int ACROSS = 13;
    private static final int JITTER_X = 14;
    private static final int JITTER_Y = 15;
    private static final int JITTER_Z = 16;
    private static final int SPEED = 17;

    /** Per strike and part: how far its progress has been emitted, and when it was last seen. */
    private static final Map<Long, Watermark> WATERMARKS = new HashMap<>();
    private static ClientLevel watermarkLevel;

    private ForgeMatterEmitter() {}

    /** Where a particle is born and which way it leaves, in the blade's own frame. */
    @FunctionalInterface
    private interface Placer {
        /** @return {x, y, z, along x, along y, along z, out x, out y, out z} */
        float[] place(int index);
    }

    /**
     * Throws off an arc of blade. {@code part} tells apart several arcs one strike draws in a frame
     * (a flurry's pulses), {@code progress} is how far through its life that arc is.
     */
    public static void arc(ForgeStrikeRenderer.State state, Matrix4f pose, Sweep sweep, int part, float progress) {
        boolean closed = ForgeSmear.closed(sweep.toDegrees() - sweep.fromDegrees());
        emit(state, pose, part, progress, index -> {
            float u = ForgeSparks.unit(state.seed, index, ALONG);
            float along = closed ? u : 1.0f - HEAD_BAND * u;
            float across = 1.0f - LIP_BAND * ForgeSparks.unit(state.seed, index, ACROSS);
            float[] p = sweep.at(along, across);
            float[] before = sweep.at(Math.max(0.0f, along - RUN_STEP), 1.0f);
            float[] after = sweep.at(Math.min(1.0f, along + RUN_STEP), 1.0f);
            // the arc's centre is the frame's origin, so the lip point is also the way out
            float[] lip = sweep.at(along, 1.0f);
            return new float[] {p[0], p[1], p[2],
                    after[0] - before[0], after[1] - before[1], after[2] - before[2],
                    lip[0], lip[1], lip[2]};
        });
    }

    /** Throws off a thrown wave's rim: out from it, and left behind it as it flies. */
    public static void rim(ForgeStrikeRenderer.State state, Matrix4f pose, Sweep rim) {
        emit(state, pose, 0, state.progress, index -> {
            float[] p = rim.at(ForgeSparks.unit(state.seed, index, ALONG), 1.0f);
            return new float[] {p[0], p[1], p[2], 0.0f, 0.0f, -1.0f, p[0], p[1], p[2]};
        });
    }

    /** Throws off the outer half of a lance: forward, and out to either side of it. */
    public static void lance(ForgeStrikeRenderer.State state, Matrix4f pose, float length, float halfWidth) {
        emit(state, pose, 0, state.progress, index -> {
            float z = length * (LANCE_FROM + (1.0f - LANCE_FROM) * ForgeSparks.unit(state.seed, index, ALONG));
            float side = ForgeSparks.unit(state.seed, index, ACROSS) * 2.0f - 1.0f;
            return new float[] {side * halfWidth, 0.0f, z, 0.0f, 0.0f, 1.0f, side, 0.0f, 0.0f};
        });
    }

    private static void emit(ForgeStrikeRenderer.State state, Matrix4f pose, int part, float progress, Placer placer) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        float last = advance(level, state.seed, part, progress);
        if (progress <= last || last >= LAST_BIRTH) {
            return;
        }
        ParticleStatus status = minecraft.options.particles().get();
        Vector3f origin = pose.transformPosition(new Vector3f());
        if (status == ParticleStatus.MINIMAL || origin.lengthSquared() > MAX_DISTANCE_SQR) {
            return;
        }
        int stride = status == ParticleStatus.DECREASED ? 2 : 1;
        List<Emission> emissions = ForgeMatter.swing(state.element);
        int[] dealt = ForgeMatter.deal(emissions, ForgeMatter.swingCount(state.grade, state.heavy, state.echo));
        boolean[] kept = ForgeMatter.keep(dealt, emissions.size(), stride);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (int index = 0; index < dealt.length; index++) {
            float birth = ForgeSparks.unit(state.seed, index, BIRTH) * LAST_BIRTH;
            if (!kept[index] || birth <= last || birth > progress) {
                continue;
            }
            Emission emission = emissions.get(dealt[index]);
            float[] placed = placer.place(index);
            Vector3f at = new Vector3f(placed[0], placed[1], placed[2]);
            Vector3f direction = launch(emission, placed);
            if (emission.launch() == ForgeMatter.Launch.IN) {
                at.add(unit(placed[6], placed[7], placed[8], 0.0f, 0.0f, 1.0f).mul(IN_START));
            }
            pose.transformPosition(at);
            if (emission.launch() == ForgeMatter.Launch.UP) {
                direction.set(0.0f, 1.0f, 0.0f);
            } else {
                pose.transformDirection(direction);
            }
            jitter(direction, state.seed, index, emission.cone());
            float speed = emission.speed() * (SLOWEST + SPREAD * ForgeSparks.unit(state.seed, index, SPEED));
            ForgeMatterOptions options = new ForgeMatterOptions(MagicalParticles.FORGE_MATTER.get(), emission.kind(),
                    ink(state, emission.stops().hot()), ink(state, emission.stops().body()),
                    ink(state, emission.stops().cool()), state.heavy ? HEAVY_SCALE : 1.0f);
            minecraft.particleEngine.createParticle(options, camera.x + at.x, camera.y + at.y, camera.z + at.z,
                    direction.x * speed, direction.y * speed, direction.z * speed);
        }
    }

    /** The direction a share leaves in, in the blade's frame, before its cone. */
    private static Vector3f launch(Emission emission, float[] placed) {
        Vector3f along = unit(placed[3], placed[4], placed[5], 0.0f, 0.0f, 1.0f);
        Vector3f out = unit(placed[6], placed[7], placed[8], 0.0f, 0.0f, 1.0f);
        return switch (emission.launch()) {
            case ALONG -> along.add(out.mul(ALONG_OUT)).normalize();
            case OUT -> out.add(along.mul(OUT_ALONG)).normalize();
            case IN -> out.negate();
            case UP -> new Vector3f(0.0f, 1.0f, 0.0f);
        };
    }

    /** Turns {@code direction} off its line by up to {@code cone}, the same way on every client. */
    private static void jitter(Vector3f direction, int seed, int index, float cone) {
        direction.normalize();
        direction.add((ForgeSparks.unit(seed, index, JITTER_X) - 0.5f) * 2.0f * cone,
                (ForgeSparks.unit(seed, index, JITTER_Y) - 0.5f) * 2.0f * cone,
                (ForgeSparks.unit(seed, index, JITTER_Z) - 0.5f) * 2.0f * cone);
        if (direction.lengthSquared() < 1.0E-6f) {
            direction.set(0.0f, 1.0f, 0.0f);
        }
        direction.normalize();
    }

    private static Vector3f unit(float x, float y, float z, float fx, float fy, float fz) {
        Vector3f v = new Vector3f(x, y, z);
        return v.lengthSquared() < 1.0E-8f ? v.set(fx, fy, fz) : v.normalize();
    }

    private static int ink(ForgeStrikeRenderer.State state, Ink ink) {
        return switch (ink) {
            case WHITE -> 0xFFFFFF;
            case EDGE -> state.edge;
            case PRIMARY -> state.primary;
            case SECONDARY -> state.secondary;
            case SOOT -> SOOT;
        };
    }

    /**
     * Moves the watermark for one strike's part up to {@code progress} and returns where it was,
     * or -1 for a strike seen the first time.
     */
    private static float advance(ClientLevel level, int seed, int part, float progress) {
        if (level != watermarkLevel) {
            WATERMARKS.clear();
            watermarkLevel = level;
        }
        long now = level.getGameTime();
        if (WATERMARKS.size() > SWEEP_AT) {
            for (Iterator<Watermark> it = WATERMARKS.values().iterator(); it.hasNext();) {
                if (now - it.next().seen > FORGET_TICKS) {
                    it.remove();
                }
            }
        }
        long key = ((long) seed << 8) | (part & 0xFF);
        Watermark mark = WATERMARKS.computeIfAbsent(key, k -> new Watermark());
        float last = mark.progress;
        mark.progress = Math.max(mark.progress, progress);
        mark.seen = now;
        return last;
    }

    private static final class Watermark {
        private float progress = -1.0f;
        private long seen;
    }
}
