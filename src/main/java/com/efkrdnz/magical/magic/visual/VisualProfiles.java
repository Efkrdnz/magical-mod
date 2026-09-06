package com.efkrdnz.magical.magic.visual;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Registry of per-skill visual profiles, indexed parallel to {@link MagicContent#orderedSkillIds()}
 * so entities can sync one int. {@link #validate()} enforces the roster-wide uniqueness rules.
 */
public final class VisualProfiles {
    private static final Map<ResourceLocation, VisualProfile> PROFILES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, VisualProfile> DEFAULTS = new HashMap<>();
    private static VisualProfile[] byIndex = new VisualProfile[0];

    private VisualProfiles() {}

    public static void register(MagicSkillDefinition definition, VisualProfile profile) {
        PROFILES.put(definition.id(), profile);
        byIndex = new VisualProfile[0];
    }

    public static void register(MagicSkillDefinition definition, VisualProfile.Builder builder) {
        register(definition, builder.build());
    }

    /** Never null: falls back to a material default (flagged by validate()). */
    public static VisualProfile of(ResourceLocation id) {
        VisualProfile profile = PROFILES.get(id);
        if (profile != null) {
            return profile;
        }
        return DEFAULTS.computeIfAbsent(id, key -> {
            MagicSkillDefinition definition = MagicContent.get(key);
            if (definition == null) {
                definition = MagicContent.get(MagicContent.STARTER_SKILL);
            }
            return VisualProfile.defaultFor(definition);
        });
    }

    public static VisualProfile of(MagicSkillDefinition definition) {
        return of(definition.id());
    }

    public static boolean hasExplicit(ResourceLocation id) {
        return PROFILES.containsKey(id);
    }

    /** Index into MagicContent.orderedSkillIds(); -1 if unknown. */
    public static int indexOf(ResourceLocation id) {
        return MagicContent.skillIndex(id);
    }

    public static VisualProfile byIndex(int index) {
        List<ResourceLocation> ids = MagicContent.orderedSkillIds();
        if (index < 0 || index >= ids.size()) {
            return of(MagicContent.STARTER_SKILL);
        }
        if (byIndex.length != ids.size()) {
            VisualProfile[] table = new VisualProfile[ids.size()];
            for (int i = 0; i < ids.size(); i++) {
                table[i] = of(ids.get(i));
            }
            byIndex = table;
        }
        VisualProfile profile = byIndex[index];
        return profile != null ? profile : of(ids.get(index));
    }

    public static Map<ResourceLocation, VisualProfile> all() {
        return java.util.Collections.unmodifiableMap(PROFILES);
    }

    /**
     * Roster-wide uniqueness rules. Returns the list of violations; callers decide whether to throw
     * (dev / JUnit) or log (production).
     */
    public static List<String> validate() {
        List<String> problems = new ArrayList<>();
        Map<Silhouette.SilhouetteKey, ResourceLocation> silhouettes = new HashMap<>();
        Map<String, ResourceLocation> signatures = new HashMap<>();
        Map<EmblemId, ResourceLocation> emblems = new HashMap<>();
        Map<String, ResourceLocation> soundAccents = new HashMap<>();
        Map<String, ResourceLocation> victimOverlays = new HashMap<>();
        Map<String, Set<Integer>> schoolSides = new HashMap<>();
        Map<String, Set<Integer>> schoolStamps = new HashMap<>();
        Map<String, Set<String>> schoolForms = new HashMap<>();

        for (ResourceLocation id : MagicContent.orderedSkillIds()) {
            MagicSkillDefinition definition = MagicContent.get(id);
            if (definition == null || MagicContent.isSubSkill(id)) {
                continue;
            }
            VisualProfile profile = PROFILES.get(id);
            if (profile == null) {
                if (definition.tier() >= 0) {
                    problems.add(SOFT + id + ": no explicit VisualProfile (default in use)");
                }
                continue;
            }
            CircleScript circle = profile.castCircle();
            String school = profile.material().name();
            if (circle.emblemLayerCount() != 1) {
                problems.add(id + ": circle must have exactly one EMBLEM layer");
            }
            if (circle.emblem() == EmblemId.BLANK) {
                problems.add(id + ": circle emblem is BLANK");
            } else {
                ResourceLocation other = emblems.putIfAbsent(circle.emblem(), id);
                if (other != null) {
                    problems.add(id + ": emblem " + circle.emblem() + " already used by " + other);
                }
            }
            if (circle.hotLayerCount() != 1) {
                problems.add(id + ": circle must have exactly one HOT layer (has " + circle.hotLayerCount() + ")");
            }
            if (circle.layers().size() < 3 || circle.layers().size() > 14) {
                problems.add(id + ": circle layer count " + circle.layers().size() + " outside 3..14");
            }
            ResourceLocation sigOther = signatures.putIfAbsent(circle.signature(), id);
            if (sigOther != null) {
                problems.add(id + ": circle signature " + circle.signature() + " collides with " + sigOther);
            }
            if (!schoolSides.computeIfAbsent(school, k -> new HashSet<>()).add(circle.outerSides())) {
                problems.add(id + ": frame sides " + circle.outerSides() + " already used within " + school);
            }
            circle.stampLayer().ifPresent(stamp -> {
                if (!schoolStamps.computeIfAbsent(school, k -> new HashSet<>()).add(stamp.stampOrEmblem())) {
                    problems.add(id + ": stamp " + stamp.stampOrEmblem() + " already used within " + school);
                }
            });
            Silhouette.SilhouetteKey key = profile.silhouette();
            if (profile.primary().family() != Silhouette.Family.CUSTOM) {
                ResourceLocation silOther = silhouettes.putIfAbsent(key, id);
                if (silOther != null) {
                    problems.add(id + ": primary silhouette " + key + " collides with " + silOther);
                }
                String formKey = profile.primary().family() + "/" + profile.primary().form();
                if (!schoolForms.computeIfAbsent(school, k -> new HashSet<>()).add(formKey)) {
                    problems.add(id + ": silhouette family/form " + formKey + " already used within " + school);
                }
            }
            // sound accent uniqueness is enforced once the mod ships its own SoundEvents (phase 2)
            soundAccents.putIfAbsent("release:" + profile.sounds().release().key(), id);
            String victimKey = school + ":" + profile.impact().victimOverlay() + ":" + profile.paletteVariant();
            ResourceLocation vOther = victimOverlays.putIfAbsent(victimKey, id);
            if (vOther != null) {
                problems.add(id + ": victim overlay " + victimKey + " duplicates " + vOther);
            }
            if (profile.budgetClass() > definition.tier() + 1 && definition.tier() >= 0) {
                problems.add(id + ": budgetClass " + profile.budgetClass() + " exceeds tier+1");
            }
        }
        return problems;
    }

    /** Prefix for advisory problems (missing explicit profiles) that never fail a strict run. */
    public static final String SOFT = "soft: ";

    public static List<String> hardProblems() {
        List<String> hard = new ArrayList<>();
        for (String problem : validate()) {
            if (!problem.startsWith(SOFT)) {
                hard.add(problem);
            }
        }
        return hard;
    }

    public static void validateAndReport(boolean strict) {
        List<String> problems = validate();
        int hard = 0;
        for (String problem : problems) {
            if (problem.startsWith(SOFT)) {
                MagicalMod.LOGGER.debug("VisualProfiles: {}", problem);
            } else {
                hard++;
                MagicalMod.LOGGER.warn("VisualProfiles: {}", problem);
            }
        }
        MagicalMod.LOGGER.info("VisualProfiles: {} explicit profiles, {} collisions, {} skills on defaults", PROFILES.size(), hard, problems.size() - hard);
        if (strict && hard > 0) {
            throw new IllegalStateException("VisualProfiles validation failed with " + hard + " collisions; see log");
        }
    }
}
