package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A written passive against a real level: a Taper lights the eye block and a struck one darkens
 * it, a Halo turns an arrow closing on the wielder and charges for it, a Half Halo turns the one
 * ahead and not the one behind, a Familiar throws a needle at a hostile in sight and charges for
 * it. A fake player never receives a player tick, so the per-tick hook is driven by hand.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class VersePassiveGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);

    private VersePassiveGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** A survival wielder of the Authority of Mana on the stand, looking along +x, full of mana, knowing every verse. */
    private static ServerPlayer wielder(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, STAND, "passive-test");
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        PlayerMagicState state = state(player);
        state.setAuthority(AuthorityContent.MANA);
        state.refillMana();
        state.grimoire().learnAll(VerseContent.CATALOGUE.all().stream().map(Verse::id).toList());
        IncantationService.forget(player.getUUID());
        return player;
    }

    private static void write(ServerPlayer player, ResourceLocation... ids) {
        if (!state(player).grimoire().incantation(0).write(List.of(ids), 1, VerseContent.CATALOGUE)) {
            throw new IllegalStateException("the test incantation did not validate");
        }
    }

    private static void tick(ServerPlayer player) {
        ClassPassiveEffects.tick(player, state(player));
    }

    /** A point {@code dx} along the wielder's line at eye height, relative: where an arrow at them flies through. */
    private static Vec3 beforeTheEyes(GameTestHelper helper, ServerPlayer player, double dx) {
        return helper.relativeVec(player.getEyePosition().add(dx, 0.0D, 0.0D));
    }

    /** An arrow at {@code at} (relative) flying with {@code motion}, shot by nobody. */
    private static Arrow arrow(GameTestHelper helper, Vec3 at, Vec3 motion) {
        Arrow arrow = helper.spawn(EntityType.ARROW, at);
        arrow.setDeltaMovement(motion);
        return arrow;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "passive_1")
    public static void aTaperLightsTheEyesWhileWrittenAndDarkensWhenStruck(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, PassiveVerses.TAPER);
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        helper.runAtTickTime(1, () -> {
            player.tickCount = VersePassives.TAPER_INTERVAL;
            tick(player);
            BlockState lit = helper.getLevel().getBlockState(eye);
            helper.assertTrue(lit.is(Blocks.LIGHT), "a light at the eyes, not " + lit);
            helper.assertTrue(lit.getValue(LightBlock.LEVEL) == VersePassives.TAPER_LIGHT, "of the taper's level");
        });
        helper.runAtTickTime(3, () -> {
            state(player).grimoire().incantation(0).clear();
            player.tickCount = VersePassives.TAPER_INTERVAL * 2;
            tick(player);
            helper.assertTrue(helper.getLevel().getBlockState(eye).isAir(), "struck from the book, the light is out");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "passive_2")
    public static void aHaloTurnsAnArrowClosingOnTheWielderAndChargesForIt(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, PassiveVerses.HALO);
        int mana = state(player).mana();
        helper.runAtTickTime(1, () -> {
            Arrow arrow = arrow(helper, beforeTheEyes(helper, player, 1.8D), new Vec3(-1.5D, 0.0D, 0.0D));
            tick(player);
            helper.assertTrue(arrow.getDeltaMovement().x > 0.0D, "the arrow flies back the way it came: " + arrow.getDeltaMovement());
            helper.assertTrue(arrow.getOwner() == player, "and is the wielder's now");
            helper.assertTrue(state(player).mana() == mana - VersePassives.HALO_COST, "three mana for the turn: " + state(player).mana() + " of " + mana);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "passive_3")
    public static void aHalfHaloTurnsTheArrowAheadAndNotTheOneBehind(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, PassiveVerses.HALF_HALO);
        int mana = state(player).mana();
        helper.runAtTickTime(1, () -> {
            Arrow ahead = arrow(helper, beforeTheEyes(helper, player, 1.8D), new Vec3(-1.5D, 0.0D, 0.0D));
            Arrow behind = arrow(helper, beforeTheEyes(helper, player, -1.8D), new Vec3(1.5D, 0.0D, 0.0D));
            tick(player);
            helper.assertTrue(ahead.getDeltaMovement().x > 0.0D, "the arrow ahead is turned: " + ahead.getDeltaMovement());
            helper.assertTrue(behind.getDeltaMovement().x > 0.0D, "the arrow behind flies on: " + behind.getDeltaMovement());
            helper.assertTrue(behind.getOwner() != player, "and is nobody's still");
            helper.assertTrue(state(player).mana() == mana - VersePassives.HALF_HALO_COST, "two mana, once: " + state(player).mana() + " of " + mana);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "passive_4")
    public static void aFamiliarThrowsANeedleAtAHostileInSightAndChargesForIt(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, PassiveVerses.FAMILIAR);
        helper.spawnWithNoFreeWill(EntityType.HUSK, helper.relativeVec(GameTestPlayers.onFloor(helper, new BlockPos(4, 2, 2))));
        int mana = state(player).mana();
        helper.runAtTickTime(1, () -> {
            player.tickCount = VersePassives.FAMILIAR_INTERVAL;
            tick(player);
            List<VerseBodyEntity> bodies = VerseBodyEntity.ownedBy(helper.getLevel(), player, helper.getBounds().inflate(2.0D));
            helper.assertTrue(bodies.size() == 1, "one needle on its way, not " + bodies.size());
            helper.assertTrue(bodies.get(0).prototype() == VersePrototypes.NEEDLE, "and it is a needle");
            helper.assertTrue(state(player).mana() == mana - VersePassives.FAMILIAR_COST, "one mana for it: " + state(player).mana() + " of " + mana);
            helper.succeed();
        });
    }
}
