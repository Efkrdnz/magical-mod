package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import java.util.EnumMap;
import java.util.Map;

/** Family -> painter dispatch; CUSTOM resolves through {@link CustomPainters}. Never an if-ladder. */
public final class FxPainters {
    private static final Map<Silhouette.Family, FamilyPainter> PAINTERS = new EnumMap<>(Silhouette.Family.class);

    static {
        PAINTERS.put(Silhouette.Family.GLYPH, GlyphCirclePainter::paintSilhouette);
        PAINTERS.put(Silhouette.Family.ORB, OrbPainter::paint);
        PAINTERS.put(Silhouette.Family.FILAMENT, FilamentPainter::paint);
        PAINTERS.put(Silhouette.Family.FIELD, FieldPainter::paint);
        PAINTERS.put(Silhouette.Family.MARK, MarkPainter::paint);
        PAINTERS.put(Silhouette.Family.RIFT, RiftPainter::paint);
        PAINTERS.put(Silhouette.Family.LENS, LensPainter::paint);
        PAINTERS.put(Silhouette.Family.BODY, BodyPainter::paint);
        PAINTERS.put(Silhouette.Family.SWARM, SwarmPainter::paint);
        PAINTERS.put(Silhouette.Family.CUSTOM, CustomPainters::paint);
    }

    private FxPainters() {}

    public static void paint(FxContext ctx, VisualProfile profile, Silhouette silhouette) {
        FamilyPainter painter = PAINTERS.get(silhouette.family());
        if (painter != null) {
            painter.paint(ctx, profile, silhouette);
        }
    }

    /** Paint every silhouette of the profile in order (first = primary). */
    public static void paintAll(FxContext ctx, VisualProfile profile) {
        for (Silhouette silhouette : profile.silhouettes()) {
            paint(ctx, profile, silhouette);
        }
    }
}
