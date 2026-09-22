package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Answering, run against a real level.
 *
 * <p>This is the only way a Sword Summoner is found in normal play - there is no command, no
 * structure, no advancement and no item - so if it does not fire, the whole class is unreachable
 * and every other test in the kit is testing something nobody can have. Nothing else covers it:
 * the rite is driven by {@code ItemTossEvent}, so a {@code /summon item} does not reach it and it
 * cannot be photographed in an unattended capture either. A game test is the only honest check.
 *
 * <p>Four conditions are cheap to get wrong and each one silently yields nothing, because the
 * rite is deliberately silent on every no: the wielder must already hold a root class and must
 * not already hold this one, it must be night and dry, every blade must see the sky, and the four
 * must come to rest spread out around the caster inside one window. The test drives
 * {@link SwordRiteService#tossed} directly rather than through the event, because a fake player
 * cannot press shift and Q - but it sets the sneak flag first, so the one guard that matters
 * (a sneak-drop is deliberate, a plain Q is an accident) is still exercised rather than bypassed.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class SwordRiteGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);

    /**
     * The four offerings, as offsets from the caster in blocks.
     *
     * <p>A square of side {@link SwordRiteService#RITE_MIN_SEPARATION}, so every pair is at
     * exactly the minimum separation or further - the rule refuses anything strictly closer, so
     * the square sits on the boundary on purpose and would fail if the comparison ever tightened.
     * Its centroid is the caster's own feet, well inside {@code RITE_CENTRE}, and every corner is
     * well inside {@code RITE_RADIUS}. It also has to fit a five-block template interior, which
     * is why it is the smallest legal figure rather than a comfortable one.
     */
    /** Dropped from just above the floor, so they are grounded within a tick or two. */
    private static final double DROP_HEIGHT = 0.25D;

    private static final double[][] SQUARE = {
        {-1.0D, -1.0D}, {1.0D, -1.0D}, {-1.0D, 1.0D}, {1.0D, 1.0D},
    };

    private SwordRiteGameTests() {}

    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = "sword_rite_1")
    public static void fourSwordsAtNightAnswerAndGrantTheClass(GameTestHelper helper) {
        ServerPlayer caster = seeker(helper);
        PlayerMagicState state = caster.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            night(helper);
            offer(helper, caster);
        });
        // The sweep runs on an interval and the ceremony takes CEREMONY_TICKS on top of it, so the
        // window has to clear both with room to spare rather than land on the first opportunity.
        // The stage is SAMPLED rather than checked at one tick, and both halves of that are
        // forced by the rite itself. It lifts each blade as its tell the moment the omen holds
        // (RITE_LIFT, HANG_TICKS), so for the first stretch the offerings are legitimately in
        // mid-air and not yet grounded; and when it answers it consumes them, so a probe at the
        // end cannot tell "never set up" from "worked, and the offerings are spent". Neither a
        // single early tick nor a single late one can distinguish a broken stage from a broken
        // rite. Asking whether the stage was EVER whole can.
        boolean[] everWhole = new boolean[1];
        String[] lastComplaint = {"never sampled"};
        for (int tick : new int[] {12, 20, 28, 36, 44, 60, 80}) {
            helper.runAtTickTime(tick, () -> {
                if (everWhole[0]) {
                    return;
                }
                String wrong = stageIsValid(helper, caster);
                if (wrong.isEmpty()) {
                    everWhole[0] = true;
                } else {
                    lastComplaint[0] = wrong;
                }
            });
        }
        helper.runAtTickTime(200, () -> {
            helper.assertTrue(everWhole[0],
                    "the test's own stage was never whole, so this says nothing about the rite: "
                            + lastComplaint[0]);
            helper.assertTrue(state.hasClass(MagicalClasses.SWORD_SUMMONER),
                    "four swords shift-dropped at night under open sky, spread out and at rest "
                            + "around the caster, must answer - this is the only way the class can "
                            + "be had, so if it does not fire the class is unreachable");
            helper.assertTrue(state.swordArray().bound() > 0,
                    "and the rite writes the four bearings as the wielder's first Array, which is "
                            + "why the Summoner rung allows exactly four stations");
            helper.succeed();
        });
    }

    /**
     * Daylight refuses. The rite is deliberately silent on every no, so the only way to know the
     * gate is a gate and not a coincidence is to run the passing case and this one side by side.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 300, batch = "sword_rite_2")
    public static void theSameFourSwordsInDaylightAnswerNothing(GameTestHelper helper) {
        ServerPlayer caster = seeker(helper);
        PlayerMagicState state = caster.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            helper.getLevel().setDayTime(6000L);
            offer(helper, caster);
        });
        helper.runAtTickTime(200, () -> {
            helper.assertFalse(state.hasClass(MagicalClasses.SWORD_SUMMONER),
                    "the rite is a night rite; daylight must answer nothing");
            helper.succeed();
        });
    }

    // ---- the stage -------------------------------------------------------------------------------

    /**
     * Every one of the rite's own conditions, restated against the live level, as a reason string
     * that is empty when the stage is good.
     *
     * <p>The rite refuses silently by design, which is right for the game and useless for a test:
     * without this, a failure here says only that nothing happened, and nothing happening is what
     * seven different mistakes all look like.
     */
    private static String stageIsValid(GameTestHelper helper, ServerPlayer caster) {
        ServerLevel level = helper.getLevel();
        StringBuilder wrong = new StringBuilder();
        if (level.isDay()) {
            wrong.append("[it is day] ");
        }
        if (level.isRaining()) {
            wrong.append("[it is raining] ");
        }
        PlayerMagicState state = caster.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasAnyRootClass()) {
            wrong.append("[caster holds no root class] ");
        }
        List<ItemEntity> lying = level.getEntitiesOfClass(ItemEntity.class, caster.getBoundingBox().inflate(6.0D));
        if (lying.size() < SwordRiteService.RITE_SWORDS) {
            wrong.append("[only ").append(lying.size()).append(" blades in the level] ");
        }
        for (ItemEntity blade : lying) {
            if (!level.canSeeSky(blade.blockPosition())) {
                wrong.append("[a blade at ").append(blade.blockPosition()).append(" cannot see the sky] ");
                break;
            }
        }
        for (ItemEntity blade : lying) {
            Vec3 motion = blade.getDeltaMovement();
            if (motion.horizontalDistanceSqr() > 1.0e-4D) {
                wrong.append("[a blade is still moving] ");
                break;
            }
        }
        for (ItemEntity blade : lying) {
            // Separate from rest on purpose: the rite asks onGround(), and a blade that is
            // motionless in mid-air passes every other check and is still refused.
            if (!blade.onGround()) {
                wrong.append("[a blade is not on the ground] ");
                break;
            }
        }
        return wrong.toString();
    }

    /** Midnight and dry. Both are gates, and a test world starts at neither. */
    private static void night(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setDayTime(18000L);
        level.setWeatherParameters(6000, 0, false, false);
    }

    /**
     * Four swords shift-dropped around the caster and settled where they land.
     *
     * <p>Gravity stays ON and the blades are dropped a little above the floor, because the rite
     * asks {@code ItemEntity.onGround()} and not merely whether the thing has stopped moving. A
     * blade held up by {@code setNoGravity} is motionless and never grounded, so it satisfies
     * every condition a reader would think to check and is still refused - which is exactly the
     * mistake this test made first, and the reason the probe above reports rest and grounding as
     * two different things.
     */
    private static void offer(GameTestHelper helper, ServerPlayer caster) {
        ServerLevel level = helper.getLevel();
        caster.setShiftKeyDown(true);
        List<ItemEntity> blades = new ArrayList<>(SwordRiteService.RITE_SWORDS);
        for (double[] offset : SQUARE) {
            Vec3 at = caster.position().add(offset[0], 0.0D, offset[1]);
            ItemEntity blade = new ItemEntity(level, at.x, at.y + DROP_HEIGHT, at.z,
                    new ItemStack(Items.IRON_SWORD));
            // Vanilla's ItemEntity constructor gives every drop a random pop so a stack of them
            // scatters; here that would move the blades off the square the test measured.
            blade.setDeltaMovement(Vec3.ZERO);
            // The caster is standing in the middle of their own square, and a survival player
            // walks over a dropped sword and takes it back within a dozen ticks - which emptied
            // the stage before the sweep ever looked at it. A real wielder steps back; a fake one
            // cannot, so the blades are pinned down instead. The rite reads position and footing
            // and never the pickup flag, so nothing it tests is bypassed by this.
            blade.setNeverPickUp();
            level.addFreshEntity(blade);
            blades.add(blade);
        }
        for (ItemEntity blade : blades) {
            SwordRiteService.tossed(caster, blade);
        }
    }

    /**
     * A survival player holding a root class and not the hidden one.
     *
     * <p>A root class is the rite's first gate - it will not hand a hidden root to somebody who
     * has never chosen a visible one - so the first starting root is taken here. Fake players
     * outlive their tests and stand in the neighbouring structures, so each test runs in its own
     * batch and clears the ones before it.
     */
    private static ServerPlayer seeker(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                SwordRiteService.forget(leftover.getUUID());
                SwordService.forget(leftover.getUUID());
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "rite-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setClientLoaded(true);
        Vec3 stand = onFloor(helper, STAND);
        player.teleportTo(stand.x, stand.y, stand.z);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.classProgressFor(MagicalClasses.startingRoots().get(0).id()).unlock();
        return player;
    }

    /** The floor under a template cell, absolute: a thing put there starts where it would land. */
    private static Vec3 onFloor(GameTestHelper helper, BlockPos at) {
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        Vec3 floor = com.efkrdnz.magical.magic.cast.AimResolver.groundBelow(helper.getLevel(), above, 8);
        return floor != null ? floor : above;
    }
}
