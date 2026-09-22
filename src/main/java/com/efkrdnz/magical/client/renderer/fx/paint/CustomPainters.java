package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/** Registry of bespoke painters (kept skills' looks, the six one-off bodies) keyed by id string. */
public final class CustomPainters {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, FamilyPainter> PAINTERS = new HashMap<>();

    /**
     * Painter ids already complained about.
     *
     * <p>{@link #paint} is called once per silhouette per entity per frame, so a bare warn here
     * would be tens of thousands of identical lines a minute - a logging bug standing where a
     * drawing bug used to be. One line the first time an id is missed says everything the
     * hundred-thousandth would.
     *
     * <p>Concurrent because the guard is the {@code add} answering true exactly once, and that is
     * the one property a plain {@link HashMap}-backed set stops promising the day anything reaches
     * a painter off the render thread. It is a handful of strings that never shrinks, which is the
     * intended lifetime: a missing painter is reported once per launch, not once per world.
     */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private CustomPainters() {}

    public static void register(String id, FamilyPainter painter) {
        PAINTERS.put(id, painter);
    }

    public static boolean has(String id) {
        return PAINTERS.containsKey(id);
    }

    /**
     * Every id this registry answers to, for {@code CustomPainterCoverageTest}.
     *
     * <p>A painter registered under an id no profile asks for is invisible from inside the game -
     * it simply never runs - and now that a miss draws nothing it is invisible from outside it
     * too. So the table has to be readable to be checked against the roster.
     */
    public static Set<String> ids() {
        return Set.copyOf(PAINTERS.keySet());
    }

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette silhouette) {
        String id = silhouette.customPainter();
        FamilyPainter painter = PAINTERS.get(id);
        if (painter != null) {
            painter.paint(ctx, profile, silhouette);
            return;
        }
        // A miss draws nothing, and the notice goes to the log.
        //
        // This used to draw an Orb.PLASMA billboard at max(0.4, sizeA * 0.5), commented "visible
        // fallback so a missing painter is noticed". It was noticed by nobody in the entire life of
        // the Sword school, because of what it actually looks like: plasma_orb is additive
        // (ONE, ONE), its shader branch reaches core = pow(1 - r, 2.4) * 1.8, mixes the colour 85%
        // toward white and adds core * 0.4 on top of that, and additiveOut multiplies the whole
        // thing by the glow again. It is over 1 before the blend and it clips to pure white at
        // every size and every distance - the brightest primitive in the library. A white disc on
        // an effect does not read as "this skill named a painter that does not exist". It reads as
        // a spell with a glowing orb in it, which is why one sat on the wielder's chest and on
        // every blade in flight through every screenshot the class was developed from.
        //
        // The visible marker is not replaced by a quieter one. A marker is only ever seen by
        // somebody already looking at the effect, and the thing that catches this is
        // CustomPainterCoverageTest, which fails the build the day an id is declared with nothing
        // behind it - before anybody launches the game. A debug-flag marker would be off in exactly
        // the session where the mistake was made and would earn a second way to be wrong.
        if (WARNED.add(id)) {
            LOGGER.warn("No custom painter is registered under \"{}\": {} declares"
                    + " Silhouette.custom(\"{}\", ...) and nothing draws it. Register one in"
                    + " client/renderer/fx/paint/custom, or, if the object is drawn by its own"
                    + " entity renderer, hold the silhouette out of the walk with .forModes().",
                    id, profile.skillId(), id);
        }
    }
}
