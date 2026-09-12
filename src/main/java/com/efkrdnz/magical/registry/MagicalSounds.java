package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own sound events. Everything else in the mod plays vanilla {@code SoundEvents}. */
public final class MagicalSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, MagicalMod.MODID);

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

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
