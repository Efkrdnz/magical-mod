package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.client.hud.HudPalette;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.space.SubspaceCrossings;
import com.efkrdnz.magical.client.renderer.space.SubspaceKind;
import com.efkrdnz.magical.client.renderer.space.SubspaceLedger;
import com.efkrdnz.magical.client.renderer.space.SubspaceLod;
import com.efkrdnz.magical.client.renderer.space.SubspaceOptics;
import com.efkrdnz.magical.client.renderer.space.SubspaceSun;
import com.efkrdnz.magical.client.renderer.space.SubspaceVault;
import com.efkrdnz.magical.entity.SpaceSubspaceEntity;
import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleChange;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boundary of a subspace: a vault of cold glass, wound as tight as the law written on it.
 *
 * <p>You are standing under an arch rather than inside a bubble. A ring runs round the wall at
 * head height and a bit, twelve ribs spring from it and climb, leaning, to an open eye of sky
 * straight overhead, and a second ring traces the floor where the wall meets the world you were
 * standing in. Six of the twelve carry a law: heavier, in the operation own colour, swelling into
 * a boss that bears its glyph. The other six pinch to an empty socket at the same height, so the
 * denominator is drawn as well as the numerator. Between all of it the pane is three percent.
 *
 * <p>Two geometric facts about that viewpoint decide everything, and the dome this replaces fell
 * foul of both. The domain is a sphere pinned to the caster body centre while the camera is at
 * their eyes, so the eye is {@code SubspaceVault.EYE_OFFSET} above the centre and nowhere else.
 * First: every sight line therefore runs along the surface normal, so the inverted Fresnel the old
 * wall was built on was a constant to ten decimal places and the wall was a flat seven percent
 * that moved blackstone by two values out of two hundred and fifty-five. Second: every meridian
 * plane contains the eye, so any member drawn up a meridian projects to an exactly straight line -
 * which is a tree trunk or a fence post, not architecture. Hence the twist.
 *
 * <p>Nothing moves except a ring where something crossed, a rib climbing once as its law is
 * written, and the wall fading up when it is raised.
 */
public final class SpaceSubspaceRenderer extends EntityRenderer<SpaceSubspaceEntity, SpaceSubspaceRenderer.State> {

    /** The wall fades up over half a second; its radius never takes part, so it is never a bubble. */
    private static final float BIRTH_TICKS = 12.0F;
    /** How far out a crossing ring runs, in degrees from the point it started at. */
    private static final float RIPPLE_REACH = 62.0F;
    private static final float RIPPLE_HALF_WIDTH = 1.1F;
    /** The ring mesh is cached per step rather than per frame, which is what makes it free. */
    private static final int RIPPLE_STEPS = 8;
    private static final int CATEGORIES = SpaceRuleCategory.values().length;

    private static final float RING_HALF = (float) SubspaceVault.RING_HALF_DEGREES;
    private static final float RIB_HALF = (float) SubspaceVault.RIB_HALF_DEGREES;
    private static final float BOSS_HALF = (float) SubspaceVault.BOSS_HALF_DEGREES;
    private static final float NOTCH_HALF_SWEEP = (float) SubspaceVault.NOTCH_HALF_SWEEP_DEGREES;
    private static final float NOTCH_HALF_HEIGHT = (float) SubspaceVault.NOTCH_HALF_HEIGHT_DEGREES;
    private static final float OCULUS_LATITUDE = (float) SubspaceVault.OCULUS_LATITUDE_DEGREES;

    public SpaceSubspaceRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /**
     * The box the frustum test uses, which is the domain itself rather than the entity declared
     * size.
     *
     * <p>An entity bounding box hangs from its feet and a domain is a ball around its middle, so
     * the registered 32x32 box covers the whole upper hemisphere and none of the lower one: a
     * caster looking down at the floor of their own subspace had it culled out from under them the
     * moment the centre left the frustum.
     */
    @Override
    protected AABB getBoundingBoxForCulling(SpaceSubspaceEntity entity) {
        double reach = entity.radius() + 2.0D;
        return new AABB(entity.getX() - reach, entity.getY() - reach, entity.getZ() - reach,
                entity.getX() + reach, entity.getY() + reach, entity.getZ() + reach);
    }

