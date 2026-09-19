package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A press on Incantation I through the real cast path: the registry's gates, the service's bill,
 * the spawner's bodies. What a unit test holds about the Reciter is held in the core; this is
 * the binding - that a press spends mana and uses, that an empty slot refuses, that a session
 * survives a press and not a forget.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class IncantationGameTests {

    private static final String TEMPLATE = "unwaking_empty";
    private static final BlockPos STAND = new BlockPos(2, 2, 2);
    private static final int SLOT = 0;
    private static final ResourceLocation SKILL = MagicContent.INCANTATION_1.id();

    private IncantationGameTests() {}

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** A survival wielder of the Authority of Mana, on the floor, full of mana, knowing every verse. */
    private static ServerPlayer wielder(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.survival(helper, STAND, "mana-test");
        // Look along +x, into the two and a half blocks of air before the far wall.
        player.setYRot(-90.0F);
        player.setXRot(0.0F);
        PlayerMagicState state = state(player);
        state.setAuthority(AuthorityContent.MANA);
        state.refillMana();
        state.grimoire().learnAll(VerseContent.CATALOGUE.all().stream().map(Verse::id).toList());
        IncantationService.forget(player.getUUID());
        return player;
    }

    private static void write(ServerPlayer player, int breath, ResourceLocation... ids) {
        boolean written = state(player).grimoire().incantation(SLOT).write(List.of(ids), breath, VerseContent.CATALOGUE);
        if (!written) {
            throw new IllegalStateException("the test incantation did not validate");
        }
        IncantationService.resetSession(player.getUUID(), SLOT);
    }

    private static int usesLeft(ServerPlayer player, int index) {
        return state(player).grimoire().incantation(SLOT).entries().get(index).usesRemaining();
    }

    private static List<VerseBodyEntity> bodies(GameTestHelper helper, ServerPlayer player) {
        return VerseBodyEntity.ownedBy(helper.getLevel(), player, helper.getBounds().inflate(2.0D));
    }

    private static void press(ServerPlayer player) {
        MagicCastingService.castById(player, SKILL, false);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_1")
    public static void aPressRecitesTheWrittenIncantation(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, 1, MulticastVerses.COUPLET, ProjectileVerses.NEEDLE, ProjectileVerses.NEEDLE);
        int max = state(player).maxMana();
        helper.runAtTickTime(1, () -> press(player));
        // One flight tick in: by the second the needles (1.6 blocks a tick) have crossed the 2.1 blocks of air to the far wall and ended there.
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(bodies(helper, player).size() == 2, "a couplet of needles is two bodies: " + bodies(helper, player).size());
            // A needle is 4 mana and a couplet nothing, at the cost scale a fresh state resolves to.
            helper.assertTrue(state(player).mana() == max - 8, "two needles billed: " + state(player).mana() + " of " + max);
            helper.assertTrue(state(player).skillCooldown(SKILL) > 0, "the beat became the cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_2")
    public static void anEmptyIncantationRefusesAndCostsNothing(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        int max = state(player).maxMana();
        helper.runAtTickTime(1, () -> press(player));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(bodies(helper, player).isEmpty(), "nothing written, nothing flies");
            helper.assertTrue(state(player).mana() == max, "and nothing billed");
            helper.assertTrue(state(player).skillCooldown(SKILL) == 0, "and no cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_3")
    public static void aWrittenIncantationMustBeKnown(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        state(player).grimoire().clear();
        helper.runAtTickTime(1, () -> {
            boolean unknown = IncantationService.setIncantation(player, SLOT, 1, List.of(ProjectileVerses.NEEDLE));
            helper.assertFalse(unknown, "a verse never learned cannot be written");
            state(player).grimoire().learnAll(List.of(ProjectileVerses.NEEDLE));
            boolean known = IncantationService.setIncantation(player, SLOT, 1, List.of(ProjectileVerses.NEEDLE));
            helper.assertTrue(known, "and can be once it is");
            helper.assertTrue(state(player).grimoire().incantation(SLOT).size() == 1, "the slot holds it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "incantation_4")
    public static void aPreviewSpendsNoUsesAndAPressDoes(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        write(player, 1, ProjectileVerses.EMBER);
        helper.runAtTickTime(1, () -> {
            RecitePlan plan = IncantationService.preview(state(player), SLOT);
            helper.assertTrue(plan.bodies().size() == 1, "the preview plans the ember");
            helper.assertTrue(usesLeft(player, 0) == 15, "and spends none of its fifteen uses: " + usesLeft(player, 0));
            press(player);
        });
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(usesLeft(player, 0) == 14, "a press spends one: " + usesLeft(player, 0));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "incantation_5")
    public static void aSecondPressPlaysTheNextVerseAndAForgetStartsOver(GameTestHelper helper) {
        ServerPlayer player = wielder(helper);
        // Breath one: the ember, then the needle, then the deck is spent and shuffles back.
        write(player, 1, ProjectileVerses.EMBER, ProjectileVerses.NEEDLE);
        helper.runAtTickTime(1, () -> press(player));
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(usesLeft(player, 0) == 14, "the first press read the ember");
            state(player).setSkillCooldown(SKILL, 0);
            IncantationService.forget(player.getUUID());
            press(player);
        });
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(usesLeft(player, 0) == 13, "forgotten, the session starts at the ember again: " + usesLeft(player, 0));
            state(player).setSkillCooldown(SKILL, 0);
            press(player);
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(usesLeft(player, 0) == 13, "kept, the session moves on to the needle: " + usesLeft(player, 0));
            helper.succeed();
        });
    }
}
