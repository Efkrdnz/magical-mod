package com.efkrdnz.magical.magic.causality;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The board against a real level.
 *
 * <p>Everything the engine decides is pinned on exact values by {@code WeaverTest}. What no unit
 * test can reach is the <b>splice</b>: whether a board actually sees a real hit, whether what a
 * Store takes really comes off the damage the player ends up receiving, and whether it takes it
 * <em>before the barrier</em> rather than after - which is the difference between banking a hit
 * saving the barrier from it and banking a hit costing the barrier anyway.
 *
 * <p>A fake player never receives a player tick, but the Authority clock is a server tick, so it
 * runs here on its own. That is what lets the ledger cross from one event to the next in the
 * {@link Cause#BRIM} test without the test having to drive anything by hand.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class CausalityGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);

    private CausalityGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** A survival wielder of Causality standing on the floor, so a hit on them is a real hit. */
    private static ServerPlayer wielder(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "weave-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        // A player is invulnerable until their client reports the world loaded; a fake one never does.
        player.setClientLoaded(true);
        Vec3 stand = onFloor(helper, STAND);
        player.teleportTo(stand.x, stand.y, stand.z);
        CausalityService.forget(player.getUUID());
        state(player).setAuthority(AuthorityContent.CAUSALITY);
        // A fresh state stands up with a full barrier, which quietly ate the hit in every test that
        // meant to watch one land. A test that wants one puts it back itself.
        state(player).setBarrier(0);
        return player;
    }

    private static Vec3 onFloor(GameTestHelper helper, BlockPos at) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        Vec3 floor = com.efkrdnz.magical.magic.cast.AimResolver.groundBelow(helper.getLevel(), above, 8);
        return floor != null ? floor : above;
    }

    /** A board of one rule: when I am hurt, take that share of it into the ledger. */
    private static void storeRule(PlayerMagicState state, int percent) {
        Weave weave = state.weave();
        weave.clear();
        CausalNode hurt = weave.add(CausalNode.of(0, Cause.HURT, 20, 20));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 120, 20).withParam(percent));
        weave.connect(hurt.id(), store.id());
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "causality_1")
    public static void whatAStoreTakesOffARealHitIsWhatItBanks(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        PlayerMagicState state = state(player);
        storeRule(state, 100);
        state.setMana(state.maxMana());

        helper.runAtTickTime(2, () -> {
            float health = player.getHealth();
            player.hurt(player.damageSources().magic(), 6.0F);
            helper.assertTrue(player.getHealth() >= health - 0.01F,
                    "a board that banks the whole hit should leave the wielder untouched, lost "
                            + (health - player.getHealth()));
            helper.assertTrue(state.ledger().held() > 0.0F,
                    "and the points have to have gone somewhere: the ledger holds " + state.ledger().held());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "causality_2")
    public static void aStoreRunsBeforeTheBarrierAndNotAfterIt(GameTestHelper helper) {
        // The whole reason the call is spliced into the damage ladder rather than put in a
        // subscriber of its own. Banking a hit has to save the barrier from it.
        ServerPlayer player = wielder(helper);
        PlayerMagicState state = state(player);
        storeRule(state, 100);
        state.setMana(state.maxMana());
        state.setBarrier(state.maxBarrier());

        helper.runAtTickTime(2, () -> {
            int barrier = state.barrier();
            helper.assertTrue(barrier > 0, "the test needs a barrier standing to prove it was spared");
            player.hurt(player.damageSources().magic(), 6.0F);
            helper.assertTrue(state.barrier() == barrier,
                    "the barrier ate a hit the board had already taken off: " + barrier + " to " + state.barrier());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "causality_3")
    public static void aBankedHitSetsOffWhateverWasWaitingOnTheLedger(GameTestHelper helper) {
        // The ledger is how a board talks to itself across time, and BRIM is the ear. The service
        // clock fires it a tick behind the store that fed it, so this waits rather than driving it.
        ServerPlayer player = wielder(helper);
        PlayerMagicState state = state(player);
        Weave weave = state.weave();
        weave.clear();
        CausalNode hurt = weave.add(CausalNode.of(0, Cause.HURT, 20, 20));
        CausalNode store = weave.add(CausalNode.of(0, Effect.STORE, 120, 20).withParam(100));
        weave.connect(hurt.id(), store.id());
        CausalNode brim = weave.add(CausalNode.of(0, Cause.BRIM, 20, 90).withParam(4));
        CausalNode kindle = weave.add(CausalNode.of(0, Effect.KINDLE, 120, 90)
                .withParam(60).withScope(Scope.SELF));
        weave.connect(brim.id(), kindle.id());
        state.setMana(state.maxMana());

        helper.runAtTickTime(2, () -> player.hurt(player.damageSources().magic(), 8.0F));
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(player.isOnFire(),
                    "the ledger crossed four and nothing was listening; it holds " + state.ledger().held());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "causality_4")
    public static void aSuspendedBoardDoesNothingAtAll(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        PlayerMagicState state = state(player);
        storeRule(state, 100);
        state.setMana(state.maxMana());
        state.weave().setSuspended(true);

        helper.runAtTickTime(2, () -> {
            player.hurt(player.damageSources().magic(), 6.0F);
            helper.assertTrue(state.ledger().empty(), "a quiet board banked " + state.ledger().held());
            helper.assertTrue(player.getHealth() < player.getMaxHealth(),
                    "and the hit should have landed in full");
            helper.succeed();
        });
    }

    /**
     * The gap that made this test necessary: every skill in the Authority is registered
     * {@code selfManaged} or {@code holdGated}, and both of those return out of the cast path
     * before it resolves a stat, spends a point of mana or starts a clock. So the numbers on the
     * five definitions were decoration - the Authority was entirely free - and nothing anywhere
     * said so, because a cost that is never taken looks exactly like a cost that is never needed.
     *
     * <p>Only the Board is exempt, and deliberately: it is a screen, priced at nothing and cooled
     * for nothing, so there is no number on it that could be silently ignored.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "causality_6")
    public static void everyPressInTheAuthorityIsPaidFor(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        PlayerMagicState state = state(player);
        storeRule(state, 100);
        // onFloor answers in absolute coordinates and spawnWithNoFreeWill wants them relative, so
        // the conversion runs exactly once - handing it a vec that was already relative walks the
        // husk eleven million blocks out of the template.
        LivingEntity victim = helper.spawnWithNoFreeWill(EntityType.HUSK,
                helper.relativeVec(onFloor(helper, new BlockPos(2, 2, 3))));

        helper.runAtTickTime(2, () -> {
            helper.assertTrue(victim.isAlive()
                            && victim.position().distanceTo(player.getEyePosition()) <= Anchor.REACH,
                    "the victim is not a body this wielder could mark: alive " + victim.isAlive()
                            + ", " + victim.position().distanceTo(player.getEyePosition()) + " away");
            spend(helper, player, state, MagicContent.CAUSAL_ANCHOR,
                    () -> CausalityService.anchorOn(player, victim.getId()));
            spend(helper, player, state, MagicContent.DECREE,
                    () -> CausalityService.decree(player, state));
            state.ledger().set(30.0F);
            spend(helper, player, state, MagicContent.RECOMPENSE,
                    () -> CausalityService.recompense(player, state));
            // Free, and still has to start a clock: without one the board can be shut and reopened
            // inside a single cascade.
            spend(helper, player, state, MagicContent.SUSPEND,
                    () -> CausalityService.suspend(player, state));
            helper.succeed();
        });
    }

    /** Runs one press with a full pool and checks the definition actually got its way. */
    private static void spend(GameTestHelper helper, ServerPlayer player, PlayerMagicState state,
            MagicSkillDefinition skill, java.util.function.BooleanSupplier press) {
        state.setMana(state.maxMana());
        state.setSkillCooldown(skill.id(), 0);
        int before = state.mana();
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        helper.assertTrue(before >= stats.manaCost(),
                skill.id() + " cannot be tested: the pool holds " + before + " of " + stats.manaCost());
        helper.assertTrue(press.getAsBoolean(), skill.id() + " refused a press it should have taken"
                + " (pool " + before + ", cooling " + state.isSkillOnCooldown(skill.id()) + ")");
        helper.assertTrue(before - state.mana() == stats.manaCost(),
                skill.id() + " billed " + (before - state.mana()) + " mana of " + stats.manaCost());
        helper.assertTrue(state.isSkillOnCooldown(skill.id()) == stats.cooldownTicks() > 0,
                skill.id() + " left its " + stats.cooldownTicks() + " tick clock unstarted");
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = "causality_5")
    public static void aCollapseTakesTheLedgerOutOfTheWielderOwnHide(GameTestHelper helper) {
        // Consequence held is consequence owed. The conservation rule keeping its promise at the
        // exact moment it would have been most convenient to break.
        ServerPlayer player = wielder(helper);
        PlayerMagicState state = state(player);
        // Not a Store: moving a consequence into the ledger is conserved, so it costs no paradox at
        // all and a board of nothing but Stores sits at 99 for ever. Nor an Erase, which is the
        // dearest effect there is and the one thing fraying reality refuses outright - at this rung
        // you can no longer claim a thing never happened. What is left is inventing consequence,
        // which is the other half of the same conservation rule and still very much allowed.
        Weave weave = state.weave();
        weave.clear();
        CausalNode hurt = weave.add(CausalNode.of(0, Cause.HURT, 20, 20));
        CausalNode echo = weave.add(CausalNode.of(0, Effect.ECHO, 120, 20));
        weave.connect(hurt.id(), echo.id());
        state.setMana(state.maxMana());
        state.ledger().set(12.0F);
        state.paradox().set(Paradox.MAX - 1);

        helper.runAtTickTime(2, () -> {
            float health = player.getHealth();
            CausalityService.dispatch(player, new CausalEvent(Cause.HURT, 40.0F, -1, 0));
            helper.assertTrue(state.ledger().empty(), "the ledger should have fallen due");
            helper.assertTrue(player.getHealth() < health, "and it should have fallen on the wielder");
            helper.assertTrue(state.paradox().shut(player.level().getGameTime()),
                    "with the board shut behind it");
            helper.succeed();
        });
    }
}
