package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.forge.chain.ForgeChainBuilder.CommittedGlyph;
import com.efkrdnz.magical.forge.chain.ForgeRules;
import com.efkrdnz.magical.forge.chain.StrokeQuantizer;
import com.efkrdnz.magical.forge.glyph.GlyphPoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client submission of a rune chain: the committed glyphs of the current forge attempt. Worst case
 * ({@link ForgeRules#MAX_GLYPHS} glyphs of {@link ForgeRules#MAX_STROKES_PER_GLYPH} strokes of
 * {@link ForgeRules#MAX_POINTS_PER_STROKE} 4-byte points) is about 18 KB, comfortably under the
 * 32767-byte serverbound custom-payload limit; a kept glyph is a short id instead, so nothing here
 * grows.
 */
public record ForgeSubmitPayload(int containerId, List<Glyph> glyphs) implements CustomPacketPayload {

    /** Longest glyph id accepted on the wire; every template path is a single short word. */
    public static final int MAX_KEPT_ID_LENGTH = 64;

    /**
     * One committed glyph, in one of two shapes.
     *
     * <p><em>Drawn</em>: {@code keptId} empty and {@code strokes} carrying what the player drew,
     * which the server re-recognizes exactly as it always has. <em>Kept</em>: {@code keptId} naming
     * a glyph carried over from the weapon already in the forge slot, with no strokes at all - a
     * preloaded glyph has none to send. The server never trusts a kept id: it checks it against the
     * weapon's own {@code ForgedWeapon} component before the id means anything.</p>
     */
    public record Glyph(Optional<String> keptId, List<Stroke> strokes) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Glyph> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_KEPT_ID_LENGTH)), Glyph::keptId,
                Stroke.STREAM_CODEC.apply(ByteBufCodecs.list(ForgeRules.MAX_STROKES_PER_GLYPH)), Glyph::strokes,
                Glyph::new);

        public static Glyph drawn(List<Stroke> strokes) {
            return new Glyph(Optional.empty(), strokes);
        }

        public static Glyph kept(String id) {
            return new Glyph(Optional.of(id), List.of());
        }

        public boolean isKept() {
            return keptId.isPresent();
        }

        /** Recovers normalized canvas points, stamping stroke ids in draw order. */
        public List<List<GlyphPoint>> toCanvasStrokes() {
            List<List<GlyphPoint>> out = new ArrayList<>(strokes.size());
            for (int strokeId = 0; strokeId < strokes.size(); strokeId++) {
                List<Point> points = strokes.get(strokeId).points();
                List<GlyphPoint> converted = new ArrayList<>(points.size());
                for (Point point : points) {
                    converted.add(new GlyphPoint(
                            StrokeQuantizer.fromUnits(point.x()),
                            StrokeQuantizer.fromUnits(point.y()),
                            strokeId));
                }
                out.add(List.copyOf(converted));
            }
            return List.copyOf(out);
        }
    }

    /** One stroke of a glyph, as its quantized points. */
    public record Stroke(List<Point> points) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Stroke> STREAM_CODEC = StreamCodec.composite(
                Point.STREAM_CODEC.apply(ByteBufCodecs.list(ForgeRules.MAX_POINTS_PER_STROKE)), Stroke::points,
                Stroke::new);
    }

    /** One point, quantized onto the {@code [0, ForgeRules.CANVAS_UNITS)} grid. */
    public record Point(short x, short y) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Point> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.SHORT, Point::x,
                ByteBufCodecs.SHORT, Point::y,
                Point::new);
    }

    public static final Type<ForgeSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "forge_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeSubmitPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ForgeSubmitPayload::containerId,
            Glyph.STREAM_CODEC.apply(ByteBufCodecs.list(ForgeRules.MAX_GLYPHS)), ForgeSubmitPayload::glyphs,
            ForgeSubmitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Builds a payload from the committed chain: drawn glyphs quantized, kept glyphs as bare ids. */
    public static ForgeSubmitPayload fromCanvas(int containerId, List<CommittedGlyph> committed) {
        List<Glyph> converted = new ArrayList<>(committed.size());
        for (CommittedGlyph glyph : committed) {
            converted.add(glyph.kept() ? Glyph.kept(glyph.id()) : Glyph.drawn(convertStrokes(glyph.strokes())));
        }
        return new ForgeSubmitPayload(containerId, List.copyOf(converted));
    }

    private static List<Stroke> convertStrokes(List<List<GlyphPoint>> strokes) {
        List<Stroke> converted = new ArrayList<>(strokes.size());
        for (List<GlyphPoint> stroke : strokes) {
            List<Point> points = new ArrayList<>(stroke.size());
            for (GlyphPoint point : stroke) {
                points.add(new Point(
                        (short) StrokeQuantizer.toUnits(point.x()),
                        (short) StrokeQuantizer.toUnits(point.y())));
            }
            converted.add(new Stroke(List.copyOf(points)));
        }
        return List.copyOf(converted);
    }
}
