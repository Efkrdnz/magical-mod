package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Loadouts: four cast keys per named set, switched with the wheel key.
 *
 * <p>The rules that would be expensive to get wrong are pinned here - that switching cannot launder
 * a cooldown, that casting locks the switch but only a successful cast does, that a skill removed
 * from the player leaves every set rather than only the active one, and that an old save converts
 * without silently eating a build.
 */
class MagicLoadoutTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PlayerMagicState withSkills() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlock(MagicContent.CRUCIBLE.id());
        state.unlock(MagicContent.WILDFIRE.id());
        state.unlock(MagicContent.RIME_SNAP.id());
        return state;
    }

    @Test
    void aFreshStateAlreadyHasOneLoadout() {
        // equippedSkill reads through to the active loadout, so a state that was never saved still
        // has to have somewhere for a skill to go.
        PlayerMagicState state = new PlayerMagicState();
        assertEquals(1, state.loadouts().size());
        assertEquals(0, state.activeLoadoutIndex());
        assertNull(state.equippedSkill(0));
    }

    @Test
    void switchingLoadoutsRepointsAllFourKeysIncludingTheEmptyOnes() {
        // The half that is easy to miss: a key the new loadout leaves unbound must go quiet, not
        // keep whatever the previous loadout had there.
        PlayerMagicState state = withSkills();
        state.equip(0, MagicContent.CRUCIBLE.id());
        state.equip(1, MagicContent.WILDFIRE.id());
        assertTrue(state.createLoadout("Second"));
        state.setLoadoutSlot(1, 0, MagicContent.RIME_SNAP.id());

        assertTrue(state.selectLoadout(1));
        assertEquals(MagicContent.RIME_SNAP.id(), state.equippedSkill(0));
        assertNull(state.equippedSkill(1), "slot two is empty in this loadout and must read empty");
    }

    @Test
    void switchingCannotLaunderACooldown() {
        // The exploit this design most invites. Cooldowns are keyed by skill and the per-slot array
        // is only a mirror, so parking a spent skill in another set must not refresh it.
        PlayerMagicState state = withSkills();
        state.equip(0, MagicContent.CRUCIBLE.id());
        state.createLoadout("Second");
        state.setLoadoutSlot(1, 0, MagicContent.CRUCIBLE.id());
        state.setSkillCooldown(MagicContent.CRUCIBLE.id(), 400);

        state.selectLoadout(1);
        assertEquals(400, state.skillCooldown(MagicContent.CRUCIBLE.id()));
        assertTrue(state.isOnCooldown(0), "the mirror must show the cooldown in the new set too");
    }

    @Test
    void castingLocksTheSwitchAndTheLockExpires() {
        PlayerMagicState state = withSkills();
        state.createLoadout("Second");

        state.armLoadoutSwapLock();
        assertEquals(MagicContent.LOADOUT_SWAP_LOCK_TICKS, state.loadoutSwapLockTicks());
        assertFalse(state.selectLoadout(1), "a switch inside the lock must be refused");
        assertEquals(0, state.activeLoadoutIndex());
    }

    @Test
    void switchingFreelyIsTheDefault() {
        // The rule taxes casting, not switching: with nothing cast, cycling costs nothing at all.
        PlayerMagicState state = withSkills();
        state.createLoadout("Second");
        state.createLoadout("Third");
        assertTrue(state.selectLoadout(1));
        assertTrue(state.selectLoadout(2));
        assertTrue(state.selectLoadout(0));
    }

    @Test
    void aSkillTakenFromThePlayerLeavesEverySet() {
        PlayerMagicState state = withSkills();
        state.equip(0, MagicContent.CRUCIBLE.id());
        state.createLoadout("Second");
        state.setLoadoutSlot(1, 2, MagicContent.CRUCIBLE.id());

        assertTrue(state.removeSkill(MagicContent.CRUCIBLE.id()));
        assertNull(state.loadout(0).slot(0));
        assertNull(state.loadout(1).slot(2), "an inactive set would hand the skill back on the next switch");
        assertFalse(state.isEquippedAnywhere(MagicContent.CRUCIBLE.id()));
    }

    @Test
    void isEquippedAnywhereSeesInactiveSets() {
        // Backs the codex marker. Looking only at the active loadout is the obvious wrong answer.
        PlayerMagicState state = withSkills();
        state.createLoadout("Second");
        state.setLoadoutSlot(1, 0, MagicContent.WILDFIRE.id());

        assertTrue(state.isEquippedAnywhere(MagicContent.WILDFIRE.id()));
        assertNull(state.equippedSkill(0), "and it is not in the active one");
        assertEquals(List.of("Second"), state.loadoutsContaining(MagicContent.WILDFIRE.id()));
    }

    @Test
    void theLastLoadoutCannotBeDeleted() {
        PlayerMagicState state = withSkills();
        assertFalse(state.deleteLoadout(0), "a player with no loadout could not cast at all");
        assertEquals(1, state.loadouts().size());
    }

    @Test
    void loadoutCountIsCapped() {
        PlayerMagicState state = withSkills();
        for (int i = 1; i < MagicContent.MAX_LOADOUTS; i++) {
            assertTrue(state.createLoadout("Set " + i));
        }
        assertFalse(state.createLoadout("One too many"));
        assertEquals(MagicContent.MAX_LOADOUTS, state.loadouts().size());
    }

    @Test
    void aNameFromAClientIsBoundedAndStripped() {
        // The only free text this mod accepts over the wire, and it is drawn straight into a GUI.
        assertEquals("Loadout", MagicLoadout.sanitizeName(null));
        assertEquals("Loadout", MagicLoadout.sanitizeName("   "));
        assertEquals(MagicLoadout.MAX_NAME_LENGTH, MagicLoadout.sanitizeName("x".repeat(400)).length());
        assertFalse(MagicLoadout.sanitizeName("red§cname").contains("§"),
                "a colour code would let a name repaint the screen around it");
        assertFalse(MagicLoadout.sanitizeName("a\nb").contains("\n"));
    }

    @Test
    void aLoadoutSurvivesASaveAndAMissingSkillDoesNot() {
        PlayerMagicState state = withSkills();
        state.equip(0, MagicContent.CRUCIBLE.id());
        state.createLoadout("Second");
        state.setLoadoutSlot(1, 1, MagicContent.WILDFIRE.id());
        state.selectLoadout(1);

        PlayerMagicState reloaded = PlayerMagicState.load(state.save());
        assertEquals(2, reloaded.loadouts().size());
        assertEquals(1, reloaded.activeLoadoutIndex());
        assertEquals("Second", reloaded.activeLoadout().name());
        assertEquals(MagicContent.WILDFIRE.id(), reloaded.equippedSkill(1));

        // A slot naming a skill the player no longer owns must come back empty, or the key would
        // look bound and silently do nothing.
        CompoundTag tag = state.save();
        ListTag unlocked = new ListTag();
        unlocked.add(StringTag.valueOf(MagicContent.WILDFIRE.id().toString()));
        tag.put("unlockedSkills", unlocked);
        assertNull(PlayerMagicState.load(tag).loadout(0).slot(0));
    }

    @Test
    void anOldWheelSaveBecomesLoadoutsWithoutLosingASkill() {
        // Three equipped keys plus a flat wheel. The three keep their positions so a returning
        // player finds Z, X and C where they left them; the wheel fills forward from the new key.
        PlayerMagicState source = withSkills();
        source.unlock(MagicContent.LODESTONE.id());
        source.unlock(MagicContent.GLINT.id());
        CompoundTag legacy = source.save();
        legacy.remove("loadouts");

        ListTag equipped = new ListTag();
        equipped.add(StringTag.valueOf(MagicContent.CRUCIBLE.id().toString()));
        equipped.add(StringTag.valueOf(""));
        equipped.add(StringTag.valueOf(MagicContent.WILDFIRE.id().toString()));
        legacy.put("equippedSkills", equipped);
        ListTag wheel = new ListTag();
        for (ResourceLocation id : List.of(MagicContent.RIME_SNAP.id(), MagicContent.LODESTONE.id(),
                MagicContent.GLINT.id())) {
            wheel.add(StringTag.valueOf(id.toString()));
        }
        legacy.put("wheelSkills", wheel);

        PlayerMagicState migrated = PlayerMagicState.load(legacy);
        assertEquals(MagicContent.CRUCIBLE.id(), migrated.loadout(0).slot(0), "Z is where it was");
        assertNull(migrated.loadout(0).slot(1), "and so is the gap the player left");
        assertEquals(MagicContent.WILDFIRE.id(), migrated.loadout(0).slot(2));
        assertEquals(MagicContent.RIME_SNAP.id(), migrated.loadout(0).slot(3),
                "the first wheel skill lands on the new fourth key");
        assertEquals(MagicContent.LODESTONE.id(), migrated.loadout(1).slot(0), "the rest spill forward");
        assertEquals(MagicContent.GLINT.id(), migrated.loadout(1).slot(1));
        assertEquals(0, migrated.takePendingLoadoutOverflow(), "nothing was dropped");
        assertEquals(0, migrated.activeLoadoutIndex());
    }

    @Test
    void aWheelTooLargeToFitReportsWhatItDropped() {
        // Silently losing a skill somebody equipped reads as the mod eating their build.
        PlayerMagicState source = new PlayerMagicState();
        List<MagicSkillDefinition> all = MagicContent.allSkills();
        for (MagicSkillDefinition skill : all) {
            source.unlock(skill.id());
        }
        CompoundTag legacy = source.save();
        legacy.remove("loadouts");
        legacy.put("equippedSkills", new ListTag());

        ListTag wheel = new ListTag();
        int capacity = MagicContent.MAX_LOADOUTS * MagicContent.LOADOUT_SIZE;
        for (int i = 0; i < capacity + 3; i++) {
            wheel.add(StringTag.valueOf(all.get(i).id().toString()));
        }
        legacy.put("wheelSkills", wheel);

        PlayerMagicState migrated = PlayerMagicState.load(legacy);
        assertEquals(MagicContent.MAX_LOADOUTS, migrated.loadouts().size());
        assertEquals(3, migrated.takePendingLoadoutOverflow());
        assertEquals(0, migrated.takePendingLoadoutOverflow(), "the notice is taken once");
    }

    @Test
    void bindingRefusesASkillThePlayerDoesNotOwn() {
        PlayerMagicState state = new PlayerMagicState();
        assertFalse(state.setLoadoutSlot(0, 0, MagicContent.CRUCIBLE.id()));
        assertNull(state.equippedSkill(0));
        assertNotNull(state.activeLoadout());
    }
}
