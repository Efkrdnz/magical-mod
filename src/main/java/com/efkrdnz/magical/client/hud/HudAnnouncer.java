package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.classes.MagicalClassProgress;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Notices what the player just gained and queues it for the sigil to announce.
 *
 * <p>Nothing on the wire says "skill unlocked" - those are chat lines - so this diffs successive
 * player-state snapshots: a set that grew has a new member, and that member is the announcement.
 * The first snapshot after a reset only primes the diff, because the state from the previous
 * world is never cleared and would otherwise announce every skill the player already had.
 */
public final class HudAnnouncer {
    public enum Kind { SKILL, PASSIVE, CURSE, CLASS, AUTHORITY, RACE }

    public record Announcement(Kind kind, ResourceLocation id, long startTick) {}

    /** Ink on for ten ticks, hold, dissolve over the last fourteen. */
    public static final int LIFETIME_TICKS = 90;
    public static final int QUEUE_CAP = 3;

    private static final ArrayDeque<Announcement> QUEUE = new ArrayDeque<>(QUEUE_CAP);
    private static boolean primed;
    private static int version;

    private HudAnnouncer() {}

    public static void observe(PlayerMagicState previous, PlayerMagicState next, long now) {
        if (!primed || previous == null) {
            primed = true;
            return;
        }
        grew(previous.unlockedSkills(), next.unlockedSkills(), Kind.SKILL, now);
        grew(previous.unlockedPassives(), next.unlockedPassives(), Kind.PASSIVE, now);
        grew(previous.activeCurses(), next.activeCurses(), Kind.CURSE, now);
        classesUnlocked(previous.classProgress(), next.classProgress(), now);
        changed(previous.authorityId(), next.authorityId(), Kind.AUTHORITY, now);
        changed(previous.raceId(), next.raceId(), Kind.RACE, now);
    }

    /**
     * A membership diff, not a size check: a consuming fusion is two skills out and one in, and the
     * set that shrank still carries the news. Wheel modes ride on their parent and do not announce.
     */
    private static void grew(Set<ResourceLocation> before, Set<ResourceLocation> after, Kind kind, long now) {
        for (ResourceLocation id : after) {
            if (before.contains(id) || kind == Kind.SKILL && MagicContent.isSubSkill(id)) {
                continue;
            }
            push(kind, id, now);
        }
    }

    private static void classesUnlocked(Map<ResourceLocation, MagicalClassProgress> before, Map<ResourceLocation, MagicalClassProgress> after, long now) {
        for (Map.Entry<ResourceLocation, MagicalClassProgress> entry : after.entrySet()) {
            if (!entry.getValue().unlocked()) {
                continue;
            }
            MagicalClassProgress was = before.get(entry.getKey());
            if (was == null || !was.unlocked()) {
                push(Kind.CLASS, entry.getKey(), now);
            }
        }
    }

    private static void changed(ResourceLocation before, ResourceLocation after, Kind kind, long now) {
        if (after != null && !after.equals(before)) {
            push(kind, after, now);
        }
    }

    private static void push(Kind kind, ResourceLocation id, long now) {
        for (Announcement queued : QUEUE) {
            if (queued.kind() == kind && queued.id().equals(id)) {
                return;
            }
        }
        if (QUEUE.size() >= QUEUE_CAP) {
            QUEUE.pollFirst();
        }
        QUEUE.addLast(new Announcement(kind, id, now));
        version++;
    }

    /** Once a tick: the head announces for its lifetime, then the next one starts. */
    public static void tick(long now) {
        Announcement head = QUEUE.peekFirst();
        if (head != null && now - head.startTick() >= LIFETIME_TICKS) {
            QUEUE.pollFirst();
            Announcement next = QUEUE.peekFirst();
            if (next != null) {
                // Queued behind the one that just ended: its clock starts now, not when it was noticed.
                QUEUE.pollFirst();
                QUEUE.addFirst(new Announcement(next.kind(), next.id(), now));
            }
            version++;
        }
    }

    /** The announcement on screen, or null. */
    public static Announcement head() {
        return QUEUE.peekFirst();
    }

    public static Collection<Announcement> queued() {
        return Collections.unmodifiableCollection(QUEUE);
    }

    public static void reset() {
        QUEUE.clear();
        primed = false;
        version++;
    }

    public static int version() {
        return version;
    }
}
