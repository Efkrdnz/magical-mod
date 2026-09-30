package com.efkrdnz.magical.magic.primordial;

import com.efkrdnz.magical.magic.MagicSchool;
import java.util.Locale;

/**
 * What a Cyclone is made of. It starts as {@link #DUST} and takes the element of whatever its owner
 * feeds it, or of the land it drifts over while it is still calm.
 *
 * <p>{@code damage}, {@code pull} and {@code reach} multiply the storm's base hit, its draw on
 * bodies and its radius; everything else an element does is a rider applied on each pulse by the
 * skill. The colour is the element's matter on the client.
 */
public enum StormElement {
    DUST(0xB8A988, 1.0F, 1.0F, 1.0F),
    EMBER(0xFF7A2E, 1.25F, 1.0F, 1.0F),
    TIDE(0x4FA8E0, 1.0F, 1.3F, 1.0F),
    FROST(0xBFEFFF, 1.0F, 1.0F, 1.0F),
    RADIANT(0xFFE68A, 1.0F, 1.0F, 1.0F),
    MAELSTROM(0x8E6BFF, 1.0F, 1.8F, 1.3F),
    BLIGHT(0x5A4670, 1.0F, 1.0F, 1.0F),
    CRIMSON(0xC4122B, 1.0F, 1.0F, 1.0F),
    ARCANE(0x72E4FF, 1.0F, 1.0F, 1.0F),
    STEEL(0xC9D3DC, 1.6F, 1.0F, 1.0F),
    STONE(0x8A7458, 1.25F, 1.15F, 1.0F),
    DEEP(0x2FBF9E, 1.0F, 1.0F, 1.0F);

    private final int color;
    private final float damage;
    private final float pull;
    private final float reach;

    StormElement(int color, float damage, float pull, float reach) {
        this.color = color;
        this.damage = damage;
        this.pull = pull;
        this.reach = reach;
    }

    public int color() {
        return color;
    }

    public float damage() {
        return damage;
    }

    public float pull() {
        return pull;
    }

    public float reach() {
        return reach;
    }

    public String key() {
        return "element.magical.storm." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * The element a swallowed spell feeds. Chaos feeds {@link #DUST}, which the feed rule reads as
     * a cleanse; a frost-accented skill feeds {@link #FROST} whatever school it is filed under;
     * the Primordial skills feed the element of the disaster they are.
     */
    public static StormElement fed(MagicSchool school, String skillPath, boolean frost) {
        if (school == MagicSchool.CHAOS) {
            return DUST;
        }
        if (frost) {
            return FROST;
        }
        return switch (school) {
            case FIRE -> EMBER;
            case WATER -> TIDE;
            case LIGHT, SOUL -> RADIANT;
            case VOID, SPATIAL -> MAELSTROM;
            case DARK -> BLIGHT;
            case BLOOD -> CRIMSON;
            case ARCANE -> ARCANE;
            case SWORD -> STEEL;
            case ELDRITCH -> DEEP;
            case CHAOS -> DUST;
            case PRIMORDIAL -> switch (skillPath) {
                case "caldera" -> EMBER;
                case "tsunami" -> TIDE;
                default -> STONE;
            };
        };
    }

    /** The land under a calm storm: lava makes it Ember, water Tide, anything else nothing. */
    public static StormElement ground(boolean lava, boolean water) {
        if (lava) {
            return EMBER;
        }
        if (water) {
            return TIDE;
        }
        return null;
    }
}
