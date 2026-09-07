package com.efkrdnz.magical.forge.art;

/**
 * When an Art is allowed to run. An Art whose trigger does not match the press returns without
 * doing anything, so the pair falls back to the plain element rider.
 *
 * <p>Deliberately Minecraft-free: the trigger table is data, and a plain unit test pins it.</p>
 */
public enum ArtTrigger {

    /** Every non-charged press. */
    LIGHT,
    /** Only the released charge. */
    HEAVY,
    /** Both, every press of the pair. */
    ANY,
    /** Only the last step of the chain. */
    FINISHER,
    /** The last step of the chain, or any released charge. */
    FINISHER_OR_HEAVY;

    public boolean matches(boolean heavy, boolean finisher) {
        return switch (this) {
            case LIGHT -> !heavy;
            case HEAVY -> heavy;
            case ANY -> true;
            case FINISHER -> finisher;
            case FINISHER_OR_HEAVY -> finisher || heavy;
        };
    }
}
