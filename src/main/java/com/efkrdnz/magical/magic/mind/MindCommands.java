package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;

import java.util.List;
import java.util.function.ToIntFunction;

/** {@code /magical mind ...}: forces every state of the Authority for captures and tests. */
public final class MindCommands {
    private static final int FULL_STUDY = 20;
    private static final int DEFAULT_STUDY = 5;

    private MindCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("mind")
                .then(Commands.literal("lexicon")
                        .then(Commands.literal("all").executes(c -> run(c, MindCommands::learnAll)))
                        .then(learn("block", Impression.Kind.BLOCK))
                        .then(learn("creature", Impression.Kind.CREATURE)))
                .then(Commands.literal("preset")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MindState.SLOTS))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            MindPresets.NAMES.forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(c -> run(c, player -> preset(player,
                                                IntegerArgumentType.getInteger(c, "slot") - 1,
                                                StringArgumentType.getString(c, "name")))))))
                .then(Commands.literal("slot")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MindState.SLOTS))
                                .executes(c -> run(c, player -> {
                                    PlayerMagicState state = state(player);
                                    state.mind().setActiveSlot(IntegerArgumentType.getInteger(c, "slot") - 1);
                                    state.sync(player);
                                    return 1;
                                }))))
                .then(Commands.literal("unveil").executes(c -> run(c, player -> {
                    PlayerMagicState state = state(player);
                    state.setSkillCooldown(MagicContent.UNVEIL.id(), 0);
                    return MindService.unveil(player, state) ? 1 : 0;
                })))
                .then(Commands.literal("show")
                        .executes(c -> run(c, player -> show(player, state(player).mind().activeSlot())))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1, MindState.SLOTS))
                                .executes(c -> run(c, player -> show(player, IntegerArgumentType.getInteger(c, "slot") - 1)))))
                .then(Commands.literal("belief").executes(c -> run(c, MindCommands::belief)))
                .then(Commands.literal("playbill").executes(c -> run(c, player -> {
                    com.efkrdnz.magical.network.MagicalNetwork.sendOpenPlaybill(player);
                    return 1;
                })))
                .then(Commands.literal("end").executes(c -> run(c, player -> {
                    MindService.endAll(player.getUUID());
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> learn(String literal, Impression.Kind kind) {
        return Commands.literal(literal)
                .then(Commands.argument("id", ResourceLocationArgument.id())
                        .executes(c -> run(c, player -> learnOne(player, kind, ResourceLocationArgument.getId(c, "id").toString(), DEFAULT_STUDY)))
                        .then(Commands.argument("gazes", IntegerArgumentType.integer(1, 100))
                                .executes(c -> run(c, player -> learnOne(player, kind,
                                        ResourceLocationArgument.getId(c, "id").toString(), IntegerArgumentType.getInteger(c, "gazes"))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, ToIntFunction<ServerPlayer> action) throws CommandSyntaxException {
        return action.applyAsInt(context.getSource().getPlayerOrException());
    }

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    private static int learnAll(ServerPlayer player) {
        PlayerMagicState state = state(player);
        BuiltInRegistries.BLOCK.keySet().forEach(id -> state.mind().lexicon().learn(Impression.block(id.toString()).key(), FULL_STUDY));
        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(entry -> {
            if (entry.getValue().getCategory() != MobCategory.MISC) {
                state.mind().lexicon().learn(Impression.creature(entry.getKey().location().toString()).key(), FULL_STUDY);
            }
        });
        state.sync(player);
        return state.mind().lexicon().size();
    }

    private static int learnOne(ServerPlayer player, Impression.Kind kind, String id, int gazes) {
        PlayerMagicState state = state(player);
        Impression impression = new Impression(kind, id);
        if (Impression.parse(impression.key()) == null) {
            return 0;
        }
        state.mind().lexicon().learn(impression.key(), gazes);
        state.sync(player);
        return 1;
    }

    private static int preset(ServerPlayer player, int slot, String name) {
        Reverie preset = MindPresets.named(name);
        if (preset == null) {
            return 0;
        }
        PlayerMagicState state = state(player);
        MindPresets.impressions(preset).forEach(key -> state.mind().lexicon().learn(key, DEFAULT_STUDY));
        state.mind().reverie(slot).copyFrom(preset);
        state.mind().setActiveSlot(slot);
        state.sync(player);
        return 1;
    }

    private static int show(ServerPlayer player, int slot) {
        Reverie reverie = state(player).mind().reverie(slot);
        player.sendSystemMessage(Component.literal("Reverie " + (slot + 1) + " '" + reverie.name() + "' facing "
                + reverie.facing() + ": " + reverie.blocks().size() + " blocks in " + reverie.clusters().size()
                + " clusters, " + reverie.figments().size() + " figments, " + UnveilCost.of(reverie) + " mana"));
        for (Figment figment : reverie.figments()) {
            player.sendSystemMessage(Component.literal("  " + figment.creatureId() + " " + figment.script() + " " + figment.senses()));
        }
        return 1;
    }

    private static int belief(ServerPlayer player) {
        List<LiveScene> scenes = MindService.scenesOf(player.getUUID());
        for (LiveScene scene : scenes) {
            player.sendSystemMessage(Component.literal("Scene " + scene.id() + " at " + scene.anchor().toShortString()));
            for (LiveScene.Element element : scene.elements()) {
                player.sendSystemMessage(Component.literal("  " + element.index() + " " + element.kind()
                        + " p=" + String.format("%.2f", scene.plausibility(element.index()))));
            }
            for (Belief.Row row : scene.belief().rows()) {
                var entity = player.serverLevel().getEntity(row.viewer());
                String who = entity == null ? "#" + row.viewer() : entity.getName().getString();
                player.sendSystemMessage(Component.literal("    " + who + " -> " + row.element() + ": "
                        + (row.shattered() ? "shattered" : String.format("%.2f", row.belief()))));
            }
        }
        return scenes.size();
    }
}
