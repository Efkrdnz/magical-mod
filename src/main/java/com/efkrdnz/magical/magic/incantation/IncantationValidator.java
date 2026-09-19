package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Pure, so the server and the screen produce the same words for the same tape. */
public final class IncantationValidator {

    public enum Problem {
        TOO_LONG,
        BAD_BREATH,
        UNKNOWN_VERSE,
        NOT_KNOWN
    }

    /** {@code index} is -1 for a problem with the tape as a whole. */
    public record Finding(Problem problem, int index, ResourceLocation id) {
    }

    private IncantationValidator() {
    }

    /** Structure and catalogue only; what a write checks. */
    public static List<Finding> problems(List<ResourceLocation> ids, int breath, VerseCatalogue catalogue) {
        return problems(ids, breath, null, catalogue);
    }

    /** With {@code known} the wielder's set as well; what the server checks on an edit. Null skips that check. */
    public static List<Finding> problems(List<ResourceLocation> ids, int breath, Set<ResourceLocation> known,
                                         VerseCatalogue catalogue) {
        List<Finding> findings = new ArrayList<>();
        if (ids.size() > ReciteCaps.MAX_VERSES) {
            findings.add(new Finding(Problem.TOO_LONG, -1, null));
        }
        if (breath < ReciteCaps.MIN_BREATH || breath > ReciteCaps.MAX_BREATH) {
            findings.add(new Finding(Problem.BAD_BREATH, -1, null));
        }
        for (int i = 0; i < ids.size(); i++) {
            ResourceLocation id = ids.get(i);
            if (id == null || !catalogue.contains(id)) {
                findings.add(new Finding(Problem.UNKNOWN_VERSE, i, id));
            } else if (known != null && !known.contains(id)) {
                findings.add(new Finding(Problem.NOT_KNOWN, i, id));
            }
        }
        return findings;
    }
}
