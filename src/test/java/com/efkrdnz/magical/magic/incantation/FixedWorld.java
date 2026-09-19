package com.efkrdnz.magical.magic.incantation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** A world that answers with whatever the test set; {@code random} returns queued rolls, then zeros. */
final class FixedWorld implements ReciteWorld {

    int enemies;
    int projectiles;
    double health = 1.0D;
    boolean everyOtherSkip;
    final Deque<Integer> rolls = new ArrayDeque<>();
    List<Verse> all = List.copyOf(VerseContent.CATALOGUE.all());
    /** Null means every verse is known. */
    Set<ResourceLocation> known;
    List<Verse> others = List.of();
    final List<Double> healthPaid = new ArrayList<>();

    FixedWorld roll(int... values) {
        for (int value : values) {
            rolls.add(value);
        }
        return this;
    }

    @Override
    public int enemiesWithin(double blocks) {
        return enemies;
    }

    @Override
    public int projectilesWithin(double blocks) {
        return projectiles;
    }

    @Override
    public double healthFraction() {
        return health;
    }

    @Override
    public boolean everyOtherSkipAndFlip() {
        boolean skip = everyOtherSkip;
        everyOtherSkip = !everyOtherSkip;
        return skip;
    }

    @Override
    public int random(int bound) {
        Integer next = rolls.poll();
        return next == null ? 0 : Math.floorMod(next, bound);
    }

    @Override
    public List<Verse> allVerses() {
        return all;
    }

    @Override
    public boolean isKnown(ResourceLocation id) {
        return known == null || known.contains(id);
    }

    @Override
    public List<Verse> otherIncantationVerses() {
        return others;
    }

    @Override
    public void payHealth(double halfHearts) {
        healthPaid.add(halfHearts);
    }
}
