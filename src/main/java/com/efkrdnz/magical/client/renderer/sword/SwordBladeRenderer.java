package com.efkrdnz.magical.client.renderer.sword;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/**
 * One blade, and the one piece of steel the whole school is drawn with.
 *
 * <p>A blade is {@link FxMesh#prism(int) prism(4)} - a diamond section - on
 * {@link MagicalFxRenderTypes#shardBody()}, which is the only depth-writing render type in the mod,
 * so a blade <em>occludes</em> the glow behind it instead of summing with it; over its spine one
 * glow band through {@link FilamentPainter}, in the shape {@code ForgeRibbon.sheath} draws round an
 * arc and for its reason - a solid gave the blade a thickness, it did not stop a blade from being a
 * blade - drawn <b>once per blade and never once per trail copy</b>, because it is the light the
 * steel is giving off and not another copy of the steel. Fifteen quads a blade, twelve blades is
 * 180 against a 60000-quad frame target, and every one of them goes through
 * {@link FxBudget#countQuads(int)}. A Blockbench model would be quads the throttle cannot see,
 * will not demote, and reports to everything else as headroom; the spec refuses one on exactly
 * that measurement.
 *
 * <p><b>The cant is load-bearing.</b> A blade flown point-first along its own flight vector is a
 * one-to-two-pixel vertical line to the person who threw it, because the thrower's eye <em>is</em>
 * the flight line and a long thin thing seen down its own axis projects to its cross-section. A
 * flat plate is worse than a bolt rather than better: it has no symmetry left to collapse into, so
 * it flickers as the perspective divide fights its normal. So every blade is yawed
 * {@link Geometry#CANT_YAW} degrees and rolled {@link Geometry#CANT_ROLL} degrees off its flight
 * line and wears a billboarded head emitted <em>outside</em> the orienting push and pop, so the
 * head is camera-facing in world space rather than in the blade's. It is also the genre: 飞剑
 * travel canted and broadside, never nose-on like arrows. {@code SwordSilhouetteTest} measures the
 * drawn solid against the flight vector and against the volume that actually catches, the way
 * {@code WaveSilhouetteTest} measures the wave.
 *
 * <p>Edge is three bits of palette-ramp index and nothing new on the wire: it already rides
 * {@code EXTRA}, so a twelve-Edge blade reads dark and heavy and a one-Edge blade reads thin
 * without a byte being added anywhere. The section does not grow with it - the drawn steel has to
 * stay inside the volume the raycast catches in, and that volume is a property of the entity and
 * not of the metal.
 */
public final class SwordBladeRenderer extends ProfileRendererShell<SwordBladeEntity> {

    /**
     * The beam shader's reveal is a <em>window</em>, not a fraction drawn: 0.5 is a whole beam and
     * 1.0 has receded to nothing. The sheath is a whole band for as long as the blade exists.
     */
    public static final float BEAM_WHOLE = 0.5F;

    /** What the glow may contribute beside the steel. Well under it: it is light, not edge. */
    private static final float SHEATH_OPACITY = 0.55F;

    /** What the head may contribute. It stands in for the blade seen end-on, not for the blade. */
    private static final float HEAD_OPACITY = 0.75F;

    /** Where a standing blade starts to come apart, as a fraction of its lying life. */
    private static final float DISSOLVE_FROM = 0.85F;

    /**
     * Where the {@code shard_body} shader starts cracking a solid. Below it the blade is whole;
     * driving it to 1.0 dissolves the faces, which is what a blade running out of time should do.
     */
    private static final float INTEGRITY_CRACK = 0.7F;

    public SwordBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends ProfileRendererShell.State {
        public byte bladeState;
        public int edge;
        /** Which way the blade points. A lying blade has none of its own and stands point-down. */
        public Vec3 heading = new Vec3(0.0D, 0.0D, 1.0D);
        public float alpha = 1.0F;
        public float integrity;
    }

