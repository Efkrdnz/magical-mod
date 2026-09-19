package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Leaves: a verse that adds a body and draws nothing, unless it carries a payload. */
public final class ProjectileVerses {

    public static final ResourceLocation NEEDLE = VerseIds.of("needle");
    public static final ResourceLocation EMBER = VerseIds.of("ember");

    private ProjectileVerses() {
    }

    /**
     * Deltas onto the state, <em>then</em> the body. The Lua calls {@code add_projectile} first and
     * writes its deltas after, so a Spark Bolt is stamped without its own spread or crit; the design's
     * deviation 11 inverts that on purpose, so a body always carries the numbers of the verse that
     * made it, and {@code ReciterTest} pins it.
     */
    static Verse projectile(String path, int mana, int uses, VersePrototype prototype, int beat, int rest,
                            Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.PROJECTILE, mana, uses, prototype, 1, Declared.of(0, beat, rest), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            r.addRest(rest);
            effect.accept(s);
            r.addProjectile(prototype);
            return VerseAction.NONE;
        });
    }

    /** A body that carries a payload of {@code draw} verses, released by {@code kind}. */
    static Verse carrier(String path, int mana, int uses, VersePrototype prototype, int beat, PayloadKind kind,
                         int fuseTicks, int draw, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.PROJECTILE, mana, uses, prototype, 1, Declared.of(draw, beat, 0), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            effect.accept(s);
            switch (kind) {
                case LATCH -> r.addProjectileLatch(prototype, draw);
                case FUSE -> r.addProjectileFuse(prototype, fuseTicks, draw);
                case EPITAPH -> r.addProjectileEpitaph(prototype, draw);
                case NONE -> r.addProjectile(prototype);
            }
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(projectile("needle", 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, 0, s -> {
            s.addSpread(-1.0D);
            s.addCrit(5.0D);
        }));
        // The burn is the body's own, written on the prototype, so it never reaches the verses behind it.
        c.register(projectile("ember", 14, 15, VersePrototypes.EMBER, 12, 0, s -> {
            s.addSpread(4.0D);
            s.addRecoil(20.0D);
        }));
        c.register(carrier("needle_latch", 6, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.LATCH, 0, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("needle_fuse", 6, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.FUSE, 4, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("needle_twin_latch", 8, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.LATCH, 0, 2, s -> s.addCrit(5.0D)));
        c.register(projectile("orb", 7, Verse.UNLIMITED, VersePrototypes.ORB, 2, 0, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_latch", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.LATCH, 0, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_fuse", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.FUSE, 8, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_epitaph", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.EPITAPH, 0, 1, s -> s.addCrit(5.0D)));
        c.register(projectile("balm_dart", 8, 20, VersePrototypes.DART, 1, 0, s -> s.addSpread(2.0D)));
        c.register(projectile("shard", 12, Verse.UNLIMITED, VersePrototypes.SHARD, 4, 0, s -> s.addKnockback(1.0D)));
        // Likewise the shock: lightning.xml carries it, the shot state does not.
        c.register(projectile("arc_bolt", 16, Verse.UNLIMITED, VersePrototypes.ARC, 17, 0, s -> s.addRecoil(60.0D)));
        // CHAINSAW: the beat is set to nothing, not added to.
        c.register(projectile("whisper", 1, Verse.UNLIMITED, VersePrototypes.WHISPER, 0, -3, s -> {
            s.setBeat(0);
            s.addSpread(6.0D);
        }));
        c.register(projectile("blink_dart", 10, Verse.UNLIMITED, VersePrototypes.BLINK, 3, 0, s -> { }));
        // RANDOM_PROJECTILE: a known projectile verse, run in this one's place.
        c.register(Verse.of("wild_bolt", VerseType.PROJECTILE, 6, Verse.UNLIMITED, null, 1, Declared.NONE, (r, rec, it) -> {
            ControlVerses.wild(r, rec, VerseType.PROJECTILE);
            return VerseAction.NONE;
        }).asRecursive());
    }
}
