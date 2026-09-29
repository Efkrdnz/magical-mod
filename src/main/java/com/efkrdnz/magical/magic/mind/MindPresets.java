package com.efkrdnz.magical.magic.mind;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Three worked reveries from the design, for captures and for a new wielder to read: offsets are
 * from the air cell in front of the face the wielder aims at, written facing south.
 */
public final class MindPresets {
    public static final List<String> NAMES = List.of("pit", "wall", "cat");

    private MindPresets() {}

    public static Reverie named(String name) {
        return switch (name) {
            case "pit" -> pit();
            case "wall" -> wall();
            case "cat" -> cat();
            default -> null;
        };
    }

    /** A lid of grass level with the ground, for a two-deep pit whose floor is aimed at. */
    private static Reverie pit() {
        Reverie reverie = start("Pit");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                reverie.put(new ImaginedBlock(new Offset(x, 1, z), "minecraft:grass_block", Set.of()));
            }
        }
        return reverie;
    }

    /** Five wide, three tall, two blocks ahead, in cobblestone, with a shadow. */
    private static Reverie wall() {
        Reverie reverie = start("Wall");
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y < 3; y++) {
                reverie.put(new ImaginedBlock(new Offset(x, y, 2), "minecraft:cobblestone", EnumSet.of(Sense.SHADOW)));
            }
        }
        return reverie;
    }

    /** A cat on Guard, staring, with its voice on: the creeper stopper. */
    private static Reverie cat() {
        Reverie reverie = start("Cat");
        reverie.put(new Figment(new Offset(0, 0, 1), "minecraft:cat", new Script(Stance.GUARD, Reaction.STARE),
                EnumSet.of(Sense.SOUND)));
        return reverie;
    }

    private static Reverie start(String name) {
        Reverie reverie = new Reverie();
        reverie.setName(name);
        reverie.setFacing(0);
        return reverie;
    }

    public static Set<String> impressions(Reverie reverie) {
        Set<String> keys = new LinkedHashSet<>();
        reverie.blocks().forEach(block -> keys.add(Impression.block(block.blockId()).key()));
        reverie.figments().forEach(figment -> keys.add(Impression.creature(figment.creatureId()).key()));
        return keys;
    }
}
