package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.registry.MagicalSounds;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.sounds.Music;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * The Supreme Deity's theme, playing for as long as this client is inside the domain.
 *
 * <p>Routed through vanilla's own {@code MusicManager} rather than a hand-rolled sound instance:
 * that is what buys the Music volume slider, the fade, and - the part that actually matters -
 * suppression of the dimension's own situational music instead of layering underneath it.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class UnwakingMusic {

    /**
     * Zero delay at both ends, the same four arguments vanilla hands the Ender Dragon fight.
     * {@code minDelay == maxDelay == 0} makes the manager restart the track the instant it ends
     * rather than waiting out a gap - a domain run routinely outlasts the five minutes of audio.
     * {@code replaceCurrentMusic} is what lets it cut in over whatever was already playing.
     */
    private static final Music THEME = new Music(MagicalSounds.MUSIC_SUPREME_DEITY, 0, 0, true);

    private UnwakingMusic() {}

    /**
     * Lowest priority on purpose. NeoForge runs high-priority listeners first and
     * {@link SelectMusicEvent#overrideMusic} cancels the event, so the last listener to run is the
     * one whose track survives - and biome and dimension music sit at the default priority above
     * this one.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSelectMusic(SelectMusicEvent event) {
        if (ClientUnwakingEncounter.inDomainFight()) {
            event.overrideMusic(new MusicInfo(THEME));
        }
    }
}
