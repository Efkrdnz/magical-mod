package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.hud.ClientCooldowns;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.causality.Anchor;
import com.efkrdnz.magical.magic.causality.WeaveReview;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Holding the Causal Anchor offers the bodies in reach and marks the one you let go on.
 *
 * <p>The Mark is the door out of your own causality and the dearest thing on the board: three
 * causes weigh 3 apiece because of it and two conditions are dead without it. Spending that on
 * whatever happened to be under the crosshair at the instant of a keypress was the one press in the
 * Authority you could least afford to fumble - a creeper drifting through the ray, a chicken in
 * front of the duellist. So it is chosen rather than pressed, like everything else this Authority
 * authors.
 *
 * <p>Three readings, three channels that cannot be confused (the rule the loadout switcher settled).
 * <b>Where</b> a body is, is the ring drawn round it out in the world, through terrain, so cover
 * cannot hide your own candidate from you. <b>Which</b> one is focused, is position along the rail
 * of pips plus the ring shape - solid for the focused one, dashed for the rest - so it survives
 * being read at a glance and being read by someone who cannot tell the two ambers apart.
 * <b>What it would cost you</b> is the words, and they are the only thing here the world cannot
 * show: what already wears the Mark, and how many pins on your board are asleep waiting for one.
 */
public final class CausalAnchorOverlay {

    private static final int ACCENT = 0xE8A33D;
    private static final int MARKED = 0xFFC85C;
    private static final int SCRIM = 0x8C060B14;
    private static final int TEXT_BRIGHT = 0xFFFFFF;
    private static final int TEXT_NEAR = 0xC9D8E6;
    private static final int TEXT_MUTED = 0x8494A6;
    private static final int BLANK_INK = 0x3A4450;
    private static final int SHADOW = 0x0A0D14;
    private static final int WARN = 0xFFB86C;

    /**
     * How far off the look a body may be and still be offered, as a dot product.
     *
     * <p>Just past a right angle. A body directly behind the wielder is not a thing anyone is
     * pointing at, and offering it would put a pip on the rail whose ring is nowhere on screen - the
     * one case where the three readings could disagree. The focused body is exempt, so turning your
     * head a little while you decide never drops the body you had already settled on.
     */
    private static final double FRONT_ENOUGH = -0.15D;

    private static boolean active;
    private static int focusId = -1;
    private static final List<Integer> OFFERED = new ArrayList<>();

    private CausalAnchorOverlay() {}

    public static boolean active() {
        return active;
    }

    /** The ids on offer this frame, in rail order. The world renderer reads the same list. */
    public static List<Integer> offered() {
        return OFFERED;
    }

    public static int focusId() {
        return focusId;
    }

    public static void begin(Minecraft minecraft) {
        active = true;
        focusId = -1;
        OFFERED.clear();
        tick(minecraft);
        // Whatever is nearest the crosshair, which makes the ordinary case - look at it, tap the
        // key - behave exactly as the bare press did. The hold only costs you something when you
        // wanted something other than what you were looking at.
        if (!OFFERED.isEmpty()) {
            focusId = OFFERED.get(0);
        }
    }

