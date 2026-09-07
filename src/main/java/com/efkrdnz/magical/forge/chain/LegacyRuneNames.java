package com.efkrdnz.magical.forge.chain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Pure string mapping from the legacy {@code magical_infusion} custom-data tag format (written by
 * the pre-rework runeforge) to the identifiers used by the new forge chain. MC-free by design
 * ({@code java.*} imports only) so it can be unit tested without the Minecraft classpath.
 */
public final class LegacyRuneNames {

    private static final Set<String> LEGACY_ELEMENTS = Set.of("fire", "frost", "storm", "void", "radiant", "venom");
    private static final Set<String> LEGACY_TEMPERS = Set.of("keen", "heavy", "swift");
    private static final String DEFAULT_TEMPER = "keen";
    private static final int DEFAULT_QUALITY = 60;
    private static final int MIN_QUALITY = 0;
    private static final int MAX_QUALITY = 100;
    private static final int BINDING_MODIFIER_THRESHOLD = 2;

    private LegacyRuneNames() {}

    /** Strips an optional {@code namespace:} prefix and lower-cases; only the six legacy attributes pass. */
    public static Optional<String> elementPath(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String path = stripNamespace(raw).toLowerCase(Locale.ROOT);
        return LEGACY_ELEMENTS.contains(path) ? Optional.of(path) : Optional.empty();
    }

    /** Legacy grade names (CRUDE/FINE/HIGH/DIVINE) map 1:1 onto {@link ForgeGrade}. */
    public static Optional<ForgeGrade> grade(String raw) {
        return ForgeGrade.byName(raw);
    }

    /** Case-insensitive; falls back to {@code keen} for anything unrecognised, blank, or null. */
    public static String temperPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_TEMPER;
        }
        String path = raw.toLowerCase(Locale.ROOT);
        return LEGACY_TEMPERS.contains(path) ? path : DEFAULT_TEMPER;
    }

    /** Legacy binding tiers 2 and 3 both carry the single new "binding" modifier; tier 1 carries none. */
    public static List<String> modifiers(int binding) {
        return binding >= BINDING_MODIFIER_THRESHOLD ? List.of("binding") : List.of();
    }

    /** Defaults to 60 when the legacy tag omitted the quality field; otherwise clamps to 0..100. */
    public static int quality(boolean present, int value) {
        if (!present) {
            return DEFAULT_QUALITY;
        }
        return Math.max(MIN_QUALITY, Math.min(MAX_QUALITY, value));
    }

    private static String stripNamespace(String raw) {
        int colon = raw.indexOf(':');
        return colon >= 0 ? raw.substring(colon + 1) : raw;
    }
}
