package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.hud.ClientCooldowns;
import com.efkrdnz.magical.client.hud.MagicalClientConfig;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.skill.sword.TheBearingSkill;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordRules;
import com.efkrdnz.magical.magic.sword.SwordService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Holding the Bearing plots the Array and lets go of the bearings you marked.
 *
 * <p>This overlay exists for one reason and it is not "the kit needs a screen": <b>six of your
 * stations are behind your head, and the crosshair cannot reach them.</b> Call the Blade writes
 * with the crosshair and nothing else in the class writes at all, so without this the half of the
 * Array behind the wielder is unreadable and permanent. Reading the shape you authored, and
 * unwriting a bearing you cannot look at, are the two things the rest of the kit genuinely
 * cannot do.
 *
 * <p><b>Frameless here means a scrim, not bare text</b>, and it is not a style preference. A drop
 * shadow preserves the shape of a glyph and does nothing whatever for its contrast: against real
 * noon sand (220,209,165) the mod's own {@code TEXT_PRIMARY} lands at 1.45:1 and
 * {@code TEXT_MUTED} at 2.06:1, so an unbacked highlight reads <em>fainter</em> than the rows it
 * is supposed to be picked out from and the highlight inverts. Every frameless surface in the mod
 * dims the world first for that reason - {@code SpaceManipulationOverlay} at 0xA6,
 * {@link MagicWheelOverlay} at 0x8C, {@code FractureOverlay} at 0x76 - and this one does it at
 * 0x8C, which takes that sand to (71,86,89). There is still no panel, no plate and no frame
 * anywhere in it, and every string carries vanilla's drop shadow.
 *
 * <p>Three readings share the plot and each owns a channel that cannot be mistaken for another,
 * the rule the loadout switcher settled. <b>Where</b> a station points is <b>position</b> on the
 * disc - angle is yaw, radius is pitch - and nothing else ever moves a mark. <b>Which</b> one the
 * scroll is on is <b>shape</b>: a ring standing off it, which survives being read by someone who
 * cannot tell two pewters apart. <b>Whether a release will pull it</b> is <b>hue</b>, cinnabar,
 * plus a cross struck through the mark, so it is legible on a frame where the ring and the hue
 * land on the same mark - which is the common case, not the corner one.
 *
 * <p>The geometry is all in {@link SwordBearingLayout}, where a test can reach it without a
 * render context. Nothing here is drawn without a measured width.
 */
public final class SwordBearingOverlay {

    /** The school's pewter, off {@code the_bearing}'s own definition colour. */
    private static final int PEWTER = 0xD4DDE4;

    /** Cinnabar: the bill past the draw, and a bearing a release is going to pull. */
    private static final int CINNABAR = 0xD4402F;

    private static final int INK_BRIGHT = 0xFFFFFF;
    private static final int INK_REST = 0xC2CCD9;
    private static final int INK_QUIET = 0x8494A6;
    private static final int GUIDE = 0x6E7F90;
    private static final int SHADOW = 0x0A0D14;

    /** The world, dimmed. The same weight the loadout switcher settled on, for the same reason. */
    private static final int SCRIM = 0x8C060B14;

    private static final float FADE_STEP = 0.22F;

    /** How far the block slides in from the left as it opens. */
    private static final int SLIDE = 5;

    /** Dots round the rim: one per yaw step, so the rim doubles as the bearing scale. */
    private static final int RIM_DOTS = Station.YAW_STEPS;

    /** Dots round the horizon ring, which is a guide and not a scale, so it is finer. */
    private static final int HORIZON_DOTS = 48;

    private static boolean active;

    /**
     * The bearing the focus ring is on, held as yaw and pitch rather than as a slot.
     *
     * <p>A slot renumbers the moment anything sheds, and at Sword God an Array can shed while the
     * hold is open. An index-based focus slides onto whatever took that place - the Causal
     * Anchor's lesson, learned there against a crowd of bodies and true here against a list that
     * renumbers under the ring.
     */
    private static int focusYaw = -1;
    private static int focusPitch;

