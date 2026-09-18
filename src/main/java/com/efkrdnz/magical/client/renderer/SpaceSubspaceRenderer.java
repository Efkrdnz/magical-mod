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
 * The boundary of a subspace: a wall of cold glass that wears the laws written on it.
 *
 * <p>What was here before drew six unrelated things on {@code RenderType.lightning()} - two shell
 * bands, a hundred and twenty-two sphere rings, five spinning orbit arcs, three rings of glyph
 * ticks and nine wandering glints - all additive, all at a fixed tessellation, all sorted every
 * frame. In daylight that was simultaneously too much and too little: from inside it laid a pale
 * cyan wash over the whole sky, and from thirty blocks outside it was not there at all. Those are
 * the same fault. A constant alpha covers the most area exactly where you are trying to see and
 * delivers the least exactly where a silhouette lives, and no value of it fixes both.
 *
 * <p>So the wall is angle-dependent instead - see {@link SubspaceOptics} - and it darkens rather
 * than adds, which is the one thing that reads over daylight sand as well as over a night sky.
 * Everything else here is an instrument rather than an ornament: a level horizon that says which
 * way gravity runs, one graduated upright standing due north so the radius can be counted off in
 * blocks, and a band of marks along the horizon, one for every law written. An empty domain is a
 * pane of glass with a horizon on it. A fully legislated one is dense enough to read across a
 * field.
 *
 * <p>Nothing moves except a ring where something crossed.
 */
public final class SpaceSubspaceRenderer extends EntityRenderer<SpaceSubspaceEntity, SpaceSubspaceRenderer.State> {

    /** Latitude of the crown cut, where the membrane has thinned to a fifth. */
    private static final float CROWN_LATITUDE = 80.0F;
    private static final float CROWN_HALF_WIDTH = 0.42F;
    private static final float HORIZON_HALF_WIDTH = 0.9F;
    /** The meridian stops short of the crown so the two lines never meet in a corner. */
    private static final float MERIDIAN_TOP = 76.0F;
    private static final float MERIDIAN_HALF_WIDTH = 0.55F;
    /** East, south and west, drawn plainer and stopped lower, so north is still the one line. */
    private static final float CARDINAL_TOP = 40.0F;
    private static final float CARDINAL_HALF_WIDTH = 0.4F;
    private static final float[] CARDINALS = {90.0F, 180.0F, 270.0F};
    private static final float TICK_HALF_WIDTH = 0.42F;
    /** Every fifth block is cut longer, the way any scale a person can read at a glance is. */
    private static final int TICK_EMPHASIS = 5;
    /**
     * Where the writing goes is the ledger's business, not the renderer's - it is the same
     * question as which slot a category owns, and it is pinned by the same test.
     */
    private static final float TICK_SWEEP = (float) SubspaceLedger.TICK_SWEEP_DEGREES;
    private static final float TICK_LONG_SWEEP = (float) SubspaceLedger.TICK_LONG_SWEEP_DEGREES;
    private static final float TICK_OFFSET = (float) SubspaceLedger.TICK_OFFSET_DEGREES;
    private static final float MARK_LATITUDE = (float) SubspaceLedger.MARK_LATITUDE_DEGREES;
    private static final float MARK_HALF_HEIGHT = (float) SubspaceLedger.MARK_HALF_HEIGHT_DEGREES;
    private static final float MARK_SWEEP = (float) SubspaceLedger.SLOT_WIDTH_DEGREES;
    private static final float RIPPLE_HALF_WIDTH = 1.1F;
    /** How far out a crossing ring runs, in degrees from the point it started at. */
    private static final float RIPPLE_REACH = 62.0F;
    /** The ring mesh is cached per step rather than per frame, which is what makes it free. */
    private static final int RIPPLE_STEPS = 8;
    /** The wall fades up over half a second; its radius never takes part, so it is never a bubble. */
    private static final float BIRTH_TICKS = 12.0F;

