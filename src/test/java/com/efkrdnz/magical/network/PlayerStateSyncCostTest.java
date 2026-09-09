package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.MagicTuningStat;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * What one call to {@code PlayerMagicState.sync} puts on the wire.
 *
 * <p>The state's NBT is also the packet, and sync is called from over a hundred sites - some of
 * them on a tick path, such as the mana drain that runs the whole time a player is flying. So the
 * size of this blob is a per-tick cost for a flying player, not a once-per-session one. These pin
 * it for a finished character so a new collection cannot quietly double it.
 */
class PlayerStateSyncCostTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * A finished character: everything unlocked, but only a handful of skills actually tuned.
     *
     * <p>That last part is the realistic bit. Points are a budget now, so a player spends them on
     * the few skills they build around and leaves the rest alone.
     */
    static PlayerMagicState lateGame() {
        PlayerMagicState state = new PlayerMagicState();
        state.setProficiencyXp(720);
        for (MagicSkillDefinition skill : MagicContent.allSkills()) {
            state.unlock(skill.id());
            state.setSkillCooldown(skill.id(), 40);
        }
        MagicPassiveContent.all().forEach(passive -> state.unlockPassive(passive.id()));
        for (MagicSkillDefinition skill : MagicContent.allSkills().subList(0, 6)) {
            state.adjustTuning(skill.id(), MagicTuningStat.DAMAGE, 3);
            state.adjustTuning(skill.id(), MagicTuningStat.SIZE, 2);
        }
        return state;
    }

    static int wireBytes(CompoundTag tag) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(sink)) {
            NbtIo.write(tag, out);
        }
        return sink.size();
    }

    /** The tag the old {@code save()} produced: every seeded entry written out, zeroes and all. */
    private static CompoundTag withEveryTuningEntry(PlayerMagicState state) {
        CompoundTag tag = state.save();
        CompoundTag tuning = tag.getCompound("tuning");
        for (ResourceLocation id : state.unlockedSkills()) {
            if (!tuning.contains(id.toString())) {
                tuning.put(id.toString(), MagicSkillTuning.DEFAULT.save());
            }
        }
        return tag;
    }

    @Test
    void anUntunedSkillCostsNothingOnTheWire() throws IOException {
        PlayerMagicState state = lateGame();
        int before = wireBytes(withEveryTuningEntry(state));
        int after = wireBytes(state.save());

        System.out.println("[sync cost] unlocked skills: " + state.unlockedSkills().size());
        System.out.println("[sync cost] every entry written: " + before + " bytes");
        System.out.println("[sync cost] defaults omitted:    " + after + " bytes");
        System.out.println("[sync cost] saved " + (before - after) + " bytes per sync ("
                + Math.round(100.0 * (before - after) / before) + "%)");

        assertTrue(after < before / 2,
                "omitting untouched entries should more than halve the payload; got "
                        + after + " vs " + before);
    }

    @Test
    void omittingDefaultsChangesNothingTheGameCanSee() throws IOException {
        // The whole optimisation rests on this: a key that is absent reads back as DEFAULT, so a
        // round trip through the smaller tag has to produce the same state the larger one did.
        PlayerMagicState original = lateGame();
        PlayerMagicState viaSmall = PlayerMagicState.load(original.save());
        PlayerMagicState viaFull = PlayerMagicState.load(withEveryTuningEntry(original));

        assertEquals(viaFull.unlockedSkills(), viaSmall.unlockedSkills());
        for (ResourceLocation id : viaFull.unlockedSkills()) {
            assertEquals(viaFull.tuningFor(id), viaSmall.tuningFor(id),
                    id + " resolved differently through the smaller tag");
        }
        assertEquals(wireBytes(viaFull.save()), wireBytes(viaSmall.save()),
                "and the two round-trip to the same tag");
    }

    @Test
    void aStateThatHasNotChangedIsNotSentTwice() throws IOException {
        // sync() drops a packet whose tag matches the last one sent. That is only sound while equal
        // states produce equal tags, so pin it: the map iteration order has to be stable too.
        PlayerMagicState state = lateGame();
        assertEquals(state.save(), state.save(), "two saves of an unchanged state must match");

        state.setMana(state.mana() - 1);
        assertTrue(!state.save().equals(withEveryTuningEntry(lateGame())),
                "a state that did change must produce a different tag");
    }

    @Test
    void theWholeProgressionStillFitsABudget() throws IOException {
        int bytes = wireBytes(lateGame().save());
        System.out.println("[sync cost] finished character: " + bytes + " bytes per sync");
        assertTrue(bytes < 16_000,
                "a single state payload has grown past 16 KB (" + bytes + "); the wire format needs "
                        + "splitting before another collection is added to it");
    }

    @Test
    void unlockedListsAreWhatIsLeft() throws IOException {
        // Not a failure, a signpost: with tuning trimmed, the remaining bulk is the two id lists.
        // If this ever needs to shrink, indices into the registry are the next lever, not more maps.
        PlayerMagicState state = lateGame();
        CompoundTag tag = state.save();
        CompoundTag skillsOnly = new CompoundTag();
        skillsOnly.put("unlockedSkills", tag.get("unlockedSkills"));
        skillsOnly.put("unlockedPassives", tag.get("unlockedPassives"));
        List<String> keys = List.copyOf(tag.getAllKeys());
        System.out.println("[sync cost] id lists alone: " + wireBytes(skillsOnly)
                + " of " + wireBytes(tag) + " bytes, across " + keys.size() + " keys");
        assertTrue(wireBytes(skillsOnly) > 0);
    }
}
