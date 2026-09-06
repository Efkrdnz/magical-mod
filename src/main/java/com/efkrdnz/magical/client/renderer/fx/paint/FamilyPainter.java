package com.efkrdnz.magical.client.renderer.fx.paint;

import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;

/** Stateless: turns one silhouette of a profile into MagicVertex emission. */
@FunctionalInterface
public interface FamilyPainter {
    void paint(FxContext ctx, VisualProfile profile, Silhouette silhouette);
}
