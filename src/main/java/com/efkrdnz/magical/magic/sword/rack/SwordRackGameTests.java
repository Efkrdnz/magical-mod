package com.efkrdnz.magical.magic.sword.rack;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.menu.SwordRackMenu;
import com.efkrdnz.magical.magic.sword.SwordMath;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The rack, in a level: only what is racked flies, it lands as hard as its weapon, it does what its
 * weapon does, and the menu lets in only what may fly.
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
        ServerPlayer player = wielder(helper, "rack-fire-test", EVERY_RUNG);
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
     * An axe is a weapon and not a sword: it does not fly until Weapon God, and nor does anything in
     * a socket the rung has not opened. What is left is the formation - here one sword.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_2")
    public static void anAxeWaitsForWeaponGod(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-axe-test", BELOW_THE_APEX);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            helper.assertValueEqual(SwordArms.unlocked(state), 10, "sockets a Saint has opened");
            helper.assertTrue(!SwordArms.weaponGod(state), "a Saint already holds Weapon God");
            SwordArms.rack(player).setItem(0, new ItemStack(Items.IRON_AXE));
            SwordArms.rack(player).setItem(1, new ItemStack(Items.IRON_SWORD));
            SwordArms.rack(player).setItem(11, new ItemStack(Items.GOLDEN_SWORD));
            SwordService.refreshRack(player, state);
            helper.assertValueEqual(state.swordArray().racked(), 0b10,
                    "sockets that fly - the sword alone, not the axe nor a socket past the rung");
            helper.assertValueEqual(SwordService.swords(state), 1, "swords a rack of one sword fields");
            helper.assertTrue(SwordArms.arm(player, state, 0).is(Items.IRON_SWORD),
                    "the first sword is not the one weapon the rack may fly");
            helper.assertTrue(SwordArms.arm(player, state, 1).isEmpty(), "a second sword flies out of a rack of one");
            state.unlockPassive(MagicPassiveContent.WEAPON_GOD.id());
            SwordService.refreshRack(player, state);
            helper.assertValueEqual(state.swordArray().racked(), 0b11, "sockets that fly under Weapon God");
            helper.assertTrue(SwordArms.arm(player, state, 0).is(Items.IRON_AXE), "Weapon God does not fly the axe");
            helper.assertTrue(SwordArms.arm(player, state, 1).is(Items.IRON_SWORD), "the sword is not the second");
            helper.succeed();
        });
    }

    /** The apex hands Weapon God to a wielder who reached it before the passive existed. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_3")
    public static void aSwordGodIsHandedWeaponGod(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-god-test", EVERY_RUNG);
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
        ServerPlayer player = wielder(helper, "rack-menu-test", List.of(MagicalClasses.SWORD_SUMMONER));
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
            helper.assertValueEqual(menu.fielded(), 4, "swords the rack says fly with four racked in Guard");
            helper.assertValueEqual(player.getInventory().countItem(Items.IRON_SWORD), 1,
                    "swords left in the inventory after a Summoner's four sockets filled");
            menu.quickMoveStack(player, 2);
            helper.assertTrue(rack.getItem(2).isEmpty(), "a racked sword could not be taken back out");
            helper.assertValueEqual(player.getInventory().countItem(Items.IRON_SWORD), 2,
                    "swords in the inventory after one came back out of the rack");
            helper.assertValueEqual(menu.fielded(), 3, "swords the rack says fly once one came back out");
            helper.assertTrue(menu.flies(3) && !menu.flies(2), "the gold rims are not the racked sockets");
            helper.succeed();
        });
    }

    /** The formation carries the rack: socket one's sword is what its first sword is drawn as, and a swap follows. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_5")
    public static void theFormationFliesWhatIsRacked(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-array-test", EVERY_RUNG);
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
            helper.assertValueEqual(array.presentCount(), 1, "swords standing round a rack of one sword");
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
     * Two swords racked with a gap between them stand as a formation of two, in order, and a rack
     * emptied while they stand puts the steel away - and an empty rack draws nothing at all.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sword_rack_6")
    public static void onlyTheRackedSwordsStand(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-count-test", EVERY_RUNG);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        helper.runAtTickTime(1, () -> {
            SwordRack rack = SwordArms.rack(player);
            state.swordArray().clear();
            state.swordArray().setStance(SwordStance.RAIN);
            helper.assertTrue(!SwordService.draw(player, state), "an empty rack drew swords");
            helper.assertTrue(!state.swordArray().drawn(), "an empty rack left the steel out");
            rack.setItem(3, new ItemStack(Items.IRON_SWORD));
            rack.setItem(9, new ItemStack(Items.GOLDEN_SWORD));
            helper.assertTrue(SwordService.draw(player, state), "two racked swords would not draw");
            SwordService.tendArrayEntity(player, state);
            helper.assertValueEqual(SwordService.swords(state), 2, "swords Rain fields with two racked");
            SwordArrayEntity array = SwordService.arrayEntity(player);
            helper.assertTrue(array != null, "no formation stood up");
            helper.assertValueEqual(array.presentCount(), 2, "swords the formation draws");
            helper.assertTrue(array.arm(0).is(Items.IRON_SWORD), "the first sword is not socket four's");
            helper.assertTrue(array.arm(1).is(Items.GOLDEN_SWORD), "the second sword is not socket ten's");
            helper.assertTrue(array.arm(2).isEmpty(), "a third sword stands out of two");
            rack.removeItemNoUpdate(3);
            rack.removeItemNoUpdate(9);
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(!state.swordArray().drawn(), "the rack was emptied and the steel stayed out");
            helper.assertTrue(SwordService.arrayEntity(player) == null, "a formation stands with nothing racked");
            helper.succeed();
        });
    }

    /**
     * The complaint that started it: a sword of 230 attack flew and landed like any other. One
     * blade of it lands 230 - the kit's unit of one sword replaced by the weapon's own hit.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "sword_rack_7")
    public static void aRackedSwordLandsItsOwnAttack(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "rack-damage-test", List.of(MagicalClasses.SWORD_SUMMONER));
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "test_attack"), 229.0D,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build());
        SwordArms.rack(player).setItem(0, sword);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, TARGET);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        pig.setHealth(1000.0F);
        SwordBladeEntity[] blade = new SwordBladeEntity[1];
        helper.runAtTickTime(10, () -> {
            Vec3 from = player.getEyePosition();
            Vec3 to = pig.getBoundingBox().getCenter();
            blade[0] = SwordBladeEntity.loose(helper.getLevel(), player, MagicContent.LOOSE.id(),
                    from, to.subtract(from), 0, 1, SwordMath.bladeDamage(), 0.0D, 0.5D, 20);
            helper.assertTrue(blade[0] != null, "the blade was refused");
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(blade[0] != null && blade[0].state() == SwordBladeEntity.STATE_SPENT,
                    "the blade never landed in the pig");
            float taken = 1000.0F - pig.getHealth();
            helper.assertTrue(Math.abs(taken - 230.0F) < 0.5F, "a sword of 230 attack landed " + taken);
            helper.succeed();
        });
    }

    /**
     * Fills the first {@code count} sockets the rung has opened with iron swords and counts them:
     * only what is racked flies, so a test that wants a formation has to rack one first.
     */
    public static void rackSwords(ServerPlayer player, int count) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        SwordRack rack = SwordArms.rack(player);
        int sockets = Math.min(count, SwordArms.unlocked(state));
        for (int socket = 0; socket < sockets; socket++) {
            rack.setItem(socket, new ItemStack(Items.IRON_SWORD));
        }
        SwordService.refreshRack(player, state);
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