    @Override
    public void extractRenderState(SpaceSubspaceEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.radius = entity.radius();
        state.age = entity.tickCount + partialTick;
        state.opacity = Math.min(1.0F, state.age / BIRTH_TICKS);
        state.sunLift = SubspaceSun.lift(entity.level().getSkyDarken());

        SubspaceLedger ledger = SubspaceLedger.of(entity.lawOrdinals());
        state.sealed = ledger.sealed();
        state.gravity = ledger.gravityState();
        state.lawCount = ledger.lawCount();
        for (int slot = 0; slot < CATEGORIES; slot++) {
            state.change[slot] = ledger.change(slot);
            state.settle[slot] = entity.lawSettle01(slot, state.age);
        }

        // The camera, not the entity own position, decides everything about cost and about which
        // walls are emitted - a domain is the one effect in the mod you can stand inside.
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distanceSq = camera.distanceToSqr(entity.position());
        state.rung = SubspaceLod.rungFor(state.radius, distanceSq);
        state.inside = distanceSq < state.radius * state.radius;

        SubspaceCrossings crossings = entity.crossings();
        for (int slot = 0; slot < SubspaceCrossings.SLOTS; slot++) {
            state.ripplePhase[slot] = crossings.live(slot, state.age) ? crossings.phase(slot, state.age) : -1.0F;
            state.rippleBearing[slot] = (float) Math.toDegrees(Math.atan2(crossings.x(slot), -crossings.z(slot)));
            state.rippleLatitude[slot] = (float) Math.toDegrees(Math.asin(Mth.clamp(crossings.y(slot), -1.0F, 1.0F)));
        }
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        drawPane(state, poseStack, buffer);
        drawVault(state, poseStack, buffer);
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    /**
     * The wall. One shell from inside, two from outside, the far one coarse because it is read
     * through the near one and they share a silhouette.
     */
    private void drawPane(State state, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(MagicalFxRenderTypes.subspaceShell());
        float radius = state.radius;
        int segments = SubspaceLod.segments(state.rung);
        int rings = SubspaceLod.rings(state.rung);
        int flags = (state.sealed ? 1 : 0) | (state.inside ? 0 : 2);
        int packed = MagicVertex.pack(SubspaceKind.MEMBRANE, sunStep(state), flags, 0.0F, 0, 0);

        if (state.inside) {
            emit(consumer, poseStack, FxMesh.globeInverted(segments, rings), radius, SubspaceOptics.BODY, state.opacity, packed);
            return;
        }
        // Far wall first: the blend is premultiplied and the type does not sort, so emission order
        // is the depth order, and it is already correct without a single comparison.
        int far = Math.min(SubspaceLod.RUNGS - 1, state.rung + 2);
        emit(consumer, poseStack, FxMesh.globeInverted(SubspaceLod.segments(far), SubspaceLod.rings(far)),
                radius, SubspaceOptics.BODY, state.opacity, packed);
        emit(consumer, poseStack, FxMesh.globe(segments, rings), radius, SubspaceOptics.BODY, state.opacity, packed);
    }

    /** Everything built on the wall, on the render type that has no front and no back. */
    private void drawVault(State state, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(MagicalFxRenderTypes.subspaceMark());
        int lift = sunStep(state);
        float radius = state.radius;
        int segments = SubspaceLod.segments(state.rung);
        float spring = (float) SubspaceVault.springLatitudeDegrees(radius);
        float foot = (float) SubspaceVault.footprintLatitudeDegrees(radius);

        // Where the wall meets the world. Everything below this is behind the floor and the depth
        // test has already removed it, so this ring is the one place the two actually touch - and
        // it is the only reading a player looking down ever gets.
        emit(consumer, poseStack, FxMesh.shellBand(segments, foot, RING_HALF, 360.0F), radius,
                SubspaceOptics.GLAZE, state.opacity,
                MagicVertex.pack(SubspaceKind.FOOT, lift, state.gravity, 0.0F, 0, 0));

        // The course the ribs spring from, and gravity read a second time: level is one course,
        // inverted winds the other way and lights from beneath, dissolved breaks into dashes.
        emit(consumer, poseStack, FxMesh.shellBand(segments, spring, RING_HALF, 360.0F), radius,
                SubspaceOptics.GLAZE, state.opacity,
                MagicVertex.pack(SubspaceKind.SPRING, lift, state.gravity, 0.0F, 0, 0));

        emit(consumer, poseStack, FxMesh.shellBand(segments, OCULUS_LATITUDE, RING_HALF, 360.0F), radius,
                SubspaceOptics.GLAZE, state.opacity,
                MagicVertex.pack(SubspaceKind.OCULUS, lift, 0, 0.0F, 0, 0));

        drawRibs(state, poseStack, consumer, lift, spring);
        drawRipples(state, poseStack, consumer, lift);
    }

    /**
     * The twelve. Each one springs from the course at the bearing its category owns and winds up to
     * the oculus; a written law makes its rib heavier, colours both of its lips and hangs a boss on
     * it, and an unwritten one leaves a socket at the same height so the count has a denominator.
     */
    private void drawRibs(State state, PoseStack poseStack, VertexConsumer consumer, int lift, float spring) {
        float radius = state.radius;
        // A domain that has been turned upside down is wound the other way, which is a thing you can
        // see in the silhouette from any distance - unlike a mirrored line, which would be buried.
        float twist = (float) SubspaceVault.twist(state.lawCount)
                * (state.gravity == SubspaceLedger.GRAVITY_FLIP ? -1.0F : 1.0F);
        int rings = Math.max(8, SubspaceLod.rings(state.rung) * 3 / 4);
        float[] rib = FxMesh.shellHelix(rings, RIB_HALF, spring, OCULUS_LATITUDE, twist);
        float[] notch = FxMesh.shellMeridian(2, NOTCH_HALF_SWEEP, -NOTCH_HALF_HEIGHT, NOTCH_HALF_HEIGHT);
        float[] boss = FxMesh.shellBand(2, 0.0F, BOSS_HALF, BOSS_HALF * 2.0F);
        float bossLatitude = (float) SubspaceVault.bossLatitudeDegrees(radius);
        int inverted = state.gravity == SubspaceLedger.GRAVITY_FLIP ? 2 : 0;

        for (int slot = 0; slot < CATEGORIES; slot++) {
            int change = state.change[slot];
            boolean lit = change >= 0;
            int rgb = lit ? HudPalette.change(SpaceRuleChange.values()[change]) : SubspaceOptics.GLAZE;
            float bearing = (float) SubspaceLedger.slotBearingDegrees(slot);
            float settle = lit ? state.settle[slot] : 1.0F;

            place(consumer, poseStack, notch, radius, bearing, spring, rgb, state.opacity,
                    MagicVertex.pack(SubspaceKind.NOTCH, lift, lit ? 1 : 0, 1.0F, 0, 0));
            place(consumer, poseStack, rib, radius, bearing, 0.0F, rgb, state.opacity,
                    MagicVertex.pack(SubspaceKind.RIB, lift, (lit ? 1 : 0) | inverted, settle, 0, 0));
            if (lit) {
                place(consumer, poseStack, boss, radius,
                        (float) SubspaceVault.bossBearingDegrees(slot, radius, state.lawCount), bossLatitude,
                        rgb, state.opacity, MagicVertex.pack(SubspaceKind.BOSS, lift, change, settle, 0, 0));
            }
        }
    }

    /** The only moving part in the domain, and it moves only because something happened. */
    private void drawRipples(State state, PoseStack poseStack, VertexConsumer consumer, int lift) {
        for (int slot = 0; slot < SubspaceCrossings.SLOTS; slot++) {
            float phase = state.ripplePhase[slot];
            if (phase < 0.0F) {
                continue;
            }
            // Quantised so the ring is one of eight cached meshes rather than a rebuild per frame.
            int step = Math.min(RIPPLE_STEPS - 1, (int) (phase * RIPPLE_STEPS));
            float reach = RIPPLE_REACH * (step + 0.5F) / RIPPLE_STEPS;
            float[] ring = FxMesh.shellBand(20, 90.0F - reach, RIPPLE_HALF_WIDTH, 360.0F);
            // The ring is built round the north pole, so swinging the pole onto the crossing takes
            // the same two rotations everything else here uses.
            place(consumer, poseStack, ring, state.radius,
                    state.rippleBearing[slot], state.rippleLatitude[slot] - 90.0F,
                    SubspaceOptics.BURNISH, state.opacity,
                    MagicVertex.pack(SubspaceKind.RIPPLE, lift, 0, phase, 0, 0));
        }
    }

    /**
     * Puts a mesh built at bearing zero somewhere else on the shell.
     *
     * <p>The two rotations are stacked so that the latitude is applied first and the bearing
     * second: a turn about the vertical preserves latitude and a turn about the horizontal does
     * not, so the other order would drag a mark off its own line.
     */
    private static void place(VertexConsumer consumer, PoseStack poseStack, float[] mesh, float radius,
            float bearingDegrees, float latitudeDegrees, int rgb, float opacity, int packed) {
        poseStack.pushPose();
        // Bearing runs east from north and a positive turn about +Y runs the other way.
        poseStack.mulPose(Axis.YP.rotationDegrees(-bearingDegrees));
        poseStack.mulPose(Axis.XP.rotationDegrees(latitudeDegrees));
        emit(consumer, poseStack, mesh, radius, rgb, opacity, packed);
        poseStack.popPose();
    }

    private static void emit(VertexConsumer consumer, PoseStack poseStack, float[] mesh, float radius,
            int rgb, float opacity, int packed) {
        FxMesh.emit(consumer, poseStack.last().pose(), mesh, radius, radius, radius, rgb, opacity, packed);
    }

    /** The sun own height, folded into the six bits the vertex has spare for it. */
    private static int sunStep(State state) {
        return Mth.clamp(Math.round(state.sunLift * 63.0F), 0, 63);
    }

    public static final class State extends EntityRenderState {
        private float radius = 5.0F;
        private float age;
        private float opacity;
        private float sunLift = 1.0F;
        private int rung;
        private int lawCount;
        private boolean inside;
        private boolean sealed;
        private int gravity;
        private final int[] change = new int[CATEGORIES];
        private final float[] settle = new float[CATEGORIES];
        private final float[] ripplePhase = new float[SubspaceCrossings.SLOTS];
        private final float[] rippleBearing = new float[SubspaceCrossings.SLOTS];
        private final float[] rippleLatitude = new float[SubspaceCrossings.SLOTS];
    }
}
