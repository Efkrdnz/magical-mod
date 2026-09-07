package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.forge.FormFamily;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** The voice of each form family, played at the wielder on every press whether it lands or not. */
public final class ForgeSounds {

    private static final float VOLUME = 0.9f;

    private ForgeSounds() {}

    public record Cue(SoundEvent sound, float pitch) {}

    public static Cue forFamily(FormFamily family) {
        return switch (family) {
            case SLASH -> new Cue(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f);
            case CLEAVE -> new Cue(SoundEvents.PLAYER_ATTACK_STRONG, 0.9f);
            case THRUST -> new Cue(SoundEvents.PLAYER_ATTACK_CRIT, 1.2f);
            case SPIN -> new Cue(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f);
            case SLAM -> new Cue(SoundEvents.ANVIL_LAND, 1.4f);
            case WAVE -> new Cue(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f);
            case RISING -> new Cue(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.1f);
            case FLURRY -> new Cue(SoundEvents.PLAYER_ATTACK_WEAK, 1.5f);
        };
    }

    public static void play(ServerLevel level, ServerPlayer player, FormFamily family) {
        Cue cue = forFamily(family);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), cue.sound(), SoundSource.PLAYERS,
                VOLUME, cue.pitch());
    }
}
