package com.efkrdnz.magical.client.renderer.sword;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.magic.sword.ArrayPose;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordRules;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Array at rest: every manned station's blade, and the threads that make the resource public.
 *
 * <p><b>Both sides run one arithmetic.</b> The blades are placed by
 * {@link ArrayPose#worldOffset(Station, Frame)} - the same pure method the server behaviour calls,
 * with the same arguments - so twelve blade positions cost <em>zero bytes</em> on the wire and a
 * blade cannot desync, because there is nothing to desync. All the entity carries is the frame and
 * the shape, and both of those change rarely. It is also why the fusion needs no case here at all:
 * One Blade drives {@code frame.scale} to zero and {@code worldOffset} puts every station on the
 * origin, which is the convergence, drawn by the same line that draws the formation.
 *
 * <p><b>The threads are the counterplay.</b> One filament from the wielder's chest to every manned
 * blade, brightening with the load and going cinnabar under strain, so an opponent counts the
 * blades from thirty blocks and reads how much Loose, Below and Ward is left without the wielder
 * being asked. They are deliberately not billboarded: a thread to a station directly ahead is
 * end-on and invisible, and that is correct, because the ones that carry the reading are the ones
 * to the sides and behind, which are exactly the ones in frame at the edges in first person.
 * Billboarding them would make each one a ribbon and lose the direction, which is the only thing
 * a thread has to say.
 */
public final class SwordArrayRenderer extends ProfileRendererShell<SwordArrayEntity> {

    /** Cinnabar, the mod's one "you are over the line" colour. Strain, and nothing else, is red. */
    public static final int STRAIN_RED = 0xD4402F;

    /** Strain at which the Array is as red as it gets. A God's draw of 84 is well past it. */
    private static final int STRAIN_FULL = 24;

    /** What a thread shows of itself with an empty budget, so an unloaded Array still has lines. */
    private static final float THREAD_FLOOR = 0.10F;

    /** What the rest of the load is worth. Floor plus this is a thread at full brightness. */
    private static final float THREAD_LOAD = 0.75F;

    /** How thin a thread is drawn. It is a thread and not a beam; the blade is the object. */
    private static final float THREAD_HALF_WIDTH = 0.012F;

    /** A twin carries half its parent's Edge, so it is drawn at about half its parent's weight. */
    private static final float MIRROR_ALPHA = 0.55F;

    private static final int[] NO_SHAPE = new int[0];

    public SwordArrayRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends ProfileRendererShell.State {
        /** Bits 0..11 the wielder's own bearings, bits 12..23 their twins. */
        public int mask;
        public int[] shape = NO_SHAPE;
        public int strain;
        public float scale = 1.0F;
        public float frameYaw;
        public float framePitch;
        /** The wielder's chest relative to the frame origin, or null while they are not loaded. */
        public Vec3 chest;
    }

    @Override
    public ProfileRendererShell.State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SwordArrayEntity entity, ProfileRendererShell.State base, float partialTick) {
        super.extractRenderState(entity, base, partialTick);
        State state = (State) base;
        // The radius a formation carries is the frame's gameplay reach, not the size of its
        // drawing, and ProfileRendererShell inflates MARK/SWARM/FIELD-dome silhouettes to whatever
        // radius it is handed - which would be a sphere the size of the Array round the wielder.
        // EldritchConstructRenderer zeroes it in the same place for the same reason.
        state.radius = 0.0F;
        state.mask = entity.extra();
        CompoundTag data = entity.syncedData();
        state.shape = data != null ? data.getIntArray(SwordArrayEntity.TAG_SHAPE) : NO_SHAPE;
        state.strain = data != null ? data.getInt(SwordArrayEntity.TAG_STRAIN) : 0;
        state.scale = Math.max(0.0F, entity.value());
        Vec3 facing = entity.direction();
        state.frameYaw = frameYaw(facing);
        state.framePitch = framePitch(facing);
        LivingEntity wielder = entity.livingTarget();
        Vec3 origin = entity.getPosition(partialTick);
        state.chest = wielder == null ? null
                : wielder.getPosition(partialTick).add(0.0D, wielder.getBbHeight() * 0.5D, 0.0D).subtract(origin);
    }

    /**
     * The frame reaches {@code REACH_MAX * scale} out from its origin, and the origin is the only
     * thing the entity's own box knows about. Without this a formation whose centre has just left
     * the frustum takes every blade with it while half of them are still on screen.
     */
    @Override
    public boolean shouldRender(SwordArrayEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        double reach = Station.REACH_MAX * Math.max(1.0D, entity.value()) + SwordBladeRenderer.Geometry.LENGTH;
        AABB box = entity.getBoundingBox().inflate(reach);
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(cameraX, cameraY, cameraZ))
                && frustum.isVisible(box);
    }

    @Override
    public void render(ProfileRendererShell.State base, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        super.render(base, pose, buffers, packedLight);
        State state = (State) base;
        if (state.mask == 0 || state.shape.length == 0) {
            return;
        }
        FxContext ctx = new FxContext(pose, buffers, state.partialTick,
                entityRenderDispatcher.cameraOrientation(), state.cameraOffset)
                .timing(state.age, state.life, state.seed);
        ctx.detail = FxBudget.detailForDistance(3, state.distanceSqr);
        ctx.lod = FxBudget.lodForDistance(state.distanceSqr);

        Frame frame = new Frame(0.0D, 0.0D, 0.0D, state.frameYaw, state.framePitch, state.scale);
        int stations = Math.min(state.shape.length, SwordArray.MAX_STATIONS);
        float heat = heat(state.strain);
        // The threads, and only the threads, need the whole shape before any of it is drawn: what
        // one of them says is how loaded the Array is, and that is a property of all of it.
        float load = load(state, stations);
        // Beyond the far LOD band the twins and the threads go before the steel does: the steel is
        // what the picture is of, and a thread at fifty blocks is a pixel either way.
        boolean near = ctx.lod > 0.0F;

        for (int slot = 0; slot < stations; slot++) {
            if ((state.mask & (1 << slot)) == 0) {
                continue;
            }
            Station station = Station.unpack(state.shape[slot]);
            station(ctx, pose, frame, station, heat, 1.0F);
            if (near && state.chest != null) {
                thread(ctx, pose, frame, station, state.chest, load, heat);
            }
        }
        if (!near) {
            return;
        }
        for (int slot = 0; slot < stations; slot++) {
            if ((state.mask & (1 << (SwordArrayEntity.MIRROR_SHIFT + slot))) == 0) {
                continue;
            }
            Station twin = twinOf(Station.unpack(state.shape[slot]));
            station(ctx, pose, frame, twin, heat, MIRROR_ALPHA);
        }
    }

    /** One blade, hung on its bearing and pointing along it, drawn by the blade's own renderer. */
    private static void station(FxContext ctx, PoseStack pose, Frame frame, Station station, float heat, float alpha) {
        double[] offset = ArrayPose.worldOffset(station, frame);
        double[] bearing = ArrayPose.worldBearing(station, frame);
        pose.pushPose();
        pose.translate(offset[0], offset[1], offset[2]);
        SwordBladeRenderer.blade(ctx, new Vec3(bearing[0], bearing[1], bearing[2]), station.edge(),
                alpha, 0.0F, tint(station.edge(), heat));
        pose.popPose();
    }

    /**
     * One thread from the wielder's chest out to a blade.
     *
     * <p>Drawn from the chest rather than from the frame origin, because for every bind but
     * {@code HELD} those are not the same point - a set Array's threads run out of the wielder and
     * across to wherever they left it, which is precisely the reading a set Array owes an
     * opponent.
     */
    private static void thread(FxContext ctx, PoseStack pose, Frame frame, Station station, Vec3 chest,
            float load, float heat) {
        double[] offset = ArrayPose.worldOffset(station, frame);
        Vec3 run = new Vec3(offset[0], offset[1], offset[2]).subtract(chest);
        double length = run.length();
        if (length < 1.0E-3D) {
            return;
        }
        pose.pushPose();
        pose.translate(chest.x, chest.y, chest.z);
        FilamentPainter.orientAlong(pose, run);
        FilamentPainter.beam(ctx, FxKinds.Filament.THREAD_KNOTS, THREAD_HALF_WIDTH, (float) length,
                SwordBladeRenderer.lerpRgb(0xB9C4CE, STRAIN_RED, heat),
                THREAD_FLOOR + THREAD_LOAD * load, SwordBladeRenderer.BEAM_WHOLE, 3, 4);
        pose.popPose();
    }

    // ---- the readings ---------------------------------------------------------------------------

    /** How far toward cinnabar the whole Array has gone. Nothing but strain is ever red here. */
    public static float heat(int strain) {
        return Math.max(0.0F, Math.min(1.0F, strain / (float) STRAIN_FULL));
    }

    /** A blade's own weight on the palette ramp, pulled toward cinnabar by the Array's strain. */
    public static int tint(int edge, float heat) {
        return heat <= 0.0F ? SwordBladeRenderer.edgeColor(edge)
                : SwordBladeRenderer.lerpRgb(SwordBladeRenderer.edgeColor(edge), STRAIN_RED, heat);
    }

    /** {@code bill * scale} against the draw, which is what a thread's brightness is reading. */
    private static float load(State state, int stations) {
        int bill = 0;
        int widest = 0;
        int manned = 0;
        for (int slot = 0; slot < stations; slot++) {
            if ((state.mask & (1 << slot)) == 0) {
                continue;
            }
            Station station = Station.unpack(state.shape[slot]);
            bill += station.weight();
            widest = Math.max(widest, station.edge());
            manned++;
        }
        if (bill <= 0) {
            return 0.0F;
        }
        int scaled = (int) Math.ceil(bill * (double) state.scale);
        return Math.min(1.0F, scaled / (float) draw(bill, widest, manned));
    }

    /**
     * The draw the threads are read against, inferred from the shape rather than synced.
     *
     * <p>The rung is not on the wire and there is nowhere to put it: the Array rides the fourteen
     * slots {@code SpellEffectEntity} already has and every one of them is spoken for. But a rung
     * is a set of caps, and a shape is evidence about which caps it was built under - so the
     * smallest rung that could legally hold this shape is the answer, and it is exactly right for
     * every wielder who has not climbed a rung without rebuilding. The one it is wrong for it
     * reads <em>hotter</em> than the truth, which is the safe direction for a gauge whose job is
     * to warn, and the reading that actually matters - the strain - is synced exactly.
     */
    private static int draw(int bill, int widestEdge, int stations) {
        for (int rung = 0; rung < SwordRules.rungs(); rung++) {
            SwordRules rules = SwordRules.forRung(rung);
            if (stations <= rules.maxStations() && widestEdge <= rules.maxEdge() && bill <= rules.draw()) {
                return rules.draw();
            }
        }
        return SwordRules.GOD.draw();
    }

    /**
     * {@code yaw + 12}, pitch negated, reach unchanged, Edge halved with a floor of one.
     *
     * <p>{@code Projection.mirror} and {@code SwordArrayEntity.mask} both build the same twin;
     * this is the third copy, because the first answers a list with no slots in it and the second
     * lives on the server. {@code ProjectionTest} pins the arithmetic all three of them run.
     */
    private static Station twinOf(Station station) {
        return new Station((station.yaw() + Station.YAW_STEPS / 2) % Station.YAW_STEPS,
                -station.pitch(), station.reach(), Math.max(1, station.edge() / 2));
    }

    // ---- the frame's facing, read back off the wire ----------------------------------------------

    /**
     * The yaw the server wrote, recovered from the unit vector it wrote it as.
     *
     * <p>{@code Vec3.directionFromRotation} lays a look vector out as
     * {@code (-sin yaw cos pitch, -sin pitch, cos yaw cos pitch)}, so this is its inverse and
     * nothing more. It has to be exact: {@link ArrayPose} runs on the frame's degrees on both
     * sides, and a sign here is a silent mirror with a green build and no log line.
     */
    public static float frameYaw(Vec3 facing) {
        if (facing.lengthSqr() < 1.0E-6D) {
            return 0.0F;
        }
        return (float) Math.toDegrees(Math.atan2(-facing.x, facing.z));
    }

    /** The same, for the pitch, which Minecraft counts positive downward. */
    public static float framePitch(Vec3 facing) {
        if (facing.lengthSqr() < 1.0E-6D) {
            return 0.0F;
        }
        Vec3 unit = facing.normalize();
        return (float) -Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, unit.y))));
    }
}
