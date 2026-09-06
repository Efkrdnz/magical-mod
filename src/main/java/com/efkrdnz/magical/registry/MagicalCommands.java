package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalConfig;
import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.entity.BlackFlameArcEntity;
import com.efkrdnz.magical.entity.BlackFlameBrandEntity;
import com.efkrdnz.magical.entity.BlackFlameProjectileEntity;
import com.efkrdnz.magical.entity.JudgementBeamEntity;
import com.efkrdnz.magical.entity.MagicCircleEffectEntity;
import com.efkrdnz.magical.entity.MagicOpponentEntity;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicCodexService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.tower.DungeonTowerService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MagicalCommands {
    private MagicalCommands() {}

    public static void register(IEventBus modEventBus) {
        // no-op; static subscriber handles the event bus registration
    }

    @EventBusSubscriber(modid = MagicalMod.MODID)
    public static final class Handlers {
        private Handlers() {}

        @SubscribeEvent
        public static void onRegisterChronosCommands(RegisterCommandsEvent event) {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("chronosfx")
                    .requires(source -> source.hasPermission(2))
                    .then(chronosEffectBranch("skycut", com.efkrdnz.magical.magic.ChronosEnvironmentService.EFFECT_SKY_CUT))
                    .then(chronosEffectBranch("shift", com.efkrdnz.magical.magic.ChronosEnvironmentService.EFFECT_THEME_SHIFT))
                    .then(chronosEffectBranch("pulse", com.efkrdnz.magical.magic.ChronosEnvironmentService.EFFECT_PULSE_STORM))
                    .then(chronosEffectBranch("vortex", com.efkrdnz.magical.magic.ChronosEnvironmentService.EFFECT_SKY_VORTEX))
                    .then(chronosEffectBranch("freeze", com.efkrdnz.magical.magic.ChronosEnvironmentService.EFFECT_TIME_FREEZE))
                    .then(chronosEffectBranch("starrain", com.efkrdnz.magical.magic.ChronosEnvironmentService.EFFECT_STAR_RAIN))
                    .then(chronosEffectBranch("clocks", com.efkrdnz.magical.magic.ChronosEnvironmentService.EFFECT_CLOCKS_ONLY))
                    .then(Commands.literal("sequence")
                            .executes(context -> {
                                com.efkrdnz.magical.magic.ChronosSequenceService.start();
                                context.getSource().sendSuccess(() -> Component.translatable("message.magical.chronos_sequence_started"), true);
                                return 1;
                            })
                            .then(Commands.literal("stop")
                                    .executes(context -> {
                                        com.efkrdnz.magical.magic.ChronosSequenceService.stop();
                                        context.getSource().sendSuccess(() -> Component.translatable("message.magical.chronos_sequence_stopped"), true);
                                        return 1;
                                    })))
                    .then(Commands.literal("color")
                            .then(chronosPaletteBranch("default", com.efkrdnz.magical.magic.ChronosEnvironmentService.PALETTE_DEFAULT))
                            .then(chronosPaletteBranch("blackwhite", com.efkrdnz.magical.magic.ChronosEnvironmentService.PALETTE_BLACK_WHITE))
                            .then(chronosPaletteBranch("whiteblack", com.efkrdnz.magical.magic.ChronosEnvironmentService.PALETTE_WHITE_BLACK))
                            .then(chronosPaletteBranch("whitegold", com.efkrdnz.magical.magic.ChronosEnvironmentService.PALETTE_WHITE_GOLD)));
            event.getDispatcher().register(root);
        }

        private static LiteralArgumentBuilder<CommandSourceStack> chronosEffectBranch(String name, int effect) {
            return Commands.literal(name)
                    .then(Commands.literal("on")
                            .executes(context -> triggerChronosEffect(effect, true, 60, 1.0F))
                            .then(Commands.argument("rampSeconds", IntegerArgumentType.integer(0, 600))
                                    .executes(context -> triggerChronosEffect(effect, true, IntegerArgumentType.getInteger(context, "rampSeconds") * 20, 1.0F))
                                    .then(Commands.argument("strength", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0.0F, 4.0F))
                                            .executes(context -> triggerChronosEffect(effect, true, IntegerArgumentType.getInteger(context, "rampSeconds") * 20,
                                                    com.mojang.brigadier.arguments.FloatArgumentType.getFloat(context, "strength"))))))
                    .then(Commands.literal("off")
                            .executes(context -> triggerChronosEffect(effect, false, 60, 1.0F))
                            .then(Commands.argument("rampSeconds", IntegerArgumentType.integer(0, 600))
                                    .executes(context -> triggerChronosEffect(effect, false, IntegerArgumentType.getInteger(context, "rampSeconds") * 20, 1.0F))));
        }

        private static int triggerChronosEffect(int effect, boolean active, int rampTicks, float strength) {
            com.efkrdnz.magical.magic.ChronosEnvironmentService.setEffect(effect, active, Math.max(1, rampTicks), strength);
            return 1;
        }

        private static LiteralArgumentBuilder<CommandSourceStack> chronosPaletteBranch(String name, int palette) {
            return Commands.literal(name)
                    .executes(context -> triggerChronosPalette(palette, 60))
                    .then(Commands.argument("rampSeconds", IntegerArgumentType.integer(0, 600))
                            .executes(context -> triggerChronosPalette(palette, IntegerArgumentType.getInteger(context, "rampSeconds") * 20)));
        }

        private static int triggerChronosPalette(int palette, int rampTicks) {
            com.efkrdnz.magical.magic.ChronosEnvironmentService.colorPalette(palette, Math.max(1, rampTicks));
            return 1;
        }

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            LiteralArgumentBuilder<CommandSourceStack> towerRoot = Commands.literal("magicaltower")
                    .then(Commands.literal("status")
                            .executes(context -> withPlayer(context.getSource(), DungeonTowerService::status)))
                    .then(Commands.literal("enter")
                            .executes(context -> withPlayer(context.getSource(), player -> DungeonTowerService.enter(player, 0)))
                            .then(Commands.argument("floor", IntegerArgumentType.integer(1, DungeonTowerService.MAIN_TOWER_FLOORS))
                                    .executes(context -> withPlayer(context.getSource(), player -> DungeonTowerService.enter(player, IntegerArgumentType.getInteger(context, "floor"))))))
                    .then(Commands.literal("wish")
                            .then(Commands.argument("choice", IntegerArgumentType.integer(1, 3))
                                    .executes(context -> withPlayer(context.getSource(), player -> DungeonTowerService.wish(player, IntegerArgumentType.getInteger(context, "choice"))))));

            event.getDispatcher().register(towerRoot);
            /*
            if (!MagicalConfig.DEBUG_COMMANDS.get()) {
                return;
            }
            */
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("magical")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("unlockstarter")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                for (ResourceLocation skillId : data.unlockStarterAwakening(player)) {
                                    MagicSkillDefinition skill = MagicContent.get(skillId);
                                    if (skill != null) {
                                        player.displayClientMessage(Component.translatable("message.magical.skill_unlocked", Component.translatable(skill.nameKey())), false);
                                    }
                                }
                                data.sync(player);
                                player.displayClientMessage(Component.translatable("message.magical.starter_unlocked"), false);
                                return 1;
                            })))
                    .then(Commands.literal("opponent")
                            .then(Commands.literal("clone")
                                    .executes(context -> spawnCloneOpponent(context.getSource(), 2))
                                    .then(Commands.argument("difficulty", IntegerArgumentType.integer(0, 5))
                                            .executes(context -> spawnCloneOpponent(context.getSource(), IntegerArgumentType.getInteger(context, "difficulty"))))))
                    .then(Commands.literal("codex")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                MagicCodexService.open(player);
                                return 1;
                            })))
                    .then(Commands.literal("circle")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                Vec3 look = player.getLookAngle().normalize();
                                Vec3 position = player.getEyePosition().add(look.scale(3.0D));
                                MagicCircleEffectEntity circle = MagicCircleEffectEntity.createStatic(
                                        player.level(),
                                        position,
                                        2.8F,
                                        0xFFE65A,
                                        100,
                                        MagicCircleEffectEntity.STYLE_DIVINE_RESTORATION,
                                        180.0F - player.getYRot(),
                                        0.0F,
                                        0.0F);
                                player.level().addFreshEntity(circle);
                                return 1;
                            })))
                    .then(Commands.literal("fpeffect")
                            .then(Commands.literal("gold")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        MagicalNetwork.playFirstPersonImpact(player, 0xFFE65A, 34, 0.38F, 8, 0.35F, 0, 4.0F);
                                        return 1;
                                    })))
                            .then(Commands.literal("red")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        MagicalNetwork.playFirstPersonImpact(player, 0xFF2A18, 28, 0.42F, 12, 0.65F, 2, -3.0F);
                                        return 1;
                                    })))
                            .then(Commands.literal("blue")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        MagicalNetwork.playFirstPersonImpact(player, 0x4AC7FF, 36, 0.34F, 6, 0.28F, 0, 2.0F);
                                        return 1;
                                    })))
                            .then(Commands.literal("shake")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        MagicalNetwork.playFirstPersonImpact(player, 0xFFFFFF, 0, 0.0F, 18, 0.9F, 0, 0.0F);
                                        return 1;
                                    })))
                            .then(Commands.literal("freeze")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        MagicalNetwork.playFirstPersonImpact(player, 0xF4F8FF, 10, 0.22F, 0, 0.0F, 5, -5.0F);
                                        return 1;
                                    }))))
                    .then(Commands.literal("unlockall")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                data.unlockAll(MagicContent.ALL_SKILLS);
                                data.sync(player);
                                player.displayClientMessage(Component.translatable("message.magical.all_unlocked"), false);
                                return 1;
                            })))
                    .then(Commands.literal("unlock")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicContent.commandIds(), builder))
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        ResourceLocation skillId = parseMagicId(StringArgumentType.getString(context, "id"));
                                        var definition = MagicContent.get(skillId);
                                        if (definition == null || MagicContent.isSubSkill(skillId)) {
                                            player.displayClientMessage(Component.translatable("message.magical.unknown_skill"), false);
                                            return 0;
                                        }
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        data.unlock(skillId);
                                        data.sync(player);
                                        player.displayClientMessage(Component.translatable("message.magical.skill_unlocked", Component.translatable(definition.nameKey())), false);
                                        return 1;
                                    }))))
                    .then(Commands.literal("remove")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicContent.commandIds(), builder))
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        ResourceLocation skillId = parseMagicId(StringArgumentType.getString(context, "id"));
                                        var definition = MagicContent.get(skillId);
                                        if (definition == null || MagicContent.isSubSkill(skillId)) {
                                            player.displayClientMessage(Component.translatable("message.magical.unknown_skill"), false);
                                            return 0;
                                        }
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        if (!data.removeSkill(skillId)) {
                                            player.displayClientMessage(Component.translatable("message.magical.skill_not_owned", Component.translatable(definition.nameKey())), false);
                                            return 0;
                                        }
                                        data.sync(player);
                                        player.displayClientMessage(Component.translatable("message.magical.skill_removed", Component.translatable(definition.nameKey())), false);
                                        return 1;
                                    }))))
                    .then(Commands.literal("setmana")
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        data.setMana(IntegerArgumentType.getInteger(context, "amount"));
                                        data.sync(player);
                                        return 1;
                                    }))))
                    .then(Commands.literal("setbarrier")
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        data.setBarrier(IntegerArgumentType.getInteger(context, "amount"));
                                        data.sync(player);
                                        return 1;
                                    }))))
                    .then(Commands.literal("setxp")
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        data.setProficiencyXp(player, IntegerArgumentType.getInteger(context, "amount"));
                                        data.sync(player);
                                        return 1;
                                    }))))
                    .then(Commands.literal("refill")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                refillPlayer(player);
                                return 1;
                            }))
                            .then(Commands.argument("targets", EntityArgument.players())
                                    .executes(context -> {
                                        int count = 0;
                                        for (ServerPlayer target : EntityArgument.getPlayers(context, "targets")) {
                                            refillPlayer(target);
                                            count++;
                                        }
                                        return count;
                                    })))
                    .then(Commands.literal("authority")
                            .then(Commands.literal("set")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(AuthorityContent.commandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                ResourceLocation authorityId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                var definition = AuthorityContent.get(authorityId);
                                                if (definition == null) {
                                                    player.displayClientMessage(Component.translatable("message.magical.unknown_authority"), false);
                                                    return 0;
                                                }
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.setAuthority(authorityId);
                                                data.sync(player);
                                                player.displayClientMessage(Component.translatable("message.magical.authority_set", Component.translatable(definition.nameKey())), false);
                                                return 1;
                                            }))))
                            .then(Commands.literal("clear")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        if (data.activeSubspaceEntityId() >= 0) {
                                            var subspace = player.serverLevel().getEntity(data.activeSubspaceEntityId());
                                            if (subspace != null) {
                                                subspace.discard();
                                            }
                                        }
                                        data.clearAuthority();
                                        data.sync(player);
                                        player.displayClientMessage(Component.translatable("message.magical.authority_cleared"), false);
                                        return 1;
                                    }))))
                    .then(Commands.literal("passive")
                            .then(Commands.literal("unlockall")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        for (var definition : MagicPassiveContent.normalPassives()) {
                                            data.unlockPassive(definition.id());
                                        }
                                        data.sync(player);
                                        player.displayClientMessage(Component.translatable("message.magical.passives_unlocked"), false);
                                        return 1;
                                    })))
                            .then(Commands.literal("unlock")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicPassiveContent.normalCommandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                ResourceLocation passiveId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                var definition = MagicPassiveContent.get(passiveId);
                                                if (definition == null || definition.curse()) {
                                                    player.displayClientMessage(Component.translatable("message.magical.unknown_passive"), false);
                                                    return 0;
                                                }
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.unlockPassive(passiveId);
                                                data.sync(player);
                                                player.displayClientMessage(Component.translatable("message.magical.passive_unlocked", Component.translatable(definition.nameKey())), false);
                                                return 1;
                                            }))))
                            .then(Commands.literal("remove")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicPassiveContent.normalCommandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                ResourceLocation passiveId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                var definition = MagicPassiveContent.get(passiveId);
                                                if (definition == null || definition.curse()) {
                                                    player.displayClientMessage(Component.translatable("message.magical.unknown_passive"), false);
                                                    return 0;
                                                }
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                if (!data.removePassive(passiveId)) {
                                                    player.displayClientMessage(Component.translatable("message.magical.passive_not_owned", Component.translatable(definition.nameKey())), false);
                                                    return 0;
                                                }
                                                data.sync(player);
                                                player.displayClientMessage(Component.translatable("message.magical.passive_removed", Component.translatable(definition.nameKey())), false);
                                                return 1;
                                            }))))
                            .then(Commands.literal("setlevel")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicPassiveContent.normalCommandIds(), builder))
                                            .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                                        ResourceLocation passiveId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                        var definition = MagicPassiveContent.get(passiveId);
                                                        if (definition == null || definition.curse()) {
                                                            player.displayClientMessage(Component.translatable("message.magical.unknown_passive"), false);
                                                            return 0;
                                                        }
                                                        int level = IntegerArgumentType.getInteger(context, "level");
                                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                        data.setPassiveLevel(passiveId, level);
                                                        data.sync(player);
                                                        player.displayClientMessage(Component.translatable("message.magical.passive_level_set", Component.translatable(definition.nameKey()), data.passiveLevel(passiveId), definition.maxLevel()), false);
                                                        return 1;
                                                    }))))))
                    .then(Commands.literal("curse")
                            .then(Commands.literal("add")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicPassiveContent.curseCommandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                ResourceLocation curseId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.addCurse(curseId);
                                                data.sync(player);
                                                return 1;
                                            }))))
                            .then(Commands.literal("dispel")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicPassiveContent.curseCommandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                ResourceLocation curseId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.dispelCurse(curseId);
                                                data.sync(player);
                                                return 1;
                                            })))))
                    .then(Commands.literal("class")
                            .then(Commands.literal("unlock")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicalClasses.rootCommandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                ResourceLocation classId = parseClassId(StringArgumentType.getString(context, "id"));
                                                MagicalClassDefinition definition = MagicalClasses.get(classId);
                                                if (definition == null || !MagicalClasses.isRoot(classId)) {
                                                    player.displayClientMessage(Component.translatable("message.magical.unknown_class"), false);
                                                    return 0;
                                                }
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.unlockClass(player, classId);
                                                data.sync(player);
                                                player.displayClientMessage(Component.translatable("message.magical.class_unlocked", Component.translatable(definition.nameKey())), false);
                                                return 1;
                                            }))))
                            .then(Commands.literal("evolve")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicalClasses.commandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                ResourceLocation classId = parseClassId(StringArgumentType.getString(context, "id"));
                                                MagicalClassDefinition definition = MagicalClasses.get(classId);
                                                if (definition == null) {
                                                    player.displayClientMessage(Component.translatable("message.magical.unknown_class"), false);
                                                    return 0;
                                                }
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.evolveClass(player, classId);
                                                data.sync(player);
                                                player.displayClientMessage(Component.translatable("message.magical.class_evolved", Component.translatable(definition.nameKey())), false);
                                                return 1;
                                            }))))
                            .then(Commands.literal("addxp")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicalClasses.commandIds(), builder))
                                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                                        ResourceLocation classId = parseClassId(StringArgumentType.getString(context, "id"));
                                                        MagicalClassDefinition definition = MagicalClasses.get(classId);
                                                        if (definition == null) {
                                                            player.displayClientMessage(Component.translatable("message.magical.unknown_class"), false);
                                                            return 0;
                                                        }
                                                        int amount = IntegerArgumentType.getInteger(context, "amount");
                                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                        data.addClassXp(classId, amount);
                                                        data.sync(player);
                                                        player.displayClientMessage(Component.translatable("message.magical.class_xp_added", amount, Component.translatable(definition.nameKey())), false);
                                                        return 1;
                                                    })))))
                            .then(Commands.literal("maxxp")
                                    .executes(context -> withPlayer(context.getSource(), player -> grantTestClassXp(player, TEST_CLASS_XP)))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                            .executes(context -> withPlayer(context.getSource(), player ->
                                                    grantTestClassXp(player, IntegerArgumentType.getInteger(context, "amount"))))))
                            .then(Commands.literal("unlockall")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                        int taken = 0;
                                        // Tier order matters: a node can only be taken once a parent is owned,
                                        // so walk the tiers outward rather than in registration order.
                                        for (int tier = 0; tier <= 3; tier++) {
                                            for (MagicalClassDefinition definition : MagicalClasses.all()) {
                                                if (definition.tier() != tier || data.hasClass(definition.id())) {
                                                    continue;
                                                }
                                                if (definition.isBase()) {
                                                    if (data.unlockClass(player, definition.id())) {
                                                        taken++;
                                                    }
                                                    continue;
                                                }
                                                // evolveClass spends from the tree's base pool, not the
                                                // node's own progress, so top up the pool it will read.
                                                data.addClassXp(definition.id(), definition.xpCost());
                                                if (data.evolveClass(player, definition.id())) {
                                                    taken++;
                                                }
                                            }
                                        }
                                        data.sync(player);
                                        player.displayClientMessage(Component.literal("Unlocked " + taken + " classes with every reward skill and passive."), false);
                                        return taken;
                                    }))));

            LiteralArgumentBuilder<CommandSourceStack> debugRoot = Commands.literal("magical-debug")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("fx")
                            .then(Commands.literal("bench")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        int staged = com.efkrdnz.magical.magic.visual.FxBench.bench(player, false);
                                        player.displayClientMessage(Component.literal("FX bench: staged " + staged + " profiles."), false);
                                        return staged;
                                    }))
                                    .then(Commands.literal("explicit")
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                int staged = com.efkrdnz.magical.magic.visual.FxBench.bench(player, true);
                                                player.displayClientMessage(Component.literal("FX bench: staged " + staged + " explicit profiles."), false);
                                                return staged;
                                            }))))
                            .then(Commands.literal("stress")
                                    .then(Commands.argument("circles", IntegerArgumentType.integer(0, 64))
                                            .then(Commands.argument("impacts", IntegerArgumentType.integer(0, 256))
                                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                                        com.efkrdnz.magical.magic.visual.FxBench.stress(player, IntegerArgumentType.getInteger(context, "circles"), IntegerArgumentType.getInteger(context, "impacts"));
                                                        player.displayClientMessage(Component.literal("FX stress spawned."), false);
                                                        return 1;
                                                    }))))))
                    .then(Commands.literal("skill")
                            .then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(MagicContent.orderedSkillIds(), builder))
                                    .executes(context -> debugCastSkill(context.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "id"), false))
                                    .then(Commands.literal("sneak")
                                            .executes(context -> debugCastSkill(context.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "id"), true)))))
                    .then(Commands.literal("scenario")
                            .then(Commands.literal("judgement")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        var stats = MagicContent.GABRIEL_JUDGEMENT.resolve(MagicSkillTuning.DEFAULT);
                                        player.serverLevel().addFreshEntity(JudgementBeamEntity.createScenario(player.serverLevel(), player, stats));
                                        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 8.0F, 0.42F);
                                        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 7.0F, 0.55F);
                                        player.displayClientMessage(Component.translatable("message.magical.debug_judgement_scenario"), false);
                                        return 1;
                                    })))
                            .then(Commands.literal("blackflames")
                                    .then(Commands.literal("cast")
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                Vec3 look = player.getLookAngle().normalize();
                                                Vec3 target = player.getEyePosition().add(0.0D, -0.18D, 0.0D);
                                                Vec3 origin = target.add(look.scale(22.0D));
                                                Vec3 direction = target.subtract(origin).normalize();
                                                var stats = MagicContent.BLACK_FLAMES_CAST.resolve(MagicSkillTuning.DEFAULT);
                                                player.serverLevel().addFreshEntity(BlackFlameProjectileEntity.scenarioProjectile(player.serverLevel(), origin, direction, stats));
                                                player.serverLevel().playSound(null, origin.x, origin.y, origin.z, SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 4.0F, 0.5F);
                                                player.displayClientMessage(Component.literal("Black Flames Cast scenario spawned."), false);
                                                return 1;
                                            })))
                                    .then(Commands.literal("imbue")
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                Vec3 look = player.getLookAngle().normalize();
                                                Vec3 target = player.getEyePosition().add(0.0D, -0.18D, 0.0D);
                                                Vec3 origin = target.add(look.scale(18.0D));
                                                Vec3 direction = target.subtract(origin).normalize();
                                                var stats = MagicContent.BLACK_FLAMES_IMBUE.resolve(MagicSkillTuning.DEFAULT);
                                                player.serverLevel().addFreshEntity(BlackFlameArcEntity.scenarioArc(player.serverLevel(), origin, direction, stats.damage(), stats.knockback()));
                                                player.serverLevel().playSound(null, origin.x, origin.y, origin.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 4.0F, 0.48F);
                                                player.displayClientMessage(Component.literal("Black Flames Imbue slash scenario spawned."), false);
                                                return 1;
                                            })))
                                    .then(Commands.literal("brand")
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                var stats = MagicContent.BLACK_FLAMES_BRAND.resolve(MagicSkillTuning.DEFAULT);
                                                player.serverLevel().addFreshEntity(BlackFlameBrandEntity.create(player.serverLevel(), null, player, stats.damage(), 2.25F + stats.size() * 0.65F, Math.max(80, stats.durationTicks())));
                                                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 4.0F, 0.72F);
                                                player.displayClientMessage(Component.literal("Black Flames Brand scenario spawned."), false);
                                                return 1;
                                            })))));

            event.getDispatcher().register(root);
            event.getDispatcher().register(debugRoot);
        }

        private static int withPlayer(CommandSourceStack source, java.util.function.ToIntFunction<ServerPlayer> action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            return action.applyAsInt(source.getPlayerOrException());
        }

        /** Enough XP to take every node of a tree many times over, for testing. */
        private static final int TEST_CLASS_XP = 1_000_000;

        /**
         * Testing shortcut: unlocks every starting class and floods all five trees with spendable XP,
         * so the whole evolution tree can be walked in the GUI without grinding kills and casts.
         * {@code addClassXp} only pays into a pool the player actually owns, hence the unlock first.
         */
        private static int grantTestClassXp(ServerPlayer player, int amount) {
            PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
            int trees = 0;
            for (MagicalClassDefinition base : MagicalClasses.startingRoots()) {
                data.unlockClass(player, base.id());
                data.addClassXp(base.id(), amount);
                trees++;
            }
            data.sync(player);
            player.displayClientMessage(Component.literal("Class XP: " + amount + " into each of " + trees + " trees, every base unlocked."), false);
            return trees;
        }

        /** Unlocks the skill if needed and casts it through the normal dispatcher. */
        private static int debugCastSkill(CommandSourceStack source, ResourceLocation skillId, boolean sneak) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            return withPlayer(source, player -> {
                MagicSkillDefinition skill = MagicContent.get(skillId);
                if (skill == null) {
                    player.displayClientMessage(Component.literal("Unknown skill " + skillId), false);
                    return 0;
                }
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                if (!state.hasUnlocked(skillId)) {
                    state.unlock(skillId);
                }
                state.setSkillCooldown(skillId, 0);
                state.setMana(state.maxMana());
                state.sync(player);
                com.efkrdnz.magical.magic.MagicCastingService.castById(player, skillId, sneak);
                return 1;
            });
        }

        private static int spawnCloneOpponent(CommandSourceStack source, int difficulty) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            return withPlayer(source, player -> {
                Vec3 look = player.getLookAngle().normalize();
                Vec3 position = player.position().add(look.scale(3.0D));
                MagicOpponentEntity clone = MagicOpponentEntity.cloneFrom(player, difficulty);
                clone.moveTo(position.x, position.y, position.z, player.getYRot() + 180.0F, 0.0F);
                clone.setTarget(player);
                player.serverLevel().addFreshEntity(clone);
                player.displayClientMessage(Component.translatable("message.magical.opponent_clone_spawned", difficulty), false);
                return 1;
            });
        }

        private static void refillPlayer(ServerPlayer player) {
            PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
            data.refillMana();
            data.setBarrier(data.maxBarrier());
            data.clearCooldowns();
            data.sync(player);
        }

        private static ResourceLocation parseClassId(String raw) {
            return parseMagicId(raw);
        }

        private static ResourceLocation parseMagicId(String raw) {
            return raw.contains(":") ? ResourceLocation.parse(raw) : ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, raw);
        }
    }
}
