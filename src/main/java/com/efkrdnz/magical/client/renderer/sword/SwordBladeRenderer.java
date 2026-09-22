package com.efkrdnz.magical.client.renderer.sword;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.ProfileRendererShell;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.forge.weapon.MagicalWeapons;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
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
 * One blade, and the one piece of steel the whole school is drawn with.
 *
 * <p><b>The steel is Duskfall.</b> This used to be {@code prism(4)} - a 0.15 by 1.6 diamond needle
 * on a crack shader - and the argument for it was that a Blockbench model would be quads the frame
 * throttle cannot see. That argument lost, because what a four-sided prism actually looks like on
 * screen is a flat dark domino with one blown highlight on it: no point, no taper, no crossguard,
 * nothing that says <em>sword</em> in a class whose entire subject is swords. So a blade is now the
 * one real 3D weapon this mod already ships - {@code magical:item/duskfall}, a 130-cuboid export -
 * drawn through {@code ItemRenderer.renderStatic} the way {@link
 * com.efkrdnz.magical.client.renderer.fx.WrenchedItemRenderer} draws its item. The throttle's
 * objection is answered rather than ignored: the model's quads are counted into {@link FxBudget}
 * exactly like hand-built ones, so they are headroom nothing else gets to spend twice.
 *
 * <p><b>{@link ItemDisplayContext#NONE} and nothing else.</b> Every other context applies the
 * model's authored display block, and Duskfall's was hand-tuned for a hand and a slot in this mod -
 * {@code ground} alone is a 0.3 scale and a three-unit lift, {@code fixed} a -35 degree roll. A
 * blade hanging in the air on a bearing owns its own orientation, and {@code NONE} is the only
 * context vanilla resolves to {@code ItemTransform.NO_TRANSFORM}. What survives is the {@code
 * translate(-0.5, -0.5, -0.5)} every context gets, which puts the pose origin at model
 * {@code (8, 8, 8)} - the middle of Duskfall's grip. See {@link Geometry#PIVOT_BACK} for why the
 * drawing does not leave it there.
 *
 * <p><b>The cant is load-bearing and survives the swap.</b> A blade flown point-first along its own
 * flight vector collapses toward its cross-section for the person who threw it, because the
 * thrower's eye <em>is</em> the flight line. It is also the genre: 飞剑 travel canted and
 * broadside, never nose-on like arrows. So every blade is yawed {@link Geometry#CANT_YAW} degrees
 * and rolled {@link Geometry#CANT_ROLL} degrees off its flight line and wears a billboarded glint
 * emitted <em>outside</em> the orienting push and pop, so the glint is camera-facing in world space
 * rather than in the blade's.
 *
 * <p><b>Edge and strain moved onto the light, because the steel can no longer carry them.</b>
 * {@code renderStatic} takes no tint, and vanilla's one tint route - a quad's {@code tintindex}
 * multiplied by the render state's tint layers - is dead here because Duskfall's 780 faces declare
 * no tint index. So the palette reading that used to be the metal's own colour is now the aura
 * around it, on two channels at once: {@link Geometry#edgeColor(int)} is its hue and {@link
 * Geometry#sheathHalfWidth(int)} is how far it stands off the steel, so a twelve-Edge blade is
 * dark <em>and</em> haloed and a one-Edge blade is bright and tight. Strain arrives already mixed
 * into that colour by the Array and rides both. The steel itself gets the one channel an item
 * render does give us - vanilla's overlay texture, whose red row is what a hurt mob flashes and
 * whose white column is what a mob flashes as it dies - as a rung at about half strain and a wash
 * for a blade running out of time.
 */
public final class SwordBladeRenderer extends ProfileRendererShell<SwordBladeEntity> {

    /**
     * The beam shader's reveal is a <em>window</em>, not a fraction drawn: 0.5 is a whole beam and
     * 1.0 has receded to nothing. The aura is a whole band for as long as the blade exists.
     */
    public static final float BEAM_WHOLE = 0.5F;

    /**
     * What the aura may contribute beside the steel. Well under it: it is light, not edge.
     *
     * <p>0.55 when the blade was a four-sided prism, because the aura was doing half the work of
     * making a thin dark shape visible at all. A textured model has its own silhouette from every
     * angle, so the aura is back to being only the Edge tell - and at 0.55 on an additive pass
     * beside an opaque model it was a blown white slab with a sword lost inside it.
     */
    private static final float SHEATH_OPACITY = 0.15F;

    /**
     * What the glint may contribute.
     *
     * <p>It was the reading that survived a prism seen edge-on, and it was priced for that job.
     * Duskfall has real thickness and a guard, so there is no angle at which the sword disappears
     * and nothing for the glint to stand in for; it is kept, small, as the highlight on the steel
     * rather than as a substitute for it.
     */
    private static final float HEAD_OPACITY = 0.20F;

    /** Where a standing blade starts to come apart, as a fraction of its lying life. */
    private static final float DISSOLVE_FROM = 0.85F;

    /**
     * A summoned blade is a conjured thing and lights itself: {@code 0xF000F0} is block 15, sky 15,
     * the same full bright {@code UnwakingSceneryRenderer} hands its figures. It is not laziness
     * about a light probe. Everything else this school draws is additive and therefore already
     * independent of the world's light, and the one thing here that is not - a painted texture -
     * would otherwise read as a black bar in exactly the places an Array is worth photographing:
     * at night, underground, and against a lit sky.
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
     * Nothing ever mutates it, and an item render reads a stack without writing to it, so one
     * instance serves all twenty-four blades of a frame instead of twenty-four allocations.
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
        // Straight up to a full dissolve, because integrity now drives a wash and a shrink rather
        // than the shard_body crack band: the steel goes white and closes on nothing instead of
        // fading, which is what running out of time looks like for a conjured thing.
        state.integrity = gone;
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
        blade(ctx, state.heading, state.edge, state.alpha, state.integrity, Geometry.edgeColor(state.edge));
    }

    // ---- the steel, shared with the Array -------------------------------------------------------

    /**
     * One blade at the current pose, pointing along {@code heading}.
     *
     * <p>Both renderers draw their steel through here, so a blade at rest in the formation and the
     * same blade a tick after it has left its bearing are the same object and cannot drift apart.
     *
     * @param alpha how much of a blade this is: 1 for a real one, less for a Mirror twin. The aura
     *     and the glint take it directly; the steel cannot fade, so it takes it as a pallor
     *     through {@link Geometry#ghost(float, float)}
     * @param integrity how far a blade out of time has come apart: 0 is whole, 1 has gone
     * @param rgb the colour to draw the light in, which is {@link Geometry#edgeColor(int)} unless
     *     strain has pulled the whole Array toward cinnabar
     */
    public static void blade(FxContext ctx, Vec3 heading, int edge, float alpha, float integrity, int rgb) {
        if (alpha <= 0.0F) {
            return;
        }
        PoseStack pose = ctx.pose;
        pose.pushPose();
        FilamentPainter.orientAlong(pose, heading);
        // The cant. The later mulPose acts on a local vector first, so the yaw tilts the blade off
        // the flight line and the roll then turns that lean round the line - which is why the axis
        // is CANT_YAW degrees off the flight vector at every bearing and pitch there is.
        pose.mulPose(Axis.ZP.rotationDegrees(Geometry.CANT_ROLL));
        pose.mulPose(Axis.YP.rotationDegrees(Geometry.CANT_YAW));

        // The aura over the spine, once for the blade and never once per trail copy, because it is
        // the light the steel is giving off and not another copy of the steel. A crossed pair
        // rather than one plate: two quads at right angles have no view that collapses both.
        pose.pushPose();
        pose.translate(0.0D, 0.0D, -Geometry.LENGTH * 0.5D);
        FilamentPainter.beam(ctx, FxKinds.Filament.BLADE_RIM, Geometry.sheathHalfWidth(edge),
                Geometry.LENGTH, rgb, alpha * SHEATH_OPACITY, BEAM_WHOLE, 2, 6);
        pose.popPose();

        float drawn = Geometry.drawnScale(integrity);
        if (!Steel.STACK.isEmpty() && drawn > 0.0F) {
            pose.pushPose();
            // A vanilla item model runs its height up +Y and the blade runs along local +Z, so the
            // same quarter turn the prism needed puts Duskfall's point down the bearing.
            pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            pose.scale(drawn, drawn, drawn);
            // Inside the scale, so the shift scales with the sword: Duskfall's origin sits in its
            // grip, and a blade pivoting on its grip swings its point through a 1.5-block arc like
            // a clock hand every time its bearing moves. On its own middle it turns in place.
            pose.translate(0.0D, -Geometry.PIVOT_BACK, 0.0D);
            int overlay = OverlayTexture.pack(Geometry.whiteOut(Geometry.ghost(alpha, integrity)),
                    Geometry.steelFlushes(rgb) ? OverlayTexture.RED_OVERLAY_V : OverlayTexture.WHITE_OVERLAY_V);
            Minecraft.getInstance().getItemRenderer().renderStatic(Steel.STACK, ItemDisplayContext.NONE,
                    CONJURED_LIGHT, overlay, pose, ctx.buffers, Minecraft.getInstance().level, ctx.seed);
            pose.popPose();
            // 780 quads a blade and up to 24 blades is Geometry.WORST_CASE_QUADS - 31% of the
            // 60000-quad frame target, and dear enough to say out loud rather than to hide by
            // drawing fewer swords than the Array actually holds. Counting it is what keeps it
            // honest: FxBudget.pressure() falls, and every other effect in the frame demotes
            // around the thing the frame is actually of.
            FxBudget.countQuads(Geometry.MODEL_QUADS);
        }
        pose.popPose();
        // Outside the orient, so the glint is camera-facing in world space. This is the half of the
        // reading the cant cannot do: at the one angle where even a canted sword is nearly edge-on,
        // the glint is the only thing left carrying the Edge's colour.
        OrbPainter.billboard(ctx, FxKinds.Orb.PLASMA, Geometry.headRadius(), rgb,
                alpha * HEAD_OPACITY, 0.5F, 3, 8);
    }

    /** Three bits of palette ramp off the Edge already on the wire: 0 is a needle, 7 is a slab. */
    public static int edgeRamp(int edge) {
        return Geometry.edgeRamp(edge);
    }

    /** Bright pewter at one Edge, the school's own shadow at twelve. Heavy metal reads heavy. */
    public static int edgeColor(int edge) {
        return Geometry.edgeColor(edge);
    }

    /** Channel-wise, because these are two points on one metal ramp and not two separate inks. */
    public static int lerpRgb(int from, int to, float t) {
        return Geometry.lerpRgb(from, to, t);
    }

    // ---- the pure half ---------------------------------------------------------------------------

    /**
     * Every number the blade is drawn from, and the arithmetic that says what the drawing claims.
     *
     * <p>A static nested class rather than methods on the renderer, and deliberately so: loading
     * {@code SwordBladeRenderer$Geometry} does not load {@code SwordBladeRenderer}, so
     * {@code SwordSilhouetteTest} can measure the silhouette and the readings without ever bringing
     * {@code EntityRenderer}, {@code Minecraft} or the item pipeline into a unit test. Nothing in
     * here touches Minecraft, GL or a pose; it is arithmetic on doubles and ints, the way
     * {@code WaveGeometry} is.
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

        /** Twelve stations, and the Mirror passive draws a twin of every one of them. */
        public static final int MAX_BLADES = 24;

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
         * sit inside it; the aura and the glint deliberately need not, because those are light and
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

        /** The tightest the aura ever sits, as a multiple of the steel's own half-breadth. */
        public static final float SHEATH_MIN = 1.15F;

        /** The widest it ever stands off, at full Edge. Heavy metal carries a heavier halo. */
        public static final float SHEATH_MAX = 1.45F;

        /** The glint, sized against the flat of the blade: a highlight on it, not a lamp beside it. */
        public static final float HEAD_SCALE = 0.35F;

        /**
         * How warm a colour has to read before the steel itself flushes.
         *
         * <p>Warmth is the red channel's lead over the better of the other two, so it is a property
         * of the colour rather than of which red the Array happens to mix toward, and every cool
         * pewter on the SWORD ramp starts well negative. Sixty puts the crossing at 0.44 strain for
         * a one-Edge blade and 0.46 for a twelve-Edge one - near enough the same rung at both ends
         * that a heavy blade does not warn later than a thin one.
         */
        public static final int FLUSH_WARMTH = 60;

        /** The far end of vanilla's white overlay column, where the wash is at its strongest. */
        public static final int OVERLAY_U_FULL = 15;

        private Geometry() {
        }

        /** The glint's radius in blocks. */
        public static float headRadius() {
            return HALF_BREADTH * HEAD_SCALE;
        }

        /**
         * How far the aura stands off the blade's centreline at this Edge, in blocks.
         *
         * <p>Never inside {@link #HALF_BREADTH}, or the light would be hidden by the sword it is
         * lighting and the whole Edge reading would go with it.
         */
        public static float sheathHalfWidth(int edge) {
            float ramp = edgeRamp(edge) / (RAMP_STEPS - 1.0F);
            return HALF_BREADTH * (SHEATH_MIN + (SHEATH_MAX - SHEATH_MIN) * ramp);
        }

        /** Three bits of palette ramp off the Edge already on the wire: 0 is a needle, 7 is a slab. */
        public static int edgeRamp(int edge) {
            int clamped = Math.max(1, Math.min(RAMP_FULL_EDGE, edge));
            return Math.round((clamped - 1) * (RAMP_STEPS - 1.0F) / (RAMP_FULL_EDGE - 1.0F));
        }

        /** Bright pewter at one Edge, the school's own shadow at twelve. Heavy metal reads heavy. */
        public static int edgeColor(int edge) {
            SchoolMaterial material = SchoolMaterial.of(MagicSchool.SWORD);
            return lerpRgb(material.variantColor(1), material.variantColor(2),
                    edgeRamp(edge) / (RAMP_STEPS - 1.0F));
        }

        /** Channel-wise, because these are two points on one metal ramp and not two separate inks. */
        public static int lerpRgb(int from, int to, float t) {
            float k = Math.max(0.0F, Math.min(1.0F, t));
            int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
            int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
            int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
            return (r << 16) | (g << 8) | b;
        }

        /** The red channel's lead over the better of the other two. Negative for every pewter. */
        public static int warmth(int rgb) {
            return ((rgb >> 16) & 0xFF) - Math.max((rgb >> 8) & 0xFF, rgb & 0xFF);
        }

        /**
         * Whether the steel itself takes vanilla's red overlay row.
         *
         * <p>A rung rather than a ramp, because that row is one fixed red and the overlay's only
         * continuous axis is already spent on the wash. The continuous half of the strain reading
         * is the aura, which takes the mixed colour whole.
         */
        public static boolean steelFlushes(int rgb) {
            return warmth(rgb) >= FLUSH_WARMTH;
        }

        /**
         * How little of a blade this is, as one number: a Mirror twin is half-real and a blade out
         * of time is on its way to nothing, and to a picture that cannot fade those are the same
         * complaint. The worse of the two wins, so a dissolving twin does not read as more solid
         * than a dissolving blade.
         */
        public static float ghost(float alpha, float integrity) {
            return Math.max(0.0F, Math.min(1.0F, Math.max(1.0F - alpha, integrity)));
        }

        /**
         * Vanilla's overlay u for that pallor: column 0 is the no-white end, 15 the far end.
         *
         * <p>{@code OverlayTexture.pack(0, WHITE_OVERLAY_V)} is exactly {@code NO_OVERLAY}, so a
         * solid whole blade asks for nothing and pays nothing.
         */
        public static int whiteOut(float pallor) {
            return Math.round(Math.max(0.0F, Math.min(1.0F, pallor)) * OVERLAY_U_FULL);
        }

        /**
         * The scale the steel is actually drawn at, given how far it has come apart.
         *
         * <p>Never above {@link #SCALE}, which is the scale every bound in this class was measured
         * at, and zero once the blade is gone - a conjured thing closes on nothing rather than
         * popping out at full size, which is what the {@code shard_body} crack band used to do
         * before the steel became a model that cannot crack.
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
