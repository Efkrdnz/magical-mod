package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import net.minecraft.resources.ResourceLocation;

/** Cast-position verses, Fresh Page and Blood Toll. */
public final class UtilityVerses {

    public static final ResourceLocation FRESH_PAGE = VerseIds.of("fresh_page");
    public static final ResourceLocation STEP_WORD = VerseIds.of("step_word");
    public static final ResourceLocation NEAR_WORD = VerseIds.of("near_word");
    public static final ResourceLocation BLOOD_TOLL = VerseIds.of("blood_toll");

    private UtilityVerses() {
    }

    static void register(VerseCatalogue c) {
        // RESET: recharge -25 frames, hand and deck to discard, and the first time rebuild the deck and forbid wrapping.
        c.register(Verse.of("fresh_page", VerseType.UTILITY, 6, Verse.UNLIMITED, null, 1, Declared.of(0, 0, -8), (r, rec, it) -> {
            r.addRest(-8);
            r.refreshPage();
            return VerseAction.NONE;
        }).asRecursive());
        // LONG_DISTANCE_CAST: an expiration-trigger utility, -5 frames. Deltas first, as everywhere else,
        // so the word is stamped with its own beat.
        c.register(Verse.of("far_word", VerseType.UTILITY, 0, Verse.UNLIMITED, VersePrototypes.WORD_FAR, 1, Declared.of(1, -2, 0), (r, rec, it) -> {
            r.state().addBeat(-2);
            r.addProjectileEpitaph(VersePrototypes.WORD_FAR, 1);
            return VerseAction.NONE;
        }));
        // TELEPORT_CAST: the carrier carries the caster to where it dies, then the payload fires there.
        c.register(Verse.of("step_word", VerseType.UTILITY, 18, Verse.UNLIMITED, VersePrototypes.WORD_STEP, 1, Declared.of(1, 7, 0), (r, rec, it) -> {
            r.state().addBeat(7);
            r.state().addSpread(24.0D);
            r.addProjectileEpitaph(VersePrototypes.WORD_STEP, 1);
            return VerseAction.NONE;
        }));
        // CASTER_CAST: the next bodies spawn on the caster.
        c.register(Verse.of("near_word", VerseType.UTILITY, 4, Verse.UNLIMITED, null, 1, Declared.of(1, 0, 0), (r, rec, it) -> {
            r.state().addSpread(-24.0D);
            r.state().behaviour(Behaviour.NEAR_WORD);
            r.drawActions(1);
            return VerseAction.NONE;
        }));
        // BLOOD_MAGIC: a refund of mana paid in health, as true damage. The Lua draws first and takes
        // the hp off afterwards, so the drawn verse is cast whether or not the price kills the caster.
        c.register(Verse.of("blood_toll", VerseType.UTILITY, -30, Verse.UNLIMITED, null, 1, Declared.of(1, -7, -7), (r, rec, it) -> {
            r.state().addBeat(-7);
            r.addRest(-7);
            r.drawActions(1);
            r.world().payHealth(4.0D);
            return VerseAction.NONE;
        }));
    }
}
