package com.efkrdnz.magical.forge.glyph;

import com.efkrdnz.magical.forge.chain.ForgeGrade;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The 35 rune glyphs the forge canvas understands, defined as code polylines so that client and
 * server build byte-identical templates.
 *
 * <p>Coordinates are unit canvas coordinates: x right, y down, both in [0, 1].
 */
public final class ForgeGlyphLibrary {

    private static final Map<String, GlyphTemplate> TEMPLATES = buildTemplates();

    private static volatile GlyphRecognizer recognizer;

    private ForgeGlyphLibrary() {
    }

    /** Every template, in declaration order. */
    public static List<GlyphTemplate> all() {
        return List.copyOf(TEMPLATES.values());
    }

    public static Optional<GlyphTemplate> byId(String id) {
        return Optional.ofNullable(TEMPLATES.get(id));
    }

    /** The shared recognizer over {@link #all()}, created on first use. */
    public static GlyphRecognizer recognizer() {
        GlyphRecognizer local = recognizer;
        if (local == null) {
            synchronized (ForgeGlyphLibrary.class) {
                local = recognizer;
                if (local == null) {
                    local = new GlyphRecognizer(all());
                    recognizer = local;
                }
            }
        }
        return local;
    }

    private static Map<String, GlyphTemplate> buildTemplates() {
        Map<String, GlyphTemplate> map = new LinkedHashMap<>();
        putGrades(map);
        putElements(map);
        putForms(map);
        putTempers(map);
        putModifiers(map);
        return Collections.unmodifiableMap(map);
    }

    private static void putGrades(Map<String, GlyphTemplate> map) {
        put(map, grade("crude", poly(.30f, .10f, .30f, .90f, .80f, .90f)));
        put(map, grade("fine", line(.11f, .35f, .89f, .35f), line(.11f, .65f, .89f, .65f)));
        put(map, grade("high",
                line(.50f, .08f, .92f, .90f),
                line(.92f, .90f, .08f, .90f),
                line(.08f, .90f, .50f, .08f)));
        put(map, grade("master",
                poly(.08f, .51f, .50f, .24f, .92f, .51f),
                poly(.08f, .51f, .50f, .78f, .92f, .51f),
                line(.50f, .30f, .50f, .72f)));
        put(map, grade("mythic",
                poly(.50f, .05f, .86f, .65f, .14f, .65f, .50f, .05f),
                poly(.50f, .95f, .14f, .35f, .86f, .35f, .50f, .95f)));
        put(map, grade("divine",
                circle(.50f, .50f, .32f),
                line(.50f, .05f, .50f, .95f),
                line(.05f, .50f, .95f, .50f),
                line(.12f, .12f, .22f, .22f),
                line(.88f, .12f, .78f, .22f)));
    }

    private static void putElements(Map<String, GlyphTemplate> map) {
        put(map, template("fire", GlyphCategory.ELEMENT,
                poly(.15f, .95f, .35f, .42f, .47f, .62f, .62f, .06f, .85f, .95f)));
        put(map, template("frost", GlyphCategory.ELEMENT,
                line(.08f, .50f, .92f, .50f),
                line(.29f, .14f, .71f, .86f),
                line(.71f, .14f, .29f, .86f)));
        put(map, template("storm", GlyphCategory.ELEMENT,
                poly(.72f, .05f, .30f, .50f, .60f, .50f, .24f, .95f)));
        put(map, template("void", GlyphCategory.ELEMENT,
                circle(.50f, .50f, .46f),
                circle(.31f, .64f, .20f)));
        put(map, template("radiant", GlyphCategory.ELEMENT,
                circle(.50f, .50f, .11f),
                line(.50f, .03f, .50f, .18f),
                line(.82f, .50f, .97f, .50f),
                line(.50f, .82f, .50f, .97f),
                line(.03f, .50f, .18f, .50f)));
        put(map, template("venom", GlyphCategory.ELEMENT,
                poly(.72f, .12f, .40f, .12f, .28f, .30f, .50f, .48f, .72f, .66f, .60f, .84f, .28f, .84f),
                line(.28f, .84f, .34f, .96f)));
        put(map, template("terra", GlyphCategory.ELEMENT,
                poly(.10f, .25f, .90f, .25f, .90f, .75f, .10f, .75f, .10f, .25f)));
        put(map, template("gale", GlyphCategory.ELEMENT,
                spiral(.50f, .50f, .48f, .06f, 1f)));
        // A mark struck through. Kept well clear of void's two circles and terra's square, which
        // are the two an inverted triangle could otherwise be confused with.
        put(map, template("dark", GlyphCategory.ELEMENT,
                poly(.12f, .18f, .88f, .18f, .50f, .92f, .12f, .18f),
                line(.26f, .52f, .74f, .52f)));
        put(map, template("blood", GlyphCategory.ELEMENT,
                arc(.26f, .22f, .16f, 180f, 360f),
                arc(.50f, .52f, .13f, 180f, 360f),
                arc(.74f, .82f, .10f, 180f, 360f)));
    }

