package com.efkrdnz.magical.client.renderer.sword;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.forge.ForgeView;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.forge.weapon.MagicalWeapons;
import com.efkrdnz.magical.registry.MagicalItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * One blade, and the one thing the whole school is drawn with.
 *
 * <p><b>The steel is Duskfall</b> - {@code magical:item/duskfall}, the one real 3D weapon this mod
 * ships, a 130-cuboid export - drawn through {@code ItemRenderer.renderStatic} the way {@link
 * com.efkrdnz.magical.client.renderer.fx.WrenchedItemRenderer} draws its item. The model's quads
 * are counted into {@link FxBudget} exactly like hand-built ones, so they are headroom nothing
 * else gets to spend twice.
 *
 * <p><b>And nothing else.</b> A blade used to carry a glow along its spine, a glint billboarded on
 * it, a red flush through vanilla's overlay texture as the formation was spent and a white wash as
 * a standing blade ran out of time, and the verdict on all of it was that none of it was a sword.
 * So a blade is the model, lit full bright, and the only things that ever change about it are
 * where it is, which way it points and how big it is: a standing blade out of time closes on
 * nothing rather than fading. What a blade <em>does</em> - landing in a body, meeting a wall - is a
 * small wave of vanilla particles thrown by {@code SwordImpactParticles}, not anything drawn here.
 *
 * <p><b>{@link ItemDisplayContext#NONE} and nothing else.</b> Every other context applies the
 * model's authored display block, and Duskfall's was hand-tuned for a hand and a slot in this mod -
 * {@code ground} alone is a 0.3 scale and a three-unit lift. {@code NONE} is the only context
 * vanilla resolves to {@code ItemTransform.NO_TRANSFORM}. What survives is the {@code
 * translate(-0.5, -0.5, -0.5)} every context gets, which puts the pose origin in the middle of
 * Duskfall's grip. See {@link Geometry#PIVOT_BACK} for why the drawing does not leave it there.
 *
 * <p><b>The cant is load-bearing.</b> A blade flown point-first along its own flight vector
 * collapses toward its cross-section for the person who threw it, because the thrower's eye
 * <em>is</em> the flight line. It is also the genre: 飞剑 travel canted and broadside, never
 * nose-on like arrows. So a flown blade is yawed {@link Geometry#CANT_YAW} degrees and rolled
 * {@link Geometry#CANT_ROLL} degrees off its flight line ({@link #blade}); steel that is held, or
 * that comes up out of the ground, has no flight line to hide along and is drawn as it is pointed
 * ({@link #steel}).
 */
public final class SwordBladeRenderer extends ProfileRendererShell<SwordBladeEntity> {

    /** Where a standing blade starts to close on nothing, as a fraction of its lying life. */
    private static final float DISSOLVE_FROM = 0.85F;

    /**
     * A summoned blade is a conjured thing and lights itself: {@code 0xF000F0} is block 15, sky 15,
     * the same full bright {@code UnwakingSceneryRenderer} hands its figures. A painted texture would
     * otherwise read as a black bar in exactly the places an Array is worth photographing: at night,
     * underground, and against a lit sky.
     */
    private static final int CONJURED_LIGHT = 0xF000F0;

    public SwordBladeRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * The one stack every blade in the game is drawn from.
     *
     * <p>A holder class rather than a field, so the lookup happens on the first blade anyone draws
     * and not when this renderer's class is loaded - the item registry is not filled at that point.
     * An item render reads a stack without writing to it, so one instance serves every blade.
     */
    private static final class Steel {
        static final ItemStack STACK = resolve();

        private static ItemStack resolve() {
            var item = MagicalItems.weapon(MagicalWeapons.DUSKFALL.id());
            // An empty stack rather than a thrown initialiser error: a catalogue that has lost
            // Duskfall should cost the Array its steel, not the whole client its render thread.
            return item == null ? ItemStack.EMPTY : new ItemStack(item.get());
        }

        private Steel() {
        }
    }

    public static final class State extends ProfileRendererShell.State {
        public byte bladeState;
        /** Which way the blade points. A standing blade has none of its own and stands point-down. */
        public Vec3 heading = new Vec3(0.0D, 0.0D, 1.0D);
        public float integrity;
        /** What it flies as: a racked weapon, or empty for Duskfall. */
        public ItemStack arm = ItemStack.EMPTY;
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
        state.integrity = progress <= DISSOLVE_FROM ? 0.0F : (progress - DISSOLVE_FROM) / (1.0F - DISSOLVE_FROM);
        state.arm = entity.arm();
    }

    @Override
    public void render(ProfileRendererShell.State base, PoseStack pose, MultiBufferSource buffers, int packedLight) {
        super.render(base, pose, buffers, packedLight);
        State state = (State) base;
        FxContext ctx = new FxContext(pose, buffers, state.partialTick,
                entityRenderDispatcher.cameraOrientation(), state.cameraOffset)
                .timing(state.age, state.life, state.seed);
        blade(ctx, state.heading, state.integrity, state.arm);
    }

    // ---- the steel, shared with the Array and the painters -----------------------------------------

    /**
     * One flown blade at the current pose, travelling along {@code heading}, canted off its line.
     *
     * <p>The Array draws every sword of its formation through here, so a blade at rest and the same
     * blade a tick after it has left its place are the same object and cannot drift apart.
     *
     * @param integrity how far a blade out of time has come apart: 0 is whole, 1 has gone
     */
    public static void blade(FxContext ctx, Vec3 heading, float integrity) {
        blade(ctx, heading, integrity, ItemStack.EMPTY);
    }

    /** The same blade flying as a racked weapon: {@code arm} empty is Duskfall. */
    public static void blade(FxContext ctx, Vec3 heading, float integrity, ItemStack arm) {
        PoseStack pose = ctx.pose;
        pose.pushPose();
        FilamentPainter.orientAlong(pose, heading);
        // The cant. The later mulPose acts on a local vector first, so the yaw tilts the blade off
        // the flight line and the roll then turns that lean round the line - which is why the axis
        // is CANT_YAW degrees off the flight vector at every bearing and pitch there is.
        pose.mulPose(Axis.ZP.rotationDegrees(Geometry.CANT_ROLL));
        pose.mulPose(Axis.YP.rotationDegrees(Geometry.CANT_YAW));
        model(ctx, Geometry.drawnScale(integrity), arm);
        pose.popPose();
    }

    /**
     * Duskfall pointing along {@code heading} with no cant, at {@code scale}, about the middle of
     * its length: steel that is held or that comes up out of the ground rather than flown.
     */
    public static void steel(FxContext ctx, Vec3 heading, float scale) {
        steel(ctx, heading, scale, ItemStack.EMPTY);
    }

    /** The same, as a racked weapon: {@code arm} empty is Duskfall. */
    public static void steel(FxContext ctx, Vec3 heading, float scale, ItemStack arm) {
        PoseStack pose = ctx.pose;
        pose.pushPose();
        FilamentPainter.orientAlong(pose, heading);
        model(ctx, scale, arm);
        pose.popPose();
    }

    /**
     * The model itself, point down local +Z, centred on the middle of its length: Duskfall, or a
     * racked weapon's icon. A racked Duskfall is Duskfall, because its model is the real thing.
     */
    private static void model(FxContext ctx, float scale, ItemStack arm) {
        if (scale <= 0.0F) {
            return;
        }
        if (arm != null && !arm.isEmpty() && !arm.is(Steel.STACK.getItem())) {
            icon(ctx, scale, arm);
            return;
        }
        if (Steel.STACK.isEmpty()) {
            return;
        }
        PoseStack pose = ctx.pose;
        pose.pushPose();
        // A vanilla item model runs its height up +Y and the blade runs along local +Z, so a
        // quarter turn puts Duskfall's point down the bearing.
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.scale(scale, scale, scale);
        // Inside the scale, so the shift scales with the sword: Duskfall's origin sits in its
        // grip, and a blade pivoting on its grip swings its point through a wide arc like a clock
        // hand every time its bearing moves. On its own middle it turns in place.
        pose.translate(0.0D, -Geometry.PIVOT_BACK, 0.0D);
        Minecraft.getInstance().getItemRenderer().renderStatic(Steel.STACK, ItemDisplayContext.NONE,
                CONJURED_LIGHT, OverlayTexture.NO_OVERLAY, pose, ctx.buffers, Minecraft.getInstance().level, ctx.seed);
        pose.popPose();
        // 780 quads a blade, counted so FxBudget.pressure() falls and every other effect in the
        // frame demotes around the thing the frame is actually of.
        FxBudget.countQuads(Geometry.MODEL_QUADS);
    }

    /**
     * A racked weapon, drawn as the picture the player knows it by: its inventory icon, corner to
     * corner along the bearing. The {@code GUI} context is chosen for that reason - it is the flat
     * icon for every weapon, a trident's included, where the in-hand context hands some of them a
     * 3D model that points nowhere in particular.
     *
     * <p>An icon's blade runs from its lower left to its upper right, so an eighth of a turn about
     * its face lays that diagonal up the item's +Y, and the same quarter turn Duskfall takes lays
     * +Y down the bearing. Sized against Duskfall's own drawn scale, so a dissolving or a Below
     * blade shrinks with the steel it stands in for.
     *
     * <p><b>It turns about its own length to face the viewer.</b> Duskfall is a solid and reads
     * from any side; an icon is a sheet, and a sheet seen along its own plane is a dark line -
     * which is what a Guard formation seen from behind its wielder was, six black bars. So the
     * sheet keeps its long axis on the (canted) bearing and spins round it until its face points
     * at the eye, read off the pose the way {@link ForgeView#eye} reads it for a strike. After the
     * quarter turn the face looks down local -Y, so the turn that points it at an eye at
     * {@code (x, y)} is {@code atan2(x, -y)}.
     */
    private static void icon(FxContext ctx, float scale, ItemStack arm) {
        PoseStack pose = ctx.pose;
        pose.pushPose();
        float[] eye = ForgeView.eye(pose.last().pose());
        pose.mulPose(Axis.ZP.rotation((float) Math.atan2(eye[0], -eye[1])));
        pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
        float size = Geometry.ICON_SCALE * scale / Geometry.SCALE;
        pose.scale(size, size, size);
        Minecraft.getInstance().getItemRenderer().renderStatic(arm, ItemDisplayContext.GUI,
                CONJURED_LIGHT, OverlayTexture.NO_OVERLAY, pose, ctx.buffers, Minecraft.getInstance().level, ctx.seed);
        pose.popPose();
        FxBudget.countQuads(Geometry.ICON_QUADS);
    }

    // ---- the pure half ---------------------------------------------------------------------------

    /**
     * Every number the blade is drawn from, and the arithmetic that says what the drawing claims.
     *
     * <p>A static nested class rather than methods on the renderer, and deliberately so: loading
     * {@code SwordBladeRenderer$Geometry} does not load {@code SwordBladeRenderer}, so
     * {@code SwordSilhouetteTest} and {@code BelowSilhouetteTest} can measure the steel without ever
     * bringing {@code EntityRenderer}, {@code Minecraft} or the item pipeline into a unit test.
     * Nothing in here touches Minecraft, GL or a pose; it is arithmetic on doubles and ints.
     */
    public static final class Geometry {

        /** The model the steel is, by the name vanilla resolves. */
        public static final String MODEL = "magical:item/duskfall";

        /** Vanilla reads a model in sixteenths of a block. */
        public static final double MODEL_UNITS = 16.0D;

        /**
         * Cuboids in {@code duskfall.json}, each six faces, so 780 quads a blade.
         *
         * <p>Not an estimate. {@code SwordSilhouetteTest} counts the elements in the file and fails
         * if this stops matching, because the number is what {@link FxBudget} is told and a quad
         * the budget is not told about reports to everything else in the frame as headroom.
         */
        public static final int MODEL_ELEMENTS = 130;

        public static final int MODEL_QUADS = MODEL_ELEMENTS * 6;

        /**
         * The model's own box, in model units measured from the render origin - which {@code
         * NONE}'s {@code translate(-0.5, -0.5, -0.5)} puts at model {@code (8, 8, 8)}.
         *
         * <p>These are the <em>true</em> bounds and not the {@code from}/{@code to} extremes: all
         * 130 cuboids carry a rotation, and a rotated cuboid reaches outside the box it is written
         * as. The test re-derives all four from the file, so a re-export that lengthens the sword
         * cannot quietly push the drawn steel outside the corridor the raycast catches in.
         *
         * <p>What they say about the sword: the pommel is 7.35 units below the origin and the point
         * 23.95 above it, so Duskfall's authored pivot sits in the middle of its grip - right for a
         * hand, wrong for a levitating blade. It is 31.30 units from pommel to point, which is
         * 1.96 blocks at 1:1, and it is 24 times broader than it is thick.
         */
        public static final double MODEL_MIN_Y = -7.35D;

        public static final double MODEL_MAX_Y = 23.945863094478902D;

        public static final double MODEL_HALF_X = 6.278409467750825D;

        public static final double MODEL_HALF_Z = 1.41D;

        /**
         * Twelve, which is the apex rung's whole complement and the most that can ever stand.
         *
         * <p>It was 24 while Mirror of the Array drew a twin of every station. That passive
         * reflects the wielder's <em>previous stance's Watch</em> now rather than their steel,
         * so it costs no quads at all and the worst frame halved.
         */
        public static final int MAX_BLADES = 12;

        /** What a Sword God's worst frame costs, stated so it cannot be forgotten. */
        public static final int WORST_CASE_QUADS = MODEL_QUADS * MAX_BLADES;

        /**
         * How much of Duskfall a summoned blade is.
         *
         * <p>1:1 is 1.96 blocks of sword, and twelve of those ringing a 1.8-block player is a scrap
         * yard rather than a formation. The ceiling is not taste, though: it is {@link
         * #scaleCeiling()}, the largest scale whose drawn steel still fits inside {@link
         * #CATCH_HALF_EXTENT} once the cant has leaned it over, and that works out at 0.66. This
         * sits at 0.60 - a 1.17-block blade about two thirds of a player tall, with 9% of margin
         * left against a bound that is a gameplay fact rather than a preference.
         */
        public static final float SCALE = 0.60F;

        /**
         * A racked weapon's icon at Duskfall's drawn scale: a sixteen-pixel sprite is a block across
         * at 1:1, and its diagonal at this size is within a few percent of Duskfall's length, so a
         * racked diamond sword and the steel it replaced read as the same size of sword.
         */
        public static final float ICON_SCALE = 0.85F;

        /** Quads a flat item icon costs at most: its two faces and the edge strips round its pixels. */
        public static final int ICON_QUADS = 96;

        /** Blocks from pommel to point, as drawn. The Array's cull box is inflated by it. */
        public static final float LENGTH = (float) ((MODEL_MAX_Y - MODEL_MIN_Y) * SCALE / MODEL_UNITS);

        /** Half the flat of the blade, as drawn. */
        public static final float HALF_BREADTH = (float) (MODEL_HALF_X * SCALE / MODEL_UNITS);

        /** Half the thickness through the blade, as drawn. A sword is nearly a sheet edge-on. */
        public static final float HALF_THICKNESS = (float) (MODEL_HALF_Z * SCALE / MODEL_UNITS);

        /**
         * Blocks to walk back down the model's own +Y to put the middle of its length on the pose
         * origin, <em>before</em> the scale is applied - so the renderer translates by this inside
         * {@code pose.scale} and the shift follows the sword's size for free.
         *
         * <p>Duskfall's origin is in its grip, 0.52 model-blocks below its mid-length. A blade left
         * on that pivot sweeps its point through a 1.5-block arc every time its bearing turns,
         * which reads as a clock hand rather than as a sword aiming; and on the flight side it puts
         * the drawn point well ahead of the entity the raycast is actually stepping.
         */
        public static final float PIVOT_BACK = (float) ((MODEL_MIN_Y + MODEL_MAX_Y) * 0.5D / MODEL_UNITS);

        /**
         * Degrees the blade's own axis leans off the flight line.
         *
         * <p>Bounded from both sides and that is the whole of the choice. Too little and the
         * thrower is looking down the sword at its cross-section; too much and the drawn steel
         * hangs outside {@link #CATCH_HALF_EXTENT}, which is the lie that reads as the game not
         * registering a hit. {@link #cantCeilingDegrees()} is 23.1 for the model at {@link #SCALE},
         * so eighteen keeps a fifth of that in hand.
         */
        public static final float CANT_YAW = 18.0F;

        /**
         * Degrees the lean is then rolled round the flight line.
         *
         * <p>It does not change how far off the line the blade leans - a roll about an axis leaves
         * that axis alone, which is why {@link #lateralReach()} does not depend on it - it chooses
         * <em>which way round</em> it leans, so a volley of blades all canted the same way does not
         * read as a rack, and it keeps the flat of the sword off the screen axes.
         */
        public static final float CANT_ROLL = 25.0F;

        /** The floor the cant is held above. Below this the blade is nearly nose-on again. */
        public static final float MIN_CANT_DEGREES = 5.0F;

        /**
         * How far off its own flight line a blade still catches, in blocks.
         *
         * <p>{@code SwordBladeEntity} is registered {@code .sized(0.3F, 0.3F)} and sweeps its box
         * with {@code SWEEP_SLACK = 0.3}, and every candidate body's box is inflated by the same
         * slack, so 0.15 + 0.3 is what the raycast actually reaches sideways. The drawn steel must
         * sit inside it, or the picture promises a hit the raycast never makes.
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

        /**
         * The scale the steel is actually drawn at, given how far it has come apart.
         *
         * <p>Never above {@link #SCALE}, which is the scale every bound in this class was measured
         * at, and zero once the blade is gone - a conjured thing closes on nothing rather than
         * popping out at full size.
         */
        public static float drawnScale(float integrity) {
            return SCALE * (1.0F - Math.max(0.0F, Math.min(1.0F, integrity)));
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

        /**
         * The eight corners of the model's box, in the flight frame: +Z is the way it is going.
         *
         * <p>The box and not the sword, so every claim measured off it is a bound the drawing
         * cannot beat. In the blade's own frame the flat runs across local X, the thickness through
         * local Y and the length along local Z, which is what the renderer's quarter turn about +X
         * arranges; then the cant's yaw and roll put that frame into the flight one.
         */
        public static double[][] corners() {
            double yaw = Math.toRadians(CANT_YAW);
            double roll = Math.toRadians(CANT_ROLL);
            double half = LENGTH * 0.5D;
            double[][] section = {
                    {HALF_BREADTH, HALF_THICKNESS}, {HALF_BREADTH, -HALF_THICKNESS},
                    {-HALF_BREADTH, HALF_THICKNESS}, {-HALF_BREADTH, -HALF_THICKNESS}};
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
         * The steepest cant {@link #CATCH_HALF_EXTENT} allows the model at {@link #SCALE}, in
         * degrees, or 0 if the sword is too big to fit the corridor at any lean at all.
         *
         * <p>Closed form rather than a search: the worst corner is at {@code HALF_BREADTH *
         * cos(yaw) + halfLength * sin(yaw)} across, with the thickness always in hand, and a sum of
         * a sine and a cosine of the same angle is one sine of a shifted one.
         */
        public static double cantCeilingDegrees() {
            double across = CATCH_HALF_EXTENT * CATCH_HALF_EXTENT - HALF_THICKNESS * HALF_THICKNESS;
            if (across <= 0.0D) {
                return 0.0D;
            }
            double reach = Math.hypot(HALF_BREADTH, LENGTH * 0.5D);
            double sine = Math.sqrt(across) / reach;
            if (sine >= 1.0D) {
                return 90.0D;
            }
            return Math.toDegrees(Math.asin(sine) - Math.atan2(HALF_BREADTH, LENGTH * 0.5D));
        }

        /** The largest {@link #SCALE} whose drawn steel still fits the corridor at this cant. */
        /** Half an icon's diagonal: how far a racked weapon reaches either way from its middle. */
        public static double iconReach() {
            return ICON_SCALE * Math.sqrt(2.0D) * 0.5D;
        }

        public static double scaleCeiling() {
            double yaw = Math.toRadians(CANT_YAW);
            double breadth = MODEL_HALF_X / MODEL_UNITS;
            double thickness = MODEL_HALF_Z / MODEL_UNITS;
            double half = (MODEL_MAX_Y - MODEL_MIN_Y) * 0.5D / MODEL_UNITS;
            double unit = Math.hypot(breadth * Math.cos(yaw) + half * Math.sin(yaw), thickness);
            return CATCH_HALF_EXTENT / unit;
        }
    }
}
