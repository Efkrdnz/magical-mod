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
import com.efkrdnz.magical.entity.SpaceSubspaceEntity;
import com.efkrdnz.magical.entity.ascendant.AscendantTier;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicCodexService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicFusionService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.SpaceAuthorityService;
import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleOperation;
import com.efkrdnz.magical.magic.SpaceTargetGroup;
import com.efkrdnz.magical.magic.incantation.IncantationService;
import com.efkrdnz.magical.magic.incantation.ReciteCaps;
import com.efkrdnz.magical.magic.incantation.RecitePlan;
import com.efkrdnz.magical.magic.incantation.Verse;
import com.efkrdnz.magical.magic.incantation.VerseContent;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.OpenSpellCreatorPayload;
import com.efkrdnz.magical.network.SpaceRuleAppliedPayload;
import com.efkrdnz.magical.tower.DungeonTowerService;
import java.util.Optional;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
                    .requires(source -> source.hasPermission(2) && !com.efkrdnz.magical.boss.unwaking.UnwakingEncounterService.get(source.getServer()).reserved())
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
                    .then(Commands.literal("blood")
                            .then(Commands.literal("harvest")
                                    .executes(context -> withPlayer(context.getSource(), player ->
                                            spillBlood(player, com.efkrdnz.magical.magic.passive.BloodHarvestRules.VESSEL_PER_DROP)))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, PlayerMagicState.MAX_BLOOD_VESSEL))
                                            .executes(context -> withPlayer(context.getSource(), player ->
                                                    spillBlood(player, IntegerArgumentType.getInteger(context, "amount")))))))
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
                                    .then(Commands.argument("difficulty", IntegerArgumentType.integer(0, AscendantTier.MAX_TIER))
                                            .executes(context -> spawnCloneOpponent(context.getSource(), IntegerArgumentType.getInteger(context, "difficulty"))))))
                    // The Grimoire screen, for captures and for a wielder who has not put the skill on a key.
                    .then(Commands.literal("grimoire")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                MagicalNetwork.sendOpenGrimoire(player);
                                return 1;
                            })))
                    .then(Commands.literal("codex")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                MagicCodexService.open(player);
                                return 1;
                            })))
                    // A fresh magic state, so a capture or a test run starts from nothing without deleting the save.
                    .then(Commands.literal("reset")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                player.setData(MagicalAttachments.MAGIC_STATE, new PlayerMagicState());
                                IncantationService.forget(player.getUUID());
                                player.getData(MagicalAttachments.MAGIC_STATE).sync(player);
                                return 1;
                            })))
                    // The creator screen, optionally straight onto a tab or with a pair preloaded (for captures).
                    .then(Commands.literal("creator")
                            .executes(context -> withPlayer(context.getSource(), player -> openCreator(player, OpenSpellCreatorPayload.TAB_CREATE, null, null)))
                            .then(Commands.literal("formulas")
                                    .executes(context -> withPlayer(context.getSource(), player -> openCreator(player, OpenSpellCreatorPayload.TAB_FORMULAS, null, null))))
                            .then(Commands.literal("create")
                                    .executes(context -> withPlayer(context.getSource(), player -> openCreator(player, OpenSpellCreatorPayload.TAB_CREATE, null, null)))
                                    .then(Commands.argument("first", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicContent.commandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> openCreator(player, OpenSpellCreatorPayload.TAB_CREATE,
                                                    parseMagicId(StringArgumentType.getString(context, "first")), null)))
                                            .then(Commands.argument("second", StringArgumentType.word())
                                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicContent.commandIds(), builder))
                                                    .executes(context -> withPlayer(context.getSource(), player -> openCreator(player, OpenSpellCreatorPayload.TAB_CREATE,
                                                            parseMagicId(StringArgumentType.getString(context, "first")),
                                                            parseMagicId(StringArgumentType.getString(context, "second")))))))))
                    // A real domain and a real law, without the authority, the mana or the wheel.
                    // `hud rule` only pops the flash; this drives the rule loop that moves things.
                    .then(Commands.literal("subspace")
                            .then(Commands.literal("create")
                                    .executes(context -> withPlayer(context.getSource(), player -> raiseSubspace(player, 8.0F, false)))
                                    .then(Commands.argument("radius", FloatArgumentType.floatArg(5.0F, SpaceSubspaceEntity.MAX_RADIUS))
                                            .executes(context -> withPlayer(context.getSource(), player ->
                                                    raiseSubspace(player, FloatArgumentType.getFloat(context, "radius"), false)))
                                            .then(Commands.argument("follow", BoolArgumentType.bool())
                                                    .executes(context -> withPlayer(context.getSource(), player -> raiseSubspace(player,
                                                            FloatArgumentType.getFloat(context, "radius"),
                                                            BoolArgumentType.getBool(context, "follow")))))))
                            .then(Commands.literal("clear")
                                    .executes(context -> withPlayer(context.getSource(), player ->
                                            SpaceAuthorityService.closeAllDomains(player, player.getData(MagicalAttachments.MAGIC_STATE), true))))
                            // Defaults to everything, including the caster - the target the wheel starts away from.
                            .then(Commands.literal("rule")
                                    .then(Commands.argument("category", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(lowerNames(SpaceRuleCategory.values()), builder))
                                            .then(Commands.argument("operation", StringArgumentType.word())
                                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(lowerNames(SpaceRuleOperation.values()), builder))
                                                    .executes(context -> withPlayer(context.getSource(), player -> subspaceRule(context, player, SpaceTargetGroup.EVERYTHING.name())))
                                                    .then(Commands.argument("target", StringArgumentType.word())
                                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(lowerNames(SpaceTargetGroup.values()), builder))
                                                            .executes(context -> withPlayer(context.getSource(), player ->
                                                                    subspaceRule(context, player, StringArgumentType.getString(context, "target")))))))))
                    // The pact screen, without walking a Vessel up first. Captures only: the cast
                    // path refuses a Vessel under a hundred, and the seal still charges it.
                    .then(Commands.literal("sacrifice")
                            .executes(context -> withPlayer(context.getSource(), player -> {
                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                com.efkrdnz.magical.magic.blood.BloodSacrificeService.open(player, data);
                                return 1;
                            })))
                    // Fuse from the console: the same service call the Create button reaches.
                    .then(Commands.literal("create")
                            .then(Commands.argument("first", StringArgumentType.word())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicContent.commandIds(), builder))
                                    .then(Commands.argument("second", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(MagicContent.commandIds(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                return MagicFusionService.create(player, data,
                                                        parseMagicId(StringArgumentType.getString(context, "first")),
                                                        parseMagicId(StringArgumentType.getString(context, "second"))) ? 1 : 0;
                                            })))))
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
                    // Forces the states the HUD draws, so every ring, satellite and chip can be seen without
                    // playing the hours that earn them. Each ends in a sync so the client sees it at once.
                    .then(Commands.literal("hud")
                            // The rule flash without a subspace: only the packet a landed rule sends, no state touched.
                            .then(Commands.literal("rule")
                                    .then(Commands.argument("category", StringArgumentType.word())
                                            .then(Commands.argument("operation", StringArgumentType.word())
                                                    .executes(context -> withPlayer(context.getSource(), player -> ruleFlash(context, player, SpaceTargetGroup.EVERYTHING_EXCEPT_USER.name())))
                                                    .then(Commands.argument("target", StringArgumentType.word())
                                                            .executes(context -> withPlayer(context.getSource(), player -> ruleFlash(context, player, StringArgumentType.getString(context, "target"))))))))
                            .then(Commands.literal("sin")
                                    .then(Commands.argument("sin", StringArgumentType.word())
                                            .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                        int amount = IntegerArgumentType.getInteger(context, "amount");
                                                        switch (StringArgumentType.getString(context, "sin")) {
                                                            case "pride" -> data.addPride(amount);
                                                            case "wrath" -> data.addWrath(amount);
                                                            case "greed" -> data.addGreedHoard(amount);
                                                            case "sloth" -> data.addSlothStillness(amount);
                                                            default -> {
                                                                context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("pride | wrath | greed | sloth"));
                                                                return 0;
                                                            }
                                                        }
                                                        data.sync(player);
                                                        return 1;
                                                    })))))
                            .then(Commands.literal("charge")
                                    .then(Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                            .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                        data.addManaCharge(IntegerArgumentType.getInteger(context, "level"), IntegerArgumentType.getInteger(context, "ticks"));
                                                        data.sync(player);
                                                        return 1;
                                                    })))))
                            .then(Commands.literal("gluttony")
                                    .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.setGluttonyCooldown(IntegerArgumentType.getInteger(context, "ticks"));
                                                data.sync(player);
                                                return 1;
                                            }))))
                            .then(Commands.literal("corruption")
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0, PlayerMagicState.MAX_CORRUPTION))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.setCorruption(IntegerArgumentType.getInteger(context, "amount"));
                                                data.sync(player);
                                                return 1;
                                            }))))
                            .then(Commands.literal("notice")
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0, PlayerMagicState.MAX_NOTICE))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.setNotice(IntegerArgumentType.getInteger(context, "amount"));
                                                data.sync(player);
                                                return 1;
                                            }))))
                            .then(Commands.literal("vessel")
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0, PlayerMagicState.MAX_BLOOD_VESSEL))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.addBloodVessel(IntegerArgumentType.getInteger(context, "amount") - data.bloodVessel());
                                                data.sync(player);
                                                return 1;
                                            }))))
                            .then(Commands.literal("status")
                                    .then(Commands.argument("status", StringArgumentType.word())
                                            .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                                    .executes(context -> withPlayer(context.getSource(), player -> applyStatus(context, player, 0)))
                                                    .then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 15))
                                                            .executes(context -> withPlayer(context.getSource(), player -> applyStatus(context, player, IntegerArgumentType.getInteger(context, "amplifier"))))))))
                            .then(Commands.literal("anchor")
                                    .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                data.setAnchorSigil(player.level().dimension().location().toString(), player.blockPosition(), IntegerArgumentType.getInteger(context, "ticks"));
                                                data.sync(player);
                                                return 1;
                                            }))))
                            .then(Commands.literal("equip")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(0, MagicContent.LOADOUT_SIZE - 1))
                                            .then(Commands.argument("id", StringArgumentType.word())
                                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                                        ResourceLocation skillId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                        if (MagicContent.get(skillId) == null) {
                                                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("unknown skill " + skillId));
                                                            return 0;
                                                        }
                                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                        data.unlock(skillId);
                                                        if (!data.setLoadoutSlot(data.activeLoadoutIndex(), IntegerArgumentType.getInteger(context, "slot"), skillId)) {
                                                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("could not equip " + skillId));
                                                            return 0;
                                                        }
                                                        data.sync(player);
                                                        return 1;
                                                    })))))
                            .then(Commands.literal("race")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(context -> withPlayer(context.getSource(), player -> {
                                                PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                if (!data.chooseRace(parseMagicId(StringArgumentType.getString(context, "id")))) {
                                                    context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("unknown race, or one is already chosen"));
                                                    return 0;
                                                }
                                                data.sync(player);
                                                return 1;
                                            }))))
                            .then(Commands.literal("cooldown")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                                        ResourceLocation skillId = parseMagicId(StringArgumentType.getString(context, "id"));
                                                        if (MagicContent.get(skillId) == null) {
                                                            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("unknown skill " + skillId));
                                                            return 0;
                                                        }
                                                        PlayerMagicState data = player.getData(MagicalAttachments.MAGIC_STATE);
                                                        data.setSkillCooldown(skillId, IntegerArgumentType.getInteger(context, "ticks"));
                                                        data.sync(player);
                                                        return 1;
                                                    }))))))
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
                    // The Grimoire by hand, until the editor screen: write a slot, learn verses,
                    // read a slot back, and see what a press would cast without pressing. Slots
                    // are one-based here, as the skills are named (Incantation I is slot 1).
                    .then(Commands.literal("incantation")
                            .then(Commands.literal("set")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                            .then(Commands.argument("breath", IntegerArgumentType.integer(ReciteCaps.MIN_BREATH, ReciteCaps.MAX_BREATH))
                                                    .then(Commands.argument("verses", StringArgumentType.greedyString())
                                                            .executes(context -> withPlayer(context.getSource(), player -> incantationSet(player,
                                                                    IntegerArgumentType.getInteger(context, "slot"),
                                                                    IntegerArgumentType.getInteger(context, "breath"),
                                                                    StringArgumentType.getString(context, "verses"))))))))
                            .then(Commands.literal("know")
                                    .then(Commands.argument("verse", StringArgumentType.word())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(versePaths(), builder))
                                            .executes(context -> withPlayer(context.getSource(), player -> incantationKnow(player,
                                                    StringArgumentType.getString(context, "verse"))))))
                            .then(Commands.literal("show")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                            .executes(context -> withPlayer(context.getSource(), player -> incantationShow(player,
                                                    IntegerArgumentType.getInteger(context, "slot"))))))
                            .then(Commands.literal("preview")
                                    .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                            .executes(context -> withPlayer(context.getSource(), player -> incantationPreview(player,
                                                    IntegerArgumentType.getInteger(context, "slot")))))))
                    // Capture tooling, and nothing else: a screenshot of an avalanche needs a
                    // loaded field, and building one honestly means pressing Burden thirty times.
                    // The skill is the only real way to make a Pile; this only seeds one.
                    .then(Commands.literal("pile")
                            .then(Commands.literal("load")
                                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 16))
                                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 16))
                                                    .executes(context -> withPlayer(context.getSource(), player -> seedPile(player,
                                                            IntegerArgumentType.getInteger(context, "radius"),
                                                            IntegerArgumentType.getInteger(context, "amount")))))))
                            .then(Commands.literal("clear")
                                    .executes(context -> withPlayer(context.getSource(), player -> {
                                        com.efkrdnz.magical.magic.chaos.PileService.forget(player.getUUID());
                                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("Pile forgotten."), false);
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
                    .then(Commands.literal("forge")
                            .then(Commands.literal("bench")
                                    .then(Commands.argument("element", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(com.efkrdnz.magical.forge.ForgeElements.all().stream().map(com.efkrdnz.magical.forge.ElementDefinition::id).toList(), builder))
                                            .executes(context -> debugForgeBench(context.getSource(), context, com.efkrdnz.magical.forge.WeaponClass.SWORD, com.efkrdnz.magical.forge.chain.ForgeGrade.MYTHIC, false))
                                            .then(Commands.argument("class", StringArgumentType.word())
                                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.efkrdnz.magical.forge.WeaponClass.values()).map(value -> value.name().toLowerCase(java.util.Locale.ROOT)).toList(), builder))
                                                    .then(Commands.argument("grade", StringArgumentType.word())
                                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.efkrdnz.magical.forge.chain.ForgeGrade.values()).map(value -> value.name().toLowerCase(java.util.Locale.ROOT)).toList(), builder))
                                                            .executes(context -> debugForgeBench(context.getSource(), context, weaponClassArg(context), gradeArg(context), false))
                                                            .then(Commands.literal("heavy")
                                                                    .executes(context -> debugForgeBench(context.getSource(), context, weaponClassArg(context), gradeArg(context), true)))))))
                            .then(Commands.literal("strike")
                                    .then(Commands.argument("form", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                            .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(com.efkrdnz.magical.forge.ForgeForms.all().stream().map(com.efkrdnz.magical.forge.FormDefinition::id).toList(), builder))
                                            .then(Commands.argument("element", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(com.efkrdnz.magical.forge.ForgeElements.all().stream().map(com.efkrdnz.magical.forge.ElementDefinition::id).toList(), builder))
                                                    .executes(context -> debugForgeStrike(context.getSource(), context, com.efkrdnz.magical.forge.WeaponClass.SWORD, com.efkrdnz.magical.forge.chain.ForgeGrade.MYTHIC, false))
                                                    .then(Commands.argument("class", StringArgumentType.word())
                                                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.efkrdnz.magical.forge.WeaponClass.values()).map(value -> value.name().toLowerCase(java.util.Locale.ROOT)).toList(), builder))
                                                            .then(Commands.argument("grade", StringArgumentType.word())
                                                                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(com.efkrdnz.magical.forge.chain.ForgeGrade.values()).map(value -> value.name().toLowerCase(java.util.Locale.ROOT)).toList(), builder))
                                                                    .executes(context -> debugForgeStrike(context.getSource(), context, weaponClassArg(context), gradeArg(context), false))
                                                                    .then(Commands.literal("heavy")
                                                                            .executes(context -> debugForgeStrike(context.getSource(), context, weaponClassArg(context), gradeArg(context), true)))))))))
                    .then(Commands.literal("skill")
                            .then(Commands.argument("id", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(MagicContent.orderedSkillIds(), builder))
                                    .executes(context -> debugCastSkill(context.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "id"), false))
                                    .then(Commands.literal("sneak")
                                            .executes(context -> debugCastSkill(context.getSource(), net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "id"), true)))))
                    // A press on Incantation <slot> with the unlock, the cooldown and the pool taken care of.
                    .then(Commands.literal("recite")
                            .then(Commands.argument("slot", IntegerArgumentType.integer(1, IncantationService.SLOTS))
                                    .executes(context -> debugCastSkill(context.getSource(),
                                            IncantationService.skillFor(IntegerArgumentType.getInteger(context, "slot") - 1).id(), false))))
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

        private static com.efkrdnz.magical.forge.WeaponClass weaponClassArg(
                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
            String name = StringArgumentType.getString(context, "class").toUpperCase(java.util.Locale.ROOT);
            for (com.efkrdnz.magical.forge.WeaponClass value : com.efkrdnz.magical.forge.WeaponClass.values()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return com.efkrdnz.magical.forge.WeaponClass.SWORD;
        }

        private static com.efkrdnz.magical.forge.chain.ForgeGrade gradeArg(
                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
            String name = StringArgumentType.getString(context, "grade").toUpperCase(java.util.Locale.ROOT);
            for (com.efkrdnz.magical.forge.chain.ForgeGrade value : com.efkrdnz.magical.forge.chain.ForgeGrade.values()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return com.efkrdnz.magical.forge.chain.ForgeGrade.MYTHIC;
        }

        /** Every form of one element in a row, so the whole set can be judged in a single frame. */
        private static int debugForgeBench(CommandSourceStack source,
                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                com.efkrdnz.magical.forge.WeaponClass archetype, com.efkrdnz.magical.forge.chain.ForgeGrade grade, boolean heavy)
                throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            ResourceLocation element = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "element");
            return withPlayer(source, player -> {
                int spawned = com.efkrdnz.magical.forge.ForgeStrikeBench.bench(player, element, archetype, grade, heavy);
                player.displayClientMessage(Component.literal(spawned == 0
                        ? "Forge bench: no such element " + element
                        : "Forge bench: " + spawned + " forms in " + element.getPath()), false);
                return spawned;
            });
        }

        private static int debugForgeStrike(CommandSourceStack source,
                com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                com.efkrdnz.magical.forge.WeaponClass archetype, com.efkrdnz.magical.forge.chain.ForgeGrade grade, boolean heavy)
                throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            ResourceLocation form = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "form");
            ResourceLocation element = net.minecraft.commands.arguments.ResourceLocationArgument.getId(context, "element");
            return withPlayer(source, player -> {
                boolean spawned = com.efkrdnz.magical.forge.ForgeStrikeBench.one(player, form, element, archetype, grade, heavy);
                if (!spawned) {
                    player.displayClientMessage(Component.literal("Forge strike: no such form or element."), false);
                }
                return spawned ? 1 : 0;
            });
        }

    /** "all" first, then every verse path, for the know command's suggestions. */
    private static java.util.List<String> versePaths() {
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add("all");
        for (Verse verse : VerseContent.CATALOGUE.all()) {
            names.add(verse.path());
        }
        return names;
    }

    private static int incantationSet(ServerPlayer player, int slot, int breath, String verses) {
        java.util.List<String> raw = java.util.Arrays.asList(verses.trim().split("\\s+"));
        java.util.List<ResourceLocation> ids = IncantationService.parseIds(raw);
        if (ids == null) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_unknown_verse", verses), false);
            return 0;
        }
        return IncantationService.setIncantation(player, slot - 1, breath, ids) ? 1 : 0;
    }

    private static int incantationKnow(ServerPlayer player, String verse) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if ("all".equalsIgnoreCase(verse)) {
            state.grimoire().learnAll(VerseContent.CATALOGUE.all().stream().map(Verse::id).toList());
        } else {
            java.util.List<ResourceLocation> ids = IncantationService.parseIds(java.util.List.of(verse));
            if (ids == null || !VerseContent.CATALOGUE.contains(ids.get(0))) {
                player.displayClientMessage(Component.translatable("message.magical.incantation_unknown_verse", verse), false);
                return 0;
            }
            state.grimoire().learn(ids.get(0));
        }
        state.sync(player);
        int known = state.grimoire().known().size();
        player.displayClientMessage(Component.translatable("message.magical.incantation_known", known), false);
        return known;
    }

    private static int incantationShow(ServerPlayer player, int slot) {
        var incantation = player.getData(MagicalAttachments.MAGIC_STATE).grimoire().incantation(slot - 1);
        if (incantation.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_empty", slot), false);
            return 0;
        }
        net.minecraft.network.chat.MutableComponent line = Component.literal("Incantation " + slot + " (breath " + incantation.breath() + "): ");
        boolean first = true;
        for (var entry : incantation.entries()) {
            if (!first) {
                line.append(Component.literal(", "));
            }
            first = false;
            line.append(Component.translatable("verse.magical." + entry.id().getPath()));
            if (entry.usesRemaining() >= 0) {
                line.append(Component.literal(" (" + entry.usesRemaining() + ")"));
            }
        }
        player.displayClientMessage(line, false);
        return incantation.size();
    }

    private static int incantationPreview(ServerPlayer player, int slot) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (state.grimoire().incantation(slot - 1).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.magical.incantation_empty", slot), false);
            return 0;
        }
        RecitePlan plan = IncantationService.preview(state, slot - 1);
        player.displayClientMessage(Component.translatable("message.magical.incantation_preview",
                slot, plan.bodies().size(), plan.manaSpent(), plan.cooldownTicks()), false);
        return plan.bodies().size();
    }

        /**
     * Presses {@code amount} of stress into every solid block in a cube around the caster.
     *
     * <p>Debug only. It goes through {@code Pile.add} rather than round it, so slack ground still
     * refuses, the site ceiling still holds, and anything already over capacity is armed exactly as
     * Burden would have armed it - a seeded field behaves like a built one.
     */
    private static int seedPile(net.minecraft.server.level.ServerPlayer player, int radius, int amount) {
        com.efkrdnz.magical.magic.chaos.Pile pile = com.efkrdnz.magical.magic.chaos.PileService.pileFor(player);
        long now = player.serverLevel().getGameTime();
        net.minecraft.core.BlockPos centre = player.blockPosition();
        int loaded = 0;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius))) {
            if (player.serverLevel().getBlockState(pos).isAir()) {
                continue;
            }
            // betweenClosed hands out one reused mutable cursor, and a Pile site is a map key.
            if (pile.add(com.efkrdnz.magical.magic.chaos.PileSite.of(pos.immutable()), amount, now)) {
                loaded++;
            }
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                loaded + " sites burdened by " + amount), false);
        return loaded;
    }

    private static int withPlayer(CommandSourceStack source, java.util.function.ToIntFunction<ServerPlayer> action) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            return action.applyAsInt(source.getPlayerOrException());
        }

        /**
         * Testing shortcut: spills a pool of harvestable blood six blocks ahead, as a kill would, so
         * the harvest can be watched and screenshotted without staging one.
         */
        private static int spillBlood(ServerPlayer player, int amount) {
            Vec3 look = player.getLookAngle();
            Vec3 ahead = player.position().add(look.x * 6.0D, 0.05D, look.z * 6.0D);
            com.efkrdnz.magical.entity.BloodHarvestEntity.spawn(player.serverLevel(), player, ahead, amount);
            return 1;
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

        /**
         * Spawns whichever opponent the number names.
         *
         * <p>0-5 is still a clone of the caller, unchanged. 6 and up is an Ascendant, which copies
         * nobody - the same fight for every player who runs the command.
         */
        private static int spawnCloneOpponent(CommandSourceStack source, int difficulty) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            return withPlayer(source, player -> {
                Vec3 look = player.getLookAngle().normalize();
                Vec3 position = player.position().add(look.scale(3.0D));
                boolean ascendant = AscendantTier.isAscendant(difficulty);
                MagicOpponentEntity opponent = ascendant
                        ? MagicOpponentEntity.ascendant(player.level(), difficulty)
                        : MagicOpponentEntity.cloneFrom(player, difficulty);
                opponent.moveTo(position.x, position.y, position.z, player.getYRot() + 180.0F, 0.0F);
                opponent.setTarget(player);
                player.serverLevel().addFreshEntity(opponent);
                player.displayClientMessage(Component.translatable(
                        ascendant ? "message.magical.ascendant_spawned" : "message.magical.opponent_clone_spawned",
                        difficulty), false);
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

        private static int openCreator(ServerPlayer player, int tab, ResourceLocation first, ResourceLocation second) {
            MagicalNetwork.sendOpenSpellCreator(player, new OpenSpellCreatorPayload(tab, Optional.ofNullable(first), Optional.ofNullable(second)));
            return 1;
        }

        private static ResourceLocation parseClassId(String raw) {
            return parseMagicId(raw);
        }

        private static ResourceLocation parseMagicId(String raw) {
            return raw.contains(":") ? ResourceLocation.parse(raw) : ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, raw);
        }
    }
    private static int applyStatus(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, net.minecraft.server.level.ServerPlayer player, int amplifier) {
        String name = StringArgumentType.getString(context, "status").toUpperCase(java.util.Locale.ROOT);
        com.efkrdnz.magical.magic.status.MagicStatus status;
        try {
            status = com.efkrdnz.magical.magic.status.MagicStatus.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("unknown status " + name));
            return 0;
        }
        com.efkrdnz.magical.magic.status.MagicStatusService.apply(player, status, IntegerArgumentType.getInteger(context, "ticks"), amplifier, 0.0F, MagicContent.STARTER_SKILL, player);
        return 1;
    }

    /** One law read off three words. */
    private record ParsedRule(SpaceRuleCategory category, SpaceRuleOperation operation, SpaceTargetGroup target) {
    }

    /**
     * Names matched against the enums case-insensitively, the operation checked against its
     * category. Returns null once the failure naming the valid words has been sent.
     */
    private static ParsedRule parseRule(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String targetName) {
        SpaceRuleCategory category = byName(SpaceRuleCategory.values(), StringArgumentType.getString(context, "category"));
        if (category == null) {
            context.getSource().sendFailure(Component.literal("category: " + names(SpaceRuleCategory.values())));
            return null;
        }
        SpaceRuleOperation operation = byName(SpaceRuleOperation.values(), StringArgumentType.getString(context, "operation"));
        if (operation == null || operation.category() != category) {
            StringBuilder valid = new StringBuilder();
            for (SpaceRuleOperation candidate : SpaceRuleOperation.values()) {
                if (candidate.category() == category) {
                    valid.append(valid.isEmpty() ? "" : ", ").append(candidate.name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            context.getSource().sendFailure(Component.literal(category.name().toLowerCase(java.util.Locale.ROOT) + " operation: " + valid));
            return null;
        }
        SpaceTargetGroup target = byName(SpaceTargetGroup.values(), targetName);
        if (target == null) {
            context.getSource().sendFailure(Component.literal("target: " + names(SpaceTargetGroup.values())));
            return null;
        }
        return new ParsedRule(category, operation, target);
    }

    /** {@code hud rule}: only the packet a landed rule sends, no subspace and no state touched. */
    private static int ruleFlash(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer player, String targetName) {
        ParsedRule rule = parseRule(context, targetName);
        if (rule == null) {
            return 0;
        }
        MagicalNetwork.sendSpaceRuleApplied(player, new SpaceRuleAppliedPayload(rule.category().ordinal(), rule.operation().ordinal(), rule.target().ordinal()));
        return 1;
    }

    /** {@code subspace create}: a domain standing at once, with none of the skill's gates. */
    private static int raiseSubspace(ServerPlayer player, float radius, boolean followOwner) {
        SpaceAuthorityService.raiseDebugSubspace(player, radius, followOwner);
        return 1;
    }

    /** {@code subspace rule}: the same three words as {@code hud rule}, onto the standing domain. */
    private static int subspaceRule(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, ServerPlayer player, String targetName) {
        ParsedRule rule = parseRule(context, targetName);
        if (rule == null) {
            return 0;
        }
        if (!SpaceAuthorityService.applyDebugRule(player, rule.category(), rule.operation(), rule.target())) {
            context.getSource().sendFailure(Component.literal("no subspace standing: /magical subspace create"));
            return 0;
        }
        return 1;
    }

    private static java.util.List<String> lowerNames(Enum<?>[] values) {
        return java.util.Arrays.stream(values).map(value -> value.name().toLowerCase(java.util.Locale.ROOT)).toList();
    }

    private static <E extends Enum<E>> E byName(E[] values, String name) {
        for (E value : values) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    private static String names(Enum<?>[] values) {
        StringBuilder out = new StringBuilder();
        for (Enum<?> value : values) {
            out.append(out.isEmpty() ? "" : ", ").append(value.name().toLowerCase(java.util.Locale.ROOT));
        }
        return out.toString();
    }

}