    private static void putForms(Map<String, GlyphTemplate> map) {
        put(map, template("slash", GlyphCategory.FORM, line(.08f, .50f, .92f, .50f)));
        put(map, template("cleave", GlyphCategory.FORM, line(.50f, .08f, .50f, .92f)));
        put(map, template("thrust", GlyphCategory.FORM,
                line(.06f, .50f, .52f, .50f),
                poly(.28f, .04f, .96f, .50f, .28f, .96f)));
        put(map, template("spin", GlyphCategory.FORM, circle(.50f, .50f, .40f)));
        put(map, template("slam", GlyphCategory.FORM, poly(.10f, .10f, .50f, .92f, .90f, .10f)));
        put(map, template("wave", GlyphCategory.FORM, sine(.05f, .95f, .50f, .28f, 1.5f)));
        put(map, template("rising", GlyphCategory.FORM,
                line(.50f, .94f, .50f, .18f),
                poly(.14f, .38f, .50f, .04f, .86f, .38f)));
        put(map, template("flurry", GlyphCategory.FORM,
                line(.10f, .70f, .30f, .30f),
                line(.40f, .70f, .60f, .30f),
                line(.70f, .70f, .90f, .30f)));
        // Spear and dagger: a shaft with a closed head, where thrust's head is an open chevron.
        put(map, template("lunge", GlyphCategory.FORM,
                line(.06f, .18f, .94f, .78f),
                line(.56f, .90f, .94f, .78f),
                line(.78f, .40f, .94f, .78f)));
        // Scythe: the blade opens left off a shaft, the mirror of echo's right-opening arcs.
        put(map, template("reap", GlyphCategory.FORM,
                line(.64f, .08f, .64f, .92f),
                arc(.64f, .50f, .42f, 90f, 270f)));
        // Claws: a fishhook. The only glyph in the set that ends in a curl.
        put(map, template("hook", GlyphCategory.FORM,
                line(.34f, .06f, .34f, .54f),
                arc(.50f, .54f, .16f, 180f, 360f),
                line(.66f, .54f, .66f, .30f)));
        // Greatsword: two chevrons driven down a stem, against rising's single chevron driven up.
        put(map, template("plunge", GlyphCategory.FORM,
                poly(.04f, .10f, .24f, .40f, .44f, .10f),
                poly(.44f, .44f, .70f, .90f, .96f, .44f)));
    }

    private static void putTempers(Map<String, GlyphTemplate> map) {
        put(map, template("keen", GlyphCategory.TEMPER,
                line(.50f, .05f, .50f, .95f),
                line(.08f, .74f, .92f, .74f)));
        put(map, template("heavy", GlyphCategory.TEMPER,
                line(.22f, .14f, .78f, .14f),
                line(.08f, .86f, .92f, .86f),
                line(.50f, .14f, .50f, .86f)));
        put(map, template("swift", GlyphCategory.TEMPER,
                poly(.08f, .55f, .35f, .90f, .92f, .10f)));
        // Dagger: speed lines behind a head. Haste is chevrons alone; the rails are the difference.
        put(map, template("rush", GlyphCategory.TEMPER,
                line(.06f, .24f, .94f, .24f),
                line(.32f, .50f, .94f, .50f),
                line(.58f, .76f, .94f, .76f)));
        // Greatsword: mass hung under a crossbeam.
        put(map, template("heft", GlyphCategory.TEMPER,
                line(.10f, .14f, .90f, .14f),
                line(.50f, .14f, .50f, .66f),
                poly(.22f, .66f, .78f, .66f, .78f, .92f, .22f, .92f, .22f, .66f)));
        // Spear: the only spiral in the set, so it can never be confused with anything.
        put(map, template("coil", GlyphCategory.TEMPER,
                spiral(.40f, .40f, .09f, .33f, 1.5f),
                line(.60f, .68f, .96f, .94f)));
        // Claws: one stroke that forks at both ends - each strike lands twice.
        put(map, template("split", GlyphCategory.TEMPER,
                line(.18f, .16f, .18f, .84f),
                line(.40f, .16f, .40f, .84f),
                line(.10f, .50f, .84f, .50f),
                poly(.70f, .34f, .90f, .50f, .70f, .66f)));
    }

