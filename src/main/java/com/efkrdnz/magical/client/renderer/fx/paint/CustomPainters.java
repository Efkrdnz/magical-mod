package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import java.util.HashMap;
import java.util.Map;

/** Registry of bespoke painters (kept skills' looks, the six one-off bodies) keyed by id string. */
public final class CustomPainters {
    private static final Map<String, FamilyPainter> PAINTERS = new HashMap<>();

    private CustomPainters() {}

    public static void register(String id, FamilyPainter painter) {
        PAINTERS.put(id, painter);
    }

    public static boolean has(String id) {
        return PAINTERS.containsKey(id);
    }

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette silhouette) {
        FamilyPainter painter = PAINTERS.get(silhouette.customPainter());
        if (painter != null) {
            painter.paint(ctx, profile, silhouette);
        } else {
            // visible fallback so a missing painter is noticed
            OrbPainter.paint(ctx, profile, Silhouette.orb(Silhouette.Form.BILLBOARD, com.efkrdnz.magical.magic.visual.FxKinds.Orb.PLASMA, Math.max(0.4F, silhouette.sizeA() * 0.5F)));
        }
    }
}
