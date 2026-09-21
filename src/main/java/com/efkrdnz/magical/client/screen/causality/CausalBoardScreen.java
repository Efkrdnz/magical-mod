package com.efkrdnz.magical.client.screen.causality;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.hud.HudQuiet;
import com.efkrdnz.magical.client.hud.MagicalClientConfig;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.causality.Cause;
import com.efkrdnz.magical.magic.causality.CausalNode;
import com.efkrdnz.magical.magic.causality.Condition;
import com.efkrdnz.magical.magic.causality.Effect;
import com.efkrdnz.magical.magic.causality.Modifier;
import com.efkrdnz.magical.magic.causality.NodeKind;
import com.efkrdnz.magical.magic.causality.Paradox;
import com.efkrdnz.magical.magic.causality.Scope;
import com.efkrdnz.magical.magic.causality.Weave;
import com.efkrdnz.magical.magic.causality.WeaveReview;
import com.efkrdnz.magical.magic.causality.WeaveText;
import com.efkrdnz.magical.network.MagicalNetwork;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Causal Board: a wall of pins and string, over the world.
 *
 * <p>Drawn the way the Grimoire and the Manipulate Space selector are - no panel, no plate and no
 * frame. The world is dimmed and everything is glyphs, string and text on it, every string carrying
 * vanilla drop shadow, which is what lets it sit on daylight terrain unbacked. The modded HUD
 * stands down while it is open ({@link HudQuiet}).
 *
 * <p>A pin is a glyph in the colour of its kind with its name beside it. A wire is a bowed line
 * from one pin right-hand mark to another left-hand one, drawn with the same one-pixel shadow the
 * text has, because a line over bright terrain needs the contrast for exactly the reason a letter
 * does. Nothing is boxed and nothing is plated: the reading comes from shape, hue and position, and
 * the scrim is what makes all three legible.
 *
 * <p>Everything is one gesture. Drag a glyph out of the palette onto the board to pin it; drag a
 * pin to move it; drag from a pin right-hand mark to another pin to run string between them; right
 * click a pin to take it down, or the middle of a piece of string to cut it. Click a pin to select
 * it, and the inspector line beneath carries its number, where its consequence lands and which
 * tools it wears. The reading under that says the selected chain in words and names the first thing
 * wrong with the board.
 *
 * <p>Edits live on a <b>draft</b> until Save sends it, and the draft is only reloaded from the state
 * while there is nothing pending, so a mana tick arriving mid-edit clobbers nothing.
 */
public final class CausalBoardScreen extends Screen implements HudDebug.Captured, HudQuiet {

    /** The Authority own light. */
    private static final int ACCENT = 0xE8A33D;
    /** The world is dimmed rather than covered; the same scrim as the Grimoire and the selector. */
    private static final int SCRIM = 0xA6060B14;
    private static final int TEXT_BRIGHT = 0xFFFFFF;
    private static final int TEXT_NEAR = 0xC9D8E6;
    private static final int TEXT_MUTED = 0x8494A6;
    private static final int TEXT_FAINT = 0x55626F;
    /** What a glyph fades toward, and the colour of an empty mark: the dimmed world behind it. */
    private static final int BLANK_INK = 0x3A4450;
    /** Under every wire and every glyph, one pixel down and right, exactly as the font does it. */
    private static final int SHADOW = 0x0A0D14;
    private static final int WARN_RED = 0xFF6B7B;
    private static final int WARN_AMBER = 0xFFB86C;

    private static final float FADE_IN_TICKS = 4.0F;
    private static final float FOCUS_SLIDE_TICKS = 5.0F;
    /** How far a press must travel before it is a drag rather than a click. */
    private static final double DRAG_THRESHOLD = 3.0D;
    /** Segments a piece of string is drawn in. Enough to read as a curve, few enough to be cheap. */
    private static final int WIRE_STEPS = 40;
    /** Pixels between one dot of string and the next, whatever the run is. */
    private static final double DOT_SPACING = 6.0D;
    private static final float GHOST_SCALE = 1.25F;

    private final Weave draft = new Weave();

    private NodeKind palette = NodeKind.CAUSE;
    private NodeKind focusFrom = NodeKind.CAUSE;
    private float focusAt = -999.0F;

    private int selected = -1;
    private int dragPin = -1;
    private int wireFrom = -1;
    private int paletteDrag = -1;
    private boolean dragging;
    private boolean dirty;
    private double pressX;
    private double pressY;
    private double mouseX;
    private double mouseY;
    private int grabDx;
    private int grabDy;

    private float openedAt;
    private float lastFrame;
    private String refusal = "";
    private float refusedAt = -999.0F;

