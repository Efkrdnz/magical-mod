package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.SpaceRuleChange;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own sound events: the boss theme and the rule flash's cues. Everything else in the
 * mod plays vanilla {@code SoundEvents}. {@code MagicalSoundsAssetsTest} checks each holder has
 * its sounds.json entry and its file.
 */
public final class MagicalSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MagicalMod.MODID);

    /** The rule flash's stamp: every accepted rule opens with it, before the kind's own cue. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_STAMP = rule("stamp");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_RAISE = rule("raise");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_LOWER = rule("lower");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_ZERO = rule("zero");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_FLIP = rule("flip");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_LOCK = rule("lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_SURGE = rule("surge");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_AIM = rule("aim");
    public static final DeferredHolder<SoundEvent, SoundEvent> RULE_RESTORE = rule("restore");

    /**
     * The Supreme Deity's theme, played for the domain half of the encounter.
     *
     * <p>Variable range rather than fixed: music is played non-positionally through the music
     * manager, so an attenuation distance is never consulted, and a variable-range event is what
     * every vanilla music track uses.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SUPREME_DEITY =
            SOUNDS.register("music.supreme_deity", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "music.supreme_deity")));

    private MagicalSounds() {}

    /** A cue under {@code rule.}; variable range like the music, since it plays on the UI channel. */
    private static DeferredHolder<SoundEvent, SoundEvent> rule(String name) {
        return SOUNDS.register("rule." + name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "rule." + name)));
    }

    /** The cue for a kind of rule change. */
    public static DeferredHolder<SoundEvent, SoundEvent> cue(SpaceRuleChange change) {
        return switch (change) {
            case RAISE -> RULE_RAISE;
            case LOWER -> RULE_LOWER;
            case ZERO -> RULE_ZERO;
            case FLIP -> RULE_FLIP;
            case LOCK -> RULE_LOCK;
            case SURGE -> RULE_SURGE;
            case AIM -> RULE_AIM;
            case RESTORE -> RULE_RESTORE;
        };
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
