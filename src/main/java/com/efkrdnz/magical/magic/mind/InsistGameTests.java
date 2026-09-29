package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class InsistGameTests {
    private static final String TEMPLATE = "unwaking_empty";

    private InsistGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "mind_insist_1")
    public static void insistingPushesTheHalfConvincedUpAndTheDoubtersDown(GameTestHelper helper) {
        ServerPlayer wielder = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), "mind-insist-test");
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.MIND);
        BlockPos anchor = BlockPos.containing(onFloor(helper, new BlockPos(2, 2, 3)));
        LiveScene scene = MindGameTests.unveil(helper, wielder.getUUID(), MindGameTests.column(), anchor);
        LivingEntity leaning = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(0, 2, 2))));
        LivingEntity doubting = helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(onFloor(helper, new BlockPos(4, 2, 2))));
        scene.belief().set(leaning.getId(), 0, 0.4F);
        scene.belief().set(doubting.getId(), 0, 0.2F);
        helper.runAtTickTime(3, () -> {
            wielder.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(anchor));
            float up = scene.belief().get(leaning.getId(), 0);
            float down = scene.belief().get(doubting.getId(), 0);
            int mana = state.mana();
            helper.assertTrue(Insist.tick(wielder, state), "insisting on a column in plain view did nothing");
            helper.assertTrue(Math.abs(scene.belief().get(leaning.getId(), 0) - (up + 0.01F)) < 1.0E-4F, "the leaning husk was not pushed up");
            helper.assertTrue(Math.abs(scene.belief().get(doubting.getId(), 0) - (down - 0.01F)) < 1.0E-4F, "the doubting husk was not pushed down");
            helper.assertTrue(state.mana() < mana, "insisting was free");

            wielder.setXRot(-90.0F);
            int before = state.mana();
            helper.assertFalse(Insist.tick(wielder, state), "insisting at the sky did something");
            helper.assertTrue(state.mana() == before, "insisting at nothing was billed");
            MindService.endAll(wielder.getUUID());
            helper.succeed();
        });
    }
}