    private CausalBoardScreen() {
        super(Component.translatable("screen.magical.causal_board"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CausalBoardScreen());
    }

    @Override
    protected void init() {
        openedAt = now();
        lastFrame = openedAt;
        reload();
    }

    /** The saved board, copied onto the draft. Skipped while there is unsaved writing. */
    private void reload() {
        if (dirty) {
            return;
        }
        draft.copyFrom(state().weave());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static PlayerMagicState state() {
        return ClientMagicState.get();
    }

    private static float now() {
        return Minecraft.getInstance().level == null ? 0.0F : Minecraft.getInstance().level.getGameTime()
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** Reduced motion cuts every animation on this screen to its final frame. */
    private static float motion(float t) {
        return MagicalClientConfig.current().reducedMotion() ? 1.0F : t;
    }

    private static float easeOut(float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - clamped) * (1.0F - clamped);
    }

    private static int ink(int rgb, float alpha) {
        return (Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int from, int to, float t) {
        float k = Mth.clamp(t, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(k, from & 0xFF, to & 0xFF));
        return (r << 16) | (g << 8) | b;
    }

    /** The block, and the two numbers everything inside it is placed from. */
    private record Frame(int x0, int y0, int width, int boardHeight) {}

    private Frame frame() {
        Rect block = CausalBoardLayout.block(width, height);
        return new Frame(block.x(), block.y(), block.w(), CausalBoardLayout.boardHeight(height));
    }

    // ---- drawing ----------------------------------------------------------------------------------

    /** Only the scrim: no blur and no vanilla gradient, so the world reads through as it does under the Grimoire. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partial) {
        float fade = motion(easeOut((now() - openedAt) / FADE_IN_TICKS));
        g.fill(0, 0, width, height, ink(SCRIM & 0xFFFFFF, (SCRIM >>> 24) / 255.0F * fade));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        super.render(g, mx, my, partial);
        mouseX = mx;
        mouseY = my;
        float time = now();
        lastFrame = time;
        float fade = motion(easeOut((time - openedAt) / FADE_IN_TICKS));
        Frame f = frame();
        double lx = mx - f.x0();
        double ly = my - f.y0();

        paintKinds(g, f, time, fade, lx, ly);
        paintPalette(g, f, fade, lx, ly);
        paintWires(g, f, fade, lx, ly);
        paintPins(g, f, fade, lx, ly);
        paintCaption(g, f, fade, lx, ly);
        paintInspector(g, f, fade, lx, ly);
        paintReading(g, f, fade, time);
        if (dragging && paletteDrag >= 0) {
            paintGhost(g);
        }
    }

    /** Causes, Conditions, Effects, with the mark sliding to whichever is showing. */
    private void paintKinds(GuiGraphics g, Frame f, float time, float fade, double lx, double ly) {
        List<Rect> rects = kindRects(f);
        int hovered = CausalBoardLayout.indexAt(rects, lx, ly);
        for (int i = 0; i < CausalBoardLayout.KIND_COUNT; i++) {
            NodeKind kind = NodeKind.values()[i];
            Rect r = rects.get(i);
            int rgb = kind == palette ? CausalGlyphs.tint(kind) : hovered == i ? TEXT_NEAR : TEXT_FAINT;
            String label = font.plainSubstrByWidth(kindWord(kind), r.w());
            g.drawString(font, label, f.x0() + r.x(), f.y0() + r.y(), ink(rgb, fade), true);
        }
        float travel = motion(easeOut((time - focusAt) / FOCUS_SLIDE_TICKS));
        Rect from = CausalBoardLayout.kindRule(rects.get(focusFrom.ordinal()));
        Rect to = CausalBoardLayout.kindRule(rects.get(palette.ordinal()));
        int x = Math.round(Mth.lerp(travel, from.x(), to.x()));
        int w = Math.round(Mth.lerp(travel, from.w(), to.w()));
        g.fill(f.x0() + x, f.y0() + to.y(), f.x0() + x + w, f.y0() + to.y() + CausalBoardLayout.RULE_H,
                ink(CausalGlyphs.tint(palette), fade));
        paintGauges(g, f, fade);
    }

    /**
     * The three numbers that decide whether a board is a good one, at the right of the kinds line.
     *
     * <p>Weight, ledger and paradox. They are together and always visible because every design
     * decision on this screen is a trade between them - a heavier chain, a bigger bank, a longer
     * argument with reality - and a wielder should never have to go looking for the cost of what
     * they just drew.
     */
    private void paintGauges(GuiGraphics g, Frame f, float fade) {
        PlayerMagicState state = state();
        Paradox.Rung rung = state.paradox().rung();
        String weight = Component.translatable("screen.magical.causal.weight",
                draft.weight(), draft.capacity()).getString();
        String ledger = Component.translatable("screen.magical.causal.ledger", state.ledger().rounded()).getString();
        String paradox = Component.translatable("screen.magical.causal.paradox", state.paradox().rounded()).getString();
        int over = draft.weight() > draft.capacity() ? WARN_RED : TEXT_NEAR;
        int strain = rung == Paradox.Rung.FRAYING ? WARN_RED : rung == Paradox.Rung.STRAINED ? WARN_AMBER : TEXT_FAINT;
        int x = f.width();
        x -= font.width(paradox);
        g.drawString(font, paradox, f.x0() + x, f.y0() + CausalBoardLayout.KIND_Y, ink(strain, fade), true);
        x -= font.width(ledger) + 10;
        g.drawString(font, ledger, f.x0() + x, f.y0() + CausalBoardLayout.KIND_Y, ink(ACCENT, fade), true);
        x -= font.width(weight) + 10;
        g.drawString(font, weight, f.x0() + x, f.y0() + CausalBoardLayout.KIND_Y, ink(over, fade), true);
        if (draft.suspended()) {
            String off = Component.translatable("screen.magical.causal.suspended").getString();
            g.drawString(font, off, f.x0() + x - font.width(off) - 10, f.y0() + CausalBoardLayout.KIND_Y,
                    ink(WARN_AMBER, fade), true);
        }
    }

    /** Every word of the showing vocabulary as its glyph, dimmed where the budget cannot pay for it. */
    private void paintPalette(GuiGraphics g, Frame f, float fade, double lx, double ly) {
        List<Rect> rects = CausalBoardLayout.palette(f.width(), paletteSize());
        int hovered = dragging ? -1 : CausalBoardLayout.indexAt(rects, lx, ly);
        for (int i = 0; i < rects.size(); i++) {
            Rect r = rects.get(i);
            CausalNode sample = sample(i, 0, 0);
            boolean affordable = draft.spare() >= sample.weight();
            float strength = hovered == i ? 1.0F : affordable ? 0.72F : 0.3F;
            drawGlyph(g, CausalGlyphs.of(sample), f.x0() + r.x(), f.y0() + r.y(), 1.0F,
                    mix(BLANK_INK, CausalGlyphs.tint(palette), strength), fade);
        }
    }

    /**
     * The string, under the pins so a wire never crosses a glyph it is not attached to.
     *
     * <p>Drawn as a chain of one-pixel dots along a bowed curve, each with a dot of shadow one pixel
     * down and right. That shadow is the whole reason a frameless board works on daylight terrain:
     * it is exactly what vanilla does to every letter on the screen, so a line gets the same
     * contrast a letter does and the scrim does not have to be any heavier than the Grimoire needs.
     */
    private void paintWires(GuiGraphics g, Frame f, float fade, double lx, double ly) {
        for (Weave.Wire wire : draft.wires()) {
            CausalNode from = draft.node(wire.from());
            CausalNode to = draft.node(wire.to());
            if (from == null || to == null) {
                continue;
            }
            Rect a = CausalBoardLayout.pin(f.width(), f.boardHeight(), from);
            Rect b = CausalBoardLayout.pin(f.width(), f.boardHeight(), to);
            int name = nameWidth(f, from, a);
            boolean cut = !dragging && CausalBoardLayout.onWire(a, name, b, lx, ly);
            int rgb = cut ? WARN_RED : mix(CausalGlyphs.tint(from.kind()), CausalGlyphs.tint(to.kind()), 0.5F);
            curve(g, f, a, name, b, rgb, fade * (cut ? 1.0F : 0.85F));
        }
        if (wireFrom >= 0 && dragging) {
            CausalNode from = draft.node(wireFrom);
            if (from != null) {
                Rect a = CausalBoardLayout.pin(f.width(), f.boardHeight(), from);
                Rect out = CausalBoardLayout.outPort(a, nameWidth(f, from, a));
                dots(g, f, out.x() + out.w() / 2.0D, out.y() + out.h() / 2.0D, lx, ly,
                        CausalGlyphs.tint(from.kind()), fade * 0.7F);
            }
        }
    }

    private void curve(GuiGraphics g, Frame f, Rect from, int fromName, Rect to, int rgb, float alpha) {
        Rect out = CausalBoardLayout.outPort(from, fromName);
        Rect in = CausalBoardLayout.inPort(to);
        dots(g, f, out.x() + out.w() / 2.0D, out.y() + out.h() / 2.0D,
                in.x() + in.w() / 2.0D, in.y() + in.h() / 2.0D, rgb, alpha);
    }

    /**
     * One bowed run of dots between two block-local points, shadowed the way a letter is.
     *
     * <p>The count of dots follows the length, not the other way round. A fixed number of steps puts
     * them a couple of pixels apart on a short run, where they close up into a dashed line, and eight
     * apart on a long one - two different-looking things doing the same job on the same board. A
     * constant spacing makes every piece of string the same string.
     */
    private void dots(GuiGraphics g, Frame f, double x0, double y0, double x1, double y1, int rgb, float alpha) {
        double[] control = CausalBoardLayout.wireControl(x0, y0, x1, y1);
        int shadow = ink(SHADOW, alpha * 0.8F);
        int argb = ink(rgb, alpha);
        double span = Math.hypot(x1 - x0, y1 - y0);
        int steps = Math.max(4, Math.min(WIRE_STEPS, (int) Math.round(span / DOT_SPACING)));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double u = 1.0D - t;
            double px = u * u * x0 + 2 * u * t * control[0] + t * t * x1;
            double py = u * u * y0 + 2 * u * t * control[1] + t * t * y1;
            int sx = f.x0() + (int) Math.round(px);
            int sy = f.y0() + (int) Math.round(py);
            g.fill(sx + 1, sy + 1, sx + 2, sy + 2, shadow);
            g.fill(sx, sy, sx + 1, sy + 1, argb);
        }
    }

    /** Every pin: its glyph, its name beside it, and the two marks string is tied to. */
    private void paintPins(GuiGraphics g, Frame f, float fade, double lx, double ly) {
        int hovered = pinAt(f, lx, ly);
        for (CausalNode node : draft.nodes()) {
            Rect r = CausalBoardLayout.pin(f.width(), f.boardHeight(), node);
            boolean lit = node.id() == selected;
            boolean warm = lit || node.id() == hovered;
            int tint = CausalGlyphs.tint(node.kind());
            drawGlyph(g, CausalGlyphs.of(node), f.x0() + r.x(), f.y0() + r.y(), 1.0F,
                    warm ? tint : mix(BLANK_INK, tint, 0.78F), fade);
            int name = nameWidth(f, node, r);
            g.drawString(font, font.plainSubstrByWidth(labelOf(node), name),
                    f.x0() + r.right() + CausalBoardLayout.LABEL_GAP, f.y0() + r.y() + 1,
                    ink(lit ? TEXT_BRIGHT : warm ? TEXT_NEAR : TEXT_MUTED, fade), true);
            if (node.kind() != NodeKind.CAUSE) {
                port(g, f, CausalBoardLayout.inPort(r), tint, fade, draft.orphan(node.id()));
            }
            if (node.kind() != NodeKind.EFFECT) {
                port(g, f, CausalBoardLayout.outPort(r, name), tint, fade,
                        draft.downstream(node.id()).isEmpty());
            }
        }
    }

    /** A mark string is tied to. Hollow while nothing is tied there, so a loose end is visible. */
    private void port(GuiGraphics g, Frame f, Rect port, int tint, float fade, boolean loose) {
        int x = f.x0() + port.x();
        int y = f.y0() + port.y();
        g.fill(x + 1, y + 1, x + port.w() + 1, y + port.h() + 1, ink(SHADOW, fade * 0.8F));
        g.fill(x, y, x + port.w(), y + port.h(), ink(loose ? BLANK_INK : tint, fade));
    }

    /** What the cursor is over, named, and what it does under that. */
    private void paintCaption(GuiGraphics g, Frame f, float fade, double lx, double ly) {
        CausalNode under = under(f, lx, ly);
        Rect first = CausalBoardLayout.caption(f.width(), f.boardHeight(), 0);
        Rect second = CausalBoardLayout.caption(f.width(), f.boardHeight(), 1);
        if (under == null) {
            g.drawString(font, font.plainSubstrByWidth(
                            Component.translatable("screen.magical.causal.hint").getString(), first.w()),
                    f.x0() + first.x(), f.y0() + first.y(), ink(TEXT_FAINT, fade), true);
            return;
        }
        String name = Component.translatable(under.translationKey()).getString();
        String cost = Component.translatable("screen.magical.causal.costs", under.weight()).getString();
        g.drawString(font, font.plainSubstrByWidth(name, first.w() - font.width(cost) - 8),
                f.x0() + first.x(), f.y0() + first.y(), ink(CausalGlyphs.tint(under.kind()), fade), true);
        g.drawString(font, cost, f.x0() + first.right() - font.width(cost), f.y0() + first.y(),
                ink(TEXT_FAINT, fade), true);
        g.drawString(font, font.plainSubstrByWidth(
                        Component.translatable(under.descriptionKey()).getString(), second.w()),
                f.x0() + second.x(), f.y0() + second.y(), ink(TEXT_MUTED, fade), true);
    }

    /**
     * The selected pin settings, and the two words that keep the board.
     *
     * <p>Only what the selected pin can actually use is drawn. A cause with no number shows no
     * number; a condition shows no scope and no tools, because it has none. An inspector that drew
     * every control greyed out would be four fifths noise, and the wielder learns the shape of the
     * vocabulary faster by seeing controls appear than by reading which ones are disabled.
     */
    private void paintInspector(GuiGraphics g, Frame f, float fade, double lx, double ly) {
        int saveW = font.width(saveWord());
        int clearW = font.width(clearWord());
        CausalBoardLayout.Inspector box = CausalBoardLayout.inspector(f.width(), f.boardHeight(), saveW, clearW);
        CausalNode node = selectedNode();
        if (node != null && node.takesParam()) {
            boolean lowHot = hit(box.minus(), lx, ly);
            boolean highHot = hit(box.plus(), lx, ly);
            g.drawString(font, "‹", f.x0() + box.minus().x(), f.y0() + box.minus().y(),
                    ink(lowHot ? TEXT_BRIGHT : TEXT_MUTED, fade), true);
            String value = WeaveText.paramText(node);
            g.drawString(font, font.plainSubstrByWidth(value, box.value().w()),
                    f.x0() + box.value().x(), f.y0() + box.value().y(), ink(TEXT_NEAR, fade), true);
            g.drawString(font, "›", f.x0() + box.plus().x(), f.y0() + box.plus().y(),
                    ink(highHot ? TEXT_BRIGHT : TEXT_MUTED, fade), true);
        }
        if (node != null && node.kind() == NodeKind.EFFECT) {
            boolean hot = hit(box.scope(), lx, ly);
            drawGlyph(g, CausalGlyphs.of(node.scope()), f.x0() + box.scope().x(), f.y0() + box.scope().y(),
                    1.0F, hot ? TEXT_BRIGHT : TEXT_NEAR, fade);
            g.drawString(font, font.plainSubstrByWidth(
                            Component.translatable(node.scope().translationKey()).getString(),
                            box.scope().w() - CausalBoardLayout.ICON - 4),
                    f.x0() + box.scope().x() + CausalBoardLayout.ICON + 4, f.y0() + box.scope().y(),
                    ink(hot ? TEXT_BRIGHT : TEXT_MUTED, fade), true);
            for (int i = 0; i < CausalBoardLayout.MOD_COUNT; i++) {
                Modifier modifier = Modifier.values()[i];
                Rect r = box.modifiers().get(i);
                boolean worn = node.wears(modifier);
                boolean hover = hit(r, lx, ly);
                float strength = worn ? 1.0F : hover ? 0.8F : 0.32F;
                drawGlyph(g, CausalGlyphs.of(modifier), f.x0() + r.x(), f.y0() + r.y(), 1.0F,
                        mix(BLANK_INK, worn ? ACCENT : TEXT_NEAR, strength), fade);
            }
        }
        int saveInk = dirty ? ACCENT : TEXT_FAINT;
        g.drawString(font, saveWord(), f.x0() + box.save().x(), f.y0() + box.save().y(),
                ink(hit(box.save(), lx, ly) ? TEXT_BRIGHT : saveInk, fade), true);
        g.drawString(font, clearWord(), f.x0() + box.clear().x(), f.y0() + box.clear().y(),
                ink(hit(box.clear(), lx, ly) ? WARN_RED : TEXT_FAINT, fade), true);
    }

    /** The selected chain in words, and the first thing wrong with the board under it. */
    private void paintReading(GuiGraphics g, Frame f, float fade, float time) {
        Rect first = CausalBoardLayout.reading(f.width(), f.boardHeight(), 0);
        Rect second = CausalBoardLayout.reading(f.width(), f.boardHeight(), 1);
        WeaveReview.Chain chain = selectedChain();
        Component line = chain == null ? WeaveText.summary(draft) : WeaveText.chain(chain);
        // Split rather than clipped: a chain is styled per pin, so it is a sequence rather than a
        // string, and the first line of a split is the longest run of it that fits.
        g.drawString(font, font.split(line, first.w()).get(0), f.x0() + first.x(), f.y0() + first.y(),
                ink(TEXT_NEAR, fade), true);
        if (time - refusedAt < 40.0F && !refusal.isEmpty()) {
            g.drawString(font, font.plainSubstrByWidth(refusal, second.w()), f.x0() + second.x(),
                    f.y0() + second.y(), ink(WARN_RED, fade), true);
            return;
        }
        List<WeaveReview.Issue> issues = WeaveReview.issues(draft, state().anchor().entityId() >= 0);
        if (issues.isEmpty()) {
            return;
        }
        Component said = WeaveText.issue(issues.get(0), draft);
        String tail = issues.size() > 1
                ? Component.translatable("screen.magical.causal.more_issues", issues.size() - 1).getString() : "";
        g.drawString(font, font.split(said, Math.max(1, second.w() - font.width(tail) - 6)).get(0),
                f.x0() + second.x(), f.y0() + second.y(), ink(WARN_AMBER, fade), true);
        if (!tail.isEmpty()) {
            g.drawString(font, tail, f.x0() + second.right() - font.width(tail), f.y0() + second.y(),
                    ink(TEXT_FAINT, fade), true);
        }
    }

    /** The glyph riding beside the cursor while a word is being carried out of the palette. */
    private void paintGhost(GuiGraphics g) {
        CausalNode sample = sample(paletteDrag, 0, 0);
        float size = CausalBoardLayout.ICON * GHOST_SCALE;
        drawGlyph(g, CausalGlyphs.of(sample), (float) mouseX + 8.0F, (float) mouseY - size / 2.0F,
                GHOST_SCALE, CausalGlyphs.tint(palette), 1.0F);
        g.drawString(font, Component.translatable(sample.translationKey()).getString(),
                (int) mouseX + 8 + Math.round(size) + 4, (int) mouseY - 4, TEXT_BRIGHT, true);
    }

    /** A glyph at a point, grown by {@code scale}, with the one-pixel shadow the font gives a letter. */
    private static void drawGlyph(GuiGraphics g, CausalGlyphs.Glyph glyph, float x, float y, float scale,
            int rgb, float alpha) {
        if (glyph == null) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        int shadow = ink(SHADOW, alpha * 0.85F);
        int argb = ink(rgb, alpha);
        for (int row = 0; row < CausalGlyphs.SIZE; row++) {
            runs(g, glyph.rows()[row], 1, row + 1, shadow);
        }
        for (int row = 0; row < CausalGlyphs.SIZE; row++) {
            runs(g, glyph.rows()[row], 0, row, argb);
        }
        g.pose().popPose();
    }

    /** One fill per run of lit pixels in a row of bits, the highest bit being the leftmost. */
    private static void runs(GuiGraphics g, int bits, int x, int y, int argb) {
        int col = 0;
        while (col < CausalGlyphs.SIZE) {
            if ((bits >> (CausalGlyphs.SIZE - 1 - col) & 1) == 0) {
                col++;
                continue;
            }
            int start = col;
            while (col < CausalGlyphs.SIZE && (bits >> (CausalGlyphs.SIZE - 1 - col) & 1) != 0) {
                col++;
            }
            g.fill(x + start, y, x + col, y + 1, argb);
        }
    }

    // ---- input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Frame f = frame();
        double lx = mx - f.x0();
        double ly = my - f.y0();
        pressX = mx;
        pressY = my;
        dragging = false;
        paletteDrag = -1;
        dragPin = -1;
        wireFrom = -1;

        int kind = CausalBoardLayout.indexAt(kindRects(f), lx, ly);
        if (kind >= 0) {
            NodeKind chosen = NodeKind.values()[kind];
            if (chosen != palette) {
                focusFrom = palette;
                focusAt = now();
                palette = chosen;
            }
            return true;
        }
        int word = CausalBoardLayout.paletteIndexAt(f.width(), paletteSize(), lx, ly);
        if (word >= 0) {
            paletteDrag = word;
            return true;
        }
        if (inspectorClicked(f, lx, ly, button)) {
            return true;
        }
        CausalNode pin = under(f, lx, ly);
        if (pin != null) {
            if (button == 1) {
                draft.remove(pin.id());
                if (selected == pin.id()) {
                    selected = -1;
                }
                dirty = true;
                return true;
            }
            Rect r = CausalBoardLayout.pin(f.width(), f.boardHeight(), pin);
            if (pin.kind() != NodeKind.EFFECT
                    && CausalBoardLayout.onPort(CausalBoardLayout.outPort(r, nameWidth(f, pin, r)), lx, ly)) {
                wireFrom = pin.id();
                return true;
            }
            selected = pin.id();
            dragPin = pin.id();
            grabDx = (int) Math.round(lx - r.x());
            grabDy = (int) Math.round(ly - r.y());
            return true;
        }
        if (button == 1 && cutWire(f, lx, ly)) {
            return true;
        }
        selected = -1;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mouseX = mx;
        mouseY = my;
        if (!dragging && Math.abs(mx - pressX) + Math.abs(my - pressY) >= DRAG_THRESHOLD) {
            dragging = true;
        }
        if (dragging && dragPin >= 0) {
            Frame f = frame();
            CausalNode node = draft.node(dragPin);
            if (node != null) {
                int bx = CausalBoardLayout.boardXFrom(f.width(), f.boardHeight(),
                        mx - f.x0() - grabDx + CausalBoardLayout.ICON / 2.0D);
                int by = CausalBoardLayout.boardYFrom(f.width(), f.boardHeight(),
                        my - f.y0() - grabDy + CausalBoardLayout.ICON / 2.0D);
                draft.replace(node.movedTo(bx, by));
                dirty = true;
            }
            return true;
        }
        return dragging || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        Frame f = frame();
        double lx = mx - f.x0();
        double ly = my - f.y0();
        try {
            if (paletteDrag >= 0) {
                place(f, lx, ly);
                return true;
            }
            if (wireFrom >= 0) {
                CausalNode target = under(f, lx, ly);
                if (target != null && target.id() != wireFrom) {
                    Weave.WireRefusal answer = draft.connect(wireFrom, target.id());
                    if (answer.ok()) {
                        dirty = true;
                    } else {
                        refuse(Component.translatable(answer.translationKey()).getString());
                    }
                }
                return true;
            }
        } finally {
            paletteDrag = -1;
            dragPin = -1;
            wireFrom = -1;
            dragging = false;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (scrollY == 0.0D) {
            return false;
        }
        Frame f = frame();
        CausalNode node = under(f, mx - f.x0(), my - f.y0());
        if (node == null) {
            node = selectedNode();
        }
        if (node == null || !node.takesParam()) {
            return false;
        }
        nudge(node, (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE && selected >= 0) {
            draft.remove(selected);
            selected = -1;
            dirty = true;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    // ---- what the gestures mean --------------------------------------------------------------------

    /** A word carried out of the palette, put down. A press that never travelled lands it in the middle. */
    private void place(Frame f, double lx, double ly) {
        int bx = dragging ? CausalBoardLayout.boardXFrom(f.width(), f.boardHeight(), lx) : Weave.BOARD_W / 2;
        int by = dragging ? CausalBoardLayout.boardYFrom(f.width(), f.boardHeight(), ly) : Weave.BOARD_H / 2;
        if (dragging && ly < CausalBoardLayout.BOARD_Y) {
            // Dropped back on the palette, or above it. Nothing was meant by that.
            return;
        }
        CausalNode placed = draft.add(sample(paletteDrag, bx, by));
        if (placed == null) {
            refuse(Component.translatable("message.magical.weave_over_budget",
                    draft.weight(), draft.capacity()).getString());
            return;
        }
        selected = placed.id();
        dirty = true;
    }

    private boolean inspectorClicked(Frame f, double lx, double ly, int button) {
        CausalBoardLayout.Inspector box = CausalBoardLayout.inspector(f.width(), f.boardHeight(),
                font.width(saveWord()), font.width(clearWord()));
        if (hit(box.save(), lx, ly)) {
            save();
            return true;
        }
        if (hit(box.clear(), lx, ly)) {
            draft.clear();
            selected = -1;
            dirty = true;
            return true;
        }
        CausalNode node = selectedNode();
        if (node == null) {
            return false;
        }
        if (node.takesParam() && hit(box.minus(), lx, ly)) {
            nudge(node, -1);
            return true;
        }
        if (node.takesParam() && hit(box.plus(), lx, ly)) {
            nudge(node, 1);
            return true;
        }
        if (node.kind() != NodeKind.EFFECT) {
            return false;
        }
        if (hit(box.scope(), lx, ly)) {
            Scope[] scopes = Scope.values();
            int step = button == 1 ? scopes.length - 1 : 1;
            CausalNode next = node.withScope(scopes[(node.scope().ordinal() + step) % scopes.length]);
            if (!draft.replace(next)) {
                refuse(Component.translatable("message.magical.weave_over_budget",
                        draft.weight(), draft.capacity()).getString());
            } else {
                dirty = true;
            }
            return true;
        }
        for (int i = 0; i < CausalBoardLayout.MOD_COUNT; i++) {
            if (!hit(box.modifiers().get(i), lx, ly)) {
                continue;
            }
            Modifier modifier = Modifier.values()[i];
            CausalNode next = node.toggled(modifier);
            if (next == node) {
                refuse(Component.translatable("message.magical.weave_too_many_tools",
                        Modifier.MAX_PER_NODE).getString());
            } else if (!draft.replace(next)) {
                refuse(Component.translatable("message.magical.weave_over_budget",
                        draft.weight(), draft.capacity()).getString());
            } else {
                dirty = true;
            }
            return true;
        }
        return false;
    }

    /**
     * The number on a pin, moved by one step of whatever it is measured in.
     *
     * <p>Ticks step by a whole second and everything else by one, because a gauge you have to scroll
     * forty times to move from one second to three is a gauge nobody tunes.
     */
    private void nudge(CausalNode node, int direction) {
        int step = ticked(node) ? Cause.MIN_INTERVAL : 1;
        CausalNode next = node.withParam(node.param() + direction * step);
        if (next.param() != node.param() && draft.replace(next)) {
            selected = node.id();
            dirty = true;
        }
    }

    private static boolean ticked(CausalNode node) {
        return switch (node.kind()) {
            case CAUSE -> node.cause() == Cause.TOLL;
            case CONDITION -> node.condition() == Condition.ONCE_PER;
            case EFFECT -> node.effect() == Effect.BIND || node.effect() == Effect.KINDLE;
        };
    }

    private boolean cutWire(Frame f, double lx, double ly) {
        for (Weave.Wire wire : draft.wires()) {
            CausalNode from = draft.node(wire.from());
            CausalNode to = draft.node(wire.to());
            if (from == null || to == null) {
                continue;
            }
            Rect a = CausalBoardLayout.pin(f.width(), f.boardHeight(), from);
            Rect b = CausalBoardLayout.pin(f.width(), f.boardHeight(), to);
            if (CausalBoardLayout.onWire(a, nameWidth(f, from, a), b, lx, ly)) {
                draft.disconnect(wire.from(), wire.to());
                dirty = true;
                return true;
            }
        }
        return false;
    }

    private void save() {
        if (draft.weight() > draft.capacity()) {
            refuse(Component.translatable("message.magical.weave_over_budget",
                    draft.weight(), draft.capacity()).getString());
            return;
        }
        MagicalNetwork.sendWeave(draft.save());
        dirty = false;
    }

    private void refuse(String said) {
        refusal = said;
        refusedAt = now();
    }

    // ---- small questions ---------------------------------------------------------------------------

    private List<Rect> kindRects(Frame f) {
        int[] widths = new int[CausalBoardLayout.KIND_COUNT];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = font.width(kindWord(NodeKind.values()[i]));
        }
        return CausalBoardLayout.kinds(f.width(), widths);
    }

    private static String kindWord(NodeKind kind) {
        return Component.translatable(kind.translationKey()).getString();
    }

    private static String saveWord() {
        return Component.translatable("screen.magical.causal.save").getString();
    }

    private static String clearWord() {
        return Component.translatable("screen.magical.causal.clear").getString();
    }

    private int paletteSize() {
        return switch (palette) {
            case CAUSE -> Cause.values().length;
            case CONDITION -> Condition.values().length;
            case EFFECT -> Effect.values().length;
        };
    }

    /** A pin of the showing vocabulary at that index, unplaced, for the palette and the ghost. */
    private CausalNode sample(int index, int x, int y) {
        return switch (palette) {
            case CAUSE -> CausalNode.of(0, Cause.values()[Mth.clamp(index, 0, Cause.values().length - 1)], x, y);
            case CONDITION -> CausalNode.of(0,
                    Condition.values()[Mth.clamp(index, 0, Condition.values().length - 1)], x, y);
            case EFFECT -> CausalNode.of(0, Effect.values()[Mth.clamp(index, 0, Effect.values().length - 1)], x, y);
        };
    }

    private int pinAt(Frame f, double lx, double ly) {
        CausalNode node = under(f, lx, ly);
        return node == null ? -1 : node.id();
    }

    /**
     * Whatever pin is under a point, the last one drawn winning.
     *
     * <p>Last, because that is the one drawn over the others - so the pin the wielder can see is the
     * pin they grab, which is the whole of what a reader of a board expects.
     */
    private CausalNode under(Frame f, double lx, double ly) {
        CausalNode found = null;
        for (CausalNode node : draft.nodes()) {
            Rect r = CausalBoardLayout.pin(f.width(), f.boardHeight(), node);
            if (CausalBoardLayout.onPin(r, lx, ly)
                    || CausalBoardLayout.onPort(CausalBoardLayout.outPort(r, nameWidth(f, node, r)), lx, ly)
                    || CausalBoardLayout.onPort(CausalBoardLayout.inPort(r), lx, ly)) {
                found = node;
            }
        }
        return found;
    }

    /** What a pin's name says: the word for it, and its number when it has one. */
    private String labelOf(CausalNode node) {
        String label = Component.translatable(node.translationKey()).getString();
        String number = WeaveText.paramText(node);
        return number.isEmpty() ? label : label + " " + number;
    }

    /**
     * How wide that name is drawn, which is also where the string leaving the pin is tied.
     *
     * <p>One number for both, so the label and the knot can never come apart: a name clipped by a
     * narrow screen pulls its knot back with it, and the run to the next pin starts wherever the
     * name actually ended.
     */
    private int nameWidth(Frame f, CausalNode node, Rect pin) {
        int room = Math.max(0, f.width() - pin.right() - CausalBoardLayout.LABEL_GAP);
        return Math.min(font.width(labelOf(node)), Math.min(room, CausalBoardLayout.LABEL_W_MAX));
    }

    private CausalNode selectedNode() {
        return selected < 0 ? null : draft.node(selected);
    }

    /** The first chain the selected pin is on, so the reading follows the cursor round the board. */
    private WeaveReview.Chain selectedChain() {
        List<WeaveReview.Chain> chains = WeaveReview.chains(draft);
        if (chains.isEmpty()) {
            return null;
        }
        if (selected >= 0) {
            for (WeaveReview.Chain chain : chains) {
                for (CausalNode pin : chain.pins()) {
                    if (pin.id() == selected) {
                        return chain;
                    }
                }
            }
        }
        return null;
    }

    private static boolean hit(Rect r, double x, double y) {
        return x >= r.x() && x < r.right() && y >= r.y() && y < r.bottom();
    }

    /** Unsaved writing is offered back on the way out rather than thrown away without a word. */
    @Override
    public void onClose() {
        if (dirty && draft.weight() <= draft.capacity()) {
            save();
        }
        super.onClose();
    }

    /** A board left open while the state ticks reloads only when there is nothing pending on it. */
    @Override
    public void tick() {
        reload();
    }
}
