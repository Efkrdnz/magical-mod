package com.efkrdnz.magical.magic.mind;

/** One thing the wielder has looked at long enough to imagine: a block or a creature, by id. */
public record Impression(Kind kind, String id) {
    public enum Kind { BLOCK, CREATURE }

    private static final String BLOCK_PREFIX = "block:";
    private static final String CREATURE_PREFIX = "creature:";
    private static final String PLAYER = "minecraft:player";
    /** A sleeping player's body; a player in all but the name. */
    private static final String SLEEPER = "magical:sleeper";

    public static Impression block(String id) {
        return new Impression(Kind.BLOCK, id);
    }

    public static Impression creature(String id) {
        return new Impression(Kind.CREATURE, id);
    }

    /** The key back into an impression, or null for anything that is not one. */
    public static Impression parse(String key) {
        if (key == null) {
            return null;
        }
        if (key.startsWith(BLOCK_PREFIX) && key.length() > BLOCK_PREFIX.length()) {
            return block(key.substring(BLOCK_PREFIX.length()));
        }
        if (key.startsWith(CREATURE_PREFIX) && key.length() > CREATURE_PREFIX.length()) {
            String id = key.substring(CREATURE_PREFIX.length());
            return PLAYER.equals(id) || SLEEPER.equals(id) ? null : creature(id);
        }
        return null;
    }

    public String key() {
        return (kind == Kind.BLOCK ? BLOCK_PREFIX : CREATURE_PREFIX) + id;
    }
}
