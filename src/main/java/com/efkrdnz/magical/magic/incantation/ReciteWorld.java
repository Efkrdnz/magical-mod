package com.efkrdnz.magical.magic.incantation;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything a verse may ask the world. The server answers over the level and the Grimoire; a test
 * answers with fixed numbers; the future preview answers with assumed ones. Nothing in the evaluator
 * knows which.
 */
public interface ReciteWorld {

    int enemiesWithin(double blocks);

    int projectilesWithin(double blocks);

    /** 0..1. */
    double healthFraction();

    /** Clause: Every Other's shared toggle: whether to skip this time, then flipped. */
    boolean everyOtherSkipAndFlip();

    /** 0 inclusive to {@code bound} exclusive. */
    int random(int bound);

    /** The whole catalogue, in registration order. */
    List<Verse> allVerses();

    boolean isKnown(ResourceLocation id);

    /** Every verse written in the wielder's other incantations, for Wild Recall. */
    List<Verse> otherIncantationVerses();

    /** Blood Toll's price, in half-hearts of true damage. */
    void payHealth(double halfHearts);
}
