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

    /** The reveal on the creator screen when a fusion lands; on the UI channel like the rule cues. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CREATION =
            SOUNDS.register("creator.creation", () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "creator.creation")));

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

    /**
     * The Riff's sixteen voices, synthesized by {@code scripts/synth-instruments.py} and named as the
     * note block names its instruments. Each is one note at its centre pitch, shifted by the game.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_HARP = note("harp");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_BASEDRUM = note("basedrum");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_SNARE = note("snare");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_HAT = note("hat");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_BASS = note("bass");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_FLUTE = note("flute");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_BELL = note("bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_GUITAR = note("guitar");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_CHIME = note("chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_XYLOPHONE = note("xylophone");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_IRON_XYLOPHONE = note("iron_xylophone");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_COW_BELL = note("cow_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_DIDGERIDOO = note("didgeridoo");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_BIT = note("bit");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_BANJO = note("banjo");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOTE_PLING = note("pling");

    /** The Song's kit: three drums, a bass and a lead, on the record channel like a jukebox. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SONG_KICK = song("kick");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONG_SNARE = song("snare");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONG_HAT = song("hat");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONG_BASS = song("bass");
    public static final DeferredHolder<SoundEvent, SoundEvent> SONG_LEAD = song("lead");

    private MagicalSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> note(String name) {
        return SOUNDS.register("note." + name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "note." + name)));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> song(String name) {
        return SOUNDS.register("song." + name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "song." + name)));
    }

    /** The voice of one Riff instrument. */
    public static DeferredHolder<SoundEvent, SoundEvent> note(com.efkrdnz.magical.magic.sound.Instrument instrument) {
        return switch (instrument) {
            case HARP -> NOTE_HARP;
            case BASEDRUM -> NOTE_BASEDRUM;
            case SNARE -> NOTE_SNARE;
            case HAT -> NOTE_HAT;
            case BASS -> NOTE_BASS;
            case FLUTE -> NOTE_FLUTE;
            case BELL -> NOTE_BELL;
            case GUITAR -> NOTE_GUITAR;
            case CHIME -> NOTE_CHIME;
            case XYLOPHONE -> NOTE_XYLOPHONE;
            case IRON_XYLOPHONE -> NOTE_IRON_XYLOPHONE;
            case COW_BELL -> NOTE_COW_BELL;
            case DIDGERIDOO -> NOTE_DIDGERIDOO;
            case BIT -> NOTE_BIT;
            case BANJO -> NOTE_BANJO;
            case PLING -> NOTE_PLING;
        };
    }

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
