package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Verses that do nothing when read and everything while written. Read, one draws the next verse
 * and that is all - transparent to composition, as Noita's passives are, so a tape of Taper, Needle
 * is a tape of Needle - and an Impose walks over it. Written in any of the four incantations, it is
 * on ({@link #written}), and {@code VersePassives} does its work every tick. Free and unlimited: a
 * wielder who wrote nothing else can still carry a light.
 */
public final class PassiveVerses {

    public static final ResourceLocation TAPER = VerseIds.of("taper");
    public static final ResourceLocation STORM_TAPER = VerseIds.of("storm_taper");
    public static final ResourceLocation HALO = VerseIds.of("halo");
    public static final ResourceLocation HALF_HALO = VerseIds.of("half_halo");
    public static final ResourceLocation FAMILIAR = VerseIds.of("familiar");

    private PassiveVerses() {
    }

    /** The whole of a passive's action: the next verse. */
    static Verse passive(String path) {
        return Verse.of(path, VerseType.PASSIVE, 0, Verse.UNLIMITED, null, 1, Declared.of(1, 0, 0), (r, rec, it) -> {
            r.drawActions(1);
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(passive("taper"));
        c.register(passive("storm_taper"));
        c.register(passive("halo"));
        c.register(passive("half_halo"));
        c.register(passive("familiar"));
    }

    /**
     * The passive verses written anywhere in the Grimoire, each once, in the order they are first
     * met. Anything that is not a passive, or that the catalogue does not know, is not counted.
     */
    public static Set<ResourceLocation> written(Grimoire grimoire, VerseCatalogue catalogue) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (int slot = 0; slot < Grimoire.SLOTS; slot++) {
            for (Incantation.Entry entry : grimoire.incantation(slot).entries()) {
                Verse verse = catalogue.get(entry.id());
                if (verse != null && verse.type() == VerseType.PASSIVE) {
                    ids.add(entry.id());
                }
            }
        }
        return ids;
    }
}