    private static void putModifiers(Map<String, GlyphTemplate> map) {
        put(map, template("pierce", GlyphCategory.MODIFIER, line(.10f, .90f, .90f, .10f)));
        put(map, template("reach", GlyphCategory.MODIFIER, line(.10f, .10f, .90f, .90f)));
        put(map, template("haste", GlyphCategory.MODIFIER,
                poly(.12f, .22f, .40f, .50f, .12f, .78f),
                poly(.52f, .22f, .80f, .50f, .52f, .78f)));
        put(map, template("echo", GlyphCategory.MODIFIER,
                arc(.62f, .50f, .45f, 120f, 240f),
                arc(.62f, .50f, .25f, 120f, 240f)));
        put(map, template("seeking", GlyphCategory.MODIFIER,
                line(.15f, .15f, .85f, .85f),
                line(.85f, .15f, .15f, .85f)));
        put(map, template("leech", GlyphCategory.MODIFIER,
                poly(.10f, .22f, .90f, .78f, .10f, .78f, .90f, .22f, .10f, .22f)));
        put(map, template("binding", GlyphCategory.MODIFIER, lemniscate(.50f, .50f, .41f, .31f)));
        put(map, template("brand", GlyphCategory.MODIFIER,
                line(.28f, .05f, .28f, .95f),
                line(.72f, .05f, .72f, .95f),
                line(.05f, .28f, .95f, .28f),
                line(.05f, .72f, .95f, .72f)));
        put(map, template("shatter", GlyphCategory.MODIFIER,
                line(.50f, .92f, .50f, .50f),
                poly(.15f, .08f, .50f, .50f, .85f, .08f)));
        put(map, template("guard", GlyphCategory.MODIFIER, arc(.50f, .62f, .40f, 180f, 360f)));
        // Chorus: three voices stacked, each answering the one above.
        put(map, template("chorus", GlyphCategory.MODIFIER,
                poly(.18f, .30f, .50f, .12f, .82f, .30f),
                poly(.18f, .56f, .50f, .38f, .82f, .56f),
                poly(.18f, .82f, .50f, .64f, .82f, .82f)));
        // Tithe: something poured into a vessel. It is the rune that spends what defends you.
        put(map, template("tithe", GlyphCategory.MODIFIER,
                line(.20f, .06f, .20f, .32f),
                line(.40f, .12f, .40f, .36f),
                poly(.08f, .46f, .20f, .90f, .76f, .90f, .94f, .52f)));
        // Carry: force stepping up out of one strike and into the next.
        put(map, template("carry", GlyphCategory.MODIFIER,
                poly(.06f, .84f, .46f, .84f, .46f, .44f, .90f, .44f),
                poly(.74f, .28f, .92f, .44f, .74f, .60f)));

        // Operators. A stem that splits: one press, several forms.
        put(map, template("fork", GlyphCategory.OPERATOR,
                line(.10f, .50f, .50f, .50f),
                line(.50f, .50f, .90f, .18f),
                line(.50f, .50f, .90f, .82f)));

        // The three triggers. Each says when the payload goes off, and each is a different gross
        // shape rather than the same shape pointed a different way - the recognizer does not
        // normalize rotation, but a point cloud still reads near-mirrors as near-identical.

        // Impact: a pennant, planted where the carrier strikes.
        put(map, template("trigger", GlyphCategory.OPERATOR,
                line(.28f, .04f, .28f, .96f),
                poly(.28f, .10f, .84f, .30f, .28f, .50f)));
        // Timer: two brackets holding a span of time between them.
        put(map, template("fuse", GlyphCategory.OPERATOR,
                poly(.38f, .08f, .12f, .08f, .12f, .92f, .38f, .92f),
                poly(.62f, .08f, .88f, .08f, .88f, .92f, .62f, .92f)));
        // Expiry: a fan bursting upward off the ground where the carrier ended.
        put(map, template("wake", GlyphCategory.OPERATOR,
                line(.10f, .92f, .90f, .92f),
                line(.50f, .92f, .50f, .26f),
                line(.32f, .88f, .14f, .40f),
                line(.68f, .88f, .86f, .40f)));
    }

