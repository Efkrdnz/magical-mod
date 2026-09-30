package com.efkrdnz.magical.magic.primordial;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.fx.ThrownSpellEntity;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.skill.primordial.CalderaSkill;
import com.efkrdnz.magical.magic.skill.primordial.CycloneSkill;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The catastrophes against a real level. The arithmetic is pinned by the unit tests beside the
 * pure core; what only a level shows is the gate in a storm's eye, the ground a chasm or a plate
 * takes coming back, and a star that will not fall on a roof. Every test lays its own stone floor
 * on the bottom row of the template, so the ground it carves is known.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class PrimordialGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(0, 2, 2);
    private static final BlockPos TARGET = new BlockPos(3, 1, 2);

    private PrimordialGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
    }

    /** A survival mage standing on the floor at the west edge, looking east at the target. */
    private static ServerPlayer mage(GameTestHelper helper, ResourceLocation skill) {
        var server = helper.getLevel().getServer();
        for (ServerPlayer leftover : List.copyOf(helper.getLevel().players())) {
            if (leftover.getGameProfile().getName().endsWith("-test")) {
                server.getPlayerList().remove(leftover);
            }
        }
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "primordial-test"), false);
        var player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setClientLoaded(true);
        Vec3 stand = helper.absoluteVec(Vec3.atBottomCenterOf(STAND));
        player.teleportTo(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(Vec3.atCenterOf(TARGET).add(0.0D, 0.5D, 0.0D)));
        state(player).unlockAll(Set.of(skill));
        state(player).refillMana();
        return player;
    }

    private static List<SpellEffectEntity> effects(GameTestHelper helper, ResourceLocation skill) {
        AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24.0D);
        return helper.getLevel().getEntitiesOfClass(SpellEffectEntity.class, around, e -> skill.equals(e.skillId()) && !(e instanceof ThrownSpellEntity));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "primordial_1")
    public static void aCycloneMeetsItsOwnersFireAndThrowsBackAStrangersArrow(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = mage(helper, MagicContent.CYCLONE.id());
        SpellEffectEntity[] storm = new SpellEffectEntity[1];
        ThrownSpellEntity[] bomb = new ThrownSpellEntity[1];
        Arrow[] arrow = new Arrow[1];
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.CYCLONE.id(), false));
        helper.runAtTickTime(3, () -> {
            List<SpellEffectEntity> storms = effects(helper, MagicContent.CYCLONE.id());
            helper.assertTrue(storms.size() == 1, "one storm must touch down, got " + storms.size());
            storm[0] = storms.get(0);
            helper.assertTrue(CycloneSkill.read(storm[0].syncedData()).calm(), "a fresh storm is dust");
            // the owner's own lava bomb, thrown sideways through the eye
            Vec3 from = storm[0].position().add(0.0D, 1.5D, -1.0D);
            SpellEffectEntity template = SpellEffectEntity.create(helper.getLevel(), MagicContent.CALDERA,
                    MagicContent.CALDERA.resolve(MagicSkillTuning.DEFAULT), player, from, 60, 0.5F, new Vec3(0.0D, 0.0D, 1.0D), 7);
            ThrownSpellEntity thrown = ThrownSpellEntity.create(helper.getLevel(), template, from, new Vec3(0.0D, 0.0D, 0.25D), 0.0F, 1.0F, 0, 60);
            thrown.setMode((byte) (CalderaSkill.MODE_BOMB << 1));
            helper.getLevel().addFreshEntity(thrown);
            bomb[0] = thrown;
        });
        helper.runAtTickTime(7, () -> {
            var fed = CycloneSkill.read(storm[0].syncedData());
            helper.assertTrue(fed.element() == StormElement.EMBER && fed.stacks() == 1,
                    "the owner's caldera bomb must make the storm Ember I, got " + fed.element() + " " + fed.stacks());
            helper.assertTrue(bomb[0].isAlive(), "and the owner's own catastrophe is met, not eaten");
            // above the bomb's line, or the arrow strikes the bomb before it ever reaches the eye
            Vec3 from = storm[0].position().add(0.2D, 2.8D, -2.5D);
            Arrow shot = new Arrow(helper.getLevel(), from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
            shot.setNoGravity(true);
            shot.setDeltaMovement(0.0D, 0.0D, 0.6D);
            helper.getLevel().addFreshEntity(shot);
            arrow[0] = shot;
        });
        helper.runAtTickTime(14, () -> {
            // thrown back the way it came: it is behind its own start again (in the barrier wall of
            // the template, stuck, which is where a turned arrow in a five-block room ends up)
            Vec3 at = arrow[0].position().subtract(storm[0].position());
            helper.assertTrue(!arrow[0].isRemoved() && (arrow[0].getDeltaMovement().z < 0.0D || at.z < -2.0D),
                    "an arrow nobody the storm knows loosed is thrown back out, not destroyed: velocity "
                    + arrow[0].getDeltaMovement() + " at " + at);
            helper.assertTrue(player.equals(arrow[0].getOwner()), "and it is the storm owner's after");
            helper.assertTrue(CycloneSkill.read(storm[0].syncedData()).element() == StormElement.EMBER,
                    "and a stranger's shot feeds the storm nothing");
            storm[0].finish();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240, batch = "primordial_2")
    public static void aFaultSwallowsAndCrushesThenGivesTheGroundBack(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = mage(helper, MagicContent.FAULT_LINE.id());
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 2));
        float health = husk.getHealth();
        BlockPos crack = new BlockPos(3, 1, 2);
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.FAULT_LINE.id(), false));
        helper.runAtTickTime(8, () -> {
            helper.assertBlockPresent(Blocks.AIR, crack);
            helper.assertTrue(husk.getY() < helper.absoluteVec(Vec3.atBottomCenterOf(crack)).y + 0.5D,
                    "what stood on the crack falls into it, husk at " + husk.getY());
        });
        helper.runAfterDelay(10, () -> helper.succeedWhen(() -> {
            helper.assertTrue(effects(helper, MagicContent.FAULT_LINE.id()).isEmpty(), "the fault is still open");
            helper.assertBlockPresent(Blocks.STONE, crack);
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 2));
            helper.assertTrue(!husk.isAlive() || husk.getHealth() < health, "what the chasm held is crushed as it shuts");
            helper.assertTrue(!husk.isAlive() || husk.getY() >= helper.absoluteVec(Vec3.atBottomCenterOf(crack)).y + 0.9D,
                    "and thrown up to the surface rather than left in the rock: " + husk.getY());
        }));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "primordial_3", skyAccess = true)
    public static void aStarRefusesGroundThatCannotSeeTheSkyAndBillsNothing(GameTestHelper helper) {
        floor(helper);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
            }
        }
        ServerPlayer player = mage(helper, MagicContent.SKYFALL.id());
        int[] mana = new int[1];
        helper.runAtTickTime(1, () -> {
            mana[0] = state(player).mana();
            MagicCastingService.castById(player, MagicContent.SKYFALL.id(), false);
        });
        helper.runAtTickTime(4, () -> {
            helper.assertTrue(effects(helper, MagicContent.SKYFALL.id()).isEmpty(), "no star falls on a roof");
            helper.assertTrue(state(player).mana() == mana[0], "and nothing is billed: " + mana[0] + " -> " + state(player).mana());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "primordial_4")
    public static void aPlateTornUpLeavesAHoleThatFillsBack(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = mage(helper, MagicContent.UPHEAVAL.id());
        helper.runAtTickTime(1, () -> MagicCastingService.castById(player, MagicContent.UPHEAVAL.id(), false));
        helper.runAtTickTime(4, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET);
            List<SpellEffectEntity> slabs = effects(helper, MagicContent.UPHEAVAL.id());
            helper.assertTrue(slabs.size() == 1, "one slab rises");
            int carried = slabs.get(0).syncedData().getIntArray(MassCodec.STATES).length;
            helper.assertTrue(carried > 10, "and it carries the ground it was torn from, " + carried + " blocks");
            PrimordialScars.closeAll(helper.getLevel());
            helper.assertBlockPresent(Blocks.STONE, TARGET);
            slabs.get(0).finish();
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20, batch = "primordial_5")
    public static void borrowedGroundDropsNothingCarriesWhatHangsOnItAndBuriesNobody(GameTestHelper helper) {
        floor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos cell = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos torch = cell.above();
        BlockPos underSand = helper.absolutePos(new BlockPos(1, 1, 2));
        level.setBlockAndUpdate(torch, Blocks.TORCH.defaultBlockState());
        level.setBlockAndUpdate(underSand.above(), Blocks.SAND.defaultBlockState());
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);

        helper.assertTrue(PrimordialService.carve(level, edit, underSand, Blocks.AIR.defaultBlockState()) == null,
                "ground under sand is never cut: the sand would fall and never come back");
        helper.assertTrue(level.getBlockState(underSand).is(Blocks.STONE), "and it is left as it stood");

        helper.assertTrue(PrimordialService.carve(level, edit, cell, Blocks.SMOOTH_BASALT.defaultBlockState()) != null,
                "plain stone with a torch on it is cut");
        helper.assertTrue(level.getBlockState(torch).isAir(), "and the torch goes with it into the edit");
        helper.assertTrue(PrimordialService.isMatter(level, cell), "what stands in the cut is conjured matter");

        level.destroyBlock(cell, true);
        AABB round = new AABB(cell).inflate(3.0D);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, round).isEmpty(),
                "neither the torch nor the matter dropped anything");

        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 1, 2));
        husk.setPos(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D);
        PrimordialService.giveBack(level, edit, s -> s.isAir() || s.canBeReplaced() || s.is(Blocks.SMOOTH_BASALT));

        helper.assertTrue(level.getBlockState(cell).is(Blocks.STONE), "the stone comes back");
        helper.assertTrue(level.getBlockState(torch).is(Blocks.TORCH), "and the torch on it");
        helper.assertTrue(!PrimordialService.isMatter(level, cell), "and the cell is plain ground again");
        helper.assertTrue(husk.getY() >= cell.getY() + 1.0D, "and the husk stood in the hole is on top, not in the rock: y "
                + (husk.getY() - cell.getY()));
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, round).isEmpty(), "and still nothing dropped");
        helper.succeed();
    }
}
