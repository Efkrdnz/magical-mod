package com.efkrdnz.magical.magic.incantation;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Tapes by path, presses with plain numbers, and the paths of what came out. */
final class ReciteFixtures {

    static final int PLENTY = 1000;

    private ReciteFixtures() {
    }

    static ResourceLocation id(String path) {
        return VerseIds.of(path);
    }

    static Incantation tape(int breath, String... paths) {
        Incantation incantation = new Incantation();
        List<ResourceLocation> ids = Arrays.stream(paths).map(ReciteFixtures::id).toList();
        assertTrue(incantation.write(ids, breath, VerseContent.CATALOGUE), "the fixture tape must be valid: " + Arrays.toString(paths));
        return incantation;
    }

    static ReciteSession session(String... paths) {
        return ReciteSession.of(tape(1, paths), VerseContent.CATALOGUE);
    }

    static RecitePlan press(ReciteSession session, int breath, int mana, ReciteWorld world) {
        return Reciter.recite(session, breath, mana, 1.0D, world);
    }

    static RecitePlan press(ReciteSession session) {
        return press(session, 1, PLENTY, new FixedWorld());
    }

    /** The verse paths of the bodies of a shot, in order. */
    static List<String> bodies(ShotPlan plan) {
        return plan.bodies().stream().map(body -> body.verse().getPath()).toList();
    }

    static List<String> unread(ReciteSession session) {
        return session.deck().stream().map(card -> card.id().getPath()).toList();
    }

    static List<String> read(ReciteSession session) {
        return session.discard().stream().map(card -> card.id().getPath()).toList();
    }

    static long played(RecitePlan plan, String path) {
        return plan.events().stream()
                .filter(event -> event.kind() == ReciteEvent.Kind.PLAYED)
                .filter(event -> event.verse() != null && event.verse().getPath().equals(path))
                .count();
    }

    static long count(RecitePlan plan, ReciteEvent.Kind kind) {
        return plan.events().stream().filter(event -> event.kind() == kind).count();
    }
}
