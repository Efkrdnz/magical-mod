package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.forge.chain.ForgeRules;
import com.efkrdnz.magical.forge.chain.StrokeQuantizer;
import com.efkrdnz.magical.forge.glyph.GlyphPoint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client submission of a drawn rune chain: the committed glyphs of the current forge attempt, each
 * as its quantized strokes. Worst case ({@link ForgeRules#MAX_GLYPHS} glyphs of
 * {@link ForgeRules#MAX_STROKES_PER_GLYPH} strokes of {@link ForgeRules#MAX_POINTS_PER_STROKE}
 * 4-byte points) is about 18 KB, comfortably under the 32767-byte serverbound custom-payload limit.
 */
public record ForgeSubmitPayload(int containerId, List<Glyph> glyphs) implements CustomPacketPayload {

    /** One committed glyph, as the strokes it was drawn with. */
    public record Glyph(List<Stroke> strokes) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Glyph> STREAM_CODEC = StreamCodec.composite(
                Stroke.STREAM_CODEC.apply(ByteBufCodecs.list(ForgeRules.MAX_STROKES_PER_GLYPH)), Glyph::strokes,
                Glyph::new);
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

    /** Builds a payload from the canvas' committed glyphs, quantizing every coordinate. */
    public static ForgeSubmitPayload fromCanvas(int containerId, List<List<List<GlyphPoint>>> glyphs) {
        List<Glyph> converted = new ArrayList<>(glyphs.size());
        for (List<List<GlyphPoint>> glyph : glyphs) {
            converted.add(new Glyph(convertStrokes(glyph)));
        }
        return new ForgeSubmitPayload(containerId, List.copyOf(converted));
    }

    /** Recovers normalized canvas points from every glyph, stamping stroke ids in draw order. */
    public List<List<List<GlyphPoint>>> toStrokes() {
        List<List<List<GlyphPoint>>> out = new ArrayList<>(glyphs.size());
        for (Glyph glyph : glyphs) {
            out.add(convertToCanvas(glyph.strokes()));
        }
        return List.copyOf(out);
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

    private static List<List<GlyphPoint>> convertToCanvas(List<Stroke> strokes) {
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
