package com.efkrdnz.magical.client.hud;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client-side HUD options, {@code config/magical-client.toml}. Spec only - no client classes
 * are referenced here, so the class is harmless wherever it is loaded; it is registered only on
 * the client in {@code MagicalMod}. Read once per snapshot rebuild through {@link #current()}.
 */
public final class MagicalClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD_ENABLED = BUILDER
            .comment("Draw the magic HUD at all.")
            .define("hudEnabled", true);
    public static final ModConfigSpec.EnumValue<HudAnchor> ANCHOR = BUILDER
            .comment("The screen corner the sigil hangs from. Top-left is the only corner nothing of vanilla's lives in.")
            .defineEnum("anchor", HudAnchor.TOP_LEFT);
    public static final ModConfigSpec.DoubleValue SCALE = BUILDER
            .comment("Size of the HUD relative to the design. Above 1.25 the top-right corner runs out of height on small GUIs.")
            .defineInRange("scale", 1.0D, 0.5D, 1.5D);
    public static final ModConfigSpec.DoubleValue OPACITY = BUILDER
            .comment("Opacity of the whole HUD.")
            .defineInRange("opacity", 1.0D, 0.2D, 1.0D);
    public static final ModConfigSpec.BooleanValue SHOW_SINS = BUILDER
            .comment("Show the seven sins as satellites in a crown above the sigil, with their readouts beside the cards.")
            .define("showSins", true);
    public static final ModConfigSpec.BooleanValue SHOW_STATUSES = BUILDER
            .comment("Show statuses on you (silenced, rooted, ...) as chips under the crosshair.")
            .define("showStatuses", true);
    public static final ModConfigSpec.BooleanValue COMPACT = BUILDER
            .comment("A smaller HUD with less on it: no crown, no readouts, announcements as emblems only.")
            .define("compact", false);
    public static final ModConfigSpec.BooleanValue REDUCED_MOTION = BUILDER
            .comment("Shorter fades and no pulses, for players who would rather the HUD held still.")
            .define("reducedMotion", false);
    public static final ModConfigSpec.BooleanValue HUD_DEBUG = BUILDER
            .comment("Print the HUD's quad and string counts in the corner, for checking its cost.")
            .define("hudDebug", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private MagicalClientConfig() {}

    /** The options as one value, or the defaults before the file has loaded. */
    public static HudOptions current() {
        if (!SPEC.isLoaded()) {
            return HudOptions.DEFAULTS;
        }
        return new HudOptions(HUD_ENABLED.get(), ANCHOR.get(), SCALE.get().floatValue(), OPACITY.get().floatValue(),
                SHOW_SINS.get(), SHOW_STATUSES.get(), COMPACT.get(), REDUCED_MOTION.get(), HUD_DEBUG.get());
    }
}
