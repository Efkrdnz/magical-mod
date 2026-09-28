package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.hud.HudSnapshot.Chip;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.HudSnapshot.Readout;
import com.efkrdnz.magical.client.hud.HudSnapshot.Slot;
import com.efkrdnz.magical.client.hud.HudSnapshot.Stamp;
import com.efkrdnz.magical.client.renderer.fx.FxTextures;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.visual.StampId;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The sigil layer rendered into a counting sink, so "one batch" is a number and not a claim. The
 * whole HUD at once has to stay under {@link HudBudget#MAX_QUADS}, the idle corner block is
 * exactly {@link HudBudget#IDLE_QUADS}, nothing a slot does costs a quad, and a reading costs
 * exactly its stamp.
 */
class HudSnapshotBudgetTest {

    /** Counts vertices, and keeps each one's packed data. The six abstract methods are all {@code MagicVertex.emit} needs. */
    static final class QuadCounter implements VertexConsumer {
        int vertices;
        final List<int[]> packed = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices++;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            packed.add(new int[] {u, v});
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private static final Label LABEL = new Label(FormattedCharSequence.EMPTY, 12, 0xFFFFFF);
    private static final SigilRenderer.ChargeSource HALF_CHARGED = (slot, partial) -> 0.5F;
    private static final SigilRenderer.ChargeSource IDLE = (slot, partial) -> 0.0F;
    private static final HudOptions COMPACT = new HudOptions(true, HudAnchor.TOP_LEFT, 1.0F, 1.0F, true, true, true, false, false);
    private static final int SLOTS = MagicContent.LOADOUT_SIZE;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HudState.fade().snap(1.0F);
        HudState.mana().snap(0.6F);
        HudState.barrier().snap(0.4F);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("magical", path);
    }

    /** A snapshot with the first {@code equipped} slots filled, cooling or not, and the given extras. */
    private static HudSnapshot snapshot(HudLayout layout, HudOptions options, int equipped, boolean cooling, int chipCount, int readoutCount) {
        Slot[] slots = new Slot[SLOTS];
        for (int k = 0; k < SLOTS; k++) {
            boolean empty = k >= equipped;
            slots[k] = new Slot(k, empty ? null : id("skill_" + k), 40 + k, 0x88DDFF, 0.0F, 1.0F, empty,
                    cooling ? 100L : 0L, cooling ? 60 : 0, cooling ? 80 : 0, Long.MIN_VALUE, LABEL,
                    layout.glyph(k), layout.cell(k));
        }
        MagicStatus[] statuses = MagicStatus.values();
        Chip[] chips = new Chip[chipCount];
        for (int i = 0; i < chipCount; i++) {
            chips[i] = new Chip(statuses[i], 5 + i, 0xB48AFF, true, i, 100L, 200, layout.statusChip(i, chipCount));
        }
        int[] widths = new int[readoutCount];
        Arrays.fill(widths, HudLayout.STAMP_LEAD + 12);
        HudLayout.Flow flow = layout.flow(widths, readoutCount);
        HudLayout.Rect[] at = flow.tokens();
        Readout[] readouts = new Readout[at.length + (flow.more() == null ? 0 : 1)];
        for (int i = 0; i < at.length; i++) {
            readouts[i] = new Readout(LABEL, at[i], new Stamp(5, 0xFFD166));
        }
        if (flow.more() != null) {
            readouts[at.length] = new Readout(LABEL, flow.more(), null);
        }
        Readout[] pools = {
                new Readout(LABEL, layout.poolsText(), null),
                new Readout(LABEL, layout.poolsStackedLine(), null)};
        return new HudSnapshot(1, 100L, options, layout, 0xFF7A45, pools, equipped > 0, slots, readouts, chips);
    }

    private static HudSnapshot snapshot(HudLayout layout, int equipped, boolean cooling, int chips, int readouts) {
        return snapshot(layout, HudOptions.DEFAULTS, equipped, cooling, chips, readouts);
    }

    private static int quads(HudSnapshot snapshot, SigilRenderer.ChargeSource charge) {
        QuadCounter sink = new QuadCounter();
        HudBatch batch = new HudBatch().begin(sink, new Matrix4f());
        SigilRenderer.emitAll(batch, snapshot, 110.5F, 0.5F, charge);
        assertEquals(0, sink.vertices % 4, "a quad was left unfinished");
        assertEquals(batch.quads() * 4, sink.vertices, "the batch miscounted its own quads");
        return batch.quads();
    }

    private static HudLayout design() {
        return HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F);
    }

    @Test
    void theIdleBlockIsTwoBarsAndOneGlyphPerEquippedSlot() {
        assertEquals(HudBudget.IDLE_QUADS, quads(snapshot(design(), SLOTS, false, 0, 0), IDLE), "four equipped and ready");
        assertEquals(2 + 3, quads(snapshot(design(), 3, false, 0, 0), IDLE), "three equipped, one empty");
        assertEquals(2, quads(snapshot(design(), 0, false, 0, 0), IDLE), "nothing equipped draws the bars alone");
    }

    @Test
    void theMaximalBlockStaysInsideTheQuadBudget() {
        int quads = quads(snapshot(design(), SLOTS, true, HudLayout.STATUS_CHIPS_MAX, HudLayout.READOUT_TOKENS_MAX + 3), HALF_CHARGED);
        assertEquals(2 + SLOTS + HudLayout.READOUT_TOKENS_MAX + HudLayout.STATUS_CHIPS_MAX, quads);
        assertTrue(quads <= HudBudget.MAX_QUADS, "the maximal HUD emitted " + quads + " quads");
        assertTrue(quads > HudBudget.IDLE_QUADS, "the maximal HUD drew less than an idle one: " + quads);
    }

    /** Cooling and charging are drawn inside quads that are already there; a reading adds its stamp and nothing else. */
    @Test
    void aSlotCostsNothingExtraAndAReadingCostsExactlyItsStamp() {
        int ready = quads(snapshot(design(), SLOTS, false, 0, 0), IDLE);
        assertEquals(ready, quads(snapshot(design(), SLOTS, true, 0, 0), IDLE), "a cooling slot grew a quad");
        assertEquals(ready, quads(snapshot(design(), SLOTS, false, 0, 0), HALF_CHARGED), "a charging slot grew a quad");
        for (int n = 1; n <= HudLayout.READOUT_TOKENS_MAX; n++) {
            assertEquals(ready + n, quads(snapshot(design(), SLOTS, false, 0, n), IDLE), n + " readings");
        }
        // The "+n" is text: eight stamps whatever is left off.
        assertEquals(ready + HudLayout.READOUT_TOKENS_MAX, quads(snapshot(design(), SLOTS, false, 0, HudLayout.READOUT_TOKENS_MAX + 4), IDLE));
    }

    private static final SigilRenderer.ChargeSource HELD = (slot, partial) -> 1.0F;

    /** One quad's packed data, read back the way the vertex shader reads it. */
    private record Quad(int kind, int paramB, float phase, int seed, int mode) {
        static List<Quad> of(QuadCounter sink) {
            List<Quad> quads = new ArrayList<>();
            for (int i = 0; i < sink.packed.size(); i += 4) {
                int x = sink.packed.get(i)[0];
                int y = sink.packed.get(i)[1];
                quads.add(new Quad(x & 31, x >> 11 & 31, (y & 255) / 255.0F, y >> 8 & 63, y >> 14 & 3));
            }
            return quads;
        }
    }

    private static List<Quad> emitted(HudSnapshot snapshot, SigilRenderer.ChargeSource charge) {
        QuadCounter sink = new QuadCounter();
        SigilRenderer.emitAll(new HudBatch().begin(sink, new Matrix4f()), snapshot, 110.5F, 0.5F, charge);
        return Quad.of(sink);
    }

    /**
     * A hold input counts a held key whether or not its skill can fire, so the cooldown has to win:
     * a glyph lit whole by a key held through its cooldown reads as ready when it is not.
     */
    @Test
    void aCoolingGlyphStaysGreyWhileItsKeyIsHeld() {
        int side = design().glyphSize();
        List<Quad> cooling = emitted(snapshot(design(), SLOTS, true, 0, 0), HELD).stream().filter(q -> q.seed() == side).toList();
        assertEquals(SLOTS, cooling.size());
        for (Quad glyph : cooling) {
            assertEquals(HudKind.SLOT.id(), glyph.kind());
            assertEquals(0, glyph.paramB() & 4, "a cooling glyph was drawn as charging");
            // 60 of 80 ticks left at tick 100, read at 110.5: 49.5 / 80.
            assertEquals(49.5F / 80.0F, glyph.phase(), 1.0F / 255.0F, "a cooling glyph lost its cooldown");
        }
        for (Quad glyph : emitted(snapshot(design(), SLOTS, false, 0, 0), HALF_CHARGED)) {
            if (glyph.seed() == side) {
                assertEquals(4, glyph.paramB() & 4, "a ready glyph did not show its charge");
                assertEquals(0.5F, glyph.phase(), 1.0F / 255.0F);
            }
        }
    }

    private static HudSnapshot withSlot(HudSnapshot base, Slot slot) {
        Slot[] slots = base.slots().clone();
        slots[slot.slot()] = slot;
        return new HudSnapshot(base.version(), base.builtAtTick(), base.options(), base.layout(), base.manaFill(),
                base.pools(), base.slotBand(), slots, base.readouts(), base.chips());
    }

    /**
     * A bar across the middle of its square is a ninth of it tall. Counted over the square, the
     * line between grey and lit crossed it in a tenth of the cooldown and it read as ready for the
     * last two fifths; counted over its ink, the part of the bar that is grey is the part of the
     * cooldown still to run, from the first tick to the last. Charging fills it the same way.
     */
    @Test
    void aShortGlyphIsGreyForItsWholeCooldown() {
        float[] bar = FxTextures.inkRows(StampId.BAR.atlasCell());
        float[] ring = FxTextures.inkRows(StampId.RING.atlasCell());
        assertTrue(bar[1] - bar[0] < 0.2F, "a bar should be a short glyph: " + Arrays.toString(bar));
        assertTrue(ring[1] - ring[0] > 0.8F, "a ring should fill its square: " + Arrays.toString(ring));
        float span = bar[1] - bar[0];
        float step = 1.0F / (255.0F * span) + 0.001F;
        HudLayout layout = design();
        HudSnapshot base = snapshot(layout, 1, false, 0, 0);
        int side = layout.glyphSize();
        for (int remaining : new int[] {100, 90, 60, 30, 12}) {
            Slot slot = new Slot(0, id("skill_0"), StampId.BAR.atlasCell(), 0x88DDFF, bar[0], bar[1], false,
                    100L, remaining, 100, Long.MIN_VALUE, LABEL, layout.glyph(0), layout.cell(0));
            float left = SigilRenderer.cooldownRemaining(slot, 110.5F);
            List<Quad> glyphs = emitted(withSlot(base, slot), IDLE).stream().filter(q -> q.seed() == side).toList();
            assertEquals(1, glyphs.size());
            float line = 1.0F - glyphs.get(0).phase();
            assertTrue(line > bar[0] - step && line < bar[1] + step, remaining + " ticks: the line is off the bar's ink at " + line);
            assertEquals(left, (bar[1] - line) / span, step, remaining + " ticks: the grey part of the bar is not the part of the cooldown left");
        }
        Slot ready = new Slot(0, id("skill_0"), StampId.BAR.atlasCell(), 0x88DDFF, bar[0], bar[1], false,
                0L, 0, 0, Long.MIN_VALUE, LABEL, layout.glyph(0), layout.cell(0));
        Quad charging = emitted(withSlot(base, ready), HALF_CHARGED).stream().filter(q -> q.seed() == side).findFirst().orElseThrow();
        assertEquals(4, charging.paramB() & 4);
        assertEquals(0.5F, (bar[1] - (1.0F - charging.phase())) / span, step, "half a charge did not light half the bar");
    }

    /** A stamp is a skill glyph at nine units drawn whole: no clock, no gauge, no second mode. */
    @Test
    void aStampIsAGlyphAtNineUnitsDrawnWhole() {
        List<Quad> stamps = emitted(snapshot(design(), 0, false, 0, 3), IDLE).stream().filter(q -> q.seed() == HudLayout.STAMP).toList();
        assertEquals(3, stamps.size());
        for (Quad stamp : stamps) {
            assertEquals(HudKind.SLOT.id(), stamp.kind());
            assertEquals(0.0F, stamp.phase());
            assertEquals(0, stamp.mode());
            assertEquals(0, stamp.paramB() & 4);
        }
    }

    /** Under the loadout rail the corner block stands down, and nothing else does: a root still shows. */
    @Test
    void underTheLoadoutRailOnlyTheChipsAreDrawn() {
        HudSnapshot full = snapshot(design(), SLOTS, true, 3, 4);
        QuadCounter sink = new QuadCounter();
        HudBatch batch = new HudBatch().begin(sink, new Matrix4f());
        SigilRenderer.emitAll(batch, full, 110.5F, 0.5F, IDLE, false);
        assertEquals(3, batch.quads(), "the chips alone");
        for (Quad chip : Quad.of(sink)) {
            assertEquals(HudKind.CHIP.id(), chip.kind());
        }
        HudText.Counting text = new HudText.Counting();
        SigilRenderer.text(text, full, 110.5F, 0.5F, IDLE, false);
        assertEquals(0, text.draws(), "the corner block's strings were drawn under the rail");
    }

    /** Vanilla draws a string whose alpha is under four fully opaque; the HUD never hands it one. */
    @Test
    void aNearlyTransparentStringIsNeverDrawn() {
        HudText.OnGraphics text = new HudText.OnGraphics();
        // No GuiGraphics behind it: reaching it would throw.
        text.draw(FormattedCharSequence.EMPTY, 0, 0, 0x03FFFFFF);
        text.drawScaled(FormattedCharSequence.EMPTY, 0.0F, 0.0F, 2.0F, 2.0F, 0x00FFFFFF);
        assertEquals(0, text.draws());
    }

    @Test
    void textIsDrawnOnceAndCounted() {
        HudText.Counting text = new HudText.Counting();
        HudLayout wide = HudLayout.of(480, 270, HudAnchor.TOP_RIGHT, 1.25F);
        SigilRenderer.text(text, snapshot(wide, SLOTS, true, HudLayout.STATUS_CHIPS_MAX, HudLayout.READOUT_TOKENS_MAX + 3), 110.5F, 0.5F, HALF_CHARGED);
        // the two counts, four cells, eight readings and the "+n"; the seconds are shaped by the
        // tick, which has not run here, so a cooling cell shows its key
        assertEquals(2 + SLOTS + HudLayout.READOUT_TOKENS_MAX + 1, text.draws());
        assertTrue(text.draws() + 1 <= HudBudget.MAX_TEXT_DRAWS, "no room left for the debug line");

        text = new HudText.Counting();
        SigilRenderer.text(text, snapshot(design(), SLOTS, false, 0, 0), 110.5F, 0.5F, IDLE);
        assertEquals(2 + SLOTS, text.draws(), "idle: the two counts and four keys");

        text = new HudText.Counting();
        SigilRenderer.text(text, snapshot(design(), COMPACT, SLOTS, false, 0, 0), 110.5F, 0.5F, IDLE);
        assertEquals(2, text.draws(), "compact drops the keys");

        text = new HudText.Counting();
        SigilRenderer.text(text, snapshot(design(), 0, false, 0, 0), 110.5F, 0.5F, IDLE);
        assertEquals(2, text.draws(), "with nothing equipped there are no cells to label");
    }

    @Test
    void aFadedOutSigilDrawsNothing() {
        HudState.fade().snap(0.0F);
        try {
            assertEquals(0, quads(snapshot(design(), SLOTS, true, HudLayout.STATUS_CHIPS_MAX, 0), HALF_CHARGED));
        } finally {
            HudState.fade().snap(1.0F);
        }
    }
}