    /** Bearings marked for pulling, by {@code key}. Slots are resolved at release and never before. */
    private static final Set<Integer> MARKED = new HashSet<>();

    private static float fade;
    private static float fadePrev;

    /** Where the ring is travelling from, in the plot's own polar coordinates. */
    private static boolean gliding;
    private static double glideFraction;
    private static double glideBearing;
    private static float glideTicks;

    private SwordBearingOverlay() {}

    // ---- the hold -------------------------------------------------------------------------------

    public static boolean isActive() {
        return active;
    }

    /** The key went down. The focus opens on the first mark clockwise from straight ahead. */
    public static void open() {
        active = true;
        fade = 0.0F;
        fadePrev = 0.0F;
        gliding = false;
        glideTicks = 0.0F;
        MARKED.clear();
        focusYaw = -1;
        focusPitch = 0;
        List<Station> stations = stations();
        int[] order = SwordBearingLayout.bearingOrder(stations);
        if (order.length > 0) {
            settleFocus(stations.get(order[0]));
        }
    }

    /**
     * Once a client tick while the key is held: keep the fade moving and keep the focus on the
     * bearing it was on rather than on the slot that bearing happened to be in.
     */
    public static void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft == null || minecraft.player == null || minecraft.level == null
                || minecraft.screen != null) {
            cancel();
            return;
        }
        fadePrev = fade;
        fade = Math.min(1.0F, fade + FADE_STEP);
        glideTicks += 1.0F;

        List<Station> stations = stations();
        if (slotOfFocus(stations) < 0) {
            int[] order = SwordBearingLayout.bearingOrder(stations);
            if (order.length == 0) {
                focusYaw = -1;
            } else {
                settleFocus(stations.get(order[0]));
            }
        }
        // A bearing that went away while it was marked cannot be pulled, and leaving it in the set
        // would put a stale count on the last line of the reading.
        MARKED.removeIf(key -> !holds(stations, key));
    }

    /** Ends the hold without pulling anything - a screen opening over it, a world unloading. */
    public static void cancel() {
        active = false;
        fade = 0.0F;
        fadePrev = 0.0F;
        gliding = false;
        MARKED.clear();
        focusYaw = -1;
    }

    /**
     * The key came up: the whole release at once, as a mask of slot indices.
     *
     * <p><b>The mask is built here, from the live Array, rather than accumulated as bits while the
     * hold was open.</b> Slots renumber whenever a station leaves the list, so a bit set three
     * seconds ago can name a bearing the wielder never marked - and the server's re-check cannot
     * catch that, because the bit is perfectly in range. Marks are held as bearings for exactly
     * this reason and turned into slots once, at the last possible moment.
     *
     * <p>Zero is a release that pulls nothing, which {@code TheBearingSkill.pull} refuses and does
     * not charge. The caller sends it: {@code MagicalNetwork.sendPullStations(mask)}.
     *
     * @return bits 0..{@code TheBearingSkill.MASK_BITS}-1, bit n being slot n of
     *     {@code SwordArray.stations()}
     */
    public static int release() {
        if (!active) {
            return 0;
        }
        List<Station> stations = stations();
        int mask = 0;
        int cap = Math.min(stations.size(), TheBearingSkill.MASK_BITS);
        for (int slot = 0; slot < cap; slot++) {
            if (MARKED.contains(key(stations.get(slot)))) {
                mask |= 1 << slot;
            }
        }
        cancel();
        return mask;
    }

    // ---- input ----------------------------------------------------------------------------------

    /**
     * Scroll walks the focus round the disc.
     *
     * @return true when the scroll was consumed, so the hotbar does not move as well
     */
    public static boolean handleScroll(double delta) {
        if (!active) {
            return false;
        }
        if (delta != 0.0D) {
            walk(delta > 0.0D ? -1 : 1);
        }
        return true;
    }

    /**
     * Left walks the focus the way it does on every other hold overlay; right marks.
     *
     * <p>Right rather than left because marking is the verb this hold exists for, and the dearest
     * verb gets the button that is not also a way of doing something else. Only a press is
     * claimed: swallowing the matching release would leave whatever the press belonged to
     * half-done elsewhere, which is why the other overlays let it through too.
     */
    public static boolean handleMouse(int button, int action) {
        if (!active || action != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            walk(1);
            return true;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (focusYaw >= 0) {
                int key = key(focusYaw, focusPitch);
                if (!MARKED.remove(key)) {
                    MARKED.add(key);
                }
            }
            return true;
        }
        return false;
    }

    private static void walk(int step) {
        List<Station> stations = stations();
        int[] order = SwordBearingLayout.bearingOrder(stations);
        if (order.length == 0) {
            return;
        }
        int at = 0;
        for (int i = 0; i < order.length; i++) {
            Station station = stations.get(order[i]);
            if (station.yaw() == focusYaw && station.pitch() == focusPitch) {
                at = i;
                break;
            }
        }
        Station next = stations.get(order[Math.floorMod(at + step, order.length)]);
        beginGlide();
        settleFocus(next);
    }

    private static void beginGlide() {
        if (focusYaw < 0) {
            return;
        }
        // Where the ring is standing right now, so a second scroll part way through the first one
        // starts from where it had actually got to rather than from where it set off.
        double[] at = ringAt(0.0F);
        glideFraction = at[0];
        glideBearing = at[1];
        gliding = true;
        glideTicks = 0.0F;
    }

    private static void settleFocus(Station station) {
        focusYaw = station.yaw();
        focusPitch = station.pitch();
    }

    // ---- the draw -------------------------------------------------------------------------------

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.player == null || minecraft.level == null) {
            return;
        }
        float shown = Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), fadePrev, fade);
        if (shown <= 0.01F) {
            return;
        }
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int alpha = Math.round(shown * 255.0F);

        graphics.fill(0, 0, guiWidth, guiHeight,
                MagicalGuiStyle.withAlpha(SCRIM, Math.round(shown * (SCRIM >>> 24))));

        // The block arrives from the left rather than only fading up. With no plate under it,
        // alpha alone reads as the terrain showing through the marks instead of as a plot opening.
        boolean still = MagicalClientConfig.current().reducedMotion();
        int slide = still ? 0 : Math.round(SLIDE * (1.0F - SwordBearingLayout.easeOut(shown)));

        Rect block = SwordBearingLayout.block(guiWidth, guiHeight);
        int ox = block.x() - slide;
        int oy = block.y();

        Rect disc = SwordBearingLayout.disc(guiWidth, guiHeight);
        drawGuides(graphics, disc, ox, oy, alpha);

        SwordArray array = array();
        SwordRules rules = rules();
        List<Station> stations = array.stations();
        drawMarks(graphics, disc, stations, rules.maxEdge(), ox, oy, alpha);
        drawFocus(graphics, disc, stations, rules.maxEdge(), ox, oy, alpha, still, partialTick);

        drawCardinals(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha);
        drawReading(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, array, rules, stations);
    }

    /**
     * The rim, the horizon and the zenith: the three things that make the disc a projection rather
     * than a scatter of dots.
     *
     * <p>The rim carries one dot per yaw step, so it is also the bearing scale; the horizon ring is
     * finer because it is only a guide - it says where the frame plane is, which is the line a
     * station is above or below.
     */
    private static void drawGuides(GuiGraphics graphics, Rect disc, int ox, int oy, int alpha) {
        double rim = SwordBearingLayout.radiusFraction(Station.PITCH_MIN);
        for (int i = 0; i < RIM_DOTS; i++) {
            plot(graphics, SwordBearingLayout.dot("rim", disc, rim, i * 360.0D / RIM_DOTS, 1),
                    ox, oy, GUIDE, alpha, 0.55F);
        }
        double horizon = SwordBearingLayout.radiusFraction(0);
        for (int i = 0; i < HORIZON_DOTS; i++) {
            plot(graphics, SwordBearingLayout.dot("horizon", disc, horizon, i * 360.0D / HORIZON_DOTS, 1),
                    ox, oy, GUIDE, alpha, 0.30F);
        }
        // Straight up, where the lattice stops and no station can ever be.
        int cx = ox + disc.x() + disc.w() / 2;
        int cy = oy + disc.y() + disc.h() / 2;
        graphics.fill(cx - 2, cy, cx + 3, cy + 1, ink(GUIDE, alpha, 0.45F));
        graphics.fill(cx, cy - 2, cx + 1, cy + 3, ink(GUIDE, alpha, 0.45F));
    }

    /**
     * One mark a station, sized by its Edge and brightened by its reach.
     *
     * <p>An emptied bearing is drawn hollow rather than left out: Ward and a shed take a station's
     * metal without unwriting it, it still counts against the station cap, and pulling it back is
     * exactly what this overlay is for.
     */
    private static void drawMarks(GuiGraphics graphics, Rect disc, List<Station> stations, int maxEdge,
            int ox, int oy, int alpha) {
        for (int slot = 0; slot < stations.size(); slot++) {
            Station station = stations.get(slot);
            Rect mark = SwordBearingLayout.mark(disc, slot, station, maxEdge);
            boolean marked = MARKED.contains(key(station));
            int rgb = marked ? CINNABAR : MagicalGuiStyle.brighten(0xFF000000 | PEWTER, reachGain(station)) & 0xFFFFFF;
            plot(graphics, mark, ox + 1, oy + 1, SHADOW, alpha, 0.7F);
            if (station.manned()) {
                plot(graphics, mark, ox, oy, rgb, alpha, 1.0F);
            } else {
                hollow(graphics, mark, ox, oy, rgb, alpha);
            }
            if (marked) {
                strike(graphics, mark, ox, oy, alpha);
            }
        }
    }

    /** The ring that says where the scroll is, gliding from the mark it left. */
    private static void drawFocus(GuiGraphics graphics, Rect disc, List<Station> stations, int maxEdge,
            int ox, int oy, int alpha, boolean still, float partialTick) {
        int slot = slotOfFocus(stations);
        if (slot < 0) {
            return;
        }
        int size = SwordBearingLayout.markSize(stations.get(slot).edge(), maxEdge);
        double[] at = still ? settled() : ringAt(partialTick);
        Rect ring = SwordBearingLayout.focusRing(
                SwordBearingLayout.glidingMark(disc, at[0], at[1], size));
        hollow(graphics, ring, ox, oy, INK_BRIGHT, alpha);
    }

    private static void drawCardinals(GuiGraphics graphics, Minecraft minecraft, int guiWidth, int guiHeight,
            int ox, int oy, int alpha) {
        String[] keys = {"bearing.magical.ahead", "bearing.magical.right",
                "bearing.magical.behind", "bearing.magical.left"};
        int limit = SwordBearingLayout.cardinalLimit(guiWidth);
        for (int quarter = 0; quarter < SwordBearingLayout.CARD_COUNT; quarter++) {
            String word = minecraft.font.plainSubstrByWidth(
                    Component.translatable(keys[quarter]).getString(), limit);
            int measured = minecraft.font.width(word);
            Rect at = SwordBearingLayout.cardinal(guiWidth, guiHeight, quarter, measured);
            graphics.drawString(minecraft.font, word, ox + at.x(), oy + at.y(),
                    MagicalGuiStyle.withAlpha(0xFF000000 | INK_QUIET, alpha), true);
        }
    }

    /**
     * Three lines: what the shape costs, what the focus is, and what letting go would do.
     *
     * <p>The last line is reserved whether or not a release would do anything, and says up front
     * when it would be refused for cooldown - a chooser that lets you weigh six bearings and then
     * says no is worse than one that never opened.
     */
    private static void drawReading(GuiGraphics graphics, Minecraft minecraft, int guiWidth, int guiHeight,
            int ox, int oy, int alpha, SwordArray array, SwordRules rules, List<Station> stations) {
        int bill = array.bill();
        line(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, 0,
                Component.translatable("bearing.magical.bill", bill, rules.draw()),
                bill > rules.draw() ? CINNABAR : INK_REST);

        int slot = slotOfFocus(stations);
        if (slot < 0) {
            line(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, 1,
                    Component.translatable("bearing.magical.empty"), INK_REST);
            line(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, 2,
                    Component.translatable("bearing.magical.write"), INK_QUIET);
            return;
        }
        Station station = stations.get(slot);
        line(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, 1,
                Component.translatable("bearing.magical.station",
                        (int) Math.round(station.yaw() * Station.YAW_STEP_DEGREES),
                        (int) Math.round(station.pitch() * Station.PITCH_STEP_DEGREES),
                        station.reach(), station.edge()),
                INK_BRIGHT);

        int cooling = ClientCooldowns.remaining(MagicContent.THE_BEARING.id());
        if (cooling > 0) {
            line(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, 2,
                    Component.translatable("bearing.magical.cooling", Mth.ceil(cooling / 20.0F)), CINNABAR);
        } else if (MARKED.isEmpty()) {
            line(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, 2,
                    Component.translatable("bearing.magical.unmarked"), INK_QUIET);
        } else {
            line(graphics, minecraft, guiWidth, guiHeight, ox, oy, alpha, 2,
                    Component.translatable("bearing.magical.marked", MARKED.size()), CINNABAR);
        }
    }

    private static void line(GuiGraphics graphics, Minecraft minecraft, int guiWidth, int guiHeight,
            int ox, int oy, int alpha, int index, Component text, int rgb) {
        Rect at = SwordBearingLayout.reading(guiWidth, guiHeight, index);
        String said = minecraft.font.plainSubstrByWidth(text.getString(),
                SwordBearingLayout.readingLimit(guiWidth));
        graphics.drawString(minecraft.font, said, ox + at.x(), oy + at.y(),
                MagicalGuiStyle.withAlpha(0xFF000000 | rgb, alpha), true);
    }

    // ---- painting helpers -----------------------------------------------------------------------

    private static void plot(GuiGraphics graphics, Rect rect, int ox, int oy, int rgb, int alpha, float weight) {
        graphics.fill(ox + rect.x(), oy + rect.y(), ox + rect.right(), oy + rect.bottom(),
                ink(rgb, alpha, weight));
    }

    /** A square outline, which is how a mark says "no metal" and a ring says "focused". */
    private static void hollow(GuiGraphics graphics, Rect rect, int ox, int oy, int rgb, int alpha) {
        int x0 = ox + rect.x();
        int y0 = oy + rect.y();
        int x1 = ox + rect.right();
        int y1 = oy + rect.bottom();
        int colour = ink(rgb, alpha, 1.0F);
        graphics.fill(x0, y0, x1, y0 + 1, colour);
        graphics.fill(x0, y1 - 1, x1, y1, colour);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, colour);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, colour);
    }

    /** A cross through a marked bearing: hue is the reading, shape is the proof of it. */
    private static void strike(GuiGraphics graphics, Rect rect, int ox, int oy, int alpha) {
        int cx = ox + rect.x() + rect.w() / 2;
        int cy = oy + rect.y() + rect.h() / 2;
        int reach = rect.w() / 2 + 2;
        int colour = ink(CINNABAR, alpha, 1.0F);
        graphics.fill(cx - reach, cy, cx + reach + 1, cy + 1, colour);
        graphics.fill(cx, cy - reach, cx + 1, cy + reach + 1, colour);
    }

    private static int ink(int rgb, int alpha, float weight) {
        return MagicalGuiStyle.withAlpha(0xFF000000 | rgb,
                Math.round(Mth.clamp(weight, 0.0F, 1.0F) * alpha));
    }

    /** Reach is brightness: a blade six blocks out is the brightest thing on its bearing. */
    private static float reachGain(Station station) {
        return 0.62F + 0.38F * (station.reach() / (float) Station.REACH_MAX);
    }

    // ---- the focus, in polar --------------------------------------------------------------------

    /**
     * Where the ring stands this frame: on its mark, or part way there.
     *
     * <p>Kept in the plot's own polar coordinates rather than in pixels, because the disc is sized
     * from the GUI and a glide held in pixels would jump the instant the window was resized
     * underneath it.
     *
     * @return {radius fraction, bearing in degrees}
     */
    private static double[] ringAt(float partialTick) {
        double[] home = settled();
        if (!gliding) {
            return home;
        }
        double fraction = home[0];
        double bearing = home[1];
        float t = Math.min(1.0F,
                (glideTicks + Mth.clamp(partialTick, 0.0F, 1.0F)) / SwordBearingLayout.FOCUS_GLIDE_TICKS);
        float e = SwordBearingLayout.ease(t);
        // The short way round, or a step from yaw 23 to yaw 0 sends the ring all the way back
        // round the disc for a bearing one step away.
        double delta = Mth.wrapDegrees(bearing - glideBearing);
        return new double[] {glideFraction + (fraction - glideFraction) * e, glideBearing + delta * e};
    }

    /** Where the ring belongs with nothing moving: the focused bearing, or the dead centre. */
    private static double[] settled() {
        return new double[] {SwordBearingLayout.radiusFraction(focusPitch),
                SwordBearingLayout.bearingDegrees(focusYaw)};
    }

    // ---- the Array ------------------------------------------------------------------------------

    private static SwordArray array() {
        return ClientMagicState.get().swordArray();
    }

    /**
     * The rung's rules, taken from class progress rather than off the Array.
     *
     * <p>{@code SwordArray.save} writes no rules at all - no enum ordinal is written anywhere in
     * this kit - so the copy that arrives over the wire always stands up on
     * {@link SwordRules#SUMMONER} whoever it belongs to. The server fixes its own copy in
     * {@code SwordService.refreshRung}; the client has to ask the same question of the same
     * synced class progress, or a Sword God reads their own draw as 24.
     *
     * <p>{@code SwordArrayRenderer} answers the same question by <em>inferring</em> the smallest
     * rung a shape could legally have been built under, and it is right to: it draws other
     * people's Arrays off an entity, where their class progress is not on the wire and never will
     * be. This overlay only ever draws the wielder's own, so it can read the truth instead of
     * evidence about it - and it must, because the number it prints is the bill the wielder is
     * about to make a decision against.
     */
    private static SwordRules rules() {
        return SwordService.rulesFor(ClientMagicState.get());
    }

    private static List<Station> stations() {
        return array().stations();
    }

    private static int slotOfFocus(List<Station> stations) {
        if (focusYaw < 0) {
            return -1;
        }
        for (int slot = 0; slot < stations.size(); slot++) {
            Station station = stations.get(slot);
            if (station.yaw() == focusYaw && station.pitch() == focusPitch) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean holds(List<Station> stations, int key) {
        for (Station station : stations) {
            if (key(station) == key) {
                return true;
            }
        }
        return false;
    }

    /** A bearing, and only a bearing: a top-up changes a station's Edge and not which one it is. */
    private static int key(Station station) {
        return key(station.yaw(), station.pitch());
    }

    private static int key(int yaw, int pitch) {
        return yaw * 16 + (pitch - Station.PITCH_MIN);
    }
}