    @Override
    public ProfileRendererShell.State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SwordBladeEntity entity, ProfileRendererShell.State base, float partialTick) {
        super.extractRenderState(entity, base, partialTick);
        State state = (State) base;
        // The radius a blade carries is the frame's gameplay reach and not the size of its
        // drawing, and ProfileRendererShell inflates MARK/SWARM/dome silhouettes to whatever
        // radius it is handed. EldritchConstructRenderer zeroes it for the same reason.
        state.radius = 0.0F;
        state.bladeState = entity.state();
        state.edge = entity.edge();
        Vec3 direction = entity.direction();
        boolean lying = state.bladeState == SwordBladeEntity.STATE_SPENT
                || state.bladeState == SwordBladeEntity.STATE_PLANTED;
        // A planted blade never flew, so it has no synced direction at all: it is set into what it
        // came down in, point first, which is downward.
        state.heading = lying || direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : direction;
        // VALUE is the state's own progress - the flight budget while it flies, the lying clock
        // while it stands - so a standing blade gives up its last sixth of life to the dissolve
        // and a flying one never reaches it.
        float progress = lying ? Math.min(1.0F, Math.max(0.0F, entity.value())) : 0.0F;
        float gone = progress <= DISSOLVE_FROM ? 0.0F : (progress - DISSOLVE_FROM) / (1.0F - DISSOLVE_FROM);
        state.alpha = 1.0F - gone * 0.6F;
        // Whole until the dissolve starts, then straight up the shader's crack band to a full
        // dissolve: the blade comes apart rather than fading, which is what running out of time
        // looks like for a thing made of metal.
        state.integrity = gone <= 0.0F ? 0.0F : INTEGRITY_CRACK + gone * (1.0F - INTEGRITY_CRACK);
    }

    @Override
    public void render(ProfileRendererShell.State base, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        super.render(base, pose, buffers, packedLight);
        State state = (State) base;
        FxContext ctx = new FxContext(pose, buffers, state.partialTick,
                entityRenderDispatcher.cameraOrientation(), state.cameraOffset)
                .timing(state.age, state.life, state.seed);
        ctx.detail = FxBudget.detailForDistance(3, state.distanceSqr);
        ctx.lod = FxBudget.lodForDistance(state.distanceSqr);
        blade(ctx, state.heading, state.edge, state.alpha, state.integrity, edgeColor(state.edge));
    }

    // ---- the steel, shared with the Array -------------------------------------------------------

    /**
     * One blade at the current pose, pointing along {@code heading}.
     *
     * <p>Both renderers draw their steel through here, so a blade at rest in the formation and the
     * same blade a tick after it has left its bearing are the same object and cannot drift apart.
     *
     * @param integrity the {@code shard_body} crack channel: 0 is whole, 1 has dissolved
     * @param rgb the colour to draw it in, which is {@link #edgeColor(int)} unless strain has
     *     pulled the whole Array toward cinnabar
     */
    public static void blade(FxContext ctx, Vec3 heading, int edge, float alpha, float integrity, int rgb) {
        if (alpha <= 0.0F) {
            return;
        }
        int packed = MagicVertex.pack(FxKinds.Body.METAL_BANDS.id(), Geometry.SIDES, edgeRamp(edge),
                integrity, ctx.seed, 0);
        VertexConsumer steel = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        PoseStack pose = ctx.pose;
        pose.pushPose();
        FilamentPainter.orientAlong(pose, heading);
        // The cant. The later mulPose acts on a local vector first, so the yaw tilts the blade off
        // the flight line and the roll then turns that lean round the line - which is why the axis
        // is CANT_YAW degrees off the flight vector at every bearing and pitch there is, and why
        // the diamond section never sits square to the screen either.
        pose.mulPose(Axis.ZP.rotationDegrees(Geometry.CANT_ROLL));
        pose.mulPose(Axis.YP.rotationDegrees(Geometry.CANT_YAW));
        pose.translate(0.0D, 0.0D, -Geometry.LENGTH * 0.5D);
        // The glow band over the spine, once for the blade. A crossed pair rather than one plate,
        // for the reason the plate is refused above: two quads at right angles have no view that
        // collapses both of them.
        FilamentPainter.beam(ctx, FxKinds.Filament.BLADE_RIM, Geometry.HALF_WIDTH * Geometry.SHEATH,
                Geometry.LENGTH, rgb, alpha * SHEATH_OPACITY, BEAM_WHOLE, 2, 6);
        pose.pushPose();
        // prism() runs its height up local +Y; the blade runs along local +Z.
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        FxMesh.emit(steel, pose.last().pose(), FxMesh.prism(Geometry.SIDES),
                Geometry.HALF_WIDTH, Geometry.LENGTH, Geometry.HALF_WIDTH, rgb, alpha, packed);
        pose.popPose();
        FxBudget.countQuads(Geometry.QUADS);
        pose.popPose();
        // Outside the orient, so the head is camera-facing in world space. This is the half of the
        // fix the cant cannot do: at the one angle where even a canted blade is nearly end-on, the
        // head is the only thing left with area.
        OrbPainter.billboard(ctx, FxKinds.Orb.PLASMA, Geometry.headRadius(), rgb,
                alpha * HEAD_OPACITY, 0.5F, 3, 8);
    }

    /** Three bits of palette ramp off the Edge already on the wire: 0 is a needle, 7 is a slab. */
    public static int edgeRamp(int edge) {
        int clamped = Math.max(1, Math.min(Geometry.RAMP_FULL_EDGE, edge));
        return Math.round((clamped - 1) * (Geometry.RAMP_STEPS - 1.0F) / (Geometry.RAMP_FULL_EDGE - 1.0F));
    }

    /** Bright pewter at one Edge, the school's own shadow at twelve. Heavy metal reads heavy. */
    public static int edgeColor(int edge) {
        SchoolMaterial material = SchoolMaterial.of(MagicSchool.SWORD);
        return lerpRgb(material.variantColor(1), material.variantColor(2),
                edgeRamp(edge) / (Geometry.RAMP_STEPS - 1.0F));
    }

    /** Channel-wise, because these are two points on one metal ramp and not two separate inks. */
    public static int lerpRgb(int from, int to, float t) {
        float k = Math.max(0.0F, Math.min(1.0F, t));
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (r << 16) | (g << 8) | b;
    }

    // ---- the pure half ---------------------------------------------------------------------------

    /**
     * Every number the blade is drawn from, and the arithmetic that says what the drawing claims.
     *
     * <p>A static nested class rather than methods on the renderer, and deliberately so: loading
     * {@code SwordBladeRenderer$Geometry} does not load {@code SwordBladeRenderer}, so
     * {@code SwordSilhouetteTest} can measure the silhouette without ever bringing
     * {@code EntityRenderer} and the whole client stack into a unit test. Nothing in here touches
     * Minecraft, GL or a pose; it is arithmetic on doubles, the way {@code WaveGeometry} is.
     */
    public static final class Geometry {

        /** A diamond section: four sides, radius 1, put on the blade's cross-section. */
        public static final int SIDES = 4;

        /** Quads {@code prism(SIDES)} emits: four side faces and the two end fans. */
        public static final int QUADS = SIDES * 3;

        /** Blocks along the blade. */
        public static final float LENGTH = 1.6F;

        /** Blocks across it, both ways: a diamond 0.15 by 0.15 by 1.6. */
        public static final float WIDTH = 0.15F;

        public static final float HALF_WIDTH = WIDTH * 0.5F;

        /** How far the glow stands off the steel. Wider than the blade, because a glow is. */
        public static final float SHEATH = 1.6F;

        /**
         * The head, sized against the <em>section</em> and not the length, because what it stands
         * in for is the blade seen end-on and the blade seen end-on is its section.
         */
        public static final float HEAD_SCALE = 1.3F;

        /**
         * Degrees the blade's own axis leans off the flight line.
         *
         * <p>Bounded from both sides and that is the whole of the choice. Too little and the
         * thrower is looking down the blade at a vertical line; too much and the drawn steel
         * hangs outside {@link #CATCH_HALF_EXTENT}, which is the lie that reads as the game not
         * registering a hit. At eighteen degrees a 1.6-block blade spreads 0.49 blocks across the
         * thrower's view and reaches 0.32 blocks off the line it catches within 0.45 of.
         */
        public static final float CANT_YAW = 18.0F;

        /**
         * Degrees the lean is then rolled round the flight line.
         *
         * <p>It does not change how far off the line the blade leans - a roll about an axis leaves
         * that axis alone - it chooses <em>which way round</em> it leans, so a volley of blades all
         * canted the same way does not read as a rack, and it turns the diamond off the screen
         * axes so the section is never a square standing on its corner in every frame.
         */
        public static final float CANT_ROLL = 25.0F;

        /** The floor the cant is held above. Below this the blade is a line to the thrower. */
        public static final float MIN_CANT_DEGREES = 5.0F;

        /** Steps of palette ramp the three bits of Edge buy. */
        public static final int RAMP_STEPS = 8;

        /** The Edge at which a blade is as dark and as heavy as the ramp goes. */
        public static final int RAMP_FULL_EDGE = 12;

        /**
         * How far off its own flight line a blade still catches, in blocks.
         *
         * <p>{@code SwordBladeEntity} is registered {@code .sized(0.3F, 0.3F)} and sweeps its box
         * with {@code SWEEP_SLACK = 0.3}, and every candidate body's box is inflated by the same
         * slack, so 0.15 + 0.3 is what the raycast actually reaches sideways. The drawn steel must
         * sit inside it; the glow and the head deliberately need not, because a glow is light and
         * light is allowed to spill past the edge that cuts.
         */
        public static final double CATCH_HALF_EXTENT = 0.45D;

        /**
         * Blocks a loosed blade covers in one tick, from the skill's own speed in spec section 4.1.
         *
         * <p>The drawn blade must not reach further along the flight line than one step of the
         * raycast, or the picture arrives somewhere the hit test has not been yet.
         */
        public static final double FLIGHT_PER_TICK = 1.8D;

        private Geometry() {
        }

        public static float headRadius() {
            return WIDTH * HEAD_SCALE;
        }

        /**
         * The blade's own axis in world space, for a blade flown along {@code (fx, fy, fz)}.
         *
         * <p>The same composition the renderer builds on the pose stack, written out: orient local
         * +Z along the flight vector, then roll, then yaw - and the later {@code mulPose} acts on a
         * local vector first, so this applies the yaw, then the roll, then the orientation.
         */
        public static double[] bladeAxis(double fx, double fy, double fz) {
            double yaw = Math.toRadians(CANT_YAW);
            double roll = Math.toRadians(CANT_ROLL);
            // The yaw, about +Y: local +Z leans out into +X.
            double x = Math.sin(yaw);
            double y = 0.0D;
            double z = Math.cos(yaw);
            // The roll, about +Z: the lean turns round the flight line.
            double rx = x * Math.cos(roll) - y * Math.sin(roll);
            double ry = x * Math.sin(roll) + y * Math.cos(roll);
            return orient(rx, ry, z, fx, fy, fz);
        }

        /**
         * {@code FilamentPainter.orientAlong} as arithmetic: yaw about +Y, then pitch about +X,
         * so local +Z lands on the flight vector.
         */
        public static double[] orient(double vx, double vy, double vz, double fx, double fy, double fz) {
            double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (length < 1.0E-6D) {
                return new double[] {vx, vy, vz};
            }
            double nx = fx / length;
            double ny = fy / length;
            double nz = fz / length;
            double yaw = Math.atan2(nx, nz);
            double pitch = Math.asin(Math.max(-1.0D, Math.min(1.0D, ny)));
            // Axis.XP.rotation(-pitch) first, then Axis.YP.rotation(yaw).
            double px = vx;
            double py = vy * Math.cos(pitch) + vz * Math.sin(pitch);
            double pz = -vy * Math.sin(pitch) + vz * Math.cos(pitch);
            return new double[] {
                    px * Math.cos(yaw) + pz * Math.sin(yaw),
                    py,
                    -px * Math.sin(yaw) + pz * Math.cos(yaw)};
        }

        /** Degrees between the drawn blade's own axis and the line it is travelling along. */
        public static double cantDegrees(double fx, double fy, double fz) {
            double length = Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (length < 1.0E-6D) {
                return 0.0D;
            }
            double[] axis = bladeAxis(fx, fy, fz);
            double dot = (axis[0] * fx + axis[1] * fy + axis[2] * fz) / length;
            return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, dot))));
        }

        /** The eight corners of the drawn solid, in the flight frame: +Z is the way it is going. */
        public static double[][] corners() {
            double yaw = Math.toRadians(CANT_YAW);
            double roll = Math.toRadians(CANT_ROLL);
            double half = LENGTH * 0.5D;
            double[][] section = {
                    {HALF_WIDTH, 0.0D}, {0.0D, HALF_WIDTH}, {-HALF_WIDTH, 0.0D}, {0.0D, -HALF_WIDTH}};
            double[] ends = {-half, half};
            double[][] out = new double[8][];
            int at = 0;
            for (double[] point : section) {
                for (double along : ends) {
                    // The yaw, about +Y, then the roll, about +Z.
                    double x = point[0] * Math.cos(yaw) + along * Math.sin(yaw);
                    double y = point[1];
                    double z = -point[0] * Math.sin(yaw) + along * Math.cos(yaw);
                    out[at++] = new double[] {
                            x * Math.cos(roll) - y * Math.sin(roll),
                            x * Math.sin(roll) + y * Math.cos(roll),
                            z};
                }
            }
            return out;
        }

        /** How far the drawn steel reaches off the flight line, in blocks. */
        public static double lateralReach() {
            double worst = 0.0D;
            for (double[] corner : corners()) {
                worst = Math.max(worst, Math.hypot(corner[0], corner[1]));
            }
            return worst;
        }

        /** How far it reaches along the flight line, either way, in blocks. */
        public static double axialReach() {
            double worst = 0.0D;
            for (double[] corner : corners()) {
                worst = Math.max(worst, Math.abs(corner[2]));
            }
            return worst;
        }

        /**
         * The area the drawn solid projects onto a viewer looking along {@code (vx, vy, vz)}, in
         * the blade's own frame, where the blade runs along +Z.
         *
         * <p>Half the sum of every face's area times the cosine it turns to the view, which is the
         * projected area of any convex solid. It exists so the claim that a blade is a solid and
         * not a sheet can be measured: a sheet's smallest projection is zero and there is a view
         * from which it is invisible, and that is the whole reason this is a prism.
         */
        public static double silhouetteArea(double vx, double vy, double vz) {
            double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (length < 1.0E-9D) {
                return 0.0D;
            }
            double nx = vx / length;
            double ny = vy / length;
            double nz = vz / length;
            double side = LENGTH * HALF_WIDTH * Math.sqrt(2.0D);
            double cap = 2.0D * HALF_WIDTH * HALF_WIDTH;
            double sum = 0.0D;
            for (int i = 0; i < SIDES; i++) {
                double bearing = Math.toRadians(45.0D + i * 90.0D);
                sum += side * Math.abs(Math.cos(bearing) * nx + Math.sin(bearing) * ny);
            }
            sum += 2.0D * cap * Math.abs(nz);
            return sum * 0.5D;
        }

        /** What the thrower would see of a blade flown point-first: its section, and nothing else. */
        public static double noseOnArea() {
            return silhouetteArea(0.0D, 0.0D, 1.0D);
        }

        /**
         * What the thrower actually sees, with the cant on.
         *
         * <p>The flight line in the blade's own frame is the cant undone, and the roll drops out of
         * it because a roll about the flight line leaves the flight line alone.
         */
        public static double cantedArea() {
            double yaw = Math.toRadians(CANT_YAW);
            return silhouetteArea(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
    }
}
