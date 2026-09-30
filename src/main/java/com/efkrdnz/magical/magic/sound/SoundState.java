package com.efkrdnz.magical.magic.sound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

/**
 * The wielder half of the Authority of Sound: the Song they wrote and the Riff they wrote. Saved with
 * the magic state and synced with it, so the Score screen reads it from the client copy and a
 * performance can start without the score being sent again by the wielder.
 */
public final class SoundState {

    private Score song = SongPresets.pulse();
    private Riff riff = Riff.standard();

    public Score song() {
        return song;
    }

    public Riff riff() {
        return riff;
    }

    public void setSong(Score song) {
        this.song = song == null ? SongPresets.pulse() : song;
    }

    public void setRiff(Riff riff) {
        this.riff = riff == null ? Riff.standard() : riff;
    }

    /** Back to what every wielder starts with. Used when the Authority is lost. */
    public void clear() {
        song = SongPresets.pulse();
        riff = Riff.standard();
    }

    public void copyFrom(SoundState other) {
        song = other.song;
        riff = other.riff;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("song", saveSong(song));
        tag.put("riff", saveRiff(riff));
        return tag;
    }

    public void load(CompoundTag tag) {
        song = tag.contains("song") ? loadSong(tag.getCompound("song")) : SongPresets.pulse();
        riff = tag.contains("riff") ? loadRiff(tag.getCompound("riff")) : Riff.standard();
    }

    public static CompoundTag saveSong(Score score) {
        CompoundTag tag = new CompoundTag();
        tag.putString("tempo", score.tempo().name());
        tag.putString("scale", score.scale().name());
        tag.put("rows", new LongArrayTag(score.rows()));
        return tag;
    }

    public static Score loadSong(CompoundTag tag) {
        return Score.of(Tempo.byName(tag.getString("tempo")), Scale.byName(tag.getString("scale")), tag.getLongArray("rows"));
    }

    public static CompoundTag saveRiff(Riff riff) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("amplitude", riff.amplitude());
        tag.putIntArray("instruments", riff.instrumentOrdinals());
        tag.putIntArray("pitches", riff.pitches());
        return tag;
    }

    public static Riff loadRiff(CompoundTag tag) {
        return Riff.of(tag.getInt("amplitude"), tag.getIntArray("instruments"), tag.getIntArray("pitches"));
    }
}
