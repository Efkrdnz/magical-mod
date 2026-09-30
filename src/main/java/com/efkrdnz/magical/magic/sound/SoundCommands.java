package com.efkrdnz.magical.magic.sound;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /magical sound ...}: the Score by command, for captures and for testing a Song without
 * writing one. {@code song hit} lands the nearest note as a perfectly timed client would, which is
 * the only way an unattended capture can be on the beat.
 */
public final class SoundCommands {

    private SoundCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sound")
                .then(Commands.literal("score").executes(c -> run(c, (player, state) -> {
                    MagicalNetwork.sendOpenScore(player);
                    return 1;
                })))
                .then(Commands.literal("show").executes(c -> run(c, SoundCommands::show)))
                .then(Commands.literal("song")
                        .then(Commands.literal("start").executes(c -> run(c, (player, state) -> {
                            state.setSkillCooldown(MagicContent.SONG.id(), 0);
                            state.refillMana();
                            return SongService.playing(player) || SongService.start(player, state) ? 1 : 0;
                        })))
                        .then(Commands.literal("stop").executes(c -> run(c, (player, state) -> {
                            SongService.stop(player, "message.magical.song_stopped");
                            return 1;
                        })))
                        .then(Commands.literal("preset")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SongPresets.NAMES, b))
                                        .executes(c -> run(c, (player, state) -> {
                                            net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
                                            data.put("song", SoundState.saveSong(SongPresets.named(StringArgumentType.getString(c, "name"))));
                                            SongService.write(player, data);
                                            return 1;
                                        }))))
                        .then(Commands.literal("hit")
                                .then(Commands.argument("action", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("crouch", "swing"), b))
                                        .executes(c -> run(c, (player, state) -> {
                                            SongAction action = action(StringArgumentType.getString(c, "action"));
                                            return SongService.claim(player, action, SongService.nearestNote(player)) == null ? 0 : 1;
                                        }))))
                        .then(Commands.literal("dance").executes(c -> run(c, (player, state) -> {
                            SongService.danceNow(player);
                            return 1;
                        }))))
                .then(Commands.literal("riff")
                        .then(Commands.literal("set")
                                .then(Commands.argument("amplitude", IntegerArgumentType.integer(Riff.MIN_AMPLITUDE, Riff.MAX_AMPLITUDE))
                                        .then(Commands.argument("notes", StringArgumentType.greedyString())
                                                .executes(c -> run(c, (player, state) -> setRiff(player,
                                                        IntegerArgumentType.getInteger(c, "amplitude"), StringArgumentType.getString(c, "notes")))))))
                        .then(Commands.literal("note")
                                .then(Commands.argument("instrument", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(instrumentIds(), b))
                                        .then(Commands.argument("pitch", IntegerArgumentType.integer(0, Riff.PITCHES - 1))
                                                .then(Commands.argument("amplitude", IntegerArgumentType.integer(Riff.MIN_AMPLITUDE, Riff.MAX_AMPLITUDE))
                                                        .executes(c -> run(c, (player, state) -> {
                                                            Instrument instrument = Instrument.byId(StringArgumentType.getString(c, "instrument"));
                                                            if (instrument == null) {
                                                                return 0;
                                                            }
                                                            RiffService.play(player, RiffNote.resolve(instrument,
                                                                    IntegerArgumentType.getInteger(c, "pitch"), IntegerArgumentType.getInteger(c, "amplitude")));
                                                            return 1;
                                                        })))))));
    }

    /** {@code harp:12 rest bell:19}: an instrument and a pitch, or a rest, up to eight. */
    private static int setRiff(ServerPlayer player, int amplitude, String notes) {
        List<Integer> instruments = new ArrayList<>();
        List<Integer> pitches = new ArrayList<>();
        for (String token : notes.trim().split("\\s+")) {
            if (instruments.size() == Riff.MAX_NOTES) {
                break;
            }
            String[] parts = token.toLowerCase(Locale.ROOT).split(":");
            Instrument instrument = Instrument.byId(parts[0]);
            instruments.add(instrument == null ? Riff.REST : instrument.ordinal());
            int pitch = Riff.CENTRE;
            if (parts.length > 1) {
                try {
                    pitch = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    pitch = Riff.CENTRE;
                }
            }
            pitches.add(pitch);
        }
        Riff riff = Riff.of(amplitude, instruments.stream().mapToInt(Integer::intValue).toArray(),
                pitches.stream().mapToInt(Integer::intValue).toArray());
        net.minecraft.nbt.CompoundTag data = new net.minecraft.nbt.CompoundTag();
        data.put("riff", SoundState.saveRiff(riff));
        SongService.write(player, data);
        return 1;
    }

    private static int show(ServerPlayer player, PlayerMagicState state) {
        Score song = state.sound().song();
        Riff riff = state.sound().riff();
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Song: %s, %d bpm, %s, %d notes%s",
                song.tempo().name().toLowerCase(Locale.ROOT), song.tempo().bpm(), song.scale().name().toLowerCase(Locale.ROOT),
                song.noteCount(), SongService.playing(player) ? ", playing" : "")));
        StringBuilder notes = new StringBuilder();
        for (int slot = 0; slot < riff.length(); slot++) {
            Instrument instrument = riff.instrument(slot);
            notes.append(instrument == null ? "rest" : instrument.id() + ":" + riff.pitch(slot)).append(' ');
        }
        player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Riff: amplitude %d, %s(%.1f mana a second)",
                riff.amplitude(), notes, riff.manaPerSecond())));
        return 1;
    }

    private static SongAction action(String name) {
        return "swing".equalsIgnoreCase(name) ? SongAction.SWING : SongAction.CROUCH;
    }

    private static List<String> instrumentIds() {
        List<String> ids = new ArrayList<>();
        for (Instrument instrument : Instrument.values()) {
            ids.add(instrument.id());
        }
        return ids;
    }

    private interface Action {
        int apply(ServerPlayer player, PlayerMagicState state);
    }

    private static int run(CommandContext<CommandSourceStack> context, Action action) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        return action.apply(player, player.getData(MagicalAttachments.MAGIC_STATE));
    }
}
