package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The world a preview assumes: nobody near, nothing in flight, full health, the Every Other toggle
 * read off a private copy, a random that always answers zero, and a Blood Toll that is never paid.
 * A preview runs the real Reciter on a copy of the incantation (design section 4), so the only
 * thing about it that may be unreal is the world - this is that world, and it is pure.
 */
public final class PreviewReciteWorld implements ReciteWorld {
    private final Grimoire grimoire;
    private final int slot;
    private final VerseCatalogue catalogue;
    private boolean everyOtherSkip;

    public PreviewReciteWorld(Grimoire grimoire, int slot, VerseCatalogue catalogue) {
        this.grimoire = grimoire;
        this.slot = slot;
        this.catalogue = catalogue;
    }

    @Override
    public int enemiesWithin(double blocks) {
        return 0;
    }

    @Override
    public int projectilesWithin(double blocks) {
        return 0;
    }

    @Override
    public double healthFraction() {
        return 1.0D;
    }

    /** A private toggle, so a preview never moves the Grimoire's. */
    @Override
    public boolean everyOtherSkipAndFlip() {
        boolean skip = everyOtherSkip;
        everyOtherSkip = !everyOtherSkip;
        return skip;
    }

    /** Fixed: the first choice, every time, so two previews of one incantation agree. */
    @Override
    public int random(int bound) {
        return 0;
    }

    @Override
    public List<Verse> allVerses() {
        return List.copyOf(catalogue.all());
    }

    @Override
    public boolean isKnown(ResourceLocation id) {
        return grimoire.knows(id);
    }

    @Override
    public List<Verse> otherIncantationVerses() {
        return otherVerses(grimoire, slot, catalogue);
    }

    /** Nothing is paid: the plan shows the price, the press collects it. */
    @Override
    public void payHealth(double halfHearts) {
    }

    /** The verses written in the other slots, in slot order, each as many times as it is written. The level world reads the same list. */
    static List<Verse> otherVerses(Grimoire grimoire, int slot, VerseCatalogue catalogue) {
        List<Verse> verses = new ArrayList<>();
        for (int other = 0; other < Grimoire.SLOTS; other++) {
            if (other == slot) {
                continue;
            }
            for (Incantation.Entry entry : grimoire.incantation(other).entries()) {
                Verse verse = catalogue.get(entry.id());
                if (verse != null) {
                    verses.add(verse);
                }
            }
        }
        return verses;
    }
}
