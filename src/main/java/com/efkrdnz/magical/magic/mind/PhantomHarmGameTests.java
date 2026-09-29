package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.mind.FigmentEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.passive.PassiveHooks;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static com.efkrdnz.magical.gametest.GameTestPlayers.onFloor;

@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class PhantomHarmGameTests {
    private static final String TEMPLATE = "unwaking_empty";
    private static final String LAVA = "minecraft:lava";
    private static final String ZOMBIE = "minecraft:zombie";

    private PhantomHarmGameTests() {}

    /**
     * A fake player with no barrier. A fresh state stands up with a full one, and it would soak the
     * phantom burn before it reached the health bar, which is what these tests watch.
     */
    private static ServerPlayer bare(GameTestHelper helper, BlockPos at, String name) {
        ServerPlayer player = GameTestPlayers.survival(helper, at, name);
        player.getData(MagicalAttachments.MAGIC_STATE).setBarrier(0);
        return player;
    }

    /** One imagined lava block where the player stands. */
    private static LiveScene lavaUnder(GameTestHelper helper, UUID owner, ServerPlayer player) {
        Lexicon lexicon = MindGameTests.knowing("block:" + LAVA);
        Reverie reverie = new Reverie();
        reverie.addBlock(new Offset(0, 0, 0), LAVA, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie, player.blockPosition(), 0, lexicon);
        helper.assertTrue(scene != null, "the lava was refused");
        return scene;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_harm_1")
    public static void aBelieverWhoWalksIntoImaginedLavaIsBurnedAndBelievesMore(GameTestHelper helper) {
        ServerPlayer player = bare(helper, new BlockPos(2, 2, 2), "mind-burned-test");
        UUID owner = UUID.randomUUID();
        LiveScene scene = lavaUnder(helper, owner, player);
        scene.belief().set(player.getId(), 0, 0.6F);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "imagined lava burned nothing");
            helper.assertFalse(scene.belief().shattered(player.getId(), 0), "the burn was taken as evidence against the lava");
            float now = scene.belief().get(player.getId(), 0);
            helper.assertTrue(now > 0.65F && now < 0.75F, "belief " + now + " did not rise by a tenth");
            helper.assertFalse(scene.manifested(0), "lava was placed for real");
            // No wielder online and nothing that bit: plain magic, whose death message names no killer.
            helper.assertTrue(player.getLastDamageSource() != null
                    && player.getLastDamageSource().is(DamageTypes.MAGIC), "an unattributed burn was not plain magic");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_harm_2")
    public static void aDoubterWhoWalksIntoImaginedLavaSeesThroughIt(GameTestHelper helper) {
        ServerPlayer player = bare(helper, new BlockPos(2, 2, 2), "mind-doubter-test");
        UUID owner = UUID.randomUUID();
        LiveScene scene = lavaUnder(helper, owner, player);
        scene.belief().set(player.getId(), 0, 0.3F);
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "lava burned a mind that doubted it");
            helper.assertTrue(scene.belief().shattered(player.getId(), 0), "a doubter walked through lava and still half-believes it");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    /**
     * With the wielder online the burn is credited to them, and it must still not shove its victim:
     * vanilla's indirect_magic is not in {@code #minecraft:no_knockback}, so a burn on that type
     * pushed the believer away from wherever the hidden wielder stood.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_harm_4")
    public static void imaginedLavaBurnsForAnOnlineWielderWithoutAShove(GameTestHelper helper) {
        ServerPlayer player = bare(helper, new BlockPos(3, 2, 3), "mind-shoved-test");
        ServerPlayer wielder = GameTestPlayers.another(helper, new BlockPos(1, 2, 1), "mind-wielder-test");
        UUID owner = wielder.getUUID();
        LiveScene scene = lavaUnder(helper, owner, player);
        scene.belief().set(player.getId(), 0, 0.6F);
        net.minecraft.world.phys.Vec3 before = player.getDeltaMovement();
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(player.getHealth() < player.getMaxHealth(), "imagined lava burned nothing");
            net.minecraft.world.phys.Vec3 after = player.getDeltaMovement();
            double shove = after.subtract(before).horizontalDistance();
            helper.assertTrue(shove < 1.0E-6, "the burn shoved its victim by " + shove);
            helper.assertTrue(player.getLastDamageSource() != null
                    && player.getLastDamageSource().is(MindDamageTypes.PHANTOM_HARM), "a credited burn was not phantom harm");
            helper.assertTrue(PassiveHooks.isSpellDamage(player.getLastDamageSource()), "phantom harm is not counted as magic");
            MindService.endAll(owner);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "mind_harm_3")
    public static void aFigmentThatStrikesABelieverHurtsAndConvinces(GameTestHelper helper) {
        ServerPlayer player = bare(helper, new BlockPos(1, 2, 2), "mind-bitten-test");
        UUID owner = UUID.randomUUID();
        Lexicon lexicon = MindGameTests.knowing("creature:" + ZOMBIE);
        Reverie reverie = new Reverie();
        reverie.addFigment(new Offset(0, 0, 0), ZOMBIE, lexicon);
        LiveScene scene = MindService.unveilAt(helper.getLevel(), owner, reverie,
                BlockPos.containing(onFloor(helper, new BlockPos(3, 2, 2))), 0, lexicon);
        helper.assertTrue(scene != null, "the zombie was refused");
        scene.belief().set(player.getId(), 0, 0.6F);
        helper.runAtTickTime(2, () -> {
            FigmentEntity zombie = (FigmentEntity) helper.getLevel().getEntity(scene.figmentEntity(0));
            MindService.figmentStrikes(zombie, player);
            // A zombie bites for 3; believed at 0.6 that is 1.8.
            float lost = player.getMaxHealth() - player.getHealth();
            helper.assertTrue(lost > 1.7F && lost < 1.9F, "an imagined zombie bit for " + lost);
            helper.assertTrue(player.getLastDamageSource() != null
                    && player.getLastDamageSource().is(MindDamageTypes.PHANTOM_HARM), "a figment's bite was not phantom harm");
            float now = scene.belief().get(player.getId(), 0);
            helper.assertTrue(now > 0.65F, "belief " + now + " did not rise after the bite");
            MindService.endAll(owner);
            helper.succeed();
        });
    }
}