    public SpaceSubspaceRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /**
     * The box the frustum test uses, which is the domain itself rather than the entity's declared
     * size.
     *
     * <p>An entity's bounding box hangs from its feet and a domain is a ball around its middle, so
     * the registered 32x32 box covers the whole upper hemisphere and none of the lower one: a
     * caster looking down at the floor of their own subspace had it culled out from under them the
     * moment the centre left the frustum. One override, and worth more than any amount of shader
     * work.
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
        for (int slot = 0; slot < state.change.length; slot++) {
            state.change[slot] = ledger.change(slot);
            state.settle[slot] = entity.lawSettle01(slot, state.age);
        }

        // The camera, not the entity's own position, decides everything about cost and about which
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
        drawMembrane(state, poseStack, buffer);
        drawWriting(state, poseStack, buffer);
        poseStack.popPose();
        super.render(state, poseStack, buffer, packedLight);
    }

    /**
     * The wall. One shell from inside, two from outside, the far one coarse because it is read
     * through the near one and they share a silhouette.
     */
    private void drawMembrane(State state, PoseStack poseStack, MultiBufferSource buffer) {
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

    /** Everything written on the wall, on the render type that has no front and no back. */
    private void drawWriting(State state, PoseStack poseStack, MultiBufferSource buffer) {
        VertexConsumer consumer = buffer.getBuffer(MagicalFxRenderTypes.subspaceMark());
        int lift = sunStep(state);
        float radius = state.radius;
        int segments = SubspaceLod.segments(state.rung);

        // The horizon is the gravity readout as well as the level line, because a level line is
        // exactly the thing that says which way is down.
        emit(consumer, poseStack, FxMesh.shellBand(segments, 0.0F, HORIZON_HALF_WIDTH, 360.0F), radius,
                SubspaceOptics.BURNISH, state.opacity,
                MagicVertex.pack(SubspaceKind.HORIZON, lift, state.gravity, 0.0F, 0, 0));

        emit(consumer, poseStack, FxMesh.shellBand(segments, CROWN_LATITUDE, CROWN_HALF_WIDTH, 360.0F), radius,
                SubspaceOptics.CROWN, state.opacity,
                MagicVertex.pack(SubspaceKind.CROWN, lift, 0, 0.0F, 0, 0));

        emit(consumer, poseStack, FxMesh.shellMeridian(SubspaceLod.rings(state.rung) / 2, MERIDIAN_HALF_WIDTH, 0.0F, MERIDIAN_TOP),
                radius, SubspaceOptics.BURNISH, state.opacity,
                MagicVertex.pack(SubspaceKind.MERIDIAN, lift, 0, 0.0F, 0, 0));

        // Three quieter uprights at the other cardinals. One line standing in three hundred and
        // sixty degrees of wall has nothing to be a landmark against; four of them are a compass,
        // and a compass is the least decoration that turns a curved surface into a place.
        float[] cardinal = FxMesh.shellMeridian(SubspaceLod.rings(state.rung) / 3, CARDINAL_HALF_WIDTH, 0.0F, CARDINAL_TOP);
        int cardinalPacked = MagicVertex.pack(SubspaceKind.MERIDIAN, lift, 1, 0.0F, 0, 0);
        for (float bearing : CARDINALS) {
            place(consumer, poseStack, cardinal, radius, bearing, 0.0F, SubspaceOptics.BURNISH, state.opacity, cardinalPacked);
        }

        drawGraduations(state, poseStack, consumer, lift);
        drawLaws(state, poseStack, consumer, lift);
        drawRipples(state, poseStack, consumer, lift);
    }

    /**
     * One graduation per block of radius up the meridian, so the size of a domain is a thing you
     * count rather than a thing you estimate. Reading a wall in blocks is the difference between
     * knowing you can clear it and hoping.
     */
    private void drawGraduations(State state, PoseStack poseStack, VertexConsumer consumer, int lift) {
        int packed = MagicVertex.pack(SubspaceKind.TICK, lift, 0, 0.0F, 0, 0);
        float top = Mth.sin(MERIDIAN_TOP * Mth.DEG_TO_RAD);
        for (int block = 1; block <= Math.floor(state.radius * top); block++) {
            float sweep = block % TICK_EMPHASIS == 0 ? TICK_LONG_SWEEP : TICK_SWEEP;
            float[] tick = FxMesh.shellBand(1, 0.0F, TICK_HALF_WIDTH, sweep);
            float latitude = (float) Math.toDegrees(Math.asin(block / state.radius));
            place(consumer, poseStack, tick, state.radius, TICK_OFFSET, latitude,
                    SubspaceOptics.BURNISH, state.opacity, packed);
        }
    }

    /**
     * One mark per law, each in the slot its category owns, on a single arc centred north. The
     * meridian falls in the gap at the middle of that arc, so the marks read as two hands of six
     * either side of a known direction - a legislated domain can be counted from outside without
     * knowing what any one mark means.
     */
    private void drawLaws(State state, PoseStack poseStack, VertexConsumer consumer, int lift) {
        float[] mark = FxMesh.shellBand(2, 0.0F, MARK_HALF_HEIGHT, MARK_SWEEP);
        for (int slot = 0; slot < state.change.length; slot++) {
            int change = state.change[slot];
            if (change < 0) {
                continue;
            }
            int packed = MagicVertex.pack(SubspaceKind.MARK, lift, change, state.settle[slot], 0, 0);
            place(consumer, poseStack, mark, state.radius,
                    (float) SubspaceLedger.slotBearingDegrees(slot), MARK_LATITUDE,
                    HudPalette.change(SpaceRuleChange.values()[change]), state.opacity, packed);
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

    /** The sun's own height, folded into the six bits the vertex has spare for it. */
    private static int sunStep(State state) {
        return Mth.clamp(Math.round(state.sunLift * 63.0F), 0, 63);
    }

    public static final class State extends EntityRenderState {
        private float radius = 5.0F;
        private float age;
        private float opacity;
        private float sunLift = 1.0F;
        private int rung;
        private boolean inside;
        private boolean sealed;
        private int gravity;
        private final int[] change = new int[SpaceRuleCategory.values().length];
        private final float[] settle = new float[SpaceRuleCategory.values().length];
        private final float[] ripplePhase = new float[SubspaceCrossings.SLOTS];
        private final float[] rippleBearing = new float[SubspaceCrossings.SLOTS];
        private final float[] rippleLatitude = new float[SubspaceCrossings.SLOTS];
    }
}
