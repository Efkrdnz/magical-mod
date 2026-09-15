package com.efkrdnz.magical.magic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * An authority is not a skill you unlock. It is a thing you hold, and its skills are what holding it
 * means - so they arrive with it and leave with it, and no other door opens them.
 *
 * <p>{@code /magical unlockall} was that other door: it handed out every authority skill in the game
 * at once, which made the debug command the easiest way to be a god and made the authority itself
 * decorative. These pin the one route in.
 */
class AuthorityGrantTest {

    private static Set<ResourceLocation> skillsOf(ResourceLocation authorityId) {
        return Set.copyOf(AuthorityContent.get(authorityId).skillIds());
    }

    @Test
    void unlockingEverySkillStillLeavesTheAuthoritiesShut() {
        PlayerMagicState state = new PlayerMagicState();
        state.unlockAll(MagicContent.ALL_SKILLS);
        for (ResourceLocation skillId : MagicContent.AUTHORITY_SKILLS) {
            assertFalse(state.unlockedSkills().contains(skillId),
                    "unlockall handed out " + skillId + ", which only an authority may grant");
        }
    }

    @Test
    void unlockingEverySkillStillGrantsEverythingElse() {
        // The other half of the rule, and the one that would fail if the filter were too wide: the
        // debug command is still the debug command for every skill that is not an authority's.
        PlayerMagicState state = new PlayerMagicState();
        state.unlockAll(MagicContent.ALL_SKILLS);
        for (MagicSkillDefinition skill : MagicContent.allSkills()) {
            if (MagicContent.isAuthoritySkill(skill.id()) || MagicContent.SUB_SKILLS.contains(skill.id())
                    || MagicContent.CREATED_SKILLS.contains(skill.id())) {
                continue;
            }
            assertTrue(state.unlockedSkills().contains(skill.id()),
                    "unlockall no longer grants " + skill.id() + ", which is not an authority's");
        }
    }

    @Test
    void anAuthorityBringsItsOwnSkillsWithIt() {
        for (AuthorityDefinition authority : AuthorityContent.all()) {
            PlayerMagicState state = new PlayerMagicState();
            assertTrue(state.setAuthority(authority.id()), "could not awaken " + authority.id());
            for (ResourceLocation skillId : authority.skillIds()) {
                assertTrue(state.unlockedSkills().contains(skillId),
                        authority.id() + " did not bring " + skillId);
            }
        }
    }

    @Test
    void takingAnotherAuthorityGivesTheFirstOneBack() {
        // One authority at a time is the whole premise: the second one has to take the first one's
        // skills away, or a player could ladder through every authority in the game and keep the lot.
        PlayerMagicState state = new PlayerMagicState();
        state.setAuthority(AuthorityContent.SPACE);
        state.setAuthority(AuthorityContent.SOUL);
        for (ResourceLocation skillId : skillsOf(AuthorityContent.SPACE)) {
            assertFalse(state.unlockedSkills().contains(skillId),
                    "a soul wielder kept " + skillId + " from the authority they gave up");
        }
        for (ResourceLocation skillId : skillsOf(AuthorityContent.SOUL)) {
            assertTrue(state.unlockedSkills().contains(skillId), "the soul wielder never got " + skillId);
        }
    }

    @Test
    void lettingGoOfAnAuthorityTakesItsSkillsWithIt() {
        PlayerMagicState state = new PlayerMagicState();
        state.setAuthority(AuthorityContent.SPACE);
        state.clearAuthority();
        for (ResourceLocation skillId : skillsOf(AuthorityContent.SPACE)) {
            assertFalse(state.unlockedSkills().contains(skillId),
                    "the skills stayed after the authority went: " + skillId);
        }
    }

    @Test
    void everySkillAnAuthorityGrantsIsKnownToBeAnAuthoritySkill() {
        // AUTHORITY_SKILLS is hand-maintained and AuthorityContent is the real roster; they are the
        // same list written twice. A new authority whose skills are missing from the set would be
        // handed out by unlockall and answered by Sovereign Aegis, both silently.
        Set<ResourceLocation> granted = new LinkedHashSet<>();
        AuthorityContent.all().forEach(authority -> granted.addAll(authority.skillIds()));
        assertEquals(granted, Set.copyOf(MagicContent.AUTHORITY_SKILLS),
                "the authorities grant one list and AUTHORITY_SKILLS names another");
    }
}
