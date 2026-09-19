package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import net.minecraft.resources.ResourceLocation;

/** Cast-position verses, Fresh Page and Blood Toll. */
public final class UtilityVerses {

    public static final ResourceLocation FRESH_PAGE = VerseIds.of("fresh_page");

    private UtilityVerses() {
    }

    static void register(VerseCatalogue c) {
        // RESET: recharge -25 frames, hand and deck to discard, and the first time rebuild the deck and forbid wrapping.
        c.register(Verse.of("fresh_page", VerseType.UTILITY, 6, Verse.UNLIMITED, null, 1, Declared.of(0, 0, -8), (r, rec, it) -> {
            r.addRest(-8);
            r.refreshPage();
            return VerseAction.NONE;
        }).asRecursive());
        // LONG_DISTANCE_CAST: an expiration-trigger utility, -5 frames.
        c.register(Verse.of("far_word", VerseType.UTILITY, 0, Verse.UNLIMITED, VersePrototypes.WORD_FAR, 1, Declared.of(1, -2, 0), (r, rec, it) -> {
            r.addProjectileEpitaph(VersePrototypes.WORD_FAR, 1);
            r.state().addBeat(-2);
            return VerseAction.NONE;
        }));
    }
}
