package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters;

/** Registers every bespoke painter id used by VisualProfiles (client setup). */
public final class MagicalPainters {
    private MagicalPainters() {}

    public static void register() {
        CustomPainters.register("wildfire", WildfirePainter::paint);
        LightPainters.register();
        VoidPainters.register();
        SpatialPainters.register();
        ClassPainters.register();
        FusionPainters.register();
    }
}
