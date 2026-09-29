package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class LullGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private static ServerPlayer wielder(GameTestHelper helper, String name) {
        DreamService.testLevel = helper.getLevel();
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), name);
        player.getData(MagicalAttachments.MAGIC_STATE).setAuthority(AuthorityContent.MIND);
        return player;
    }

    /** A live scene of the wielder's, off to one side, and the viewer believing its first element this much. */
    private static void believes(GameTestHelper helper, ServerPlayer wielder, LivingEntity viewer, float belief) {
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(0, 2, 4)));
        LiveScene scene = MindGameTests.unveil(helper, wielder.getUUID(), MindGameTests.column(), anchor);
        scene.belief().set(viewer.getId(), 0, belief);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_1")
    public static void aSureMobFallsAsleepAndItCosts(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-mob-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 3))));
        believes(helper, wielder, husk, 0.9F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getEyePosition());
        int before = state.mana();
        helper.assertTrue(DreamService.lull(wielder, state), "a sure husk would not sleep");
        helper.assertTrue(MagicStatusService.has(husk, MagicStatus.ASLEEP), "the husk is awake");
        helper.assertTrue(state.mana() < before, "Lull was free");
        helper.assertTrue(state.isSkillOnCooldown(MagicContent.LULL.id()), "Lull has no clock");
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_2")
    public static void anUnsureMobStaysAwakeAndItCostsNothing(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-unsure-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 3))));
        believes(helper, wielder, husk, 0.6F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getEyePosition());
        int before = state.mana();
        helper.assertFalse(DreamService.lull(wielder, state), "a half-believer was lulled");
        helper.assertFalse(MagicStatusService.has(husk, MagicStatus.ASLEEP), "the husk sleeps");
        helper.assertTrue(state.mana() == before && !state.isSkillOnCooldown(MagicContent.LULL.id()), "a refusal was billed");
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_3")
    public static void aSurePlayerFallsIntoYourDreamscape(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-wielder-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        ServerPlayer dreamer = GameTestPlayers.another(helper, new BlockPos(2, 2, 3), "lull-dreamer-test");
        dreamer.getData(MagicalAttachments.MAGIC_STATE).setBarrier(0);
        DreamGameTests.withFlaw(helper.getLevel(), wielder.getUUID());
        believes(helper, wielder, dreamer, 0.9F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, dreamer.getEyePosition());
        helper.assertTrue(DreamService.lull(wielder, state), "a sure player would not sleep");
        helper.assertTrue(DreamService.dreaming(dreamer.getUUID()), "the player is not dreaming");
        Dreamscape scape = DreamService.dreamscape(helper.getLevel(), wielder.getUUID());
        helper.assertTrue(DreamRules.plotAt(dreamer.getX(), dreamer.getZ()) == scape.plot(), "the player is not in your Dreamscape");
        DreamService.wake(dreamer);
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_4")
    public static void aDreamscapeWithNoFlawTakesNobody(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-flawless-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        ServerPlayer dreamer = GameTestPlayers.another(helper, new BlockPos(2, 2, 3), "lull-safe-test");
        believes(helper, wielder, dreamer, 0.9F);
        wielder.lookAt(EntityAnchorArgument.Anchor.EYES, dreamer.getEyePosition());
        int before = state.mana();
        helper.assertFalse(DreamService.lull(wielder, state), "a Dreamscape with no Flaw took a dreamer");
        helper.assertFalse(DreamService.dreaming(dreamer.getUUID()), "the player is dreaming");
        helper.assertTrue(state.mana() == before, "a refusal was billed");
        MindService.endAll(wielder.getUUID());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "lull_5")
    public static void sneakingLullsYouIntoYourOwnDreamAndLullAgainWakesYou(GameTestHelper helper) {
        ServerPlayer wielder = wielder(helper, "lull-self-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        int before = state.mana();
        wielder.setShiftKeyDown(true);
        helper.assertTrue(DreamService.lull(wielder, state), "sneaking did not lull you");
        helper.assertTrue(DreamService.dreamingOwn(wielder.getUUID()), "you are not in your own dream");
        helper.assertTrue(state.mana() == before && !state.isSkillOnCooldown(MagicContent.LULL.id()), "your own dream was billed");
        wielder.setShiftKeyDown(false);
        helper.assertTrue(DreamService.lull(wielder, state), "Lull in your own dream did not wake you");
        helper.assertFalse(DreamService.dreaming(wielder.getUUID()), "you are still dreaming");
        helper.succeed();
    }
}