    /** Letting go marks the focused body. The server re-checks every word of this. */
    public static void finish() {
        if (active && focusId >= 0) {
            MagicalNetwork.sendAnchorTarget(focusId);
        } else if (active) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("message.magical.weave_nothing_in_reach"), true);
            }
        }
        cancel();
    }

    /** Ends the hold without marking anything - a screen opening over it, a world unloading. */
    public static void cancel() {
        active = false;
        focusId = -1;
        OFFERED.clear();
    }

    /**
     * Re-gathers the bodies on offer, keeping the focus on the same body rather than the same slot.
     *
     * <p>A list that reshuffles under a cursor is how a hold overlay marks the wrong thing: walk
     * two steps while deciding and an index-based focus slides onto whatever took that place.
     */
    public static void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            cancel();
            return;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle().normalize();
        List<LivingEntity> found = new ArrayList<>(minecraft.level.getEntitiesOfClass(LivingEntity.class,
                minecraft.player.getBoundingBox().inflate(Anchor.REACH),
                candidate -> candidate.isAlive() && candidate != minecraft.player
                        && candidate.position().distanceTo(eye) <= Anchor.REACH
                        && (candidate.getId() == focusId || offAxis(eye, look, candidate) >= FRONT_ENOUGH)));
        found.sort(Comparator.comparingDouble(candidate -> -offAxis(eye, look, candidate)));
        OFFERED.clear();
        for (LivingEntity living : found) {
            if (OFFERED.size() >= CausalAnchorLayout.MAX_CANDIDATES) {
                break;
            }
            OFFERED.add(living.getId());
        }
        // The body that was focused keeps its place even if the crowd pushed it past the cap.
        if (focusId >= 0 && !OFFERED.contains(focusId)) {
            if (minecraft.level.getEntity(focusId) instanceof LivingEntity living && living.isAlive()) {
                if (OFFERED.size() >= CausalAnchorLayout.MAX_CANDIDATES) {
                    OFFERED.remove(OFFERED.size() - 1);
                }
                OFFERED.add(focusId);
            } else {
                focusId = -1;
            }
        }
        if (focusId < 0 && !OFFERED.isEmpty()) {
            focusId = OFFERED.get(0);
        }
    }

    /** How aligned a body is with the look, 1 straight ahead and -1 straight behind. */
    private static double offAxis(Vec3 eye, Vec3 look, Entity candidate) {
        Vec3 toward = candidate.getBoundingBox().getCenter().subtract(eye);
        double length = toward.length();
        return length < 1.0E-4D ? 1.0D : look.dot(toward.scale(1.0D / length));
    }

    public static boolean handleScroll(double delta) {
        if (!active || delta == 0.0D) {
            return active;
        }
        walk(delta > 0.0D ? -1 : 1);
        return true;
    }

    /**
     * Left and right walk the focus, as they do on every other hold overlay.
     *
     * <p>Only a press is claimed. Swallowing the matching release would leave whatever the press
     * belonged to half-done elsewhere, which is why the other overlays let it through too.
     */
    public static boolean handleMouseButton(int button, int action) {
        if (!active || action != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            walk(1);
            return true;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            walk(-1);
            return true;
        }
        return false;
    }

    private static void walk(int step) {
        if (OFFERED.isEmpty()) {
            return;
        }
        int at = Math.max(0, OFFERED.indexOf(focusId));
        focusId = OFFERED.get(Math.floorMod(at + step, OFFERED.size()));
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!active || minecraft.player == null || minecraft.level == null) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        graphics.fill(0, 0, w, h, SCRIM);

        List<Rect> pips = CausalAnchorLayout.pips(w, h, OFFERED.size());
        for (int i = 0; i < pips.size(); i++) {
            Rect pip = pips.get(i);
            boolean lit = OFFERED.get(i) == focusId;
            boolean worn = wearsTheMark(minecraft, OFFERED.get(i));
            graphics.fill(pip.x() + 1, pip.y() + 1, pip.right() + 1, pip.bottom() + 1, ink(SHADOW, 0.7F));
            graphics.fill(pip.x(), pip.y(), pip.right(), pip.bottom(),
                    ink(lit ? TEXT_BRIGHT : worn ? MARKED : BLANK_INK, lit ? 1.0F : 0.85F));
        }

        LivingEntity focused = focused(minecraft);
        if (focused == null) {
            centred(graphics, minecraft, 0, Component.translatable("anchor.magical.nothing"), TEXT_NEAR);
            centred(graphics, minecraft, 1,
                    Component.translatable("anchor.magical.reach", (int) Anchor.REACH), TEXT_MUTED);
            return;
        }
        int range = Mth.ceil(focused.position().distanceTo(minecraft.player.getEyePosition()));
        centred(graphics, minecraft, 0,
                Component.translatable("anchor.magical.body", focused.getDisplayName(), range), TEXT_BRIGHT);
        boolean worn = wearsTheMark(minecraft, focusId);
        centred(graphics, minecraft, 1,
                Component.translatable(worn ? "anchor.magical.worn" : "anchor.magical.unworn"),
                worn ? MARKED : TEXT_MUTED);
        int wakes = waking();
        centred(graphics, minecraft, 2, wakingText(wakes), wakes > 0 ? ACCENT : TEXT_MUTED);
        // A chooser that lets you weigh three bodies and then refuses the release is worse than one
        // that never opened, so the last line says up front whether letting go will do anything.
        int cooling = ClientCooldowns.remaining(MagicContent.CAUSAL_ANCHOR.id());
        centred(graphics, minecraft, 3, cooling > 0
                        ? Component.translatable("anchor.magical.cooling", Mth.ceil(cooling / 20.0F))
                        : Component.translatable("anchor.magical.pick",
                                Math.max(0, OFFERED.indexOf(focusId)) + 1, OFFERED.size()),
                cooling > 0 ? WARN : TEXT_MUTED);
    }

    private static LivingEntity focused(Minecraft minecraft) {
        if (focusId < 0 || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getEntity(focusId) instanceof LivingEntity living ? living : null;
    }

    /**
     * How many pins are asleep for want of a Mark.
     *
     * <p>The one reading the world cannot give you, and the reason this is a Causality overlay
     * rather than a target picker: it answers whether marking anything at all does something for
     * you, which on a board with no marked pin on it is honestly no.
     */
    private static int waking() {
        var state = ClientMagicState.get();
        if (state == null) {
            return 0;
        }
        int count = 0;
        for (WeaveReview.Issue issue : WeaveReview.issues(state.weave(), false)) {
            if (issue.kind() == WeaveReview.Kind.WANTS_MARK) {
                count++;
            }
        }
        return count;
    }

    private static Component wakingText(int count) {
        if (count <= 0) {
            return Component.translatable("anchor.magical.wakes_none");
        }
        return count == 1
                ? Component.translatable("anchor.magical.wakes_one")
                : Component.translatable("anchor.magical.wakes", count);
    }

    private static boolean wearsTheMark(Minecraft minecraft, int entityId) {
        var state = ClientMagicState.get();
        return state != null && minecraft.level != null
                && state.anchor().set(minecraft.level.getGameTime())
                && state.anchor().entityId() == entityId;
    }

    private static void centred(GuiGraphics graphics, Minecraft minecraft, int index, Component text, int rgb) {
        int w = graphics.guiWidth();
        Rect line = CausalAnchorLayout.line(w, graphics.guiHeight(), index);
        String said = minecraft.font.plainSubstrByWidth(text.getString(), CausalAnchorLayout.textLimit(w));
        graphics.drawString(minecraft.font, said,
                line.x() + (line.w() - minecraft.font.width(said)) / 2, line.y(), ink(rgb, 1.0F), true);
    }

    private static int ink(int rgb, float alpha) {
        return (Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
    }
}
