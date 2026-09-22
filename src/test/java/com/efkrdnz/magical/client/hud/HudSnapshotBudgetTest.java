package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.hud.HudSnapshot.Announcement;
import com.efkrdnz.magical.client.hud.HudSnapshot.Card;
import com.efkrdnz.magical.client.hud.HudSnapshot.Chip;
import com.efkrdnz.magical.client.hud.HudSnapshot.GaugeLine;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.HudSnapshot.Line;
import com.efkrdnz.magical.client.hud.HudSnapshot.Satellite;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The sigil layer rendered into a counting sink, so "one batch" is a number and not a claim. The
 * whole HUD at once has to stay under {@link HudBudget#MAX_QUADS}, and the idle HUD under
 * {@link HudBudget#IDLE_QUADS}.
 */
class HudSnapshotBudgetTest {

    /** Counts vertices. The six abstract methods are all {@code MagicVertex.emit} needs. */
    static final class QuadCounter implements VertexConsumer {
        int vertices;

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
    /** Three rings, the core plate and glyph, the level tag, four spokes, cards and tags, the caption plate. */
    private static final int IDLE_EXPECTED = 3 + 2 + 1 + 4 * 3 + 1;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HudState.fade().snap(1.0F);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("magical", path);
    }

    private static HudSnapshot snapshot(HudLayout layout, boolean maximal) {
        return snapshot(layout, maximal, maximal);
    }

    private static HudSnapshot snapshot(HudLayout layout, boolean maximal, boolean drawArc) {
        Line[] core = {new Line(LABEL, layout.coreLine(0, 2, LABEL.width())), new Line(LABEL, layout.coreLine(1, 2, LABEL.width()))};
        Line level = new Line(LABEL, layout.levelTag(LABEL.width()));
        int cards = MagicContent.LOADOUT_SIZE;
        Card[] cardArray = new Card[cards];
        for (int k = 0; k < cards; k++) {
            boolean empty = !maximal && k == cards - 1;
            cardArray[k] = new Card(k, empty ? null : id("skill_" + k), 40 + k, 0x88DDFF, maximal && k == 1, empty,
                    maximal ? 100L : 0L, maximal ? 60 : 0, maximal ? 80 : 0, Long.MIN_VALUE, LABEL,
                    layout.card(k), layout.keyTag(k, 10), layout.cardText(k));
        }
        Satellite[] satellites = HudSnapshot.NO_SATELLITES;
        GaugeLine[] gauges = HudSnapshot.NO_GAUGES;
        Chip[] chips = HudSnapshot.NO_CHIPS;
        Announcement[] announcements = HudSnapshot.NO_ANNOUNCEMENTS;
        if (maximal) {
            satellites = new Satellite[HudLayout.CROWN_SEATS];
            for (int seat = 0; seat < satellites.length; seat++) {
                satellites[seat] = new Satellite(seat, id("sin_" + seat), 20 + seat, 0xFFD166, seat == 6, layout.crownSeat(seat));
                HudState.sinGauge(seat).snap(0.8F);
            }
            gauges = new GaugeLine[HudLayout.GAUGE_LINES_MAX];
            for (int i = 0; i < gauges.length; i++) {
                gauges[i] = new GaugeLine(i, LABEL, layout.gaugeLine(i));
            }
            MagicStatus[] statuses = MagicStatus.values();
            chips = new Chip[HudLayout.STATUS_CHIPS_MAX];
            for (int i = 0; i < chips.length; i++) {
                chips[i] = new Chip(statuses[i], 5 + i, 0xB48AFF, true, i, 100L, 200, layout.statusChip(i, chips.length));
            }
            announcements = new Announcement[] {new Announcement(33, 0xFFD166, false, LABEL, 100L, HudAnnouncer.LIFETIME_TICKS, layout.announceEmblem(), layout.announceText())};
            HudState.halo().snap(0.6F);
            HudState.vessel().snap(0.5F);
            HudState.corruption().snap(0.74F);
        } else {
            HudState.halo().snap(0.0F);
        }
        Line[] captions = new Line[maximal ? HudLayout.CAPTIONS_MAX : 1];
        for (int i = 0; i < captions.length; i++) {
            captions[i] = new Line(LABEL, layout.captionLine(i));
        }
        HudState.draw().snap(drawArc ? 0.75F : 0.0F);
        return new HudSnapshot(1, 100L, HudOptions.DEFAULTS, layout, MagicSchool.FIRE, 0xFF7A45, 0xFFB15A, 16, 4, 0xFFB15A,
                false, maximal, maximal, drawArc, HudPalette.draw(false),
                core, level, cardArray, satellites, gauges, chips, announcements, captions,
                maximal ? 200L : 0L);
    }

    private static int quads(HudSnapshot snapshot, SigilRenderer.ChargeSource charge) {
        QuadCounter sink = new QuadCounter();
        HudBatch batch = new HudBatch().begin(sink, new Matrix4f());
        SigilRenderer.emitAll(batch, snapshot, 110.5F, 0.5F, charge);
        assertEquals(0, sink.vertices % 4, "a quad was left unfinished");
        assertEquals(batch.quads() * 4, sink.vertices, "the batch miscounted its own quads");
        return batch.quads();
    }

    @Test
    void theMaximalSigilStaysInsideTheQuadBudget() {
        HudLayout layout = HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F);
        int quads = quads(snapshot(layout, true), HALF_CHARGED);
        assertTrue(quads <= HudBudget.MAX_QUADS, "the maximal HUD emitted " + quads + " quads");
        assertTrue(quads > HudBudget.IDLE_QUADS, "the maximal HUD drew less than an idle one: " + quads);
    }

    @Test
    void theIdleSigilIsTiny() {
        HudLayout layout = HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F);
        int quads = quads(snapshot(layout, false), IDLE);
        assertEquals(IDLE_EXPECTED, quads, "the idle HUD emitted " + quads + " quads");
        assertTrue(quads <= HudBudget.IDLE_QUADS, "the idle HUD emitted " + quads + " quads");
    }

    @Test
    void textIsDrawnOnceAndCounted() {
        HudLayout layout = HudLayout.of(480, 270, HudAnchor.TOP_RIGHT, 1.25F);
        HudText.Counting text = new HudText.Counting();
        SigilRenderer.text(text, snapshot(layout, true), 110.5F, 0.5F);
        // two core lines, the level, four key tags, three captions, eight readouts, the announcement;
        // the seconds over cooling cards are shaped by the tick, which has not run here
        assertEquals(2 + 1 + 4 + 3 + 8 + 1, text.draws());
        text = new HudText.Counting();
        SigilRenderer.text(text, snapshot(HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F), false), 110.5F, 0.5F);
        // two core lines, the level, four key tags, the caption
        assertEquals(2 + 1 + 4 + 1, text.draws());
    }

    /**
     * The Array's draw arc: exactly one quad, and only while an Array is standing.
     *
     * <p>Pinned as a difference rather than as a total, because the total is the whole sigil and
     * the point of this one is that a wielder who never found the chain pays nothing for it -
     * which is also why it is absent from the idle count above rather than folded into it.
     */
    @Test
    void theDrawArcIsOneQuadAndOnlyWhileAnArrayStands() {
        HudLayout layout = HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F);
        int without = quads(snapshot(layout, true, false), HALF_CHARGED);
        int with = quads(snapshot(layout, true, true), HALF_CHARGED);
        assertEquals(without + 1, with, "the draw arc is not exactly one quad");
        assertEquals(IDLE_EXPECTED, quads(snapshot(layout, false, false), IDLE), "an idle HUD grew");
        // And it lives in the hairline gap the two pools leave, rather than on top of either: a
        // ring drawn over the barrier or the mana would be read as part of that pool's meter.
        assertTrue(HudLayout.DRAW_R_IN >= HudLayout.BARRIER_R_OUT, "the draw arc sits on the barrier");
        assertTrue(HudLayout.DRAW_R_OUT <= HudLayout.MANA_R_IN, "the draw arc sits on the mana ring");
        assertTrue(HudLayout.DRAW_R_OUT > HudLayout.DRAW_R_IN, "the draw arc has no width");
    }

    @Test
    void aFadedOutSigilDrawsNothing() {
        HudLayout layout = HudLayout.of(480, 270, HudAnchor.TOP_LEFT, 1.0F);
        HudState.fade().snap(0.0F);
        try {
            assertEquals(0, quads(snapshot(layout, true), HALF_CHARGED));
        } finally {
            HudState.fade().snap(1.0F);
        }
    }
}
