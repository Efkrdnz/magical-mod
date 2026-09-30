package com.efkrdnz.magical.magic.sound;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.gametest.GameTestPlayers;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.function.IntPredicate;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What only a live server can say about the Authority of Sound: that a Song bills and plays and
 * answers the notes it is acted on, that its upkeep ends it, and that every family of the Riff does
 * to a body what its note says it does. The judging itself is pinned exactly in {@code SongRunTest};
 * here a claim is made the way a well-timed client would make it, on a note near the server clock.
 */
@GameTestHolder(MagicalMod.MODID)
@PrefixGameTestTemplate(false)
public final class SoundGameTests {

    private static final String TEMPLATE = "unwaking_empty";

    private SoundGameTests() {}

    private static ServerPlayer wielder(GameTestHelper helper, String name) {
        ServerPlayer player = GameTestPlayers.survival(helper, new BlockPos(2, 2, 0), name);
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        state.setAuthority(AuthorityContent.SOUND);
        state.setBarrier(0);
        state.refillMana();
        return player;
    }

    private static PlayerMagicState state(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE);
    }

    /** Brisk: a kick on every even step, the bass on each beat, the melody between the beats. */
    static Score dense() {
        Score score = Score.empty().withTempo(Tempo.BRISK);
        for (int step = 0; step < Score.STEPS; step += 2) {
            score = score.set(Track.PERCUSSION, 0, step, true).score();
            if (step % 4 == 0) {
                score = score.set(Track.BASS, 1, step, true).score();
            } else {
                score = score.set(Track.MELODY, 2, step, true).score();
            }
        }
        return score;
    }

    /** A note near the server clock on a step that passes the test, as a well-timed client would name it. */
    private static long noteNear(ServerPlayer player, IntPredicate step) {
        SongRun run = SongService.run(player);
        long now = player.level().getGameTime();
        long around = SongRun.indexAt(run.start(), run.score().tempo(), now);
        for (long distance = 0; distance <= 6; distance++) {
            for (long index : new long[] {around + distance, around - distance}) {
                long at = SongRun.tickOf(run.start(), run.score().tempo(), index);
                if (index >= 0 && step.test((int) Math.floorMod(index, (long) Score.STEPS))
                        && Math.abs(at - now) <= SongRun.LAG_TOLERANCE) {
                    return index;
                }
            }
        }
        return -1L;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sound_song_1")
    public static void theSongPaysToStartAndStopsForFree(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-start-test");
        PlayerMagicState state = state(player);
        int before = state.mana();
        helper.assertTrue(SongService.start(player, state), "a Song with notes did not start");
        helper.assertTrue(SongService.playing(player), "the Song is not playing");
        helper.assertTrue(state.mana() < before, "starting the Song cost nothing");
        int after = state.mana();
        SongService.toggle(player, state);
        helper.assertFalse(SongService.playing(player), "pressing again did not stop it");
        helper.assertTrue(state.mana() == after, "stopping the Song was billed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sound_song_2")
    public static void anEmptySongIsRefusedAndBillsNothing(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-empty-test");
        PlayerMagicState state = state(player);
        state.sound().setSong(Score.empty());
        int before = state.mana();
        helper.assertFalse(SongService.start(player, state), "an empty Song started");
        helper.assertTrue(state.mana() == before, "an empty Song was billed");
        helper.assertFalse(state.isSkillOnCooldown(MagicContent.SONG.id()), "an empty Song started its cooldown");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "sound_song_3")
    public static void aCrouchOnTheKitAndMelodyIsABulwarkAndAMend(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-crouch-test");
        PlayerMagicState state = state(player);
        state.sound().setSong(dense());
        helper.assertTrue(SongService.start(player, state), "the Song did not start");
        helper.runAtTickTime(14, () -> {
            player.setHealth(10.0F);
            long index = noteNear(player, step -> step % 4 == 2);
            helper.assertTrue(index >= 0, "no kit-and-melody note near the clock");
            SongRun.Judgement judgement = SongService.claim(player, SongAction.CROUCH, index);
            helper.assertTrue(judgement != null && judgement.onBeat(), "a crouch on a note was not on the beat");
            helper.assertTrue(judgement.tracks().contains(Track.PERCUSSION) && judgement.tracks().contains(Track.MELODY),
                    "the kit and the melody did not both answer: " + judgement.tracks());
            helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "the kit gave no Bulwark");
            helper.assertTrue(player.getHealth() > 10.0F, "the melody gave no Mend");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "sound_song_4")
    public static void aSwingOnTheBassWeighsDownWhatHearsIt(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-swing-test");
        PlayerMagicState state = state(player);
        state.sound().setSong(dense());
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 3));
        helper.assertTrue(SongService.start(player, state), "the Song did not start");
        helper.runAtTickTime(14, () -> {
            long index = noteNear(player, step -> step % 4 == 0);
            SongRun.Judgement judgement = SongService.claim(player, SongAction.SWING, index);
            helper.assertTrue(judgement != null && judgement.onBeat(), "a swing on a note was not on the beat");
            helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the bass did not weigh the husk down");
            helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a swing weighed down the wielder");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "sound_song_5")
    public static void aClaimOffTheClockIsNotBelievedAndGivesNothing(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-forged-test");
        PlayerMagicState state = state(player);
        state.sound().setSong(dense());
        helper.assertTrue(SongService.start(player, state), "the Song did not start");
        helper.runAtTickTime(14, () -> {
            long far = noteNear(player, step -> step % 4 == 2) + Score.STEPS;
            SongRun.Judgement judgement = SongService.claim(player, SongAction.CROUCH, far);
            helper.assertTrue(judgement != null && judgement.kind() == SongRun.Kind.NOT_BELIEVED, "a note a loop away was believed");
            helper.assertFalse(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "a claim that was not believed still paid out");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sound_song_6")
    public static void aFullStreakMakesEverythingInEarshotDance(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-dance-test");
        PlayerMagicState state = state(player);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 3));
        helper.assertTrue(SongService.start(player, state), "the Song did not start");
        SongService.danceNow(player);
        helper.assertTrue(SongService.dancing(player, husk), "the husk is not dancing");
        helper.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "a dancer can still land a blow");
        helper.assertFalse(SongService.dancing(player, player), "the wielder dances to their own Song");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = "sound_song_7")
    public static void aBarThatCannotBePaidForEndsTheSong(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-upkeep-test");
        PlayerMagicState state = state(player);
        state.sound().setSong(dense());
        helper.assertTrue(SongService.start(player, state), "the Song did not start");
        state.setMana(0);
        helper.runAtTickTime(SongService.LEAD_IN + Tempo.BRISK.barTicks() - 4, () ->
                helper.assertTrue(SongService.playing(player), "the Song ended before its first bar was due"));
        helper.runAtTickTime(SongService.LEAD_IN + Tempo.BRISK.barTicks() + 6, () -> {
            helper.assertFalse(SongService.playing(player), "a bar nobody paid for was played");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sound_riff_1")
    public static void aHeldRiffPlaysANoteEveryFourTicksAndBillsTheSquare(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-riff-test");
        PlayerMagicState state = state(player);
        state.sound().setRiff(Riff.of(5, new int[] {Instrument.BASEDRUM.ordinal()}, new int[] {12}));
        state.equip(1, MagicContent.RIFF.id());
        int perNote = Math.round(RiffNote.resolve(Instrument.BASEDRUM, 12, 5).mana());
        helper.assertTrue(perNote == 10, "a note at amplitude five should cost 0.4 x 25 = 10, not " + perNote);
        int[] before = new int[1];
        helper.runAtTickTime(1, () -> {
            before[0] = state.mana();
            HoldService.setHeld(player, 1, true);
        });
        helper.runAtTickTime(14, () -> {
            HoldService.setHeld(player, 1, false);
            int spent = before[0] - state.mana();
            helper.assertTrue(spent >= 3 * perNote && spent <= 4 * perNote && spent % perNote == 0,
                    "twelve ticks held should be three or four notes of " + perNote + ", was " + spent);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sound_riff_2")
    public static void aDrumNoteStrikesWhatIsInFrontOfIt(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-drum-test");
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 3));
        helper.runAtTickTime(2, () -> {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getBoundingBox().getCenter());
            float health = husk.getHealth();
            RiffService.play(player, RiffNote.resolve(Instrument.SNARE, 12, 3));
            helper.assertTrue(husk.getHealth() < health, "the drum did not strike the husk in front of it");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40, batch = "sound_riff_3")
    public static void aKeyRayPassesThroughTheFirstBodyIntoTheSecond(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-keys-test");
        Husk near = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 2));
        Husk far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 4));
        helper.runAtTickTime(2, () -> {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, far.getBoundingBox().getCenter());
            float nearHealth = near.getHealth();
            float farHealth = far.getHealth();
            RiffService.play(player, RiffNote.resolve(Instrument.HARP, 12, 3));
            helper.assertTrue(near.getHealth() < nearHealth, "the ray missed the first husk");
            helper.assertTrue(far.getHealth() < farHealth, "the ray stopped at the first husk");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "sound_riff_4")
    public static void aBellRingsThroughArmourAndItsEnchantment(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-bell-test");
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 3));
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        husk.setItemSlot(EquipmentSlot.CHEST, chest);
        RiffNote note = RiffNote.resolve(Instrument.BELL, 12, 3);
        float[] health = new float[1];
        helper.runAtTickTime(2, () -> {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getBoundingBox().getCenter());
            health[0] = husk.getHealth();
            RiffService.play(player, note);
        });
        helper.runAtTickTime(16, () -> {
            float lost = health[0] - husk.getHealth();
            helper.assertTrue(lost >= note.damage() * 0.99F, "armour and Protection took something off a bell: lost " + lost + " of " + note.damage());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 60, batch = "sound_riff_5")
    public static void aLowNoteRollsThroughAWallAndWeighsDownWhatIsBehindIt(GameTestHelper helper) {
        ServerPlayer player = wielder(helper, "sound-low-test");
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.STONE);
        Husk husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 4));
        float[] health = new float[1];
        helper.runAtTickTime(2, () -> {
            player.lookAt(EntityAnchorArgument.Anchor.EYES, husk.getBoundingBox().getCenter());
            health[0] = husk.getHealth();
            RiffService.play(player, RiffNote.resolve(Instrument.BASS, 12, 3));
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(husk.getHealth() < health[0], "the low note stopped at the wall");
            helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the low note did not weigh the husk down");
            helper.succeed();
        });
    }
}
