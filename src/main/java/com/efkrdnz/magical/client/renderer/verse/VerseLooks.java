package com.efkrdnz.magical.client.renderer.verse;

import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.Wake;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import java.util.EnumMap;
import java.util.Map;

/**
 * What a body looks like, per {@link VersePrototype.Look}: one existing painter kind, a scale on
 * the prototype's radius, and the count and parameter the shader takes. No new art - every kind
 * here is one the profile painters already draw - and the colour is the school's own ramp, so a
 * Fire needle and an Arcane needle are the same shape in different light. Pure, so a test can
 * hold the table total.
 */
public final class VerseLooks {

    /** The palette variant a body is painted in; the brighter variant 1 lights its glyphs. */
    public static final int PALETTE_VARIANT = 0;
    public static final int GLYPH_VARIANT = 1;
    /** Nothing draws below this, whatever the prototype says its radius is. */
    public static final float MIN_DRAWN = 0.08F;

    public enum Shape { BOLT, ORB, MARK }

    /** One look. {@code length} is the bolt's beam length in blocks; the other shapes ignore it. */
    public record Row(Shape shape, FxKinds.Orb orb, FxKinds.Filament filament, FxKinds.Mark mark,
                      float scale, float length, float opacity, int count, int paramB) {
        static Row bolt(FxKinds.Filament filament, float scale, float length, float opacity, int count, int paramB) {
            return new Row(Shape.BOLT, null, filament, null, scale, length, opacity, count, paramB);
        }

        static Row orb(FxKinds.Orb orb, float scale, float opacity, int count, int paramB) {
            return new Row(Shape.ORB, orb, null, null, scale, 0.0F, opacity, count, paramB);
        }

        static Row mark(FxKinds.Mark mark, float scale, float opacity, int count, int paramB) {
            return new Row(Shape.MARK, null, null, mark, scale, 0.0F, opacity, count, paramB);
        }
    }

    private static final Map<VersePrototype.Look, Row> ROWS = new EnumMap<>(VersePrototype.Look.class);
    private static final Map<Behaviour, FxKinds.Orb> GLYPHS = new EnumMap<>(Behaviour.class);

    static {
        ROWS.put(VersePrototype.Look.NEEDLE, Row.bolt(FxKinds.Filament.PLASMA_TUBE, 1.4F, 1.2F, 1.0F, 4, 10));
        ROWS.put(VersePrototype.Look.ORB, Row.orb(FxKinds.Orb.PLASMA, 1.6F, 1.0F, 6, 10));
        ROWS.put(VersePrototype.Look.SHARD, Row.orb(FxKinds.Orb.SHARD_DIAMOND, 1.8F, 1.0F, 5, 12));
        ROWS.put(VersePrototype.Look.EMBER, Row.orb(FxKinds.Orb.EMBER_CLUSTER, 2.0F, 1.0F, 7, 9));
        ROWS.put(VersePrototype.Look.ARC, Row.bolt(FxKinds.Filament.LIGHTNING, 1.6F, 2.2F, 1.0F, 6, 6));
        ROWS.put(VersePrototype.Look.DART, Row.bolt(FxKinds.Filament.RIBBON, 1.5F, 1.0F, 1.0F, 3, 8));
        ROWS.put(VersePrototype.Look.WHISPER, Row.orb(FxKinds.Orb.SPARK_BURST, 1.2F, 0.8F, 8, 6));
        ROWS.put(VersePrototype.Look.BLINK, Row.orb(FxKinds.Orb.LENS_STREAKS, 1.8F, 1.0F, 6, 12));
        ROWS.put(VersePrototype.Look.RING, Row.mark(FxKinds.Mark.RIPPLES, 1.0F, 0.9F, 8, 6));
        ROWS.put(VersePrototype.Look.BURST, Row.mark(FxKinds.Mark.RAY_BURST, 3.0F, 1.0F, 12, 8));
        ROWS.put(VersePrototype.Look.PIT, Row.mark(FxKinds.Mark.VORTEX_SPIRAL, 1.0F, 0.9F, 8, 10));
        ROWS.put(VersePrototype.Look.WORD, Row.orb(FxKinds.Orb.HEX_LENS, 1.5F, 0.9F, 6, 8));

        GLYPHS.put(Behaviour.SEEKER, FxKinds.Orb.CRESCENT);
        GLYPHS.put(Behaviour.SIGHTLINE, FxKinds.Orb.LENS_STREAKS);
        GLYPHS.put(Behaviour.PUNCTURE, FxKinds.Orb.SHARD_DIAMOND);
        GLYPHS.put(Behaviour.SERPENTINE, FxKinds.Orb.THIN_HALO);
        GLYPHS.put(Behaviour.GYRE, FxKinds.Orb.HOLLOW_SHELL);
        GLYPHS.put(Behaviour.ERRANT, FxKinds.Orb.SPARK_BURST);
        GLYPHS.put(Behaviour.RELAY, FxKinds.Orb.CHARGE_SPHERE);
        GLYPHS.put(Behaviour.TWIN_PATH, FxKinds.Orb.BLOOM_FLASH);
        GLYPHS.put(Behaviour.NAUGHT, FxKinds.Orb.VOID_CORE);
        GLYPHS.put(Behaviour.UNDYING, FxKinds.Orb.HEX_LENS);
        GLYPHS.put(Behaviour.LANTERN, FxKinds.Orb.BLOOM_FLASH);
        GLYPHS.put(Behaviour.NEAR_WORD, FxKinds.Orb.PLASMA);
        GLYPHS.put(Behaviour.BOUNCE_BURST, FxKinds.Orb.EMBER_CLUSTER);
    }

    private VerseLooks() {
    }

    public static Row of(VersePrototype.Look look) {
        Row row = ROWS.get(look);
        return row != null ? row : ROWS.get(VersePrototype.Look.ORB);
    }

    /** The row's scale on the body's radius, floored so a thin needle is still a line. */
    public static float drawnSize(Row row, float radius) {
        return Math.max(MIN_DRAWN, radius * row.scale());
    }

    /** The small orb that orbits a body carrying this behaviour. */
    public static FxKinds.Orb glyph(Behaviour behaviour) {
        FxKinds.Orb orb = GLYPHS.get(behaviour);
        return orb != null ? orb : FxKinds.Orb.PLASMA;
    }

    public static FxKinds.Filament wake(Wake wake) {
        return switch (wake) {
            case FIRE -> FxKinds.Filament.FLAME_TONGUE;
            case WATER -> FxKinds.Filament.WATER;
            case FROST -> FxKinds.Filament.CRYSTAL_SHARD;
        };
    }

    /** A wake is its material's colour, not the body's school: a Fire Wake behind an Arcane needle is still fire. */
    public static int wakeColor(Wake wake) {
        return switch (wake) {
            case FIRE -> SchoolMaterial.FIRE.variantColor(0);
            case WATER -> SchoolMaterial.WATER.variantColor(0);
            case FROST -> SchoolMaterial.WATER.variantColor(1);
        };
    }
}