    private static void put(Map<String, GlyphTemplate> map, GlyphTemplate template) {
        GlyphTemplate previous = map.put(template.id(), template);
        if (previous != null) {
            throw new IllegalStateException("duplicate glyph template id: " + template.id());
        }
    }

    @SafeVarargs
    private static GlyphTemplate grade(String id, List<GlyphPoint>... strokes) {
        float acceptScore = ForgeGrade.byName(id)
                .orElseThrow(() -> new IllegalStateException("no grade named " + id))
                .sigilAcceptScore();
        return GlyphTemplate.of(id, GlyphCategory.GRADE, acceptScore, List.of(strokes));
    }

    @SafeVarargs
    private static GlyphTemplate template(String id, GlyphCategory category, List<GlyphPoint>... strokes) {
        return GlyphTemplate.of(id, category, category.defaultAcceptScore(), List.of(strokes));
    }

    // --- stroke helpers -------------------------------------------------------------------

    private static List<GlyphPoint> line(float x0, float y0, float x1, float y1) {
        return List.of(new GlyphPoint(x0, y0, 0), new GlyphPoint(x1, y1, 0));
    }

    private static List<GlyphPoint> poly(float... xy) {
        if (xy.length < 4 || xy.length % 2 != 0) {
            throw new IllegalArgumentException("poly needs an even number of at least 4 coordinates");
        }
        List<GlyphPoint> out = new ArrayList<>(xy.length / 2);
        for (int i = 0; i < xy.length; i += 2) {
            out.add(new GlyphPoint(xy[i], xy[i + 1], 0));
        }
        return List.copyOf(out);
    }

    /** 24 segments clockwise from the top, closed by repeating the first point. */
    private static List<GlyphPoint> circle(float cx, float cy, float r) {
        List<GlyphPoint> out = new ArrayList<>(25);
        for (int i = 0; i <= 24; i++) {
            out.add(onCircle(cx, cy, r, 270f + i * 15f));
        }
        return List.copyOf(out);
    }

    /** 16 points along the arc from {@code fromDeg} to {@code toDeg}, 0 degrees pointing right. */
    private static List<GlyphPoint> arc(float cx, float cy, float r, float fromDeg, float toDeg) {
        List<GlyphPoint> out = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            out.add(onCircle(cx, cy, r, fromDeg + (toDeg - fromDeg) * (i / 15f)));
        }
        return List.copyOf(out);
    }

    /** 32 points of a sine wave that rises first, drawn left to right. */
    private static List<GlyphPoint> sine(float x0, float x1, float y, float amp, float periods) {
        List<GlyphPoint> out = new ArrayList<>(32);
        for (int i = 0; i < 32; i++) {
            float t = i / 31f;
            float px = x0 + (x1 - x0) * t;
            float py = (float) (y - amp * Math.sin(2 * Math.PI * periods * t));
            out.add(new GlyphPoint(px, py, 0));
        }
        return List.copyOf(out);
    }

    /** 48 points spiralling clockwise from the top, radius sweeping r0 to r1. */
    private static List<GlyphPoint> spiral(float cx, float cy, float r0, float r1, float turns) {
        List<GlyphPoint> out = new ArrayList<>(48);
        for (int i = 0; i < 48; i++) {
            float t = i / 47f;
            out.add(onCircle(cx, cy, r0 + (r1 - r0) * t, 270f + 360f * turns * t));
        }
        return List.copyOf(out);
    }

    /** 40 points of a sideways figure eight. */
    private static List<GlyphPoint> lemniscate(float cx, float cy, float a, float b) {
        List<GlyphPoint> out = new ArrayList<>(40);
        for (int i = 0; i < 40; i++) {
            double t = 2 * Math.PI * (i / 39.0);
            out.add(new GlyphPoint(
                    (float) (cx + a * Math.cos(t)),
                    (float) (cy + b * Math.sin(t) * Math.cos(t)),
                    0));
        }
        return List.copyOf(out);
    }

    private static GlyphPoint onCircle(float cx, float cy, float r, float degrees) {
        double theta = Math.toRadians(degrees);
        return new GlyphPoint((float) (cx + r * Math.cos(theta)), (float) (cy + r * Math.sin(theta)), 0);
    }
}
