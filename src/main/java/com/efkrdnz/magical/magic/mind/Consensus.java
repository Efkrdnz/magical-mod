package com.efkrdnz.magical.magic.mind;

import java.util.Set;

/**
 * When a lie becomes real. An element has a weight - half a point a block, a quarter of a creature's
 * health - and a consensus, the sum of each convinced viewer's belief times how much their mind
 * counts. It manifests when the consensus reaches the weight and holds while it stays above half.
 */
public final class Consensus {
    public static final float PER_BLOCK = 0.5F;
    public static final float PER_HEALTH = 0.25F;
    /** A real thing stays real down to this fraction of its weight. */
    public static final float HOLD = 0.5F;
    /** How long the lilac rim takes to fade off a thing that has just become real. */
    public static final int HARDEN_TICKS = 16;

    private static final float MIND = 1.0F;
    private static final float PLAYER = 3.0F;
    private static final float BOSS = 5.0F;
    private static final Set<String> BOSSES = Set.of("minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian");
    private static final double ROUNDING = 1.0E-4;

    private Consensus() {}

    public static float clusterWeight(int blocks) {
        return PER_BLOCK * blocks;
    }

    public static float figmentWeight(float maxHealth) {
        return PER_HEALTH * maxHealth;
    }

    /** How much one convinced mind of this kind counts toward making a thing real. */
    public static float voter(String entityTypeId) {
        if ("minecraft:player".equals(entityTypeId)) {
            return PLAYER;
        }
        return BOSSES.contains(entityTypeId) ? BOSS : MIND;
    }

    /** Whether an element is real after this step, given whether it was real before it. */
    public static boolean real(boolean manifested, float consensus, float weight) {
        if (weight <= 0.0F) {
            return false;
        }
        return consensus >= (manifested ? weight * HOLD : weight);
    }

    /** How many minds of one weight, each sure of it, make a thing of this weight real; -1 for never. */
    public static int needed(float weight, float voter) {
        if (voter <= 0.0F) {
            return -1;
        }
        return (int) Math.ceil(weight / (voter * Belief.SURE) - ROUNDING);
    }

    /** The share of its kind's health a real figment has, by how firmly it is agreed on. */
    public static float healthFraction(float consensus, float weight) {
        return weight <= 0.0F ? 1.0F : Math.min(1.0F, consensus / weight);
    }
}
