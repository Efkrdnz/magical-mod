package com.efkrdnz.magical.magic.sword.rack;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.menu.SwordRackMenu;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The rack, in a level: what a socket holds is what its sword flies as, what it does when it lands,
 * and what the menu lets in.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class SwordRackGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 1);
    private static final BlockPos TARGET = new BlockPos(2, 2, 3);

    private static final List<ResourceLocation> EVERY_RUNG = List.of(MagicalClasses.SWORD_SUMMONER,
            MagicalClasses.SWORD_RIDER, MagicalClasses.SWORD_SAINT, MagicalClasses.SWORD_GOD);

    private static final List<ResourceLocation> BELOW_THE_APEX = List.of(MagicalClasses.SWORD_SUMMONER,
            MagicalClasses.SWORD_RIDER, MagicalClasses.SWORD_SAINT);

    private SwordRackGameTests() {}

    /**
     * A diamond sword with Fire Aspect in socket one: the blade that leaves socket one flies as it,
     * and the pig it lands in burns - the enchantment's own after-hit effect, run by the rack.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "sword_rack_1")
    public static void aFireAspectSwordRackedInSocketOneSetsItsTargetAlight(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-fire", EVERY_RUNG);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FIRE_ASPECT), 2);
        SwordArms.rack(player).setItem(0, sword);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, TARGET);
        SwordBladeEntity[] blade = new SwordBladeEntity[1];
        // Late enough for the pig to have settled on the floor, so the line it is thrown along is
        // still the line to the pig when the blade gets there.
        helper.runAtTickTime(10, () -> {
            Vec3 from = player.getEyePosition();
            Vec3 to = pig.getBoundingBox().getCenter();
            blade[0] = SwordBladeEntity.loose(helper.getLevel(), player, MagicContent.LOOSE.id(),
                    from, to.subtract(from), 0, 1, 2.0D, 0.0D, 0.5D, 20);
            helper.assertTrue(blade[0] != null, "the blade was refused");
            helper.assertTrue(ItemStack.isSameItemSameComponents(blade[0].arm(), sword),
                    "the blade off socket one flies as " + blade[0].arm() + ", not the racked sword");
            helper.assertTrue(!pig.isOnFire(), "the pig was burning before anything reached it");
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(blade[0] != null && blade[0].state() == SwordBladeEntity.STATE_SPENT,
                    "the blade never landed in the pig");
            helper.assertTrue(pig.isOnFire(), "a Fire Aspect sword landed and set nothing alight");
            helper.succeed();
        });
    }

    /**
     * An axe is a weapon and not a sword: it waits in its socket as plain steel until Weapon God,
     * and a socket the rung has not opened flies nothing at all.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_2")
    public static void anAxeFliesAsSteelUntilWeaponGod(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-axe", BELOW_THE_APEX);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            helper.assertValueEqual(SwordArms.unlocked(state), 10, "sockets a Saint has opened");
            helper.assertTrue(!SwordArms.weaponGod(state), "a Saint already holds Weapon God");
            SwordArms.rack(player).setItem(0, new ItemStack(Items.IRON_AXE));
            SwordArms.rack(player).setItem(1, new ItemStack(Items.IRON_SWORD));
            SwordArms.rack(player).setItem(11, new ItemStack(Items.GOLDEN_SWORD));
            helper.assertTrue(SwordArms.arm(player, state, 0).isEmpty(), "an axe flies before Weapon God");
            helper.assertTrue(SwordArms.arm(player, state, 1).is(Items.IRON_SWORD), "a sword does not fly");
            helper.assertTrue(SwordArms.arm(player, state, 11).isEmpty(), "a socket the rung has not opened flies");
            state.unlockPassive(MagicPassiveContent.WEAPON_GOD.id());
            helper.assertTrue(SwordArms.arm(player, state, 0).is(Items.IRON_AXE), "Weapon God does not fly the axe");
            helper.succeed();
        });
    }

    /** The apex hands Weapon God to a wielder who reached it before the passive existed. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_3")
    public static void aSwordGodIsHandedWeaponGod(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-god", EVERY_RUNG);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            helper.assertTrue(SwordArms.weaponGod(state), "a Sword God is refused any weapon but a sword");
            helper.assertValueEqual(SwordArms.unlocked(state), SwordRack.SIZE, "sockets the apex opens");
            state.togglePassive(MagicPassiveContent.WEAPON_GOD.id());
            SwordService.refreshRung(player, state);
            helper.assertTrue(!SwordArms.weaponGod(state), "the repair switched Weapon God back on under its owner");
            helper.succeed();
        });
    }

    /** Shift-clicking a sword racks it; a stick, and a sword past the rung's sockets, stay put. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_4")
    public static void theMenuRacksSwordsAndRefusesTheRest(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-menu", List.of(MagicalClasses.SWORD_SUMMONER));
        helper.runAtTickTime(1, () -> {
            SwordRack rack = SwordArms.rack(player);
            SwordRackMenu menu = new SwordRackMenu(0, player.getInventory(), rack, SwordRackMenu.data(player));
            int hotbar = SwordRack.SIZE + 27;
            player.getInventory().setItem(0, new ItemStack(Items.STICK));
            menu.quickMoveStack(player, hotbar);
            helper.assertTrue(rack.isEmpty(), "a stick was racked");
            for (int i = 0; i < 5; i++) {
                player.getInventory().setItem(i, new ItemStack(Items.IRON_SWORD));
                menu.quickMoveStack(player, hotbar + i);
            }
            for (int socket = 0; socket < 4; socket++) {
                helper.assertTrue(rack.getItem(socket).is(Items.IRON_SWORD), "socket " + socket + " stayed empty");
            }
            helper.assertTrue(rack.getItem(4).isEmpty(), "a Summoner racked a fifth sword");
            helper.assertValueEqual(player.getInventory().countItem(Items.IRON_SWORD), 1,
                    "swords left in the inventory after a Summoner's four sockets filled");
            menu.quickMoveStack(player, 2);
            helper.assertTrue(rack.getItem(2).isEmpty(), "a racked sword could not be taken back out");
            helper.assertValueEqual(player.getInventory().countItem(Items.IRON_SWORD), 2,
                    "swords in the inventory after one came back out of the rack");
            helper.succeed();
        });
    }

    /** The formation carries the rack: socket one's sword is what its first sword is drawn as, and a swap follows. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_5")
    public static void theFormationFliesWhatIsRacked(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-array", EVERY_RUNG);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            SwordArms.rack(player).setItem(0, new ItemStack(Items.DIAMOND_SWORD));
            state.swordArray().clear();
            state.swordArray().setStance(SwordStance.GUARD);
            SwordService.draw(player, state);
            SwordService.tendArrayEntity(player, state);
            SwordArrayEntity array = SwordService.arrayEntity(player);
            helper.assertTrue(array != null, "no formation stood up");
            helper.assertTrue(array.arm(0).is(Items.DIAMOND_SWORD), "the first sword is not the racked one");
            helper.assertTrue(array.arm(1).isEmpty(), "an empty socket flies as something");
            SwordArms.rack(player).setItem(0, new ItemStack(Items.NETHERITE_SWORD));
        });
        helper.runAtTickTime(4, () -> {
            SwordArrayEntity array = SwordService.arrayEntity(player);
            helper.assertTrue(array != null && array.arm(0).is(Items.NETHERITE_SWORD),
                    "a sword swapped in the rack is still drawn as the old one");
            helper.succeed();
        });
    }

    /**
     * A survival player on the floor of the cell holding the named rungs. The traps are the ones
     * {@code StanceGameTests.wielder} documents: leftovers cleared by name, a fake player made
     * vulnerable by hand, and put on the floor rather than left to fall.
     */
    private static ServerPlayer wielder(GameTestHelper helper, String name, List<ResourceLocation> rungs) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().startsWith("rack-")) {
                SwordService.forget(leftover.getUUID());
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setClientLoaded(true);
        Vec3 above = helper.absoluteVec(Vec3.atBottomCenterOf(STAND));
        Vec3 floor = com.efkrdnz.magical.magic.cast.AimResolver.groundBelow(helper.getLevel(), above, 8);
        Vec3 stand = floor != null ? floor : above;
        player.teleportTo(stand.x, stand.y, stand.z);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        for (ResourceLocation rung : rungs) {
            state.classProgressFor(rung).unlock();
        }
        SwordService.refreshRung(player, state);
        state.setMana(state.maxMana());
        return player;
    }
}
