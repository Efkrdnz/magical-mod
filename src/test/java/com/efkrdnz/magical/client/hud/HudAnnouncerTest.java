package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.client.hud.HudAnnouncer.Announcement;
import com.efkrdnz.magical.client.hud.HudAnnouncer.Kind;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The announcer notices only what is new, once, and never what the player already had. */
class HudAnnouncerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void reset() {
        HudAnnouncer.reset();
    }

    private static PlayerMagicState withSkill(PlayerMagicState base) {
        PlayerMagicState next = base.copy();
        next.unlock(MagicContent.GABRIEL.id());
        return next;
    }

    @Test
    void theFirstSnapshotAfterAResetOnlyPrimes() {
        PlayerMagicState first = withSkill(new PlayerMagicState());
        HudAnnouncer.observe(new PlayerMagicState(), first, 10L);
        assertNull(HudAnnouncer.head(), "the state from a previous world must not announce itself");
    }

    @Test
    void newSkillsAreAnnouncedOnceAndExpire() {
        PlayerMagicState before = new PlayerMagicState();
        HudAnnouncer.observe(null, before, 0L);
        PlayerMagicState after = withSkill(before);
        // Gabriel brings its wheel modes with it, so several skills arrive in one packet.
        Set<ResourceLocation> gained = new HashSet<>(after.unlockedSkills());
        gained.removeAll(before.unlockedSkills());
        assertTrue(gained.contains(MagicContent.GABRIEL.id()));
        HudAnnouncer.observe(before, after, 10L);
        Announcement head = HudAnnouncer.head();
        assertNotNull(head);
        assertEquals(Kind.SKILL, head.kind());
        assertTrue(gained.contains(head.id()), "announced something the player did not just gain: " + head.id());
        assertEquals(10L, head.startTick());
        int queued = HudAnnouncer.queued().size();
        assertEquals(Math.min(gained.size(), HudAnnouncer.QUEUE_CAP), queued);

        HudAnnouncer.observe(after, after.copy(), 11L);
        assertEquals(queued, HudAnnouncer.queued().size(), "an unchanged set announces nothing");

        HudAnnouncer.tick(10L + HudAnnouncer.LIFETIME_TICKS - 1);
        assertEquals(head, HudAnnouncer.head(), "still on screen a tick before its lifetime");
        HudAnnouncer.tick(10L + HudAnnouncer.LIFETIME_TICKS);
        assertTrue(HudAnnouncer.head() == null || !HudAnnouncer.head().equals(head), "gone at its lifetime");
    }

    @Test
    void aPassiveAndACurseAreTheirOwnKinds() {
        PlayerMagicState before = new PlayerMagicState();
        HudAnnouncer.observe(null, before, 0L);
        PlayerMagicState after = before.copy();
        after.unlockPassive(MagicPassiveContent.MANA_FLIGHT.id());
        after.addCurse(MagicPassiveContent.SIN_PRIDE_CURSE.id());
        HudAnnouncer.observe(before, after, 5L);
        assertEquals(2, HudAnnouncer.queued().size());
        assertEquals(Kind.PASSIVE, HudAnnouncer.head().kind());
    }

    @Test
    void theQueueKeepsTheNewestThreeAndTheNextStartsWhenTheHeadEnds() {
        PlayerMagicState before = new PlayerMagicState();
        HudAnnouncer.observe(null, before, 0L);
        PlayerMagicState after = before.copy();
        after.unlockPassive(MagicPassiveContent.MANA_FLIGHT.id());
        after.unlockPassive(MagicPassiveContent.SIN_PRIDE.id());
        after.unlockPassive(MagicPassiveContent.SIN_WRATH.id());
        after.unlockPassive(MagicPassiveContent.SIN_SLOTH.id());
        HudAnnouncer.observe(before, after, 20L);
        assertEquals(HudAnnouncer.QUEUE_CAP, HudAnnouncer.queued().size(), "the oldest was dropped");
        int version = HudAnnouncer.version();
        HudAnnouncer.tick(20L + HudAnnouncer.LIFETIME_TICKS);
        assertEquals(2, HudAnnouncer.queued().size());
        assertEquals(20L + HudAnnouncer.LIFETIME_TICKS, HudAnnouncer.head().startTick(), "the next one's clock starts when it reaches the front");
        assertEquals(version + 1, HudAnnouncer.version(), "expiry is a change the snapshot must see");
    }
}
